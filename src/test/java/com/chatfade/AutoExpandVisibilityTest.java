package com.chatfade;

import net.runelite.api.vars.InputType;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The decision behind Fixed Mode's auto-expand, and the circularity it used to contain.
 *
 * <p>The check consulted {@code Widget.isHidden()}, which walks the parent chain. The chatbox
 * frame is in that chain, and hiding it is the whole point of the feature — so a dialogue inside
 * it always read as hidden, the chatbox was never put back, and the dialogue stayed hidden. The
 * feature was the reason its own condition was false.
 *
 * <p>It looked intermittent rather than broken because a dialogue opened <em>during</em> a
 * conversation inherited an already-reopened chatbox and read as visible. Only the first
 * interaction failed: searching a shelf, or clicking Buy at the Grand Exchange.
 *
 * <p>Everything below is phrased in each widget's own hidden flag, which is the only thing this
 * decision may depend on.
 */
public class AutoExpandVisibilityTest
{
	private static boolean open(boolean selfHidden, boolean... children)
	{
		return ChatFadePlugin.countsAsOpen(selfHidden, children);
	}

	@Test
	public void aDialogueWithAVisibleChildIsOpen()
	{
		assertTrue(open(false, false));
	}

	@Test
	public void aDialogueWhoseChildrenAreAllHiddenIsNotOpen()
	{
		// The game hides the components when the dialogue goes away.
		assertFalse(open(false, true, true, true));
	}

	@Test
	public void oneVisibleChildAmongHiddenOnesIsEnough()
	{
		assertTrue(open(false, true, true, false, true));
	}

	@Test
	public void aWidgetHiddenInItsOwnRightIsNeverOpen()
	{
		assertFalse("its own flag still decides", open(true, false, false));
	}

	@Test
	public void aWidgetWithNoChildrenCountsOnItsOwnFlag()
	{
		assertTrue(open(false));
		assertFalse(open(true));
	}

	@Test
	public void handlesNoChildArrayAtAll()
	{
		assertTrue(ChatFadePlugin.countsAsOpen(false, null));
		assertFalse(ChatFadePlugin.countsAsOpen(true, null));
	}

	// ── Which test applies to which entry ───────────────────

	@Test
	public void theChatboxOwnComponentsKeepTheParentAwareTest()
	{
		// 162's components are always present and always self-visible. Judging them on their
		// own flag matched forever, which pinned auto-expand on: the chatbox never closed and
		// the overlay never drew — no messages, no typing line, no caret. Observed live as
		// "auto-expand match now [162:42]", logged once and never changing again.
		assertTrue(ChatFadePlugin.needsParentAwareTest(162));
	}

	@Test
	public void separateDialogueInterfacesDoNot()
	{
		// These are the ones nested inside the hidden frame, where the circularity lives.
		assertFalse("CHAT_LEFT, an NPC speaking", ChatFadePlugin.needsParentAwareTest(231));
		assertFalse("CHAT_RIGHT, you speaking", ChatFadePlugin.needsParentAwareTest(217));
		assertFalse("CHATMENU", ChatFadePlugin.needsParentAwareTest(219));
		assertFalse("MESSAGEBOX", ChatFadePlugin.needsParentAwareTest(229));
		assertFalse("OBJECTBOX", ChatFadePlugin.needsParentAwareTest(193));
		assertFalse("OBJECTBOX_DOUBLE", ChatFadePlugin.needsParentAwareTest(11));
		assertFalse("SKILLMULTI", ChatFadePlugin.needsParentAwareTest(270));
	}

	/**
	 * The regression itself: a dialogue the game has shown must count as open even though
	 * everything above it in the widget tree is hidden.
	 *
	 * <p>There is no parent in this signature, and that is the point — the old code reached for
	 * one and deadlocked on it. If a parent flag ever creeps back into the decision, it will
	 * have to come through this method, and this test is where that gets noticed.
	 */
	@Test
	public void theHiddenChatboxAroundItIsNotPartOfTheDecision()
	{
		// Exactly the shelf-search case: OBJECTBOX_DOUBLE shown by the game, chatbox frame
		// hidden by us, no preceding conversation to have reopened it.
		assertTrue("a shown dialogue inside a hidden chatbox must still reopen it",
			open(false, false));
	}

	// ── The input prompt, asked of the client rather than the widget tree ──

	@Test
	public void noInputPromptMeansNoMatch()
	{
		assertFalse(ChatFadePlugin.isInputPrompt(InputType.NONE.getType()));
	}

	@Test
	public void theGameOwnPromptsCount()
	{
		// SEARCH is the one the Grand Exchange's "Buy" opens, and the case that went
		// unrecognised for as long as this was decided by hunting for a widget: the search
		// has no component that is reliably present and reliably hidden when it is not up.
		assertTrue("item/bank search", ChatFadePlugin.isInputPrompt(InputType.SEARCH.getType()));
		assertTrue("addressing a player",
			ChatFadePlugin.isInputPrompt(InputType.PRIVATE_MESSAGE.getType()));
	}

	@Test
	public void runeLiteOwnChatboxPanelsCount()
	{
		// Negative values, which is why this is phrased as "not NONE" rather than "> 0".
		assertTrue(ChatFadePlugin.isInputPrompt(InputType.RUNELITE.getType()));
		assertTrue(ChatFadePlugin.isInputPrompt(InputType.RUNELITE_CHATBOX_PANEL.getType()));
	}

	@Test
	public void anUnrecognisedPromptStillCounts()
	{
		// The enum names only the handful RuneLite has a use for. The numeric entry prompts
		// are not among them, and a prompt we cannot name is still borrowing the chat area.
		assertTrue(ChatFadePlugin.isInputPrompt(3));
		assertTrue(ChatFadePlugin.isInputPrompt(14));
		assertTrue(ChatFadePlugin.isInputPrompt(-7));
	}
}
