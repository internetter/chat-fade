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
- **Guest / GIM chat separation** — Clan, Guest Clan and Group Ironman chat have independent show toggles and colours (#19, thanks @TyboJones24)
- **`@name@` colour tokens** — Jagex's older palette syntax (e.g. `@mes_hl_pur@`) is parsed for colour instead of rendering as literal text; unknown tokens are stripped rather than shown raw (#21)
- **Chat Filter integration fixed** — `chatFilterCheck` fires after `ChatMessage`, so blocked messages are now removed retroactively by message id (#11, #22, thanks @jarredgoddard)
- **Per-message ignore lists** — `Ignored Messages` (comma-separated fragments) and `Ignored Regex`, independent of the Chat Filter plugin (#11, #22)
- **PM direction** — Private messages are prefixed `From`/`To` so incoming and outgoing are distinguishable (#20)

## Needs Verification
- **`@mes_hl_*@` shades** — Hues are taken from the token suffix and are correct; the exact RGB values in `ColorTokens` are approximations. Sample the real colours in-game (Death Charge, thralls, freeze messages) and correct them.

## Potential Features
- **Emoji rendering** — Other plugins (e.g., Emoji plugin) render emoji shortcodes like `:rat:`, `:boy:` as actual images in chat. Chat Fade currently shows the raw shortcode text instead. Investigate whether we can hook into the emoji plugin's image rendering or parse the shortcodes ourselves to display the actual emoji sprites in the overlay.
