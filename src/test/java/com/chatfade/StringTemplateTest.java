package com.chatfade;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Some broadcasts carry their variable part as a named template — a definition holding the
 * value, then a reference where it belongs — rather than writing it inline:
 *
 * <pre>&lt;str_quest_name_0=The Lost Tribe&gt;Bob has completed a quest: &lt;str_quest_name_0&gt;</pre>
 *
 * <p>Both halves look like ordinary markup, so stripping tags took the quest name with them
 * and the message arrived ending at its colon. That is #32, reported by @jarromie and
 * confirmed by @wizzyhatg.
 */
public class StringTemplateTest
{
	private static final BufferedImage ICON = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);

	private static final String QUEST =
		"<str_quest_name_0=The Lost Tribe>Bob has completed a quest: <str_quest_name_0>";

	private static String expand(String raw)
	{
		return ChatFadePlugin.expandStringTemplates(raw);
	}

	/** What the overlay would actually draw, spans and all. */
	private static String rendered(String raw)
	{
		String expanded = ChatFadePlugin.stripIngestPrefixes(expand(raw));
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(expanded, Color.WHITE, i -> ICON);
		if (spans == null)
		{
			return ChatFadePlugin.toDisplayText(expanded);
		}

		StringBuilder sb = new StringBuilder();
		spans.stream().filter(s -> !s.isIcon()).forEach(s -> sb.append(s.getText()));
		return sb.toString();
	}

	@Test
	public void keepsTheQuestName()
	{
		assertEquals("Bob has completed a quest: The Lost Tribe", rendered(QUEST));
	}

	@Test
	public void theDisplayTextAndTheSpansAgree()
	{
		// Highlight offsets are measured on one and painted onto the other.
		String expanded = ChatFadePlugin.stripIngestPrefixes(expand(QUEST));
		assertEquals(ChatFadePlugin.toDisplayText(expanded), rendered(QUEST));
	}

	@Test
	public void survivesALeadingRankIcon()
	{
		String raw = "<img=22> " + QUEST;

		assertTrue("the quest name should be there: " + rendered(raw),
			rendered(raw).endsWith("The Lost Tribe"));
	}

	@Test
	public void handlesAQuestNameContainingPunctuation()
	{
		assertEquals("Bob has completed a quest: Eagles' Peak",
			rendered("<str_quest_name_0=Eagles' Peak>Bob has completed a quest: <str_quest_name_0>"));
	}

	@Test
	public void substitutesEveryTemplateNotJustTheFirst()
	{
		assertEquals("one Alpha two Beta",
			expand("<str_a=Alpha><str_b=Beta>one <str_a> two <str_b>"));
	}

	@Test
	public void repeatsAValueUsedMoreThanOnce()
	{
		assertEquals("Alpha and Alpha", expand("<str_a=Alpha><str_a> and <str_a>"));
	}

	@Test
	public void leavesAMessageWithoutTemplatesAlone()
	{
		String plain = "<img=22> Bob has completed a quest: Cook's Assistant";

		assertEquals(plain, expand(plain));
	}

	@Test
	public void leavesColourMarkupAlone()
	{
		// <col=...> is not a template and must survive to be parsed as colour.
		String coloured = "<col=ff0000>Bob has completed a quest: Dragon Slayer</col>";

		assertEquals(coloured, expand(coloured));
	}

	@Test
	public void toleratesAReferenceWithNoDefinition()
	{
		// Nothing to substitute; the stray reference is stripped later as ordinary markup.
		assertEquals("Bob has completed a quest: <str_missing>",
			expand("Bob has completed a quest: <str_missing>"));
	}

	@Test
	public void handlesAnEmptyValue()
	{
		assertEquals("Bob has completed a quest: ",
			expand("<str_quest_name_0=>Bob has completed a quest: <str_quest_name_0>"));
	}

	@Test
	public void handlesNullAndPlainText()
	{
		assertEquals(null, expand(null));
		assertEquals("nothing special here", expand("nothing special here"));
	}

	// --- Numeric templates: #44, and the clan drop broadcasts that lost their numbers ---
	//
	// Every raw string below was captured from a live client, not invented.

	@Test
	public void keepsTheXpInAMilestone()
	{
		// #44, reported by @Infinitay, with the raw text from their log.
		assertEquals("Nefarious has reached 17,000,000 XP in Sailing.",
			expand("<int_0=17000000>Nefarious has reached <int_0:spacer> XP in Sailing."));
	}

	@Test
	public void keepsThePriceInAClanDropBroadcast()
	{
		assertEquals("<img=2> Adelie received a drop: Dagon'hai robe top (309,257 coins).",
			expand("<img=2> Adelie received a drop: Dagon'hai robe top<int_0=309257>"
				+ " (<int_0:spacer> coins)."));
	}

	@Test
	public void aNameReusedPartWayThroughTakesItsLaterValue()
	{
		// The quantity and the price share int_0: the second definition must only affect the
		// reference after it. Collecting definitions up front gave both references 183,744.
		assertEquals("<img=2> Adelie received a drop: 18 x Runite ore (183,744 coins).",
			expand("<img=2> Adelie received a drop: <int_0=18><int_0:spacer> x Runite ore"
				+ "<int_0=183744> (<int_0:spacer> coins)."));
	}

	@Test
	public void theRestoredNumbersMakeTheDropParseAgain()
	{
		// The point of the fix: without the numbers LootBroadcast saw no drop at all, so a
		// 183,744 coin drop got no value tier.
		String raw = "<img=2> Adelie received a drop: <int_0=18><int_0:spacer> x Runite ore"
			+ "<int_0=183744> (<int_0:spacer> coins).";
		String displayed = ChatFadePlugin.toDisplayText(
			ChatFadePlugin.stripIngestPrefixes(expand(raw)));

		LootBroadcast.Match drop = LootBroadcast.parse(displayed);
		assertEquals("18 x Runite ore (183,744 coins)",
			displayed.substring(drop.start, drop.end));
		assertEquals(183744L, drop.value);
	}

	@Test
	public void withoutTheFixTheDropWasUnrecognisable()
	{
		// Pins down what was actually wrong, so this cannot quietly regress to "no match".
		assertEquals(null, LootBroadcast.parse("Adelie received a drop:  x Runite ore ( coins)."));
	}

	@Test
	public void aSmallQuantityIsNotGrouped()
	{
		assertEquals("5 x Rune full helm", expand("<int_0=5><int_0:spacer> x Rune full helm"));
	}

	@Test
	public void groupingPutsASeparatorEveryThreeDigits()
	{
		assertEquals("1", ChatFadePlugin.formatTemplateValue("1", "spacer"));
		assertEquals("12", ChatFadePlugin.formatTemplateValue("12", "spacer"));
		assertEquals("123", ChatFadePlugin.formatTemplateValue("123", "spacer"));
		assertEquals("1,234", ChatFadePlugin.formatTemplateValue("1234", "spacer"));
		assertEquals("12,345", ChatFadePlugin.formatTemplateValue("12345", "spacer"));
		assertEquals("123,456", ChatFadePlugin.formatTemplateValue("123456", "spacer"));
		assertEquals("1,234,567", ChatFadePlugin.formatTemplateValue("1234567", "spacer"));
		assertEquals("17,000,000", ChatFadePlugin.formatTemplateValue("17000000", "spacer"));
	}

	@Test
	public void aReferenceWithNoFormatIsNotGrouped()
	{
		// Only "spacer" asks for separators; a bare reference prints the digits as they came.
		assertEquals("204160", expand("<int_0=204160><int_0>"));
	}

	@Test
	public void anUnknownFormatIsLeftAsTheDefinitionWroteIt()
	{
		assertEquals("204160", ChatFadePlugin.formatTemplateValue("204160", "whatever"));
		assertEquals("204160", expand("<int_0=204160><int_0:whatever>"));
	}

	@Test
	public void aNonNumericValueIsNeverReformatted()
	{
		assertEquals("The Lost Tribe",
			ChatFadePlugin.formatTemplateValue("The Lost Tribe", "spacer"));
		assertEquals("-1234", ChatFadePlugin.formatTemplateValue("-1234", "spacer"));
	}

	@Test
	public void anUndefinedNumericReferenceIsLeftAlone()
	{
		assertEquals("has reached <int_0:spacer> XP", expand("has reached <int_0:spacer> XP"));
	}

	@Test
	public void aCollectionLogBroadcastCarriesNoTemplatesAndIsUntouched()
	{
		// Which is why these were the one broadcast shape that always worked.
		String raw = "<img=2> Adelie received a new collection log item: Dagon'hai robe top (1383/1734)";

		assertEquals(raw, expand(raw));
	}

	@Test
	public void theDisplayTextAndTheSpansAgreeForNumericTemplatesToo()
	{
		// Highlight offsets are measured on one and painted onto the other, so a template that
		// expanded in only one of them would paint the wrong characters.
		String raw = "<img=2> Adelie received a drop: <int_0=18><int_0:spacer> x Runite ore"
			+ "<int_0=183744> (<int_0:spacer> coins).";
		String expanded = ChatFadePlugin.stripIngestPrefixes(expand(raw));

		assertEquals(ChatFadePlugin.toDisplayText(expanded), rendered(raw));
	}
}
