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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.vars.InputType;
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

@Slf4j
@PluginDescriptor(
	name = "Chat Fade",
	description = "Shows chat messages as fading floating text above the chatbox",
	tags = {"chat", "fade", "overlay", "collapsed", "notifications"}
)
public class ChatFadePlugin extends Plugin implements KeyListener
{
	/** Config keys that only affect the built-in matchers, so those are rebuilt on any of them. */
	private static final Set<String> FILTER_KEYS = ImmutableSet.of(
		"filteredWords", "filteredRegex", "filteredNames",
		"filterType", "filterStripAccents",
		"allowedWords", "allowedRegex", "allowedNames"
	);

	/** Message types that carry something a player typed, so name filters apply. */
	private static final Set<ChatMessageType> FILTERABLE_PLAYER_CHAT = ImmutableSet.of(
		ChatMessageType.PUBLICCHAT,
		ChatMessageType.MODCHAT,
		ChatMessageType.AUTOTYPER,
		ChatMessageType.PRIVATECHAT,
		ChatMessageType.MODPRIVATECHAT,
		ChatMessageType.FRIENDSCHAT,
		ChatMessageType.CLAN_CHAT,
		ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.CLAN_GIM_CHAT
	);

	/** Game-authored types worth collapsing when they repeat. */
	private static final Set<ChatMessageType> COLLAPSIBLE_GAME_CHAT = ImmutableSet.of(
		ChatMessageType.ENGINE,
		ChatMessageType.GAMEMESSAGE,
		ChatMessageType.ITEM_EXAMINE,
		ChatMessageType.NPC_EXAMINE,
		ChatMessageType.OBJECT_EXAMINE,
		ChatMessageType.SPAM,
		ChatMessageType.NPC_SAY
	);

	/** Game-authored types, only filtered when "Filter Game Chat" is on. */
	private static final Set<ChatMessageType> FILTERABLE_GAME_CHAT = ImmutableSet.of(
		ChatMessageType.GAMEMESSAGE,
		ChatMessageType.ENGINE,
		ChatMessageType.FRIENDSCHATNOTIFICATION,
		ChatMessageType.ITEM_EXAMINE,
		ChatMessageType.NPC_EXAMINE,
		ChatMessageType.OBJECT_EXAMINE,
		ChatMessageType.SPAM,
		ChatMessageType.CLAN_MESSAGE,
		ChatMessageType.CLAN_GUEST_MESSAGE,
		ChatMessageType.CLAN_GIM_MESSAGE,
		ChatMessageType.NPC_SAY
	);

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

