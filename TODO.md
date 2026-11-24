# Chat Fade - TODO

## Completed
- **Typing input overlay** — Show typed text with blinking `>` when chatbox is collapsed
- **Command response display** — Commands like `!task`, `!kc` now show the actual response instead of raw command text
- **Per-type custom colors** — Customizable color pickers for each message category when "Use Default Colors" is off
- **Font picker** — Full font selection (family, size, bold, italic) via RuneLite's FontType config
- **Respect chat tab filters** — Messages hidden by the game's Filtered/Off tab states are also hidden from Chat Fade
- **Above-chatbox positioning** — When chatbox is open, overlay renders above the chatbox instead of overlapping it
- **Split PM avoidance** — Overlay dynamically repositions above any visible split private chat messages
- **Username colorization** — Player names render in a separate configurable color for readability
- **Dialog awareness** — Game dialogs requiring a response (e.g. High Alchemy warning) pin messages at full opacity and show an "Open chatbox to continue" prompt
- **NPC dialogue formatting** — DIALOG/MESBOX messages split on `|` so NPC name renders separately with its own configurable color (golden yellow by default)
- **Dialog prompt improvements** — Prompt is widget-driven (disappears immediately on dismiss); MESBOX fallback clears the instant the chatbox is opened; never shows while chatbox is open
- **CA_ID prefix stripping** — Combat Achievement clan messages have the `CA_ID:###` prefix stripped at ingest so only the human-readable text is displayed
- **Guest / GIM chat separation** — Clan, Guest Clan and Group Ironman chat have independent show toggles and colours (#19)
- **`@name@` colour macros** — Jagex's older palette syntax (e.g. `@mes_hl_pur@`) is expanded via `Client.macroExpand` at ingest, so it renders in the game's own colours instead of appearing as literal text (#21)
- **Chat Filter integration fixed** — `chatFilterCheck` fires after `ChatMessage`, so blocked messages are now removed retroactively by message id (#11, #22)
- **Per-message ignore lists** — `Ignored Messages` (comma-separated fragments) and `Ignored Regex`, independent of the Chat Filter plugin (#11, #22)
- **PM direction** — Private messages are prefixed `From`/`To` so incoming and outgoing are distinguishable (#20)
- **Inline chat icons** — Emoji, clan/friends chat rank badges, ironman icons and mod crowns render in the overlay. `<img=N>` tags resolve against `client.getModIcons()`; rank badges are not in the message at all and are looked up from the channel
- **Channel names** — Optional `[Clan name]` prefix on clan and friends chat, drawn in the message type's colour
- **Loot value tiers** — Clan drop broadcasts and the player's own "Valuable drop" notifications colour the item and value by GE value, mirroring the Ground Items plugin's tiers (#24)
- **Collection log highlighting** — Clan collection log broadcasts colour the item name (#24)
- **Escaped character handling** — The game escapes printable characters as pseudo-tags (`<at>`, `<lt>`, `<gt>`); these are now restored instead of being stripped along with real markup, so a typed `@` survives
- **CA_ID marker stripping** — The marker is preceded by a rank icon and a space in clan broadcasts; the prefix pattern did not allow that space, so 95% of Combat Achievement messages kept `CA_ID:###|` on screen
- **Message wrapping** — Long messages continue on an indented line instead of being cut off; Max Visible Messages counts lines rather than messages
- **Anchored / draggable overlay** — Optional mode that positions the overlay through RuneLite's overlay system so it can be dragged and other overlays stack around it instead of on top (#25)
- **Chat Filter censor mode** — "Censor Words" rewrites text rather than blocking it, and only on the object stack; the overlay now picks that rewrite up instead of showing the uncensored original
- **Chat direction** — Optional "Newest Message First" mode that reverses the stack so new messages grow downward from a fixed top edge. In anchored mode the newest message then stays put on the box's top edge instead of running off the bottom of the screen (#26)
- **Built-in message filter** — Word, regex and player-name filtering with censor/remove modes, ported from the Chat Filter plugin but applied at ingest. The old "Respect Chat Filter Plugin" option depends on the `chatFilterCheck` callback, which only fires while the chatbox is being rebuilt — not guaranteed when the chatbox is collapsed, which is the state this plugin exists for. Also censors overhead text, which never passes through the chat pipeline (#11)
- **Repeats move to the newest position** — A collapsed line stayed in the slot its first copy took, so a message that had just repeated sat among older ones while its counter climbed. It now moves to where the newest message belongs. Matched by identity rather than equality: `FadingMessage` is a `@Data` class, so removing by equality could pull out a different message with the same field values, leaving the target in the list twice and dropping its twin (#35)
- **Quest name missing from broadcasts** — The game sends some broadcasts as a named template, a definition carrying the value and a reference where it belongs: `<str_quest_name_0=The Lost Tribe>Bob has completed a quest: <str_quest_name_0>`. Both halves look like ordinary markup, so stripping tags took the quest name with them and the message ended at its colon. Templates are now expanded before anything strips tags (#32)
- **Public chat tab states** — The Public tab has four states, not three. Only "Off" was honoured, so setting Public to "Hide" silenced the chatbox while every passing player kept fading in through the overlay. "Hide" now hides them, and "Friends" keeps only friends and your own messages
- **Late-registering chat icons** — An `<img=N>` that could not be resolved was dropped for good, and since the plain text carries no icon either, a message whose body was only an emoji rendered as a bare "name:". Icons now resolve again on later ticks, and the sprite cache is cleared on login instead of only when the array changes length (#33)
- **Overlay reappearing on private messages** — The render gate treated a hidden chat *input* as a collapsed chatbox, but the game hides it momentarily whenever a PM is sent, so the overlay flashed back over an open chat. The chat area decides it now, with a hidden input only counting when Key Remapping is what hid it (#34)
- **Configurable bottom padding** — The gap above the chatbox is now a setting rather than a fixed 4px, defaulting to the previous value (#34)
- **Leading-icon highlight alignment** — `toDisplayText` trims but `parseColorSpans` did not, so every highlight on a broadcast starting with a rank icon landed a character early. Spans now drop that whitespace across as many leading icons as the message carries (#29)
- **Anchored frame jump** — A corner-anchored overlay is positioned from the previous frame's height, so an arriving message drew one frame too low before snapping up; `baseY` now absorbs the growth (#31)
- **Duplicate collapsing** — Repeats fold onto one line with a `(N)` counter, bounded by sender, message type and remaining lifetime. Counted internally rather than from the Chat Filter plugin's config, and the collapsed line deliberately does not re-adopt the MessageNode, which would let the per-tick refresh undo the filter's censoring (#30)
- **Channel-coloured typing line** — The typing overlay is coloured for the channel the message will be sent to, resolved from the message prefix, selected chat tab and sticky chat mode. Reuses the overlay's existing per-channel colours (#28)
- **Hide during dialogue** — Dialogue borrows the chat area while the chatbox itself is away, so by every test the plugin has the chatbox is hidden, and the overlay drew straight over the quest text. Optional gate, off by default, that stands the overlay down while something is using the chat area. Messages still age out behind it rather than piling up for the moment it closes (#38)
- **Message allowlist** — Watched words, regex and names, with an optional "Only Show Matching" mode that hides everything else and a separate display duration for matched messages. Matching is deliberately independent of the gate: with the gate on, every message shown has matched by definition, so a separate duration would otherwise be meaningless (#37)
- **Adjustable line spacing and wrap indent** — Both were hardcoded in the overlay as `LINE_SPACING = 1` and `WRAP_INDENT = 12`; they are now settings defaulting to those values, so an upgrade does not relayout anyone's overlay (#36)
- **NPC overhead speech** — `NPC_SAY` is the line an NPC says over their head that the game also puts in the chatbox. It was not recognised as NPC speech, so the speaker arrived as a literal `Name|text` pipe and the line was coloured as a game message. It now splits and colours like the dialogue box. Its visibility toggle is deliberately left under Show Game Messages rather than NPC Dialogue, since moving it would silence overheads for anyone who keeps dialogue off
- **Fixed-mode auto-expand for input prompts** — The chatbox was not brought back for prompts that take typed input. The decision now consults `MESLAYERMODE`, a client variable, rather than hunting for a widget: bank search, fairy ring search and the "enter amount" prompts all reopen the chatbox. Deliberately not consulted by the dialogue hide gate, where it would hide the overlay for a chatbox that cannot return
- **Fixed-mode auto-expand circularity** — The check consulted `Widget.isHidden()`, which walks the parent chain. The chatbox frame Fixed Mode hides is in that chain, so a dialogue read as hidden *because* the chatbox was hidden, and the chatbox was never put back. Resolved to each widget's own flag, except for the chatbox's own components, which are always self-visible and would otherwise match forever
- **Missing auto-expand interfaces** — `MESSAGEBOX`, `OBJECTBOX_DOUBLE`, `MESSAGEBOX_TITLED` and `MESSAGEBOX_URL` were absent, so click-to-continue boxes left the chatbox away. The list mixed two sets of interface-id constants with identical numbers and different names, which is what hid the gaps; it is now uniformly `gameval.InterfaceID`
- **Emoji taking the message with them** — The Emoji plugin replaces what the player typed with an `<img=N>` tag, and that rewrite reaches the overlay twice: through the chatbox rebuild and through the per-tick node refresh. Both adopted it unconditionally. When the icon was not going to be drawn — chat icons off, so there is no resolver at all, or the icon not registered yet — the tag was stripped and the rewrite was simply the message with a piece missing: "Nice :)" arrived as "Nice", and an emoji on its own arrived as nothing, leaving a bare "Name:" with the only copy of the text discarded. The typed text is now kept instead, and the icon replaces it the moment it can be drawn. Refused only when the rewrite is the old text minus the dropped icon, which is what keeps the Chat Filter plugin working: censoring replaces characters rather than removing them, so it is not contained in the old text and is still adopted (#47)
- **Numbers missing from broadcasts** — The companion to the quest-name bug. The game also ships numeric values as templates, with a format on the reference: `<int_0=17000000>Nefarious has reached <int_0:spacer> XP in Sailing.` Only `<str_*>` was expanded, so every such number was stripped as markup — XP milestones arrived with no XP, and clan drop broadcasts with neither quantity nor price, which also stopped `LootBroadcast` recognising them as drops at all, so they never got a value tier. Both prefixes now expand, and `spacer` groups the digits as the chatbox does. Read strictly left to right, because a name is reused: a drop broadcast sets `int_0` to the quantity, uses it, then sets the same name to the price, so collecting the definitions up front gives both references the second number (#44)
- **Watched word colouring** — Matched words are painted in their own colour inside the message. Unlike every other highlight this one overrides the colour the game chose, because most game-authored lines arrive coloured end to end and deferring to them meant the colour almost never showed. Item highlighting still wins where the two want the same characters: the region it coloured is clipped out of each watched range first, so `Valuable drop:` can go pink while the item keeps its value tier. A drop below the lowest threshold claims nothing, since there is no colour there to protect (#37)
- **One path for building spans** — Four places rebuilt a message's colour spans — ingest, the Chat Filter rewrite, the pending-icon retry, and the per-tick refresh that is where emoji actually arrive — and they had drifted: the retry re-applied item highlighting but not watched words, and the other two re-applied neither. A watched word or a drop broadcast's value tier in a message carrying an emoji therefore lost its colour the moment the icon resolved. All four now go through one method
- **Negative line spacing** — The setting was floored at 0, which is the tightest the font's own leading allows; the chatbox packs its lines tighter than that. The range now goes to -5 so the overlay can match it. Every use is a plain addition to the line height, so a negative value simply shortens the advance between lines (#42)

- **Overlay drawn over an open chatbox while composing a private message** — "Enter message to send to" replaces the chat input, so a hidden input read as a collapsed chatbox and the overlay drew itself over the open chat and its split private messages. The chat area now settles it: a prompt using the chat area means the chatbox is present, whatever the input is doing (#34)
- **Typing line spacing** — The messages and the typing line were positioned from different reference points, and the room for the typing line was reserved twice: once by pushing the stack up a line, again by anchoring the typing line to the bottom. That left an empty gap about a line tall that no setting removed, and made the stack jump the moment you typed. Both now measure from one anchor (#34)
- **Anchored overlay over split private chat** — An anchored overlay could not position itself around split private messages the way the normal path does. It now grows instead: the extra height is empty space at the bottom, and since a bottom-anchored overlay keeps its bottom edge, a taller box lifts the text clear. Does not apply to an overlay the player has dragged, which RuneLite pins by its top-left (#34)

## Known quirks
- **Grand Exchange** — The chatbox is not brought back when the Grand Exchange opens, so its item search types into a chat area that is not on screen. Detection works (the interface is seen, and its search sets an input mode); the reveal is what fails. `TO_CONTRACT_WIDGETS` holds only the bank and seed vault, the large interfaces shrunk to make room for the chatbox, and the Grand Exchange is absent from it — untested, but that is where to start

## Planned — 1.9.8

Sequenced so nothing lands on top of unverified work. Each fix is its own commit, the test
baseline (327 at 1.9.7) only goes up, and new decision logic is extracted as pure statics so
it can be mutation-tested — there is no Mockito in this project.

### First

- **Plugin stops working after a while** — With *Hide During Dialogue* on, the overlay stops
  drawing for good, typically within 5-15 minutes. Reported against emptying a birdhouse and
  taking a spirit tree. `findAutoExpandMatch` tests every dialogue interface with
  `isSelfHidden()` — only group `CHATBOX` gets the parent-aware test — so a widget the game
  dismisses by hiding its *parent* stays self-visible, `countsAsOpen` keeps returning true,
  and `isChatDialogOpen()` latches. Both repros fit: a birdhouse opens `OBJECTBOX`/
  `MESSAGEBOX`, a spirit tree opens `CHATMENU`. Reverting to `isHidden()` is not available —
  that is the circularity with Fixed Mode's hidden chatbox frame that the self-hidden test was
  introduced to solve. Candidate fixes are tracking open interfaces from `WidgetLoaded` /
  `WidgetClosed` (which carries `getGroupId()`, `getModalMode()`, `isUnload()`), or walking the
  parent chain while ignoring the chatbox frame's own flag. Confirm which widget actually
  latches with an INFO probe before choosing. Mitigated by the option defaulting to off (#40)
- **Username in front of trade requests** — The overlay prefixes `senderName + ": "` to any
  message carrying a name, but the game supplies the name separately for `TRADEREQ` and its own
  chatbox composes `X wishes to trade with you.`, so the overlay renders
  `X: wishes to trade with you.` Confirm against the reporter's screenshot before coding (#46)

### Second, and not holding the release

- **GIM custom colour has no effect** — Blocked on one answer: does turning off *Preserve
  In-Game Colors* fix it? That option is on by default, and the per-type mapping is present and
  correct, so the likely cause is the game's own colour markup on GIM lines winning. If so the
  decision is the one already settled for watched words — whether a custom per-type colour
  should override a colour the game chose (#41)
- **Own RSN and account icon on the typing line** — The typing line already shows the RSN; the
  account-type icon is what is missing. The same `modIcons` lookup already resolves ironman
  icons for other players (#45)

### Not planned

- **Built-in enter-to-chat** — Asks for the flashing `>` to be hidden and Enter to toggle
  typing, so Key Remapping is not needed alongside. It reimplements a plugin the reporter
  already runs, which already works with this one, and it lives in the typing-line and input
  code that has caused two regressions here (#43)

## Backlog
- **Chat icons intermittently fail to resolve** — A valid emoji index sometimes resolves to nothing, so the message falls back to showing what was typed (`3:)`) rather than the icon. `ChatIcons.resolve` only drops its cache when `client.getModIcons()` changes *length*, so an array rebuilt at the same size with different contents leaves stale or missing entries behind, which would explain why it is unpredictable about which emoji and when. No longer loses the message now that the typed text is kept, so this is cosmetic rather than urgent. Catching it needs a probe on `ChatIcons.resolve` logging index, array length and cache state on every miss, while the intermittent case happens (#47)
