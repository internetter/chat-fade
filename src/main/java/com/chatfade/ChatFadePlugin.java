package com.chatfade;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Chat Fade",
	description = "Shows chat messages as fading floating text above the chatbox",
	tags = {"chat", "fade", "overlay", "collapsed", "notifications"}
)
public class ChatFadePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ChatFadeConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatFadeOverlay overlay;

	@Getter
	private final CopyOnWriteArrayList<FadingMessage> messages = new CopyOnWriteArrayList<>();

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		messages.clear();
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		ChatMessageType type = chatMessage.getType();

		if (!isMessageTypeEnabled(type))
		{
			return;
		}

		if (!isAllowedByChatFilter(type))
		{
			return;
		}

		String cleanedText = Text.removeTags(chatMessage.getMessage());
		String sender = chatMessage.getName();
		if (sender != null && !sender.isEmpty())
		{
			sender = Text.removeTags(sender);
		}

		// NPC dialogue arrives as "NPC Name|dialogue text" — split it so the name
		// renders separately just like player chat.
		if ((type == ChatMessageType.DIALOG || type == ChatMessageType.MESBOX)
			&& cleanedText.contains("|"))
		{
			int sep = cleanedText.indexOf('|');
			sender = cleanedText.substring(0, sep).trim();
			cleanedText = cleanedText.substring(sep + 1).trim();
		}

		Color color = config.useOriginalColors()
			? getColorForType(type)
			: getCustomColorForType(type);

		// Store MessageNode for command messages so we can detect async updates
		MessageNode messageNode = cleanedText.startsWith("!")
			? chatMessage.getMessageNode()
			: null;

		FadingMessage fadingMessage = FadingMessage.builder()
			.senderName(sender != null && !sender.isEmpty() ? sender : null)
			.text(cleanedText)
			.type(type)
			.timestamp(System.currentTimeMillis())
			.color(color)
			.messageNode(messageNode)
			.build();

		messages.add(fadingMessage);

		while (messages.size() > config.maxMessages())
		{
			messages.remove(0);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		for (FadingMessage msg : messages)
		{
			MessageNode node = msg.getMessageNode();
			if (node == null)
			{
				continue;
			}

			String updated = node.getRuneLiteFormatMessage();
			if (updated != null && !updated.isEmpty())
			{
				String cleanedUpdate = Text.removeTags(updated);
				if (!cleanedUpdate.equals(msg.getText()))
				{
					msg.setText(cleanedUpdate);
					msg.setMessageNode(null);
				}
			}
		}
	}

	void pruneExpiredMessages()
	{
		long now = System.currentTimeMillis();
		long totalLifetimeMs = (config.displayDuration() + config.fadeDuration()) * 1000L;

		Iterator<FadingMessage> it = messages.iterator();
		while (it.hasNext())
		{
			FadingMessage msg = it.next();
			if (now - msg.getTimestamp() > totalLifetimeMs)
			{
				messages.remove(msg);
			}
		}
	}

	private boolean isAllowedByChatFilter(ChatMessageType type)
	{
		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			case WELCOME:
			case CONSOLE:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_GAME_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			case SPAM:
			{
				// SPAM type = messages the game considers filterable.
				// When Game tab is "Filtered", these are hidden from the chatbox.
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_GAME_FILTER);
				if ("Off".equals(state) || "Filtered".equals(state))
				{
					return false;
				}
				return true;
			}

			case LOGINLOGOUTNOTIFICATION:
			case FRIENDNOTIFICATION:
			case IGNORENOTIFICATION:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_GAME_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_PUBLIC_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case PRIVATECHATOUT:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_PRIVATE_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			case CLAN_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_CLAN_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_FRIENDSCHAT_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			case TRADE:
			case TRADE_SENT:
			case TRADEREQ:
			{
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_TRADE_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

			default:
				return true;
		}
	}

	private String getTabFilterText(int widgetId)
	{
		Widget widget = client.getWidget(widgetId);
		if (widget == null)
		{
			return null;
		}
		String text = widget.getText();
		if (text == null)
		{
			return null;
		}
		return Text.removeTags(text);
	}

	private boolean isMessageTypeEnabled(ChatMessageType type)
	{
		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			case SPAM:
			case WELCOME:
			case LOGINLOGOUTNOTIFICATION:
			case FRIENDNOTIFICATION:
			case IGNORENOTIFICATION:
			case CONSOLE:
				return config.showGameMessages();

			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				return config.showPublicChat();

			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case PRIVATECHATOUT:
				return config.showPrivateChat();

			case CLAN_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
				return config.showClanChat();

			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
				return config.showFriendsChat();

			case TRADE:
			case TRADE_SENT:
			case TRADEREQ:
				return config.showTradeMessages();

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				return config.showExamineMessages();

			case BROADCAST:
				return config.showBroadcasts();

			case DIALOG:
			case MESBOX:
				return config.showNpcDialogue();

			default:
				return config.showGameMessages();
		}
	}

	private Color getColorForType(ChatMessageType type)
	{
		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			case SPAM:
			case WELCOME:
			case CONSOLE:
				return new Color(100, 200, 255);

			case LOGINLOGOUTNOTIFICATION:
			case FRIENDNOTIFICATION:
			case IGNORENOTIFICATION:
				return new Color(255, 255, 100);

			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				return Color.WHITE;

			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case PRIVATECHATOUT:
				return new Color(100, 255, 200);

			case CLAN_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
				return new Color(255, 130, 130);

			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
				return new Color(255, 160, 100);

			case TRADE:
			case TRADE_SENT:
			case TRADEREQ:
				return new Color(220, 150, 255);

			case BROADCAST:
				return new Color(255, 215, 0);

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				return new Color(150, 255, 150);

			case DIALOG:
			case MESBOX:
				return new Color(255, 220, 80);

			default:
				return Color.WHITE;
		}
	}

	private Color getCustomColorForType(ChatMessageType type)
	{
		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			case SPAM:
			case WELCOME:
			case CONSOLE:
				return config.gameMessageColor();

			case LOGINLOGOUTNOTIFICATION:
			case FRIENDNOTIFICATION:
			case IGNORENOTIFICATION:
				return config.notificationColor();

			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				return config.publicChatColor();

			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case PRIVATECHATOUT:
				return config.privateChatColor();

			case CLAN_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
				return config.clanChatColor();

			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
				return config.friendsChatColor();

			case TRADE:
			case TRADE_SENT:
			case TRADEREQ:
				return config.tradeColor();

			case BROADCAST:
				return config.broadcastColor();

			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
				return config.examineColor();

			case DIALOG:
			case MESBOX:
				return config.npcDialogueColor();

			default:
				return config.gameMessageColor();
		}
	}

	@Provides
	ChatFadeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatFadeConfig.class);
	}
}
