package com.chatfade;

import java.awt.Color;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Covers {@link ChatFadePlugin#parseColorSpans}, which backs the "Preserve In-Game Colors"
 * option. Issue #21: messages coloured purely with {@code @name@} tokens carry no
 * {@code <col=...>} tag, so the parser used to bail out and fall back to the flat per-type
 * colour.
 */
public class ColorSpanParsingTest
{
	private static final Color FALLBACK = new Color(100, 200, 255);

	// ── Expanded macros arrive as ordinary col tags ─────────

	@Test
	public void parsesExpandedMacroAsColour()
	{
		// What Client.macroExpand turns "@mes_hl_red@You have been frozen!</col>" into.
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=ff3030>You have been frozen!</col>", FALLBACK);

		assertNotNull("coloured message should produce spans", spans);
		assertEquals(1, spans.size());
		assertEquals("You have been frozen!", spans.get(0).getText());
		assertEquals(new Color(0xFF3030), spans.get(0).getColor());
	}

	@Test
	public void closingTagRestoresFallback()
	{
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=ff0000>warning</col> and the rest", FALLBACK);

		assertNotNull(spans);
		assertEquals(2, spans.size());
		assertEquals("warning", spans.get(0).getText());
		assertEquals(new Color(0xFF0000), spans.get(0).getColor());
		assertEquals(" and the rest", spans.get(1).getText());
		assertEquals(FALLBACK, spans.get(1).getColor());
	}

	// ── Existing <col=...> behaviour must be preserved ──────

	@Test
	public void stillParsesAngleBracketTags()
	{
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"a<col=ff0000>b</col>c", FALLBACK);

		assertNotNull(spans);
		assertEquals(3, spans.size());
		assertEquals("a", spans.get(0).getText());
		assertEquals(FALLBACK, spans.get(0).getColor());
		assertEquals("b", spans.get(1).getText());
		assertEquals(new Color(0xFF0000), spans.get(1).getColor());
		assertEquals("c", spans.get(2).getText());
		assertEquals(FALLBACK, spans.get(2).getColor());
	}

	@Test
	public void skipsNonColourTags()
	{
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=00ff00>hi<img=5> there", FALLBACK);

		assertNotNull(spans);
		assertEquals(1, spans.size());
		assertEquals("hi there", spans.get(0).getText());
	}

	@Test
	public void returnsNullWhenNoColourPresent()
	{
		assertNull(ChatFadePlugin.parseColorSpans("just a plain message", FALLBACK));
	}

	@Test
	public void returnsNullWhenEverythingIsFallbackColoured()
	{
		// A message whose only tag re-states the fallback is not worth span rendering.
		String sameAsFallback = String.format("<col=%06x>text</col>", FALLBACK.getRGB() & 0xFFFFFF);
		assertNull(ChatFadePlugin.parseColorSpans(sameAsFallback, FALLBACK));
	}

	// ── Player text must not be mistaken for markup ─────────

	@Test
	public void doesNotTreatPlayerAtTextAsColour()
	{
		assertNull(ChatFadePlugin.parseColorSpans("@bob@ what's up", FALLBACK));
		assertNull(ChatFadePlugin.parseColorSpans("@bob_smith@ what's up", FALLBACK));
	}

	@Test
	public void keepsPlayerAtTextInsideAColouredMessage()
	{
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=ff0000>ping @bob@ and @bob_smith@ now</col>", FALLBACK);

		assertNotNull(spans);
		assertEquals(1, spans.size());
		assertEquals("ping @bob@ and @bob_smith@ now", spans.get(0).getText());
	}

	@Test
	public void keepsEscapedPrintablesInsideColouredSpans()
	{
		// The span path must restore <at> too, or a coloured message loses its @ signs
		// even though the plain-text path keeps them.
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=ff0000>ping <at>bob<at> now</col>", FALLBACK);

		assertNotNull(spans);
		assertEquals(1, spans.size());
		assertEquals("ping @bob@ now", spans.get(0).getText());
	}
}
