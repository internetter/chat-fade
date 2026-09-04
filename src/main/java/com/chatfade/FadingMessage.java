package com.chatfade;

import java.awt.Color;
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
	private final long timestamp;
	private final Color color;
	private List<ColorSpan> colorSpans;
	private MessageNode messageNode;

	/**
	 * Id of the originating {@link MessageNode}, or -1 when unknown. Kept separately from
	 * {@link #messageNode} because that reference is released once the text stops changing,
	 * while the Chat Filter integration needs to identify this message for its whole lifetime.
	 */
	private final int messageId;
}
