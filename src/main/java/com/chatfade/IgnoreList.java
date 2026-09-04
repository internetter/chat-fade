package com.chatfade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;

/**
 * The plugin's own message filtering, independent of RuneLite's Chat Filter plugin.
 *
 * <p>Holds a list of plain text fragments and a list of regular expressions. Matching is
 * done against a normalised form of the message — colour markup of both syntaxes removed,
 * trimmed, lowercased — so a filter written in plain text still matches a message the game
 * decorated with colour.
 */
@Slf4j
class IgnoreList
{
	private List<String> fragments = new ArrayList<>();
	private List<Pattern> patterns = new ArrayList<>();

	/**
	 * Reduces a message to a comparable form: no colour markup, trimmed, lowercase.
	 * Messages are macro-expanded before they get here, so {@code <...>} is the only
	 * markup left to strip.
	 */
	static String normalize(String message)
	{
		if (message == null || message.isEmpty())
		{
			return "";
		}

		return Text.removeTags(message).trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Rebuilds both lists from raw config values. Invalid regular expressions are logged and
	 * skipped so that one bad entry cannot take the rest of the filtering down with it.
	 *
	 * @param csv comma-separated text fragments
	 * @param regexLines newline-separated regular expressions
	 */
	void rebuild(String csv, String regexLines)
	{
		List<String> newFragments = new ArrayList<>();
		for (String entry : Text.fromCSV(csv == null ? "" : csv))
		{
			String normalized = normalize(entry);
			if (!normalized.isEmpty())
			{
				newFragments.add(normalized);
			}
		}
		fragments = newFragments;

		List<Pattern> newPatterns = new ArrayList<>();
		for (String line : (regexLines == null ? "" : regexLines).split("\n"))
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			try
			{
				newPatterns.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
			}
			catch (PatternSyntaxException ex)
			{
				log.warn("Chat Fade: ignoring invalid regex \"{}\"", trimmed, ex);
			}
		}
		patterns = newPatterns;
	}

	/** @return true if the message matches any configured fragment or pattern */
	boolean matches(String rawMessage)
	{
		if (fragments.isEmpty() && patterns.isEmpty())
		{
			return false;
		}

		String normalized = normalize(rawMessage);
		if (normalized.isEmpty())
		{
			return false;
		}

		for (String fragment : fragments)
		{
			if (normalized.contains(fragment))
			{
				return true;
			}
		}

		for (Pattern pattern : patterns)
		{
			if (pattern.matcher(normalized).find())
			{
				return true;
			}
		}

		return false;
	}

	/** @return number of usable regular expressions, i.e. excluding any that failed to compile */
	int patternCount()
	{
		return patterns.size();
	}

	/** @return number of text fragments */
	int fragmentCount()
	{
		return fragments.size();
	}
}
