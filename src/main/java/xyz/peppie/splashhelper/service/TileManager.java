package xyz.peppie.splashhelper.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.util.Constants;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;

/**
 * Service for managing tile-related functionality including boundary tiles, knight tiles, and movement tracking.
 */
@Slf4j
@Singleton
public class TileManager
{
	private static final String CONFIG_GROUP = "splashhelper";
	private static final String CONFIG_BOUNDARY_TILE = "boundaryTile";
	private static final String CONFIG_KNIGHT_TILE = "knightTile";
	private static final String CONFIG_STACK_TILE = "stackTile";

	// Pre-rename config keys ("Knight Tile 1/2"), migrated to the keys above on startup.
	private static final String LEGACY_CONFIG_KNIGHT_TILE1 = "knightTile1";
	private static final String LEGACY_CONFIG_KNIGHT_TILE2 = "knightTile2";
	private static final String LEGACY_CONFIG_KNIGHT_TILE1_COLOR = "knightTile1Color";
	private static final String LEGACY_CONFIG_KNIGHT_TILE2_COLOR = "knightTile2Color";
	private static final String CONFIG_KNIGHT_TILE_COLOR = "knightTileColor";
	private static final String CONFIG_STACK_TILE_COLOR = "stackTileColor";

	@Inject
	private SplashHelperConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SessionManager sessionManager;

	@Inject
	private NotificationService notificationService;

	@Getter
	private WorldPoint boundaryTile = null;

	@Getter
	private WorldPoint knightTile = null;

	@Getter
	private WorldPoint stackTile = null;

	// Movement tracking
	private WorldPoint lastNpcPosition = null;
	private int movementCount = 0;
	private Instant trackingStartTime = null;
	@Getter
	private double movementsPerHour = 0.0;

	// hasEscaped state machine
	@Getter
	private boolean hasEscaped = false;
	private int boundaryTickCounter = 0;
	private boolean boundaryNotified = false;

	// Current target
	@Getter
	private Actor currentTarget = null;

	// The exact knight NPC this splashing session was started on. Captured the
	// first time a target is engaged and held until the target is cleared, so
	// safety mode can restrict clicks to this knight only (and not a second
	// knight dragged in by a griefer).
	@Getter
	private NPC sessionKnight = null;

	/**
	 * Set the boundary tile.
	 */
	public void setBoundaryTile(WorldPoint tile)
	{
		this.boundaryTile = tile;
		boundaryNotified = false;
		saveTile(CONFIG_BOUNDARY_TILE, tile);
		log.debug("Boundary tile set to: {}", tile);
	}

	/**
	 * Unset the boundary tile.
	 */
	public void unsetBoundaryTile()
	{
		boundaryTile = null;
		boundaryNotified = false;
		saveTile(CONFIG_BOUNDARY_TILE, null);
		log.debug("Boundary tile unset");
	}

	public void setKnightTile(WorldPoint tile)
	{
		this.knightTile = tile;
		saveTile(CONFIG_KNIGHT_TILE, tile);
		log.debug("Knight Tile set to: {}", tile);

		// Initialize tracking if both tiles are set
		if (knightTile != null && stackTile != null)
		{
			resetMovementTracking();
		}
	}

	public void unsetKnightTile()
	{
		knightTile = null;
		saveTile(CONFIG_KNIGHT_TILE, null);
		resetMovementTracking();
		log.debug("Knight Tile unset");
	}

	public void setStackTile(WorldPoint tile)
	{
		this.stackTile = tile;
		saveTile(CONFIG_STACK_TILE, tile);
		log.debug("Stack Tile set to: {}", tile);

		// Initialize tracking if both tiles are set
		if (knightTile != null && stackTile != null)
		{
			resetMovementTracking();
		}
	}

	/**
	 * Unset the Stack Tile.
	 */
	public void unsetStackTile()
	{
		stackTile = null;
		saveTile(CONFIG_STACK_TILE, null);
		resetMovementTracking();
		log.debug("Stack Tile unset");
	}

	/**
	 * Set the current target.
	 */
	public void setCurrentTarget(Actor target)
	{
		if (target == null)
		{
			// Target cleared (knight died / session reset) releases the lock so a
			// fresh session can lock onto whichever knight it starts on.
			sessionKnight = null;
		}
		else if (sessionKnight == null && target instanceof NPC)
		{
			// Lock onto the first knight this session engages with.
			sessionKnight = (NPC) target;
		}
		this.currentTarget = target;
	}

	/**
	 * Reset movement tracking.
	 */
	public void resetMovementTracking()
	{
		lastNpcPosition = null;
		movementCount = 0;
		trackingStartTime = null;
		movementsPerHour = 0.0;
	}

