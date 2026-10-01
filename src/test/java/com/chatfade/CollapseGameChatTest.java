package com.chatfade;

import java.awt.Color;
import java.util.List;
import net.runelite.api.ChatMessageType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Covers mirroring the Chat Filter plugin's "Collapse game chat" / "Collapse player chat":
 * repeated messages update the existing floating entry in place with the filter's live count,
 * rather than popping a new floating line each time.
 */
public class CollapseGameChatTest
{
	private static final long LIFETIME_MS = 10_000;
	private static final long NOW = 1_000_000;

	private ChatFadePlugin plugin;

	@Before
	public void setUp()
	{
		plugin = new ChatFadePlugin();
		plugin.config = new ChatFadeConfig() {};
	}

	private FadingMessage entry(int messageId, String text)
	{
		FadingMessage msg = FadingMessage.builder()
			.text(text)
			.rawText(text)
			.type(ChatMessageType.GAMEMESSAGE)
			.timestamp(NOW)
			.color(Color.WHITE)
			.messageId(messageId)
			.build();
		plugin.getMessages().add(msg);
		return msg;
	}

	@Test
	public void stripsTheCollapseCounterSuffix()
	{
		assertEquals("You have killed a Mad Angel.",
			ChatFadePlugin.stripCountSuffix("You have killed a Mad Angel. (3)"));
		assertEquals("Buy 2 milk", ChatFadePlugin.stripCountSuffix("Buy 2 milk"));
		assertNull(ChatFadePlugin.stripCountSuffix(null));
	}

	@Test
	public void collapseTargetFindsTheNewestMatchingEntry()
	{
		entry(1, "other message");
		entry(2, "You have killed a Mad Angel.");

		FadingMessage target = ChatFadePlugin.collapseTarget(
			plugin.getMessages(), "You have killed a Mad Angel.", null,
			ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW);

		assertEquals(2, target.getMessageId());
	}

	@Test
	public void collapseTargetTreatsEmptySenderAsNull()
	{
		entry(1, "You have killed a Mad Angel.");

		FadingMessage target = ChatFadePlugin.collapseTarget(
			plugin.getMessages(), "You have killed a Mad Angel.", "",
			ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW);

		assertEquals(1, target.getMessageId());
	}

	@Test
	public void collapseTargetIgnoresExpiredDifferentSendersAndTypes()
	{
		entry(1, "You have killed a Mad Angel.");
		plugin.getMessages().get(0).setTimestamp(NOW - LIFETIME_MS - 1);

		assertNull("an expired entry starts a fresh run, not a merge",
			ChatFadePlugin.collapseTarget(plugin.getMessages(), "You have killed a Mad Angel.",
				null, ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW));

		assertNull("a different sender is a different collapse run",
			ChatFadePlugin.collapseTarget(plugin.getMessages(), "You have killed a Mad Angel.",
				"Bob", ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW));

		assertNull("a different message type is not merged",
			ChatFadePlugin.collapseTarget(plugin.getMessages(), "You have killed a Mad Angel.",
				null, ChatMessageType.SPAM, LIFETIME_MS, NOW));
	}

	@Test
	public void collapseTargetStillMatchesWhenTheEntryNarratesACount()
	{
		entry(1, "You have killed a Mad Angel. (2)");

		FadingMessage target = ChatFadePlugin.collapseTarget(
			plugin.getMessages(), "You have killed a Mad Angel.", null,
			ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW);

		assertEquals("trailing count suffix must not prevent duplicate matching",
			1, target.getMessageId());
	}

	@Test
	public void collapsingDuplicatesUpdatesMessageIdAndAdoptsFilterCount()
	{
		FadingMessage first = entry(10, "You have killed a Mad Angel.");

		FadingMessage target = ChatFadePlugin.collapseTarget(
			plugin.getMessages(), "You have killed a Mad Angel.", null,
			ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW);
		target.setMessageId(11);
		target.setRawText("You have killed a Mad Angel.");

		plugin.removeBlockedMessage(10);
		assertEquals(1, plugin.getMessages().size());

		plugin.applyFilteredText(11, "You have killed a Mad Angel. (2)");
		assertEquals(2, first.getCount());
		assertEquals("You have killed a Mad Angel.", first.getText());

		FadingMessage next = ChatFadePlugin.collapseTarget(
			plugin.getMessages(), "You have killed a Mad Angel.", null,
			ChatMessageType.GAMEMESSAGE, LIFETIME_MS, NOW);
		next.setMessageId(12);
		next.setRawText("You have killed a Mad Angel.");

		plugin.removeBlockedMessage(11);
		assertEquals(1, plugin.getMessages().size());

		plugin.applyFilteredText(12, "You have killed a Mad Angel. (3)");
		assertEquals(3, first.getCount());
		assertEquals("You have killed a Mad Angel.", first.getText());
	}

	@Test
	public void aPlainBlockedMessageIsStillRemoved()
	{
		entry(1, "buying gf");
		entry(4, "You have killed a Mad Angel.");

		plugin.removeBlockedMessage(1);

		assertEquals(1, plugin.getMessages().size());
		assertEquals(4, plugin.getMessages().get(0).getMessageId());
	}
}
