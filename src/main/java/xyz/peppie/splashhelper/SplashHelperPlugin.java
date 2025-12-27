package xyz.peppie.splashhelper;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.config.ModifierlessKeybind;
import java.awt.event.KeyEvent;

import xyz.peppie.splashhelper.overlays.BoundaryTileOverlay;
import xyz.peppie.splashhelper.overlays.GriefPreventionOverlay;
import xyz.peppie.splashhelper.overlays.MagicBonusWarningOverlay;
import xyz.peppie.splashhelper.overlays.SafetyModeOverlay;
import xyz.peppie.splashhelper.overlays.SplashHelperOverlay;
import xyz.peppie.splashhelper.overlays.VisualNotificationOverlay;
import xyz.peppie.splashhelper.model.SplashSession;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.service.KnightDetector;
import xyz.peppie.splashhelper.service.NotificationService;
import xyz.peppie.splashhelper.service.PlayerTracker;
import xyz.peppie.splashhelper.service.RuneCalculator;
import xyz.peppie.splashhelper.service.SessionManager;
import xyz.peppie.splashhelper.service.TileManager;
import xyz.peppie.splashhelper.ui.SplashStatisticsPanel;
import xyz.peppie.splashhelper.util.Constants;

@Slf4j
@PluginDescriptor(
	name = "Splash Helper",
	description = "A helper plugin for splashing in Old School RuneScape",
	tags = {"combat", "magic", "splashing"}
)
public class SplashHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private SplashHelperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SplashHelperOverlay overlay;

	@Inject
	private BoundaryTileOverlay boundaryOverlay;

	@Inject
	private VisualNotificationOverlay visualNotificationOverlay;

	@Inject
	private SafetyModeOverlay safetyModeOverlay;

	@Inject
	private GriefPreventionOverlay griefPreventionOverlay;

	@Inject
	private MagicBonusWarningOverlay magicBonusWarningOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	// Services
	@Inject
	private RuneCalculator runeCalculator;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private KnightDetector knightDetector;

	@Inject
	private PlayerTracker playerTracker;

	@Inject
	private NotificationService notificationService;

	@Inject
	private TileManager tileManager;

	@Inject
	private KeyManager keyManager;
	
	@Inject
	private ClientThread clientThread;

	// Statistics panel
	private SplashStatisticsPanel statisticsPanel;
	private NavigationButton navButton;

	@Getter
	private Instant timerEnd;

	@Getter
	private boolean safetyModeEnabled = false;

	private boolean hasNotified = false;
	
	// Track previous game state for welcome message
	private GameState previousGameState = null;

	// Session tracking delegated to SessionManager service
	private Instant lastStatsSample = null;
	
	// Magic attack bonus tracking
	@Getter
	private boolean hasBadMagicBonus = false;
	private Instant lastBonusWarning = null;

	// Session delegation methods
	public SplashSession getCurrentSession()
	{
		return sessionManager.getCurrentSession();
	}

	/**
	 * Get the session to display in UI - current if active, otherwise last finalized.
	 */
	public SplashSession getDisplayableSession()
	{
		return sessionManager.getDisplayableSession();
	}

	public List<SplashSession> getSessionHistory()
	{
		return sessionManager.getSessionHistory();
	}

	// Tile delegation methods - forward to TileManager
	public WorldPoint getBoundaryTile()
	{
		return tileManager.getBoundaryTile();
	}

	public WorldPoint getKnightTile1()
	{
		return tileManager.getKnightTile1();
	}

	public WorldPoint getKnightTile2()
	{
		return tileManager.getKnightTile2();
	}

	public boolean isHasEscaped()
	{
		return tileManager.isHasEscaped();
	}

	public double getMovementsPerMinute()
	{
		return tileManager.getMovementsPerMinute();
	}

	public Actor getCurrentTarget()
	{
		return tileManager.getCurrentTarget();
	}

	/**
	 * Trigger visual notification overlay.
	 * Called by NotificationService when visual notifications are enabled.
	 */
	public void triggerVisualNotification()
	{
		if (config != null)
		{
			showVisualNotification = true;
			visualNotificationEnd = Instant.now().plusSeconds(config.notificationDuration());
		}
	}

	// Cached values for UI (updated on client thread)
	@Getter
	private volatile int cachedRemainingCasts = 0;
	@Getter
	private volatile java.util.Set<Integer> cachedInfiniteRunes = new java.util.HashSet<>();
	@Getter
	private volatile java.util.List<int[]> cachedActualRuneUsage = new java.util.ArrayList<>();
	@Getter
	private volatile long cachedRuneCost = 0;

	// Player tracking delegated to PlayerTracker service
	public int getCurrentPickpocketerCount()
	{
		SplashSession session = sessionManager.getCurrentSession();
		return session != null ? session.getPickpocketerCount() : 0;
	}

	// Visual notification state
	@Getter
	private boolean showVisualNotification = false;
	private Instant visualNotificationEnd = null;

	@Provides
	SplashHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SplashHelperConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Splash Helper started!");
		overlay.setPlugin(this, tileManager);
		overlayManager.add(overlay);
		
		// Set up visual notification callback
		notificationService.setVisualNotificationCallback(this::triggerVisualNotification);
		overlayManager.add(boundaryOverlay);
		overlayManager.add(visualNotificationOverlay);
		overlayManager.add(griefPreventionOverlay);
		overlayManager.add(magicBonusWarningOverlay);
		overlayManager.add(safetyModeOverlay);

		// Register key listener for safety mode
		keyManager.registerKeyListener(safetyModeKeyListener);
		
		// Load safety mode state from config
		safetyModeEnabled = config.safetyModeEnabled();

		// Create statistics panel
		statisticsPanel = new SplashStatisticsPanel(this, config, itemManager, sessionManager);
		
		final BufferedImage icon = ImageUtil.loadImageResource(SplashHelperPlugin.class, "icon.png");
		
		navButton = NavigationButton.builder()
			.tooltip("Splash Statistics")
			.icon(icon)
			.priority(10)
			.panel(statisticsPanel)
			.build();
		
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Splash Helper stopped!");
		overlayManager.remove(overlay);
		overlayManager.remove(boundaryOverlay);
		overlayManager.remove(visualNotificationOverlay);
		overlayManager.remove(griefPreventionOverlay);
		overlayManager.remove(magicBonusWarningOverlay);
		overlayManager.remove(safetyModeOverlay);

		// Unregister key listener
		keyManager.unregisterKeyListener(safetyModeKeyListener);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		
		// Finalize any active session
		sessionManager.finalizeSession();
		
		timerEnd = null;
		hasNotified = false;
		tileManager.reset();
		lastStatsSample = null;
		playerTracker.reset();
		showVisualNotification = false;
		visualNotificationEnd = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState currentState = gameStateChanged.getGameState();
		
		// Only show welcome message when actually logging in or hopping worlds
		// (not when teleporting, which also triggers LOGGED_IN)
		if (currentState == GameState.LOGGED_IN)
		{
			if (config.enableWelcomeMessage() && 
				(previousGameState == GameState.LOGIN_SCREEN || 
				 previousGameState == GameState.HOPPING ||
				 previousGameState == GameState.LOGGING_IN))
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Splash Helper is active!", null);
			}
		}
		
		previousGameState = currentState;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("splashhelper"))
		{
			return;
		}

		// Rebuild statistics panel when any statistics panel config changes
		if (event.getKey().startsWith("overallStatFields") ||
			event.getKey().startsWith("currentSessionFields") ||
			event.getKey().startsWith("sessionHistoryFields") ||
			event.getKey().equals("showOverallStats") ||
			event.getKey().equals("showCurrentSession") ||
			event.getKey().equals("showSessionHistory"))
		{
			if (statisticsPanel != null)
			{
				statisticsPanel.rebuildPanels();
				log.debug("Statistics panel rebuilt due to config change: {}", event.getKey());
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.MAGIC)
		{
			return;
		}

		if (!sessionManager.hasActiveSession())
		{
			return;
		}

		int currentXp = event.getXp();
		SplashSession session = sessionManager.getCurrentSession();
		
		// Detect or use configured spell
		SplashSpell spell = null;
		if (config.autoDetectSpell())
		{
			// Try to detect from XP - need to calculate gain
			int lastXp = session.getCurrentMagicXp();
			if (lastXp > 0)
			{
				int xpGained = currentXp - lastXp;
				if (xpGained > 0)
				{
					spell = SplashSpell.fromXpDrop(xpGained);
					if (spell != null)
					{
						sessionManager.updateSpell(spell);
						log.debug("Auto-detected spell: {} from {} XP", spell.getName(), xpGained);
					}
				}
			}
			if (spell == null)
			{
				spell = session.getSpell();
			}
		}
		else
		{
			spell = config.selectedSpell();
			sessionManager.updateSpell(spell);
		}

		// Record the cast via SessionManager
		int xpGained = sessionManager.recordCast(currentXp, spell);
		
		if (xpGained > 0)
		{
			// Update panel
			if (statisticsPanel != null)
			{
				statisticsPanel.updatePanel();
			}
			
			log.debug("Spell cast detected: +{} XP, total casts: {}", xpGained, session.getSpellsCast());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateVisualNotificationState();
		checkTimerExpiration();
		checkHpThreshold();

		if (client.getTopLevelWorldView() == null)
		{
			return;
		}

		// Check magic attack bonus
		hasBadMagicBonus = hasBadMagicAttackBonus();

		if (tileManager.updateKnightPositionTracking())
		{
			notificationService.sendBoundaryNotification("Knight reached boundary tile!");
		}
		tileManager.trackKnightMovement();

		updateSessionStatistics();
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getTarget() != null && event.getTarget().getName() != null) 
		{
			String sourceName = event.getSource().getName();
			String playerName = client.getLocalPlayer().getName();
			if (sourceName != null && sourceName.equalsIgnoreCase(playerName)) {
				// Session start moved to onAnimationChanged to detect actual spell casting
				// This works for both normal combat and safe-spotting scenarios
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == tileManager.getCurrentTarget())
		{
			notificationService.sendNotification("Actor died, resetting timer");
			tileManager.setCurrentTarget(null);
			timerEnd = null;
			hasNotified = false;
		}
	}

	@Subscribe
	@SuppressWarnings("deprecation")
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();
		
		// Remove "Attack" option from knights if magic bonus is too high
		if (hasBadMagicBonus)
		{
			String configuredNpc = config.targetNpc().getNpcName();
			java.util.List<MenuEntry> filteredEntries = new java.util.ArrayList<>();
			
			for (MenuEntry entry : entries)
			{
				boolean shouldRemove = false;
				
				if (entry.getOption() != null && entry.getOption().equalsIgnoreCase("Attack"))
				{
					String targetName = cleanNpcName(entry.getTarget());
					if (configuredNpc != null && !configuredNpc.isEmpty() && 
						isAllowedNpc(targetName) && targetName.equalsIgnoreCase(configuredNpc))
					{
						shouldRemove = true;
					}
				}
				
				if (!shouldRemove)
				{
					filteredEntries.add(entry);
				}
			}
			
			// Update menu entries if we removed any
			if (filteredEntries.size() < entries.length)
			{
				client.setMenuEntries(filteredEntries.toArray(new MenuEntry[0]));
				entries = client.getMenuEntries();
			}
		}
		
		// Add "Knight Boundary" submenu to tile right-click menu
		
		// Find the first Walk menu entry to get tile coordinates
		for (MenuEntry entry : entries)
		{
			if (entry.getType() == MenuAction.WALK)
			{
				// Create main "Knight Boundary" menu entry
				MenuEntry boundaryMenu = client.createMenuEntry(1)
					.setOption("Knight Boundary")
					.setTarget("")
					.setType(MenuAction.RUNELITE);
				
				// Create submenu
				Menu submenu = boundaryMenu.createSubMenu();
				
				// Add Set/Unset option to submenu
				if (tileManager.getBoundaryTile() == null)
				{
					submenu.createMenuEntry(-1)
						.setOption("Set")
						.setType(MenuAction.RUNELITE)
						.setParam0(entry.getParam0())
						.setParam1(entry.getParam1())
						.onClick(this::onBoundarySetClick);
				}
				else
				{
					submenu.createMenuEntry(-1)
						.setOption("Unset")
						.setType(MenuAction.RUNELITE)
						.setParam0(entry.getParam0())
						.setParam1(entry.getParam1())
						.onClick(this::onBoundaryUnsetClick);
				}
				
				// Add Color option to submenu
				submenu.createMenuEntry(-1)
					.setOption("Color")
					.setType(MenuAction.RUNELITE)
					.setParam0(entry.getParam0())
					.setParam1(entry.getParam1())
					.onClick(this::onBoundaryColorClick);
				
				// Create main "Knight Tile 1" menu entry
				MenuEntry tile1Menu = client.createMenuEntry(2)
					.setOption("Knight Tile 1")
					.setTarget("")
					.setType(MenuAction.RUNELITE);
				
				// Create submenu for Knight Tile 1
				Menu submenu1 = tile1Menu.createSubMenu();
				
				// Add Set/Unset option to submenu
				if (tileManager.getKnightTile1() == null)
				{
					submenu1.createMenuEntry(-1)
						.setOption("Set")
						.setType(MenuAction.RUNELITE)
						.setParam0(entry.getParam0())
						.setParam1(entry.getParam1())
						.onClick(this::onKnightTile1SetClick);
				}
				else
				{
					submenu1.createMenuEntry(-1)
						.setOption("Unset")
						.setType(MenuAction.RUNELITE)
						.setParam0(entry.getParam0())
						.setParam1(entry.getParam1())
						.onClick(this::onKnightTile1UnsetClick);
				}
				
				// Add Color option to submenu
				submenu1.createMenuEntry(-1)
					.setOption("Color")
					.setType(MenuAction.RUNELITE)
					.setParam0(entry.getParam0())
					.setParam1(entry.getParam1())
					.onClick(this::onKnightTile1ColorClick);
				
				// Create main "Knight Tile 2" menu entry
				MenuEntry tile2Menu = client.createMenuEntry(3)
					.setOption("Knight Tile 2")
					.setTarget("")
					.setType(MenuAction.RUNELITE);
				
				// Create submenu for Knight Tile 2
				Menu submenu2 = tile2Menu.createSubMenu();
				
				// Add Set/Unset option to submenu
				if (tileManager.getKnightTile2() == null)
				{
					submenu2.createMenuEntry(-1)
						.setOption("Set")
						.setType(MenuAction.RUNELITE)
						.setParam0(entry.getParam0())
						.setParam1(entry.getParam1())
						.onClick(this::onKnightTile2SetClick);
				}
				else
				{
					submenu2.createMenuEntry(-1)
						.setOption("Unset")
						.setType(MenuAction.RUNELITE)
						.setParam0(entry.getParam0())
						.setParam1(entry.getParam1())
						.onClick(this::onKnightTile2UnsetClick);
				}
				
				// Add Color option to submenu
				submenu2.createMenuEntry(-1)
					.setOption("Color")
					.setType(MenuAction.RUNELITE)
					.setParam0(entry.getParam0())
					.setParam1(entry.getParam1())
					.onClick(this::onKnightTile2ColorClick);
				
				// Only add it once
				break;
			}
		}
	}

	private void onBoundarySetClick(MenuEntry entry)
	{
		if (client.getTopLevelWorldView() == null)
		{
			log.warn("TopLevelWorldView is null, cannot set boundary");
			return;
		}
		
		Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
		
		if (tile != null)
		{
			WorldPoint location = tile.getWorldLocation();
			tileManager.setBoundaryTile(location);
			log.info("✓ Boundary tile successfully set to: {}", location);
			notificationService.sendNotification("Boundary tile set at: " + location.getX() + ", " + location.getY());
		}
	}

	private void onBoundaryUnsetClick(MenuEntry entry)
	{
		tileManager.unsetBoundaryTile();
		log.info("✓ Boundary tile unset");
		notificationService.sendNotification("Boundary tile unset");
	}

	private void onBoundaryColorClick(MenuEntry entry)
	{
		// The color picker is automatically shown by RuneLite's config system
		// when the user changes the boundaryTileColor config item
		notificationService.sendNotification("Change boundary color in the plugin settings");
	}

	private void onKnightTile1SetClick(MenuEntry entry)
	{
		if (client.getTopLevelWorldView() == null)
		{
			log.warn("TopLevelWorldView is null, cannot set Knight Tile 1");
			return;
		}
		
		Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
		
		if (tile != null)
		{
			WorldPoint location = tile.getWorldLocation();
			tileManager.setKnightTile1(location);
			log.info("✓ Knight Tile 1 successfully set to: {}", location);
			notificationService.sendNotification("Knight Tile 1 set at: " + location.getX() + ", " + location.getY());
		}
	}

	private void onKnightTile1UnsetClick(MenuEntry entry)
	{
		tileManager.unsetKnightTile1();
		log.info("✓ Knight Tile 1 unset");
		notificationService.sendNotification("Knight Tile 1 unset");
	}

	private void onKnightTile1ColorClick(MenuEntry entry)
	{
		notificationService.sendNotification("Change Knight Tile 1 color in the plugin settings");
	}

	private void onKnightTile2SetClick(MenuEntry entry)
	{
		if (client.getTopLevelWorldView() == null)
		{
			log.warn("TopLevelWorldView is null, cannot set Knight Tile 2");
			return;
		}
		
		Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
		
		if (tile != null)
		{
			WorldPoint location = tile.getWorldLocation();
			tileManager.setKnightTile2(location);
			log.info("✓ Knight Tile 2 successfully set to: {}", location);
			notificationService.sendNotification("Knight Tile 2 set at: " + location.getX() + ", " + location.getY());
		}
	}

	private void onKnightTile2UnsetClick(MenuEntry entry)
	{
		tileManager.unsetKnightTile2();
		log.info("✓ Knight Tile 2 unset");
		notificationService.sendNotification("Knight Tile 2 unset");
	}

	private void onKnightTile2ColorClick(MenuEntry entry)
	{
		notificationService.sendNotification("Change Knight Tile 2 color in the plugin settings");
	}

	
	/**
	 * Checks if an NPC is allowed to be set as a target.
	 * @param npcName The cleaned NPC name
	 * @return true if the NPC is allowed, false otherwise
	 */
	private boolean isAllowedNpc(String npcName)
	{
		if (npcName == null)
		{
			return false;
		}
		
		// List of allowed NPCs for targeting
		return npcName.equalsIgnoreCase("Knight of Ardougne") ||
			   npcName.equalsIgnoreCase("Rat") ||
			   npcName.equalsIgnoreCase("Guard");
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Safety mode action filtering
		if (safetyModeEnabled && shouldBlockAction(event))
		{
			event.consume();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
				"<col=ff0000>Safety Mode: This action is blocked while safety mode is enabled.</col>", null);
			return;
		}

		// When hasEscaped is true and player interacts, mute notifications until knight returns
		if (tileManager.isHasEscaped() && !notificationService.areNotificationsMuted())
		{
			// Any click interaction while escaped mutes notifications
			if (event.getMenuAction() != MenuAction.CANCEL &&
				event.getMenuAction() != MenuAction.WALK)
			{
				notificationService.muteNotifications();
				log.info("Notifications muted - player interacted while knight escaped");
			}
		}

		// Check if the click is an NPC interaction
		if (event.getMenuAction() != MenuAction.NPC_FIRST_OPTION &&
			event.getMenuAction() != MenuAction.NPC_SECOND_OPTION &&
			event.getMenuAction() != MenuAction.NPC_THIRD_OPTION &&
			event.getMenuAction() != MenuAction.NPC_FOURTH_OPTION &&
			event.getMenuAction() != MenuAction.NPC_FIFTH_OPTION)
		{
			return;
		}

		// Get the NPC that was clicked
		String targetName = cleanNpcName(event.getMenuTarget());

		// Check if this is the NPC we're tracking and if it's allowed
		String configuredNpc = config.targetNpc().getNpcName();
		if (configuredNpc != null && !configuredNpc.isEmpty())
		{
			if (isAllowedNpc(targetName) && targetName.equalsIgnoreCase(configuredNpc))
			{
				// Check if player is trying to attack with bad magic bonus
				String menuOption = event.getMenuOption();
				if (menuOption != null && menuOption.equalsIgnoreCase("Attack") && hasBadMagicBonus)
				{
					// Block the attack action
					event.consume();
					
					// Show warning notification
					Instant now = Instant.now();
					if (lastBonusWarning == null || Duration.between(lastBonusWarning, now).getSeconds() > 5)
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
							"<col=ff0000>Warning: Your magic attack bonus is too high for splashing! (Need -64 or lower)</col>", null);
						lastBonusWarning = now;
					}
					return;
				}

				// Only restart timer if session is already active
				// Session start is handled by onInteractingChanged
				if (timerEnd != null && timerEnd.isAfter(Instant.now()))
				{
					// Restart timer if clicking knight during active session
					startTimer();
					log.info("Timer restarted by clicking knight");
				}
			}
		}
	}

	private void startTimer()
	{
		int durationMinutes = config.timerDuration();
		timerEnd = Instant.now().plus(Duration.ofMinutes(durationMinutes));
		hasNotified = false;
		
		log.info("Timer started for {} minutes", durationMinutes);
		
		if (config.enableWelcomeMessage())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
				String.format("Timer started: %d minutes", durationMinutes), null);
		}
		
		// Start session when timer starts (player engaged with target)
		if (config.enableStatistics() && !sessionManager.hasActiveSession())
		{
			startSession(config.selectedSpell());
		}
	}

	/**
	 * Cleans an NPC name by removing color tags, level information, and trailing whitespace.
	 * @param npcName The raw NPC name from the game (e.g., "<col=ffffff>Knight of Ardougne (level-46)")
	 * @return The cleaned NPC name (e.g., "Knight of Ardougne")
	 */
	private String cleanNpcName(String npcName)
	{
		if (npcName == null)
		{
			return null;
		}
		
		// Remove color tags like <col=ffffff>
		String cleaned = npcName.replaceAll("<.*?>", "");
		
		// Remove level information like (level-46)
		cleaned = cleaned.replaceAll("\\s*\\([^)]*\\)\\s*$", "");
		
		// Trim any remaining whitespace
		return cleaned.trim();
	}

	/**
	 * Check if the player's magic attack bonus is too high for splashing.
	 * For reliable splashing, magic attack bonus should be -64 or lower.
	 * @return true if magic attack bonus is greater than -64 (too high)
	 */
	private boolean hasBadMagicAttackBonus()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return false;
		}

		// Get magic attack bonus directly from client
		int magicAttackBonus = client.getBoostedSkillLevel(Skill.MAGIC);
		
		// Actually we need the equipment bonus, not skill level
		// Use the player's combat stats - magic attack is stored in the player's stats
		// For now, we'll check equipment manually
		
		// Get equipment container
		ItemContainer equipment = client.getItemContainer(InterfaceID.INVENTORY);
		if (equipment == null)
		{
			return false;
		}

		magicAttackBonus = 0;

		// Sum up magic attack bonus from all equipment slots
		Item[] items = equipment.getItems();
		for (int i = 0; i < items.length; i++)
		{
			Item item = items[i];
			if (item != null && item.getId() > 0)
			{
				ItemStats itemStats = itemManager.getItemStats(item.getId());
				if (itemStats != null && itemStats.getEquipment() != null)
				{
					magicAttackBonus += itemStats.getEquipment().getAmagic();
				}
			}
		}

		// Return true if bonus is greater than -64 (bad for splashing)
		return magicAttackBonus > -64;
	}

	// ==================== Session Management ====================

	private void startSession(SplashSpell spell)
	{
		// Finalize any existing session first
		sessionManager.finalizeSession();

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		String playerName = localPlayer.getName();
		int world = client.getWorld();
		boolean stickyKnight = isStickyKnight();

		sessionManager.startSession(playerName, spell, timerEnd, world, stickyKnight);

		lastStatsSample = Instant.now();
		
		log.info("Splash session started for {} using {}", playerName, spell);
	}

	
	private void sampleSessionStatistics()
	{
		SplashSession session = sessionManager.getCurrentSession();
		if (session == null || !session.isActive())
		{
			return;
		}

		// Update magic XP
		session.setCurrentMagicXp(client.getSkillExperience(Skill.MAGIC));

		// Update rune count
		SplashSpell spell = session.getSpell();
		if (spell != null)
		{
			session.setCurrentRuneCount(countLimitingRunes(spell));
		}

		// Count nearby players
		int nearbyPlayers = countNearbyPlayers(config.playerCountRadius());
		sessionManager.recordPlayerCountSample(nearbyPlayers);

		// Add pickpocketers to session from tracker
		for (String pickpocketer : playerTracker.getPickpocketers())
		{
			sessionManager.addPickpocketer(pickpocketer);
		}
	}

	public int countNearbyPlayers(int radius)
	{
		if (client.getTopLevelWorldView() == null || client.getLocalPlayer() == null)
		{
			return 0;
		}

		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		int count = 0;

		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player != null && player != client.getLocalPlayer())
			{
				WorldPoint otherLocation = player.getWorldLocation();
				if (otherLocation != null && playerLocation.distanceTo(otherLocation) <= radius)
				{
					count++;
				}
			}
		}

		return count;
	}

	/**
	 * Get remaining casts for the current spell (from session or config).
	 * Delegates to RuneCalculator service.
	 */
	public int getRemainingCastsForCurrentSpell()
	{
		return runeCalculator.getRemainingCasts(getCurrentSpell());
	}

	/**
	 * Get infinite runes from equipped staves.
	 * Delegates to RuneCalculator service.
	 */
	public java.util.Set<Integer> getInfiniteRunesFromEquipment()
	{
		return runeCalculator.getInfiniteRunesFromEquipment();
	}

	/**
	 * Count limiting runes for a spell.
	 * Delegates to RuneCalculator service.
	 */
	private int countLimitingRunes(SplashSpell spell)
	{
		return runeCalculator.getRemainingCasts(spell);
	}

	/**
	 * Get the current spell from session or config.
	 */
	private SplashSpell getCurrentSpell()
	{
		SplashSession session = sessionManager.getCurrentSession();
		if (session != null && session.getSpell() != null)
		{
			return session.getSpell();
		}
		return config != null ? config.selectedSpell() : null;
	}

	/**
	 * Check if current target is a sticky knight.
	 * Delegates to KnightDetector service.
	 */
	private boolean isStickyKnight()
	{
		return knightDetector.isStickyKnight(tileManager.getCurrentTarget());
	}

	// ==================== Event Handlers ====================

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();

		// Check if local player is casting a spell
		if (actor == client.getLocalPlayer())
		{
			int animationId = actor.getAnimation();
			
			// Common magic casting animation IDs
			boolean isCasting = (animationId >= 711 && animationId <= 717) ||  // Standard spells
							   (animationId >= 1162 && animationId <= 1167) || // Ancient spells
							   (animationId >= 1978 && animationId <= 1993) || // Lunar spells
							   (Constants.isStandardSpellAnimation(animationId));
			
			if (isCasting)
			{
				// Get who the player is currently interacting with
				Actor interactingTarget = client.getLocalPlayer().getInteracting();
				
				if (interactingTarget != null && interactingTarget.getName() != null)
				{
					String targetName = cleanNpcName(interactingTarget.getName());
					String configuredNpc = config.targetNpc().getNpcName();
					
					if (configuredNpc != null && !configuredNpc.isEmpty() &&
						targetName != null && targetName.equalsIgnoreCase(configuredNpc))
					{
						// Check if we already have an active session
						if (timerEnd == null || timerEnd.isBefore(Instant.now()))
						{
							// Start session when player is actually casting spells at the knight
							tileManager.setCurrentTarget(interactingTarget);
							startTimer();
							log.info("Session started by detecting spell animation on {} (animation: {})", targetName, animationId);
						}
					}
				}
			}
		}

		// Check if a player near the knight is pickpocketing
		if (actor instanceof Player && actor != client.getLocalPlayer())
		{
			Player player = (Player) actor;
			int animationId = player.getAnimation();
			
			// Pickpocket animation ID
			if (animationId == 881)
			{
				// Check if they're near the knight
				if (tileManager.getCurrentTarget() != null)
				{
					WorldPoint playerPos = player.getWorldLocation();
					WorldPoint knightPos = tileManager.getCurrentTarget().getWorldLocation();
					
					if (playerPos != null && knightPos != null && playerPos.distanceTo(knightPos) <= 1)
					{
						String playerName = player.getName();
						if (playerName != null)
						{
							playerTracker.addPickpocketer(playerName);
							sessionManager.addPickpocketer(playerName);
						}
					}
				}
			}
		}
	}

	/**
	 * Update visual notification state when timer expires.
	 */
	private void updateVisualNotificationState()
	{
		if (showVisualNotification && visualNotificationEnd != null)
		{
			if (Instant.now().isAfter(visualNotificationEnd))
			{
				showVisualNotification = false;
				visualNotificationEnd = null;
			}
		}
	}

	/**
	 * Check if timer has expired and send notification.
	 */
	private void checkTimerExpiration()
	{
		if (timerEnd != null && !hasNotified)
		{
			Duration remaining = Duration.between(Instant.now(), timerEnd);
			if (remaining.isNegative() || remaining.isZero())
			{
				notificationService.sendTimerNotification("Splash timer has expired!");
				hasNotified = true;
				log.info("Timer expired - notification sent");
			}
		}
	}

	/**
	 * Check HP threshold when knight can attack.
	 */
	private void checkHpThreshold()
	{
		if (tileManager.isHasEscaped() && config != null && config.enableHpNotification())
		{
			// Validate client is available
			if (client == null)
			{
				return;
			}
			
			int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
			if (currentHp <= config.hpThreshold())
			{
				notificationService.sendHpNotification("HP is low (" + currentHp + ")! Knight may be attacking you!");
			}
		}
	}

	
	
	/**
	 * Update session statistics and panel.
	 */
	private void updateSessionStatistics()
	{
		if (config != null && config.enableStatistics())
		{
			// Validate sessionManager before using
			if (sessionManager == null)
			{
				return;
			}
			
			// Update cached values on client thread for UI access
			cachedRemainingCasts = getRemainingCastsForCurrentSpell();
			cachedInfiniteRunes = getInfiniteRunesFromEquipment();
			
			Instant now = Instant.now();
			if (sessionManager.hasActiveSession())
			{
				// Get accumulated rune usage from session (tracked per cast)
				SplashSession currentSession = sessionManager.getCurrentSession();
				if (currentSession != null)
				{
					cachedActualRuneUsage = currentSession.getActualRuneUsage();
					cachedRuneCost = currentSession.getRuneCostGp();
				}
				
				// Check for session timeout via service
				if (sessionManager.checkSessionTimeout())
				{
					// Reset timer when session goes idle
					timerEnd = null;
					hasNotified = false;
				}
				else if (lastStatsSample == null || 
					Duration.between(lastStatsSample, now).getSeconds() >= config.statisticsInterval())
				{
					sampleSessionStatistics();
					lastStatsSample = now;
				}
			}
			
			// Update statistics panel
			if (statisticsPanel != null)
			{
				statisticsPanel.updatePanel();
			}
		}
	}

	/**
	 * Check if an action should be blocked in safety mode.
	 */
	private boolean shouldBlockAction(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		
		// Allowed actions that reset timer - return false (not blocked)
		if (isAllowedAction(event))
		{
			// Reset splash timer for allowed actions
			if (timerEnd != null && timerEnd.isAfter(Instant.now()))
			{
				startTimer();
				log.info("Timer restarted by allowed action: {}", option);
			}
			return false;
		}
		
		// Block all other actions
		return true;
	}

	/**
	 * Check if an action is allowed in safety mode.
	 */
	private boolean isAllowedAction(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		MenuAction action = event.getMenuAction();
		
		// Allow clicking the knight (NPC interactions)
		if (action == MenuAction.NPC_FIRST_OPTION ||
			action == MenuAction.NPC_SECOND_OPTION ||
			action == MenuAction.NPC_THIRD_OPTION ||
			action == MenuAction.NPC_FOURTH_OPTION ||
			action == MenuAction.NPC_FIFTH_OPTION)
		{
			String targetName = cleanNpcName(event.getMenuTarget());
			String configuredNpc = config.targetNpc().getNpcName();
			if (configuredNpc != null && !configuredNpc.isEmpty() &&
				targetName != null && targetName.equalsIgnoreCase(configuredNpc))
			{
				return true;
			}
		}
		
		// Allow unequipping items from equipment menu
		if (action == MenuAction.CC_OP && option != null && option.equalsIgnoreCase("Remove"))
		{
			return true;
		}
		
		// Allow clicking "use" menu option
		if (option != null && option.equalsIgnoreCase("Use"))
		{
			return true;
		}
		
		// Allow clicking stackable items in inventory (but only if they're actually stackable)
		if (action == MenuAction.CC_OP)
		{
			ItemContainer inventory = client.getItemContainer(InterfaceID.INVENTORY);
			if (inventory != null)
			{
				Item item = inventory.getItem(event.getParam0());
				if (item != null)
				{
					ItemComposition itemDef = client.getItemDefinition(item.getId());
					if (itemDef != null && itemDef.isStackable())
					{
						return true;
					}
				}
			}
			// If it's an inventory item action but not stackable, it's blocked
			// Don't return false here, let the method continue to check other conditions
		}
		
		// Block all other actions
		return false;
	}

	/**
	 * Toggle safety mode on/off.
	 */
	public void toggleSafetyMode()
	{
		safetyModeEnabled = !safetyModeEnabled;
		log.info("Safety mode toggled: {}", safetyModeEnabled ? "ON" : "OFF");
		
		// Send notification to user (must be on client thread)
		String message = safetyModeEnabled ? 
			"<col=00ff00>Safety mode ENABLED</col>" : 
			"<col=ff0000>Safety mode DISABLED</col>";
		clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
	}

	/**
	 * Key listener for safety mode hotkey.
	 */
	private final KeyListener safetyModeKeyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			ModifierlessKeybind hotkey = config.safetyModeHotkey();
			if (hotkey.matches(e))
			{
				toggleSafetyMode();
				e.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	}
