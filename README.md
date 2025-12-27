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
- **Detailed statistics** - Track player counts, knight movements, and pickpocketers
- **Persistent storage** - Session history is saved between plugin restarts

### Tile Markers
Three types of customizable tile markers:

1. **Knight Boundary** - Mark a boundary tile to receive notifications when an NPC reaches it
2. **Knight Tile 1** - First movement tracking tile
3. **Knight Tile 2** - Second movement tracking tile

### Movement Tracking
When both **Knight Tile 1** and **Knight Tile 2** are set, the plugin automatically:
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
   - Options include: spell, casts, XP gained, XP/hour, rune cost, player counts
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

**Knight Tile 1 & 2:**
- Right-click tile → Knight Tile 1 (or 2) → Set
- When both are set, movement tracking begins automatically
- Movements per minute displayed in overlay

### Reading the Overlay

The overlay displays (when visible):
- **Timer**: Remaining time in MM:SS format
  - Green: > 1 minute remaining
  - Yellow: < 1 minute remaining
  - Red: < 30 seconds remaining
- **Boundary**: Shows "SET" when boundary tile is configured
- **Movements**: Shows X.X/min when both Knight Tiles are set
  - Cyan: No movements detected yet
  - Green: Movement tracking active

## Configuration Options

### Timer & NPC Settings
| Setting | Description | Default |
|---------|-------------|---------|
| **Enable Welcome Message** | Display a welcome message when logging in | Enabled |
| **NPC Name** | Name of the NPC to track (e.g., "Knight of Ardougne", "Rat") | "Rat" |
| **Timer Duration** | Timer countdown duration in minutes | 13 |
| **Show Timer Overlay** | Display the timer overlay on screen | Enabled |

### Session Statistics Settings
| Setting | Description | Default |
|---------|-------------|---------|
| **Show Session History** | Display session history panel in statistics | Enabled |
| **Session History Fields** | Choose which statistics to display in session entries | All fields enabled |
| **Max Player Count Samples** | Maximum number of player count samples to store | 100 |

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
| **Knight Tile 1 Color** | Color of the first movement tracking tile | Green |
| **Knight Tile 2 Color** | Color of the second movement tracking tile | Blue |

All color settings support transparency via the alpha channel.

## Author

PeppieLangwaus