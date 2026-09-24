package com.chatfade;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.ui.JagexColors;
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
	private static final int ICON_SPACING = 2;

	/** The chatbox draws channel brackets in its plain text colour. */
	private static final Color CHANNEL_BRACKET_COLOR = Color.WHITE;

	/** How far a wrapped continuation line sits in from its first line. */
	private static final int WRAP_INDENT = 12;

	private static final String ELLIPSIS = "...";

	// Placeholder shown in the chatbox input when Key Remapping's "Press Enter to Chat" is active.
	private static final String PRESS_ENTER_TO_CHAT = "Press Enter to Chat...";

	private final Client client;
	private final ChatFadePlugin plugin;
	private final ChatFadeConfig config;
	private final ChatColorConfig chatColorConfig;

	// Sticky: once we observe the "Press Enter to Chat" prompt we remember the user has it enabled,
	// because the prompt text disappears the moment they press Enter and can't be re-detected.
	private boolean keyRemapping = false;

	@Inject
	public ChatFadeOverlay(Client client, ChatFadePlugin plugin, ChatFadeConfig config,
		ChatColorConfig chatColorConfig)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.chatColorConfig = chatColorConfig;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	/**
	 * Chooses whether interfaces cover the overlay.
	 *
	 * <p>UNDER_WIDGETS draws under every interface but still above the game scene, so chat
	 * stays visible while playing and the bank, Grand Exchange and similar simply cover it.
	 *
	 * <p>The overlay manager reads the layer only when an overlay is registered, so the
	 * plugin re-adds this overlay after calling it.
	 */
	void setDrawUnderInterfaces(boolean underInterfaces)
	{
		setLayer(underInterfaces ? OverlayLayer.UNDER_WIDGETS : OverlayLayer.ABOVE_WIDGETS);
	}

	/**
	 * Chooses between positioning ourselves above the chatbox and letting RuneLite place us.
	 *
	 * <p>A DYNAMIC overlay reports no bounds, which is why overlays anchored bottom-left end
	 * up drawn on top of the chat text. Giving it a real anchor means the overlay manager
	 * knows our size, stacks other overlays around us, and lets the box be dragged.
	 */
	void setAnchored(boolean anchored)
	{
		setPosition(anchored ? OverlayPosition.BOTTOM_LEFT : OverlayPosition.DYNAMIC);
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

		if (messages.isEmpty() && typedText == null && !chatInputEnabled())
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
		boolean hasTypingLine = (keyRemapping && chatInputEnabled()) || typedText != null;

		// Anchored mode hands positioning to RuneLite: the graphics context is already
		// translated to our box, so everything is drawn relative to (0, 0) and the size is
		// reported back. That is what lets other overlays stack around us instead of on top.
		boolean anchored = config.anchoredOverlay();

		int baseX = anchored ? 0 : PADDING_LEFT + config.xOffset();
		int maxWidth = config.maxMessageWidth();
		int indent = config.wrapMessages() ? WRAP_INDENT : 0;

		// Lay messages out newest-first so the budget keeps the most recent ones, then draw
		// oldest-first. The budget counts *lines*, so a message that wraps costs two slots.
		List<FadingMessage> visible = new ArrayList<>();
		List<List<List<ColorSpan>>> layouts = new ArrayList<>();
		int totalLines = 0;
		for (int i = messages.size() - 1; i >= 0; i--)
		{
			FadingMessage msg = messages.get(i);
			List<List<ColorSpan>> lines = layout(msg, fm, maxWidth, indent);
			if (!visible.isEmpty() && totalLines + lines.size() > config.maxMessages())
			{
				break;
			}
			visible.add(0, msg);
			layouts.add(0, lines);
			totalLines += lines.size();
		}

		boolean showTyping = config.showTypingInput() && hasTypingLine;
		int baseY = anchored
			? fm.getAscent()
			: calculateBaseY(lineHeight, totalLines, showTyping) + config.yOffset();

		long now = System.currentTimeMillis();
		long displayMs = config.displayDuration() * 1000L;
		long fadeMs = config.fadeDuration() * 1000L;

		Composite originalComposite = graphics.getComposite();

		int y = baseY;
		for (int m = 0; m < visible.size(); m++)
		{
			FadingMessage msg = visible.get(m);
			List<List<ColorSpan>> lines = layouts.get(m);
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
				y += lines.size() * (lineHeight + LINE_SPACING);
				continue;
			}

			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			for (int l = 0; l < lines.size(); l++)
			{
				drawWrappedLine(graphics, fm, lines.get(l), baseX + (l == 0 ? 0 : indent), y, alpha);
				y += lineHeight + LINE_SPACING;
			}
		}

		// Render typing input line
		if (hasTypingLine && config.showTypingInput())
		{
			graphics.setComposite(originalComposite);

			int caretWidth = fm.stringWidth("> ");
			int textX = baseX + caretWidth;
			int inputY = anchored ? y : calculateTypingInputY(lineHeight) + config.yOffset();

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
			int inputWidth = config.maxMessageWidth() - caretWidth;
			String inputDisplay = fm.stringWidth(raw) > inputWidth ? truncate(raw, fm, inputWidth) : raw;

			// Shadow
			graphics.setColor(Color.BLACK);
			graphics.drawString(inputDisplay, textX + SHADOW_OFFSET, inputY + SHADOW_OFFSET);

			// Main text
			graphics.setColor(Color.WHITE);
			graphics.drawString(inputDisplay, textX, inputY);
		}

		graphics.setComposite(originalComposite);

		if (!anchored)
		{
			return null;
		}

		// Width is the widest line actually drawn, so the box hugs the text rather than
		// always reserving the configured maximum.
		int width = 0;
		for (int m = 0; m < layouts.size(); m++)
		{
			List<List<ColorSpan>> lines = layouts.get(m);
			for (int l = 0; l < lines.size(); l++)
			{
				int lineWidth = (l == 0 ? 0 : indent);
				for (ColorSpan piece : lines.get(l))
				{
					lineWidth += pieceWidth(piece, fm);
				}
				width = Math.max(width, lineWidth);
			}
		}

		int renderedLines = totalLines + (showTyping ? 1 : 0);
		if (showTyping)
		{
			width = Math.max(width, config.maxMessageWidth());
		}
		if (renderedLines == 0)
		{
			return null;
		}

		return new Dimension(width, renderedLines * (lineHeight + LINE_SPACING));
	}

	/**
	 * Lays a message out into the lines it will occupy.
	 *
	 * <p>With wrapping off this still returns a list, just always of one line, clipped with
	 * an ellipsis — so the caller never has to care which mode is active.
	 */
	private List<List<ColorSpan>> layout(FadingMessage msg, FontMetrics fm, int maxWidth, int indent)
	{
		List<ColorSpan> pieces = buildPieces(msg);
		List<List<ColorSpan>> lines = wrapPieces(pieces, fm, maxWidth, maxWidth - indent);

		if (config.wrapMessages() || lines.size() <= 1)
		{
			return lines;
		}

		List<ColorSpan> first = new ArrayList<>(lines.get(0));

		// Make room for the ellipsis by shortening from the end of the line, so the result
		// still fits the configured width rather than overflowing it by three characters.
		int ellipsisWidth = fm.stringWidth(ELLIPSIS);
		int used = 0;
		for (ColorSpan piece : first)
		{
			used += pieceWidth(piece, fm);
		}

		while (used + ellipsisWidth > maxWidth && !first.isEmpty())
		{
			int lastIndex = first.size() - 1;
			ColorSpan last = first.get(lastIndex);
			if (last.isIcon() || last.getText().isEmpty())
			{
				used -= pieceWidth(last, fm);
				first.remove(lastIndex);
				continue;
			}

			String shortened = last.getText().substring(0, last.getText().length() - 1);
			used -= fm.charWidth(last.getText().charAt(last.getText().length() - 1));
			if (shortened.isEmpty())
			{
				first.remove(lastIndex);
			}
			else
			{
				first.set(lastIndex, new ColorSpan(shortened, last.getColor()));
			}
		}

		first.add(new ColorSpan(ELLIPSIS, msg.getColor()));
		return java.util.Collections.singletonList(first);
	}

	/**
	 * Flattens a message into the ordered pieces that make up its visual line: channel,
	 * sender badges, sender name, then the body.
	 *
	 * <p>Everything downstream — wrapping, measuring and drawing — works on this one list, so
	 * the colour rules live here and nowhere else.
	 */
	private List<ColorSpan> buildPieces(FadingMessage msg)
	{
		List<ColorSpan> pieces = new ArrayList<>();

		// With in-game colours preserved the channel matches the chatbox: plain brackets
		// around a name in the channel-name colour. Otherwise it takes the type's colour.
		String channel = msg.getChannelName();
		if (channel != null)
		{
			if (config.preserveInlineColors())
			{
				pieces.add(new ColorSpan("[", CHANNEL_BRACKET_COLOR));
				pieces.add(new ColorSpan(channel, channelNameColor(msg.getType())));
				pieces.add(new ColorSpan("] ", CHANNEL_BRACKET_COLOR));
			}
			else
			{
				pieces.add(new ColorSpan("[" + channel + "] ", msg.getColor()));
			}
		}

		List<BufferedImage> icons = msg.getSenderIcons();
		if (icons != null)
		{
			for (BufferedImage icon : icons)
			{
				pieces.add(ColorSpan.icon(icon));
			}
		}

		String senderName = msg.getSenderName();
		if (senderName != null)
		{
			boolean isNpcMessage = msg.getType() == net.runelite.api.ChatMessageType.DIALOG
				|| msg.getType() == net.runelite.api.ChatMessageType.MESBOX;
			Color nameColor = isNpcMessage && config.colorizeNpcNames() ? config.npcNameColor()
				: (!isNpcMessage && config.colorizeUsernames()) ? config.usernameColor()
				: msg.getColor();
			pieces.add(new ColorSpan(senderName + ": ", nameColor));
		}

		List<ColorSpan> spans = msg.getColorSpans();
		if (spans != null && !spans.isEmpty())
		{
			pieces.addAll(spans);
		}
		else
		{
			pieces.add(new ColorSpan(msg.getText(), msg.getColor()));
		}

		return pieces;
	}

	/**
	 * Breaks a message's pieces into visual lines.
	 *
	 * <p>Continuation lines are narrower by the indent, so a wrapped message sits visibly
	 * under the one it belongs to. Breaks happen at spaces; a single word too wide for a line
	 * is broken by character so an over-long word cannot loop forever.
	 */
	static List<List<ColorSpan>> wrapPieces(List<ColorSpan> pieces, FontMetrics fm,
		int firstWidth, int contWidth)
	{
		List<List<ColorSpan>> lines = new ArrayList<>();
		List<ColorSpan> current = new ArrayList<>();
		int used = 0;
		int limit = firstWidth;

		for (ColorSpan piece : pieces)
		{
			if (piece.isIcon())
			{
				int iconWidth = piece.getImage().getWidth() + ICON_SPACING;
				if (used + iconWidth > limit && !current.isEmpty())
				{
					lines.add(current);
					current = new ArrayList<>();
					used = 0;
					limit = contWidth;
				}
				current.add(piece);
				used += iconWidth;
				continue;
			}

			String text = piece.getText();
			StringBuilder buf = new StringBuilder();
			int i = 0;
			while (i < text.length())
			{
				int end = nextToken(text, i);
				String token = text.substring(i, end);
				int tokenWidth = fm.stringWidth(token);

				if (used + tokenWidth > limit)
				{
					if (buf.length() > 0 || !current.isEmpty())
					{
						if (buf.length() > 0)
						{
							current.add(new ColorSpan(buf.toString(), piece.getColor()));
							buf.setLength(0);
						}
						lines.add(current);
						current = new ArrayList<>();
						used = 0;
						limit = contWidth;

						// A line never starts with the space that caused the break.
						int lead = 0;
						while (lead < token.length() && token.charAt(lead) == ' ')
						{
							lead++;
						}
						token = token.substring(lead);
						tokenWidth = fm.stringWidth(token);
					}

					if (tokenWidth > limit)
					{
						for (int c = 0; c < token.length(); c++)
						{
							int charWidth = fm.charWidth(token.charAt(c));
							if (used + charWidth > limit && (buf.length() > 0 || !current.isEmpty()))
							{
								if (buf.length() > 0)
								{
									current.add(new ColorSpan(buf.toString(), piece.getColor()));
									buf.setLength(0);
								}
								lines.add(current);
								current = new ArrayList<>();
								used = 0;
								limit = contWidth;
							}
							buf.append(token.charAt(c));
							used += charWidth;
						}
						i = end;
						continue;
					}
				}

				buf.append(token);
				used += tokenWidth;
				i = end;
			}

			if (buf.length() > 0)
			{
				current.add(new ColorSpan(buf.toString(), piece.getColor()));
			}
		}

		if (!current.isEmpty() || lines.isEmpty())
		{
			lines.add(current);
		}
		return lines;
	}

	/** @return the width a piece occupies, text or icon */
	private static int pieceWidth(ColorSpan piece, FontMetrics fm)
	{
		return piece.isIcon()
			? piece.getImage().getWidth() + ICON_SPACING
			: fm.stringWidth(piece.getText());
	}

	/** @return index just past the next run of spaces plus the word that follows them */
	private static int nextToken(String text, int from)
	{
		int i = from;
		while (i < text.length() && text.charAt(i) == ' ')
		{
			i++;
		}
		while (i < text.length() && text.charAt(i) != ' ')
		{
			i++;
		}
		return i == from ? from + 1 : i;
	}

	/** Draws one already-wrapped line. */
	private void drawWrappedLine(Graphics2D graphics, FontMetrics fm, List<ColorSpan> line,
		int x, int y, float alpha)
	{
		int currentX = x;
		for (ColorSpan piece : line)
		{
			if (piece.isIcon())
			{
				int drawn = drawIcon(graphics, piece.getImage(), currentX, y, fm, Integer.MAX_VALUE);
				currentX += Math.max(0, drawn);
			}
			else
			{
				currentX += drawPart(graphics, fm, piece.getText(), currentX, y,
					piece.getColor(), alpha);
			}
		}
	}

	/** Draws a run of text with the overlay's shadow. @return the width drawn */
	private int drawPart(Graphics2D graphics, FontMetrics fm, String text, int x, int y,
		Color color, float alpha)
	{
		graphics.setColor(new Color(0, 0, 0, Math.round(alpha * 255)));
		graphics.drawString(text, x + SHADOW_OFFSET, y + SHADOW_OFFSET);
		graphics.setColor(withAlpha(color, alpha));
		graphics.drawString(text, x, y);
		return fm.stringWidth(text);
	}

	/**
	 * The colour the game gives a channel name, honouring RuneLite's Chat Color settings when
	 * they have been customised.
	 *
	 * <p>Uses the transparent-chatbox palette regardless of the player's chatbox mode: the
	 * overlay is drawn over the game world, where the opaque palette's dark blue on black
	 * brackets would be unreadable.
	 */
	private Color channelNameColor(net.runelite.api.ChatMessageType type)
	{
		Color custom;
		switch (type)
		{
			case FRIENDSCHAT:
				custom = chatColorConfig.transparentFriendsChatChannelName();
				break;
			case CLAN_GUEST_CHAT:
				custom = chatColorConfig.transparentClanChannelGuestName();
				break;
			default:
				custom = chatColorConfig.transparentClanChannelName();
				break;
		}
		return custom != null ? custom : JagexColors.CHAT_FC_NAME_TRANSPARENT_BACKGROUND;
	}

	/**
	 * Draws the badges that precede a sender's name.
	 *
	 * @return the total width consumed
	 */
	private int drawSenderIcons(Graphics2D graphics, List<BufferedImage> icons,
		int x, int y, FontMetrics fm, int maxWidth)
	{
		if (icons == null || icons.isEmpty())
		{
			return 0;
		}

		int used = 0;
		for (BufferedImage icon : icons)
		{
			int width = drawIcon(graphics, icon, x + used, y, fm, maxWidth - used);
			if (width < 0)
			{
				break;
			}
			used += width;
		}
		return used;
	}

	/**
	 * Draws an inline chat icon on the text baseline.
	 *
	 * <p>Icons are drawn at their natural size and sit slightly above the baseline so they
	 * line up with the text rather than hanging below it.
	 *
	 * @return the width consumed including trailing spacing, or -1 when it will not fit
	 */
	private int drawIcon(Graphics2D graphics, BufferedImage icon, int x, int y,
		FontMetrics fm, int remainingWidth)
	{
		if (icon == null)
		{
			return 0;
		}

		int width = icon.getWidth() + ICON_SPACING;
		if (width > remainingWidth)
		{
			return -1;
		}

		// Centre the icon on the text's x-height so it reads as part of the line.
		int top = y - fm.getAscent() + Math.max(0, (fm.getAscent() - icon.getHeight()) / 2);
		graphics.drawImage(icon, x, top, null);
		return width;
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
		Widget chatboxInput = getChatboxInput();
		return chatboxInput == null || chatboxInput.isHidden();
	}

	private boolean chatInputEnabled()
	{
		Widget chatboxInput = getChatboxInput();
		return chatboxInput != null && !chatboxInput.getText().contains(PRESS_ENTER_TO_CHAT);
	}

	/**
	 * Fetches the chatbox input widget, recording whether the user has Key Remapping's
	 * "Press Enter to Chat" enabled as a side effect (see {@link #keyRemapping}).
	 */
	private Widget getChatboxInput()
	{
		Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (chatboxInput != null && chatboxInput.getText().contains(PRESS_ENTER_TO_CHAT))
		{
			keyRemapping = true;
		}
		return chatboxInput;
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
