package com.chatfade;

import com.google.inject.Provides;
import com.google.common.collect.ImmutableSet;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.AbstractMap;
import java.util.ArrayList;
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
import net.runelite.api.events.ScriptCallbackEvent;
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
import net.runelite.client.events.ConfigChanged;
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

	@Inject
	private net.runelite.client.game.ChatIconManager chatIconManager;

	// ── State ───────────────────────────────────────────────

	@Getter
	private final CopyOnWriteArrayList<FadingMessage> messages = new CopyOnWriteArrayList<>();

	@Getter
	private boolean chatHidden = true;
	private boolean chatHiddenPrevious = true;
	private int lastClickedTab = 0;
	private final IgnoreList ignoreList = new IgnoreList();
	private final ChatIcons chatIcons = new ChatIcons();

	// ── Lifecycle ───────────────────────────────────────────

	@Override
	protected void startUp()
	{
		rebuildIgnoreLists();
		applyOverlayLayer();
		spriteManager.addSpriteOverrides(FixedHideChatSprites.values());
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		messages.clear();
		chatIcons.clear();
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

	@Subscribe(priority = -3)
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"chatFilterCheck".equals(event.getEventName()))
		{
			return;
		}

		if (!config.respectChatFilter())
		{
			return;
		}

		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		final int messageId = intStack[intStackSize - 1];

		// Chat Filter plugin sets intStack[intStackSize - 3] to 0 when blocking a message
		if (intStack[intStackSize - 3] == 0)
		{
			// chatFilterCheck runs during the chatbox rebuild, which happens *after* the
			// ChatMessage event has already been posted, so by the time we learn a message
			// was blocked it is already queued in the overlay. Drop it retroactively.
			//
			// Match on the message id rather than the text: with the Chat Filter plugin's
			// "Collapse game chat"/"Collapse player chat" options every duplicate is
			// reported as blocked, and matching by text would also delete the first copy
			// that the user is still reading.
			removeBlockedMessage(messageId);
			return;
		}

		// Not blocked, but "Censor Words" mode rewrites the text in place rather than
		// blocking it. That rewrite only reaches the object stack — censorMessage does not
		// touch the MessageNode — so without this the overlay keeps showing the original
		// uncensored wording that the chatbox has already starred out.
		Object[] objectStack = client.getObjectStack();
		int objectStackSize = client.getObjectStackSize();
		if (objectStackSize > 0 && objectStack[objectStackSize - 1] instanceof String)
		{
			applyFilteredText(messageId, (String) objectStack[objectStackSize - 1]);
		}
	}

	/**
	 * Replaces a queued message's text with the Chat Filter plugin's censored version.
	 *
	 * <p>Only acts when the filter genuinely rewrote the message. The chatbox rebuild hands
	 * us the game's original text for every line it redraws, so adopting it unconditionally
	 * would undo rewrites other plugins make on the {@link MessageNode} — chat commands
	 * replacing "!kc" with the real kill count being the obvious casualty.
	 *
	 * <p>When it does act, the node reference is released so the per-tick update pass cannot
	 * revert the censoring from the node's untouched original.
	 */
	void applyFilteredText(int messageId, String filteredRaw)
	{
		if (messageId < 0 || filteredRaw == null || filteredRaw.isEmpty())
		{
			return;
		}

		for (FadingMessage msg : messages)
		{
			if (msg.getMessageId() != messageId)
			{
				continue;
			}

			if (filteredRaw.equals(msg.getRawText()))
			{
				// Unchanged by the filter — leave the message alone.
				return;
			}

			String raw = stripIngestPrefixes(expandIfGameAuthored(filteredRaw, msg.getType()));
			String cleaned = toDisplayText(raw);
			if (!cleaned.equals(msg.getText()))
			{
				msg.setText(cleaned);
				msg.setColorSpans(config.preserveInlineColors()
					? parseColorSpans(raw, msg.getColor(), iconResolver())
					: null);
				msg.setMessageNode(null);
			}
			return;
		}
	}

	/**
	 * Drops a message the Chat Filter plugin blocked, identified by {@link MessageNode#getId()}.
	 *
	 * <p>Deliberately not matched by text: with that plugin's "Collapse game chat" /
	 * "Collapse player chat" options every duplicate is reported as blocked, so text matching
	 * would also delete the earlier copy the user is still reading.
	 */
	void removeBlockedMessage(int blockedId)
	{
		if (blockedId < 0)
		{
			// Replayed message — no stable id to match against.
			return;
		}

		messages.removeIf(m -> m.getMessageId() == blockedId);
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

		// Colour reaches us in two syntaxes: <col=rrggbb> and the older @name@ macros
		// (e.g. @mes_hl_pur@). Let the client expand the macros against its own palette so
		// everything downstream only ever deals with <col=...> — but never for text a
		// player typed, which is not markup.
		String expanded = expandIfGameAuthored(chatMessage.getMessage(), type);

		// Plugin's own ignore lists — independent of the Chat Filter plugin
		if (ignoreList.matches(expanded))
		{
			return;
		}

		// Messages blocked by the Chat Filter plugin are removed in onScriptCallbackEvent,
		// which necessarily runs after this event — see the comment there.

		String rawMessage = stripIngestPrefixes(expanded);
		String cleanedText = toDisplayText(rawMessage);

		String sender = chatMessage.getName();
		List<java.awt.image.BufferedImage> senderIcons = extractSenderIcons(type, sender);
		if (sender != null && !sender.isEmpty())
		{
			sender = toDisplayText(sender);
			sender = applyPrivateMessagePrefix(sender, type, config.showPmDirection());
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
		// Icons and colours are independent options: with "Preserve In-Game Colors" off we
		// still want icons drawn, just with every run of text at the per-type colour.
		List<ColorSpan> colorSpans = parseColorSpans(rawForSpans, color, iconResolver());
		if (!config.preserveInlineColors())
		{
			colorSpans = dropSpanColours(colorSpans, color);
		}

		colorSpans = applyLootHighlight(colorSpans, cleanedText, color);

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
			.messageId(messageNode != null ? messageNode.getId() : -1)
			.rawText(chatMessage.getMessage())
			.senderIcons(senderIcons)
			.channelName(config.showChannelName() ? channelName(chatMessage.getSender()) : null)
			.build();

		messages.add(fadingMessage);

		while (messages.size() > config.maxMessages())
		{
			messages.remove(0);
		}
	}

	/**
	 * Matches the "CA_ID:###" marker on Combat Achievement messages.
	 *
	 * <p>Everything ahead of the marker is captured so it can be put back: a rank icon,
	 * colour tags, and crucially the space between an icon and the marker. Clan broadcasts
	 * arrive as {@code <img=2> CA_ID:413|Bob has completed...}, and without allowing that
	 * space the marker stayed on screen for every message carrying an icon.
	 */
	private static final Pattern CA_ID_PREFIX =
		Pattern.compile("^((?:<[^>]+>|\\s)*)CA_ID:\\d+\\s*\\|?");

	/** Matches the numeric skill-id prefix on level-up messages, after any tags or spacing. */
	private static final Pattern SKILL_ID_PREFIX =
		Pattern.compile("^((?:<[^>]+>|\\s)*)\\d+\\|");

	/**
	 * Expands the game's {@code @name@} colour macros into {@code <col=rrggbb>} using the
	 * client's own palette, so only one colour syntax reaches the rest of the plugin.
	 *
	 * <p>The client knows its real macro table and the exact colours behind each name, which
	 * is why this is left to {@link Client#macroExpand} rather than matched here.
	 */
	private String macroExpand(String message)
	{
		if (message == null || message.indexOf('@') < 0)
		{
			return message == null ? "" : message;
		}

		String expanded = client.macroExpand(message);
		return expanded != null ? expanded : message;
	}

	/**
	 * Expands colour macros only for messages the game itself wrote.
	 *
	 * <p>Player-typed text is not markup. Running it through the client's macro expander
	 * consumes anything shaped like a macro, so someone typing "@bob@" in public chat had
	 * the @ signs eaten in the overlay while the game's own chatbox showed them intact.
	 */
	private String expandIfGameAuthored(String message, ChatMessageType type)
	{
		return isPlayerAuthored(type) ? (message == null ? "" : message) : macroExpand(message);
	}

	/** @return true when the message text was typed by a player rather than generated by the game */
	private static boolean isPlayerAuthored(ChatMessageType type)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case PRIVATECHATOUT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
			case FRIENDSCHAT:
				return true;

			default:
				return false;
		}
	}

	/**
	 * Removes the machine-readable prefixes the game puts in front of some messages, leaving
	 * colour markup intact so it can still be parsed into spans.
	 */
	static String stripIngestPrefixes(String rawMessage)
	{
		if (rawMessage == null)
		{
			return "";
		}

		// Put group 1 back so a leading rank icon or colour tag survives the strip.
		String stripped = CA_ID_PREFIX.matcher(rawMessage).replaceFirst("$1").trim();
		return SKILL_ID_PREFIX.matcher(stripped).replaceFirst("$1").trim();
	}

	/**
	 * Reduces a message to plain display text.
	 *
	 * <p>Uses {@link Text#unescapeJagex} rather than {@link Text#removeTags} because the game
	 * escapes printable characters as pseudo-tags — a typed "@" arrives as {@code <at>}, and
	 * blind tag removal deletes it, so player text lost its @ signs entirely. The overlay
	 * draws one line per message, so any line breaks collapse to spaces.
	 */
	static String toDisplayText(String rawMessage)
	{
		if (rawMessage == null)
		{
			return "";
		}

		// Trimmed because removing a leading icon tag leaves the space that followed it,
		// which would otherwise indent the message by a character.
		return Text.unescapeJagex(rawMessage).replace('\n', ' ').trim();
	}

	/** @return the icon for an {@code <img=N>} tag, or null if it is not one or cannot resolve */
	private static java.awt.image.BufferedImage resolveIconTag(String tag,
		java.util.function.IntFunction<java.awt.image.BufferedImage> iconResolver)
	{
		if (iconResolver == null)
		{
			return null;
		}

		Matcher m = ChatIcons.IMG_TAG.matcher(tag);
		if (!m.matches())
		{
			return null;
		}

		try
		{
			return iconResolver.apply(Integer.parseInt(m.group(1)));
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	/**
	 * @return the character an escaped printable pseudo-tag stands for, or null if the tag is
	 * ordinary markup that should simply be dropped
	 */
	private static String unescapeEntity(String tag)
	{
		switch (tag)
		{
			case "<lt>":
				return "<";
			case "<gt>":
				return ">";
			case "<at>":
				return "@";
			case "<nbh>":
				return "-";
			case "<br>":
			case "<n>":
				return " ";
			default:
				return null;
		}
	}

	/**
	 * Colours the item and value in a clan drop broadcast according to its value tier.
	 *
	 * <p>The value comes from the message itself, so this works for untradeables and does not
	 * depend on the item price index having loaded.
	 */
	private List<ColorSpan> applyLootHighlight(List<ColorSpan> spans, String plainText, Color fallback)
	{
		if (config.highlightLootValue())
		{
			LootBroadcast.Match drop = LootBroadcast.parse(plainText);
			if (drop != null)
			{
				Color tier = valueTierColor(drop.value);
				// A value below the lowest threshold stays at the per-type colour.
				// Recognised drops tier even over the game's own flat drop colour — see
				// Highlighter for why this narrow exception exists.
				return tier == null
					? spans
					: Highlighter.highlightRange(spans, plainText, fallback,
						drop.start, drop.end, tier, true);
			}
		}

		if (config.highlightCollectionLog())
		{
			LootBroadcast.Match collection = LootBroadcast.parseCollectionLog(plainText);
			if (collection != null)
			{
				return Highlighter.highlightRange(spans, plainText, fallback,
					collection.start, collection.end, config.collectionLogColor(), true);
			}
		}

		return spans;
	}

	/** @return the tier colour for a drop value, or null when it is below the lowest threshold */
	private Color valueTierColor(long value)
	{
		if (value >= config.insaneValuePrice())
		{
			return config.insaneValueColor();
		}
		if (value >= config.highValuePrice())
		{
			return config.highValueColor();
		}
		if (value >= config.mediumValuePrice())
		{
			return config.mediumValueColor();
		}
		if (value >= config.lowValuePrice())
		{
			return config.lowValueColor();
		}
		return null;
	}

	/**
	 * Resets every text run to the per-type colour, keeping icons. Returns null when nothing
	 * but fallback-coloured text is left, so the caller falls back to plain rendering.
	 */
	static List<ColorSpan> dropSpanColours(List<ColorSpan> spans, Color fallback)
	{
		if (spans == null || spans.isEmpty())
		{
			return null;
		}

		if (spans.stream().noneMatch(ColorSpan::isIcon))
		{
			return null;
		}

		List<ColorSpan> out = new ArrayList<>(spans.size());
		for (ColorSpan span : spans)
		{
			out.add(span.isIcon() ? span : new ColorSpan(span.getText(), fallback));
		}
		return out;
	}

	/**
	 * @return a resolver for inline chat icons, or null when the option is off so that the
	 * parser drops icon tags exactly as it did before
	 */
	private java.util.function.IntFunction<java.awt.image.BufferedImage> iconResolver()
	{
		return config.showChatIcons() ? index -> chatIcons.resolve(client, index) : null;
	}

	/**
	 * Pulls the badges that precede a sender's name out of the raw name string.
	 *
	 * <p>Rank, title and account-type icons are always a prefix in chat, so they are kept as
	 * an ordered list rather than positioned spans.
	 */
	/**
	 * Resolves the rank badge shown beside a sender's name in clan and friends chat.
	 *
	 * <p>These are not in the chat message at all — the chatbox looks the sender's rank up
	 * from the channel as it draws each line, so the same lookup has to happen here.
	 */
	private java.awt.image.BufferedImage resolveRankIcon(ChatMessageType type, String rawName)
	{
		if (!config.showChatIcons() || rawName == null || rawName.isEmpty())
		{
			return null;
		}

		String name = Text.toJagexName(Text.removeTags(rawName));

		switch (type)
		{
			case FRIENDSCHAT:
			{
				net.runelite.api.FriendsChatManager manager = client.getFriendsChatManager();
				if (manager == null)
				{
					return null;
				}
				net.runelite.api.FriendsChatMember member = manager.findByName(name);
				if (member == null)
				{
					return null;
				}
				net.runelite.api.FriendsChatRank rank = member.getRank();
				return rank == null || rank == net.runelite.api.FriendsChatRank.UNRANKED
					? null
					: chatIconManager.getRankImage(rank);
			}

			case CLAN_CHAT:
				return clanRankImage(client.getClanChannel(), client.getClanSettings(), name);

			case CLAN_GUEST_CHAT:
				return clanRankImage(client.getGuestClanChannel(), client.getGuestClanSettings(), name);

			case CLAN_GIM_CHAT:
				return clanRankImage(
					client.getClanChannel(net.runelite.api.clan.ClanID.GROUP_IRONMAN),
					client.getClanSettings(net.runelite.api.clan.ClanID.GROUP_IRONMAN),
					name);

			default:
				return null;
		}
	}

	private java.awt.image.BufferedImage clanRankImage(net.runelite.api.clan.ClanChannel channel,
		net.runelite.api.clan.ClanSettings settings, String name)
	{
		if (channel == null || settings == null)
		{
			return null;
		}

		net.runelite.api.clan.ClanChannelMember member = channel.findMember(name);
		if (member == null || member.getRank() == null)
		{
			return null;
		}

		net.runelite.api.clan.ClanTitle title = settings.titleForRank(member.getRank());
		return title == null ? null : chatIconManager.getRankImage(title);
	}

	private List<java.awt.image.BufferedImage> extractSenderIcons(ChatMessageType type, String rawName)
	{
		if (!config.showChatIcons())
		{
			return null;
		}

		List<java.awt.image.BufferedImage> icons = null;

		// The channel rank badge comes first, matching the chatbox.
		java.awt.image.BufferedImage rank = resolveRankIcon(type, rawName);
		if (rank != null)
		{
			icons = new ArrayList<>(2);
			icons.add(rank);
		}

		if (rawName == null || rawName.indexOf('<') < 0)
		{
			return icons;
		}

		// Some plugins append their own icons to the name (friend notes, for example).
		Matcher matcher = ChatIcons.IMG_TAG.matcher(rawName);
		while (matcher.find())
		{
			java.awt.image.BufferedImage icon =
				chatIcons.resolve(client, Integer.parseInt(matcher.group(1)));
			if (icon == null)
			{
				continue;
			}
			if (icons == null)
			{
				icons = new ArrayList<>(2);
			}
			icons.add(icon);
		}
		return icons;
	}

	/**
	 * @return the channel a message came through, or null when it came through none.
	 *
	 * <p>{@link net.runelite.api.events.ChatMessage#getSender()} carries the clan or friends
	 * chat name and is empty for every other message type, so no type check is needed — only
	 * channel messages have one.
	 */
	static String channelName(String channel)
	{
		if (channel == null || channel.isEmpty())
		{
			return null;
		}

		String cleaned = Text.unescapeJagex(channel).trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

	/**
	 * Prefixes private message senders with "From"/"To" so incoming and outgoing messages are
	 * distinguishable, matching how the in-game chatbox presents them. Without this both
	 * directions render identically, because for PRIVATECHATOUT the name field holds the
	 * recipient rather than the local player.
	 */
	static String applyPrivateMessagePrefix(String sender, ChatMessageType type, boolean enabled)
	{
		if (!enabled)
		{
			return sender;
		}

		switch (type)
		{
			case PRIVATECHAT:
			case MODPRIVATECHAT:
				return "From " + sender;

			case PRIVATECHATOUT:
				return "To " + sender;

			default:
				return sender;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"chatfade".equals(event.getGroup()))
		{
			return;
		}

		if ("ignoredMessages".equals(event.getKey()) || "ignoredRegex".equals(event.getKey()))
		{
			rebuildIgnoreLists();
		}
		else if ("drawUnderInterfaces".equals(event.getKey()))
		{
			applyOverlayLayer();
		}
	}

	/**
	 * Registers the overlay on the layer the config asks for. The overlay manager reads an
	 * overlay's layer only when it is added, so changing it means re-adding.
	 */
	private void applyOverlayLayer()
	{
		overlayManager.remove(overlay);
		overlay.setDrawUnderInterfaces(config.drawUnderInterfaces());
		overlayManager.add(overlay);
	}

	private void rebuildIgnoreLists()
	{
		ignoreList.rebuild(config.ignoredMessages(), config.ignoredRegex());
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
				// Expand once and use the same string for both, so the rendered text and
				// the colour spans can never disagree about what the message says.
				String expanded = expandIfGameAuthored(updated, msg.getType());
				String cleanedUpdate = toDisplayText(expanded);
				if (!cleanedUpdate.equals(msg.getText()))
				{
					msg.setText(cleanedUpdate);
					// Emoji reach us here, not at ingest: the Emoji plugin rewrites the node
				// after ChatMessage has already fired, so this path must resolve icons too.
				List<ColorSpan> updatedSpans = parseColorSpans(expanded, msg.getColor(), iconResolver());
				if (!config.preserveInlineColors())
				{
					updatedSpans = dropSpanColours(updatedSpans, msg.getColor());
				}
				msg.setColorSpans(updatedSpans);
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
			case LOGINLOGOUTNOTIFICATION:
				return config.showPrivateChat();

			case CLAN_CHAT:
			case CLAN_MESSAGE:
				return config.showClanChat();

			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
				return config.showGuestClanChat();

			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
				return config.showGIMChat();

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

	static Color getColorForType(ChatMessageType type)
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
				return new Color(255, 130, 130);

			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
				return new Color(0, 211, 0);

			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
				return new Color(127, 0, 0);

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
				return config.clanChatColor();

			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
				return config.guestChatColor();

			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
				return config.gimChatColor();

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
		return parseColorSpans(raw, fallback, null);
	}

	/**
	 * @param iconResolver turns an {@code <img=N>} index into a drawable icon, or null to
	 * drop icons as before
	 */
	static List<ColorSpan> parseColorSpans(String raw, Color fallback,
		java.util.function.IntFunction<java.awt.image.BufferedImage> iconResolver)
	{
		// Input is macro-expanded, so <col=...> is the only colour syntax that can appear.
		// Icons matter too: a message can be worth rendering as spans purely because it
		// contains one, even with no colour markup at all.
		boolean wantsIcons = iconResolver != null && raw.contains("<img=");
		if (!raw.contains("<col=") && !wantsIcons)
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
					String tag = raw.substring(pos, anyMatcher.end());

					// Escaped printables (<at>, <lt>, ...) stand for a real character.
					String entity = unescapeEntity(tag);
					if (entity != null)
					{
						currentText.append(entity);
						pos = anyMatcher.end();
						continue;
					}

					// Inline chat icons become their own span so the renderer can draw them.
					java.awt.image.BufferedImage icon = resolveIconTag(tag, iconResolver);
					if (icon != null)
					{
						if (currentText.length() > 0)
						{
							spans.add(new ColorSpan(currentText.toString(), currentColor));
							currentText.setLength(0);
						}
						spans.add(ColorSpan.icon(icon));
					}

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

		// Filter out empty spans, but never the icon ones — they carry no text by design.
		spans.removeIf(s -> !s.isIcon() && s.getText().isEmpty());

		// Spans are only worth keeping if they say something a single colour could not:
		// either more than one colour, or at least one icon to draw.
		boolean hasIcon = spans.stream().anyMatch(ColorSpan::isIcon);
		if (!hasIcon && spans.stream().allMatch(s -> fallback.equals(s.getColor())))
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
