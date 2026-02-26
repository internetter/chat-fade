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
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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
		if (config.onlyWhenCollapsed() && !isChatboxCollapsed())
		{
			return null;
		}

		plugin.pruneExpiredMessages();

		List<FadingMessage> messages = plugin.getMessages();
		if (messages.isEmpty())
		{
			return null;
		}

		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, config.fontSize());
		graphics.setFont(font);
		graphics.setRenderingHint(
			RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON
		);

		FontMetrics fm = graphics.getFontMetrics();
		int lineHeight = fm.getHeight();

		int baseY = calculateBaseY(lineHeight, messages.size());
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

			String displayText = formatDisplayText(msg, fm);

			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

			// Shadow
			graphics.setColor(new Color(0, 0, 0, Math.round(alpha * 255)));
			graphics.drawString(displayText, baseX + SHADOW_OFFSET, y + SHADOW_OFFSET);

			// Main text
			graphics.setColor(withAlpha(msg.getColor(), alpha));
			graphics.drawString(displayText, baseX, y);

			y += lineHeight + LINE_SPACING;
		}

		graphics.setComposite(originalComposite);

		return null;
	}

	private String formatDisplayText(FadingMessage msg, FontMetrics fm)
	{
		String text;
		if (msg.getSenderName() != null)
		{
			text = msg.getSenderName() + ": " + msg.getText();
		}
		else
		{
			text = msg.getText();
		}

		int maxWidth = config.maxMessageWidth();
		if (fm.stringWidth(text) > maxWidth)
		{
			String ellipsis = "...";
			int ellipsisWidth = fm.stringWidth(ellipsis);
			int availableWidth = maxWidth - ellipsisWidth;

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < text.length(); i++)
			{
				if (fm.stringWidth(sb.toString() + text.charAt(i)) > availableWidth)
				{
					break;
				}
				sb.append(text.charAt(i));
			}
			text = sb + ellipsis;
		}

		return text;
	}

	private int calculateBaseY(int lineHeight, int messageCount)
	{
		int canvasHeight = client.getCanvasHeight();
		int chatboxTop;

		if (!isChatboxCollapsed())
		{
			Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_INPUT);
			if (chatboxInput != null)
			{
				Rectangle bounds = chatboxInput.getBounds();
				if (bounds != null)
				{
					chatboxTop = bounds.y;
				}
				else
				{
					chatboxTop = canvasHeight - 165;
				}
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

		int totalHeight = messageCount * (lineHeight + LINE_SPACING) - LINE_SPACING;

		return chatboxTop - totalHeight - PADDING_BOTTOM;
	}

	private boolean isChatboxCollapsed()
	{
		Widget chatboxInput = client.getWidget(WidgetInfo.CHATBOX_INPUT);
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
