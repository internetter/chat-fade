# Chat Fade - TODO

## Completed
- **Typing input overlay** — Show typed text with blinking `>` when chatbox is collapsed
- **Command response display** — Commands like `!task`, `!kc` now show the actual response instead of raw command text
- **Per-type custom colors** — Customizable color pickers for each message category when "Use Default Colors" is off
- **Font picker** — Full font selection (family, size, bold, italic) via RuneLite's FontType config
- **Respect chat tab filters** — Messages hidden by the game's Filtered/Off tab states are also hidden from Chat Fade
- **Above-chatbox positioning** — When chatbox is open, overlay renders above the chatbox instead of overlapping it

## Potential Features
- **Emoji rendering** — Other plugins (e.g., Emoji plugin) render emoji shortcodes like `:rat:`, `:boy:` as actual images in chat. Chat Fade currently shows the raw shortcode text instead. Investigate whether we can hook into the emoji plugin's image rendering or parse the shortcodes ourselves to display the actual emoji sprites in the overlay.
