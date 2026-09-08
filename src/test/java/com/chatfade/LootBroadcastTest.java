package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Every sample here is a real broadcast taken from recorded clan logs (player names
 * replaced), so the parser is checked against the format the game actually sends rather
 * than an assumed one.
 */
public class LootBroadcastTest
{
	private static LootBroadcast.Match parse(String s)
	{
		return LootBroadcast.parse(s);
	}

	/** Convenience: the exact substring the parser would recolour. */
	private static String region(String message)
	{
		LootBroadcast.Match m = parse(message);
		return m == null ? null : message.substring(m.start, m.end);
	}

	// ── The two real shapes ─────────────────────────────────

	@Test
	public void parsesDropWithoutQuantity()
	{
		LootBroadcast.Match m = parse("Bob received a drop: Awakener's orb (877,401 coins).");

		assertNotNull(m);
		assertEquals(877401L, m.value);
		assertEquals("Awakener's orb (877,401 coins)",
			"Bob received a drop: Awakener's orb (877,401 coins).".substring(m.start, m.end));
	}

	@Test
	public void parsesDropWithQuantity()
	{
		LootBroadcast.Match m = parse("Bob received a drop: 18 x Runite ore (196,200 coins).");

		assertNotNull(m);
		assertEquals(196200L, m.value);
		assertEquals("18 x Runite ore (196,200 coins)",
			"Bob received a drop: 18 x Runite ore (196,200 coins).".substring(m.start, m.end));
	}

	@Test
	public void parsesDropWithCommaSeparatedQuantityAndSource()
	{
		LootBroadcast.Match m = parse("Bob received a drop: 1,000 x Soul rune (177,000 coins) from Yama.");

		assertNotNull(m);
		assertEquals(177000L, m.value);
		assertEquals("1,000 x Soul rune (177,000 coins)",
			"Bob received a drop: 1,000 x Soul rune (177,000 coins) from Yama.".substring(m.start, m.end));
	}

	@Test
	public void handlesLargeValues()
	{
		// Above 2^24, where a float-based parse would start losing precision.
		LootBroadcast.Match m = parse("Bob received a drop: Virtus robe top (61,058,904 coins).");

		assertNotNull(m);
		assertEquals(61058904L, m.value);
	}

	@Test
	public void handlesRankIconPrefixOnceMarkupIsStripped()
	{
		// The raw message begins with an <img=22> rank icon, which is already removed by
		// the time the parser sees the text.
		assertEquals("Awakener's orb (877,401 coins)",
			region("Bob received a drop: Awakener's orb (877,401 coins)."));
	}

	@Test
	public void handlesApostrophesAndHyphensInNames()
	{
		assertEquals("Ahrim's robeskirt (2,795,497 coins)",
			region("Bob received a drop: Ahrim's robeskirt (2,795,497 coins)."));
	}

	// ── Region boundaries ───────────────────────────────────

	@Test
	public void regionStartsAtTheItemNotThePlayerName()
	{
		String msg = "Bob received a drop: Abyssal whip (1,219,310 coins).";
		LootBroadcast.Match m = parse(msg);

		assertNotNull(m);
		assertEquals("Bob received a drop: ", msg.substring(0, m.start));
	}

	@Test
	public void regionExcludesTheSourceSuffix()
	{
		String msg = "Bob received a drop: 10 x Magic seed (1,031,540 coins) from Hespori.";
		LootBroadcast.Match m = parse(msg);

		assertNotNull(m);
		assertEquals(" from Hespori.", msg.substring(m.end));
	}

	// ── Non-matches ─────────────────────────────────────────

	@Test
	public void ignoresOtherClanBroadcasts()
	{
		// By far the most common clan broadcast in practice; no item, nothing to tier.
		assertNull(parse("Bob has reached Thieving level 99."));
		assertNull(parse("Bob has reached 50,000,000 XP in Magic."));
		assertNull(parse("Bob has reached combat level 126."));
	}

	@Test
	public void ignoresOrdinaryMessages()
	{
		assertNull(parse("You feel a surge of power."));
		assertNull(parse("How much for the whip?"));
		assertNull(parse(""));
		assertNull(parse(null));
	}

	@Test
	public void ignoresDropTextWithoutAValue()
	{
		// Defensive: if the format ever loses its value, do nothing rather than guess.
		assertNull(parse("Bob received a drop: Awakener's orb."));
	}

	// ── Collection log broadcasts ───────────────────────────

	@Test
	public void parsesCollectionLogItem()
	{
		String msg = "Bob received a new collection log item: Dragon defender (33/1717).";
		LootBroadcast.Match m = LootBroadcast.parseCollectionLog(msg);

		assertNotNull(m);
		assertEquals("Dragon defender", msg.substring(m.start, m.end));
	}

	@Test
	public void collectionLogNameKeepsItsOwnParentheses()
	{
		// "(g)" is part of the name; only the digits/digits counter ends it.
		String msg = "Bob received a new collection log item: Adamant full helm (g) (12/1717).";
		LootBroadcast.Match m = LootBroadcast.parseCollectionLog(msg);

		assertNotNull(m);
		assertEquals("Adamant full helm (g)", msg.substring(m.start, m.end));
	}

	@Test
	public void collectionLogExcludesTheProgressCounter()
	{
		String msg = "Bob received a new collection log item: Abyssal whip (1/1717).";
		LootBroadcast.Match m = LootBroadcast.parseCollectionLog(msg);

		assertNotNull(m);
		assertEquals(" (1/1717).", msg.substring(m.end));
	}

	@Test
	public void dropAndCollectionLogDoNotMatchEachOther()
	{
		// A collection log line has no "(N coins)" and a drop has no "(N/N)" counter.
		assertNull(LootBroadcast.parse("Bob received a new collection log item: Dragon defender (33/1717)."));
		assertNull(LootBroadcast.parseCollectionLog("Bob received a drop: Dragon defender (240,000 coins)."));
	}
}
