package com.chatfade;

import java.awt.Color;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Colouring the watched words inside a message (#37, asked for by @cykabass).
 *
 * <p>The ranges are handed to {@link Highlighter}, which recolours spans that concatenate to
 * exactly the text passed in — so an offset that is off by even one character paints the wrong
 * letters. These tests are mostly about the offsets being in the caller's coordinates.
 */
public class WatchedWordHighlightTest
{
	private static final Color FALLBACK = Color.WHITE;
	private static final Color HIGHLIGHT = Color.PINK;

	private static MessageFilter watching(String words, String regex, boolean stripAccents)
	{
		MessageFilter filter = new MessageFilter();
		filter.rebuild(words, regex, "", MessageFilterType.REMOVE_MESSAGE, stripAccents);
		return filter;
	}

	private static String highlighted(MessageFilter watch, String text)
	{
		List<ColorSpan> spans = null;
		for (int[] range : watch.matchRanges(text))
		{
			spans = Highlighter.highlightRange(spans, text, FALLBACK, range[0], range[1], HIGHLIGHT);
		}

		if (spans == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (ColorSpan span : spans)
		{
			if (HIGHLIGHT.equals(span.getColor()))
			{
				sb.append(span.getText());
			}
		}
		return sb.toString();
	}

	@Test
	public void theMatchedWordIsTheTextThatGetsColoured()
	{
		assertEquals("cabbage", highlighted(watching("cabbage", "", false), "selling cabbage cheap"));
	}

	@Test
	public void matchingIsCaseInsensitiveButColoursTheOriginalCasing()
	{
		assertEquals("Cabbage", highlighted(watching("cabbage", "", false), "selling Cabbage cheap"));
	}

	@Test
	public void everyOccurrenceIsColoured()
	{
		assertEquals("cabbagecabbage",
			highlighted(watching("cabbage", "", false), "cabbage and more cabbage"));
	}

	@Test
	public void aWordAtTheVeryStartIsColoured()
	{
		assertEquals("cabbage", highlighted(watching("cabbage", "", false), "cabbage for sale"));
	}

	@Test
	public void aWordAtTheVeryEndIsColoured()
	{
		assertEquals("cabbage", highlighted(watching("cabbage", "", false), "selling cabbage"));
	}

	@Test
	public void regexRangesLandOnWhatTheyMatched()
	{
		assertEquals("50000", highlighted(watching("", "\\d+", false), "price is 50000 gp"));
	}

	@Test
	public void severalWatchedWordsEachGetColoured()
	{
		String out = highlighted(watching("cabbage,turnip", "", false), "cabbage and turnip");
		assertTrue("cabbage coloured", out.contains("cabbage"));
		assertTrue("turnip coloured", out.contains("turnip"));
	}

	@Test
	public void nothingMatchingColoursNothing()
	{
		assertEquals("", highlighted(watching("cabbage", "", false), "selling turnips"));
	}

	@Test
	public void anEmptyWatchListColoursNothing()
	{
		assertTrue(watching("", "", false).matchRanges("selling cabbage").isEmpty());
	}

	@Test
	public void nameMatchesContributeNoRange()
	{
		// A name is a property of the sender, not a run of characters in the line, so there
		// is nothing in the message to colour even though the message counts as watched.
		MessageFilter byName = new MessageFilter();
		byName.rebuild("", "", "^Danky$", MessageFilterType.REMOVE_MESSAGE, false);

		assertTrue(byName.isNameFiltered("Danky"));
		assertTrue(byName.matchRanges("nothing to see here").isEmpty());
	}

	@Test
	public void aPatternThatCanMatchNothingContributesNoRange()
	{
		// "a*" matches an empty string at every position. Empty ranges would be meaningless
		// to colour and would scale with the length of the message.
		assertTrue(watching("", "x*", false).matchRanges("cabbage").isEmpty());
	}

	@Test
	public void rangesStayInsideTheTextTheyWereFoundIn()
	{
		String text = "selling cabbage cheap";
		for (int[] range : watching("cabbage", "", false).matchRanges(text))
		{
			assertTrue("start >= 0", range[0] >= 0);
			assertTrue("end within text", range[1] <= text.length());
			assertTrue("non-empty", range[1] > range[0]);
		}
	}

	@Test
	public void accentStrippingKeepsTheOffsetsAligned()
	{
		// Matching happens on the stripped copy but the colours are applied to the original,
		// so the two must agree on length or the highlight lands on the wrong characters.
		assertEquals("cabbàge", highlighted(watching("cabbage", "", true), "selling cabbàge cheap"));
	}

	// --- Clipping against what item highlighting already painted (#37 + the loot exemption) ---

	private static String ranges(List<int[]> parts)
	{
		StringBuilder sb = new StringBuilder();
		for (int[] part : parts)
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(part[0]).append("..").append(part[1]);
		}
		return sb.toString();
	}

	@Test
	public void nothingClaimedLeavesTheRangeWhole()
	{
		assertEquals("4..9", ranges(ChatFadePlugin.outside(new int[]{4, 9}, null)));
	}

	@Test
	public void aRangeEndingWhereTheClaimStartsIsUntouched()
	{
		assertEquals("4..9", ranges(ChatFadePlugin.outside(new int[]{4, 9}, new int[]{9, 20})));
	}

	@Test
	public void aRangeStartingWhereTheClaimEndsIsUntouched()
	{
		assertEquals("9..14", ranges(ChatFadePlugin.outside(new int[]{9, 14}, new int[]{4, 9})));
	}

	@Test
	public void aRangeInsideTheClaimIsDroppedEntirely()
	{
		assertEquals("", ranges(ChatFadePlugin.outside(new int[]{6, 8}, new int[]{4, 9})));
	}

	@Test
	public void aRangeOverlappingTheClaimKeepsOnlyItsOutsidePart()
	{
		assertEquals("1..4", ranges(ChatFadePlugin.outside(new int[]{1, 6}, new int[]{4, 9})));
		assertEquals("9..12", ranges(ChatFadePlugin.outside(new int[]{6, 12}, new int[]{4, 9})));
	}

	@Test
	public void aRangeStraddlingTheClaimKeepsBothSides()
	{
		assertEquals("1..4,9..12", ranges(ChatFadePlugin.outside(new int[]{1, 12}, new int[]{4, 9})));
	}

	@Test
	public void aClaimCoveringExactlyTheRangeDropsIt()
	{
		assertEquals("", ranges(ChatFadePlugin.outside(new int[]{4, 9}, new int[]{4, 9})));
	}








	// --- What item highlighting claims, and therefore what the watch colour may not touch ---

	private static final java.util.function.LongPredicate OVER_10K = value -> value >= 10_000L;

	@Test
	public void aTieredDropClaimsItsItemPhrase()
	{
		String text = "Valuable drop: Superior dragon bones (20,124 coins)";
		int[] claimed = ChatFadePlugin.itemHighlightedRange(text, true, true, OVER_10K);
		assertEquals("Superior dragon bones (20,124 coins)",
			text.substring(claimed[0], claimed[1]));
	}

	@Test
	public void aDropUnderTheLowestThresholdClaimsNothing()
	{
		// Nothing is painted there, so there is nothing to protect and the watch colour is
		// free to apply — the gap the live probe caught on "2 x Blue dragonhide (3,384 coins)".
		String text = "Valuable drop: 2 x Blue dragonhide (3,384 coins)";
		assertNull(ChatFadePlugin.itemHighlightedRange(text, true, true, OVER_10K));
	}

	@Test
	public void aDropClaimsNothingWhileLootHighlightingIsOff()
	{
		String text = "Valuable drop: Superior dragon bones (20,124 coins)";
		assertNull(ChatFadePlugin.itemHighlightedRange(text, false, true, OVER_10K));
	}

	@Test
	public void aCollectionLogEntryClaimsItsItemName()
	{
		String text = "Danetta received a new collection log item: Ancient page 1 (447/1734)";
		int[] claimed = ChatFadePlugin.itemHighlightedRange(text, true, true, OVER_10K);
		assertEquals("Ancient page 1", text.substring(claimed[0], claimed[1]));
	}

	@Test
	public void aCollectionLogEntryClaimsNothingWhileItsHighlightingIsOff()
	{
		String text = "Danetta received a new collection log item: Ancient page 1 (447/1734)";
		assertNull(ChatFadePlugin.itemHighlightedRange(text, true, false, OVER_10K));
	}

	@Test
	public void anOrdinaryMessageClaimsNothing()
	{
		assertNull(ChatFadePlugin.itemHighlightedRange("Someone get a drop", true, true, OVER_10K));
	}

	// --- Painting: the watch colour beats the game's, but never the claimed region ---

	private static String paint(String text, List<int[]> ranges, int[] claimed, Color existing)
	{
		List<ColorSpan> spans = existing == null
			? null
			: java.util.Collections.singletonList(new ColorSpan(text, existing));

		List<ColorSpan> out =
			ChatFadePlugin.paintWatched(spans, text, FALLBACK, ranges, claimed, HIGHLIGHT);

		StringBuilder sb = new StringBuilder();
		for (ColorSpan span : out)
		{
			if (HIGHLIGHT.equals(span.getColor()))
			{
				sb.append(span.getText()).append('|');
			}
		}
		return sb.toString();
	}

	@Test
	public void theWatchColourReplacesAColourTheGameChose()
	{
		String text = "Valuable drop: Superior dragon bones (20,124 coins)";
		assertEquals("Valuable drop:|",
			paint(text, java.util.Collections.singletonList(new int[]{0, 14}), null, Color.RED));
	}

	@Test
	public void theWatchColourAppliesToUncolouredTextToo()
	{
		String text = "Valuable drop: Superior dragon bones (20,124 coins)";
		assertEquals("Valuable drop:|",
			paint(text, java.util.Collections.singletonList(new int[]{0, 14}), null, null));
	}

	@Test
	public void theClaimedItemPhraseSurvivesAWatchedWordInsideIt()
	{
		String text = "Valuable drop: Superior dragon bones (20,124 coins)";
		LootBroadcast.Match drop = LootBroadcast.parse(text);
		int[] claimed = new int[]{drop.start, drop.end};

		MessageFilter watch = watching("Valuable drop:,dragon bones", "", false);
		// "dragon bones" sits inside the tiered phrase and is clipped away; the prefix is not.
		assertEquals("Valuable drop:|", paint(text, watch.matchRanges(text), claimed, Color.RED));
	}

	@Test
	public void aClanBroadcastParsesWithAQuantityAndASource()
	{
		String text = "TradePending received a drop: 3 x Snapdragon seed (112,809 coins) from Araxxor.";
		LootBroadcast.Match drop = LootBroadcast.parse(text);
		assertEquals("3 x Snapdragon seed (112,809 coins)", text.substring(drop.start, drop.end));
		assertEquals(112809L, drop.value);
	}
}
