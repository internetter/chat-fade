package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the message stack sits relative to the typing line (#34, reported by @hydroxiide).
 *
 * <p>The two were positioned by separate calculations from different reference points, and the
 * room for the typing line was reserved twice: once by pushing the stack up a line, and again
 * by anchoring the typing line to the bottom independently. The result was an empty gap about
 * a line tall that no setting could remove, and a stack that jumped the moment you typed.
 *
 * <p>Anchored mode never had the gap, because it puts the typing line directly after the
 * messages. These tests hold the two modes to the same spacing.
 */
public class OverlayLayoutTest
{
	private static final int LINE_HEIGHT = 16;
	private static final int LINE_SPACING = 1;
	private static final int ANCHOR = 400;

	/** Where the typing line is drawn: the anchor itself. */
	private static int typingBaseline()
	{
		return ANCHOR;
	}

	private static int lastMessageBaseline(int messageCount)
	{
		return ChatFadeOverlay.firstMessageBaseline(ANCHOR, LINE_HEIGHT, LINE_SPACING, messageCount)
			+ (messageCount - 1) * (LINE_HEIGHT + LINE_SPACING);
	}

	/**
	 * The reservation bug, and the test that catches it coming back. Reinstating the "push the
	 * stack up a line for the typing input" offset inside {@code firstMessageBaseline} doubles
	 * this gap, which is exactly what it looked like on screen.
	 */
	@Test
	public void theGapToTheTypingLineIsOneLine()
	{
		// Was 2 * lineHeight + lineSpacing, i.e. a blank line's worth of dead space.
		assertEquals(LINE_HEIGHT, typingBaseline() - lastMessageBaseline(3));
	}

	@Test
	public void theGapIsTheSameWhateverTheStackHeight()
	{
		for (int count = 1; count <= 8; count++)
		{
			assertEquals("messageCount=" + count,
				LINE_HEIGHT, typingBaseline() - lastMessageBaseline(count));
		}
	}

	@Test
	public void theLowestMessageStaysClearOfTheAnchor()
	{
		// The anchor is the chatbox top, or the topmost split private message, less the
		// configured padding. Drawing at or past it is what put the overlay on top of split
		// private chat in the first place.
		assertTrue(lastMessageBaseline(5) < ANCHOR);
	}

	@Test
	public void aSingleMessageSitsOneLineAboveTheAnchor()
	{
		assertEquals(ANCHOR - LINE_HEIGHT,
			ChatFadeOverlay.firstMessageBaseline(ANCHOR, LINE_HEIGHT, LINE_SPACING, 1));
	}

	@Test
	public void eachExtraMessageRaisesTheStackByExactlyOneLineStep()
	{
		int one = ChatFadeOverlay.firstMessageBaseline(ANCHOR, LINE_HEIGHT, LINE_SPACING, 1);
		int two = ChatFadeOverlay.firstMessageBaseline(ANCHOR, LINE_HEIGHT, LINE_SPACING, 2);

		assertEquals(LINE_HEIGHT + LINE_SPACING, one - two);
	}

	@Test
	public void lineSpacingOfZeroStacksMessagesByTheFontHeightAlone()
	{
		int one = ChatFadeOverlay.firstMessageBaseline(ANCHOR, LINE_HEIGHT, 0, 1);
		int two = ChatFadeOverlay.firstMessageBaseline(ANCHOR, LINE_HEIGHT, 0, 2);

		assertEquals(LINE_HEIGHT, one - two);
	}

	// ── Whether the chatbox counts as out of the way ────────

	private static boolean away(boolean collapsed, boolean prompt, boolean remap, boolean inputHidden)
	{
		return ChatFadeOverlay.chatboxIsAway(collapsed, prompt, remap, inputHidden);
	}

	@Test
	public void aCollapsedChatAreaSettlesItOnItsOwn()
	{
		assertTrue(away(true, false, false, false));
		assertTrue("even with a prompt up", away(true, true, false, false));
	}

	@Test
	public void aPromptUsingTheChatAreaMeansTheChatboxIsNotAway()
	{
		// The reported case: composing a private message with Press Enter to Chat on. The
		// prompt replaces the chat input, so the input reads as hidden, and the overlay used
		// to conclude the chatbox was gone and draw over it and its split private messages.
		assertFalse("composing a private message", away(false, true, true, true));
		assertFalse("a search or amount prompt", away(false, true, false, true));
	}

	@Test
	public void keyRemappingStillShowsTheOverlayOverAnOpenChatbox()
	{
		// The behaviour the hidden-input check exists for, and which the prompt gate must not
		// break: Press Enter to Chat hides the input while the chat stays open.
		assertTrue(away(false, false, true, true));
	}

	@Test
	public void anOpenChatboxWithAVisibleInputIsNotAway()
	{
		assertFalse(away(false, false, true, false));
		assertFalse(away(false, false, false, false));
	}

	@Test
	public void aHiddenInputAloneIsNotEnoughWithoutKeyRemapping()
	{
		// The game hides the input briefly whenever a private message is sent. Treating that
		// as a collapsed chatbox is what made the overlay flash back over an open chat.
		assertFalse(away(false, false, false, true));
	}

	// ── Anchored overlay lifting clear of split private chat ──

	private static int push(int boxBottom, int pmTop)
	{
		return ChatFadeOverlay.splitPmPush(boxBottom, pmTop, 4);
	}

	@Test
	public void noPrivateMessagesMeansNoPush()
	{
		// -1 is "none showing". The overlay keeps exactly the height it always had.
		assertEquals(0, push(460, -1));
		assertEquals(0, push(460, 0));
	}

	@Test
	public void messagesBelowTheOverlayNeedNoPush()
	{
		// The whole stack already sits above them.
		assertEquals(0, push(300, 400));
	}

	@Test
	public void theOverlayGrowsByHowFarTheMessagesReachIntoIt()
	{
		// Bottom edge at 460, messages start at 420: they intrude 40px, plus the 4px gap.
		assertEquals(44, push(460, 420));
	}

	@Test
	public void theGapIsIncludedSoTheyDoNotEndUpTouching()
	{
		// Exactly flush without the padding, which would leave the two lines adjacent.
		assertEquals(4, push(460, 460));
	}

	@Test
	public void thePushIsStableWhenTheOverlayGrows()
	{
		// The answer feeds the overlay's own height, so it has to be independent of that
		// height or it would run away. A BOTTOM_LEFT overlay keeps its bottom edge whatever
		// its height, so the same bottom edge must always give the same answer.
		int first = push(460, 420);
		int afterGrowing = push(460, 420);

		assertEquals(first, afterGrowing);
	}

	@Test
	public void aTallerStackOfMessagesPushesFurther()
	{
		assertTrue(push(460, 400) > push(460, 430));
	}

	@Test
	public void thePushIsNeverNegative()
	{
		// A negative push would pull the overlay down into the chatbox.
		assertEquals(0, push(100, 460));
	}

	// --- Negative line spacing (#42, asked for by @aelikh) ---

	@Test
	public void negativeSpacingTightensTheStack()
	{
		// Each gap shrinks by one pixel per step, so three messages sit two pixels lower
		// than they would at zero spacing — closer to the bottom anchor, not further.
		int atZero = ChatFadeOverlay.firstMessageBaseline(400, 12, 0, 3);
		int atMinusOne = ChatFadeOverlay.firstMessageBaseline(400, 12, -1, 3);

		assertTrue(atMinusOne > atZero);
		assertEquals(2, atMinusOne - atZero);
	}

	@Test
	public void theBottomAnchorIsUnmovedByNegativeSpacing()
	{
		// Spacing goes between the lines, so a single message starts in the same place
		// whatever the spacing is. Only a stack can tighten.
		assertEquals(ChatFadeOverlay.firstMessageBaseline(400, 12, 0, 1),
			ChatFadeOverlay.firstMessageBaseline(400, 12, -5, 1));
	}

	@Test
	public void theTightestAllowedSpacingStillLeavesLinesApart()
	{
		// -5 is the configured floor. With the smallest font the plugin offers it must still
		// advance between lines, or messages would print on top of each other.
		int smallestLineHeight = 9;
		int blockOfTwo = ChatFadeOverlay.firstMessageBaseline(400, smallestLineHeight, -5, 1)
			- ChatFadeOverlay.firstMessageBaseline(400, smallestLineHeight, -5, 2);

		assertTrue("lines would overlap: " + blockOfTwo, blockOfTwo > 0);
	}
}
