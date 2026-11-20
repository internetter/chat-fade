package com.chatfade;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Applies highlight colours to a message without ever overriding a colour the game chose.
 *
 * <p>The rule is deliberately singular: <b>the game's colour always wins, and we only paint
 * text the game left uncoloured.</b> A span still carrying the per-type fallback colour is
 * "unclaimed" and may be recoloured; anything the game explicitly coloured is left alone.
 * That keeps the interaction with "Preserve In-Game Colors" free of special cases — with it
 * off, everything is unclaimed, so highlighting simply applies throughout.
 */
final class Highlighter
{
	private Highlighter()
	{
	}

	/**
	 * Recolours the plain-text range {@code [start, end)} of a message.
	 *
	 * @param spans    existing spans, or null when the message has no game colouring
	 * @param plainText the message as displayed, which the spans concatenate to
	 * @param fallback the per-type colour, i.e. the marker for unclaimed text
	 * @param start    inclusive start offset in {@code plainText}
	 * @param end      exclusive end offset in {@code plainText}
	 * @param highlight colour to apply
	 * @return new spans, or null when nothing was recoloured and there was no prior colouring
	 */
	static List<ColorSpan> highlightRange(List<ColorSpan> spans, String plainText, Color fallback,
		int start, int end, Color highlight)
	{
		return highlightRange(spans, plainText, fallback, start, end, highlight, false);
	}

	/**
	 * As above, but {@code replaceGameColour} lifts the "game colour wins" rule for this range.
	 *
	 * <p>Two callers need it, both for the same reason: the game paints whole lines one flat
	 * colour, so deferring to it means never colouring anything. Item highlighting uses it so
	 * a value tier can replace the game's single "Valuable drop" colour — otherwise the
	 * feature could only ever colour other people's broadcasts, never the player's own drops.
	 * Watched words use it because a word the player asked to be shown their colour is not
	 * worth hiding behind the game's.
	 *
	 * <p>The default stays the other way round, and the two exceptions do not collide: a
	 * watched word overlapping a region item highlighting has already claimed is clipped out
	 * before it reaches here.
	 */
	static List<ColorSpan> highlightRange(List<ColorSpan> spans, String plainText, Color fallback,
		int start, int end, Color highlight, boolean replaceGameColour)
	{
		if (plainText == null || start < 0 || end > plainText.length() || start >= end)
		{
			return spans;
		}

		List<ColorSpan> source = spans != null && !spans.isEmpty()
			? spans
			: Collections.singletonList(new ColorSpan(plainText, fallback));

		List<ColorSpan> out = new ArrayList<>(source.size() + 2);
		int offset = 0;
		boolean changed = false;

		for (ColorSpan span : source)
		{
			String text = span.getText();
			int spanStart = offset;
			int spanEnd = offset + text.length();
			offset = spanEnd;

			// Leave anything outside the range untouched, and — unless this is a recognised
			// drop — anything the game coloured.
			boolean claimedByGame = !fallback.equals(span.getColor());
			if ((claimedByGame && !replaceGameColour) || spanEnd <= start || spanStart >= end)
			{
				out.add(span);
				continue;
			}

			int localStart = Math.max(start, spanStart) - spanStart;
			int localEnd = Math.min(end, spanEnd) - spanStart;

			if (localStart > 0)
			{
				out.add(new ColorSpan(text.substring(0, localStart), span.getColor()));
			}
			out.add(new ColorSpan(text.substring(localStart, localEnd), highlight));
			if (localEnd < text.length())
			{
				out.add(new ColorSpan(text.substring(localEnd), span.getColor()));
			}
			changed = true;
		}

		if (!changed)
		{
			// Nothing was recoloured, so don't manufacture spans the renderer doesn't need.
			return spans;
		}

		return out;
	}
}
