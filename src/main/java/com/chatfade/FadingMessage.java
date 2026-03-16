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
}
