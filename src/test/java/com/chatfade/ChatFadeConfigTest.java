package com.chatfade;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Structural checks on the config panel and the built-in colour palette.
 *
 * <p>The position test exists because PR #19 left two Message Types items sharing
 * position 37, which makes their order in the panel arbitrary — a class of bug that is
 * invisible in review but trivial to catch here.
 */
public class ChatFadeConfigTest
{
	/** @return every {@code @ConfigItem}, grouped by the section it belongs to */
	private static Map<String, List<Method>> itemsBySection()
	{
		Map<String, List<Method>> bySection = new HashMap<>();
		for (Method method : ChatFadeConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null)
			{
				continue;
			}
			bySection.computeIfAbsent(item.section(), k -> new ArrayList<>()).add(method);
		}
		return bySection;
	}

	@Test
	public void noTwoItemsInASectionShareAPosition()
	{
		List<String> clashes = new ArrayList<>();

		for (Map.Entry<String, List<Method>> section : itemsBySection().entrySet())
		{
			Map<Integer, String> seen = new HashMap<>();
			for (Method method : section.getValue())
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				String previous = seen.put(item.position(), item.keyName());
				if (previous != null)
				{
					clashes.add(String.format("section '%s' position %d: %s and %s",
						section.getKey(), item.position(), previous, item.keyName()));
				}
			}
		}

		if (!clashes.isEmpty())
		{
			fail("config items share a position, so their panel order is arbitrary:\n  "
				+ String.join("\n  ", clashes));
		}
	}

	@Test
	public void everyConfigItemHasAKeyNameAndDescription()
	{
		for (List<Method> methods : itemsBySection().values())
		{
			for (Method method : methods)
			{
				ConfigItem item = method.getAnnotation(ConfigItem.class);
				assertTrue("missing keyName on " + method.getName(), !item.keyName().isEmpty());
				assertTrue("missing name on " + method.getName(), !item.name().isEmpty());
				assertTrue("missing description on " + method.getName(), !item.description().isEmpty());
			}
		}
	}

	@Test
	public void configKeyNamesAreUnique()
	{
		Set<String> seen = new HashSet<>();
		for (List<Method> methods : itemsBySection().values())
		{
			for (Method method : methods)
			{
				String key = method.getAnnotation(ConfigItem.class).keyName();
				assertTrue("duplicate config keyName: " + key, seen.add(key));
			}
		}
	}

	@Test
	public void newClanTogglesExistAndDefaultToVisible()
	{
		// PR #19 added these; defaulting them to true keeps upgrade behaviour unchanged.
		assertTrue(new ChatFadeConfig() {}.showClanChat());
		assertTrue(new ChatFadeConfig() {}.showGuestClanChat());
		assertTrue(new ChatFadeConfig() {}.showGIMChat());
	}

	@Test
	public void pmDirectionDefaultsOn()
	{
		assertTrue(new ChatFadeConfig() {}.showPmDirection());
	}

	// ── Built-in palette (issue #19) ────────────────────────

	@Test
	public void clanVariantsHaveDistinctBuiltInColours()
	{
		Color clan = ChatFadePlugin.getColorForType(ChatMessageType.CLAN_CHAT);
		Color guest = ChatFadePlugin.getColorForType(ChatMessageType.CLAN_GUEST_CHAT);
		Color gim = ChatFadePlugin.getColorForType(ChatMessageType.CLAN_GIM_CHAT);

		assertNotEquals("clan and guest clan must be distinguishable", clan, guest);
		assertNotEquals("clan and GIM must be distinguishable", clan, gim);
		assertNotEquals("guest clan and GIM must be distinguishable", guest, gim);
	}

	@Test
	public void clanBroadcastsMatchTheirChatColour()
	{
		// The *_MESSAGE variants are the system lines for each channel and should track
		// the colour of the channel they belong to.
		assertEquals(ChatFadePlugin.getColorForType(ChatMessageType.CLAN_CHAT),
			ChatFadePlugin.getColorForType(ChatMessageType.CLAN_MESSAGE));
		assertEquals(ChatFadePlugin.getColorForType(ChatMessageType.CLAN_GUEST_CHAT),
			ChatFadePlugin.getColorForType(ChatMessageType.CLAN_GUEST_MESSAGE));
		assertEquals(ChatFadePlugin.getColorForType(ChatMessageType.CLAN_GIM_CHAT),
			ChatFadePlugin.getColorForType(ChatMessageType.CLAN_GIM_MESSAGE));
	}

	@Test
	public void everyMessageTypeResolvesToANonNullColour()
	{
		for (ChatMessageType type : ChatMessageType.values())
		{
			org.junit.Assert.assertNotNull("no colour for " + type,
				ChatFadePlugin.getColorForType(type));
		}
	}
}
