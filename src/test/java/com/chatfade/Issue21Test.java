package com.chatfade;

import java.awt.Color;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The two symptoms reported in issue #21:
 *
 * <ol>
 *   <li>"It shows an internal string for colors" — colour markup rendered as literal text
 *       in the overlay.</li>
 *   <li>"the 'Preserve In-Game Colors' option doesn't work" — the message fell back to the
 *       flat per-type colour instead of carrying the colour the game gave it.</li>
 * </ol>
 *
 * <p>The {@code @name@} macros the report was about are expanded by
 * {@link net.runelite.api.Client#macroExpand} before anything here runs, so the inputs
 * below are in the post-expansion form the pipeline actually sees. Whether expansion itself
 * is correct is the client's responsibility, not this plugin's — that is the whole reason
 * for delegating it.
 */
public class Issue21Test
{
	private static final Color PER_TYPE_FALLBACK = new Color(100, 200, 255);

	/** Post-macroExpand forms of the messages from the report. */
	private static final String[] REPORTED_MESSAGES = {
		"<col=ff3030>You have been frozen!</col>",
		"<col=30ff30>The bush is already fruiting and won't benefit from any more pollen.</col>",
		"<col=b060ff>Your Death Charge spell will be active.</col>",
		"CA_ID:330|<col=b060ff>Sam completed a hard combat task: Mad Angel.</col>",
		"<col=ff0000>CA_ID:47|<col=ffff30>Sam completed an elite combat task.</col>",
	};

	// ── Symptom 1: no markup on screen ──────────────────────

	@Test
	public void noMarkupSurvivesToTheDisplayTextPath()
	{
		for (String raw : REPORTED_MESSAGES)
		{
			String display = ChatFadePlugin.toDisplayText(ChatFadePlugin.stripIngestPrefixes(raw));

			assertFalse("markup leaked into display text: " + display, display.contains("<col="));
			assertFalse("closing tag leaked: " + display, display.contains("</col>"));
			assertFalse("unexpanded macro leaked: " + display, display.contains("@mes_hl"));
		}
	}

	@Test
	public void noMarkupSurvivesToTheColourSpanPath()
	{
		for (String raw : REPORTED_MESSAGES)
		{
			List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
				ChatFadePlugin.stripIngestPrefixes(raw), PER_TYPE_FALLBACK);

			if (spans == null)
			{
				continue; // renders through the display-text path instead
			}

			for (ColorSpan span : spans)
			{
				assertFalse("markup leaked into a span: " + span.getText(),
					span.getText().contains("<col="));
			}
		}
	}

	@Test
	public void machineReadablePrefixesAreGoneToo()
	{
		for (String raw : REPORTED_MESSAGES)
		{
			String display = ChatFadePlugin.toDisplayText(ChatFadePlugin.stripIngestPrefixes(raw));
			assertFalse("CA_ID prefix leaked: " + display, display.contains("CA_ID"));
		}
	}

	// ── Symptom 2: colour actually comes through ────────────

	@Test
	public void colouredMessageProducesSpansInsteadOfFallingBack()
	{
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=ff3030>You have been frozen!</col>", PER_TYPE_FALLBACK);

		assertNotNull("coloured message must produce spans, not fall back", spans);
		assertFalse(spans.isEmpty());

		boolean anyNonFallback = spans.stream().anyMatch(s -> !s.getColor().equals(PER_TYPE_FALLBACK));
		assertTrue("at least one span must carry the message's own colour", anyNonFallback);
	}

	@Test
	public void colourIsCarriedNotJustStripped()
	{
		// Distinguishes a real fix from merely deleting the markup: the resulting colour
		// must be the one the game specified, not the per-type fallback.
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"<col=b060ff>Your Death Charge spell will be active.</col>", PER_TYPE_FALLBACK);

		assertNotNull(spans);
		assertEquals(1, spans.size());
		assertEquals("Your Death Charge spell will be active.", spans.get(0).getText());
		assertEquals(new Color(0xB060FF), spans.get(0).getColor());
	}

	@Test
	public void everyReportedMessageEitherColoursOrReadsCleanly()
	{
		for (String raw : REPORTED_MESSAGES)
		{
			String stripped = ChatFadePlugin.stripIngestPrefixes(raw);
			List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(stripped, PER_TYPE_FALLBACK);
			String display = ChatFadePlugin.toDisplayText(stripped);

			assertFalse("display text should never be empty for: " + raw, display.isEmpty());

			if (spans != null)
			{
				// Reassembling the spans must reproduce the display text exactly, so the
				// two render paths can never disagree about what the message says.
				StringBuilder joined = new StringBuilder();
				spans.forEach(s -> joined.append(s.getText()));
				assertEquals("span text and display text diverge for: " + raw,
					display, joined.toString());
			}
		}
	}

	// ── Player text is not markup ───────────────────────────

	@Test
	public void playerTypedAtTextIsUntouched()
	{
		// Previously a hand-rolled matcher could eat things like "@bob_smith@". Nothing in
		// the plugin inspects @ signs any more, so player text passes through verbatim.
		assertEquals("ping @bob@ and @bob_smith@ now",
			ChatFadePlugin.toDisplayText(
				ChatFadePlugin.stripIngestPrefixes("ping @bob@ and @bob_smith@ now")));
	}
}
