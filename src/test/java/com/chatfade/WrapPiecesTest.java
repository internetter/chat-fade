package com.chatfade;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Wrapping is pure given a FontMetrics, so it is exercised directly with real metrics from
 * a headless image rather than through the overlay.
 */
public class WrapPiecesTest
{
	private static FontMetrics fm;

	@BeforeClass
	public static void setUp()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		fm = g.getFontMetrics();
	}

	private static List<List<ColorSpan>> wrap(List<ColorSpan> pieces, int first, int cont)
	{
		return ChatFadeOverlay.wrapPieces(pieces, fm, first, cont);
	}

	private static String textOf(List<ColorSpan> line)
	{
		StringBuilder sb = new StringBuilder();
		line.forEach(p -> sb.append(p.getText()));
		return sb.toString();
	}

	private static List<ColorSpan> text(String s)
	{
		return new ArrayList<>(Arrays.asList(new ColorSpan(s, Color.WHITE)));
	}

	@Test
	public void keepsAShortMessageOnOneLine()
	{
		List<List<ColorSpan>> lines = wrap(text("short message"), 1000, 1000);

		assertEquals(1, lines.size());
		assertEquals("short message", textOf(lines.get(0)));
	}

	@Test
	public void breaksAtSpacesNotMidWord()
	{
		int width = fm.stringWidth("hello world");
		List<List<ColorSpan>> lines = wrap(text("hello world again"), width, width);

		assertTrue(lines.size() >= 2);
		for (List<ColorSpan> line : lines)
		{
			String t = textOf(line);
			assertFalse("a line should not start with a space: [" + t + "]", t.startsWith(" "));
		}
	}

	@Test
	public void losesNoTextWhenWrapping()
	{
		String source = "the quick brown fox jumps over the lazy dog";
		List<List<ColorSpan>> lines = wrap(text(source), fm.stringWidth("the quick"), fm.stringWidth("the quick"));

		StringBuilder rejoined = new StringBuilder();
		for (int i = 0; i < lines.size(); i++)
		{
			if (i > 0)
			{
				rejoined.append(' ');
			}
			rejoined.append(textOf(lines.get(i)));
		}
		assertEquals(source, rejoined.toString());
	}

	@Test
	public void breaksAWordTooLongForOneLine()
	{
		// Must terminate rather than loop forever on a word wider than the limit.
		String longWord = "supercalifragilisticexpialidocious";
		List<List<ColorSpan>> lines = wrap(text(longWord), fm.stringWidth("short"), fm.stringWidth("short"));

		assertTrue("expected the word to be split", lines.size() > 1);
		StringBuilder rejoined = new StringBuilder();
		lines.forEach(l -> rejoined.append(textOf(l)));
		assertEquals(longWord, rejoined.toString());
	}

	@Test
	public void usesTheNarrowerWidthForContinuationLines()
	{
		String source = "aaa bbb ccc ddd eee fff";
		int wide = fm.stringWidth("aaa bbb ccc ddd eee fff");

		// First line fits everything; continuation width is irrelevant.
		assertEquals(1, wrap(text(source), wide, 10).size());

		// A narrow first line forces continuation lines, which are narrower still.
		List<List<ColorSpan>> lines = wrap(text(source), fm.stringWidth("aaa bbb"), fm.stringWidth("aaa"));
		assertTrue(lines.size() >= 3);
	}

	@Test
	public void treatsAnIconAsAnUnbreakableUnit()
	{
		BufferedImage icon = new BufferedImage(11, 11, BufferedImage.TYPE_INT_ARGB);
		List<ColorSpan> pieces = new ArrayList<>();
		pieces.add(new ColorSpan("name: ", Color.WHITE));
		pieces.add(ColorSpan.icon(icon));
		pieces.add(new ColorSpan("after", Color.WHITE));

		List<List<ColorSpan>> lines = wrap(pieces, 1000, 1000);

		assertEquals(1, lines.size());
		long icons = lines.get(0).stream().filter(ColorSpan::isIcon).count();
		assertEquals("the icon must survive wrapping", 1, icons);
	}

	@Test
	public void alwaysReturnsAtLeastOneLine()
	{
		assertEquals(1, wrap(new ArrayList<>(), 100, 100).size());
		assertEquals(1, wrap(text(""), 100, 100).size());
	}
}
