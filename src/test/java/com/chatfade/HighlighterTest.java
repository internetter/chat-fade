package com.chatfade;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * The one invariant that keeps highlighting from turning into a precedence matrix:
 * the game's colour always wins, and only text still at the fallback colour is repainted.
 */
public class HighlighterTest
{
	private static final Color FALLBACK = new Color(100, 200, 255);
	private static final Color GAME = new Color(0xFF0000);
	private static final Color TIER = new Color(0xFF66B2);

	private static String textOf(List<ColorSpan> spans)
	{
		StringBuilder sb = new StringBuilder();
		spans.forEach(s -> sb.append(s.getText()));
		return sb.toString();
	}

	// ── Uncoloured messages ─────────────────────────────────

	@Test
	public void highlightsRangeWhenThereAreNoExistingSpans()
	{
		String text = "Bob received a drop: Whip (1,000 coins).";
		int start = text.indexOf("Whip");
		int end = text.indexOf(')') + 1;

		List<ColorSpan> out = Highlighter.highlightRange(null, text, FALLBACK, start, end, TIER);

		assertNotNull(out);
		assertEquals(3, out.size());
		assertEquals("Bob received a drop: ", out.get(0).getText());
		assertEquals(FALLBACK, out.get(0).getColor());
		assertEquals("Whip (1,000 coins)", out.get(1).getText());
		assertEquals(TIER, out.get(1).getColor());
		assertEquals(".", out.get(2).getText());
		assertEquals(FALLBACK, out.get(2).getColor());
	}

	@Test
	public void reassembledTextIsUnchanged()
	{
		// The renderer draws span text verbatim, so highlighting must never alter content.
		String text = "Bob received a drop: Whip (1,000 coins).";
		List<ColorSpan> out = Highlighter.highlightRange(null, text, FALLBACK, 21, 39, TIER);

		assertEquals(text, textOf(out));
	}

	// ── The invariant ───────────────────────────────────────

	@Test
	public void neverOverridesAColourTheGameChose()
	{
		// The middle span was coloured by the game and must survive untouched, even though
		// the highlight range covers it.
		List<ColorSpan> spans = Arrays.asList(
			new ColorSpan("plain ", FALLBACK),
			new ColorSpan("GAME", GAME),
			new ColorSpan(" tail", FALLBACK));

		List<ColorSpan> out = Highlighter.highlightRange(spans, "plain GAME tail", FALLBACK,
			0, 15, TIER);

		assertEquals("plain GAME tail", textOf(out));
		ColorSpan gameSpan = out.stream().filter(s -> "GAME".equals(s.getText())).findFirst().orElse(null);
		assertNotNull("the game-coloured span should still exist", gameSpan);
		assertEquals("and keep its colour", GAME, gameSpan.getColor());
	}

	@Test
	public void recolboursOnlyTheUnclaimedPortions()
	{
		List<ColorSpan> spans = Arrays.asList(
			new ColorSpan("aaa", FALLBACK),
			new ColorSpan("bbb", GAME));

		List<ColorSpan> out = Highlighter.highlightRange(spans, "aaabbb", FALLBACK, 0, 6, TIER);

		assertEquals(TIER, out.get(0).getColor());
		assertEquals(GAME, out.get(1).getColor());
	}

	// ── No-ops ──────────────────────────────────────────────

	@Test
	public void returnsInputUnchangedWhenNothingIsRecoloured()
	{
		List<ColorSpan> spans = Arrays.asList(new ColorSpan("all game coloured", GAME));

		assertSame("should not manufacture new spans for a no-op",
			spans, Highlighter.highlightRange(spans, "all game coloured", FALLBACK, 0, 17, TIER));
	}

	@Test
	public void rejectsOutOfBoundsAndEmptyRanges()
	{
		String text = "short";

		assertSame(null, Highlighter.highlightRange(null, text, FALLBACK, -1, 3, TIER));
		assertSame(null, Highlighter.highlightRange(null, text, FALLBACK, 0, 99, TIER));
		assertSame(null, Highlighter.highlightRange(null, text, FALLBACK, 3, 3, TIER));
		assertSame(null, Highlighter.highlightRange(null, null, FALLBACK, 0, 1, TIER));
	}

	@Test
	public void handlesRangeSpanningMultipleUnclaimedSpans()
	{
		List<ColorSpan> spans = Arrays.asList(
			new ColorSpan("one ", FALLBACK),
			new ColorSpan("two ", FALLBACK),
			new ColorSpan("three", FALLBACK));

		List<ColorSpan> out = Highlighter.highlightRange(spans, "one two three", FALLBACK, 4, 8, TIER);

		assertEquals("one two three", textOf(out));
		ColorSpan highlighted = out.stream().filter(s -> TIER.equals(s.getColor())).findFirst().orElse(null);
		assertNotNull(highlighted);
		assertEquals("two ", highlighted.getText());
	}
}
