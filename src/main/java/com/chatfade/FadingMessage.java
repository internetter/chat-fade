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
	/** Refreshed when a duplicate collapses onto this message, restarting its fade. */
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

	/**
	 * The message exactly as the game delivered it, before any of our processing.
	 *
	 * <p>Used to tell whether the Chat Filter plugin actually rewrote a message. Without it
	 * we cannot distinguish "the filter censored this" from "the filter left it alone", and
	 * blindly adopting the rebuilt text clobbers rewrites made by other plugins — notably
	 * chat commands replacing "!kc" with the real kill count.
	 */
	private final String rawText;

	/**
	 * Rank, title and account-type badges that precede the sender's name. These always
	 * appear as a prefix in chat, so they need no positioning beyond drawing in order.
	 */
	private final List<BufferedImage> senderIcons;

	/**
	 * How many identical messages this line stands for, rendered as a trailing "(N)".
	 *
	 * <p>Counted here rather than read back out of the chatbox so collapsing does not depend
	 * on the Chat Filter plugin being installed or on its rebuild reporting anything.
	 */
	@Builder.Default
	private int count = 1;

	/**
	 * Whether this message matched the watch list, so it can be held on screen longer.
	 *
	 * <p>Recorded independently of "Only Show Matching". Matching decides how long a message
	 * lasts; the toggle decides whether unmatched ones appear at all. Tying the two together
	 * would make the longer duration meaningless, since with the toggle on every message
	 * shown has matched by definition.
	 */
	private final boolean watched;

	/**
	 * Whether this message still has an {@code <img=N>} that did not resolve.
	 *
	 * <p>Kept so the per-tick pass can rebuild the spans once the sprite exists, rather than
	 * the icon being lost for good because the message happened to arrive before the plugin
	 * that owns it finished registering.
	 */
	private boolean pendingIcons;

	/**
	 * The message text the spans were built from, so they can be rebuilt later without the
	 * MessageNode, which a censored message deliberately does not keep.
	 */
	private String spanSource;

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