	/**
	 * Interfaces that borrow the chat area, and the component in each that has to be on screen
	 * for it to count.
	 *
	 * <p>Named from {@code gameval.InterfaceID} throughout. This list used to mix those with the
	 * deprecated {@code widgets.InterfaceID} spellings of the very same numbers — DIALOG_NPC is
	 * CHAT_LEFT, DIALOG_SPRITE is OBJECTBOX, DIALOG_OPTION is CHATMENU — and reading it then
	 * told you nothing about what was absent. The plain "Click here to continue" message box sat
	 * missing behind that for as long as the feature has existed, so a quest step waiting on it
	 * looked like a quest doing nothing at all.
	 */
	static final Set<Map.Entry<Integer, Integer>> AUTO_EXPAND_WIDGETS = ImmutableSet
		.<Map.Entry<Integer, Integer>>builder()
		// Somebody talking.
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHAT_LEFT, 0))         // 231, an NPC
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHAT_RIGHT, 0))        // 217, you
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHATMENU, 0))          // 219, choose an option
		// Boxes that wait on a click, which is what makes missing one so disorienting.
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.MESSAGEBOX, 0))        // 229, text only
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.MESSAGEBOX_TITLED, 0)) // 923
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.MESSAGEBOX_URL, 0))    // 917
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.OBJECTBOX, 0))         // 193, one item
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.OBJECTBOX_DOUBLE, 0))  // 11, two items
		// Prompts that take input.
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.SKILLMULTI, 0))        // 270, "how many?"
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHATBOX, 42))
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHATBOX, 43))
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHATBOX, 48))
		.add(new AbstractMap.SimpleEntry<>(InterfaceID.CHATBOX, 566))
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
	private final MessageFilter messageFilter = new MessageFilter();

	/**
	 * The same matcher, loaded with the words to watch *for* rather than the words to hide.
	 * Matching is matching, so the allowlist is a second instance rather than a second
	 * implementation.
	 */
	private final MessageFilter allowList = new MessageFilter();
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

			if (msg.isCensored())
			{
				// Our own filter already rewrote this one; the chatbox's version of the text
				// would undo that substitution.
				return;
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

		// A collapsed line is reported blocked once per duplicate by the Chat Filter plugin's
		// own collapsing, which would delete the very line those duplicates were folded into.
		messages.removeIf(m -> m.getMessageId() == blockedId && m.getCount() <= 1);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		ChatMessageType type = chatMessage.getType();

		if (!isMessageTypeEnabled(type))
		{
			return;
		}

		if (!isAllowedByChatFilter(type, chatMessage.getName()))
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

		// Asked before the gate, and of the watch list rather than of the gate's verdict: a
		// matched message is held longer whether or not unmatched ones are being hidden.
		final boolean watched = allowList.isActive()
			&& isWatched(allowList, chatMessage.getName(), expanded);

		// With the allowlist armed, matching one of the watched entries is the only way in.
		if (config.allowOnlyMatching() && allowList.isActive() && !watched)
		{
			return;
		}

		// Messages blocked by the Chat Filter plugin are removed in onScriptCallbackEvent,
		// which necessarily runs after this event — see the comment there.

		// The built-in filter, by contrast, runs right here. It does not depend on another
		// plugin being installed, and more importantly not on the chatbox rebuild callback,
		// which is not guaranteed to fire while the chatbox is collapsed — the one state this
		// plugin exists for.
		MessageFilter.Result filterResult = applyMessageFilter(type, chatMessage.getName(), expanded);
		if (filterResult.getOutcome() == MessageFilter.Outcome.BLOCK)
		{
			return;
		}

		logBroadcastDiagnostics(type, chatMessage.getMessage());

		String rawMessage = stripIngestPrefixes(expanded);
		String cleanedText = toDisplayText(rawMessage);

		String sender = chatMessage.getName();
		List<java.awt.image.BufferedImage> senderIcons = extractSenderIcons(type, sender);
		if (sender != null && !sender.isEmpty())
		{
			sender = toDisplayText(sender);
			sender = applyPrivateMessagePrefix(sender, type, config.showPmDirection());
		}

		// NPC speech arrives as "NPC Name|what they said" — split it so the name renders
		// separately just like player chat.
		String rawForSpans = rawMessage;
		if (isNpcSpeech(type))
		{
			String[] parts = splitSpeaker(cleanedText);
			if (parts != null)
			{
				sender = parts[0];
				cleanedText = parts[1];

				// The raw message is split too, so colour spans are parsed from the speech
				// rather than from the name that precedes it.
				String[] rawParts = splitSpeaker(rawMessage);
				if (rawParts != null)
				{
					rawForSpans = rawParts[1];
				}
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

		// Censoring removes markup, just as the game's own filter does, so the inline colours
		// and loot highlighting no longer line up with the characters and go with it.
		boolean censored = filterResult.getOutcome() == MessageFilter.Outcome.CENSORED;
		if (censored)
		{
			cleanedText = filterResult.getText();
			colorSpans = null;
		}

		// Store MessageNode so we can detect async updates (e.g. emoji plugin replacing text with <img=X> tags)
		MessageNode messageNode = chatMessage.getMessageNode();

		String collapseSender = (sender == null || sender.isEmpty()) ? null : sender;
		if (shouldCollapse(type))
		{
			FadingMessage target = collapseTarget(messages, cleanedText, collapseSender, type,
				displayMsFor(false, 0, config.displayDuration()) + config.fadeDuration() * 1000L,
				displayMsFor(true, config.watchedDuration(), config.displayDuration())
					+ config.fadeDuration() * 1000L,
				System.currentTimeMillis());
			if (target != null)
			{
				target.setCount(target.getCount() + 1);
				// Restart the fade so a message that keeps repeating keeps itself on screen.
				target.setTimestamp(System.currentTimeMillis());
				if (messageNode != null)
				{
					// Follow the newest copy's id so a later block removes the line we are
					// actually showing. The node itself is deliberately not adopted: the
					// per-tick refresh reads text back out of it, which would undo the
					// filter's censoring on a message we have already rewritten.
					target.setMessageId(messageNode.getId());
				}
				// A repeat is the newest thing that happened, so the line moves to where the
				// newest message belongs rather than staying in the slot its first copy took.
				moveToNewest(messages, target);
				return;
			}
		}

		FadingMessage fadingMessage = FadingMessage.builder()
			.senderName(sender != null && !sender.isEmpty() ? sender : null)
			.text(cleanedText)
			.type(type)
			.watched(watched)
			.timestamp(System.currentTimeMillis())
			.color(color)
			.colorSpans(colorSpans)
			// Dropped for a censored message: the per-tick pass refreshes text from the node,
			// which still holds the original wording and would put it straight back.
			.messageNode(censored ? null : messageNode)
			.messageId(messageNode != null ? messageNode.getId() : -1)
			.rawText(chatMessage.getMessage())
			.senderIcons(senderIcons)
			.censored(censored)
			.pendingIcons(!censored && hasUnresolvedIcon(rawForSpans))
			.spanSource(rawForSpans)
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
	/** A broadcast's named value: {@code <str_quest_name_0=The Lost Tribe>}. */
	private static final Pattern STRING_TEMPLATE = Pattern.compile("<(str_\\w+)=([^>]*)>");

	private static final Pattern SKILL_ID_PREFIX =
		Pattern.compile("^((?:<[^>]+>|\\s)*)\\d+\\|");

	/**
	 * Drops the whitespace left in front of the first real text once leading icons are gone.
	 *
	 * <p>{@link #toDisplayText} trims, so a broadcast like {@code <img=19> Bob received...}
	 * displays with no leading space. The spans kept it, and because highlight offsets are
	 * measured against the display text, every highlight on such a message landed a character
	 * early — starting on the space and clipping the last letter of the item name.
	 *
	 * <p>Walks past as many icons and all-whitespace spans as it takes: clan broadcasts can
	 * carry a rank badge and an account-type badge, and stopping at the first of them would
	 * leave the same off-by-one behind the second.
	 */
	private static void trimLeadingSpace(List<ColorSpan> spans)
	{
		for (int i = 0; i < spans.size(); i++)
		{
			ColorSpan span = spans.get(i);
			if (span.isIcon())
			{
				continue;
			}

			String trimmed = span.getText().replaceFirst("^\\s+", "");
			if (!trimmed.equals(span.getText()))
			{
				spans.set(i, new ColorSpan(trimmed, span.getColor()));
			}

			// Only an all-whitespace span can have more trimming after it; anything else
			// means the real text has started and the offsets now line up.
			if (!trimmed.isEmpty())
			{
				return;
			}
		}
	}

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
		if (isPlayerAuthored(type))
		{
			return message == null ? "" : message;
		}

		// Templates are expanded after the colour macros so the substituted value is plain
		// text by the time anything downstream looks at it.
		return expandStringTemplates(macroExpand(message));
	}

	/**
	 * Substitutes the game's string templates into the message.
	 *
	 * <p>Some broadcasts carry their variable part as a named template rather than inline:
	 * a definition holding the value, then a reference where it belongs, as in
	 * {@code <str_quest_name_0=The Lost Tribe>Bob has completed a quest: <str_quest_name_0>}.
	 *
	 * <p>Both halves look like ordinary markup, so stripping tags took the quest name with
	 * them and left the message ending at its colon — which is what made quest completions
	 * arrive with the quest missing (#32). Expanding here, before anything strips tags, means
	 * the display text and the colour spans both end up with the real value.
	 */
	static String expandStringTemplates(String message)
	{
		if (message == null || !message.contains("<str_"))
		{
			return message;
		}

		Matcher matcher = STRING_TEMPLATE.matcher(message);
		Map<String, String> values = new java.util.LinkedHashMap<>();
		StringBuffer withoutDefinitions = new StringBuffer();
		while (matcher.find())
		{
			values.put(matcher.group(1), matcher.group(2));
			// The definition itself is not shown; it only carries the value.
			matcher.appendReplacement(withoutDefinitions, "");
		}
		matcher.appendTail(withoutDefinitions);

		String expanded = withoutDefinitions.toString();
		for (Map.Entry<String, String> value : values.entrySet())
		{
			expanded = expanded.replace("<" + value.getKey() + ">", value.getValue());
		}
		return expanded;
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

		if ("ignoredMessages".equals(event.getKey()) || "ignoredRegex".equals(event.getKey())
			|| FILTER_KEYS.contains(event.getKey()))
		{
			rebuildIgnoreLists();
		}
		else if ("drawUnderInterfaces".equals(event.getKey())
			|| "anchoredOverlay".equals(event.getKey()))
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
		overlay.setAnchored(config.anchoredOverlay());
		overlayManager.add(overlay);

		if (!config.anchoredOverlay())
		{
			// Dragging the box stores a preferred location, and RuneLite keeps translating
			// to it even once the overlay goes back to positioning itself. That moved the
			// text off screen entirely, so the stored position is cleared on the way out.
			overlayManager.resetOverlay(overlay);
		}
	}

	/**
	 * How long a message holds at full opacity before it starts to fade.
	 *
	 * <p>A watched message gets its own duration, but only when one has been set: 0 means the
	 * setting is off, not that the message should vanish instantly.
	 */
	static long displayMsFor(boolean watched, int watchedSeconds, int defaultSeconds)
	{
		int seconds = (watched && watchedSeconds > 0) ? watchedSeconds : defaultSeconds;
		return seconds * 1000L;
	}

	/** The display duration this message has earned, in milliseconds. */
	long displayMsFor(FadingMessage message)
	{
		return displayMsFor(message.isWatched(), config.watchedDuration(), config.displayDuration());
	}

	/**
	 * Moves a collapsed line to the end of the list, where the newest message lives.
	 *
	 * <p>Matched by identity rather than equality. {@code FadingMessage} is a Lombok
	 * {@code @Data} class, so {@code List.remove(Object)} would compare every field and could
	 * pull out a different message that happened to look the same.
	 *
	 * <p>Does nothing when the message is already last, which is the usual case — a repeat
	 * normally follows its twin immediately — and skips the two array copies that moving an
	 * element through a {@link java.util.concurrent.CopyOnWriteArrayList} would otherwise
	 * cost on every duplicate.
	 */
	static void moveToNewest(List<FadingMessage> messages, FadingMessage target)
	{
		int last = messages.size() - 1;
		for (int i = last; i >= 0; i--)
		{
			if (messages.get(i) == target)
			{
				if (i != last)
				{
					messages.remove(i);
					messages.add(target);
				}
				return;
			}
		}
	}

	/** Whether repeats of this message type should fold into one line with a counter. */
	private boolean shouldCollapse(ChatMessageType type)
	{
		if (type == ChatMessageType.PUBLICCHAT || type == ChatMessageType.MODCHAT)
		{
			return config.collapsePlayerMessages();
		}

		return COLLAPSIBLE_GAME_CHAT.contains(type) && config.collapseGameMessages();
	}

	/**
	 * Finds a still-visible message that this one is a repeat of.
	 *
	 * <p>Searches newest first, since a repeat almost always follows its twin closely, and
	 * requires the sender and type to match as well as the text — two different players saying
	 * "hi" are not the same event, and neither is the same words arriving on another channel.
	 *
	 * @param lifetimeMs how long a message stays on screen, so faded-out lines are not revived
	 */
	static FadingMessage collapseTarget(List<FadingMessage> from, String text, String sender,
		ChatMessageType type, long lifetimeMs, long watchedLifetimeMs, long now)
	{
		if (text == null)
		{
			return null;
		}

		for (int i = from.size() - 1; i >= 0; i--)
		{
			FadingMessage existing = from.get(i);

			// A watched line outlives the rest, so the window it can still be collapsed into
			// has to be its own — otherwise a repeat opens a second copy underneath the first.
			long limit = existing.isWatched() ? watchedLifetimeMs : lifetimeMs;
			if (now - existing.getTimestamp() > limit)
			{
				continue;
			}
			if (existing.getType() != type)
			{
				continue;
			}

			String existingSender = (existing.getSenderName() == null || existing.getSenderName().isEmpty())
				? null : existing.getSenderName();
			if (!java.util.Objects.equals(existingSender, sender))
			{
				continue;
			}
			if (!text.equals(existing.getText()))
			{
				continue;
			}

			return existing;
		}

		return null;
	}

	/**
	 * Logs the untouched text of broadcasts that have been reported as rendering wrongly.
	 *
	 * <p>Quest completions lose the quest name for some people (#32), and the formats that
	 * could explain it all render correctly when run through this pipeline — so the game is
	 * evidently sending something other than what the logs from six months ago show. The
	 * message is gone by the time anyone notices on screen, so capture it as it arrives.
	 *
	 * <p>Debug level, so it costs nothing unless someone turns it on, and deliberately narrow:
	 * only the broadcast shapes under investigation, never ordinary chat, which would amount
	 * to logging private conversations.
	 */
	private void logBroadcastDiagnostics(ChatMessageType type, String raw)
	{
		if (!log.isDebugEnabled() || raw == null)
		{
			return;
		}

		if (type != ChatMessageType.CLAN_MESSAGE
			&& type != ChatMessageType.CLAN_GUEST_MESSAGE
			&& type != ChatMessageType.CLAN_GIM_MESSAGE)
		{
			return;
		}

		if (raw.contains("completed a quest"))
		{
			log.debug("Chat Fade raw quest broadcast [{}]: {}", type, raw);
		}
	}

	/**
	 * Rebuilds the spans of a message whose icons were not available when it arrived.
	 *
	 * <p>Costs nothing in the normal case: the flag is only ever set for a message that had
	 * an unresolvable tag, and it clears the moment the icon appears, so this stops as soon
	 * as it has done its job rather than running for the life of every message.
	 */
	private void retryPendingIcons(FadingMessage msg)
	{
		if (!msg.isPendingIcons())
		{
			return;
		}

		if (!config.showChatIcons())
		{
			// Icons are off, so there is nothing left to wait for.
			msg.setPendingIcons(false);
			return;
		}

		if (hasUnresolvedIcon(msg.getSpanSource()))
		{
			return;
		}

		List<ColorSpan> spans = parseColorSpans(msg.getSpanSource(), msg.getColor(), iconResolver());
		if (!config.preserveInlineColors())
		{
			spans = dropSpanColours(spans, msg.getColor());
		}
		msg.setColorSpans(applyLootHighlight(spans, msg.getText(), msg.getColor()));
		msg.setPendingIcons(false);
	}

	/**
	 * Drops converted icons when the client rebuilds its sprite array.
	 *
	 * <p>{@link ChatIcons} otherwise only notices a rebuild when the array changes length,
	 * which misses one rebuilt at the same size — a logging out and back in, or a plugin
	 * re-registering its icons. The stale entries that follow are what made emoji come back
	 * blank for some people and not others.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.LOGGING_IN)
		{
			chatIcons.clear();
		}
	}

	/**
	 * Whether any {@code <img=N>} in this message could not be resolved yet.
	 *
	 * <p>Icons are registered asynchronously — the Emoji plugin adds its sprites after the
	 * client starts — so a message arriving in that window finds nothing at its index. The
	 * tag is dropped rather than drawn, and because the plain text has no icon either the
	 * whole body can vanish, leaving just "name:". Flagging it lets the per-tick pass try
	 * again instead of losing the icon for the life of the message.
	 */
	private boolean hasUnresolvedIcon(String raw)
	{
		if (raw == null || !config.showChatIcons())
		{
			return false;
		}

		Matcher matcher = ChatIcons.IMG_TAG.matcher(raw);
		while (matcher.find())
		{
			try
			{
				if (chatIcons.resolve(client, Integer.parseInt(matcher.group(1))) == null)
				{
					return true;
				}
			}
			catch (NumberFormatException ex)
			{
				// Not an index we could ever resolve; nothing to wait for.
			}
		}
		return false;
	}

	/**
	 * Censors the text that floats above a player's head.
	 *
	 * <p>Overhead text never goes through the chat message pipeline, so the ingest filter
	 * cannot reach it — the words are still readable over the player even once the chat line
	 * itself has been starred out. This is the one place the plugin changes something outside
	 * its own overlay, which is why it can be turned off on its own.
	 */
	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		if (!messageFilter.isActive() || !config.filterOverheadText())
		{
			return;
		}

		if (!(event.getActor() instanceof Player))
		{
			// NPC chatter is not something a player typed.
			return;
		}

		String name = event.getActor().getName();
		if (name == null)
		{
			return;
		}

		String standardized = Text.standardize(name);
		if (standardized.isEmpty() || !canFilterPlayer(standardized))
		{
			return;
		}

		MessageFilter.Result result = messageFilter.apply(name, event.getOverheadText());
		switch (result.getOutcome())
		{
			case BLOCK:
				// A blank string would leave the previous text on screen, so draw a space.
				event.getActor().setOverheadText(" ");
				break;
			case CENSORED:
				event.getActor().setOverheadText(result.getText());
				break;
			default:
				break;
		}
	}

	/**
	 * Applies the built-in filter, deciding first whether this message is even in scope.
	 *
	 * <p>Scope mirrors the game's own filter: anything a player typed can be filtered by name
	 * or wording, game-authored text only when "Filter Game Chat" is on, and your own messages
	 * never are. Friends, friends chat members and clan mates are exempt unless opted in.
	 */
	private MessageFilter.Result applyMessageFilter(ChatMessageType type, String sender, String message)
	{
		if (!messageFilter.isActive())
		{
			return MessageFilter.Result.PASS;
		}

		if (FILTERABLE_PLAYER_CHAT.contains(type))
		{
			String name = Text.standardize(sender == null ? "" : sender);
			if (name.isEmpty() || !canFilterPlayer(name))
			{
				return MessageFilter.Result.PASS;
			}
			return messageFilter.apply(sender, message);
		}

		if (FILTERABLE_GAME_CHAT.contains(type) && config.filterGameChat())
		{
			// No sender to match names against, so only the word and pattern filters apply.
			return messageFilter.apply(null, message);
		}

		return MessageFilter.Result.PASS;
	}

	/**
	 * The allowlist gate: when it is armed, only messages matching a watched word, pattern or
	 * name reach the overlay (#37, requested by @Infinitay).
	 *
	 * <p>An empty watch list counts as unarmed rather than as "matches nothing", so turning the
	 * toggle on before filling anything in does not silence the overlay outright.
	 *
	 * <p>Unlike the filter this has no notion of scope — "only show matching" means exactly
	 * that, including game messages and your own chat.
	 */
	static boolean isAllowedByWatchList(MessageFilter watchList, boolean enabled, String sender,
		String message)
	{
		if (!enabled || !watchList.isActive())
		{
			return true;
		}

		return isWatched(watchList, sender, message);
	}

	/**
	 * Whether a message matches the watch list, ignoring whether the gate is armed.
	 *
	 * <p>Separate from {@link #isAllowedByWatchList} because matching now means two things:
	 * it decides what gets through when "Only Show Matching" is on, and it decides what stays
	 * on screen longer regardless.
	 */
	static boolean isWatched(MessageFilter watchList, String sender, String message)
	{
		if (!watchList.isActive())
		{
			return false;
		}

		// Game-authored lines arrive with an empty sender rather than none, and a watched name
		// pattern must not be handed an empty string to match against.
		String name = (sender == null || sender.isEmpty()) ? null : sender;
		return watchList.matches(name, message);
	}

	/**
	 * Whether a message is an NPC (or your own character) speaking.
	 *
	 * <p>{@code DIALOG} and {@code MESBOX} are the dialogue box; {@code NPC_SAY} is the line an
	 * NPC says over their head that the game also puts in the chatbox — Minimus greeting you at
	 * the Colosseum, or a quest NPC calling out mid-cutscene. All three carry the speaker in the
	 * same {@code Name|text} form and all three are somebody talking, so they are treated alike.
	 *
	 * <p>Which of them the overlay *shows* is a separate question, still decided per type.
	 */
	static boolean isNpcSpeech(ChatMessageType type)
	{
		return type == ChatMessageType.DIALOG
			|| type == ChatMessageType.MESBOX
			|| type == ChatMessageType.NPC_SAY;
	}

	/**
	 * Splits {@code "Name|what they said"} into its two halves.
	 *
	 * @return {name, speech}, or null when there is no separator and the caller should leave
	 *     the message as it found it
	 */
	static String[] splitSpeaker(String text)
	{
		if (text == null)
		{
			return null;
		}

		int sep = text.indexOf('|');
		if (sep < 0)
		{
			return null;
		}

		return new String[]{text.substring(0, sep).trim(), text.substring(sep + 1).trim()};
	}

	/** @param standardizedName sender name already put through {@link Text#standardize} */
	private boolean canFilterPlayer(String standardizedName)
	{
		Player local = client.getLocalPlayer();
		if (local != null && standardizedName.equals(Text.standardize(local.getName() == null ? "" : local.getName())))
		{
			// Never filter yourself — you already know what you said.
			return false;
		}

		if (!config.filterFriends() && client.isFriended(standardizedName, false))
		{
			return false;
		}

		if (!config.filterFriendsChat() && isFriendsChatMember(standardizedName))
		{
			return false;
		}

		return config.filterClanChat() || !isClanChatMember(standardizedName);
	}

	private boolean isFriendsChatMember(String name)
	{
		net.runelite.api.FriendsChatManager manager = client.getFriendsChatManager();
		return manager != null && manager.findByName(name) != null;
	}

	private boolean isClanChatMember(String name)
	{
		net.runelite.api.clan.ClanChannel channel = client.getClanChannel();
		if (channel != null && channel.findMember(name) != null)
		{
			return true;
		}

		channel = client.getClanChannel(net.runelite.api.clan.ClanID.GROUP_IRONMAN);
		return channel != null && channel.findMember(name) != null;
	}

	private void rebuildIgnoreLists()
	{
		ignoreList.rebuild(config.ignoredMessages(), config.ignoredRegex());
		messageFilter.rebuild(
			config.filteredWords(),
			config.filteredRegex(),
			config.filteredNames(),
			config.filterType(),
			config.filterStripAccents()
		);
		allowList.rebuild(
			config.allowedWords(),
			config.allowedRegex(),
			config.allowedNames(),
			// Only ever asked whether a message matches, never what to do about it, so the
			// type is immaterial — it just has to be something.
			MessageFilterType.REMOVE_MESSAGE,
			config.filterStripAccents()
		);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		reportOpenedInterfaces();

		for (FadingMessage msg : messages)
		{
			retryPendingIcons(msg);

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

					// The rewrite is the text the spans now describe, and it may itself carry
					// an icon that is not registered yet — keep both in step with the retry.
					msg.setSpanSource(expanded);
					msg.setPendingIcons(hasUnresolvedIcon(expanded));
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

	/**
	 * Whether something is currently using the chat area: NPC or quest dialogue, a "how many?"
	 * prompt, the fairy ring or spellbook search.
	 *
	 * <p>Fixed Mode asks this to decide when to put the chatbox back; the overlay asks it to
	 * decide when to get out of the way. Same question either way, so it is answered once.
	 */
	boolean isChatDialogOpen()
	{
		// Input prompts are deliberately excluded here, though Fixed Mode's auto-expand counts
		// them. Where auto-expand can reopen the chatbox for a prompt, the overlay already
		// stands down on its own — isChatboxAway() reads the chat area, which is no longer
		// collapsed. So counting prompts here changes nothing in the cases that work, and in
		// the one that does not it costs the player both: the Grand Exchange's search sets an
		// input mode, but its interface keeps the chatbox from coming back, so the overlay
		// would vanish for a chatbox that never arrives.
		return !findAutoExpandMatch(false).isEmpty();
	}

	/** The entry that last satisfied the check, so a change in it can be logged. */
	private String lastAutoExpandMatch = "";

	private boolean isAnyAutoExpandWidgetVisible()
	{
		String match = findAutoExpandMatch(true);

		if (log.isDebugEnabled() && !match.equals(lastAutoExpandMatch))
		{
			lastAutoExpandMatch = match;
			log.debug("CHATFADE-DIAG auto-expand match now [{}] chatHidden={}",
				match.isEmpty() ? "none" : match, chatHidden);
		}

		return !match.isEmpty();
	}

	/**
	 * @param includeInputPrompts whether a chat-area input prompt counts, which Fixed Mode's
	 *     auto-expand wants and the overlay's hide gate does not — see {@link #isChatDialogOpen}
	 * @return a label for the entry that counts as open, or "" when none does
	 */
	private String findAutoExpandMatch(boolean includeInputPrompts)
	{
		// The client's own answer to "is the chat area taking input?", and the only test here
		// that cannot go circular: it reads a variable the game sets, not a widget whose
		// visibility Fixed Mode is itself responsible for. Covers the cases that have no
		// stable component to point at — the Grand Exchange's item search, bank search, the
		// "enter amount" prompts — as well as the chatbox panels RuneLite plugins open.
		final int inputMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		if (includeInputPrompts && isInputPrompt(inputMode))
		{
			return "input-prompt:" + inputMode;
		}

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
					return "fairy-ring-search";
				}
			}
		}

		for (Map.Entry<Integer, Integer> entry : AUTO_EXPAND_WIDGETS)
		{
			final Widget widget = client.getWidget(entry.getKey(), entry.getValue());
			if (widget == null)
			{
				// A closed interface is gone, not merely hidden, so there is nothing to ask.
				continue;
			}

			final Widget[] staticChildren = widget.getStaticChildren();
			final Widget[] children = (staticChildren != null && staticChildren.length > 0)
				? staticChildren
				: widget.getNestedChildren();

			boolean open = needsParentAwareTest(entry.getKey())
				? isVisibleIncludingParents(widget, children)
				: countsAsOpen(widget.isSelfHidden(), selfHiddenFlags(children));

			if (open)
			{
				return entry.getKey() + ":" + entry.getValue();
			}
		}
		return "";
	}

	/**
	 * Whether {@code MESLAYERMODE} says the chat area is hosting an input prompt.
	 *
	 * <p>{@link InputType#NONE} is the only value meaning "nothing". The game uses positive
	 * values for its own prompts ({@link InputType#SEARCH} for item and bank search,
	 * {@link InputType#PRIVATE_MESSAGE} for addressing a player, others for the numeric
	 * entries), and RuneLite uses negative ones for the chatbox panels its plugins open. All
	 * of them borrow the chat area, so all of them count.
	 *
	 * <p>Deliberately phrased as "not NONE" rather than a list of known values: the enum only
	 * names the handful RuneLite cares about, and an unrecognised prompt is still a prompt.
	 */
	static boolean isInputPrompt(int meslayerMode)
	{
		return meslayerMode != InputType.NONE.getType();
	}

	/**
	 * Whether an entry is a component of the chatbox itself rather than a separate interface
	 * that opens inside it.
	 *
	 * <p>The distinction decides which visibility test applies, and getting it wrong is not
	 * subtle: the chatbox's own components are always present and always self-visible, so
	 * judging them on their own flag alone matches forever. That pinned auto-expand on, which
	 * kept the chatbox permanently open and the overlay permanently suppressed — no messages,
	 * no typing line, no caret.
	 */
	static boolean needsParentAwareTest(int group)
	{
		return group == InterfaceID.CHATBOX;
	}

	/**
	 * The original visibility test, still used for the chatbox's own components.
	 *
	 * <p>It consults the parent chain, so while the chatbox frame is hidden these never match.
	 * That is exactly the behaviour this plugin shipped with, deliberately restored rather than
	 * replaced: the circularity that justified dropping the parent check applies to interfaces
	 * nested inside the hidden frame, not to the frame's own parts. Whether a search prompt
	 * ought to reopen the chatbox is a real question, but a separate one — and not worth
	 * breaking the overlay to answer.
	 */
	private static boolean isVisibleIncludingParents(Widget widget, Widget[] children)
	{
		if (widget.isSelfHidden())
		{
			return false;
		}

		if (children == null || children.length == 0)
		{
			return !widget.isHidden();
		}

		for (Widget child : children)
		{
			if (child != null && !child.isSelfHidden() && !child.isHidden())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether one of {@link #AUTO_EXPAND_WIDGETS} is currently up.
	 *
	 * <p>Only each widget's <em>own</em> hidden flag may be consulted here. {@code isHidden()}
	 * walks the parent chain, and the chatbox frame is in that chain — the very frame this
	 * feature hid. Asking it produced a circular answer: the dialogue read as hidden because the
	 * chatbox was hidden, so the chatbox was never put back, so the dialogue stayed hidden. That
	 * is why auto-expand appeared to work mid-conversation, when something had already reopened
	 * the chatbox, and failed on the first interaction — searching a shelf, clicking Buy at the
	 * Grand Exchange — when nothing had.
	 *
	 * @param selfHidden the widget's own hidden flag
	 * @param childrenSelfHidden each child's own hidden flag; empty when it has no children
	 */
	static boolean countsAsOpen(boolean selfHidden, boolean[] childrenSelfHidden)
	{
		if (selfHidden)
		{
			return false;
		}

		if (childrenSelfHidden == null || childrenSelfHidden.length == 0)
		{
			// Nothing nested to inspect, and the widget itself is showing.
			return true;
		}

		for (boolean hidden : childrenSelfHidden)
		{
			if (!hidden)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean[] selfHiddenFlags(Widget[] widgets)
	{
		if (widgets == null)
		{
			return new boolean[0];
		}

		boolean[] flags = new boolean[widgets.length];
		for (int i = 0; i < widgets.length; i++)
		{
			flags[i] = widgets[i] == null || widgets[i].isSelfHidden();
		}
		return flags;
	}

	/**
	 * Reports every interface the game opens, and whether Fixed Mode's auto-expand recognised
	 * it.
	 *
	 * <p>That auto-expand only puts the chatbox back for the widgets named in
	 * {@link #AUTO_EXPAND_WIDGETS}, so a prompt missing from that list leaves the chatbox shut
	 * and the player with no sign anything happened — reported for the Grand Exchange's "Buy"
	 * search and for quest dialogue. A line here saying {@code autoExpand=false} names the
	 * group that needs adding, which beats guessing interface numbers.
	 *
	 * <p>Debug builds only, and driven by the event rather than polled, so it costs a live
	 * install nothing.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (log.isDebugEnabled())
		{
			// Reported next tick, not now: at load time the interface's children are not built
			// yet, so asking whether auto-expand sees it always answers no and proves nothing.
			openedThisTick.add(event.getGroupId());
		}
	}

	/** Interfaces opened since the last tick, awaiting their reading — see {@link #onWidgetLoaded}. */
	private final List<Integer> openedThisTick = new java.util.ArrayList<>();

	private void reportOpenedInterfaces()
	{
		if (openedThisTick.isEmpty())
		{
			return;
		}

		boolean recognised = isAnyAutoExpandWidgetVisible();
		for (Integer group : openedThisTick)
		{
			log.debug("CHATFADE-DIAG interface {} open — fixed={} hideChat={} chatHidden={} autoExpand={}",
				group, !client.isResized(), config.fixedModeHideChat(), chatHidden, recognised);
		}
		openedThisTick.clear();
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
		long fadeMs = config.fadeDuration() * 1000L;

		Iterator<FadingMessage> it = messages.iterator();
		while (it.hasNext())
		{
			FadingMessage msg = it.next();
			// Per message, since a watched one may be holding far longer than the rest.
			if (now - msg.getTimestamp() > displayMsFor(msg) + fadeMs)
			{
				messages.remove(msg);
			}
		}
	}

	/**
	 * Decides whether a message the game's own chat tabs are hiding should still fade in.
	 *
	 * @param sender the message's sender, needed for the Public tab's "Friends" state
	 */
	private boolean isAllowedByChatFilter(ChatMessageType type, String sender)
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
				// The Public tab has four states, not three: On, Friends, Off and Hide.
				// Only "Off" used to be honoured, so someone who set Public to Hide still
				// saw every passing player fade in here while the game hid them everywhere
				// else.
				String state = getTabFilterText(InterfaceID.Chatbox.CHAT_PUBLIC_FILTER);
				logUnknownPublicFilterState(state);
				return allowedByPublicFilter(state, isSelfOrFriend(sender));
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

	/**
	 * Applies the Public chat tab's state to one message.
	 *
	 * <p>Kept separate and free of client state so the four-way behaviour is testable.
	 * An unrecognised state shows the message: the states are read from widget text, and
	 * going quiet on something unexpected would hide public chat with no way to tell why.
	 *
	 * @param selfOrFriend whether the sender is you or a friend, which is all "Friends" keeps
	 */
	static boolean allowedByPublicFilter(String state, boolean selfOrFriend)
	{
		if ("Off".equals(state) || "Hide".equals(state))
		{
			return false;
		}

		if ("Friends".equals(state))
		{
			return selfOrFriend;
		}

		return true;
	}

	/**
	 * Reports a Public tab state we do not act on.
	 *
	 * <p>The states are read as widget text rather than from a varbit, so the exact wording
	 * is an assumption. If the game labels a state something other than the four handled
	 * here, this says so in the log instead of the setting quietly doing nothing.
	 */
	private void logUnknownPublicFilterState(String state)
	{
		if (!log.isDebugEnabled() || state == null)
		{
			return;
		}

		if (!"On".equals(state) && !"Off".equals(state)
			&& !"Hide".equals(state) && !"Friends".equals(state))
		{
			log.debug("Chat Fade: unhandled Public chat tab state \"{}\"", state);
		}
	}

	/** @return true when this name is the local player or one of their friends */
	private boolean isSelfOrFriend(String sender)
	{
		if (sender == null || sender.isEmpty())
		{
			return false;
		}

		String name = Text.standardize(sender);
		if (name.isEmpty())
		{
			return false;
		}

		Player local = client.getLocalPlayer();
		if (local != null && local.getName() != null
			&& name.equals(Text.standardize(local.getName())))
		{
			// Your own messages stay visible on Friends, exactly as they do in the chatbox.
			return true;
		}

		return client.isFriended(name, false);
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

	/**
	 * Colour the overlay uses for a message type, honouring the default/custom colour choice.
	 */
	Color colorForType(ChatMessageType type)
	{
		return config.useOriginalColors() ? getColorForType(type) : getCustomColorForType(type);
	}

	/**
	 * Colour for the typing line, taken from the channel the message is headed for.
	 *
	 * <p>Reuses the overlay's own per-channel colours rather than introducing a second set, so
	 * a clan message looks the same while you are typing it as it will once it is sent.
	 */
	Color typingLineColor()
	{
		if (!config.colorTypingByChannel())
		{
			return Color.WHITE;
		}

		TypingChannel channel = TypingChannel.resolve(
			client.getVarcStrValue(VarClientID.CHATINPUT),
			client.getFriendsChatManager() != null,
			isGroupIronman(),
			client.getVarcIntValue(VarClientID.CHAT_VIEW),
			client.getVarcIntValue(VarClientID.CHATBOX_MODE)
		);

		return colorForType(channel.getMessageType());
	}

	private boolean isGroupIronman()
	{
		switch (client.getVarbitValue(VarbitID.IRONMAN))
		{
			case 4: // group
			case 5: // hardcore group
			case 6: // unranked group
				return true;
			default:
				return false;
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
			case NPC_SAY:
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
			case NPC_SAY:
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
				// toDisplayText folds newlines to spaces; spans must agree or every
				// character offset past the newline is off by the difference.
				char c = raw.charAt(pos);
				currentText.append(c == '\n' ? ' ' : c);
				pos++;
			}
		}

		if (currentText.length() > 0)
		{
			spans.add(new ColorSpan(currentText.toString(), currentColor));
		}

		trimLeadingSpace(spans);

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
