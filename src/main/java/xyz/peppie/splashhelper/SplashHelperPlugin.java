package xyz.peppie.splashhelper;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.awt.TrayIcon;
import java.awt.Color;
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
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

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
	private Notifier notifier;

	@Getter
	private Instant timerEnd;

	private boolean hasNotified = false;
	
	@Getter
	private WorldPoint boundaryTile = null;
	@Getter
	private WorldPoint knightTile1 = null;
	@Getter
	private WorldPoint knightTile2 = null;
	private Actor currentTarget = null;
	private boolean boundaryNotified = false;
	
	// Movement tracking
	private WorldPoint lastNpcPosition = null;
	private int movementCount = 0;
	private Instant trackingStartTime = null;
	@Getter
	private double movementsPerMinute = 0.0;

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
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Splash Helper stopped!");
		overlayManager.remove(overlay);
		overlayManager.remove(boundaryOverlay);
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
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			if (config.enableWelcomeMessage())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Splash Helper is active!", null);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Check if timer has expired and send notification
		if (timerEnd != null && !hasNotified)
		{
			Duration remaining = Duration.between(Instant.now(), timerEnd);
			if (remaining.isNegative() || remaining.isZero())
			{
				sendNotification("Splash timer has expired!");
				hasNotified = true;
				log.info("Timer expired - notification sent");
			}
		}

		// Check if NPC is on boundary tile
		if (boundaryTile != null && currentTarget != null)
		{
			if (client.getTopLevelWorldView() == null)
			{
				return;
			}
			
			String configuredNpc = config.npcName();
			
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc != null && npc.getWorldLocation() != null)
				{
					String npcName = cleanNpcName(npc.getName());
					
					// Check if NPC matches configured name (if set)
					boolean nameMatches = configuredNpc == null || configuredNpc.isEmpty() || 
						(npcName != null && npcName.equalsIgnoreCase(configuredNpc));
					
					if (nameMatches)
					{
						if (npc.getWorldLocation().equals(boundaryTile))
						{
							String displayName = npcName != null ? npcName : "NPC";
							sendNotification(displayName + " reached boundary tile!");
							boundaryNotified = true;
							log.info("✓ NPC '{}' reached boundary at {}", displayName, boundaryTile);
							break;
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
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getTarget() != null && event.getTarget().getName() != null) 
		{
			String interactedNpcName = cleanNpcName(event.getTarget().getName());

			if (interactedNpcName.equalsIgnoreCase(config.npcName()))
			{
				currentTarget = event.getTarget();
				startTimer();	
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
				MenuEntry boundaryMenu = client.createMenuEntry(-1)
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
				MenuEntry tile1Menu = client.createMenuEntry(-2)
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
				MenuEntry tile2Menu = client.createMenuEntry(-3)
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

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
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

		// Check if this is the NPC we're tracking
		String configuredNpc = config.npcName();
		if (configuredNpc != null && !configuredNpc.isEmpty())
		{
			if (targetName.equalsIgnoreCase(configuredNpc))
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
}
