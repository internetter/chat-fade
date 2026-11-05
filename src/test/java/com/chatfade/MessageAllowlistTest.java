package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The allowlist is the filter read the other way round: instead of naming what to hide, you
 * name the one thing you are watching for and everything else goes quiet. @Infinitay asked for
 * it in #37.
 *
 * <p>Both halves are pure — the matching in {@link MessageFilter#matches} and the gate in
 * {@link ChatFadePlugin#isAllowedByWatchList} — so both are exercised directly.
 */
public class MessageAllowlistTest
{
	private static MessageFilter watchList(String words, String regex, String names)
	{
		MessageFilter f = new MessageFilter();
		// The outcome type is never consulted by the allowlist; REMOVE_MESSAGE is what the
		// plugin passes.
		f.rebuild(words, regex, names, MessageFilterType.REMOVE_MESSAGE, false);
		return f;
	}

	private static MessageFilter watching(String words)
	{
		return watchList(words, "", "");
	}

	/** The gate as the plugin calls it, with the allowlist switched on. */
	private static boolean allowed(MessageFilter list, String sender, String message)
	{
		return ChatFadePlugin.isAllowedByWatchList(list, true, sender, message);
	}

	// ── The gate ────────────────────────────────────────────

	@Test
	public void letsEverythingThroughWhileTheToggleIsOff()
	{
		// The default, and the only behaviour existing users should ever see.
		MessageFilter list = watching("cabbage");

		assertTrue(ChatFadePlugin.isAllowedByWatchList(list, false, "Bob", "selling runes"));
	}

	@Test
	public void letsEverythingThroughWhenTheWatchListIsEmpty()
	{
		// Arming an empty list must not silence the overlay — it reads as "nothing to watch
		// for yet", not as "match nothing".
		MessageFilter empty = watching("");

		assertFalse("an empty watch list should not be active", empty.isActive());
		assertTrue(allowed(empty, "Bob", "selling runes"));
	}

	@Test
	public void keepsAMatchingMessageAndDropsTheRest()
	{
		MessageFilter list = watching("cabbage");

		assertTrue(allowed(list, "Bob", "buying cabbage now"));
		assertFalse(allowed(list, "Bob", "buying runes now"));
	}

	@Test
	public void appliesToGameMessagesToo()
	{
		// No exemptions: "only show matching" means game lines have to match as well.
		MessageFilter list = watching("Barrows");

		assertTrue(allowed(list, "", "Your Barrows chest count is: 42."));
		assertFalse(allowed(list, "", "You feel a surge of power."));
	}

	@Test
	public void doesNotTreatAnEmptySenderAsAName()
	{
		// Game-authored lines arrive with an empty sender rather than none. A catch-all name
		// pattern must not match that and quietly allow every game message through.
		MessageFilter list = watchList("", "", ".*");

		assertFalse(allowed(list, "", "You feel a surge of power."));
		assertFalse(allowed(list, null, "You feel a surge of power."));
		assertTrue("a real sender still matches", allowed(list, "Bob", "anything"));
	}

	// ── The matching ────────────────────────────────────────

	@Test
	public void matchesNothingWhenNothingIsConfigured()
	{
		assertFalse(watching("").matches("Bob", "cabbage"));
	}

	@Test
	public void matchesAnywhereInTheMessageIgnoringCase()
	{
		MessageFilter list = watching("cabbage");

		assertTrue(list.matches("Bob", "CABBAGE for sale"));
		assertTrue(list.matches("Bob", "fresh Cabbage"));
	}

	@Test
	public void matchesThroughColourMarkup()
	{
		// Broadcasts are full of tags; matching the markup instead of the words would mean a
		// watch list never fired on the lines people most want to catch.
		MessageFilter list = watching("Tumeken");

		assertTrue(list.matches(null, "<col=ff0000>Bob</col> received a drop: Tumeken's shadow"));
	}

	@Test
	public void anAnchoredWatchedRegexSeesTheStartOfTheRealText()
	{
		// Matching runs on the text with markup already gone, so ^ means the first character
		// the player reads — not the first character of a colour tag.
		MessageFilter list = watchList("", "^Bob received", "");

		assertTrue(list.matches(null, "<col=ff0000>Bob</col> received a drop: Abyssal whip"));
	}

	@Test
	public void matchesAWatchedRegex()
	{
		MessageFilter list = watchList("", "received a (special )?drop", "");

		assertTrue(list.matches(null, "Bob received a special drop: Dragon pickaxe"));
		assertTrue(list.matches(null, "Bob received a drop: Abyssal whip"));
		assertFalse(list.matches(null, "Bob has completed a quest: The Lost Tribe"));
	}

	@Test
	public void matchesAWatchedName()
	{
		MessageFilter list = watchList("", "", "^Zezima$");

		assertTrue("everything this player says is watched", list.matches("Zezima", "hello"));
		assertFalse(list.matches("Bob", "hello"));
	}

	@Test
	public void matchesANameWithAnUnderscoreForASpace()
	{
		// Names reach us with either a space or a non-breaking space; standardize settles it.
		MessageFilter list = watchList("", "", "^danky dan$");

		assertTrue(list.matches("Danky Dan", "hello"));
	}

	@Test
	public void aWatchedWordStillMatchesSomeoneNotOnTheNameList()
	{
		// Words and names are alternatives, not conditions on each other.
		MessageFilter list = watchList("cabbage", "", "^Zezima$");

		assertTrue(list.matches("Bob", "buying cabbage"));
	}

	@Test
	public void treatsAWatchedWordAsLiteralNotAsAPattern()
	{
		// Same reason the filter quotes its words: "c++" is a thing people type.
		MessageFilter list = watching("c++");

		assertTrue(list.matches("Bob", "learning c++ today"));
		assertFalse(list.matches("Bob", "ccc"));
	}

	@Test
	public void skipsAnInvalidWatchedRegexWithoutTakingTheRestDown()
	{
		MessageFilter list = watchList("", "drop\nreceived a [", "");

		assertTrue("the valid line should still match", list.matches(null, "Bob received a drop"));
		assertFalse(list.matches(null, "nothing to see"));
	}

	@Test
	public void handlesANullMessage()
	{
		assertFalse(watching("cabbage").matches("Bob", null));
		assertTrue("a watched name needs no message", watchList("", "", "^Bob$").matches("Bob", null));
	}

	@Test
	public void canMatchAccentsStrippedWhenTheFilterOptionIsOn()
	{
		// The allowlist shares the filter's Strip Accents setting, since there is one answer
		// to "are these the same characters".
		MessageFilter stripped = new MessageFilter();
		stripped.rebuild("cabbage", "", "", MessageFilterType.REMOVE_MESSAGE, true);

		assertTrue(stripped.matches("Bob", "fresh cábbage"));
	}

	@Test
	public void matchingDoesNotCensorTheMessage()
	{
		// matches() is apply() without the side effects; sharing the class must not mean
		// sharing the starring out.
		MessageFilter list = watching("cabbage");
		String message = "buying cabbage now";

		assertTrue(list.matches("Bob", message));
		assertFalse("the message itself must be untouched", message.contains("*"));
	}

	// ── Watched duration (#37 part 2) ────────────────────

	@Test
	public void anUnwatchedMessageUsesTheNormalDuration()
	{
		assertEquals(5000L, ChatFadePlugin.displayMsFor(false, 60, 5));
	}

	@Test
	public void aWatchedMessageUsesItsOwnDuration()
	{
		assertEquals(60000L, ChatFadePlugin.displayMsFor(true, 60, 5));
	}

	@Test
	public void zeroMeansTheSettingIsOffNotThatTheMessageVanishes()
	{
		// The obvious reading of 0 is "no time at all", which would make a watched message
		// disappear the instant it arrived — the exact opposite of what the setting is for.
		assertEquals(5000L, ChatFadePlugin.displayMsFor(true, 0, 5));
	}

	@Test
	public void matchingIsIndependentOfTheGate()
	{
		// The whole point of separating the two: with "Only Show Matching" off, a watched
		// message must still be recognised so it can be held longer, while everything else
		// carries on showing normally.
		MessageFilter watch = new MessageFilter();
		watch.rebuild("cabbage", "", "", MessageFilterType.REMOVE_MESSAGE, false);

		assertTrue("matches regardless of the gate",
			ChatFadePlugin.isWatched(watch, "Danky", "selling cabbage"));
		assertFalse(ChatFadePlugin.isWatched(watch, "Danky", "selling turnips"));

		// ...and with the gate off, the unmatched message is still allowed through.
		assertTrue(ChatFadePlugin.isAllowedByWatchList(watch, false, "Danky", "selling turnips"));
	}

	@Test
	public void anEmptyWatchListWatchesNothing()
	{
		// Must not report every message as watched, or turning on a duration with no terms
		// filled in would freeze the whole overlay on screen.
		MessageFilter empty = new MessageFilter();
		empty.rebuild("", "", "", MessageFilterType.REMOVE_MESSAGE, false);

		assertFalse(ChatFadePlugin.isWatched(empty, "Danky", "anything at all"));
	}
}
