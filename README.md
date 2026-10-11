# Splash Helper

A RuneLite plugin designed to assist with splashing, mostly focused on Ardy Knights.

## Features

### Combat Idle Timer
- Configurable countdown timer that starts when you click on the configured NPC
- Audio and visual notifications when the timer expires
- Customizable duration (default: 10 minutes)

### Session Statistics & History
- **Real-time session tracking** - Monitor casts, XP gained, rune costs, and more
- **Session history** - Automatically saves and displays past splashing sessions
- **Session deletion** - Right-click any session entry to delete unwanted sessions
- **Detailed statistics** - Track player counts, knight movements, pickpocketers, and nearby player deaths
- **Persistent storage** - Session history is saved between plugin restarts (oldest sessions pruned once the configured limit is reached)
- **Session resume** - A brief countdown lets a session resume automatically if you log back in or hop shortly after it ends, instead of starting a new one

### Tile Markers
Three types of customizable tile markers:

1. **Knight Boundary** - Mark a boundary tile to receive notifications when an NPC reaches it
2. **Knight Tile** - The tile the knight normally stands on
3. **Stack Tile** - The tile the player stack stands on

### Movement Tracking
When both **Knight Tile** and **Stack Tile** are set, the plugin automatically:
- Tracks NPC movements between the two tiles
- Calculates movements per minute
- Displays real-time statistics in the overlay

### Safety Features
- **Safety mode hotkey** - Toggle safety mode to prevent accidental interactions
- **Magic bonus warnings** - Get notified when magic bonus equipment is missing

## Usage

### Session Statistics & History

1. **Viewing Session Statistics**
   - Open the Splash Statistics panel from the RuneLite sidebar
   - View real-time stats for active session (casts, XP, costs, etc.)
   - Browse session history with detailed information for each session

2. **Managing Session History**
   - **Right-click** any session entry to open context menu
   - Select **"Delete Session"** to remove unwanted sessions
   - Confirm deletion in the dialog (shows session details)
   - Sessions are removed from both UI and persistent storage

3. **Configuring Session Display**
   - Use plugin settings to choose which statistics to display
   - Options include: spell, casts, XP gained, XP/hour, rune cost, player counts, deaths
   - Toggle session history panel visibility

### Setting Up the Timer

1. **Configure NPC Name**
   - Open plugin settings
   - Set "NPC Name" to the NPC you want to track (e.g., "Knight of Ardougne")
   - The plugin automatically cleans names (removes color tags and level indicators)

2. **Start the Timer**
   - Simply attack or interact with the configured NPC
   - The timer will automatically start and display in the overlay
   - You'll receive a notification when the timer expires

### Setting Up Tile Markers

Right-click any ground tile to access the tile marker menus:

**Knight Boundary:**
- Right-click tile → Knight Boundary → Set
- Receive notification when NPC reaches this tile
- Change color: Knight Boundary → Color → Open plugin settings

**Knight Tile & Stack Tile:**
- Right-click tile → Knight Tile (or Stack Tile) → Set
- When both are set, movement tracking begins automatically
- Movements per hour displayed in overlay

### Reading the Overlay

The overlay displays (when visible):
- **Timer**: Remaining time in MM:SS format
  - Green: above the warning threshold
  - Orange: at or below the warning threshold (default: 5 minutes)
  - Red: at or below the critical threshold (default: 2 minutes), or expired
- **Boundary**: Shows "SET" when boundary tile is configured
- **Movements**: Shows X.X/hr when both the Knight Tile and Stack Tile are set
  - Cyan: No movements detected yet
  - Green: Movement tracking active

When **Visual Notification** is enabled, the full screen tint follows the same thresholds: orange at the warning threshold, red at the critical threshold and on notification (timer expiry).

## Configuration Options

### Timer & NPC Settings
| Setting | Description | Default |
|---------|-------------|---------|
| **Enable Welcome Message** | Display a welcome message when logging in | Enabled |
| **NPC Name** | Name of the NPC to track (e.g., "Knight of Ardougne", "Rat") | "Rat" |
| **Timer Duration** | Timer countdown duration in minutes | 13 |
| **Show Timer Overlay** | Display the timer overlay on screen | Enabled |
| **Warning Threshold (minutes)** | Timer text and screen tint turn orange when this many minutes remain (clamped to the timer duration) | 5 |
| **Critical Threshold (minutes)** | Timer text and screen tint turn red when this many minutes remain (clamped to the warning threshold) | 2 |

### Session Statistics Settings
| Setting | Description | Default |
|---------|-------------|---------|
| **Show Session History** | Display session history panel in statistics | Enabled |
| **Session History Fields** | Choose which statistics to display in session entries | All fields enabled |
| **Max Player Count Samples** | Maximum number of player count samples to store | 100 |
| **Sessions Kept On Disk** | Maximum number of finished sessions kept in local history; older sessions are pruned (Server Sync retains full history) | 200 |

### Safety & Warning Settings
| Setting | Description | Default |
|---------|-------------|---------|
| **Safety Mode Hotkey** | Key combination to toggle safety mode | Not set |
| **Safety Mode Enabled** | Whether safety mode is currently active | Disabled |
| **Show Magic Bonus Warning** | Display warning when magic bonus equipment is missing | Enabled |

### Tile Marker Colors
| Setting | Description | Default |
|---------|-------------|---------|
| **Boundary Tile Color** | Color of the boundary tile marker (with alpha) | Red (semi-transparent) |
| **Knight Tile Color** | Color of the Knight Tile marker | Green |
| **Stack Tile Color** | Color of the Stack Tile marker | Blue |

All color settings support transparency via the alpha channel.

### Stack Tracking

**Disabled by default.** A data-collection feature for investigating how the knight escapes the
pickpocket stack: diffs who's standing on the Stack Tile every tick, tracks how long the
blocking flag stays "clear" between a player leaving and the next player entering, and tracks
the knight's excursions away from the Knight Tile. Enable it under the "Stack Tracking" settings
section, then:
- CSV files are written to `.runelite/splashhelper/knightstack/` (events, windows, excursions)
- Optionally enable "Log Raw Events" for a per-tick ENTER/LEAVE/DESPAWN CSV
- Enable "Show Summary In Overlay" to see flag-clear and knight-move rates live in the timer overlay
- Set "Blocking Re-setter Username" to a player's name (e.g. an alt) to tag flag-clear windows
  where that player was on the Stack Tile, for comparing windows with/without them attempting to block

### Server Sync

**Disabled by default.** Enabling Server Sync connects to a third-party Splash Helper server (splasher.help by default) and streams live session data, including your account username and session statistics — spell used, world, magic XP, rune usage and cost, spells cast, knight movements, nearby player counts, nearby player deaths, and timestamps. No data is sent while this setting is disabled.

## Author

PeppieLangwaus