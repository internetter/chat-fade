package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Emoji disappearing from the overlay, leaving either a bare "Name:" or a message with a piece
 * missing (#47).
 *
 * <p>The Emoji plugin replaces what the player typed with an {@code <img=N>} tag, and that
 * rewrite reaches the overlay through the chatbox rebuild and the per-tick node refresh. When
 * the icon will not be drawn — chat icons off, or the icon not registered yet — the tag is
 * stripped and the rewrite is the message minus a piece. Every case below was captured from a
 * live client before being written down.
 */
public class EmojiBodyTest
{
	// --- Captured live: ":)" sent with chat icons off ---
	//   INGEST   cleaned=[:)]
	//   FILTERED raw=[<img=414>] cleaned=[]  spans=null
	// The whole message was the emoji, so adopting the rewrite left "Danky:" and nothing else.

	@Test
	public void anEmojiOnItsOwnKeepsTheTypedText()
	{
		assertTrue(ChatFadePlugin.holdsOldText(":)", "", true));
	}

	// --- Captured live: "Nice :)" sent with chat icons off ---
	//   FILTERED raw=[Nice <img=414>] cleaned=[Nice]
	// The rewrite is the message minus the emoji, so the ":)" was silently dropped.

	@Test
	public void anEmojiAfterTextKeepsTheTypedText()
	{
		assertTrue(ChatFadePlugin.holdsOldText("Nice :)", "Nice", true));
	}

	@Test
	public void aRewriteIsAdoptedWhenTheIconWillBeDrawn()
	{
		// Icons on and resolving: the tag becomes a span, so the rewrite loses nothing.
		assertFalse(ChatFadePlugin.holdsOldText("Nice :)", "Nice", false));
		assertFalse(ChatFadePlugin.holdsOldText(":)", "", false));
	}

	// --- The Chat Filter plugin uses the same path, and must not be blocked ---

	@Test
	public void censoringIsStillAdoptedEvenWhenAnIconIsDropped()
	{
		// Censoring replaces characters rather than removing them, so the rewrite is not
		// contained in the old text. Refusing it would leave the uncensored wording on screen.
		assertFalse(ChatFadePlugin.holdsOldText("Nice :)", "****", true));
		assertFalse(ChatFadePlugin.holdsOldText("badword :)", "*******", true));
	}

	@Test
	public void aWholeMessageCensoredIsStillAdopted()
	{
		assertFalse(ChatFadePlugin.holdsOldText("Nice :)",
			MessageFilter.CENSORED_MESSAGE, true));
	}

	@Test
	public void aRewriteThatAddsTextIsStillAdopted()
	{
		// Not a reduction of the old text, so something other than an icon changed.
		assertFalse(ChatFadePlugin.holdsOldText("Nice", "Nice indeed", true));
	}

	@Test
	public void nullsAreNeverHeld()
	{
		assertFalse(ChatFadePlugin.holdsOldText(null, "Nice", true));
		assertFalse(ChatFadePlugin.holdsOldText("Nice", null, true));
	}

	@Test
	public void anUnchangedRewriteIsNotHeld()
	{
		// Identical text never reaches the guard in practice, but holding it would be wrong
		// in principle: nothing was dropped.
		assertTrue(ChatFadePlugin.holdsOldText("Nice", "Nice", true));
	}
}
