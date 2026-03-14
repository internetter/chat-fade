package com.chatfade;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class ChatFadeOverlay extends Overlay
{
	private static final int LINE_SPACING = 1;
	private static final int PADDING_BOTTOM = 4;
	private static final int PADDING_LEFT = 5;
	private static final int SHADOW_OFFSET = 1;

	private final Client client;
	private final ChatFadePlugin plugin;
	private final ChatFadeConfig config;

	@Inject
	public ChatFadeOverlay(Client client, ChatFadePlugin plugin, ChatFadeConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean chatboxHidden = isChatboxInputHidden();

		if (config.onlyWhenHidden() && !chatboxHidden)
		{
			return null;
		}

		plugin.pruneExpiredMessages();

		String typedText = getTypedText(chatboxHidden);
		List<FadingMessage> messages = plugin.getMessages();

		if (messages.isEmpty() && typedText == null)
		{
			return null;
		}

		Font font = config.fontType().getFont();
		graphics.setFont(font);
		graphics.setRenderingHint(
			RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON
		);

		FontMetrics fm = graphics.getFontMetrics();
		int lineHeight = fm.getHeight();
		boolean hasTypingLine = typedText != null;

		int baseY = calculateBaseY(lineHeight, messages.size(), hasTypingLine);
		int baseX = PADDING_LEFT;

		long now = System.currentTimeMillis();
		long displayMs = config.displayDuration() * 1000L;
		long fadeMs = config.fadeDuration() * 1000L;

		Composite originalComposite = graphics.getComposite();

		int y = baseY;
		for (FadingMessage msg : messages)
		{
			long elapsed = now - msg.getTimestamp();

			float alpha;
			if (elapsed < displayMs)
			{
				alpha = 1.0f;
			}
			else
			{
				long fadeElapsed = elapsed - displayMs;
				alpha = Math.max(0.0f, 1.0f - ((float) fadeElapsed / fadeMs));
			}

			if (alpha <= 0.0f)
			{
				y += lineHeight + LINE_SPACING;
				continue;
			}

			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			renderMessageLine(graphics, fm, msg, baseX, y, alpha);

			y += lineHeight + LINE_SPACING;
		}

		// Render typing input line
		if (hasTypingLine)
		{
			graphics.setComposite(originalComposite);

			int caretWidth = fm.stringWidth("> ");
			int textX = baseX + caretWidth;
			int inputY = calculateTypingInputY(lineHeight);

			// Blinking caret
			boolean showCaret = System.currentTimeMillis() % 1000 < 500;
			if (showCaret)
			{
				graphics.setColor(Color.BLACK);
				graphics.drawString(">", baseX + SHADOW_OFFSET, inputY + SHADOW_OFFSET);
				graphics.setColor(Color.WHITE);
				graphics.drawString(">", baseX, inputY);
			}

			// Truncate message text if needed
			String raw = typedText != null ? typedText : "";
			int maxWidth = config.maxMessageWidth() - caretWidth;
			String inputDisplay = fm.stringWidth(raw) > maxWidth ? truncate(raw, fm, maxWidth) : raw;

			// Shadow
			graphics.setColor(Color.BLACK);
			graphics.drawString(inputDisplay, textX + SHADOW_OFFSET, inputY + SHADOW_OFFSET);

			// Main text
			graphics.setColor(Color.WHITE);
			graphics.drawString(inputDisplay, textX, inputY);
		}

		graphics.setComposite(originalComposite);

		return null;
	}

	private void renderMessageLine(Graphics2D graphics, FontMetrics fm, FadingMessage msg,
		int x, int y, float alpha)
	{
		Color shadowColor = new Color(0, 0, 0, Math.round(alpha * 255));
		String senderName = msg.getSenderName();
		int maxWidth = config.maxMessageWidth();

		boolean isNpcMessage = msg.getType() == net.runelite.api.ChatMessageType.DIALOG
			|| msg.getType() == net.runelite.api.ChatMessageType.MESBOX;
		Color nameColor = isNpcMessage && config.colorizeNpcNames() ? config.npcNameColor()
			: (!isNpcMessage && config.colorizeUsernames()) ? config.usernameColor()
			: null;

		if (senderName != null && nameColor != null)
		{
			String senderPart = senderName + ": ";
			int senderWidth = fm.stringWidth(senderPart);
			int remainingWidth = maxWidth - senderWidth;

			String messagePart = msg.getText();
			if (remainingWidth > 0 && fm.stringWidth(messagePart) > remainingWidth)
			{
				messagePart = truncate(messagePart, fm, remainingWidth);
			}

			// Shadow
			graphics.setColor(shadowColor);
			graphics.drawString(senderPart, x + SHADOW_OFFSET, y + SHADOW_OFFSET);
			graphics.drawString(messagePart, x + senderWidth + SHADOW_OFFSET, y + SHADOW_OFFSET);

			// Name
			graphics.setColor(withAlpha(nameColor, alpha));
			graphics.drawString(senderPart, x, y);

			// Message text
			graphics.setColor(withAlpha(msg.getColor(), alpha));
			graphics.drawString(messagePart, x + senderWidth, y);
		}
		else
		{
			String displayText;
			if (senderName != null)
			{
				displayText = senderName + ": " + msg.getText();
			}
			else
			{
				displayText = msg.getText();
			}
			if (fm.stringWidth(displayText) > maxWidth)
			{
				displayText = truncate(displayText, fm, maxWidth);
			}

			// Shadow
			graphics.setColor(shadowColor);
			graphics.drawString(displayText, x + SHADOW_OFFSET, y + SHADOW_OFFSET);

			// Main text
			graphics.setColor(withAlpha(msg.getColor(), alpha));
			graphics.drawString(displayText, x, y);
		}
	}

	private String truncate(String text, FontMetrics fm, int maxWidth)
	{
		String ellipsis = "...";
		int availableWidth = maxWidth - fm.stringWidth(ellipsis);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++)
		{
			if (fm.stringWidth(sb.toString() + text.charAt(i)) > availableWidth)
			{
				break;
			}
			sb.append(text.charAt(i));
		}
		return sb + ellipsis;
	}

	private String getTypedText(boolean collapsed)
	{
		if (!collapsed || !config.showTypingInput())
		{
			return null;
		}

		String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
		if (typed == null || typed.isEmpty())
		{
			return null;
		}

		return typed;
	}

	private int calculateTypingInputY(int lineHeight)
	{
		int canvasHeight = client.getCanvasHeight();
		int anchorY = canvasHeight - 22;

		int splitPmY = getSplitPmTopY(canvasHeight);
		if (splitPmY > 0)
		{
			anchorY = Math.min(anchorY, splitPmY);
		}

		return anchorY - PADDING_BOTTOM;
	}

	private int calculateBaseY(int lineHeight, int messageCount, boolean hasTypingLine)
	{
		int canvasHeight = client.getCanvasHeight();
		int chatboxTop;

		if (!isChatboxCollapsed())
		{
			Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
			if (chatArea != null)
			{
				Rectangle bounds = chatArea.getBounds();
				chatboxTop = (bounds != null) ? bounds.y : canvasHeight - 165;
			}
			else
			{
				chatboxTop = canvasHeight - 165;
			}
		}
		else
		{
			chatboxTop = canvasHeight - 22;
		}

		// If split private chat messages are visible, position above them too
		int splitPmY = getSplitPmTopY(canvasHeight);
		if (splitPmY > 0)
		{
			chatboxTop = Math.min(chatboxTop, splitPmY);
		}

		int typingOffset = hasTypingLine ? lineHeight + LINE_SPACING : 0;
		int totalHeight = messageCount * (lineHeight + LINE_SPACING) - LINE_SPACING;

		return chatboxTop - totalHeight - PADDING_BOTTOM - typingOffset;
	}

	/**
	 * Returns the top Y of the topmost visible split PM message, or -1 if none
	 * are on screen. Checks PM1–PM5 individually since the container widget's
	 * bounds may be unreliable (parked at y≈0 even when messages are showing).
	 */
	private int getSplitPmTopY(int canvasHeight)
	{
		int[] pmWidgetIds = {
			InterfaceID.PmChat.PM1,
			InterfaceID.PmChat.PM2,
			InterfaceID.PmChat.PM3,
			InterfaceID.PmChat.PM4,
			InterfaceID.PmChat.PM5
		};

		int topY = -1;
		for (int id : pmWidgetIds)
		{
			Widget pm = client.getWidget(id);
			if (pm == null)
			{
				continue;
			}
			Rectangle bounds = pm.getBounds();
			if (bounds == null)
			{
				continue;
			}
			// Skip empty slots — inactive PM slots collapse to zero height
			if (bounds.height <= 0)
			{
				continue;
			}
			// Only count widgets positioned in the lower half of the screen
			if (bounds.y < canvasHeight / 2)
			{
				continue;
			}
			if (topY < 0 || bounds.y < topY)
			{
				topY = bounds.y;
			}
		}
		return topY;
	}

	private boolean isChatboxCollapsed()
	{
		Widget chatArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
		if (chatArea == null)
		{
			return true;
		}
		return chatArea.isHidden();
	}

	private boolean isChatboxInputHidden()
	{
		Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (chatboxInput == null)
		{
			return true;
		}
		return chatboxInput.isHidden();
	}

	private static Color withAlpha(Color color, float alpha)
	{
		return new Color(
			color.getRed(),
			color.getGreen(),
			color.getBlue(),
			Math.round(alpha * 255)
		);
	}
}
