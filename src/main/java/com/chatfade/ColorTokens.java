package com.chatfade;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Support for Jagex's {@code @name@} colour tokens.
 *
 * <p>These predate the {@code <col=rrggbb>} syntax and are still emitted by the game — the
 * Mad Angel / Combat Achievement update leaned on them heavily. They are not angle-bracket
 * tags, so {@link net.runelite.client.util.Text#removeTags} leaves them in the string and
 * they render as literal text in the overlay.
 *
 * <p>A token behaves like an opening colour tag and is terminated by {@code </col>}, e.g.
 * {@code "@mes_hl_red@You have been frozen!</col>"}.
 *
 * <p>Matching is deliberately conservative so that player-typed text such as
 * {@code "@bob@"} is left untouched: a run between {@code @} signs is only treated as a
 * colour token when it is either a known palette name or is structured like one
 * (all lowercase, containing an underscore).
 */
final class ColorTokens
{
	/** Matches a candidate token; the caller decides whether the name is really one. */
	private static final Pattern TOKEN = Pattern.compile("@([a-z0-9_]{2,})@");

	/** Shape of the newer named entries, e.g. {@code mes_hl_pur}. Requires an underscore. */
	private static final Pattern STRUCTURED_NAME = Pattern.compile("[a-z][a-z0-9]*(?:_[a-z0-9]+)+");

	private static final Map<String, Color> PALETTE = new HashMap<>();

	static
	{
		// Classic RS2 chat codes. These values are long-standing and well established.
		PALETTE.put("red", new Color(0xFF0000));
		PALETTE.put("gre", new Color(0x00FF00));
		PALETTE.put("blu", new Color(0x0000FF));
		PALETTE.put("yel", new Color(0xFFFF00));
		PALETTE.put("cya", new Color(0x00FFFF));
		PALETTE.put("mag", new Color(0xFF00FF));
		PALETTE.put("whi", new Color(0xFFFFFF));
		PALETTE.put("bla", new Color(0x000000));
		PALETTE.put("lre", new Color(0xFF9040));
		PALETTE.put("dre", new Color(0x800000));
		PALETTE.put("or1", new Color(0xFFB000));
		PALETTE.put("or2", new Color(0xFF7000));
		PALETTE.put("or3", new Color(0xFF3000));
		PALETTE.put("gr1", new Color(0xC0FF00));
		PALETTE.put("gr2", new Color(0x80FF00));
		PALETTE.put("gr3", new Color(0x40FF00));

		// Newer "message highlight" entries. Hues are taken from the token suffix, which is
		// reliable; the exact shades are approximations pending in-game sampling. Any
		// mes_hl_* token not listed here still gets stripped rather than shown as raw text,
		// so an unknown entry degrades to the per-type colour instead of leaking garbage.
		PALETTE.put("mes_hl_red", new Color(0xFF3030));
		PALETTE.put("mes_hl_gre", new Color(0x30FF30));
		PALETTE.put("mes_hl_blu", new Color(0x6060FF));
		PALETTE.put("mes_hl_yel", new Color(0xFFFF30));
		PALETTE.put("mes_hl_cya", new Color(0x30FFFF));
		PALETTE.put("mes_hl_mag", new Color(0xFF30FF));
		PALETTE.put("mes_hl_ora", new Color(0xFF9030));
		PALETTE.put("mes_hl_pur", new Color(0xB060FF));
		PALETTE.put("mes_hl_whi", new Color(0xFFFFFF));
	}

	private ColorTokens()
	{
	}

	static Pattern tokenPattern()
	{
		return TOKEN;
	}

	/**
	 * @return the colour for a token name, or null if the name is not a known palette entry
	 */
	static Color colorFor(String name)
	{
		return PALETTE.get(name);
	}

	/**
	 * @return true if the name looks like a colour token even though we have no colour for
	 * it — such tokens are removed from the text rather than rendered literally
	 */
	static boolean isStructuredName(String name)
	{
		return STRUCTURED_NAME.matcher(name).matches();
	}

	/** @return true if the name should be consumed by the parser (coloured or stripped) */
	static boolean isToken(String name)
	{
		return PALETTE.containsKey(name) || isStructuredName(name);
	}

	/**
	 * Removes colour tokens from text, leaving anything that merely looks similar
	 * (such as {@code "@bob@"}) alone.
	 */
	static String strip(String text)
	{
		if (text == null || text.indexOf('@') < 0)
		{
			return text;
		}

		Matcher matcher = TOKEN.matcher(text);
		StringBuffer out = new StringBuffer(text.length());
		while (matcher.find())
		{
			String replacement = isToken(matcher.group(1)) ? "" : Matcher.quoteReplacement(matcher.group());
			matcher.appendReplacement(out, replacement);
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/** @return true if the text contains at least one real colour token */
	static boolean containsToken(String text)
	{
		if (text == null || text.indexOf('@') < 0)
		{
			return false;
		}

		Matcher matcher = TOKEN.matcher(text);
		while (matcher.find())
		{
			if (isToken(matcher.group(1)))
			{
				return true;
			}
		}
		return false;
	}
}
