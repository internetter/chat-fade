package com.chatfade;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;

/**
 * The plugin's own word, pattern and name filtering — a self-contained equivalent of
 * RuneLite's Chat Filter plugin rather than an observer of it.
 *
 * <p>The earlier "Respect Chat Filter Plugin" approach watched the {@code chatFilterCheck}
 * script callback, which only fires while the game rebuilds the chatbox. Chat Fade exists to
 * show messages when the chatbox is collapsed, so that callback is exactly the signal least
 * likely to arrive when it is needed. Filtering here instead runs at ingest and does not care
 * what any other plugin is doing.
 *
 * <p>This class is deliberately free of client state: deciding *which* messages are in scope
 * (game chat, friends, clan mates) needs the client and stays in the plugin, while the
 * matching and censoring below is pure and directly testable.
 */
@Slf4j
class MessageFilter
{
	/** What the game's own filter substitutes for a whole censored message. */
	static final String CENSORED_MESSAGE = "Hey, everyone, I just tried to say something very silly!";

	enum Outcome
	{
		/** Not matched — show the message untouched. */
		PASS,
		/** Matched — do not show the message at all. */
		BLOCK,
		/** Matched — show the replacement text instead. */
		CENSORED
	}

	static class Result
	{
		static final Result PASS = new Result(Outcome.PASS, null);
		static final Result BLOCK = new Result(Outcome.BLOCK, null);

		private final Outcome outcome;
		private final String text;

		private Result(Outcome outcome, String text)
		{
			this.outcome = outcome;
			this.text = text;
		}

		static Result censored(String text)
		{
			return new Result(Outcome.CENSORED, text);
		}

		Outcome getOutcome()
		{
			return outcome;
		}

		/** The replacement text; only meaningful for {@link Outcome#CENSORED}. */
		String getText()
		{
			return text;
		}
	}

	private List<Pattern> wordPatterns = new ArrayList<>();
	private List<Pattern> namePatterns = new ArrayList<>();
	private MessageFilterType filterType = MessageFilterType.CENSOR_WORDS;
	private boolean stripAccents;

	/**
	 * Rebuilds the filter from raw config values. Invalid regular expressions are logged and
	 * skipped so one bad entry cannot take the rest of the filtering down with it.
	 *
	 * @param words comma-separated literal fragments
	 * @param regexLines newline-separated regular expressions matched against the message
	 * @param nameLines newline-separated regular expressions matched against the sender
	 */
	void rebuild(String words, String regexLines, String nameLines, MessageFilterType type, boolean stripAccents)
	{
		this.filterType = type == null ? MessageFilterType.CENSOR_WORDS : type;
		this.stripAccents = stripAccents;

		List<Pattern> newWords = new ArrayList<>();
		for (String entry : Text.fromCSV(words == null ? "" : words))
		{
			String trimmed = entry.trim();
			if (!trimmed.isEmpty())
			{
				// Literal, so a filter like "c++" is not read as a quantifier.
				newWords.add(Pattern.compile(Pattern.quote(stripAccents(trimmed)), Pattern.CASE_INSENSITIVE));
			}
		}
		compileInto(regexLines, newWords, "message");
		wordPatterns = newWords;

		List<Pattern> newNames = new ArrayList<>();
		compileInto(nameLines, newNames, "name");
		namePatterns = newNames;
	}

	private void compileInto(String lines, List<Pattern> target, String what)
	{
		for (String line : (lines == null ? "" : lines).split("\n"))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			try
			{
				target.add(Pattern.compile(stripAccents(trimmed), Pattern.CASE_INSENSITIVE));
			}
			catch (PatternSyntaxException ex)
			{
				log.warn("Chat Fade: ignoring invalid {} filter regex \"{}\"", what, trimmed, ex);
			}
		}
	}

	/** @return true when anything is configured, so callers can skip the work entirely */
	boolean isActive()
	{
		return !wordPatterns.isEmpty() || !namePatterns.isEmpty();
	}

	/**
	 * Applies the filter to one message.
	 *
	 * @param senderName sender to match name filters against, or null for game-authored text
	 * @param message the message as the game supplied it, markup and all
	 */
	Result apply(String senderName, String message)
	{
		if (!isActive() || message == null)
		{
			return Result.PASS;
		}

		if (senderName != null && isNameFiltered(senderName))
		{
			switch (filterType)
			{
				case CENSOR_WORDS:
					// Length of the visible text, not the markup, so the stars line up with
					// what the player actually said.
					return Result.censored(repeat('*', plainText(message).length()));
				case CENSOR_MESSAGE:
					return Result.censored(CENSORED_MESSAGE);
				case REMOVE_MESSAGE:
					return Result.BLOCK;
			}
		}

		String plain = plainText(message);
		// Matching happens on the accent-stripped copy but the stars are written into the
		// original, so both must stay the same length — see stripAccents.
		String matchable = stripAccents(plain);

		boolean censored = false;
		for (Pattern pattern : wordPatterns)
		{
			Matcher m = pattern.matcher(matchable);

			StringBuilder sb = new StringBuilder();
			int idx = 0;
			while (m.find())
			{
				switch (filterType)
				{
					case CENSOR_WORDS:
						sb.append(plain, idx, m.start()).append(repeat('*', m.end() - m.start()));
						idx = m.end();
						censored = true;
						break;
					case CENSOR_MESSAGE:
						return Result.censored(CENSORED_MESSAGE);
					case REMOVE_MESSAGE:
						return Result.BLOCK;
				}
			}
			sb.append(plain.substring(idx));
			plain = sb.toString();
		}

		return censored ? Result.censored(plain) : Result.PASS;
	}

	boolean isNameFiltered(String senderName)
	{
		if (senderName == null || namePatterns.isEmpty())
		{
			return false;
		}

		String standardized = stripAccents(Text.standardize(senderName));
		for (Pattern pattern : namePatterns)
		{
			if (pattern.matcher(standardized).find())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Reduces a message to the characters the player actually sees: markup removed, escaped
	 * characters restored, anything the game cannot render dropped.
	 */
	static String plainText(String message)
	{
		String unescaped = Text.unescapeJagex(message);
		return Text.JAGEX_PRINTABLE_CHAR_MATCHER.retainFrom(unescaped).replace(' ', ' ');
	}

	/**
	 * Strips accents one character at a time, keeping the original where a character does not
	 * decompose to a single base letter.
	 *
	 * <p>The censoring above writes star runs into the unstripped text using offsets found in
	 * the stripped one, so the two strings must agree on length. Normalising the whole string
	 * at once does not guarantee that, since some characters decompose to several.
	 */
	private String stripAccents(String input)
	{
		if (!stripAccents || input == null)
		{
			return input;
		}

		StringBuilder sb = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++)
		{
			char c = input.charAt(i);
			if (c < 0x80)
			{
				sb.append(c);
				continue;
			}

			String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD)
				.replaceAll("\\p{Mn}+", "");
			sb.append(decomposed.length() == 1 ? decomposed.charAt(0) : c);
		}
		return sb.toString();
	}

	private static String repeat(char c, int count)
	{
		StringBuilder sb = new StringBuilder(Math.max(0, count));
		for (int i = 0; i < count; i++)
		{
			sb.append(c);
		}
		return sb.toString();
	}

	int wordPatternCount()
	{
		return wordPatterns.size();
	}

	int namePatternCount()
	{
		return namePatterns.size();
	}
}
