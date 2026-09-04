package com.chatfade;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Covers the plugin's own message filtering, added alongside issue #11. */
public class IgnoreListTest
{
	private IgnoreList list;

	@Before
	public void setUp()
	{
		list = new IgnoreList();
	}

	// ── Text fragments ──────────────────────────────────────

	@Test
	public void matchesConfiguredFragment()
	{
		list.rebuild("surge of power", "");

		assertTrue(list.matches("You feel a surge of power."));
		assertFalse(list.matches("You feel nothing at all."));
	}

	@Test
	public void fragmentMatchingIsCaseInsensitive()
	{
		list.rebuild("SURGE OF POWER", "");
		assertTrue(list.matches("You feel a surge of power."));
	}

	@Test
	public void fragmentMatchingIgnoresColourMarkup()
	{
		// A filter typed in plain text should still catch a message the game coloured,
		// in either syntax.
		list.rebuild("surge of power", "");

		assertTrue(list.matches("<col=ff0000>You feel a surge of power.</col>"));
		assertTrue(list.matches("@mes_hl_red@You feel a surge of power.</col>"));
	}

	@Test
	public void splitsFragmentsOnCommas()
	{
		list.rebuild("one thing, another thing ,third", "");

		assertEquals(3, list.fragmentCount());
		assertTrue(list.matches("here is another thing for you"));
		assertTrue(list.matches("the third option"));
	}

	// ── Regular expressions ─────────────────────────────────

	@Test
	public void matchesConfiguredRegex()
	{
		list.rebuild("", "^you have \\d+ charges?");

		assertTrue(list.matches("You have 12 charges"));
		assertFalse(list.matches("You have many charges"));
	}

	@Test
	public void supportsMultipleRegexLines()
	{
		list.rebuild("", "^first\n^second");

		assertEquals(2, list.patternCount());
		assertTrue(list.matches("first message"));
		assertTrue(list.matches("second message"));
		assertFalse(list.matches("third message"));
	}

	@Test
	public void skipsInvalidRegexWithoutBreakingTheRest()
	{
		list.rebuild("", "[unclosed\n^valid");

		assertEquals("the bad pattern should be dropped, not kept", 1, list.patternCount());
		assertTrue("the good pattern must still apply", list.matches("valid message"));
	}

	@Test
	public void invalidRegexAloneDisablesNothingElse()
	{
		list.rebuild("keepme", "[unclosed");

		assertEquals(0, list.patternCount());
		assertTrue("fragments must still work", list.matches("please keepme here"));
	}

	// ── Empty and edge states ───────────────────────────────

	@Test
	public void emptyConfigMatchesNothing()
	{
		list.rebuild("", "");

		assertFalse(list.matches("any message at all"));
		assertEquals(0, list.fragmentCount());
		assertEquals(0, list.patternCount());
	}

	@Test
	public void handlesNullConfigValues()
	{
		list.rebuild(null, null);
		assertFalse(list.matches("any message at all"));
	}

	@Test
	public void blankAndWhitespaceEntriesAreDiscarded()
	{
		list.rebuild(" , ,  ", "\n\n   \n");

		assertEquals(0, list.fragmentCount());
		assertEquals(0, list.patternCount());
		assertFalse("an all-blank config must not swallow every message", list.matches("hello"));
	}

	@Test
	public void rebuildReplacesRatherThanAccumulates()
	{
		list.rebuild("first", "");
		list.rebuild("second", "");

		assertEquals(1, list.fragmentCount());
		assertFalse(list.matches("first message"));
		assertTrue(list.matches("second message"));
	}
}
