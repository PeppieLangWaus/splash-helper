package xyz.peppie.splashhelper;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("splashhelper")
public interface SplashHelperConfig extends Config
{
	@ConfigItem(
		keyName = "enableWelcomeMessage",
		name = "Enable Welcome Message",
		description = "Display a welcome message when logging in"
	)
	default boolean enableWelcomeMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "targetNpc",
		name = "Target NPC",
		description = "Select the NPC to track for splashing"
	)
	default TargetNpc targetNpc()
	{
		return TargetNpc.KNIGHT_OF_ARDOUGNE;
	}

	@ConfigItem(
		keyName = "timerDuration",
		name = "Timer Duration (minutes)",
		description = "How many minutes the timer should count down"
	)
	default int timerDuration()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Timer Overlay",
		description = "Display the timer overlay on screen"
	)
	default boolean showOverlay()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "boundaryTileColor",
		name = "Boundary Tile Color",
		description = "Color of the boundary tile marker"
	)
	default Color boundaryTileColor()
	{
		return new Color(255, 0, 0, 100); // Semi-transparent red
	}

	@Alpha
	@ConfigItem(
		keyName = "knightTile1Color",
		name = "Knight Tile 1 Color",
		description = "Color of the Knight Tile 1 marker"
	)
	default Color knightTile1Color()
	{
		return new Color(0, 255, 0, 255); // Green
	}

	@Alpha
	@ConfigItem(
		keyName = "knightTile2Color",
		name = "Knight Tile 2 Color",
		description = "Color of the Knight Tile 2 marker"
	)
	default Color knightTile2Color()
	{
		return new Color(0, 0, 255, 255); // Blue
	}
}
