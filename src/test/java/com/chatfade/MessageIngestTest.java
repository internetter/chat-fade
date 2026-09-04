package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * End-to-end coverage of the text pipeline a chat message goes through on ingest:
 * prefix stripping, then reduction to display text.
 *
 * <p>Where possible the inputs are real game strings. The two {@code @mes_hl_*@} messages
 * are taken verbatim from RuneLite core, which matches on them as literals
 * (TimersAndBuffsPlugin and WoodcuttingPlugin), so they are known-accurate samples of the
 * syntax that broke in issue #21.
 */
public class MessageIngestTest
{
	private static String ingest(String raw)
	{
		return ChatFadePlugin.toDisplayText(ChatFadePlugin.stripIngestPrefixes(raw));
	}

	// ── Issue #21: @name@ colour tokens ─────────────────────

	@Test
	public void stripsFreezeMessageToken()
	{
		// Verbatim from RuneLite's TimersAndBuffsPlugin.
		assertEquals("You have been frozen!",
			ingest("@mes_hl_red@You have been frozen!</col>"));
	}

	@Test
	public void stripsFarmingMessageToken()
	{
		// Verbatim from RuneLite's WoodcuttingPlugin.
		assertEquals("The bush is already fruiting and won't benefit from any more pollen.",
			ingest("@mes_hl_gre@The bush is already fruiting and won't benefit from any more pollen.</col>"));
	}

	@Test
	public void stripsTokenAndAngleBracketMarkupTogether()
	{
		assertEquals("Congratulations, you found a rare item!",
			ingest("@mes_hl_pur@Congratulations, <col=ff0000>you found a rare item!</col></col>"));
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
	public void combinesPrefixStrippingWithTokenStripping()
	{
		// The case issue #21 was actually reported against: a CA message that also
		// carries the newer colour syntax.
		assertEquals("Sam completed a hard combat task: Mad Angel.",
			ingest("CA_ID:330|@mes_hl_pur@Sam completed a hard combat task: Mad Angel.</col>"));
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
		assertEquals("ping @bob@ when you're on", ingest("ping @bob@ when you're on"));
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
