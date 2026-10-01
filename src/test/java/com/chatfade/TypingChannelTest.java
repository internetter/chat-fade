package com.chatfade;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Channel resolution is pure, so the whole prefix precedence can be checked without a client.
 *
 * <p>Chat view and chatbox mode values are the raw varc values: view 4 is the Channel tab,
 * 5 the Clan tab, 6 Trade/Group; mode 1 friends, 2 clan, 3 guest, 4 group ironman.
 */
public class TypingChannelTest
{
	private static final int VIEW_NONE = 0;
	private static final int MODE_NONE = 0;

	private static TypingChannel resolve(String typed)
	{
		return TypingChannel.resolve(typed, false, false, VIEW_NONE, MODE_NONE);
	}

	private static TypingChannel resolve(String typed, boolean inFriendsChat, boolean gim)
	{
		return TypingChannel.resolve(typed, inFriendsChat, gim, VIEW_NONE, MODE_NONE);
	}

	@Test
	public void plainTextGoesToPublic()
	{
		assertEquals(TypingChannel.PUBLIC, resolve("hello everyone"));
		assertEquals(TypingChannel.PUBLIC, resolve(""));
		assertEquals(TypingChannel.PUBLIC, resolve(null));
	}

	@Test
	public void oneSlashGoesToFriendsChatWhenInOne()
	{
		assertEquals(TypingChannel.FRIEND, resolve("/hello", true, false));
	}

	@Test
	public void oneSlashFallsBackToPublicWhenNotInAFriendsChat()
	{
		// The message goes nowhere, but it certainly is not clan chat.
		assertEquals(TypingChannel.PUBLIC, resolve("/hello", false, false));
	}

	@Test
	public void twoSlashesGoToClan()
	{
		assertEquals(TypingChannel.CLAN, resolve("//hello"));
	}

	@Test
	public void threeSlashesGoToGuestClan()
	{
		assertEquals(TypingChannel.GUEST, resolve("///hello"));
	}

	@Test
	public void fourSlashesGoToGroupIronmanOnlyWithAGroup()
	{
		assertEquals(TypingChannel.GIM, resolve("////hello", false, true));
		assertEquals(TypingChannel.GUEST, resolve("////hello", false, false));
	}

	@Test
	public void moreThanFourSlashesBehaveAsFour()
	{
		assertEquals(TypingChannel.GIM, resolve("//////hello", false, true));
	}

	@Test
	public void namedPrefixesPickTheirChannel()
	{
		assertEquals(TypingChannel.PUBLIC, resolve("/@p hello"));
		assertEquals(TypingChannel.CLAN, resolve("/@c hello"));
		assertEquals(TypingChannel.CLAN, resolve("/c hello"));
		assertEquals(TypingChannel.GUEST, resolve("/@gc hello"));
		assertEquals(TypingChannel.GUEST, resolve("/gc hello"));
		assertEquals(TypingChannel.FRIEND, resolve("/@f hello", true, false));
		assertEquals(TypingChannel.GIM, resolve("/g hello", false, true));
	}

	@Test
	public void guestPrefixIsNotReadAsGroupIronman()
	{
		// "/gc " must not be taken as the group prefix followed by a stray "c".
		assertEquals(TypingChannel.GUEST, resolve("/gc hello", false, true));
	}

	@Test
	public void groupPrefixesFallBackWithoutAGroup()
	{
		assertEquals(TypingChannel.PUBLIC, resolve("/g hello", false, false));
		assertEquals(TypingChannel.FRIEND, resolve("/g hello", true, false));
		assertEquals(TypingChannel.CLAN, resolve("/@g hello", false, false));
	}

	@Test
	public void aWordStartingWithAChannelLetterIsStillJustOneSlash()
	{
		// "/corn" is not the clan prefix, which needs "/c " or "/@c".
		assertEquals(TypingChannel.FRIEND, resolve("/corn", true, false));
		assertEquals(TypingChannel.PUBLIC, resolve("/corn", false, false));
	}

	@Test
	public void theSelectedTabDecidesWhenThereIsNoPrefix()
	{
		assertEquals(TypingChannel.CLAN, TypingChannel.resolve("hello", false, false, 5, MODE_NONE));
		assertEquals(TypingChannel.FRIEND, TypingChannel.resolve("hello", true, false, 4, MODE_NONE));
		assertEquals(TypingChannel.PUBLIC, TypingChannel.resolve("hello", false, false, 4, MODE_NONE));
		assertEquals(TypingChannel.GIM, TypingChannel.resolve("hello", false, true, 6, MODE_NONE));
		assertEquals(TypingChannel.PUBLIC, TypingChannel.resolve("hello", false, false, 6, MODE_NONE));
	}

	@Test
	public void theStickyChatModeDecidesWhenNothingElseDoes()
	{
		assertEquals(TypingChannel.CLAN, TypingChannel.resolve("hello", false, false, VIEW_NONE, 2));
		assertEquals(TypingChannel.GUEST, TypingChannel.resolve("hello", false, false, VIEW_NONE, 3));
		assertEquals(TypingChannel.GIM, TypingChannel.resolve("hello", false, true, VIEW_NONE, 4));
		assertEquals(TypingChannel.FRIEND, TypingChannel.resolve("hello", true, false, VIEW_NONE, 1));
	}

	@Test
	public void friendsModeFallsBackToPublicAfterLeavingTheChannel()
	{
		assertEquals(TypingChannel.PUBLIC, TypingChannel.resolve("hello", false, false, VIEW_NONE, 1));
	}

	@Test
	public void aPrefixBeatsTheSelectedTabAndMode()
	{
		assertEquals(TypingChannel.PUBLIC, TypingChannel.resolve("/@p hi", false, false, 5, 2));
		assertEquals(TypingChannel.CLAN, TypingChannel.resolve("//hi", false, false, 4, 1));
	}

	@Test
	public void everyChannelMapsToAMessageType()
	{
		for (TypingChannel channel : TypingChannel.values())
		{
			assertEquals(channel + " should map to a message type the overlay colours",
				true, channel.getMessageType() != null);
		}
	}
}
