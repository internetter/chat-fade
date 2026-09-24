# Chat Fade

A RuneLite plugin that displays chat messages as floating, fading text above the chatbox when it's collapsed — so you never miss important messages while keeping your screen clean. Built in compatibility to hide chat when playing on fixed mode.

## Features

- Floating text overlay that fades out after a configurable duration
- Works when the chatbox is collapsed (or always, if preferred)
- **Typing input overlay** — see what you're typing with a blinking `>` cursor when the chatbox is collapsed
- **Command response display** — `!task`, `!kc`, and other commands show their actual response instead of raw command text
- Per-message-type color coding (game, public, private, clan, friends chat, etc.)
- **Username colorization** — player names render in a separate configurable color for easier readability
- **NPC dialogue formatting** — NPC names split from dialogue text and rendered in their own configurable color
- **Custom colors** — override default colors with per-type color pickers
- **Font picker** — choose any font (RuneScape, system fonts), with size, bold, and italic options
- **Respects chat tab filters** — messages hidden by the game's Filtered/Off tab states are also hidden from Chat Fade
- **Above-chatbox positioning** — when chatbox is open, messages appear above it instead of overlapping
- **Avoids split private chat** — overlay repositions above any split PM messages currently on screen
- **Dialog awareness** — when a game dialog requires a response (e.g. High Alchemy warning), messages pin at full opacity and a prompt appears to open the chatbox
- **Message wrapping** — long messages continue on an indented line instead of being cut off
- **Anchored / draggable overlay** — optionally position the overlay through RuneLite's overlay system so it can be dragged and other overlays stack around it
- **Inline chat icons** — emoji, clan and friends chat rank badges, ironman icons and mod crowns render in the overlay instead of being dropped
- **Channel names** — optionally prefix clan and friends chat with the channel, e.g. `[Valence] Bob: hi`
- **Loot value tiers** — clan drop broadcasts and your own "Valuable drop" notifications colour the item and value by GE value, like the Ground Items plugin
- **Collection log highlighting** — clan collection log broadcasts colour the item name
- **PM direction** — private messages are prefixed `From`/`To` so incoming and outgoing are distinguishable
- **In-game colour preservation** — messages keep the colours the game gave them, including the older `@name@` palette syntax
- **Chat Filter integration** — messages the Chat Filter plugin blocks or censors are blocked or censored here too
- **Per-message ignore lists** — hide messages by text fragment or regular expression, independently of any other plugin
- Configurable display duration, fade speed, and max width
- Filter which message types are shown

## Configuration

| Setting | Description | Default |
|---|---|---|
| Display Duration | How long a message stays at full opacity | 3s |
| Fade Duration | How long the fade-out animation takes | 2s |
| Max Visible Messages | Maximum lines shown at once (a wrapped message costs two) | 8 |
| Font | Font family, size, bold, italic | RuneScape Small |
| Max Message Width | Maximum width before truncation | 500px |
| Colorize Usernames | Show player names in a separate color | On |
| Username Color | Color used for player names | White |
| Colorize NPC Names | Show NPC names in a separate color | On |
| NPC Name Color | Color used for NPC names | Golden yellow |
| Wrap Long Messages | Continue long messages on an indented line | On |
| Anchored / Draggable Overlay | Position via RuneLite's overlay system; draggable | Off |
| Draw Behind Interfaces | Let the bank and similar cover the overlay | On |
| Show Chat Icons | Draw emoji, rank badges, crowns and ironman icons | On |
| Show Channel Name | Prefix clan/friends chat with the channel in brackets | Off |
| Preserve In-Game Colors | Keep the colours the game gave a message | On |
| Use Default Colors | Distinct color per message type | On |
| Only When Chatbox Collapsed | Only show overlay when chatbox is hidden | On |
| Show Typing Input | Show typed text overlay when chatbox is collapsed | On |
| Show PM Direction | Prefix private messages with From/To | On |
| Respect Chat Filter Plugin | Hide messages the Chat Filter plugin removed | On |
| Ignored Messages | Comma-separated text fragments to hide | — |
| Ignored Regex | One regular expression per line to hide | — |
| Colour Loot Drops By Value | Tier drop broadcasts by GE value | On |
| Highlight Collection Log Items | Colour the item name in collection log broadcasts | On |

**Custom Colors** — When "Use Default Colors" is off, per-type color pickers are available for: Game Messages, Notifications, Public Chat, Private Messages, Clan Chat, Friends Chat, Trade, Broadcast, Examine, and NPC Dialogue.

**Item Highlighting** — Loot drops are coloured by value using four configurable tiers, defaulting to 20k / 500k / 5m / 50m. Collection log items use their own colour.

**Message type filters** (Game, Public, Private, Clan, Guest Clan, Group Ironman, Friends, Trade, Examine, Broadcast, NPC Dialogue) can be individually toggled in the config panel.

## Installation

Available on the [RuneLite Plugin Hub](https://runelite.net/plugin-hub/). Search for **Chat Fade** in the Plugin Hub panel within RuneLite.
