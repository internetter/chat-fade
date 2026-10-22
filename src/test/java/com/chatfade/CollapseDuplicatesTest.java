package com.chatfade;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Collapse matching is pure given the message list, so it is tested directly.
 *
 * <p>The feature was proposed as #30 by @cerkie against the Chat Filter plugin's own collapse
 * settings; this version counts repeats itself so it works without that plugin.
 */
public class CollapseDuplicatesTest
{
	private static final long LIFETIME = 10_000L;
	private static final long NOW = 1_000_000L;

	private static FadingMessage msg(String sender, String text, ChatMessageType type, long age)
	{
		return FadingMessage.builder()
			.senderName(sender)
			.text(text)
			.type(type)
			.timestamp(NOW - age)
			.color(Color.WHITE)
			.messageId(1)
			.build();
	}

	private static FadingMessage find(List<FadingMessage> messages, String text, String sender, ChatMessageType type)
	{
		return ChatFadePlugin.collapseTarget(messages, text, sender, type, LIFETIME, NOW);
	}

	private static List<FadingMessage> listOf(FadingMessage... items)
	{
		List<FadingMessage> list = new ArrayList<>();
		for (FadingMessage m : items)
		{
			list.add(m);
		}
		return list;
	}

	@Test
	public void findsAnIdenticalRecentMessage()
	{
		FadingMessage first = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 500);

		assertSame(first, find(listOf(first), "hello", "Bob", ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void returnsNothingWhenTheTextDiffers()
	{
		FadingMessage first = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 500);

		assertNull(find(listOf(first), "goodbye", "Bob", ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void doesNotCollapseAcrossSenders()
	{
		// Two people saying the same thing are two events, not a repeat.
		FadingMessage bob = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 500);

		assertNull(find(listOf(bob), "hello", "Alice", ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void doesNotCollapseAcrossMessageTypes()
	{
		FadingMessage pub = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 500);

		assertNull(find(listOf(pub), "hello", "Bob", ChatMessageType.CLAN_CHAT));
	}

	@Test
	public void doesNotReviveAMessageThatHasAlreadyFadedOut()
	{
		FadingMessage old = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, LIFETIME + 1);

		assertNull(find(listOf(old), "hello", "Bob", ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void collapsesGameMessagesWhichHaveNoSender()
	{
		FadingMessage game = msg(null, "You find some coins.", ChatMessageType.GAMEMESSAGE, 100);

		assertSame(game, find(listOf(game), "You find some coins.", null, ChatMessageType.GAMEMESSAGE));
	}

	@Test
	public void treatsAnEmptySenderAsNoSender()
	{
		FadingMessage game = msg("", "You find some coins.", ChatMessageType.GAMEMESSAGE, 100);

		assertSame(game, find(listOf(game), "You find some coins.", null, ChatMessageType.GAMEMESSAGE));
	}

	@Test
	public void prefersTheMostRecentMatch()
	{
		FadingMessage older = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 5000);
		FadingMessage newer = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 100);

		assertSame(newer, find(listOf(older, newer), "hello", "Bob", ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void skipsAFadedCopyToReachALiveOne()
	{
		FadingMessage expired = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, LIFETIME + 1);
		FadingMessage live = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 200);

		assertSame(live, find(listOf(expired, live), "hello", "Bob", ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void handlesAnEmptyListAndNullText()
	{
		assertNull(find(new ArrayList<>(), "hello", "Bob", ChatMessageType.PUBLICCHAT));
		assertNull(find(listOf(msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 100)), null, "Bob",
			ChatMessageType.PUBLICCHAT));
	}

	@Test
	public void aNewMessageStartsAtACountOfOne()
	{
		assertEquals(1, msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 0).getCount());
	}

	@Test
	public void movesACollapsedLineToTheNewestPosition()
	{
		// A repeat is the newest event, so it should not stay in the slot its first copy took.
		FadingMessage repeated = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 5000);
		FadingMessage other = msg("Alice", "hi", ChatMessageType.PUBLICCHAT, 100);
		List<FadingMessage> messages = listOf(repeated, other);

		ChatFadePlugin.moveToNewest(messages, repeated);

		assertEquals(other, messages.get(0));
		assertSame("the repeat belongs last, where the newest message lives", repeated, messages.get(1));
	}

	@Test
	public void leavesAMessageThatIsAlreadyNewestAlone()
	{
		// The usual case — a repeat follows its twin immediately — and worth not paying for.
		FadingMessage older = msg("Alice", "hi", ChatMessageType.PUBLICCHAT, 5000);
		FadingMessage newest = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 100);
		List<FadingMessage> messages = listOf(older, newest);

		ChatFadePlugin.moveToNewest(messages, newest);

		assertSame(older, messages.get(0));
		assertSame(newest, messages.get(1));
		assertEquals(2, messages.size());
	}

	@Test
	public void keepsTheOrderOfEverythingElse()
	{
		FadingMessage a = msg("A", "one", ChatMessageType.PUBLICCHAT, 9000);
		FadingMessage b = msg("B", "two", ChatMessageType.PUBLICCHAT, 8000);
		FadingMessage c = msg("C", "three", ChatMessageType.PUBLICCHAT, 7000);
		List<FadingMessage> messages = listOf(a, b, c);

		ChatFadePlugin.moveToNewest(messages, b);

		assertSame(a, messages.get(0));
		assertSame(c, messages.get(1));
		assertSame(b, messages.get(2));
	}

	@Test
	public void movesTheMessageItWasGivenNotOneThatMerelyLooksLikeIt()
	{
		// FadingMessage is a Lombok @Data class, so two messages with the same field values
		// are equal. Removing by equality would pull out the wrong one, leaving the target
		// in the list twice and dropping its twin entirely.
		FadingMessage first = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 500);
		FadingMessage identical = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 500);
		FadingMessage other = msg("Alice", "hi", ChatMessageType.PUBLICCHAT, 400);
		assertEquals("these must be equal for the test to mean anything", first, identical);

		List<FadingMessage> messages = listOf(first, identical, other);

		ChatFadePlugin.moveToNewest(messages, identical);

		assertEquals(3, messages.size());
		assertSame("the twin must still be there", first, messages.get(0));
		assertSame(other, messages.get(1));
		assertSame(identical, messages.get(2));
	}

	@Test
	public void ignoresAMessageThatIsNotInTheList()
	{
		FadingMessage present = msg("Bob", "hello", ChatMessageType.PUBLICCHAT, 100);
		FadingMessage absent = msg("Carol", "elsewhere", ChatMessageType.PUBLICCHAT, 100);
		List<FadingMessage> messages = listOf(present);

		ChatFadePlugin.moveToNewest(messages, absent);

		assertEquals(1, messages.size());
		assertSame(present, messages.get(0));
	}

	@Test
	public void collapsesOntoAlreadyCensoredText()
	{
		// Repeats of a filtered message match on the censored text, so they fold together
		// rather than stacking up as separate starred lines.
		FadingMessage censored = FadingMessage.builder()
			.senderName("Bob")
			.text("***")
			.type(ChatMessageType.PUBLICCHAT)
			.timestamp(NOW - 100)
			.color(Color.WHITE)
			.messageId(1)
			.censored(true)
			.build();

		assertSame(censored, find(listOf(censored), "***", "Bob", ChatMessageType.PUBLICCHAT));
	}
}
