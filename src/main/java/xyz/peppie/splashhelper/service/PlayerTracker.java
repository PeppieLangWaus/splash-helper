package xyz.peppie.splashhelper.service;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service responsible for tracking players near the knight,
 * particularly pickpocketers who may affect splashing.
 */
@Singleton
public class PlayerTracker
{
	private final Set<String> pickpocketers = new HashSet<>();

	@Inject
	public PlayerTracker()
	{
	}

	/**
	 * Reset all tracking data.
	 */
	public void reset()
	{
		pickpocketers.clear();
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
}
