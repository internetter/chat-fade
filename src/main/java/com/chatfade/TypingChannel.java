package com.chatfade;

import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.ChatMessageType;

/**
 * Which chat channel a message being typed will be sent to.
 *
 * <p>Used to colour the overlay's typing line the same way the message will appear once sent,
 * so you can tell at a glance that what you are about to say is going to your clan rather than
 * to everyone standing around you.
 *
 * <p>Resolution is pure: the caller reads the handful of client values and passes them in,
 * which keeps the fiddly prefix precedence testable without a running game.
 */
enum TypingChannel
{
	// Declaration order is the matching order. GUEST must come before GIM so that "/gc " is
	// read as guest clan chat rather than as the group ironman prefix followed by a "c".
	PUBLIC(ChatMessageType.PUBLICCHAT, Pattern.compile("^/(@p|p ).*"), 0),
	FRIEND(ChatMessageType.FRIENDSCHAT, Pattern.compile("^/(@?f).*"), 1),
	CLAN(ChatMessageType.CLAN_CHAT, Pattern.compile("^/(@c|c ).*"), 2),
	GUEST(ChatMessageType.CLAN_GUEST_CHAT, Pattern.compile("^/(@gc|gc ).*"), 3),
	GIM(ChatMessageType.CLAN_GIM_CHAT, Pattern.compile("^/(@g[^c]|g ).*"), 4);

	/** Value of {@code VarClientID.CHAT_VIEW} for each selectable chat tab. */
	private static final int VIEW_CHANNEL = 4;
	private static final int VIEW_CLAN = 5;
	private static final int VIEW_TRADE_OR_GIM = 6;

	private final ChatMessageType messageType;
	private final Pattern prefix;
	private final int slashCount;

	TypingChannel(ChatMessageType messageType, Pattern prefix, int slashCount)
	{
		this.messageType = messageType;
		this.prefix = prefix;
		this.slashCount = slashCount;
	}

	/**
	 * The message type this channel's sent messages arrive as, so the typing line can borrow
	 * whatever colour the overlay already uses for that channel.
	 */
	ChatMessageType getMessageType()
	{
		return messageType;
	}

	/**
	 * Works out where the text being typed is headed.
	 *
	 * @param typed the in-progress message
	 * @param inFriendsChat whether the player is currently in a friends chat channel
	 * @param groupIronman whether the account is any flavour of group ironman
	 * @param chatView {@code VarClientID.CHAT_VIEW} — the selected chat tab
	 * @param chatboxMode {@code VarClientID.CHATBOX_MODE} — the sticky chat mode
	 */
	static TypingChannel resolve(String typed, boolean inFriendsChat, boolean groupIronman,
		int chatView, int chatboxMode)
	{
		String text = typed == null ? "" : typed;

		// An explicit prefix on the message wins over everything else.
		TypingChannel prefixed = fromPrefix(text, inFriendsChat, groupIronman);
		if (prefixed != null)
		{
			return prefixed;
		}

		// Then the selected tab: typing with the Clan tab open sends to clan chat.
		TypingChannel view = fromChatView(chatView, inFriendsChat, groupIronman);
		if (view != PUBLIC)
		{
			return view;
		}

		// Then the sticky mode set by the chat buttons.
		TypingChannel mode = fromChatboxMode(chatboxMode);
		if (mode != null)
		{
			return resolveAvailability(mode, text, inFriendsChat, groupIronman);
		}

		return PUBLIC;
	}

	@Nullable
	private static TypingChannel fromPrefix(String text, boolean inFriendsChat, boolean groupIronman)
	{
		for (TypingChannel channel : values())
		{
			if (channel.prefix.matcher(text).matches())
			{
				return resolveAvailability(channel, text, inFriendsChat, groupIronman);
			}
		}

		TypingChannel bySlash = fromSlashCount(countLeadingSlashes(text));
		return bySlash == null ? null : resolveAvailability(bySlash, text, inFriendsChat, groupIronman);
	}

	static int countLeadingSlashes(String text)
	{
		int count = 0;
		while (count < text.length() && text.charAt(count) == '/')
		{
			count++;
		}
		// Five or more slashes behave as four.
		return Math.min(count, 4);
	}

	@Nullable
	private static TypingChannel fromSlashCount(int count)
	{
		if (count == 0)
		{
			return null;
		}
		for (TypingChannel channel : values())
		{
			if (channel.slashCount == count)
			{
				return channel;
			}
		}
		return null;
	}

	private static TypingChannel fromChatView(int chatView, boolean inFriendsChat, boolean groupIronman)
	{
		switch (chatView)
		{
			case VIEW_CHANNEL:
				return inFriendsChat ? FRIEND : PUBLIC;
			case VIEW_CLAN:
				return CLAN;
			case VIEW_TRADE_OR_GIM:
				return groupIronman ? GIM : PUBLIC;
			default:
				return PUBLIC;
		}
	}

	@Nullable
	private static TypingChannel fromChatboxMode(int chatboxMode)
	{
		switch (chatboxMode)
		{
			case 1:
				return FRIEND;
			case 2:
				return CLAN;
			case 3:
				return GUEST;
			case 4:
				return GIM;
			default:
				return null;
		}
	}

	/**
	 * Redirects a channel the player cannot actually send to. Aiming at friends chat while not
	 * in one, or at group ironman chat on a normal account, does not send where the prefix
	 * says — the game falls back, and the colour should follow.
	 */
	private static TypingChannel resolveAvailability(TypingChannel channel, String text,
		boolean inFriendsChat, boolean groupIronman)
	{
		switch (channel)
		{
			case FRIEND:
				return inFriendsChat ? FRIEND : PUBLIC;
			case GIM:
				if (groupIronman)
				{
					return GIM;
				}
				// Without a group, the group ironman prefixes mean something else.
				if (text.startsWith("/g"))
				{
					return inFriendsChat ? FRIEND : PUBLIC;
				}
				if (text.startsWith("/@g"))
				{
					return CLAN;
				}
				if (text.startsWith("////"))
				{
					return GUEST;
				}
				return GIM;
			default:
				return channel;
		}
	}
}
