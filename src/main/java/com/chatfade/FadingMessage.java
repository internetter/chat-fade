package com.chatfade;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;

@Data
@Builder
public class FadingMessage
{
	private final String senderName;
	private String text;
	private final ChatMessageType type;
	private long timestamp;
	private final Color color;
	private List<ColorSpan> colorSpans;
	private MessageNode messageNode;

	/**
	 * Id of the originating {@link MessageNode}, or -1 when unknown. Kept separately from
	 * {@link #messageNode} because that reference is released once the text stops changing,
	 * while the Chat Filter integration needs to identify this message for its whole lifetime.
	 */
	private int messageId;

	@Builder.Default
	private int count = 1;

	/**
	 * The message exactly as the game delivered it, before any of our processing.
	 *
	 * <p>Used to tell whether the Chat Filter plugin actually rewrote a message. Without it
	 * we cannot distinguish "the filter censored this" from "the filter left it alone", and
	 * blindly adopting the rebuilt text clobbers rewrites made by other plugins — notably
	 * chat commands replacing "!kc" with the real kill count.
	 */
	private String rawText;

	/**
	 * Rank, title and account-type badges that precede the sender's name. These always
	 * appear as a prefix in chat, so they need no positioning beyond drawing in order.
	 */
	private final List<BufferedImage> senderIcons;

	/**
	 * Whether the built-in message filter rewrote this message.
	 *
	 * <p>Stops the Chat Filter plugin integration from overwriting our censored text with the
	 * chatbox's own version on the next rebuild, which would otherwise undo the substitution
	 * and briefly show the wording we had just starred out.
	 */
	private final boolean censored;

	/**
	 * Clan or friends chat channel this came through, without brackets, or null.
	 *
	 * <p>Kept apart from the sender name so it can be drawn in the message type's own colour
	 * rather than the username colour.
	 */
	private final String channelName;
}
