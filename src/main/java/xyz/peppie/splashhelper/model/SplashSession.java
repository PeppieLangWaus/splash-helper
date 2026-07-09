package xyz.peppie.splashhelper.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
public class SplashSession
{
	// Static fields (set once at session start)
	private final String playerName;
	private SplashSpell spell;
	private int runeCostPerCast;
	private final Instant startTime;
	@Setter
	private Instant logoutTime;
	@Setter
	private int world;
	@Setter
	private boolean stickyKnight;

	// Dynamic fields (updated during session)
	@Setter
	private int spellsCast = 0;
	@Setter
	private int startMagicXp;
	@Setter
	private int currentMagicXp;
	@Setter
	private int knightMovements = 0;
	// Count of nearby players observed dying during this session
	@Setter
	private int playerDeaths = 0;
	@Setter
	private Instant endTime = null;

	// Player tracking (transient - not persisted)
	private transient final Set<String> pickpocketers = new HashSet<>();
	// Running mean for player count: O(1) memory, exact average over any session length.
	private transient long playerCountSum = 0;
	private transient long playerCountSampleN = 0;
	// Bounded, downsampled player-count-over-time series (persisted; used for server graphs)
	private PlayerCountSeries playerCountSeries = new PlayerCountSeries();
	@Setter
	private int highestPlayerCount = 0;
	
	// Derived statistics for persistence
	@Setter
	private int averagePlayerCount = 0;
	@Setter
	private int pickpocketerCount = 0;

	// Rune tracking
	@Setter
	private int startingRuneCount = 0;
	@Setter
	private int currentRuneCount = 0;
	private final Map<Integer, Integer> runeUsageMap = new HashMap<>();  // ItemId -> Total amount used
	@Setter
	private long runeCostGp = 0;  // Total GP cost of runes used

	public SplashSession(String playerName, SplashSpell spell, Instant logoutTime, int world, boolean stickyKnight, int startMagicXp)
	{
		this.playerName = playerName;
		this.spell = spell;
		this.runeCostPerCast = spell != null ? spell.getTotalRuneCost() : 0;
		this.startTime = Instant.now();
		this.logoutTime = logoutTime;
		this.world = world;
		this.stickyKnight = stickyKnight;
		this.startMagicXp = startMagicXp;
		this.currentMagicXp = startMagicXp;
	}

	public void setSpell(SplashSpell newSpell)
	{
		this.spell = newSpell;
		this.runeCostPerCast = newSpell != null ? newSpell.getTotalRuneCost() : 0;
	}

	public long getSessionDurationSeconds()
	{
		Instant end = endTime != null ? endTime : Instant.now();
		return java.time.Duration.between(startTime, end).getSeconds();
	}

	public int getMagicXpGained()
	{
		return currentMagicXp - startMagicXp;
	}

	public double getXpPerHour()
	{
		long seconds = getSessionDurationSeconds();
		if (seconds <= 0)
		{
			return 0;
		}
		return (getMagicXpGained() * 3600.0) / seconds;
	}

	public void addPickpocketer(String playerName)
	{
		if (playerName != null && !playerName.isEmpty())
		{
			pickpocketers.add(playerName);
			pickpocketerCount = pickpocketers.size();
		}
	}

	public void addPlayerCountSample(int count)
	{
		playerCountSampleN++;
		playerCountSum += count;
		if (count > highestPlayerCount)
		{
			highestPlayerCount = count;
		}
		averagePlayerCount = (int) Math.round((double) playerCountSum / playerCountSampleN);

		if (playerCountSeries == null)
		{
			// Sessions loaded from an older on-disk format have no series
			playerCountSeries = new PlayerCountSeries();
		}
		playerCountSeries.addSample(java.time.Duration.between(startTime, Instant.now()).getSeconds(), count);
	}

	public double getAveragePlayerCount()
	{
		return averagePlayerCount;
	}

	public void incrementSpellsCast()
	{
		spellsCast++;
	}

	public void incrementKnightMovements()
	{
		knightMovements++;
	}

	public void incrementPlayerDeaths()
	{
		playerDeaths++;
	}

	/**
	 * Add rune usage for a single cast to the accumulated total.
	 * This allows tracking mixed spell usage without recalculating.
	 * @param runesUsedThisCast List of [itemId, amount] pairs for this cast
	 * @param costGp GP cost of runes used in this cast
	 */
	public void addRuneUsageForCast(List<int[]> runesUsedThisCast, long costGp)
	{
		if (runesUsedThisCast == null || runesUsedThisCast.isEmpty())
		{
			return;
		}

		// Accumulate rune usage using Map (no reference issues)
		for (int[] runeUsage : runesUsedThisCast)
		{
			int itemId = runeUsage[0];
			int amount = runeUsage[1];
			
			// Simply add to the map - Map.put handles both new and existing entries
			runeUsageMap.put(itemId, runeUsageMap.getOrDefault(itemId, 0) + amount);
		}

		// Accumulate cost
		runeCostGp += costGp;
	}

	/**
	 * Get actual rune usage as List<int[]> for compatibility with existing code.
	 * @return List of [itemId, totalAmount] pairs
	 */
	public List<int[]> getActualRuneUsage()
	{
		List<int[]> result = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : runeUsageMap.entrySet())
		{
			result.add(new int[]{entry.getKey(), entry.getValue()});
		}
		return result;
	}

	/**
	 * Un-finalize this session so it can be resumed after a brief interruption.
	 */
	public void resume()
	{
		this.endTime = null;
	}

	public boolean isActive()
	{
		return endTime == null;
	}
}
