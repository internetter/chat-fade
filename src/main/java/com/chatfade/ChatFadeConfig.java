package com.chatfade;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
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
	@Range(min = 1, max = 30)
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
		keyName = "fontSize",
		name = "Font Size",
		description = "Size of the fading text",
		position = 12,
		section = displaySection
	)
	@Range(min = 8, max = 24)
	default int fontSize()
	{
		return 11;
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
		keyName = "useOriginalColors",
		name = "Use Chat Type Colors",
		description = "Use a distinct color per message type instead of a single custom color",
		position = 14,
		section = displaySection
	)
	default boolean useOriginalColors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "customTextColor",
		name = "Custom Text Color",
		description = "Color for all messages when 'Use Chat Type Colors' is off",
		position = 15,
		section = displaySection
	)
	default Color customTextColor()
	{
		return Color.WHITE;
	}

	// ── Behavior ────────────────────────────────────────────

	@ConfigSection(
		name = "Behavior",
		description = "When the overlay is shown",
		position = 20
	)
	String behaviorSection = "behavior";

	@ConfigItem(
		keyName = "onlyWhenCollapsed",
		name = "Only When Chatbox Collapsed",
		description = "Only show fading messages when the chatbox is hidden/collapsed",
		position = 21,
		section = behaviorSection
	)
	default boolean onlyWhenCollapsed()
	{
		return true;
	}

	// ── Message Types ───────────────────────────────────────

	@ConfigSection(
		name = "Message Types",
		description = "Which types of chat messages to display",
		position = 30,
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
}
