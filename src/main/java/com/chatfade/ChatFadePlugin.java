package com.chatfade;

import com.google.inject.Provides;
import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Chat Fade",
	description = "Shows chat messages as fading floating text above the chatbox",
	tags = {"chat", "fade", "overlay", "collapsed", "notifications"}
)
public class ChatFadePlugin extends Plugin implements KeyListener
{
	// ── Fixed Mode Hide Chat constants ──────────────────────
	private static final int DEFAULT_VIEW_HEIGHT = 334;
	private static final int EXPANDED_VIEW_HEIGHT = 476;
	private static final int BANK_X = 12;
	private static final int BANK_Y = 2;
	private static final int DEFAULT_VIEW_WIDGET_HEIGHT = DEFAULT_VIEW_HEIGHT - BANK_Y - 1;
	private static final int EXPANDED_VIEW_WIDGET_HEIGHT = EXPANDED_VIEW_HEIGHT - BANK_Y - 1;

	private static final Map.Entry<Integer, Integer> FIXED_MAIN = new AbstractMap.SimpleEntry<>(
		net.runelite.api.widgets.InterfaceID.FIXED_VIEWPORT, 9
	);

	private static final Set<Map.Entry<Integer, Integer>> AUTO_EXPAND_WIDGETS = ImmutableSet
		.<Map.Entry<Integer, Integer>>builder()
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.DIALOG_OPTION, 0))
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.DIALOG_PLAYER, 0))
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.DIALOG_SPRITE, 0))
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.SKILLMULTI, 0))
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.CHATBOX, 42))
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.CHATBOX, 566))
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.CHATBOX, 43))
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHAT_LEFT, 0))
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHATBOX, 48))
		.build();

	private static final Set<Map.Entry<Integer, Integer>> TO_CONTRACT_WIDGETS = ImmutableSet
		.<Map.Entry<Integer, Integer>>builder()
		.add(new AbstractMap.SimpleEntry<>(ComponentID.BANK_CONTAINER, 0))
		.add(new AbstractMap.SimpleEntry<>(net.runelite.api.widgets.InterfaceID.SEED_VAULT, 1))
		.build();

	// ── Injections ──────────────────────────────────────────

	@Inject
	private Client client;

	@Inject
	private ChatFadeConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatFadeOverlay overlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ClientThread clientThread;

	// ── State ───────────────────────────────────────────────

	@Getter
	private final CopyOnWriteArrayList<FadingMessage> messages = new CopyOnWriteArrayList<>();

	@Getter
	private boolean chatHidden = true;
	private boolean chatHiddenPrevious = true;
	private int lastClickedTab = 0;

	// ── Lifecycle ───────────────────────────────────────────

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		spriteManager.addSpriteOverrides(FixedHideChatSprites.values());
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		messages.clear();
		spriteManager.removeSpriteOverrides(FixedHideChatSprites.values());
		keyManager.unregisterKeyListener(this);
		chatHidden = true;
		lastClickedTab = 0;
		clientThread.invoke(this::resetFixedModeWidgets);
	}

	// ── KeyListener ─────────────────────────────────────────

	@Override
	public void keyTyped(KeyEvent e) {}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!config.fixedModeHideChat() || client.isResized())
		{
			return;
		}
		if (e.getKeyCode() == config.hideChatHotkey().getKeyCode()
			&& e.getModifiersEx() == config.hideChatHotkey().getModifiers())
		{
			chatHidden = !chatHidden;
			e.consume();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {}

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

		String rawMessage = chatMessage.getMessage();

		// Strip CA_ID prefix — may be preceded by color tags (e.g. "<col=fff>CA_ID:330|...")
		rawMessage = rawMessage.replaceFirst("^(?:<[^>]+>)*CA_ID:\\d+\\s*\\|?", "").trim();

		// Strip skill-ID prefix from level-up messages (e.g. "24|Check the skill guide...")
		// May also be preceded by color tags.
		rawMessage = rawMessage.replaceFirst("^(?:<[^>]+>)*\\d+\\|", "").trim();

		String cleanedText = Text.removeTags(rawMessage);

		String sender = chatMessage.getName();
		if (sender != null && !sender.isEmpty())
		{
			sender = Text.removeTags(sender);
		}

		// NPC dialogue arrives as "NPC Name|dialogue text" — split it so the name
		// renders separately just like player chat.
		String rawForSpans = rawMessage;
		if ((type == ChatMessageType.DIALOG || type == ChatMessageType.MESBOX)
			&& cleanedText.contains("|"))
		{
			int sep = cleanedText.indexOf('|');
			sender = cleanedText.substring(0, sep).trim();
			cleanedText = cleanedText.substring(sep + 1).trim();

			// Also split raw message at the pipe for color span parsing
			int rawSep = rawMessage.indexOf('|');
			if (rawSep >= 0)
			{
				rawForSpans = rawMessage.substring(rawSep + 1).trim();
			}
		}

		Color color = config.useOriginalColors()
			? getColorForType(type)
			: getCustomColorForType(type);

		// Parse in-game color tags — if present, these take priority over per-type colors
		List<ColorSpan> colorSpans = parseColorSpans(rawForSpans, color);

		// Store MessageNode so we can detect async updates (e.g. emoji plugin replacing text with <img=X> tags)
		MessageNode messageNode = chatMessage.getMessageNode();

		FadingMessage fadingMessage = FadingMessage.builder()
			.senderName(sender != null && !sender.isEmpty() ? sender : null)
			.text(cleanedText)
			.type(type)
			.timestamp(System.currentTimeMillis())
			.color(color)
			.colorSpans(colorSpans)
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
					msg.setColorSpans(parseColorSpans(updated, msg.getColor()));
					msg.setMessageNode(null);
				}
			}
		}
	}

	// ── Fixed Mode Hide Chat ────────────────────────────────

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (!config.fixedModeHideChat() || client.isResized())
		{
			resetFixedModeWidgets();
			return;
		}

		// Bank container workaround — reposition when toggling
		final Widget bankWidget = client.getWidget(ComponentID.BANK_CONTAINER);
		if (bankWidget != null && !bankWidget.isSelfHidden())
		{
			if (chatHiddenPrevious != chatHidden)
			{
				Object[] onLoad = bankWidget.getOnLoadListener();
				if (onLoad != null)
				{
					client.runScript(onLoad);
				}
			}
			changeWidgetXY(bankWidget, BANK_X);
		}

		// Seed vault container workaround
		final Widget seedVaultWidget = client.getWidget(41353217);
		if (seedVaultWidget != null && !seedVaultWidget.isSelfHidden())
		{
			changeWidgetXY(seedVaultWidget, 6);
		}

		// Always expand viewport in fixed mode when this feature is enabled
		setViewSizeTo(DEFAULT_VIEW_HEIGHT, EXPANDED_VIEW_HEIGHT);

		final Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
		if (chatboxFrame != null)
		{
			boolean showChat = !chatHidden;

			// Auto-expand: if any dialog/search widget is visible, show the chatbox
			if (!showChat)
			{
				showChat = isAnyAutoExpandWidgetVisible();
			}

			setWidgetsSizeTo(
				showChat ? EXPANDED_VIEW_WIDGET_HEIGHT : DEFAULT_VIEW_WIDGET_HEIGHT,
				showChat ? DEFAULT_VIEW_WIDGET_HEIGHT : EXPANDED_VIEW_WIDGET_HEIGHT);

			chatboxFrame.setHidden(!showChat);
		}

		fixedHideChatBorders();
		chatHiddenPrevious = chatHidden;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.fixedModeHideChat() || client.isResized())
		{
			return;
		}

		if (!"Switch tab".equals(event.getMenuOption()))
		{
			return;
		}

		final Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
		final int newTab = event.getParam1();
		chatHidden = true;

		if (newTab != lastClickedTab || (chatboxFrame != null && chatboxFrame.isHidden()))
		{
			chatHidden = false;
			lastClickedTab = newTab;
		}
	}

	private boolean isAnyAutoExpandWidgetVisible()
	{
		// Check fairy ring search specifically
		final Widget fairyRingSearch = client.getWidget(net.runelite.api.widgets.InterfaceID.CHATBOX, 38);
		if (fairyRingSearch != null)
		{
			Widget[] children = fairyRingSearch.getDynamicChildren();
			if (children != null && children.length > 0 && children[0] != null)
			{
				String text = children[0].getText();
				if (text != null && text.contains("fairy"))
				{
					return true;
				}
			}
		}

		for (Map.Entry<Integer, Integer> entry : AUTO_EXPAND_WIDGETS)
		{
			final Widget widget = client.getWidget(entry.getKey(), entry.getValue());
			if (widget != null && !widget.isSelfHidden())
			{
				final Widget[] nestedChildren = widget.getNestedChildren();
				final Widget[] staticChildren = widget.getStaticChildren();

				if (staticChildren != null && staticChildren.length > 0)
				{
					if (isWidgetTreeVisible(staticChildren))
					{
						return true;
					}
				}
				else if (nestedChildren != null && nestedChildren.length > 0)
				{
					if (isWidgetTreeVisible(nestedChildren))
					{
						return true;
					}
				}
				else if (!widget.isHidden())
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean isWidgetTreeVisible(Widget[] widgets)
	{
		for (Widget w : widgets)
		{
			if (w != null && !w.isSelfHidden() && !w.isHidden())
			{
				return true;
			}
		}
		return false;
	}

	private static void changeWidgetXY(Widget widget, int xPosition)
	{
		widget.setOriginalX(xPosition);
		widget.setOriginalY(BANK_Y);
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		widget.revalidateScroll();
	}

	private static void setWidgetHeight(Widget widget, int height)
	{
		widget.setOriginalHeight(height);
		widget.setHeightMode(WidgetSizeMode.ABSOLUTE);
		widget.revalidateScroll();
	}

	private static void changeWidgetHeight(int originalHeight, int newHeight, Widget widget)
	{
		if (widget.getHeight() == originalHeight)
		{
			setWidgetHeight(widget, newHeight);

			Widget[] nestedChildren = widget.getNestedChildren();
			if (nestedChildren != null)
			{
				for (Widget child : nestedChildren)
				{
					if (child.getHeight() == originalHeight)
					{
						setWidgetHeight(child, newHeight);
					}
				}
			}

			Widget[] dynamicChildren = widget.getDynamicChildren();
			if (dynamicChildren != null)
			{
				for (Widget child : dynamicChildren)
				{
					if (child.getHeight() == originalHeight)
					{
						setWidgetHeight(child, newHeight);
					}
				}
			}
		}
	}

	private void setWidgetsSizeTo(int originalHeight, int newHeight)
	{
		for (Map.Entry<Integer, Integer> entry : TO_CONTRACT_WIDGETS)
		{
			Widget widget = entry.getValue() == 0
				? client.getWidget(entry.getKey())
				: client.getWidget(entry.getKey(), entry.getValue());
			if (widget != null && !widget.isSelfHidden())
			{
				changeWidgetHeight(originalHeight, newHeight, widget);
			}
		}
	}

	private void setViewSizeTo(int originalHeight, int newHeight)
	{
		final Widget viewport = client.getWidget(InterfaceID.Toplevel.MAIN);
		if (viewport != null)
		{
			setWidgetHeight(viewport, newHeight);
		}

		final Widget fixedMain = client.getWidget(FIXED_MAIN.getKey(), FIXED_MAIN.getValue());
		if (fixedMain != null && fixedMain.getHeight() == originalHeight)
		{
			setWidgetHeight(fixedMain, newHeight);

			Widget[] staticChildren = fixedMain.getStaticChildren();
			if (staticChildren != null)
			{
				for (Widget child : staticChildren)
				{
					changeWidgetHeight(originalHeight, newHeight, child);
				}
			}
		}
	}

	private void fixedHideChatBorders()
	{
		Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
		if (client.isResized() || chatboxFrame == null || !chatboxFrame.isHidden())
		{
			resetFixedHideChatBorders();
			return;
		}

		Widget chatbox = client.getWidget(ComponentID.CHATBOX_PARENT);
		if (chatbox == null || chatbox.getChild(1) != null)
		{
			return;
		}

		Widget leftBorder = chatbox.createChild(-1, WidgetType.GRAPHIC);
		leftBorder.setSpriteId(FixedHideChatSprites.FIXED_HIDE_CHAT_LEFT_BORDER.getSpriteId());
		leftBorder.setOriginalWidth(4);
		leftBorder.setOriginalHeight(142);
		leftBorder.setOriginalX(0);
		leftBorder.setOriginalY(0);
		leftBorder.setHidden(false);
		leftBorder.revalidate();

		Widget rightBorder = chatbox.createChild(-1, WidgetType.GRAPHIC);
		rightBorder.setSpriteId(FixedHideChatSprites.FIXED_HIDE_CHAT_RIGHT_BORDER.getSpriteId());
		rightBorder.setOriginalWidth(3);
		rightBorder.setOriginalHeight(142);
		rightBorder.setOriginalX(516);
		rightBorder.setOriginalY(0);
		rightBorder.setHidden(false);
		rightBorder.revalidate();
	}

	private void resetFixedHideChatBorders()
	{
		Widget chatbox = client.getWidget(ComponentID.CHATBOX_PARENT);
		if (chatbox != null && chatbox.getChild(1) != null)
		{
			chatbox.deleteAllChildren();
		}
	}

	private void resetFixedModeWidgets()
	{
		if (client.isResized())
		{
			return;
		}

		setViewSizeTo(EXPANDED_VIEW_HEIGHT, DEFAULT_VIEW_HEIGHT);
		setWidgetsSizeTo(EXPANDED_VIEW_WIDGET_HEIGHT, DEFAULT_VIEW_WIDGET_HEIGHT);

		Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
		if (chatboxFrame != null)
		{
			chatboxFrame.setHidden(false);
			resetFixedHideChatBorders();
		}
	}

	// ── Message management ──────────────────────────────────

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
			{
				// Respect the in-game "Friend Login/Logout messages" setting
				// (0 = Timeout, 1 = On, 2 = Off)
				if (client.getVarbitValue(VarbitID.LOGINLOGOUT_SETTING) == 2)
				{
					return false;
				}
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_GAME_FILTER);
				if ("Off".equals(state))
				{
					return false;
				}
				return true;
			}

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

	private static final Pattern COL_TAG = Pattern.compile("<col=([0-9a-fA-F]{6})>");
	private static final Pattern COL_CLOSE = Pattern.compile("</col>");
	private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");

	static List<ColorSpan> parseColorSpans(String raw, Color fallback)
	{
		if (!raw.contains("<col="))
		{
			return null;
		}

		List<ColorSpan> spans = new ArrayList<>();
		Color currentColor = fallback;
		int pos = 0;
		StringBuilder currentText = new StringBuilder();

		while (pos < raw.length())
		{
			Matcher colMatcher = COL_TAG.matcher(raw);
			colMatcher.region(pos, raw.length());

			Matcher closeMatcher = COL_CLOSE.matcher(raw);
			closeMatcher.region(pos, raw.length());

			Matcher anyMatcher = ANY_TAG.matcher(raw);
			anyMatcher.region(pos, raw.length());

			if (anyMatcher.lookingAt())
			{
				if (colMatcher.lookingAt())
				{
					if (currentText.length() > 0)
					{
						spans.add(new ColorSpan(currentText.toString(), currentColor));
						currentText.setLength(0);
					}
					currentColor = new Color(Integer.parseInt(colMatcher.group(1), 16));
					pos = colMatcher.end();
				}
				else if (closeMatcher.lookingAt())
				{
					if (currentText.length() > 0)
					{
						spans.add(new ColorSpan(currentText.toString(), currentColor));
						currentText.setLength(0);
					}
					currentColor = fallback;
					pos = closeMatcher.end();
				}
				else
				{
					// Other tag (img, lt, gt, etc.) — skip it
					pos = anyMatcher.end();
				}
			}
			else
			{
				currentText.append(raw.charAt(pos));
				pos++;
			}
		}

		if (currentText.length() > 0)
		{
			spans.add(new ColorSpan(currentText.toString(), currentColor));
		}

		// Filter out empty spans
		spans.removeIf(s -> s.getText().isEmpty());

		// If all spans use the fallback color, no multi-color — return null
		if (spans.stream().allMatch(s -> s.getColor().equals(fallback)))
		{
			return null;
		}

		return spans;
	}

	@Provides
	ChatFadeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatFadeConfig.class);
	}
}
