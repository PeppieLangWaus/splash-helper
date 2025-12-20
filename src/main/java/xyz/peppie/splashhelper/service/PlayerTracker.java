package xyz.peppie.splashhelper.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import xyz.peppie.splashhelper.util.Constants;

/**
 * Service responsible for tracking players near the knight,
 * particularly pickpocketers who may affect splashing.
 */
@Singleton
public class PlayerTracker
{
	private final Client client;
	private final Set<String> pickpocketers = new HashSet<>();

	@Getter
	private int currentPickpocketerCount = 0;

	@Inject
	public PlayerTracker(Client client)
	{
		this.client = client;
	}

	/**
	 * Reset all tracking data.
	 */
	public void reset()
	{
		pickpocketers.clear();
		currentPickpocketerCount = 0;
	}

	/**
	 * Add a player to the pickpocketer set.
	 * @return true if this is a new pickpocketer
	 */
	public boolean addPickpocketer(String playerName)
	{
		if (playerName == null || playerName.isEmpty())
		{
			return false;
		}
		return pickpocketers.add(playerName);
	}

	/**
	 * Get all tracked pickpocketers.
	 */
	public Set<String> getPickpocketers()
	{
		return new HashSet<>(pickpocketers);
	}

	/**
	 * Check if a player is performing the pickpocket animation.
	 */
	public boolean isPickpocketing(Player player)
	{
		if (player == null)
		{
			return false;
		}
		return player.getAnimation() == Constants.ANIMATION_PICKPOCKET;
	}

	/**
	 * Count players within a radius of a location.
	 */
	public int countPlayersInRadius(WorldPoint center, int radius)
	{
		if (center == null)
		{
			return 0;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return 0;
		}

		int count = 0;
		List<Player> players = client.getPlayers();

		for (Player player : players)
		{
			if (player == null || player == localPlayer)
			{
				continue;
			}

			WorldPoint otherLocation = player.getWorldLocation();
			if (otherLocation != null && center.distanceTo(otherLocation) <= radius)
			{
				count++;
			}
		}

		return count;
	}

	/**
	 * Update pickpocketer count for players near an actor.
	 */
	public void updatePickpocketerCount(Actor target, int radius)
	{
		if (target == null)
		{
			currentPickpocketerCount = 0;
			return;
		}

		WorldPoint targetLocation = target.getWorldLocation();
		currentPickpocketerCount = countPlayersInRadius(targetLocation, radius);
	}

	/**
	 * Check if a player is near the target and performing pickpocket animation.
	 * If so, add them to the pickpocketer set.
	 */
	public void checkAndTrackPickpocketer(Player player, Actor target, int radius)
	{
		if (player == null || target == null)
		{
			return;
		}

		if (!isPickpocketing(player))
		{
			return;
		}

		WorldPoint playerLocation = player.getWorldLocation();
		WorldPoint targetLocation = target.getWorldLocation();

		if (playerLocation != null && targetLocation != null &&
			playerLocation.distanceTo(targetLocation) <= radius)
		{
			String playerName = player.getName();
			if (playerName != null)
			{
				addPickpocketer(playerName);
			}
		}
	}
}
