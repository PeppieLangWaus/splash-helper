package xyz.peppie.splashhelper;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

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

	// ==================== Notification Settings ====================

	@ConfigSection(
		name = "Notifications",
		description = "Configure notification behavior",
		position = 10
	)
	String notificationSection = "notifications";

	@ConfigItem(
		keyName = "enableBoundaryNotification",
		name = "Boundary Notification",
		description = "Notify when knight reaches boundary tile",
		section = notificationSection
	)
	default boolean enableBoundaryNotification()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableTimerNotification",
		name = "Timer Notification",
		description = "Notify when splash timer expires",
		section = notificationSection
	)
	default boolean enableTimerNotification()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableHpNotification",
		name = "HP Threshold Notification",
		description = "Notify when HP drops below threshold (only when knight can attack)",
		section = notificationSection
	)
	default boolean enableHpNotification()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(
		keyName = "hpThreshold",
		name = "HP Threshold",
		description = "HP level at which to notify (when knight can attack)",
		section = notificationSection
	)
	default int hpThreshold()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "useVisualNotification",
		name = "Visual Notification",
		description = "Use screen tint instead of sound for notifications",
		section = notificationSection
	)
	default boolean useVisualNotification()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "visualNotificationColor",
		name = "Visual Notification Color",
		description = "Color of the screen tint for visual notifications",
		section = notificationSection
	)
	default Color visualNotificationColor()
	{
		return new Color(255, 0, 0, 80); // Semi-transparent red
	}

	// ==================== Statistics Settings ====================

	@ConfigSection(
		name = "Statistics",
		description = "Configure statistics tracking",
		position = 20
	)
	String statisticsSection = "statistics";

	@ConfigItem(
		keyName = "enableStatistics",
		name = "Enable Statistics",
		description = "Track splashing session statistics",
		section = statisticsSection
	)
	default boolean enableStatistics()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "statisticsInterval",
		name = "Tracking Interval (seconds)",
		description = "How often to sample statistics data",
		section = statisticsSection
	)
	default int statisticsInterval()
	{
		return 3;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "playerCountRadius",
		name = "Player Count Radius",
		description = "Tile radius for counting nearby players",
		section = statisticsSection
	)
	default int playerCountRadius()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "selectedSpell",
		name = "Splash Spell",
		description = "Select the spell you are using for splashing",
		section = statisticsSection
	)
	default SplashSpell selectedSpell()
	{
		return SplashSpell.FIRE_STRIKE;
	}
}