	/**
	 * Track NPC movement between the Knight Tile and the Stack Tile.
	 */
	public void trackKnightMovement()
	{
		if (knightTile != null && stackTile != null && currentTarget != null)
		{
			WorldPoint currentPosition = currentTarget.getWorldLocation();

			if (currentPosition != null)
			{
				// Check if NPC is on either tile
				boolean onKnightTile = currentPosition.equals(knightTile);
				boolean onStackTile = currentPosition.equals(stackTile);

				if (onKnightTile || onStackTile)
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
						boolean wasOnKnightTile = lastNpcPosition.equals(knightTile);
						boolean wasOnStackTile = lastNpcPosition.equals(stackTile);

						if ((wasOnKnightTile && onStackTile) || (wasOnStackTile && onKnightTile))
						{
							movementCount++;
							sessionManager.recordKnightMovement();
						}

						lastNpcPosition = currentPosition;
					}

					// Calculate movements per hour
					if (trackingStartTime != null)
					{
						java.time.Duration trackingDuration = java.time.Duration.between(trackingStartTime, Instant.now());
						double hours = trackingDuration.toMillis() / 3_600_000.0;

						if (hours > 0)
						{
							movementsPerHour = movementCount / hours;
						}
					}
				}
			}
		}
	}

	/**
	 * Update knight position tracking and boundary notifications.
	 * Returns true if boundary notification should be sent.
	 */
	public boolean updateKnightPositionTracking()
	{
		boolean shouldNotifyBoundary = false;
		
		if (currentTarget != null && currentTarget instanceof NPC)
		{
			NPC knight = (NPC) currentTarget;
			WorldPoint knightPos = knight.getWorldLocation();
			
			if (knightPos != null)
			{
				boolean onKnightTile = (knightTile != null && knightPos.equals(knightTile)) ||
									   (stackTile != null && knightPos.equals(stackTile));
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
							shouldNotifyBoundary = true;
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
						boundaryNotified = false;
						boundaryTickCounter = 0;
						notificationService.unmuteNotifications();
					}
				}
				else
				{
					// Knight is on some other tile (not boundary, not knight tiles)
					if (hasEscaped)
					{
						boundaryTickCounter++;
						// After 5 ticks on a non-valid tile, allow re-notification
						if (boundaryTickCounter >= Constants.BOUNDARY_DEBOUNCE_TICKS)
						{
							boundaryNotified = false;
						}
					}
				}
			}
		}
		
		return shouldNotifyBoundary;
	}

	/**
	 * Reset the hasEscaped state.
	 */
	public void resetEscapedState()
	{
		hasEscaped = false;
		boundaryNotified = false;
		boundaryTickCounter = 0;
		notificationService.unmuteNotifications();
	}

	/**
	 * Reset all tile and tracking state.
	 */
	public void reset()
	{
		currentTarget = null;
		sessionKnight = null;
		boundaryNotified = false;
		lastNpcPosition = null;
		movementCount = 0;
		trackingStartTime = null;
		movementsPerHour = 0.0;
		hasEscaped = false;
		boundaryTickCounter = 0;
		notificationService.unmuteNotifications();
	}

	/**
	 * Load persisted tiles from config. Called on plugin startup.
	 */
	public void loadPersistedTiles()
	{
		migrateLegacyTileConfig();
		boundaryTile = loadTile(CONFIG_BOUNDARY_TILE);
		knightTile = loadTile(CONFIG_KNIGHT_TILE);
		stackTile = loadTile(CONFIG_STACK_TILE);
		log.debug("Loaded persisted tiles - boundary: {}, knight: {}, stack: {}", boundaryTile, knightTile, stackTile);
	}

	private void migrateLegacyTileConfig()
	{
		migrateConfigKey(LEGACY_CONFIG_KNIGHT_TILE1, CONFIG_KNIGHT_TILE);
		migrateConfigKey(LEGACY_CONFIG_KNIGHT_TILE2, CONFIG_STACK_TILE);
		migrateConfigKey(LEGACY_CONFIG_KNIGHT_TILE1_COLOR, CONFIG_KNIGHT_TILE_COLOR);
		migrateConfigKey(LEGACY_CONFIG_KNIGHT_TILE2_COLOR, CONFIG_STACK_TILE_COLOR);
	}

	private void migrateConfigKey(String legacyKey, String newKey)
	{
		String existingNewValue = configManager.getConfiguration(CONFIG_GROUP, newKey);
		if (existingNewValue != null && !existingNewValue.isEmpty())
		{
			return;
		}

		String legacyValue = configManager.getConfiguration(CONFIG_GROUP, legacyKey);
		if (legacyValue == null || legacyValue.isEmpty())
		{
			return;
		}

		configManager.setConfiguration(CONFIG_GROUP, newKey, legacyValue);
		configManager.unsetConfiguration(CONFIG_GROUP, legacyKey);
		log.debug("Migrated legacy config key '{}' to '{}'", legacyKey, newKey);
	}

	private void saveTile(String key, WorldPoint tile)
	{
		if (tile == null)
		{
			configManager.unsetConfiguration(CONFIG_GROUP, key);
		}
		else
		{
			String value = tile.getX() + "," + tile.getY() + "," + tile.getPlane();
			configManager.setConfiguration(CONFIG_GROUP, key, value);
		}
	}

	private WorldPoint loadTile(String key)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, key);
		if (value == null || value.isEmpty())
		{
			return null;
		}
		try
		{
			String[] parts = value.split(",");
			return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		catch (Exception e)
		{
			log.warn("Failed to parse persisted tile '{}': {}", key, e.getMessage());
			return null;
		}
	}
}
