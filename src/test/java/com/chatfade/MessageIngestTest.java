package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * End-to-end coverage of the text pipeline a chat message goes through on ingest:
 * prefix stripping, then reduction to display text.
 *
 * <p>Inputs are in the form the pipeline actually receives: the game's {@code @name@}
 * colour macros are expanded to {@code <col=...>} by
 * {@link net.runelite.api.Client#macroExpand} before any of this runs.
 */
public class MessageIngestTest
{
	private static String ingest(String raw)
	{
		return ChatFadePlugin.toDisplayText(ChatFadePlugin.stripIngestPrefixes(raw));
	}

	// ── Issue #21: @name@ colour tokens ─────────────────────

	@Test
	public void stripsFreezeMessageMarkup()
	{
		assertEquals("You have been frozen!",
			ingest("<col=ff3030>You have been frozen!</col>"));
	}

	@Test
	public void stripsFarmingMessageMarkup()
	{
		assertEquals("The bush is already fruiting and won't benefit from any more pollen.",
			ingest("<col=30ff30>The bush is already fruiting and won't benefit from any more pollen.</col>"));
	}

	@Test
	public void stripsNestedMarkup()
	{
		assertEquals("Congratulations, you found a rare item!",
			ingest("<col=b060ff>Congratulations, <col=ff0000>you found a rare item!</col></col>"));
	}

	// ── Existing prefix stripping must survive ──────────────

	@Test
	public void stripsCombatAchievementPrefix()
	{
		assertEquals("Sam completed a hard combat task: Mad Angel.",
			ingest("CA_ID:330|Sam completed a hard combat task: Mad Angel."));
	}

	@Test
	public void stripsCombatAchievementPrefixBehindColourTags()
	{
		assertEquals("Sam completed a hard combat task.",
			ingest("<col=ff0000>CA_ID:330|Sam completed a hard combat task."));
	}

	@Test
	public void stripsLevelUpSkillIdPrefix()
	{
		assertEquals("Check the skill guide for more information.",
			ingest("24|Check the skill guide for more information."));
	}

	@Test
	public void combinesPrefixStrippingWithMarkupStripping()
	{
		// The case issue #21 was actually reported against: a CA message that also
		// carries colour markup.
		assertEquals("Sam completed a hard combat task: Mad Angel.",
			ingest("CA_ID:330|<col=b060ff>Sam completed a hard combat task: Mad Angel.</col>"));
	}

	// ── Ordinary messages pass through unchanged ────────────

	@Test
	public void leavesPlainMessagesAlone()
	{
		assertEquals("You feel a surge of power.", ingest("You feel a surge of power."));
	}

	@Test
	public void doesNotStripPlayerAtText()
	{
		// Nothing in the plugin inspects @ signs any more, so player text is verbatim.
		assertEquals("ping @bob@ and @bob_smith@ when you're on",
			ingest("ping @bob@ and @bob_smith@ when you're on"));
	}

	@Test
	public void doesNotMistakeLeadingNumberForSkillId()
	{
		// The skill-id rule requires a trailing pipe; a message that merely starts with
		// digits must survive intact.
		assertEquals("20 Kingdom favour gained.", ingest("20 Kingdom favour gained."));
	}

	@Test
	public void handlesNullSafely()
	{
		assertEquals("", ingest(null));
	}
}
