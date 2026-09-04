package com.chatfade;

import java.awt.Color;
import java.util.List;
import net.runelite.api.ChatMessageType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers removal of messages blocked by RuneLite's Chat Filter plugin.
 *
 * <p>The regression this guards against: matching blocked messages by text meant that with
 * the Chat Filter plugin's "Collapse game chat" / "Collapse player chat" options enabled —
 * where every duplicate is reported as blocked — the still-visible first copy was deleted
 * along with the duplicate.
 */
public class ChatFilterRemovalTest
{
	private ChatFadePlugin plugin;

	@Before
	public void setUp()
	{
		// The message list is initialised inline, so no injection is needed to exercise
		// removal on its own.
		plugin = new ChatFadePlugin();
	}

	private void add(int messageId, String text)
	{
		plugin.getMessages().add(FadingMessage.builder()
			.text(text)
			.type(ChatMessageType.GAMEMESSAGE)
			.timestamp(System.currentTimeMillis())
			.color(Color.WHITE)
			.messageId(messageId)
			.build());
	}

	private List<FadingMessage> remaining()
	{
		return plugin.getMessages();
	}

	@Test
	public void removesTheBlockedMessage()
	{
		add(1, "buying gf 1m");
		add(2, "You feel a surge of power.");

		plugin.removeBlockedMessage(1);

		assertEquals(1, remaining().size());
		assertEquals("You feel a surge of power.", remaining().get(0).getText());
	}

	@Test
	public void keepsEarlierDuplicateWhenACollapsedCopyIsBlocked()
	{
		// The collapse regression, stated directly: two messages with identical text but
		// distinct ids. Blocking the second must not take the first with it.
		add(10, "You have killed a Mad Angel.");
		add(11, "You have killed a Mad Angel.");

		plugin.removeBlockedMessage(11);

		assertEquals(1, remaining().size());
		assertEquals(10, remaining().get(0).getMessageId());
	}

	@Test
	public void keepsEarlierDuplicatesAcrossARunOfCollapsedMessages()
	{
		add(10, "You have killed a Mad Angel.");
		for (int id = 11; id <= 14; id++)
		{
			add(id, "You have killed a Mad Angel.");
			plugin.removeBlockedMessage(id);
		}

		assertEquals("only the first copy should survive a collapsed run", 1, remaining().size());
		assertEquals(10, remaining().get(0).getMessageId());
	}

	@Test
	public void ignoresReplayedMessagesWithNoStableId()
	{
		add(1, "buying gf 1m");
		add(-1, "replayed public chat");

		plugin.removeBlockedMessage(-1);

		assertEquals("a -1 id must not match anything", 2, remaining().size());
	}

	@Test
	public void unknownIdRemovesNothing()
	{
		add(1, "one");
		add(2, "two");

		plugin.removeBlockedMessage(99);

		assertEquals(2, remaining().size());
	}

	@Test
	public void identicalTextIsNotSufficientToRemove()
	{
		add(5, "Your Death Charge spell will be active.");
		add(6, "Your Death Charge spell will be active.");
		add(7, "Your Death Charge spell will be active.");

		plugin.removeBlockedMessage(6);

		assertEquals(2, remaining().size());
		assertTrue(remaining().stream().anyMatch(m -> m.getMessageId() == 5));
		assertTrue(remaining().stream().anyMatch(m -> m.getMessageId() == 7));
	}
}
