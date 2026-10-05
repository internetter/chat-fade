package com.chatfade;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Highlight offsets are measured against {@link ChatFadePlugin#toDisplayText} but painted onto
 * the spans from {@link ChatFadePlugin#parseColorSpans}, so the two must produce identical
 * text. Any disagreement shifts every highlight on the message.
 *
 * <p>They drifted apart when the CA_ID fix added a trim to toDisplayText: removing a leading
 * icon tag leaves the space that followed it, which the display text dropped and the spans
 * kept. Reported as #29 by @cerkie.
 */
public class SpanAlignmentTest
{
	private static final BufferedImage ICON = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

	/** The text the spans would render, which must equal the display text character for character. */
	private static String spanText(String raw)
	{
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(raw, Color.WHITE, i -> ICON);
		if (spans == null)
		{
			return ChatFadePlugin.toDisplayText(raw);
		}

		StringBuilder sb = new StringBuilder();
		spans.stream().filter(s -> !s.isIcon()).forEach(s -> sb.append(s.getText()));
		return sb.toString();
	}

	private static void assertAligned(String raw)
	{
		assertEquals("spans must render the same characters as the display text",
			ChatFadePlugin.toDisplayText(raw), spanText(raw));
	}

	@Test
	public void alignsWithNoIcon()
	{
		assertAligned("Bob received a new collection log item: Granite dust (163/1717)");
	}

	@Test
	public void alignsWithOneLeadingIcon()
	{
		assertAligned("<img=19> Bob received a new collection log item: Granite dust (163/1717)");
	}

	@Test
	public void alignsWithTwoLeadingIcons()
	{
		// A clan broadcast can carry a rank badge and an account-type badge. Trimming only the
		// first text span left this one a character out.
		assertAligned("<img=19> <img=42> Bob received a new collection log item: Granite dust (163/1717)");
	}

	@Test
	public void alignsWithALeadingIconInsideAColourTag()
	{
		assertAligned("<col=ff0000><img=19> Bob received a drop: Abyssal whip (1,200,000 coins)</col>");
	}

	@Test
	public void alignsWhenTheMessageContainsANewline()
	{
		assertAligned("<img=19> Bob received a drop:\nAbyssal whip (1,200,000 coins)");
	}

	@Test
	public void highlightLandsOnTheWholeItemNameAfterALeadingIcon()
	{
		String raw = "<img=19> Bob received a new collection log item: Granite dust (163/1717)";
		String cleaned = ChatFadePlugin.toDisplayText(raw);
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(raw, Color.WHITE, i -> ICON);

		LootBroadcast.Match match = LootBroadcast.parseCollectionLog(cleaned);
		assertNotNull(match);
		assertEquals("Granite dust", cleaned.substring(match.start, match.end));

		List<ColorSpan> highlighted = Highlighter.highlightRange(
			spans, cleaned, Color.WHITE, match.start, match.end, Color.YELLOW, true);

		ColorSpan yellow = highlighted.stream()
			.filter(s -> Color.YELLOW.equals(s.getColor()))
			.findFirst()
			.orElse(null);
		assertNotNull("the item name should have been recoloured", yellow);
		assertEquals("Granite dust", yellow.getText());
	}

	@Test
	public void highlightLandsCorrectlyAfterTwoLeadingIcons()
	{
		String raw = "<img=19> <img=42> Bob received a new collection log item: Granite dust (163/1717)";
		String cleaned = ChatFadePlugin.toDisplayText(raw);
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(raw, Color.WHITE, i -> ICON);

		LootBroadcast.Match match = LootBroadcast.parseCollectionLog(cleaned);
		assertNotNull(match);

		List<ColorSpan> highlighted = Highlighter.highlightRange(
			spans, cleaned, Color.WHITE, match.start, match.end, Color.YELLOW, true);

		ColorSpan yellow = highlighted.stream()
			.filter(s -> Color.YELLOW.equals(s.getColor()))
			.findFirst()
			.orElse(null);
		assertNotNull("the item name should have been recoloured", yellow);
		assertEquals("Granite dust", yellow.getText());
	}
}
