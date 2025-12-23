package xyz.peppie.splashhelper.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
	private final Instant logoutTime;
	private final int world;
	private final boolean stickyKnight;

	// Dynamic fields (updated during session)
	@Setter
	private int spellsCast = 0;
	@Setter
	private int startMagicXp;
	@Setter
	private int currentMagicXp;
	@Setter
	private int knightMovements = 0;
	@Setter
	private Instant endTime = null;

	// Player tracking
	private final Set<String> pickpocketers = new HashSet<>();
	private final List<Integer> playerCountSamples = new ArrayList<>();
	@Setter
	private int highestPlayerCount = 0;

	// Rune tracking
	@Setter
	private int startingRuneCount = 0;
	@Setter
	private int currentRuneCount = 0;
	@Setter
	private List<int[]> actualRuneUsage = new ArrayList<>();  // Runes actually consumed (excludes infinite)
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

	public int getRunesUsed()
	{
		return startingRuneCount - currentRuneCount;
	}

	public int getRemainingCasts()
	{
		// currentRuneCount is already the number of possible casts (from countLimitingRunes)
		return currentRuneCount;
	}

	public void addPickpocketer(String playerName)
	{
		if (playerName != null && !playerName.isEmpty())
		{
			pickpocketers.add(playerName);
		}
	}

	public int getPickpocketerCount()
	{
		return pickpocketers.size();
	}

	public void addPlayerCountSample(int count)
	{
		playerCountSamples.add(count);
		if (count > highestPlayerCount)
		{
			highestPlayerCount = count;
		}
	}

	public double getAveragePlayerCount()
	{
		if (playerCountSamples.isEmpty())
		{
			return 0;
		}
		return playerCountSamples.stream().mapToInt(Integer::intValue).average().orElse(0);
	}

	public void incrementSpellsCast()
	{
		spellsCast++;
	}

	public void incrementKnightMovements()
	{
		knightMovements++;
	}

	public void finalizeSession()
	{
		this.endTime = Instant.now();
	}

	public boolean isActive()
	{
		return endTime == null;
	}
}
