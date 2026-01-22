package xyz.peppie.splashhelper.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import xyz.peppie.splashhelper.SplashHelperConfig;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;

/**
 * Service for managing tile-related functionality including boundary tiles, knight tiles, and movement tracking.
 */
@Slf4j
@Singleton
public class TileManager
{
	@Inject
	private SplashHelperConfig config;

	@Inject
	private SessionManager sessionManager;

	@Getter
	private WorldPoint boundaryTile = null;

	@Getter
	private WorldPoint knightTile1 = null;

	@Getter
	private WorldPoint knightTile2 = null;

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
	private boolean boundaryNotified = false;

	// Current target
	@Getter
	private Actor currentTarget = null;

	/**
	 * Set the boundary tile.
	 */
	public void setBoundaryTile(WorldPoint tile)
	{
		this.boundaryTile = tile;
		boundaryNotified = false;
		log.debug("✓ Boundary tile successfully set to: {}", tile);
	}

	/**
	 * Unset the boundary tile.
	 */
	public void unsetBoundaryTile()
	{
		boundaryTile = null;
		boundaryNotified = false;
		log.debug("✓ Boundary tile unset");
	}

	/**
	 * Set knight tile 1.
	 */
	public void setKnightTile1(WorldPoint tile)
	{
		this.knightTile1 = tile;
		log.debug("✓ Knight Tile 1 successfully set to: {}", tile);
		
		// Initialize tracking if both tiles are set
		if (knightTile1 != null && knightTile2 != null)
		{
			resetMovementTracking();
		}
	}

	/**
	 * Unset knight tile 1.
	 */
	public void unsetKnightTile1()
	{
		knightTile1 = null;
		resetMovementTracking();
		log.debug("✓ Knight Tile 1 unset");
	}

	/**
	 * Set knight tile 2.
	 */
	public void setKnightTile2(WorldPoint tile)
	{
		this.knightTile2 = tile;
		log.debug("✓ Knight Tile 2 successfully set to: {}", tile);
		
		// Initialize tracking if both tiles are set
		if (knightTile1 != null && knightTile2 != null)
		{
			resetMovementTracking();
		}
	}

	/**
	 * Unset knight tile 2.
	 */
	public void unsetKnightTile2()
	{
		knightTile2 = null;
		resetMovementTracking();
		log.debug("✓ Knight Tile 2 unset");
	}

	/**
	 * Set the current target.
	 */
	public void setCurrentTarget(Actor target)
	{
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
		movementsPerMinute = 0.0;
	}

	/**
	 * Track NPC movement between Knight Tile 1 and Knight Tile 2.
	 */
	public void trackKnightMovement()
	{
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
							sessionManager.recordKnightMovement();
						}
						
						lastNpcPosition = currentPosition;
					}
					
					// Calculate movements per minute
					if (trackingStartTime != null)
					{
						java.time.Duration trackingDuration = java.time.Duration.between(trackingStartTime, Instant.now());
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
	}

	/**
	 * Reset all tile and tracking state.
	 */
	public void reset()
	{
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
	}
}
