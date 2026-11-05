package com.chatfade;

import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * An NPC line that goes over their head *and* into the chatbox arrives as {@code NPC_SAY},
 * not {@code DIALOG} — Mudfoot shouting mid-quest, Minimus greeting you at the Colosseum.
 *
 * <p>It carries the speaker in the same {@code "Name|what they said"} form, but only DIALOG
 * and MESBOX were split, so those lines rendered with the pipe showing and in the plain game
 * message colour while the dialogue box lines beside them were gold.
 */
public class NpcSpeechTest
{
	private static String[] split(String text)
	{
		return ChatFadePlugin.splitSpeaker(text);
	}

	// ── Which types are somebody speaking ───────────────────

	@Test
	public void npcOverheadSpeechCountsAsSpeech()
	{
		assertTrue(ChatFadePlugin.isNpcSpeech(ChatMessageType.NPC_SAY));
	}

	@Test
	public void theDialogueBoxTypesStillCount()
	{
		assertTrue(ChatFadePlugin.isNpcSpeech(ChatMessageType.DIALOG));
		assertTrue(ChatFadePlugin.isNpcSpeech(ChatMessageType.MESBOX));
	}

	@Test
	public void ordinaryChatIsNotSpeechForThisPurpose()
	{
		// Public chat already has a sender of its own and must never be pipe-split.
		assertFalse(ChatFadePlugin.isNpcSpeech(ChatMessageType.PUBLICCHAT));
		assertFalse(ChatFadePlugin.isNpcSpeech(ChatMessageType.GAMEMESSAGE));
		assertFalse(ChatFadePlugin.isNpcSpeech(ChatMessageType.CLAN_CHAT));
	}

	// ── Splitting the speaker off ───────────────────────────

	@Test
	public void splitsTheNameFromTheSpeech()
	{
		assertArrayEquals(new String[]{"Mudfoot", "Look! There she is!"},
			split("Mudfoot|Look! There she is!"));
	}

	@Test
	public void keepsPunctuationInTheSpeech()
	{
		// The reported line, which is also a good test that nothing trims too eagerly.
		assertArrayEquals(new String[]{"Outlaw", "H.A.M. will get rid of people like you!"},
			split("Outlaw|H.A.M. will get rid of people like you!"));
	}

	@Test
	public void splitsOnTheFirstPipeOnly()
	{
		// A second pipe belongs to the speech, not to another name.
		assertArrayEquals(new String[]{"Minimus", "Ready? Then let us begin | round one"},
			split("Minimus|Ready? Then let us begin | round one"));
	}

	@Test
	public void trimsSpaceAroundBothHalves()
	{
		assertArrayEquals(new String[]{"Mudfoot", "Okay. Me behind you."},
			split("Mudfoot | Okay. Me behind you. "));
	}

	@Test
	public void leavesAMessageWithNoPipeAlone()
	{
		// The caller keeps the message as it found it, rather than inventing an empty sender.
		assertNull(split("You feel a surge of power."));
	}

	@Test
	public void handlesNullAndEmpty()
	{
		assertNull(split(null));
		assertNull(split(""));
	}

	@Test
	public void handlesAnEmptyNameOrEmptySpeech()
	{
		assertArrayEquals(new String[]{"", "something"}, split("|something"));
		assertArrayEquals(new String[]{"Mudfoot", ""}, split("Mudfoot|"));
	}

	// ── Colour ──────────────────────────────────────────────

	@Test
	public void overheadSpeechTakesTheNpcDialogueColourNotTheGameMessageOne()
	{
		// In the report the same NPC appeared gold on one line and white on the next, because
		// only DIALOG reached the dialogue colour.
		assertTrue(ChatFadePlugin.getColorForType(ChatMessageType.NPC_SAY)
			.equals(ChatFadePlugin.getColorForType(ChatMessageType.DIALOG)));
	}

	@Test
	public void overheadSpeechNoLongerLooksLikeAGameMessage()
	{
		assertFalse(ChatFadePlugin.getColorForType(ChatMessageType.NPC_SAY)
			.equals(ChatFadePlugin.getColorForType(ChatMessageType.GAMEMESSAGE)));
	}
}
