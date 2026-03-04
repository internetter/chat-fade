# Chat Fade - TODO

## Completed
- **Typing input overlay** — Show typed text with blinking `>` when chatbox is collapsed
- **Command response display** — Commands like `!task`, `!kc` now show the actual response instead of raw command text
- **Per-type custom colors** — Customizable color pickers for each message category when "Use Default Colors" is off

### 3. Emoji rendering
Other plugins (e.g., Emoji plugin) render emoji shortcodes like `:rat:`, `:boy:` as actual images in chat. Chat Fade currently shows the raw shortcode text instead. Investigate whether we can hook into the emoji plugin's image rendering or parse the shortcodes ourselves to display the actual emoji sprites in the overlay.
