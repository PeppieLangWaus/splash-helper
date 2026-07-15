package xyz.peppie.splashhelper;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
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
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.config.ModifierlessKeybind;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import xyz.peppie.splashhelper.overlays.BoundaryTileOverlay;
import xyz.peppie.splashhelper.overlays.GriefPreventionOverlay;
import xyz.peppie.splashhelper.overlays.MagicBonusWarningOverlay;
import xyz.peppie.splashhelper.overlays.SafetyModeOverlay;
import xyz.peppie.splashhelper.overlays.SplashHelperOverlay;
import xyz.peppie.splashhelper.overlays.VisualNotificationOverlay;
import xyz.peppie.splashhelper.overlays.GuideSceneOverlay;
import xyz.peppie.splashhelper.overlays.GuideWidgetOverlay;
import xyz.peppie.splashhelper.overlays.GuidePanelOverlay;
import xyz.peppie.splashhelper.guide.GuideEngine;
import xyz.peppie.splashhelper.model.SplashSession;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.model.TimerAlertLevel;
import xyz.peppie.splashhelper.service.KnightDetector;
import xyz.peppie.splashhelper.service.NotificationService;
import xyz.peppie.splashhelper.service.PlayerTracker;
import xyz.peppie.splashhelper.service.RuneCalculator;
import xyz.peppie.splashhelper.service.SessionManager;
import xyz.peppie.splashhelper.service.SplashWebSocketClient;
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
	private GuideEngine guideEngine;

	@Inject
	private GuideSceneOverlay guideSceneOverlay;

	@Inject
	private GuideWidgetOverlay guideWidgetOverlay;

	@Inject
	private GuidePanelOverlay guidePanelOverlay;

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
	private MouseManager mouseManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SplashWebSocketClient webSocketClient;

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
	private boolean serverConnectPending = false;

	// Session tracking delegated to SessionManager service
	private Instant lastStatsSample = null;

	// Tracks the last "Timer started" message to avoid spamming identical messages
	private int lastTimerChatTick = -1;
	
	// Magic attack bonus tracking
	@Getter
	private boolean hasBadMagicBonus = false;
	private Instant lastBonusWarning = null;
	private Instant lastSafetyBlockMessage = null;

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

	/**
	 * Trigger visual notification overlay.
	 * Called by NotificationService when visual notifications are enabled.
	 */
	public void triggerVisualNotification()
	{
		if (config != null)
		{
			showVisualNotification = true;
			// No duration timeout - notification persists until user interaction resets the timer
		}
	}

	// Cached values for UI (updated on client thread)
	@Getter
	private volatile int cachedRemainingCasts = 0;
	// Spell the remaining-casts cache was computed with (drives Potential XP)
	@Getter
	private volatile SplashSpell cachedProjectionSpell = null;
	@Getter
	private volatile java.util.Set<Integer> cachedInfiniteRunes = new java.util.HashSet<>();
	@Getter
	private volatile java.util.List<int[]> cachedActualRuneUsage = new java.util.ArrayList<>();
	@Getter
	private volatile long cachedRuneCost = 0;

	// Visual notification state
	@Getter
	private boolean showVisualNotification = false;

	@Provides
	SplashHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SplashHelperConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Splash Helper started!");
		overlay.setPlugin(this, tileManager);
		overlayManager.add(overlay);
		
		// Set up visual notification callback
		notificationService.setVisualNotificationCallback(this::triggerVisualNotification);
		overlayManager.add(boundaryOverlay);
		overlayManager.add(visualNotificationOverlay);
		overlayManager.add(griefPreventionOverlay);
		overlayManager.add(magicBonusWarningOverlay);
		overlayManager.add(safetyModeOverlay);
		overlayManager.add(guideSceneOverlay);
		overlayManager.add(guideWidgetOverlay);
		overlayManager.add(guidePanelOverlay);

		// Register key listeners
		keyManager.registerKeyListener(safetyModeKeyListener);
		keyManager.registerKeyListener(guideKeyListener);
		keyManager.registerKeyListener(timerResetKeyListener);
		mouseManager.registerMouseListener(timerResetMouseListener);


		// Load safety mode state from config
		safetyModeEnabled = config.safetyModeEnabled();

		// Load persisted boundary/knight tiles
		tileManager.loadPersistedTiles();

		// Create statistics panel
		statisticsPanel = new SplashStatisticsPanel(this, config, itemManager, sessionManager, webSocketClient, clientThread);
		
		final BufferedImage icon = ImageUtil.loadImageResource(SplashHelperPlugin.class, "icon.png");
		
		navButton = NavigationButton.builder()
			.tooltip("Splash Statistics")
			.icon(icon)
			.priority(10)
			.panel(statisticsPanel)
			.build();
		
		clientToolbar.addNavigation(navButton);

		// Set up WebSocket session lifecycle callbacks
		sessionManager.setSessionStartedCallback(() -> {
			if (config.enableServerSync())
			{
				webSocketClient.sendSessionStart(sessionManager.getCurrentSession());
			}
		});
		sessionManager.setSessionFinalizedCallback(session -> {
			if (config.enableServerSync())
			{
				webSocketClient.sendSessionEnd(session);
			}
		});
		// Re-send SESSION_START after a WS reconnect so the server always has the
		// current session even if it missed the original SESSION_START (e.g. mid-session
		// network drop, or a re-auth triggered by requestSetupLink()).
		webSocketClient.setOnAuthSuccessCallback(() -> {
			clientThread.invokeLater(() -> {
				SplashSession session = sessionManager.getCurrentSession();
				if (session != null && config.enableServerSync())
				{
					webSocketClient.sendSessionStart(session);
				}
				// Retry any finalized sessions that never got confirmed synced (e.g. finalized
				// while offline), so they eventually become eligible for local pruning.
				if (config.enableServerSync())
				{
					webSocketClient.resyncUnsyncedSessions(sessionManager.getUnsyncedSessions());
				}
			});
		});

		// If server sync is enabled, always mark a connect as pending on startup.
		// requestServerConnection() will either connect immediately (if already logged in
		// with a valid player name) or defer to the next in-game tick via serverConnectPending.
		serverConnectPending = config.enableServerSync();
		requestServerConnection();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Splash Helper stopped!");
		overlayManager.remove(overlay);
		overlayManager.remove(boundaryOverlay);
		overlayManager.remove(visualNotificationOverlay);
		overlayManager.remove(griefPreventionOverlay);
		overlayManager.remove(magicBonusWarningOverlay);
		overlayManager.remove(safetyModeOverlay);
		overlayManager.remove(guideSceneOverlay);
		overlayManager.remove(guideWidgetOverlay);
		overlayManager.remove(guidePanelOverlay);

		// Unregister key listeners
		keyManager.unregisterKeyListener(safetyModeKeyListener);
		keyManager.unregisterKeyListener(guideKeyListener);
		keyManager.unregisterKeyListener(timerResetKeyListener);
		mouseManager.unregisterMouseListener(timerResetMouseListener);
		guideEngine.stop();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		if (statisticsPanel != null)
		{
			statisticsPanel.shutdown();
			statisticsPanel = null;
		}
		
		// Finalize any active session
		sessionManager.finalizeSession(false);
		// Clear the resumable-session pointer so that a disable/enable cycle does not
		// resume the just-finalized session, which would create duplicate history entries.
		sessionManager.clearResumableSession();
		
		// Disconnect WebSocket (don't shutdown — singleton survives plugin disable/enable)
		serverConnectPending = false;
		webSocketClient.disconnect();
		
		timerEnd = null;
		hasNotified = false;
		lastTimerChatTick = -1;
		tileManager.reset();
		lastStatsSample = null;
		playerTracker.reset();
		showVisualNotification = false;
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

			// Connect to WS server when the player finishes logging in or hopping.
			// Include LOADING since RuneLite sometimes transitions through it between
			// LOGGING_IN and LOGGED_IN, which would otherwise miss the trigger.
			if (config.enableServerSync() &&
				(previousGameState == GameState.LOGIN_SCREEN ||
				 previousGameState == GameState.HOPPING ||
				 previousGameState == GameState.LOGGING_IN ||
				 previousGameState == GameState.LOADING))
			{
				requestServerConnection();
			}
		}

		// Finalize any active session before disconnecting or hopping
		// so SESSION_END is sent while the WebSocket is still open.
		if (currentState == GameState.HOPPING || currentState == GameState.LOGIN_SCREEN)
		{
			sessionManager.finalizeSession(true);
			timerEnd = null;
			hasNotified = false;
			lastTimerChatTick = -1;
			tileManager.resetEscapedState();
		}

		if (currentState == GameState.LOGIN_SCREEN)
		{
			serverConnectPending = false;
			webSocketClient.disconnect();
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

		// Reconnect or disconnect when server sync settings change
		if (event.getKey().equals("enableServerSync") || event.getKey().equals("serverUrl"))
		{
			if (config.enableServerSync())
			{
				requestServerConnection();
			}
			else
			{
				serverConnectPending = false;
				webSocketClient.disconnect();
			}
		}
	}

	private void requestServerConnection()
	{
		if (!config.enableServerSync())
		{
			serverConnectPending = false;
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			// Not logged in yet — leave the pending flag as-is so that the next
			// game tick (which only fires while in-game) can retry.
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getName() == null || localPlayer.getName().trim().isEmpty())
		{
			serverConnectPending = true;
			return;
		}

		serverConnectPending = false;
		webSocketClient.connect(localPlayer.getName());
	}

	private void attemptPendingServerConnection()
	{
		if (!serverConnectPending || webSocketClient.isConnected())
		{
			return;
		}

		requestServerConnection();
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
		// getLocalPlayer() can still be null on the tick GameStateChanged reports
		// LOGGED_IN, so re-check here every tick; this is a no-op once the right
		// user's history is already loaded.
		Player tickLocalPlayer = client.getLocalPlayer();
		if (tickLocalPlayer != null)
		{
			sessionManager.ensureHistoryLoadedForUser(tickLocalPlayer.getName());
		}

		attemptPendingServerConnection();
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

		guideEngine.onGameTick();

		updateSessionStatistics();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		guideEngine.onHitsplat(event);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		guideEngine.onGraphicChanged(event);
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
			lastTimerChatTick = -1;
			sessionManager.finalizeSession(false);
		}
	}

	/**
	 * Toggle the sticky knight setup guide on/off.
	 */
	private void toggleGuide()
	{
		if (guideEngine.isStartedOrAck())
		{
			guideEngine.stop();
			clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=ff0000>Sticky knight guide stopped</col>", null));
		}
		else
		{
			guideEngine.start();
		}
	}

	/**
	 * Key listener for the sticky knight guide (start/stop, next/skip, back).
	 */
	private final KeyListener guideKeyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (config.guideStartHotkey().matches(e))
			{
				toggleGuide();
				e.consume();
			}
			else if (config.guideNextHotkey().matches(e))
			{
				guideEngine.next();
				e.consume();
			}
			else if (config.guideBackHotkey().matches(e))
			{
				guideEngine.back();
				e.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	@Subscribe
	@SuppressWarnings("deprecation")
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();

		// While the guide is on an Entangle step, only allow casting on the knight so the
		// spell can't be miscast on the alt or another entity behind it.
		if (guideEngine.restrictEntangleToKnight())
		{
			java.util.List<MenuEntry> kept = new java.util.ArrayList<>();
			for (MenuEntry entry : entries)
			{
				MenuAction action = entry.getType();
				boolean spellOnTarget =
					action == MenuAction.WIDGET_TARGET_ON_NPC ||
					action == MenuAction.WIDGET_TARGET_ON_PLAYER ||
					action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT ||
					action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM ||
					action == MenuAction.WIDGET_TARGET_ON_WIDGET;

				if (spellOnTarget)
				{
					String targetName = cleanNpcName(entry.getTarget());
					if (action == MenuAction.WIDGET_TARGET_ON_NPC &&
						targetName != null && targetName.equalsIgnoreCase("Knight of Ardougne"))
					{
						kept.add(entry);
					}
					// otherwise drop it — not a valid Entangle target during the guide
				}
				else
				{
					kept.add(entry);
				}
			}

			if (kept.size() < entries.length)
			{
				client.setMenuEntries(kept.toArray(new MenuEntry[0]));
				entries = client.getMenuEntries();
			}
		}

		// Note: a bad magic bonus only surfaces a warning (see onMenuOptionClicked); we
		// deliberately leave the "Attack" option in place so the click still goes through —
		// stripping it could cost the player aggro on the knight.

		// Add the tile submenus to the first Walk menu entry (to get tile coordinates)
		for (MenuEntry entry : entries)
		{
			if (entry.getType() == MenuAction.WALK)
			{
				// Sticky Knight Guide start/stop entry
				String guideOption = guideEngine.isStartedOrAck() ? "Stop" : "Start";
				client.createMenuEntry(1)
					.setOption(guideOption + " Sticky Knight Guide")
					.setTarget("")
					.setType(MenuAction.RUNELITE)
					.onClick(me -> toggleGuide());

				addTileSubmenu(entry, 1, "Knight Boundary", tileManager.getBoundaryTile(),
					this::onBoundarySetClick, this::onBoundaryUnsetClick, this::onBoundaryColorClick);
				addTileSubmenu(entry, 2, "Knight Tile 1", tileManager.getKnightTile1(),
					this::onKnightTile1SetClick, this::onKnightTile1UnsetClick, this::onKnightTile1ColorClick);
				addTileSubmenu(entry, 3, "Knight Tile 2", tileManager.getKnightTile2(),
					this::onKnightTile2SetClick, this::onKnightTile2UnsetClick, this::onKnightTile2ColorClick);

				// Only add it once
				break;
			}
		}
	}

	/**
	 * Add a "Set"/"Unset" + "Color" right-click submenu for a tile marker to the
	 * given Walk menu entry. Shows "Set" when the tile is unset and "Unset" when
	 * it is already set.
	 */
	private void addTileSubmenu(MenuEntry walkEntry, int position, String label, WorldPoint currentTile,
		java.util.function.Consumer<MenuEntry> onSet,
		java.util.function.Consumer<MenuEntry> onUnset,
		java.util.function.Consumer<MenuEntry> onColor)
	{
		MenuEntry mainMenu = client.createMenuEntry(position)
			.setOption(label)
			.setTarget("")
			.setType(MenuAction.RUNELITE);

		Menu submenu = mainMenu.createSubMenu();

		// Show "Set" when unset, "Unset" when already set
		submenu.createMenuEntry(-1)
			.setOption(currentTile == null ? "Set" : "Unset")
			.setType(MenuAction.RUNELITE)
			.setParam0(walkEntry.getParam0())
			.setParam1(walkEntry.getParam1())
			.onClick(currentTile == null ? onSet : onUnset);

		submenu.createMenuEntry(-1)
			.setOption("Color")
			.setType(MenuAction.RUNELITE)
			.setParam0(walkEntry.getParam0())
			.setParam1(walkEntry.getParam1())
			.onClick(onColor);
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
			log.debug("✓ Boundary tile successfully set to: {}", location);
			notificationService.sendNotification("Boundary tile set at: " + location.getX() + ", " + location.getY());
		}
	}

	private void onBoundaryUnsetClick(MenuEntry entry)
	{
		tileManager.unsetBoundaryTile();
		log.debug("✓ Boundary tile unset");
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
			log.debug("✓ Knight Tile 1 successfully set to: {}", location);
			notificationService.sendNotification("Knight Tile 1 set at: " + location.getX() + ", " + location.getY());
		}
	}

	private void onKnightTile1UnsetClick(MenuEntry entry)
	{
		tileManager.unsetKnightTile1();
		log.debug("✓ Knight Tile 1 unset");
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
			log.debug("✓ Knight Tile 2 successfully set to: {}", location);
			notificationService.sendNotification("Knight Tile 2 set at: " + location.getX() + ", " + location.getY());
		}
	}

	private void onKnightTile2UnsetClick(MenuEntry entry)
	{
		tileManager.unsetKnightTile2();
		log.debug("✓ Knight Tile 2 unset");
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
		// Any client interaction dismisses the visual notification
		showVisualNotification = false;

		// Safety mode action filtering
		if (safetyModeEnabled && shouldBlockAction(event))
		{
			event.consume();
			// Broad blocking can fire on rapid misclicks, so throttle the notice.
			Instant now = Instant.now();
			if (lastSafetyBlockMessage == null || Duration.between(lastSafetyBlockMessage, now).getSeconds() >= 2)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=ff0000>Safety Mode: This action is blocked while safety mode is enabled.</col>", null);
				lastSafetyBlockMessage = now;
			}
			return;
		}

		// Any un-blocked left/right click resets OSRS's combat idle timer, so
		// mirror it on the splash timer (only while it is running; a stopped
		// timer is only restarted by clicking the knight, handled below).
		resetTimer();

		// When hasEscaped is true and player interacts, mute notifications until knight returns
		if (tileManager.isHasEscaped() && !notificationService.areNotificationsMuted())
		{
			// Any click interaction while escaped mutes notifications
			if (event.getMenuAction() != MenuAction.CANCEL &&
				event.getMenuAction() != MenuAction.WALK)
			{
				notificationService.muteNotifications();
				log.debug("Notifications muted - player interacted while knight escaped");
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
				// Warn — but don't block — if attacking with a bad magic bonus. Consuming the
				// click could cost us aggro in some cases, so we only surface the warning and
				// let the attack proceed.
				String menuOption = event.getMenuOption();
				if (menuOption != null && menuOption.equalsIgnoreCase("Attack") && hasBadMagicBonus)
				{
					Instant now = Instant.now();
					if (lastBonusWarning == null || Duration.between(lastBonusWarning, now).getSeconds() > 5)
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
							"<col=ff0000>Warning: Your magic attack bonus is too high for splashing! (Need -64 or lower)</col>", null);
						lastBonusWarning = now;
					}
				}

				// Track the knight this session is engaged with so its type
				// (sticky/normal) can be detected from the very first click.
				Actor clickedNpc = event.getMenuEntry().getNpc();
				if (clickedNpc != null)
				{
					tileManager.setCurrentTarget(clickedNpc);
				}

				// Restart timer on user click (works even after timer has expired)
				startTimer();
				log.debug("Timer restarted by clicking knight");
			}
		}
	}

	private void startTimer()
	{
		int durationMinutes = config.timerDuration();
		timerEnd = Instant.now().plus(Duration.ofMinutes(durationMinutes));
		hasNotified = false;
		showVisualNotification = false;

		log.debug("Timer started for {} minutes", durationMinutes);

		if (config.enableWelcomeMessage())
		{
			String timerMsg = String.format("Timer started: %d minutes", durationMinutes);
			int currentTick = client.getTickCount();
			if (currentTick != lastTimerChatTick)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", timerMsg, null);
				lastTimerChatTick = currentTick;
			}
		}

		// Start session when timer starts (player engaged with target)
		if (config.enableStatistics() && !sessionManager.hasActiveSession())
		{
			startSession(config.selectedSpell());
		}
	}

	/**
	 * Silently extend a running timer back to its full duration.
	 * Mirrors how OSRS's combat idle timer is reset by clicks, typing, and
	 * keyboard camera movement — no chat message, no session churn. Does
	 * nothing when the timer is not running (a stopped combat timer can only
	 * be restarted by re-engaging the target).
	 */
	private void resetTimer()
	{
		if (timerEnd == null)
		{
			return;
		}
		timerEnd = Instant.now().plus(Duration.ofMinutes(config.timerDuration()));
		hasNotified = false;
		showVisualNotification = false;
	}

	/**
	 * Alert level of the running timer against the configured thresholds.
	 * Thresholds are clamped so that critical <= warning <= timer duration.
	 */
	public TimerAlertLevel getTimerAlertLevel()
	{
		if (timerEnd == null)
		{
			// After expiry the timer is cleared; the lingering "expired" alert
			// is carried by showVisualNotification until the user interacts.
			return TimerAlertLevel.NONE;
		}

		long secondsLeft = Duration.between(Instant.now(), timerEnd).getSeconds();
		if (secondsLeft <= 0)
		{
			return TimerAlertLevel.EXPIRED;
		}

		int warningMinutes = Math.min(config.timerWarningMinutes(), config.timerDuration());
		int criticalMinutes = Math.min(config.timerCriticalMinutes(), warningMinutes);

		if (secondsLeft <= criticalMinutes * 60L)
		{
			return TimerAlertLevel.CRITICAL;
		}
		if (secondsLeft <= warningMinutes * 60L)
		{
			return TimerAlertLevel.WARNING;
		}
		return TimerAlertLevel.NORMAL;
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

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return false;
		}

		int magicAttackBonus = 0;

		// Sum up magic attack bonus from all equipment slots
		for (Item item : equipment.getItems())
		{
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
		sessionManager.finalizeSession(false);

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		String playerName = localPlayer.getName();
		int world = client.getWorld();
		boolean stickyKnight = isStickyKnight();

		sessionManager.startSession(playerName, spell, timerEnd, world, stickyKnight);

		// Snapshot the cast projection immediately so the statistics panel has
		// meaningful values from the very first tick of the session.
		cachedRemainingCasts = getRemainingCastsForCurrentSpell();
		cachedProjectionSpell = getCurrentSpell();

		lastStatsSample = Instant.now();

		log.debug("Splash session started for {} using {}", playerName, spell);
	}

	
	private void sampleSessionStatistics()
	{
		SplashSession session = sessionManager.getCurrentSession();
		if (session == null || !session.isActive())
		{
			return;
		}

		if (tileManager.getCurrentTarget() != null)
		{
			sessionManager.updateStickyKnight(isStickyKnight());
		}

		// Update magic XP
		session.setCurrentMagicXp(client.getSkillExperience(Skill.MAGIC));

		// Update rune count
		SplashSpell spell = session.getSpell();
		if (spell != null)
		{
			session.setCurrentRuneCount(runeCalculator.getRemainingCasts(spell));
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

		if (actor == client.getLocalPlayer())
		{
			int animationId = actor.getAnimation();
			
			boolean isCasting = (animationId >= 711 && animationId <= 717) ||  // Standard spells
							   (animationId >= 1162 && animationId <= 1167) || // Ancient spells
							   (animationId >= 1978 && animationId <= 1993) || // Lunar spells
							   (Constants.isStandardSpellAnimation(animationId));
			
			if (isCasting)
			{
				Actor interactingTarget = client.getLocalPlayer().getInteracting();
				
				if (interactingTarget != null && interactingTarget.getName() != null)
				{
					String targetName = cleanNpcName(interactingTarget.getName());
					String configuredNpc = config.targetNpc().getNpcName();
					
					if (configuredNpc != null && !configuredNpc.isEmpty() &&
						targetName != null && targetName.equalsIgnoreCase(configuredNpc))
					{
						tileManager.setCurrentTarget(interactingTarget);

						if (timerEnd == null && !hasNotified)
						{
							startTimer();
							log.debug("Timer and session started by detecting spell animation on {} (animation: {})", targetName, animationId);
						}
						else if (!sessionManager.hasActiveSession() && config.enableStatistics())
						{
							startSession(config.selectedSpell());
							log.debug("New session started (timer still active) for {} (animation: {})", targetName, animationId);
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
			if (animationId == Constants.ANIMATION_PICKPOCKET)
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
			// Count nearby players that die during an active session
			if (animationId == AnimationID.HUMAN_DEATH && sessionManager.hasActiveSession())
			{
				Player localPlayer = client.getLocalPlayer();
				WorldPoint playerPos = player.getWorldLocation();
				WorldPoint localPos = localPlayer != null ? localPlayer.getWorldLocation() : null;

				if (playerPos != null && localPos != null
					&& localPos.distanceTo(playerPos) <= config.playerCountRadius())
				{
					sessionManager.recordPlayerDeath();
					log.debug("Nearby player death detected: {}", player.getName());
				}
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
				timerEnd = null;
				lastTimerChatTick = -1;
				log.debug("Timer expired - notification sent, timer cleared");
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
			
			// Update cached values on client thread for UI access. Remaining
			// casts (and the Hours Remaining / Potential XP values derived from
			// it) always reflect the live value during a session; when idle,
			// the last meaningful value is retained instead of snapping to 0
			// (e.g. when the configured spell doesn't match carried runes).
			int computedRemaining = getRemainingCastsForCurrentSpell();
			if (sessionManager.hasActiveSession() || computedRemaining > 0)
			{
				cachedRemainingCasts = computedRemaining;
				cachedProjectionSpell = getCurrentSpell();
			}
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
				
				// Check for session timeout via service (session is independent of timer)
				if (sessionManager.checkSessionTimeout())
				{
					timerEnd = null;
					hasNotified = false;
					lastTimerChatTick = -1;
				}
				
				if (lastStatsSample == null || 
					Duration.between(lastStatsSample, now).getSeconds() >= config.statisticsInterval())
				{
					sampleSessionStatistics();
					lastStatsSample = now;

					if (config.enableServerSync())
					{
						webSocketClient.sendSessionUpdate(sessionManager.getCurrentSession());
					}
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
	 *
	 * Safety mode locks the player into the splash spot, so it works as an
	 * allowlist: everything is blocked except the few interactions that cannot
	 * move the player, change equipment/stats or otherwise interrupt the
	 * session. This blocks walking (clicking the ground), clicking objects
	 * (ladders, doors, bank stalls, deposit boxes, ...), interacting with NPCs,
	 * player options and chat-box menus (following, trading, reporting, ...),
	 * casting spells from the spellbook (teleports, ...), equipping items,
	 * changing combat options, and opening interfaces such as the POH options
	 * or rune pouch. Only Examine, Cancel, selecting an item with Use, and
	 * switching sidebar tabs remain available (so items can still be
	 * examined, menus closed, an item picked up for use, and the interface
	 * panel switched); actually applying a selected item to another
	 * item/object/NPC/player/ground item stays blocked no matter what the
	 * target is (e.g. using an item on the Ardougne knight would otherwise
	 * walk the player to it), except clicking the selected item itself
	 * again, which cancels the selection rather than applying it and so
	 * stays allowed. Switching to the Grouping tab (chat channel / your clan
	 * / view another clan / grouping) specifically also stays blocked. This
	 * plugin's own right-click entries also remain available. Continuous
	 * autocast splashing is unaffected because it does not fire menu clicks.
	 */
	private boolean shouldBlockAction(MenuOptionClicked event)
	{
		return !isAllowedInSafetyMode(event);
	}

	/**
	 * The short allowlist of interactions permitted while safety mode is on.
	 * See {@link #shouldBlockAction}.
	 */
	private boolean isAllowedInSafetyMode(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();

		// This plugin's own (and other plugins'/overlays') right-click entries,
		// which are dispatched via their own onClick handlers.
		if (action != null && action.name().startsWith("RUNELITE"))
		{
			return true;
		}

		// Closing/cancelling an open right-click menu.
		if (action == MenuAction.CANCEL)
		{
			return true;
		}

		// Harmless options are always allowed: for items this leaves
		// Use/Examine/Cancel and blocks equipping, unequipping, dropping, etc.
		String option = event.getMenuOption();
		String lower = option == null ? "" : option.toLowerCase();
		if (lower.equals("examine") || lower.equals("cancel"))
		{
			return true;
		}
		// "Use" itself only selects the item (harmless); applying it to another
		// item, an object, an NPC, a player or a ground item is a distinct menu
		// action and stays blocked, since that's where the actual effect happens.
		if (lower.equals("use") && !isUseOnTargetAction(action))
		{
			return true;
		}
		if (isUseOnTargetAction(action))
		{
			// Clicking the already-selected item again cancels the selection
			// instead of applying it; that click fires the exact same menu
			// action as a real "use on X", so it must be allowed here or a
			// selected item could never be deselected while safety mode is on.
			Widget selectedWidget = client.getSelectedWidget();
			Widget targetWidget = event.getMenuEntry().getWidget();
			if (selectedWidget != null && targetWidget != null
				&& selectedWidget.getIndex() == targetWidget.getIndex()
				&& selectedWidget.getItemId() == targetWidget.getItemId())
			{
				return true;
			}

			// Applying a selected item to anything else - another item, an
			// object, an NPC (e.g. walking to and using it on the Ardougne
			// knight), a player or a ground item - actually performs the
			// action, so it stays blocked no matter what the target is.
			return false;
		}

		// Attacking/casting at a knight is allowed only for the exact NPC this
		// session was started on. This stops a griefer's second knight — which
		// produces a duplicate "Attack Knight of Ardougne" entry the splasher
		// can't tell apart — from being clicked by mistake.
		NPC clickedNpc = event.getMenuEntry().getNpc();
		if (clickedNpc != null)
		{
			NPC sessionKnight = tileManager.getSessionKnight();
			return sessionKnight != null
				&& clickedNpc.getIndex() == sessionKnight.getIndex();
		}

		// Switching between sidebar tabs (inventory, equipment, prayer, magic,
		// stats, quests, combat, settings, emotes, music, logout, ...) is
		// harmless on its own and stays allowed; only the Grouping tab (chat
		// channel / your clan / view another clan / grouping) stays blocked,
		// since it opens social features that could distract from splashing.
		Widget widget = event.getMenuEntry().getWidget();
		if (widget != null)
		{
			int widgetId = widget.getId();
			if (GROUPING_TAB_WIDGET_IDS.contains(widgetId))
			{
				return false;
			}
			if (SIDEBAR_TAB_WIDGET_IDS.contains(widgetId))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Widget IDs for the sidebar tab-switch buttons ("stones" and their icon
	 * overlays), across the fixed, resizable-modern and resizable-classic
	 * layouts. Excludes the Grouping tab (slot 7); see
	 * {@link #GROUPING_TAB_WIDGET_IDS}.
	 */
	private static final Set<Integer> SIDEBAR_TAB_WIDGET_IDS = new HashSet<>(Arrays.asList(
		// Fixed viewport
		InterfaceID.Toplevel.STONE0, InterfaceID.Toplevel.STONE1, InterfaceID.Toplevel.STONE2,
		InterfaceID.Toplevel.STONE3, InterfaceID.Toplevel.STONE4, InterfaceID.Toplevel.STONE5,
		InterfaceID.Toplevel.STONE6, InterfaceID.Toplevel.STONE8, InterfaceID.Toplevel.STONE9,
		InterfaceID.Toplevel.STONE10, InterfaceID.Toplevel.STONE11, InterfaceID.Toplevel.STONE12,
		InterfaceID.Toplevel.STONE13,
		InterfaceID.Toplevel.ICON0, InterfaceID.Toplevel.ICON1, InterfaceID.Toplevel.ICON2,
		InterfaceID.Toplevel.ICON3, InterfaceID.Toplevel.ICON4, InterfaceID.Toplevel.ICON5,
		InterfaceID.Toplevel.ICON6, InterfaceID.Toplevel.ICON8, InterfaceID.Toplevel.ICON9,
		InterfaceID.Toplevel.ICON10, InterfaceID.Toplevel.ICON11, InterfaceID.Toplevel.ICON12,
		InterfaceID.Toplevel.ICON13,

		// Resizable (modern) viewport
		InterfaceID.ToplevelOsrsStretch.STONE0, InterfaceID.ToplevelOsrsStretch.STONE1,
		InterfaceID.ToplevelOsrsStretch.STONE2, InterfaceID.ToplevelOsrsStretch.STONE3,
		InterfaceID.ToplevelOsrsStretch.STONE4, InterfaceID.ToplevelOsrsStretch.STONE5,
		InterfaceID.ToplevelOsrsStretch.STONE6, InterfaceID.ToplevelOsrsStretch.STONE8,
		InterfaceID.ToplevelOsrsStretch.STONE9, InterfaceID.ToplevelOsrsStretch.STONE10,
		InterfaceID.ToplevelOsrsStretch.STONE11, InterfaceID.ToplevelOsrsStretch.STONE12,
		InterfaceID.ToplevelOsrsStretch.STONE13,
		InterfaceID.ToplevelOsrsStretch.ICON0, InterfaceID.ToplevelOsrsStretch.ICON1,
		InterfaceID.ToplevelOsrsStretch.ICON2, InterfaceID.ToplevelOsrsStretch.ICON3,
		InterfaceID.ToplevelOsrsStretch.ICON4, InterfaceID.ToplevelOsrsStretch.ICON5,
		InterfaceID.ToplevelOsrsStretch.ICON6, InterfaceID.ToplevelOsrsStretch.ICON8,
		InterfaceID.ToplevelOsrsStretch.ICON9, InterfaceID.ToplevelOsrsStretch.ICON10,
		InterfaceID.ToplevelOsrsStretch.ICON11, InterfaceID.ToplevelOsrsStretch.ICON12,
		InterfaceID.ToplevelOsrsStretch.ICON13,

		// Resizable (classic / bottom-line) viewport
		InterfaceID.ToplevelPreEoc.STONE0, InterfaceID.ToplevelPreEoc.STONE1,
		InterfaceID.ToplevelPreEoc.STONE2, InterfaceID.ToplevelPreEoc.STONE3,
		InterfaceID.ToplevelPreEoc.STONE4, InterfaceID.ToplevelPreEoc.STONE5,
		InterfaceID.ToplevelPreEoc.STONE6, InterfaceID.ToplevelPreEoc.STONE8,
		InterfaceID.ToplevelPreEoc.STONE9, InterfaceID.ToplevelPreEoc.STONE10,
		InterfaceID.ToplevelPreEoc.STONE11, InterfaceID.ToplevelPreEoc.STONE12,
		InterfaceID.ToplevelPreEoc.STONE13,
		InterfaceID.ToplevelPreEoc.ICON0, InterfaceID.ToplevelPreEoc.ICON1,
		InterfaceID.ToplevelPreEoc.ICON2, InterfaceID.ToplevelPreEoc.ICON3,
		InterfaceID.ToplevelPreEoc.ICON4, InterfaceID.ToplevelPreEoc.ICON5,
		InterfaceID.ToplevelPreEoc.ICON6, InterfaceID.ToplevelPreEoc.ICON8,
		InterfaceID.ToplevelPreEoc.ICON9, InterfaceID.ToplevelPreEoc.ICON10,
		InterfaceID.ToplevelPreEoc.ICON11, InterfaceID.ToplevelPreEoc.ICON12,
		InterfaceID.ToplevelPreEoc.ICON13
	));

	/**
	 * Widget IDs for the Grouping tab (slot 7 — chat channel / your clan /
	 * view another clan / grouping), across all three layouts. Stays blocked
	 * in safety mode even though other sidebar tabs are allowed.
	 */
	private static final Set<Integer> GROUPING_TAB_WIDGET_IDS = new HashSet<>(Arrays.asList(
		InterfaceID.Toplevel.STONE7, InterfaceID.Toplevel.ICON7,
		InterfaceID.ToplevelOsrsStretch.STONE7, InterfaceID.ToplevelOsrsStretch.ICON7,
		InterfaceID.ToplevelPreEoc.STONE7, InterfaceID.ToplevelPreEoc.ICON7
	));

	/**
	 * True for the menu actions fired when an already-selected "Use" item is
	 * applied to a target (another item, a game object, an NPC, a player or a
	 * ground item), as opposed to {@link MenuAction#WIDGET_TARGET} which just
	 * selects the item and does nothing to the world yet.
	 */
	@SuppressWarnings("deprecation")
	private boolean isUseOnTargetAction(MenuAction action)
	{
		switch (action)
		{
			case WIDGET_TARGET_ON_WIDGET:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_NPC:
			case WIDGET_TARGET_ON_PLAYER:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case ITEM_USE_ON_ITEM:
			case WIDGET_USE_ON_ITEM:
			case ITEM_USE_ON_GAME_OBJECT:
			case ITEM_USE_ON_NPC:
			case ITEM_USE_ON_PLAYER:
			case ITEM_USE_ON_GROUND_ITEM:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Toggle safety mode on/off.
	 */
	public void toggleSafetyMode()
	{
		safetyModeEnabled = !safetyModeEnabled;
		log.debug("Safety mode toggled: {}", safetyModeEnabled ? "ON" : "OFF");
		
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

	/**
	 * Global key listener that mirrors OSRS's combat idle timer: typing and
	 * keyboard camera movement (arrow keys) reset it, so they reset the splash
	 * timer too. Observation only — events are never consumed or synthesized.
	 */
	private final KeyListener timerResetKeyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			// The safety-mode hotkey is consumed by safetyModeKeyListener and
			// never reaches the game, so it must not reset the timer.
			if (config.safetyModeHotkey().matches(e))
			{
				return;
			}
			// resetTimer() no-ops when the timer is not running, so idle
			// typing (e.g. in the bank) never starts a timer or session.
			clientThread.invokeLater(SplashHelperPlugin.this::resetTimer);
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};

	/**
	 * Global mouse listener that mirrors OSRS's combat idle timer: right-clicking
	 * anywhere on the game window (even just to open a context menu, without
	 * selecting an option) resets it, so it resets the splash timer too.
	 * Observation only — events are never consumed.
	 */
	private final MouseListener timerResetMouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			if (e.getButton() == MouseEvent.BUTTON3)
			{
				// resetTimer() no-ops when the timer is not running, so idle
				// right-clicks (e.g. in the bank) never start a timer or session.
				clientThread.invokeLater(SplashHelperPlugin.this::resetTimer);
			}
			return e;
		}
	};
}
