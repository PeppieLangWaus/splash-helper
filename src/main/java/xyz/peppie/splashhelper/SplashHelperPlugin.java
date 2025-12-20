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
	private static final int SESSION_TIMEOUT_SECONDS = 10;
	
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

	// Combination rune IDs
	private static final int MIST_RUNE = 4695;   // Air + Water
	private static final int DUST_RUNE = 4696;   // Air + Earth
	private static final int MUD_RUNE = 4698;    // Water + Earth
	private static final int SMOKE_RUNE = 4697;  // Air + Fire
	private static final int STEAM_RUNE = 4694;  // Water + Fire
	private static final int LAVA_RUNE = 4699;   // Earth + Fire

	// Rune pouch IDs
	private static final int RUNE_POUCH = 12791;
	private static final int RUNE_POUCH_DIVINE = 27281;

	/**
	 * Get remaining casts for the current spell (from session or config).
	 * Accounts for combination runes, rune pouch, and equipped staves.
	 */
	public int getRemainingCastsForCurrentSpell()
	{
		SplashSpell spell = null;
		
		// Try to get spell from current session first
		if (currentSession != null && currentSession.getSpell() != null)
		{
			spell = currentSession.getSpell();
		}
		else
		{
			// Fall back to config spell
			spell = config.selectedSpell();
		}
		
		return countLimitingRunesAdvanced(spell);
	}

	/**
	 * Count limiting runes with support for combination runes and rune pouch.
	 */
	private int countLimitingRunesAdvanced(SplashSpell spell)
	{
		if (spell == null)
		{
			return 0;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}

		// Check equipment for staves that provide infinite runes
		java.util.Set<Integer> infiniteRunes = getInfiniteRunesFromEquipment();

		// Build a map of rune type -> count (including combo runes and pouch)
		java.util.Map<Integer, Integer> runeCounts = new java.util.HashMap<>();
		
		// Count inventory runes
		for (Item item : inventory.getItems())
		{
			int id = item.getId();
			int qty = item.getQuantity();
			
			// Regular runes
			if (isBasicRune(id))
			{
				runeCounts.merge(id, qty, Integer::sum);
			}
			
			// Combination runes - add to both element types
			addCombinationRuneCounts(runeCounts, id, qty);
			
			// Check for rune pouch
			if (id == RUNE_POUCH || id == RUNE_POUCH_DIVINE)
			{
				addRunePouchCounts(runeCounts);
			}
		}

		int minCasts = Integer.MAX_VALUE;
		
		for (SplashSpell.RuneCost cost : spell.getRuneCosts())
		{
			// If staff provides infinite runes of this type, skip it
			if (infiniteRunes.contains(cost.getItemId()))
			{
				continue;
			}
			
			int runeCount = runeCounts.getOrDefault(cost.getItemId(), 0);
			int castsWithThisRune = runeCount / cost.getAmount();
			minCasts = Math.min(minCasts, castsWithThisRune);
		}

		return minCasts == Integer.MAX_VALUE ? 0 : minCasts;
	}

	private boolean isBasicRune(int id)
	{
		return id == SplashSpell.ItemID.AIR_RUNE ||
			id == SplashSpell.ItemID.WATER_RUNE ||
			id == SplashSpell.ItemID.EARTH_RUNE ||
			id == SplashSpell.ItemID.FIRE_RUNE ||
			id == SplashSpell.ItemID.MIND_RUNE ||
			id == SplashSpell.ItemID.BODY_RUNE ||
			id == SplashSpell.ItemID.CHAOS_RUNE ||
			id == SplashSpell.ItemID.DEATH_RUNE ||
			id == SplashSpell.ItemID.BLOOD_RUNE ||
			id == SplashSpell.ItemID.WRATH_RUNE;
	}

	/**
	 * Get the actual runes being consumed for the current spell.
	 * Detects combination runes in inventory and excludes infinite runes from staves.
	 * Returns list of int[2] arrays: [itemId, amountPerCast]
	 */
	private java.util.List<int[]> getActualRuneUsage()
	{
		java.util.List<int[]> result = new java.util.ArrayList<>();
		
		SplashSpell spell = null;
		if (currentSession != null && currentSession.getSpell() != null)
		{
			spell = currentSession.getSpell();
		}
		else
		{
			spell = config.selectedSpell();
		}
		
		if (spell == null)
		{
			return result;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return result;
		}

		java.util.Set<Integer> infiniteRunes = getInfiniteRunesFromEquipment();
		
		// Track which combo runes we have in inventory
		java.util.Map<Integer, Integer> comboRunesInInventory = new java.util.HashMap<>();
		for (Item item : inventory.getItems())
		{
			int id = item.getId();
			if (isCombinationRune(id))
			{
				comboRunesInInventory.put(id, item.getQuantity());
			}
		}
		
		// Also check rune pouch for combo runes
		final int[] RUNE_POUCH_RUNE_VARBITS = {29, 1622, 1623};
		final int[] RUNE_POUCH_AMOUNT_VARBITS = {1624, 1625, 1626};
		for (int i = 0; i < 3; i++)
		{
			int runeId = runeIdFromVarbit(client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]));
			int amount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
			if (runeId > 0 && amount > 0 && isCombinationRune(runeId))
			{
				comboRunesInInventory.merge(runeId, amount, Integer::sum);
			}
		}
		
		// For each rune cost, determine actual rune being used
		for (SplashSpell.RuneCost cost : spell.getRuneCosts())
		{
			int requiredRuneId = cost.getItemId();
			
			// Skip if staff provides this rune infinitely
			if (infiniteRunes.contains(requiredRuneId))
			{
				continue;
			}
			
			// Check if a combination rune provides this element
			int comboRuneId = findCombinationRuneFor(requiredRuneId, comboRunesInInventory.keySet());
			if (comboRuneId > 0)
			{
				result.add(new int[]{comboRuneId, cost.getAmount()});
			}
			else
			{
				// Use regular rune
				result.add(new int[]{requiredRuneId, cost.getAmount()});
			}
		}
		
		// Deduplicate combo runes (if same combo rune provides multiple elements)
		return deduplicateRuneUsage(result);
	}

	private boolean isCombinationRune(int id)
	{
		return id == MIST_RUNE || id == DUST_RUNE || id == MUD_RUNE ||
			id == SMOKE_RUNE || id == STEAM_RUNE || id == LAVA_RUNE;
	}

	private int findCombinationRuneFor(int elementRuneId, java.util.Set<Integer> availableComboRunes)
	{
		for (int comboId : availableComboRunes)
		{
			if (combinationRuneProvides(comboId, elementRuneId))
			{
				return comboId;
			}
		}
		return -1;
	}

	private boolean combinationRuneProvides(int comboRuneId, int elementRuneId)
	{
		switch (comboRuneId)
		{
			case MIST_RUNE:
				return elementRuneId == SplashSpell.ItemID.AIR_RUNE || elementRuneId == SplashSpell.ItemID.WATER_RUNE;
			case DUST_RUNE:
				return elementRuneId == SplashSpell.ItemID.AIR_RUNE || elementRuneId == SplashSpell.ItemID.EARTH_RUNE;
			case MUD_RUNE:
				return elementRuneId == SplashSpell.ItemID.WATER_RUNE || elementRuneId == SplashSpell.ItemID.EARTH_RUNE;
			case SMOKE_RUNE:
				return elementRuneId == SplashSpell.ItemID.AIR_RUNE || elementRuneId == SplashSpell.ItemID.FIRE_RUNE;
			case STEAM_RUNE:
				return elementRuneId == SplashSpell.ItemID.WATER_RUNE || elementRuneId == SplashSpell.ItemID.FIRE_RUNE;
			case LAVA_RUNE:
				return elementRuneId == SplashSpell.ItemID.EARTH_RUNE || elementRuneId == SplashSpell.ItemID.FIRE_RUNE;
			default:
				return false;
		}
	}

	private java.util.List<int[]> deduplicateRuneUsage(java.util.List<int[]> runeUsage)
	{
		// If same rune appears multiple times (combo rune providing 2 elements), keep only one entry
		java.util.Map<Integer, Integer> seen = new java.util.LinkedHashMap<>();
		for (int[] entry : runeUsage)
		{
			int itemId = entry[0];
			int amount = entry[1];
			// For combo runes, they provide both elements per rune, so we only count once
			if (!seen.containsKey(itemId))
			{
				seen.put(itemId, amount);
			}
		}
		
		java.util.List<int[]> result = new java.util.ArrayList<>();
		for (java.util.Map.Entry<Integer, Integer> entry : seen.entrySet())
		{
			result.add(new int[]{entry.getKey(), entry.getValue()});
		}
		return result;
	}

	private void addCombinationRuneCounts(java.util.Map<Integer, Integer> runeCounts, int itemId, int quantity)
	{
		switch (itemId)
		{
			case MIST_RUNE: // Air + Water
				runeCounts.merge(SplashSpell.ItemID.AIR_RUNE, quantity, Integer::sum);
				runeCounts.merge(SplashSpell.ItemID.WATER_RUNE, quantity, Integer::sum);
				break;
			case DUST_RUNE: // Air + Earth
				runeCounts.merge(SplashSpell.ItemID.AIR_RUNE, quantity, Integer::sum);
				runeCounts.merge(SplashSpell.ItemID.EARTH_RUNE, quantity, Integer::sum);
				break;
			case MUD_RUNE: // Water + Earth
				runeCounts.merge(SplashSpell.ItemID.WATER_RUNE, quantity, Integer::sum);
				runeCounts.merge(SplashSpell.ItemID.EARTH_RUNE, quantity, Integer::sum);
				break;
			case SMOKE_RUNE: // Air + Fire
				runeCounts.merge(SplashSpell.ItemID.AIR_RUNE, quantity, Integer::sum);
				runeCounts.merge(SplashSpell.ItemID.FIRE_RUNE, quantity, Integer::sum);
				break;
			case STEAM_RUNE: // Water + Fire
				runeCounts.merge(SplashSpell.ItemID.WATER_RUNE, quantity, Integer::sum);
				runeCounts.merge(SplashSpell.ItemID.FIRE_RUNE, quantity, Integer::sum);
				break;
			case LAVA_RUNE: // Earth + Fire
				runeCounts.merge(SplashSpell.ItemID.EARTH_RUNE, quantity, Integer::sum);
				runeCounts.merge(SplashSpell.ItemID.FIRE_RUNE, quantity, Integer::sum);
				break;
		}
	}

	private void addRunePouchCounts(java.util.Map<Integer, Integer> runeCounts)
	{
		// Rune pouch contents are stored in varbit values
		// Varbit IDs for rune pouch slots
		final int[] RUNE_POUCH_RUNE_VARBITS = {29, 1622, 1623};
		final int[] RUNE_POUCH_AMOUNT_VARBITS = {1624, 1625, 1626};
		
		for (int i = 0; i < 3; i++)
		{
			int runeId = runeIdFromVarbit(client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]));
			int amount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
			
			if (runeId > 0 && amount > 0)
			{
				runeCounts.merge(runeId, amount, Integer::sum);
				// Also handle combination runes in pouch
				addCombinationRuneCounts(runeCounts, runeId, amount);
			}
		}
	}

	private int runeIdFromVarbit(int varbitValue)
	{
		// Varbit value to rune ID mapping
		switch (varbitValue)
		{
			case 1: return SplashSpell.ItemID.AIR_RUNE;
			case 2: return SplashSpell.ItemID.WATER_RUNE;
			case 3: return SplashSpell.ItemID.EARTH_RUNE;
			case 4: return SplashSpell.ItemID.FIRE_RUNE;
			case 5: return SplashSpell.ItemID.MIND_RUNE;
			case 6: return SplashSpell.ItemID.BODY_RUNE;
			case 7: return SplashSpell.ItemID.DEATH_RUNE;
			case 8: return 561; // Nature rune
			case 9: return SplashSpell.ItemID.CHAOS_RUNE;
			case 10: return 563; // Law rune
			case 11: return 564; // Cosmic rune
			case 12: return SplashSpell.ItemID.BLOOD_RUNE;
			case 13: return 566; // Soul rune
			case 14: return 9075; // Astral rune
			case 15: return MIST_RUNE;
			case 16: return MUD_RUNE;
			case 17: return DUST_RUNE;
			case 18: return LAVA_RUNE;
			case 19: return STEAM_RUNE;
			case 20: return SMOKE_RUNE;
			case 21: return SplashSpell.ItemID.WRATH_RUNE;
			default: return -1;
		}
	}

	private int countLimitingRunes(SplashSpell spell)
	{
		// Use the advanced method which includes combo runes and pouch
		return countLimitingRunesAdvanced(spell);
	}

	public java.util.Set<Integer> getInfiniteRunesFromEquipment()
	{
		java.util.Set<Integer> infiniteRunes = new java.util.HashSet<>();
		
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return infiniteRunes;
		}

		for (Item item : equipment.getItems())
		{
			int id = item.getId();
			
			// Air staves
			if (id == 1381 || id == 1397 || id == 11998 || // Staff of air, Air battlestaff, Mystic air staff
			    id == 11787 || id == 12795 || // Smoke battlestaff, Mystic smoke staff
			    id == 20736 || id == 20739 || // Dust battlestaff, Mystic dust staff
			    id == 6562 || id == 6563)     // Mist battlestaff, Mystic mist staff
			{
				infiniteRunes.add(SplashSpell.ItemID.AIR_RUNE);
			}
			
			// Water staves
			if (id == 1383 || id == 1395 || id == 11991 || // Staff of water, Water battlestaff, Mystic water staff
			    id == 11789 || id == 12797 || // Steam battlestaff, Mystic steam staff
			    id == 6562 || id == 6563 ||   // Mist battlestaff, Mystic mist staff
			    id == 6564 || id == 6565)     // Mud battlestaff, Mystic mud staff
			{
				infiniteRunes.add(SplashSpell.ItemID.WATER_RUNE);
			}
			
			// Earth staves
			if (id == 1385 || id == 1399 || id == 11994 || // Staff of earth, Earth battlestaff, Mystic earth staff
			    id == 20736 || id == 20739 || // Dust battlestaff, Mystic dust staff
			    id == 6564 || id == 6565 ||   // Mud battlestaff, Mystic mud staff
			    id == 3053 || id == 3054)     // Lava battlestaff, Mystic lava staff
			{
				infiniteRunes.add(SplashSpell.ItemID.EARTH_RUNE);
			}
			
			// Fire staves
			if (id == 1387 || id == 1393 || id == 11998 || // Staff of fire, Fire battlestaff, Mystic fire staff
			    id == 11787 || id == 12795 || // Smoke battlestaff, Mystic smoke staff
			    id == 11789 || id == 12797 || // Steam battlestaff, Mystic steam staff
			    id == 3053 || id == 3054)     // Lava battlestaff, Mystic lava staff
			{
				infiniteRunes.add(SplashSpell.ItemID.FIRE_RUNE);
			}
		}
		
		return infiniteRunes;
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
