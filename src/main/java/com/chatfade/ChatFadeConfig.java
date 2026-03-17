package com.chatfade;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.FontType;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("chatfade")
public interface ChatFadeConfig extends Config
{
	// ── Timing ──────────────────────────────────────────────

	@ConfigSection(
		name = "Timing",
		description = "Control how long messages stay visible and how they fade",
		position = 0
	)
	String timingSection = "timing";

	@ConfigItem(
		keyName = "displayDuration",
		name = "Display Duration",
		description = "How long (seconds) a message stays at full opacity before fading",
		position = 1,
		section = timingSection
	)
	@Units(Units.SECONDS)
	@Range(min = 1, max = 300)
	default int displayDuration()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "fadeDuration",
		name = "Fade Duration",
		description = "How long (seconds) the fade-out animation takes",
		position = 2,
		section = timingSection
	)
	@Units(Units.SECONDS)
	@Range(min = 1, max = 15)
	default int fadeDuration()
	{
		return 2;
	}

	// ── Display ─────────────────────────────────────────────

	@ConfigSection(
		name = "Display",
		description = "Visual appearance settings",
		position = 10
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "maxMessages",
		name = "Max Visible Messages",
		description = "Maximum number of messages shown at once",
		position = 11,
		section = displaySection
	)
	@Range(min = 1, max = 20)
	default int maxMessages()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "fontType",
		name = "Font",
		description = "Font used for fading text and typing overlay",
		position = 12,
		section = displaySection
	)
	default FontType fontType()
	{
		return FontType.SMALL;
	}

	@ConfigItem(
		keyName = "maxMessageWidth",
		name = "Max Message Width",
		description = "Maximum width in pixels before a message is truncated",
		position = 13,
		section = displaySection
	)
	@Range(min = 200, max = 800)
	default int maxMessageWidth()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "colorizeUsernames",
		name = "Colorize Usernames",
		description = "Show player names in a separate color from the message text",
		position = 14,
		section = displaySection
	)
	default boolean colorizeUsernames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "usernameColor",
		name = "Username Color",
		description = "Color used for player names when 'Colorize Usernames' is on",
		position = 15,
		section = displaySection
	)
	default Color usernameColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "colorizeNpcNames",
		name = "Colorize NPC Names",
		description = "Show NPC names in a separate color from their dialogue text",
		position = 16,
		section = displaySection
	)
	default boolean colorizeNpcNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcNameColor",
		name = "NPC Name Color",
		description = "Color used for NPC names when 'Colorize NPC Names' is on",
		position = 17,
		section = displaySection
	)
	default Color npcNameColor()
	{
		return new Color(255, 220, 80);
	}

	@ConfigItem(
		keyName = "useOriginalColors",
		name = "Use Default Colors",
		description = "Use built-in colors per message type. Turn off to customize colors below.",
		position = 18,
		section = displaySection
	)
	default boolean useOriginalColors()
	{
		return true;
	}

	// ── Custom Colors ──────────────────────────────────────

	@ConfigSection(
		name = "Custom Colors",
		description = "Per-type message colors (only used when 'Use Default Colors' is off)",
		position = 15,
		closedByDefault = true
	)
	String customColorsSection = "customColors";

	@ConfigItem(
		keyName = "gameMessageColor",
		name = "Game Messages",
		description = "Loot drops, kill counts, system messages",
		position = 16,
		section = customColorsSection
	)
	default Color gameMessageColor()
	{
		return new Color(100, 200, 255);
	}

	@ConfigItem(
		keyName = "notificationColor",
		name = "Notifications",
		description = "Login/logout, friend notifications",
		position = 17,
		section = customColorsSection
	)
	default Color notificationColor()
	{
		return new Color(255, 255, 100);
	}

	@ConfigItem(
		keyName = "publicChatColor",
		name = "Public Chat",
		description = "Public chat messages from other players",
		position = 18,
		section = customColorsSection
	)
	default Color publicChatColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "privateChatColor",
		name = "Private Messages",
		description = "Incoming and outgoing private messages",
		position = 19,
		section = customColorsSection
	)
	default Color privateChatColor()
	{
		return new Color(100, 255, 200);
	}

	@ConfigItem(
		keyName = "clanChatColor",
		name = "Clan Chat",
		description = "Clan and GIM chat messages",
		position = 20,
		section = customColorsSection
	)
	default Color clanChatColor()
	{
		return new Color(255, 130, 130);
	}

	@ConfigItem(
		keyName = "friendsChatColor",
		name = "Friends Chat",
		description = "Friends chat messages",
		position = 21,
		section = customColorsSection
	)
	default Color friendsChatColor()
	{
		return new Color(255, 160, 100);
	}

	@ConfigItem(
		keyName = "tradeColor",
		name = "Trade Messages",
		description = "Trade-related messages",
		position = 22,
		section = customColorsSection
	)
	default Color tradeColor()
	{
		return new Color(220, 150, 255);
	}

	@ConfigItem(
		keyName = "broadcastColor",
		name = "Broadcast Messages",
		description = "Broadcast messages",
		position = 23,
		section = customColorsSection
	)
	default Color broadcastColor()
	{
		return new Color(255, 215, 0);
	}

	@ConfigItem(
		keyName = "examineColor",
		name = "Examine Messages",
		description = "Item/NPC/object examine text",
		position = 24,
		section = customColorsSection
	)
	default Color examineColor()
	{
		return new Color(150, 255, 150);
	}

	@ConfigItem(
		keyName = "npcDialogueColor",
		name = "NPC Dialogue",
		description = "NPC dialogue message text",
		position = 25,
		section = customColorsSection
	)
	default Color npcDialogueColor()
	{
		return new Color(255, 220, 80);
	}

	// ── Behavior ────────────────────────────────────────────

	@ConfigSection(
		name = "Behavior",
		description = "When the overlay is shown",
		position = 30
	)
	String behaviorSection = "behavior";

	@ConfigItem(
		keyName = "onlyWhenCollapsed",
		name = "Only When Chatbox Hidden",
		description = "Only show fading messages when the chatbox is hidden/collapsed",
		position = 31,
		section = behaviorSection
	)
	default boolean onlyWhenHidden()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTypingInput",
		name = "Show Typing Input",
		description = "Show your typed text as an overlay when the chatbox is collapsed",
		position = 32,
		section = behaviorSection
	)
	default boolean showTypingInput()
	{
		return true;
	}

	// ── Message Types ───────────────────────────────────────

	@ConfigSection(
		name = "Message Types",
		description = "Which types of chat messages to display",
		position = 40,
		closedByDefault = true
	)
	String messageTypesSection = "messageTypes";

	@ConfigItem(
		keyName = "showGameMessages",
		name = "Game Messages",
		description = "Loot drops, kill counts, system messages, etc.",
		position = 31,
		section = messageTypesSection
	)
	default boolean showGameMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPublicChat",
		name = "Public Chat",
		description = "Public chat messages from other players",
		position = 32,
		section = messageTypesSection
	)
	default boolean showPublicChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrivateChat",
		name = "Private Messages",
		description = "Incoming and outgoing private messages",
		position = 33,
		section = messageTypesSection
	)
	default boolean showPrivateChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showClanChat",
		name = "Clan Chat",
		description = "Clan chat messages",
		position = 34,
		section = messageTypesSection
	)
	default boolean showClanChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFriendsChat",
		name = "Friends Chat",
		description = "Friends chat messages",
		position = 35,
		section = messageTypesSection
	)
	default boolean showFriendsChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTradeMessages",
		name = "Trade Messages",
		description = "Trade-related messages",
		position = 36,
		section = messageTypesSection
	)
	default boolean showTradeMessages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showExamineMessages",
		name = "Examine Messages",
		description = "Item/NPC/object examine text",
		position = 37,
		section = messageTypesSection
	)
	default boolean showExamineMessages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBroadcasts",
		name = "Broadcast Messages",
		description = "Broadcast messages",
		position = 38,
		section = messageTypesSection
	)
	default boolean showBroadcasts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showNpcDialogue",
		name = "NPC Dialogue",
		description = "Messages from NPC dialogue boxes",
		position = 39,
		section = messageTypesSection
	)
	default boolean showNpcDialogue()
	{
		return true;
	}
}
