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
