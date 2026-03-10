# Chat Fade

A RuneLite plugin that displays chat messages as floating, fading text above the chatbox when it's collapsed — so you never miss important messages while keeping your screen clean. For fixed mode players, this plugin is designed to work best with a separate plugin: Fixed Mode Hide Chat.

## Features

- Floating text overlay that fades out after a configurable duration
- Works when the chatbox is collapsed (or always, if preferred)
- **Typing input overlay** — see what you're typing with a blinking `>` cursor when the chatbox is collapsed
- **Command response display** — `!task`, `!kc`, and other commands show their actual response instead of raw command text
- Per-message-type color coding (game, public, private, clan, friends chat, etc.)
- **Username colorization** — player names render in a separate configurable color for easier readability
- **Custom colors** — override default colors with per-type color pickers
- **Font picker** — choose any font (RuneScape, system fonts), with size, bold, and italic options
- **Respects chat tab filters** — messages hidden by the game's Filtered/Off tab states are also hidden from Chat Fade
- **Above-chatbox positioning** — when chatbox is open, messages appear above it instead of overlapping
- **Avoids split private chat** — overlay repositions above any split PM messages currently on screen
- **Dialog awareness** — when a game dialog requires a response (e.g. High Alchemy warning), messages pin at full opacity and a prompt appears to open the chatbox
- Configurable display duration, fade speed, and max width
- Filter which message types are shown

## Configuration

| Setting | Description | Default |
|---|---|---|
| Display Duration | How long a message stays at full opacity | 3s |
| Fade Duration | How long the fade-out animation takes | 2s |
| Max Visible Messages | Maximum messages shown at once | 8 |
| Font | Font family, size, bold, italic | RuneScape Small |
| Max Message Width | Maximum width before truncation | 500px |
| Colorize Usernames | Show player names in a separate color | On |
| Username Color | Color used for player names | White |
| Use Default Colors | Distinct color per message type | On |
| Only When Chatbox Collapsed | Only show overlay when chatbox is hidden | On |
| Show Typing Input | Show typed text overlay when chatbox is collapsed | On |

**Custom Colors** — When "Use Default Colors" is off, per-type color pickers are available for: Game Messages, Notifications, Public Chat, Private Messages, Clan Chat, Friends Chat, Trade, Broadcast, and Examine.

**Message type filters** (Game, Public, Private, Clan, Friends, Trade, Examine, Broadcast) can be individually toggled in the config panel.

## Installation

Available on the [RuneLite Plugin Hub](https://runelite.net/plugin-hub/). Search for **Chat Fade** in the Plugin Hub panel within RuneLite.
