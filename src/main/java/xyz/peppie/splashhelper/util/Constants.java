package xyz.peppie.splashhelper.util;

import java.util.Set;

/**
 * Central location for all constant values used throughout the plugin.
 */
public final class Constants
{
	private Constants() {}

	// ==================== Timing Constants ====================
	public static final int SESSION_TIMEOUT_SECONDS = 10;
	public static final int BOUNDARY_DEBOUNCE_TICKS = 5;
	public static final int VISUAL_NOTIFICATION_DURATION_MS = 2000;

	// ==================== Combination Rune IDs ====================
	public static final int MIST_RUNE = 4695;   // Air + Water
	public static final int DUST_RUNE = 4696;   // Air + Earth
	public static final int SMOKE_RUNE = 4697;  // Air + Fire
	public static final int MUD_RUNE = 4698;    // Water + Earth
	public static final int STEAM_RUNE = 4694;  // Water + Fire
	public static final int LAVA_RUNE = 4699;   // Earth + Fire

	// ==================== Rune Pouch IDs ====================
	public static final int RUNE_POUCH = 12791;
	public static final int RUNE_POUCH_DIVINE = 27281;

	// ==================== Rune Pouch Varbits ====================
	public static final int[] RUNE_POUCH_RUNE_VARBITS = {29, 1622, 1623};
	public static final int[] RUNE_POUCH_AMOUNT_VARBITS = {1624, 1625, 1626};

	// ==================== Basic Rune IDs ====================
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

	// ==================== Staff IDs ====================
	// Air staves
	public static final int STAFF_OF_AIR = 1381;
	public static final int AIR_BATTLESTAFF = 1397;
	public static final int MYSTIC_AIR_STAFF = 1405;
	public static final int SMOKE_BATTLESTAFF = 11787;
	public static final int MYSTIC_SMOKE_STAFF = 12795;
	public static final int DUST_BATTLESTAFF = 20736;
	public static final int MYSTIC_DUST_STAFF = 20739;
	public static final int MIST_BATTLESTAFF = 6562;
	public static final int MYSTIC_MIST_STAFF = 6563;

	// Water staves
	public static final int STAFF_OF_WATER = 1383;
	public static final int WATER_BATTLESTAFF = 1395;
	public static final int MYSTIC_WATER_STAFF = 1403;
	public static final int STEAM_BATTLESTAFF = 11789;
	public static final int MYSTIC_STEAM_STAFF = 12797;
	public static final int MUD_BATTLESTAFF = 6564;
	public static final int MYSTIC_MUD_STAFF = 6565;

	// Earth staves
	public static final int STAFF_OF_EARTH = 1385;
	public static final int EARTH_BATTLESTAFF = 1399;
	public static final int MYSTIC_EARTH_STAFF = 1407;
	public static final int LAVA_BATTLESTAFF = 3053;
	public static final int MYSTIC_LAVA_STAFF = 3054;

	// Fire staves
	public static final int STAFF_OF_FIRE = 1387;
	public static final int FIRE_BATTLESTAFF = 1393;
	public static final int MYSTIC_FIRE_STAFF = 1401;

	// ==================== Knight Model IDs ====================
	public static final Set<Integer> FEMALE_KNIGHT_MODEL_IDS = Set.of(
		11936,  // Female model
		3297    // Male model (note: this seems incorrect in original code)
	);

	// ==================== Animation IDs ====================
	public static final int ANIMATION_MAGIC_COMBAT = 711;
	public static final int ANIMATION_CURSE = 710;
	public static final int ANIMATION_WEAKEN_CONFUSE = 716;
	public static final int ANIMATION_UNKNOWN_MAGIC = 727;
	public static final int ANIMATION_PICKPOCKET = 881;

	// ==================== NPC IDs ====================
	public static final int KNIGHT_OF_ARDOUGNE = 3297;

	// ==================== Utility Methods ====================
	public static boolean isBasicRune(int id)
	{
		return id == AIR_RUNE || id == WATER_RUNE || id == EARTH_RUNE ||
			id == FIRE_RUNE || id == MIND_RUNE || id == BODY_RUNE ||
			id == CHAOS_RUNE || id == DEATH_RUNE || id == BLOOD_RUNE ||
			id == WRATH_RUNE;
	}

	public static boolean isCombinationRune(int id)
	{
		return id == MIST_RUNE || id == DUST_RUNE || id == MUD_RUNE ||
			id == SMOKE_RUNE || id == STEAM_RUNE || id == LAVA_RUNE;
	}

	public static String getRuneName(int itemId)
	{
		switch (itemId)
		{
			case MIND_RUNE: return "Mind rune";
			case WATER_RUNE: return "Water rune";
			case EARTH_RUNE: return "Earth rune";
			case FIRE_RUNE: return "Fire rune";
			case AIR_RUNE: return "Air rune";
			case BODY_RUNE: return "Body rune";
			case CHAOS_RUNE: return "Chaos rune";
			case DEATH_RUNE: return "Death rune";
			case BLOOD_RUNE: return "Blood rune";
			case WRATH_RUNE: return "Wrath rune";
			case MIST_RUNE: return "Mist rune";
			case DUST_RUNE: return "Dust rune";
			case SMOKE_RUNE: return "Smoke rune";
			case MUD_RUNE: return "Mud rune";
			case STEAM_RUNE: return "Steam rune";
			case LAVA_RUNE: return "Lava rune";
			default: return "Rune";
		}
	}
}
