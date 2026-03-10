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

## Potential Features
- **Emoji rendering** — Other plugins (e.g., Emoji plugin) render emoji shortcodes like `:rat:`, `:boy:` as actual images in chat. Chat Fade currently shows the raw shortcode text instead. Investigate whether we can hook into the emoji plugin's image rendering or parse the shortcodes ourselves to display the actual emoji sprites in the overlay.
