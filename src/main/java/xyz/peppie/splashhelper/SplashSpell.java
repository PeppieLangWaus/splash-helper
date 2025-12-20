package xyz.peppie.splashhelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public enum SplashSpell
{
	// Standard spellbook - Strike spells
	WIND_STRIKE("Wind Strike", 711, 5.5, new RuneCost(ItemID.AIR_RUNE, 1), new RuneCost(ItemID.MIND_RUNE, 1)),
	WATER_STRIKE("Water Strike", 711, 7.5, new RuneCost(ItemID.AIR_RUNE, 1), new RuneCost(ItemID.MIND_RUNE, 1), new RuneCost(ItemID.WATER_RUNE, 1)),
	EARTH_STRIKE("Earth Strike", 711, 9.5, new RuneCost(ItemID.AIR_RUNE, 1), new RuneCost(ItemID.MIND_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 2)),
	FIRE_STRIKE("Fire Strike", 711, 11.5, new RuneCost(ItemID.AIR_RUNE, 2), new RuneCost(ItemID.MIND_RUNE, 1), new RuneCost(ItemID.FIRE_RUNE, 3)),

	// Standard spellbook - Bolt spells
	WIND_BOLT("Wind Bolt", 711, 13.5, new RuneCost(ItemID.AIR_RUNE, 2), new RuneCost(ItemID.CHAOS_RUNE, 1)),
	WATER_BOLT("Water Bolt", 711, 16.5, new RuneCost(ItemID.AIR_RUNE, 2), new RuneCost(ItemID.CHAOS_RUNE, 1), new RuneCost(ItemID.WATER_RUNE, 2)),
	EARTH_BOLT("Earth Bolt", 711, 19.5, new RuneCost(ItemID.AIR_RUNE, 2), new RuneCost(ItemID.CHAOS_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 3)),
	FIRE_BOLT("Fire Bolt", 711, 22.5, new RuneCost(ItemID.AIR_RUNE, 3), new RuneCost(ItemID.CHAOS_RUNE, 1), new RuneCost(ItemID.FIRE_RUNE, 4)),

	// Standard spellbook - Blast spells
	WIND_BLAST("Wind Blast", 711, 25.5, new RuneCost(ItemID.AIR_RUNE, 3), new RuneCost(ItemID.DEATH_RUNE, 1)),
	WATER_BLAST("Water Blast", 711, 28.5, new RuneCost(ItemID.AIR_RUNE, 3), new RuneCost(ItemID.DEATH_RUNE, 1), new RuneCost(ItemID.WATER_RUNE, 3)),
	EARTH_BLAST("Earth Blast", 711, 31.5, new RuneCost(ItemID.AIR_RUNE, 3), new RuneCost(ItemID.DEATH_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 4)),
	FIRE_BLAST("Fire Blast", 711, 34.5, new RuneCost(ItemID.AIR_RUNE, 4), new RuneCost(ItemID.DEATH_RUNE, 1), new RuneCost(ItemID.FIRE_RUNE, 5)),

	// Standard spellbook - Wave spells
	WIND_WAVE("Wind Wave", 711, 36.0, new RuneCost(ItemID.AIR_RUNE, 5), new RuneCost(ItemID.BLOOD_RUNE, 1)),
	WATER_WAVE("Water Wave", 711, 37.5, new RuneCost(ItemID.AIR_RUNE, 5), new RuneCost(ItemID.BLOOD_RUNE, 1), new RuneCost(ItemID.WATER_RUNE, 7)),
	EARTH_WAVE("Earth Wave", 711, 40.0, new RuneCost(ItemID.AIR_RUNE, 5), new RuneCost(ItemID.BLOOD_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 7)),
	FIRE_WAVE("Fire Wave", 711, 42.5, new RuneCost(ItemID.AIR_RUNE, 5), new RuneCost(ItemID.BLOOD_RUNE, 1), new RuneCost(ItemID.FIRE_RUNE, 7)),

	// Standard spellbook - Surge spells
	WIND_SURGE("Wind Surge", 711, 44.5, new RuneCost(ItemID.AIR_RUNE, 7), new RuneCost(ItemID.WRATH_RUNE, 1)),
	WATER_SURGE("Water Surge", 711, 46.5, new RuneCost(ItemID.AIR_RUNE, 7), new RuneCost(ItemID.WRATH_RUNE, 1), new RuneCost(ItemID.WATER_RUNE, 10)),
	EARTH_SURGE("Earth Surge", 711, 48.5, new RuneCost(ItemID.AIR_RUNE, 7), new RuneCost(ItemID.WRATH_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 10)),
	FIRE_SURGE("Fire Surge", 711, 50.5, new RuneCost(ItemID.AIR_RUNE, 7), new RuneCost(ItemID.WRATH_RUNE, 1), new RuneCost(ItemID.FIRE_RUNE, 10)),

	// Curses (commonly used for splashing)
	CURSE("Curse", 710, 29.0, new RuneCost(ItemID.BODY_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 3), new RuneCost(ItemID.WATER_RUNE, 2)),
	WEAKEN("Weaken", 716, 21.0, new RuneCost(ItemID.BODY_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 2), new RuneCost(ItemID.WATER_RUNE, 3)),
	CONFUSE("Confuse", 716, 13.0, new RuneCost(ItemID.BODY_RUNE, 1), new RuneCost(ItemID.EARTH_RUNE, 2), new RuneCost(ItemID.WATER_RUNE, 3));

	private final String name;
	private final int animationId;
	private final double baseXp;
	private final RuneCost[] runeCosts;

	SplashSpell(String name, int animationId, double baseXp, RuneCost... runeCosts)
	{
		this.name = name;
		this.animationId = animationId;
		this.baseXp = baseXp;
		this.runeCosts = runeCosts;
	}

	public int getTotalRuneCost()
	{
		int total = 0;
		for (RuneCost cost : runeCosts)
		{
			total += cost.getAmount();
		}
		return total;
	}

	public int getRuneCount(int itemId)
	{
		for (RuneCost cost : runeCosts)
		{
			if (cost.getItemId() == itemId)
			{
				return cost.getAmount();
			}
		}
		return 0;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public static SplashSpell fromAnimationId(int animationId)
	{
		for (SplashSpell spell : values())
		{
			if (spell.getAnimationId() == animationId)
			{
				return spell;
			}
		}
		return null;
	}

	/**
	 * Detect the spell from the XP gained.
	 * Each spell has a unique base XP value, so we can identify it from the XP drop.
	 */
	public static SplashSpell fromXpDrop(int xpGained)
	{
		for (SplashSpell spell : values())
		{
			// XP is stored as integer, baseXp is double (e.g., 5.5 -> 5 or 6 XP)
			int expectedXp = (int) spell.getBaseXp();
			int expectedXpRounded = (int) Math.round(spell.getBaseXp());
			
			if (xpGained == expectedXp || xpGained == expectedXpRounded)
			{
				return spell;
			}
		}
		return null;
	}

	@Getter
	@RequiredArgsConstructor
	public static class RuneCost
	{
		private final int itemId;
		private final int amount;
	}

	// Item IDs for runes (from RuneLite's ItemID)
	public static class ItemID
	{
		public static final int AIR_RUNE = 556;
		public static final int WATER_RUNE = 555;
		public static final int EARTH_RUNE = 557;
		public static final int FIRE_RUNE = 554;
		public static final int MIND_RUNE = 558;
		public static final int BODY_RUNE = 559;
		public static final int CHAOS_RUNE = 562;
		public static final int DEATH_RUNE = 560;
		public static final int BLOOD_RUNE = 565;
		public static final int WRATH_RUNE = 21880;
	}
}
