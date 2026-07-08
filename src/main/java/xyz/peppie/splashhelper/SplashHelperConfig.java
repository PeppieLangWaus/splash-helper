package xyz.peppie.splashhelper;

import xyz.peppie.splashhelper.model.TargetNpc;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.model.OverallStatField;
import xyz.peppie.splashhelper.model.SessionStatField;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.ModifierlessKeybind;
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

	@Range(min = 1, max = 30)
	@ConfigItem(
		keyName = "notificationDuration",
		name = "Notification Duration",
		description = "How long notifications are displayed (in seconds)",
		section = notificationSection
	)
	default int notificationDuration()
	{
		return 5;
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

	@Range(min = 0, max = 600)
	@ConfigItem(
		keyName = "minimumSessionDuration",
		name = "Min Session Duration (seconds)",
		description = "Sessions shorter than this duration will be discarded",
		section = statisticsSection
	)
	default int minimumSessionDuration()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "enableGriefPrevention",
		name = "Enable Grief Prevention",
		description = "Show warning overlay if Accept Aid is on or Private Chat is set to All",
		section = statisticsSection
	)
	default boolean enableGriefPrevention()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoDetectSpell",
		name = "Auto-detect Spell",
		description = "Automatically detect the spell being cast based on XP gained. Disable to use manual selection below.",
		section = statisticsSection,
		position = 10
	)
	default boolean autoDetectSpell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "selectedSpell",
		name = "Splash Spell",
		description = "Select the spell you are using for splashing (only used when auto-detect is disabled)",
		section = statisticsSection,
		position = 11
	)
	default SplashSpell selectedSpell()
	{
		return SplashSpell.FIRE_STRIKE;
	}

	// ==================== Overall Statistics Panel ====================

	@ConfigSection(
		name = "Overall Statistics Panel",
		description = "Configure which fields to show in the overall statistics panel",
		position = 30,
		closedByDefault = true
	)
	String overallStatsSection = "overallStats";

	@ConfigItem(
		keyName = "showOverallStats",
		name = "Show Overall Statistics",
		description = "Enable the overall statistics panel",
		section = overallStatsSection,
		position = 0
	)
	default boolean showOverallStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overallStatFields",
		name = "Visible Fields",
		description = "Select which fields to display in the overall statistics panel",
		section = overallStatsSection,
		position = 1
	)
	default Set<OverallStatField> overallStatFields()
	{
		return EnumSet.allOf(OverallStatField.class);
	}

	// ==================== Current Session Panel ====================

	@ConfigSection(
		name = "Current Session Panel",
		description = "Configure which fields to show in the current session panel",
		position = 31,
		closedByDefault = true
	)
	String currentSessionSection = "currentSession";

	@ConfigItem(
		keyName = "showCurrentSession",
		name = "Show Current Session",
		description = "Enable the current session panel",
		section = currentSessionSection,
		position = 0
	)
	default boolean showCurrentSession()
	{
		return true;
	}

	@ConfigItem(
		keyName = "currentSessionFields",
		name = "Visible Fields",
		description = "Select which fields to display in the current session panel",
		section = currentSessionSection,
		position = 1
	)
	default Set<SessionStatField> currentSessionFields()
	{
		return EnumSet.allOf(SessionStatField.class);
	}

	// ==================== Session History Panel ====================

	@ConfigSection(
		name = "Session History Panel",
		description = "Configure which fields to show in the session history panel",
		position = 32,
		closedByDefault = true
	)
	String sessionHistorySection = "sessionHistory";

	@ConfigItem(
		keyName = "showSessionHistory",
		name = "Show Session History",
		description = "Enable the session history panel",
		section = sessionHistorySection,
		position = 0
	)
	default boolean showSessionHistory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sessionHistoryFields",
		name = "Visible Fields",
		description = "Select which fields to display in the session history panel",
		section = sessionHistorySection,
		position = 1
	)
	default Set<SessionStatField> sessionHistoryFields()
	{
		return EnumSet.allOf(SessionStatField.class);
	}

	@Range(min = 0, max = 3600)
	@ConfigItem(
		keyName = "sessionResumeWindowSeconds",
		name = "Session Resume Window (seconds)",
		description = "How long after a session ends it can be resumed when the same player returns (e.g. after using dragon spear or the 6-hour logout). Set to 0 to disable.",
		section = statisticsSection,
		position = 12
	)
	default int sessionResumeWindowSeconds()
	{
		return 600; // 10 minutes
	}

	@ConfigItem(
		keyName = "safetyModeEnabled",
		name = "Safety Mode",
		description = "Enable safety mode to prevent actions that lose knight aggro"
	)
	default boolean safetyModeEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "safetyModeHotkey",
		name = "Safety Mode Hotkey",
		description = "Hotkey to toggle safety mode (default: Numlock *)"
	)
	default ModifierlessKeybind safetyModeHotkey()
	{
		return new ModifierlessKeybind(KeyEvent.VK_MULTIPLY, 0);
	}

	// ==================== Server Sync ====================

	@ConfigSection(
		name = "Server Sync",
		description = "Sync live session data with a Splash Helper server via WebSocket",
		position = 40,
		closedByDefault = true
	)
	String serverSyncSection = "serverSync";

	@ConfigItem(
		keyName = "enableServerSync",
		name = "Enable Server Sync",
		description = "Connect to a Splash Helper server and stream session data in real time",
		section = serverSyncSection,
		position = 0
	)
	default boolean enableServerSync()
	{
		return true;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "WebSocket URL of the Splash Helper server",
		section = serverSyncSection,
		position = 1
	)
	default String serverUrl()
	{
		return "wss://api.splasher.help";
	}

	// ==================== Sticky Knight Guide ====================

	@ConfigSection(
		name = "Sticky Knight Guide",
		description = "Step-by-step guide for setting up a sticky knight",
		position = 50,
		closedByDefault = true
	)
	String guideSection = "guide";

	@ConfigItem(
		keyName = "guideStartHotkey",
		name = "Start/Stop Guide",
		description = "Hotkey to start or stop the sticky knight setup guide",
		section = guideSection,
		position = 0
	)
	default Keybind guideStartHotkey()
	{
		return new Keybind(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "guideNextHotkey",
		name = "Next / Skip Step",
		description = "Hotkey to acknowledge the requirements screen, or skip the current step",
		section = guideSection,
		position = 1
	)
	default Keybind guideNextHotkey()
	{
		return new Keybind(KeyEvent.VK_PERIOD, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "guideBackHotkey",
		name = "Previous Step",
		description = "Hotkey to go back one step",
		section = guideSection,
		position = 2
	)
	default Keybind guideBackHotkey()
	{
		return new Keybind(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK);
	}

	@Range(min = 3, max = 120)
	@ConfigItem(
		keyName = "guideStepTimeoutSeconds",
		name = "Stuck Hint Delay (seconds)",
		description = "How long on a step before showing the 'you can go back' hint",
		section = guideSection,
		position = 3
	)
	default int guideStepTimeoutSeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "requirementsAcknowledged",
		name = "Requirements Acknowledged",
		description = "Whether the setup requirements have been acknowledged. Uncheck to show the requirements screen again on the next start.",
		section = guideSection,
		position = 4
	)
	default boolean requirementsAcknowledged()
	{
		return false;
	}

	@ConfigItem(
		keyName = "requirementsAcknowledged",
		name = "",
		description = ""
	)
	void setRequirementsAcknowledged(boolean value);
}
