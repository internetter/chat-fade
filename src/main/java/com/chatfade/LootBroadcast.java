package com.chatfade;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the clan loot broadcast the game sends when a member receives a drop.
 *
 * <p>The game supplies the value itself, so no price lookup is needed — which also means
 * untradeable drops tier correctly and there is no dependency on the item price index
 * having loaded.
 *
 * <p>Two shapes occur, with an optional source suffix:
 * <pre>
 *   Bob received a drop: Awakener's orb (877,401 coins).
 *   Bob received a drop: 18 x Runite ore (196,200 coins).
 *   Bob received a drop: 1,000 x Soul rune (177,000 coins) from Yama.
 * </pre>
 */
final class LootBroadcast
{
	/**
	 * The item portion runs from the quantity (when present) to the closing parenthesis of
	 * the value, so the whole "what dropped and what it's worth" phrase is highlighted as
	 * one unit. Reluctant matching on the name stops it swallowing a later parenthesis.
	 */
	private static final Pattern DROP = Pattern.compile(
		"(?:received a drop|Valuable drop): (?<item>(?:[\\d,]+ x )?.+? \\((?<value>[\\d,]+) coins\\))");

	/**
	 * Collection log broadcasts carry no value, so only the item name is matched — the
	 * "(33/1717)" progress counter is left in the base colour.
	 *
	 * <p>Reluctant matching again matters here: names such as "Adamant full helm (g)"
	 * contain their own parentheses, and only the digits/digits group ends the name.
	 */
	private static final Pattern COLLECTION_LOG = Pattern.compile(
		"received a new collection log item: (?<item>.+?) \\(\\d+/\\d+\\)");

	private LootBroadcast()
	{
	}

	/**
	 * @param plainText message with all markup already removed
	 * @return the item name's region, or null when the message is not a collection log entry
	 */
	static Match parseCollectionLog(String plainText)
	{
		if (plainText == null || plainText.isEmpty())
		{
			return null;
		}

		Matcher matcher = COLLECTION_LOG.matcher(plainText);
		if (!matcher.find())
		{
			return null;
		}

		return new Match(matcher.start("item"), matcher.end("item"), 0L);
	}

	/**
	 * @param plainText message with all markup already removed
	 * @return the matched region and its value, or null when the message is not a drop
	 */
	static Match parse(String plainText)
	{
		if (plainText == null || plainText.isEmpty())
		{
			return null;
		}

		Matcher matcher = DROP.matcher(plainText);
		if (!matcher.find())
		{
			return null;
		}

		// The value group is constrained to digits and commas by the pattern, so this
		// cannot overflow into a parse failure for any string the game actually sends.
		long value;
		try
		{
			value = Long.parseLong(matcher.group("value").replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return null;
		}

		return new Match(matcher.start("item"), matcher.end("item"), value);
	}

	/** A highlightable region of a message, in plain-text character offsets. */
	static final class Match
	{
		final int start;
		final int end;
		final long value;

		Match(int start, int end, long value)
		{
			this.start = start;
			this.end = end;
			this.value = value;
		}
	}
}
