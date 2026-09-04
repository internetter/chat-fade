package com.chatfade;

import java.awt.Color;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The two symptoms reported in issue #21, asserted directly:
 *
 * <ol>
 *   <li>"It shows an internal string for colors" — the {@code @name@} token rendered as
 *       literal text in the overlay.</li>
 *   <li>"the 'Preserve In-Game Colors' option doesn't work" — a message coloured only by a
 *       token produced no spans, so it fell back to the flat per-type colour.</li>
 * </ol>
 *
 * <p>Both render paths are covered. When spans are produced the overlay draws those; when
 * they are null it draws the display text. Neither may contain a token.
 */
public class Issue21Test
{
	private static final Color PER_TYPE_FALLBACK = new Color(100, 200, 255);

	/** Every sample below is a shape the game actually emits. */
	private static final String[] REPORTED_MESSAGES = {
		"@mes_hl_red@You have been frozen!</col>",
		"@mes_hl_gre@The bush is already fruiting and won't benefit from any more pollen.</col>",
		"@mes_hl_pur@Your Death Charge spell will be active.</col>",
		"CA_ID:330|@mes_hl_pur@Sam completed a hard combat task: Mad Angel.</col>",
		"<col=ff0000>CA_ID:47|@mes_hl_yel@Sam completed an elite combat task.</col>",
	};

	// ── Symptom 1: no internal string on screen ─────────────

	@Test
	public void noTokenSurvivesToTheDisplayTextPath()
	{
		for (String raw : REPORTED_MESSAGES)
		{
			String display = ChatFadePlugin.toDisplayText(ChatFadePlugin.stripIngestPrefixes(raw));

			assertFalse("token leaked into display text: " + display, display.contains("@mes_hl"));
			assertFalse("angle-bracket markup leaked: " + display, display.contains("<col="));
			assertFalse("closing tag leaked: " + display, display.contains("</col>"));
		}
	}

	@Test
	public void noTokenSurvivesToTheColourSpanPath()
	{
		for (String raw : REPORTED_MESSAGES)
		{
			List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
				ChatFadePlugin.stripIngestPrefixes(raw), PER_TYPE_FALLBACK);

			if (spans == null)
			{
				continue; // that message renders through the display-text path instead
			}

			for (ColorSpan span : spans)
			{
				assertFalse("token leaked into a span: " + span.getText(),
					span.getText().contains("@mes_hl"));
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

	// ── Symptom 2: colours actually come back ───────────────

	@Test
	public void tokenOnlyMessageProducesColourInsteadOfFallingBack()
	{
		// Before the fix, a message with no <col= in it returned null here, which is why
		// "Preserve In-Game Colors" appeared to do nothing on these messages.
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"@mes_hl_red@You have been frozen!</col>", PER_TYPE_FALLBACK);

		assertNotNull("token-coloured message must produce spans, not fall back", spans);
		assertFalse(spans.isEmpty());

		boolean anyNonFallback = spans.stream().anyMatch(s -> !s.getColor().equals(PER_TYPE_FALLBACK));
		assertTrue("at least one span must carry the token's colour", anyNonFallback);
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

	@Test
	public void colourIsCarriedNotJustStripped()
	{
		// Distinguishes a real fix from merely deleting the token: the resulting colour
		// must be the token's, not the per-type fallback.
		List<ColorSpan> spans = ChatFadePlugin.parseColorSpans(
			"@mes_hl_pur@Your Death Charge spell will be active.</col>", PER_TYPE_FALLBACK);

		assertNotNull(spans);
		assertEquals(1, spans.size());
		assertEquals("Your Death Charge spell will be active.", spans.get(0).getText());
		assertEquals(ColorTokens.colorFor("mes_hl_pur"), spans.get(0).getColor());
	}
}
