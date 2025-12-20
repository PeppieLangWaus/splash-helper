package xyz.peppie.splashhelper;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.awt.TrayIcon;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import xyz.peppie.splashhelper.overlays.BoundaryTileOverlay;
import xyz.peppie.splashhelper.overlays.SplashHelperOverlay;
import xyz.peppie.splashhelper.service.KnightDetector;
import xyz.peppie.splashhelper.service.PlayerTracker;
import xyz.peppie.splashhelper.service.RuneCalculator;
import xyz.peppie.splashhelper.service.SessionManager;
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
	private ClientToolbar clientToolbar;

	@Inject
	private Notifier notifier;

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

	// Statistics panel
	private SplashStatisticsPanel statisticsPanel;
	private NavigationButton navButton;

	@Getter
	private Instant timerEnd;

	private boolean hasNotified = false;
	
	@Getter
	private WorldPoint boundaryTile = null;
	@Getter
	private WorldPoint knightTile1 = null;
	@Getter
	private WorldPoint knightTile2 = null;
	@Getter
	private Actor currentTarget = null;
	private boolean boundaryNotified = false;
	
	// Movement tracking
	private WorldPoint lastNpcPosition = null;
	private int movementCount = 0;
	private Instant trackingStartTime = null;
	@Getter
	private double movementsPerMinute = 0.0;

	// hasEscaped state machine
	@Getter
	private boolean hasEscaped = false;
	private int boundaryTickCounter = 0;
	private static final int BOUNDARY_DEBOUNCE_TICKS = 5;
	private boolean notificationsMuted = false;

	// Session tracking
	@Getter
	private SplashSession currentSession = null;
	@Getter
	private final List<SplashSession> sessionHistory = new ArrayList<>();
	private Instant lastStatsSample = null;
	private boolean isSplashing = false;
	
	// Cast tracking via XP drops
	private int lastMagicXp = -1;
	private Instant lastCastTime = null;
	private static final int SESSION_TIMEOUT_SECONDS = Constants.SESSION_TIMEOUT_SECONDS;
	
	// Cached values for UI (updated on client thread)
	@Getter
	private volatile int cachedRemainingCasts = 0;
	@Getter
	private volatile java.util.Set<Integer> cachedInfiniteRunes = new java.util.HashSet<>();
	@Getter
	private volatile java.util.List<int[]> cachedActualRuneUsage = new java.util.ArrayList<>();

	// Player tracking for pickpocketers
	private final Set<String> pickpocketers = new HashSet<>();
	@Getter
	private int currentPickpocketerCount = 0;

	// Visual notification state
	@Getter
	private boolean showVisualNotification = false;
	private Instant visualNotificationEnd = null;
	private static final int VISUAL_NOTIFICATION_DURATION_MS = 2000;

	@Provides
	SplashHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SplashHelperConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Splash Helper started!");
		overlay.setPlugin(this);
		boundaryOverlay.setPlugin(this);
		overlayManager.add(overlay);
		overlayManager.add(boundaryOverlay);

		// Create statistics panel
		statisticsPanel = new SplashStatisticsPanel(this, config, itemManager);
		
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
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		
		// Finalize any active session
		if (currentSession != null && currentSession.isActive())
		{
			finalizeSession();
		}
		
		timerEnd = null;
		hasNotified = false;
		boundaryTile = null;
		knightTile1 = null;
		knightTile2 = null;
		currentTarget = null;
		boundaryNotified = false;
		lastNpcPosition = null;
		movementCount = 0;
		trackingStartTime = null;
		movementsPerMinute = 0.0;
		hasEscaped = false;
		boundaryTickCounter = 0;
		notificationsMuted = false;
		currentSession = null;
		lastStatsSample = null;
		isSplashing = false;
		pickpocketers.clear();
		currentPickpocketerCount = 0;
		showVisualNotification = false;
		visualNotificationEnd = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			// Initialize magic XP tracking
			lastMagicXp = client.getSkillExperience(Skill.MAGIC);
			
			if (config.enableWelcomeMessage())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Splash Helper is active!", null);
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

		int currentXp = event.getXp();
		
		// Initialize on first call
		if (lastMagicXp < 0)
		{
			lastMagicXp = currentXp;
			return;
		}

		int xpGained = currentXp - lastMagicXp;
		lastMagicXp = currentXp;

		// Only count positive XP gains (spell cast)
		if (xpGained > 0 && currentSession != null && currentSession.isActive())
		{
			// Detect or use configured spell
			SplashSpell detectedSpell = null;
			if (config.autoDetectSpell())
			{
				detectedSpell = SplashSpell.fromXpDrop(xpGained);
				if (detectedSpell != null && currentSession.getSpell() != detectedSpell)
				{
					currentSession.setSpell(detectedSpell);
					log.debug("Auto-detected spell: {} from {} XP", detectedSpell.getName(), xpGained);
				}
			}
			else
			{
				// Use manually configured spell
				SplashSpell configSpell = config.selectedSpell();
				if (currentSession.getSpell() != configSpell)
				{
					currentSession.setSpell(configSpell);
				}
			}
			
			// Increment cast counter
			currentSession.incrementSpellsCast();
			
			// Update XP
			currentSession.setCurrentMagicXp(currentXp);
			
			// Update rune count
			SplashSpell spell = currentSession.getSpell();
			if (spell != null)
			{
				currentSession.setCurrentRuneCount(countLimitingRunes(spell));
			}
			
			// Record last cast time for timeout
			lastCastTime = Instant.now();
			
			// Update panel
			if (statisticsPanel != null)
			{
				statisticsPanel.updatePanel();
			}
			
			log.debug("Spell cast detected: +{} XP, total casts: {}", xpGained, currentSession.getSpellsCast());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Update visual notification state
		if (showVisualNotification && visualNotificationEnd != null)
		{
			if (Instant.now().isAfter(visualNotificationEnd))
			{
				showVisualNotification = false;
				visualNotificationEnd = null;
			}
		}

		// Check if timer has expired and send notification
		if (timerEnd != null && !hasNotified)
		{
			Duration remaining = Duration.between(Instant.now(), timerEnd);
			if (remaining.isNegative() || remaining.isZero())
			{
				sendTimerNotification("Splash timer has expired!");
				hasNotified = true;
				log.info("Timer expired - notification sent");
			}
		}

		// Check HP threshold when knight can attack (hasEscaped is true)
		if (hasEscaped && config.enableHpNotification())
		{
			int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
			if (currentHp <= config.hpThreshold())
			{
				sendHpNotification("HP is low (" + currentHp + ")! Knight may be attacking you!");
			}
		}

		if (client.getTopLevelWorldView() == null)
		{
			return;
		}

		// Check knight position for hasEscaped state machine and boundary notifications
		if (currentTarget != null && currentTarget instanceof NPC)
		{
			NPC knight = (NPC) currentTarget;
			WorldPoint knightPos = knight.getWorldLocation();
			
			if (knightPos != null)
			{
				boolean onKnightTile = (knightTile1 != null && knightPos.equals(knightTile1)) ||
									   (knightTile2 != null && knightPos.equals(knightTile2));
				boolean onBoundary = boundaryTile != null && knightPos.equals(boundaryTile);
				
				// hasEscaped state machine
				if (onBoundary)
				{
					if (!hasEscaped)
					{
						hasEscaped = true;
						boundaryTickCounter = 0;
						
						// Send notification only once when entering boundary
						if (!boundaryNotified && config.enableBoundaryNotification())
						{
							sendBoundaryNotification("Knight reached boundary tile!");
							boundaryNotified = true;
						}
					}
					else
					{
						boundaryTickCounter++;
					}
				}
				else if (onKnightTile)
				{
					// Knight returned to original tile
					if (hasEscaped)
					{
						hasEscaped = false;
						notificationsMuted = false;
						boundaryNotified = false;
						boundaryTickCounter = 0;
					}
				}
				else
				{
					// Knight is on some other tile (not boundary, not knight tiles)
					if (hasEscaped)
					{
						boundaryTickCounter++;
						// After 5 ticks on a non-valid tile, allow re-notification
						if (boundaryTickCounter >= BOUNDARY_DEBOUNCE_TICKS)
						{
							boundaryNotified = false;
						}
					}
				}
			}
		}
		
		// Track NPC movement between Knight Tile 1 and Knight Tile 2
		if (knightTile1 != null && knightTile2 != null && currentTarget != null)
		{
			WorldPoint currentPosition = currentTarget.getWorldLocation();
			
			if (currentPosition != null)
			{
				// Check if NPC is on either Knight Tile
				boolean onTile1 = currentPosition.equals(knightTile1);
				boolean onTile2 = currentPosition.equals(knightTile2);
				
				if (onTile1 || onTile2)
				{
					// Initialize tracking on first detection
					if (trackingStartTime == null)
					{
						trackingStartTime = Instant.now();
						lastNpcPosition = currentPosition;
					}
					// Check if NPC moved between tiles
					else if (lastNpcPosition != null && !currentPosition.equals(lastNpcPosition))
					{
						// Only count movements between the two tiles
						boolean wasOnTile1 = lastNpcPosition.equals(knightTile1);
						boolean wasOnTile2 = lastNpcPosition.equals(knightTile2);
						
						if ((wasOnTile1 && onTile2) || (wasOnTile2 && onTile1))
						{
							movementCount++;
							if (currentSession != null)
							{
								currentSession.incrementKnightMovements();
							}
						}
						
						lastNpcPosition = currentPosition;
					}
					
					// Calculate movements per minute
					if (trackingStartTime != null)
					{
						Duration trackingDuration = Duration.between(trackingStartTime, Instant.now());
						double minutes = trackingDuration.toMillis() / 60000.0;
						
						if (minutes > 0)
						{
							movementsPerMinute = movementCount / minutes;
						}
					}
				}
				// Update last position if NPC is on either tile
				if (onTile1 || onTile2)
				{
					lastNpcPosition = currentPosition;
				}
			}
		}

		// Session statistics sampling and panel update
		if (config.enableStatistics())
		{
			// Update cached values on client thread for UI access
			cachedRemainingCasts = getRemainingCastsForCurrentSpell();
			cachedInfiniteRunes = getInfiniteRunesFromEquipment();
			cachedActualRuneUsage = getActualRuneUsage();
			
			Instant now = Instant.now();
			if (currentSession != null && currentSession.isActive())
			{
				// Check for session timeout (no cast in last 10 seconds)
				if (lastCastTime != null && 
					Duration.between(lastCastTime, now).getSeconds() >= SESSION_TIMEOUT_SECONDS)
				{
					log.info("Session timeout - no cast in {} seconds", SESSION_TIMEOUT_SECONDS);
					finalizeSession();
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

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getTarget() != null && event.getTarget().getName() != null) 
		{
			String sourceName = event.getSource().getName();
			String playerName = client.getLocalPlayer().getName();
			if (sourceName != null && sourceName.equalsIgnoreCase(playerName)) {
				String interactedNpcName = cleanNpcName(event.getTarget().getName());
	
				// Check if NPC is allowed and matches configured name
				if (isAllowedNpc(interactedNpcName) && interactedNpcName.equalsIgnoreCase(config.targetNpc().getNpcName()))
				{
					currentTarget = event.getTarget();
					startTimer();	
				}
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == currentTarget)
		{
			sendNotification("Actor died, resetting timer");
			currentTarget = null;
			timerEnd = null;
			hasNotified = false;
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		// Add "Knight Boundary" submenu to tile right-click menu
		MenuEntry[] entries = event.getMenuEntries();
		
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
				if (boundaryTile == null)
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
				if (knightTile1 == null)
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
				if (knightTile2 == null)
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
			boundaryTile = tile.getWorldLocation();
			boundaryNotified = false;
			log.info("✓ Boundary tile successfully set to: {}", boundaryTile);
			sendNotification("Boundary tile set at: " + boundaryTile.getX() + ", " + boundaryTile.getY());
		}
	}

	private void onBoundaryUnsetClick(MenuEntry entry)
	{
		boundaryTile = null;
		boundaryNotified = false;
		log.info("✓ Boundary tile unset");
		sendNotification("Boundary tile unset");
	}

	private void onBoundaryColorClick(MenuEntry entry)
	{
		// The color picker is automatically shown by RuneLite's config system
		// when the user changes the boundaryTileColor config item
		sendNotification("Change boundary color in the plugin settings");
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
			knightTile1 = tile.getWorldLocation();
			log.info("✓ Knight Tile 1 successfully set to: {}", knightTile1);
			sendNotification("Knight Tile 1 set at: " + knightTile1.getX() + ", " + knightTile1.getY());
			
			// Initialize tracking if both tiles are set
			if (knightTile1 != null && knightTile2 != null)
			{
				resetMovementTracking();
			}
		}
	}

	private void onKnightTile1UnsetClick(MenuEntry entry)
	{
		knightTile1 = null;
		resetMovementTracking();
		log.info("✓ Knight Tile 1 unset");
		sendNotification("Knight Tile 1 unset");
	}

	private void onKnightTile1ColorClick(MenuEntry entry)
	{
		sendNotification("Change Knight Tile 1 color in the plugin settings");
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
			knightTile2 = tile.getWorldLocation();
			log.info("✓ Knight Tile 2 successfully set to: {}", knightTile2);
			sendNotification("Knight Tile 2 set at: " + knightTile2.getX() + ", " + knightTile2.getY());
			
			// Initialize tracking if both tiles are set
			if (knightTile1 != null && knightTile2 != null)
			{
				resetMovementTracking();
			}
		}
	}

	private void onKnightTile2UnsetClick(MenuEntry entry)
	{
		knightTile2 = null;
		resetMovementTracking();
		log.info("✓ Knight Tile 2 unset");
		sendNotification("Knight Tile 2 unset");
	}

	private void onKnightTile2ColorClick(MenuEntry entry)
	{
		sendNotification("Change Knight Tile 2 color in the plugin settings");
	}

	private void resetMovementTracking()
	{
		lastNpcPosition = null;
		movementCount = 0;
		trackingStartTime = null;
		movementsPerMinute = 0.0;
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
		// When hasEscaped is true and player interacts, mute notifications until knight returns
		if (hasEscaped && !notificationsMuted)
		{
			// Any click interaction while escaped mutes notifications
			if (event.getMenuAction() != MenuAction.CANCEL &&
				event.getMenuAction() != MenuAction.WALK)
			{
				notificationsMuted = true;
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
		Actor clickedActor = client.getTopLevelWorldView().npcs().byIndex(event.getId());

		// Check if this is the NPC we're tracking and if it's allowed
		String configuredNpc = config.targetNpc().getNpcName();
		if (configuredNpc != null && !configuredNpc.isEmpty())
		{
			if (isAllowedNpc(targetName) && targetName.equalsIgnoreCase(configuredNpc))
			{
				startTimer();
				currentTarget = clickedActor;
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
		if (config.enableStatistics() && (currentSession == null || !currentSession.isActive()))
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

	private void sendNotification(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, this.getName(),  message, null);
		// Send notification with sound using RuneLite's notifier
		Notification notification = new Notification(
			true,
			true,
			false,
			false,
			TrayIcon.MessageType.WARNING,
			RequestFocusType.OFF,
			NotificationSound.CUSTOM,
			null,
			client.getMusicVolume(),
			1,
			true,
			FlashNotification.DISABLED,
			Color.GREEN,
			false
			);
		notifier.notify(notification, message);
	}

	private void sendTimerNotification(String message)
	{
		if (!config.enableTimerNotification())
		{
			return;
		}
		if (notificationsMuted)
		{
			return;
		}
		sendNotificationInternal(message);
	}

	private void sendBoundaryNotification(String message)
	{
		if (!config.enableBoundaryNotification())
		{
			return;
		}
		if (notificationsMuted)
		{
			return;
		}
		sendNotificationInternal(message);
	}

	private void sendHpNotification(String message)
	{
		if (!config.enableHpNotification())
		{
			return;
		}
		if (notificationsMuted)
		{
			return;
		}
		sendNotificationInternal(message);
	}

	private void sendNotificationInternal(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, this.getName(), message, null);
		
		if (config.useVisualNotification())
		{
			// Trigger visual notification
			showVisualNotification = true;
			visualNotificationEnd = Instant.now().plusMillis(VISUAL_NOTIFICATION_DURATION_MS);
		}
		else
		{
			// Send sound notification
			Notification notification = new Notification(
				true,
				true,
				false,
				false,
				TrayIcon.MessageType.WARNING,
				RequestFocusType.OFF,
				NotificationSound.CUSTOM,
				null,
				client.getMusicVolume(),
				1,
				true,
				FlashNotification.DISABLED,
				Color.GREEN,
				false
				);
			notifier.notify(notification, message);
		}
	}

	// ==================== Session Management ====================

	private void startSession(SplashSpell spell)
	{
		if (currentSession != null && currentSession.isActive())
		{
			finalizeSession();
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		String playerName = localPlayer.getName();
		int world = client.getWorld();
		int startMagicXp = client.getSkillExperience(Skill.MAGIC);
		
		// Initialize cast tracking
		lastMagicXp = startMagicXp;
		lastCastTime = Instant.now();
		
		// Check if knight is sticky (female model - NPC ID check would be needed)
		boolean stickyKnight = isStickyKnight();

		currentSession = new SplashSession(
			playerName,
			spell,
			timerEnd,
			world,
			stickyKnight,
			startMagicXp
		);

		// Set initial rune count
		currentSession.setStartingRuneCount(countLimitingRunes(spell));
		currentSession.setCurrentRuneCount(currentSession.getStartingRuneCount());

		isSplashing = true;
		lastStatsSample = Instant.now();
		
		log.info("Splash session started for {} using {}", playerName, spell);
	}

	private void finalizeSession()
	{
		if (currentSession == null)
		{
			return;
		}

		currentSession.finalizeSession();
		sessionHistory.add(currentSession);
		
		log.info("Session finalized: {} casts, {} XP gained, {}s duration",
			currentSession.getSpellsCast(),
			currentSession.getMagicXpGained(),
			currentSession.getSessionDurationSeconds());

		currentSession = null;
		isSplashing = false;
		lastStatsSample = null;
	}

	private void sampleSessionStatistics()
	{
		if (currentSession == null || !currentSession.isActive())
		{
			return;
		}

		// Update magic XP
		currentSession.setCurrentMagicXp(client.getSkillExperience(Skill.MAGIC));

		// Update rune count
		SplashSpell spell = currentSession.getSpell();
		if (spell != null)
		{
			currentSession.setCurrentRuneCount(countLimitingRunes(spell));
		}

		// Count nearby players
		int nearbyPlayers = countNearbyPlayers(config.playerCountRadius());
		currentSession.addPlayerCountSample(nearbyPlayers);

		// Add pickpocketers to session
		for (String pickpocketer : pickpocketers)
		{
			currentSession.addPickpocketer(pickpocketer);
		}
		currentPickpocketerCount = currentSession.getPickpocketerCount();
	}

	private int countNearbyPlayers(int radius)
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
	 * Get the actual runes being consumed for the current spell.
	 * Delegates to RuneCalculator service.
	 */
	private java.util.List<int[]> getActualRuneUsage()
	{
		return runeCalculator.getActualRuneUsage(getCurrentSpell());
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
		if (currentSession != null && currentSession.getSpell() != null)
		{
			return currentSession.getSpell();
		}
		return config.selectedSpell();
	}

	// Female Knight of Ardougne model IDs (female armor body models)
	// These are the model IDs used by the female variant of the knight
	private static final java.util.Set<Integer> FEMALE_KNIGHT_MODEL_IDS = java.util.Set.of(
		11936,  // Female model
		3297   	// Male model
	);

	private boolean isStickyKnight()
	{
		if (currentTarget == null || !(currentTarget instanceof NPC))
		{
			return false;
		}
		NPC knight = (NPC) currentTarget;
		
		// Get the NPC's composition to check its models
		net.runelite.api.NPCComposition composition = knight.getTransformedComposition();
		if (composition == null)
		{
			composition = knight.getComposition();
		}
		
		if (composition == null)
		{
			return false;
		}
		
		// Check if any of the NPC's models match female knight models
		int[] models = composition.getModels();
		if (models != null)
		{
			for (int modelId : models)
			{
				if (FEMALE_KNIGHT_MODEL_IDS.contains(modelId))
				{
					return true;
				}
			}
		}
		
		return false;
	}

	// ==================== Event Handlers ====================

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();

		// Check if a player near the knight is pickpocketing
		if (actor instanceof Player && actor != client.getLocalPlayer())
		{
			Player player = (Player) actor;
			int animationId = player.getAnimation();
			
			// Pickpocket animation ID
			if (animationId == 881)
			{
				// Check if they're near the knight
				if (currentTarget != null)
				{
					WorldPoint playerPos = player.getWorldLocation();
					WorldPoint knightPos = currentTarget.getWorldLocation();
					
					if (playerPos != null && knightPos != null && playerPos.distanceTo(knightPos) <= 1)
					{
						String playerName = player.getName();
						if (playerName != null)
						{
							pickpocketers.add(playerName);
							if (currentSession != null)
							{
								currentSession.addPickpocketer(playerName);
							}
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// Track pickpocket hitsplats on the knight
		if (currentTarget != null && event.getActor() == currentTarget)
		{
			// Someone hit the knight (could be pickpocket or splash)
			// We primarily track via animation, so this is supplementary
		}
	}

	private boolean isSplashingConditionsMet()
	{
		// Check if all conditions are met to start tracking
		if (currentTarget == null)
		{
			return false;
		}
		if (knightTile1 == null || knightTile2 == null)
		{
			return false;
		}
		
		WorldPoint knightPos = currentTarget.getWorldLocation();
		if (knightPos == null)
		{
			return false;
		}
		
		boolean onValidTile = knightPos.equals(knightTile1) || knightPos.equals(knightTile2);
		return onValidTile;
	}
}
