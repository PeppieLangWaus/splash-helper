# Splash Helper

A RuneLite plugin designed to assist with splashing, mostly focused on Ardy Knights.

## Features

### Combat Idle Timer
- Configurable countdown timer that starts when you click on the configured NPC
- Audio and visual notifications when the timer expires
- Customizable duration (default: 10 minutes)

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

## Usage

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

| Setting | Description | Default |
|---------|-------------|---------|
| **Enable Welcome Message** | Display a welcome message when logging in | Enabled |
| **NPC Name** | Name of the NPC to track (e.g., "Knight of Ardougne", "Rat") | "Rat" |
| **Timer Duration** | Timer countdown duration in minutes | 13 |
| **Show Timer Overlay** | Display the timer overlay on screen | Enabled |
| **Boundary Tile Color** | Color of the boundary tile marker (with alpha) | Red (semi-transparent) |
| **Knight Tile 1 Color** | Color of the first movement tracking tile | Green |
| **Knight Tile 2 Color** | Color of the second movement tracking tile | Blue |

All color settings support transparency via the alpha channel.

## Author

PeppieLangwaus