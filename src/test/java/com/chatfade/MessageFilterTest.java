package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The built-in filter is pure given its config, so it is exercised directly rather than
 * through the plugin. Scope rules (which message types, which players are exempt) need the
 * client and live in the plugin instead.
 */
public class MessageFilterTest
{
	private static MessageFilter filter(String words, String regex, String names,
		MessageFilterType type, boolean stripAccents)
	{
		MessageFilter f = new MessageFilter();
		f.rebuild(words, regex, names, type, stripAccents);
		return f;
	}

	private static MessageFilter words(String words, MessageFilterType type)
	{
		return filter(words, "", "", type, false);
	}

	@Test
	public void passesEverythingWhenNothingIsConfigured()
	{
		MessageFilter f = filter("", "", "", MessageFilterType.CENSOR_WORDS, false);

		assertFalse("an empty filter should not be active", f.isActive());
		assertEquals(MessageFilter.Outcome.PASS, f.apply("Bob", "anything at all").getOutcome());
	}

	@Test
	public void starsOutOnlyTheMatchedWord()
	{
		MessageFilter.Result r = words("cabbage", MessageFilterType.CENSOR_WORDS)
			.apply("Bob", "buying cabbage now");

		assertEquals(MessageFilter.Outcome.CENSORED, r.getOutcome());
		assertEquals("buying ******* now", r.getText());
	}

	@Test
	public void removesTheWholeMessageWhenConfiguredTo()
	{
		MessageFilter.Result r = words("cabbage", MessageFilterType.REMOVE_MESSAGE)
			.apply("Bob", "buying cabbage now");

		assertEquals(MessageFilter.Outcome.BLOCK, r.getOutcome());
	}

	@Test
	public void replacesTheWholeMessageWhenConfiguredTo()
	{
		MessageFilter.Result r = words("cabbage", MessageFilterType.CENSOR_MESSAGE)
			.apply("Bob", "buying cabbage now");

		assertEquals(MessageFilter.Outcome.CENSORED, r.getOutcome());
		assertEquals(MessageFilter.CENSORED_MESSAGE, r.getText());
	}

	@Test
	public void leavesAMessageWithNoMatchAlone()
	{
		assertEquals(MessageFilter.Outcome.PASS,
			words("cabbage", MessageFilterType.CENSOR_WORDS).apply("Bob", "buying potatoes").getOutcome());
	}

	@Test
	public void matchesRegardlessOfCase()
	{
		MessageFilter.Result r = words("cabbage", MessageFilterType.REMOVE_MESSAGE)
			.apply("Bob", "buying CaBbAgE now");

		assertEquals(MessageFilter.Outcome.BLOCK, r.getOutcome());
	}

	@Test
	public void treatsFilteredWordsAsLiteralsNotPatterns()
	{
		// "c++" would not compile as a regex, so it must be quoted on the way in.
		MessageFilter f = words("c++", MessageFilterType.REMOVE_MESSAGE);

		assertEquals(MessageFilter.Outcome.BLOCK, f.apply("Bob", "i like c++ a lot").getOutcome());
		assertEquals(MessageFilter.Outcome.PASS, f.apply("Bob", "i like ccc a lot").getOutcome());
	}

	@Test
	public void ignoresColourMarkupWhenMatchingAndStripsItFromTheResult()
	{
		MessageFilter.Result r = words("cabbage", MessageFilterType.CENSOR_WORDS)
			.apply("Bob", "<col=ff0000>buying cabbage now</col>");

		assertEquals(MessageFilter.Outcome.CENSORED, r.getOutcome());
		assertEquals("buying ******* now", r.getText());
	}

	@Test
	public void appliesMessageRegex()
	{
		MessageFilter f = filter("", "^buy.*now$", "", MessageFilterType.REMOVE_MESSAGE, false);

		assertEquals(MessageFilter.Outcome.BLOCK, f.apply("Bob", "buying cabbage now").getOutcome());
		assertEquals(MessageFilter.Outcome.PASS, f.apply("Bob", "selling cabbage now").getOutcome());
	}

	@Test
	public void skipsInvalidRegexWithoutBreakingTheRest()
	{
		MessageFilter f = filter("", "[unclosed\nworking", "", MessageFilterType.REMOVE_MESSAGE, false);

		assertEquals("only the valid pattern should survive", 1, f.wordPatternCount());
		assertEquals(MessageFilter.Outcome.BLOCK, f.apply("Bob", "a working example").getOutcome());
	}

	@Test
	public void filtersEverythingFromAMatchedName()
	{
		MessageFilter f = filter("", "", "^spammer$", MessageFilterType.REMOVE_MESSAGE, false);

		assertTrue(f.isNameFiltered("Spammer"));
		assertEquals(MessageFilter.Outcome.BLOCK, f.apply("Spammer", "hello there").getOutcome());
		assertEquals(MessageFilter.Outcome.PASS, f.apply("Someone Else", "hello there").getOutcome());
	}

	@Test
	public void starsOutAWholeMessageFromAMatchedName()
	{
		MessageFilter.Result r = filter("", "", "^spammer$", MessageFilterType.CENSOR_WORDS, false)
			.apply("Spammer", "hello");

		assertEquals(MessageFilter.Outcome.CENSORED, r.getOutcome());
		assertEquals("stars should cover the visible text only", "*****", r.getText());
	}

	@Test
	public void matchesNamesWithNonBreakingSpaces()
	{
		// Chat delivers names with   rather than a plain space.
		MessageFilter f = filter("", "", "^bad guy$", MessageFilterType.REMOVE_MESSAGE, false);

		assertTrue(f.isNameFiltered("Bad Guy"));
	}

	@Test
	public void ignoresAccentsOnlyWhenAskedTo()
	{
		assertEquals(MessageFilter.Outcome.PASS,
			filter("cabbage", "", "", MessageFilterType.REMOVE_MESSAGE, false)
				.apply("Bob", "buying cábbage").getOutcome());

		assertEquals(MessageFilter.Outcome.BLOCK,
			filter("cabbage", "", "", MessageFilterType.REMOVE_MESSAGE, true)
				.apply("Bob", "buying cábbage").getOutcome());
	}

	@Test
	public void keepsCensoredTextAlignedWhenStrippingAccents()
	{
		// The star run is written into the original text using offsets found in the stripped
		// copy, so the two must stay the same length.
		MessageFilter.Result r = filter("cabbage", "", "", MessageFilterType.CENSOR_WORDS, true)
			.apply("Bob", "buying cábbage now");

		assertEquals(MessageFilter.Outcome.CENSORED, r.getOutcome());
		assertEquals("buying ******* now", r.getText());
	}

	@Test
	public void censorsEveryOccurrence()
	{
		MessageFilter.Result r = words("ha", MessageFilterType.CENSOR_WORDS).apply("Bob", "ha ha ha");

		assertEquals("** ** **", r.getText());
	}

	@Test
	public void appliesSeveralFiltersToOneMessage()
	{
		MessageFilter.Result r = words("buying,cabbage", MessageFilterType.CENSOR_WORDS)
			.apply("Bob", "buying cabbage");

		assertEquals("****** *******", r.getText());
	}

	@Test
	public void handlesANullMessage()
	{
		assertEquals(MessageFilter.Outcome.PASS,
			words("cabbage", MessageFilterType.REMOVE_MESSAGE).apply("Bob", null).getOutcome());
	}
}
