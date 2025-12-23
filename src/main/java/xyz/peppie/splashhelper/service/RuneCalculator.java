package xyz.peppie.splashhelper.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.util.Constants;

/**
 * Service responsible for all rune-related calculations including:
 * - Counting runes in inventory and rune pouch
 * - Detecting combination runes
 * - Detecting infinite runes from equipped staves
 * - Calculating remaining spell casts
 */
@Singleton
public class RuneCalculator
{
	private final Client client;

	@Inject
	public RuneCalculator(Client client)
	{
		this.client = client;
	}

	/**
	 * Calculate remaining casts for a spell, accounting for combo runes, pouch, and staves.
	 */
	public int getRemainingCasts(SplashSpell spell)
	{
		if (spell == null)
		{
			return 0;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return 0;
		}

		Set<Integer> infiniteRunes = getInfiniteRunesFromEquipment();
		Map<Integer, Integer> runeCounts = buildRuneCountMap(inventory);

		int minCasts = Integer.MAX_VALUE;

		for (SplashSpell.RuneCost cost : spell.getRuneCosts())
		{
			if (infiniteRunes.contains(cost.getItemId()))
			{
				continue;
			}

			int runeCount = runeCounts.getOrDefault(cost.getItemId(), 0);
			int castsWithThisRune = runeCount / cost.getAmount();
			minCasts = Math.min(minCasts, castsWithThisRune);
		}

		return minCasts == Integer.MAX_VALUE ? 0 : minCasts;
	}

	/**
	 * Get the actual runes being consumed for a spell.
	 * Detects combination runes in inventory and excludes infinite runes from staves.
	 * @return List of int[2] arrays: [itemId, amountPerCast]
	 */
	public List<int[]> getActualRuneUsage(SplashSpell spell)
	{
		List<int[]> result = new ArrayList<>();

		if (spell == null)
		{
			return result;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return result;
		}

		Set<Integer> infiniteRunes = getInfiniteRunesFromEquipment();
		Map<Integer, Integer> comboRunesInInventory = findCombinationRunes(inventory);

		for (SplashSpell.RuneCost cost : spell.getRuneCosts())
		{
			int requiredRuneId = cost.getItemId();

			if (infiniteRunes.contains(requiredRuneId))
			{
				continue;
			}

			int comboRuneId = findCombinationRuneFor(requiredRuneId, comboRunesInInventory.keySet());
			if (comboRuneId > 0)
			{
				result.add(new int[]{comboRuneId, cost.getAmount()});
			}
			else
			{
				result.add(new int[]{requiredRuneId, cost.getAmount()});
			}
		}

		return deduplicateRuneUsage(result);
	}

	/**
	 * Get set of rune IDs that are provided infinitely by equipped staves.
	 */
	public Set<Integer> getInfiniteRunesFromEquipment()
	{
		Set<Integer> infiniteRunes = new HashSet<>();

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return infiniteRunes;
		}

		for (Item item : equipment.getItems())
		{
			int id = item.getId();

			// Air staves
			if (id == Constants.STAFF_OF_AIR || id == Constants.AIR_BATTLESTAFF || id == Constants.MYSTIC_AIR_STAFF ||
				id == Constants.SMOKE_BATTLESTAFF || id == Constants.MYSTIC_SMOKE_STAFF ||
				id == Constants.DUST_BATTLESTAFF || id == Constants.MYSTIC_DUST_STAFF ||
				id == Constants.MIST_BATTLESTAFF || id == Constants.MYSTIC_MIST_STAFF)
			{
				infiniteRunes.add(Constants.AIR_RUNE);
			}

			// Water staves
			if (id == Constants.STAFF_OF_WATER || id == Constants.WATER_BATTLESTAFF || id == Constants.MYSTIC_WATER_STAFF ||
				id == Constants.STEAM_BATTLESTAFF || id == Constants.MYSTIC_STEAM_STAFF ||
				id == Constants.MIST_BATTLESTAFF || id == Constants.MYSTIC_MIST_STAFF ||
				id == Constants.MUD_BATTLESTAFF || id == Constants.MYSTIC_MUD_STAFF)
			{
				infiniteRunes.add(Constants.WATER_RUNE);
			}

			// Earth staves
			if (id == Constants.STAFF_OF_EARTH || id == Constants.EARTH_BATTLESTAFF || id == Constants.MYSTIC_EARTH_STAFF ||
				id == Constants.DUST_BATTLESTAFF || id == Constants.MYSTIC_DUST_STAFF ||
				id == Constants.MUD_BATTLESTAFF || id == Constants.MYSTIC_MUD_STAFF ||
				id == Constants.LAVA_BATTLESTAFF || id == Constants.MYSTIC_LAVA_STAFF)
			{
				infiniteRunes.add(Constants.EARTH_RUNE);
			}

			// Fire staves
			if (id == Constants.STAFF_OF_FIRE || id == Constants.FIRE_BATTLESTAFF || id == Constants.MYSTIC_FIRE_STAFF ||
				id == Constants.SMOKE_BATTLESTAFF || id == Constants.MYSTIC_SMOKE_STAFF ||
				id == Constants.STEAM_BATTLESTAFF || id == Constants.MYSTIC_STEAM_STAFF ||
				id == Constants.LAVA_BATTLESTAFF || id == Constants.MYSTIC_LAVA_STAFF)
			{
				infiniteRunes.add(Constants.FIRE_RUNE);
			}
		}

		return infiniteRunes;
	}

	/**
	 * Build a map of rune type -> count (including combo runes and pouch).
	 */
	private Map<Integer, Integer> buildRuneCountMap(ItemContainer inventory)
	{
		Map<Integer, Integer> runeCounts = new HashMap<>();

		for (Item item : inventory.getItems())
		{
			int id = item.getId();
			int qty = item.getQuantity();

			if (Constants.isBasicRune(id))
			{
				runeCounts.merge(id, qty, Integer::sum);
			}

			addCombinationRuneCounts(runeCounts, id, qty);

			if (id == Constants.RUNE_POUCH || id == Constants.RUNE_POUCH_DIVINE)
			{
				addRunePouchCounts(runeCounts);
			}
		}

		return runeCounts;
	}

	/**
	 * Find combination runes in inventory and rune pouch.
	 */
	private Map<Integer, Integer> findCombinationRunes(ItemContainer inventory)
	{
		Map<Integer, Integer> comboRunes = new HashMap<>();

		for (Item item : inventory.getItems())
		{
			int id = item.getId();
			if (Constants.isCombinationRune(id))
			{
				comboRunes.put(id, item.getQuantity());
			}
		}

		// Check rune pouch for combo runes
		for (int i = 0; i < 3; i++)
		{
			int runeId = runeIdFromVarbit(client.getVarbitValue(Constants.RUNE_POUCH_RUNE_VARBITS[i]));
			int amount = client.getVarbitValue(Constants.RUNE_POUCH_AMOUNT_VARBITS[i]);
			if (runeId > 0 && amount > 0 && Constants.isCombinationRune(runeId))
			{
				comboRunes.merge(runeId, amount, Integer::sum);
			}
		}

		return comboRunes;
	}

	/**
	 * Add combination rune counts to the rune map (both element types).
	 */
	private void addCombinationRuneCounts(Map<Integer, Integer> runeCounts, int itemId, int quantity)
	{
		switch (itemId)
		{
			case Constants.MIST_RUNE:
				runeCounts.merge(Constants.AIR_RUNE, quantity, Integer::sum);
				runeCounts.merge(Constants.WATER_RUNE, quantity, Integer::sum);
				break;
			case Constants.DUST_RUNE:
				runeCounts.merge(Constants.AIR_RUNE, quantity, Integer::sum);
				runeCounts.merge(Constants.EARTH_RUNE, quantity, Integer::sum);
				break;
			case Constants.MUD_RUNE:
				runeCounts.merge(Constants.WATER_RUNE, quantity, Integer::sum);
				runeCounts.merge(Constants.EARTH_RUNE, quantity, Integer::sum);
				break;
			case Constants.SMOKE_RUNE:
				runeCounts.merge(Constants.AIR_RUNE, quantity, Integer::sum);
				runeCounts.merge(Constants.FIRE_RUNE, quantity, Integer::sum);
				break;
			case Constants.STEAM_RUNE:
				runeCounts.merge(Constants.WATER_RUNE, quantity, Integer::sum);
				runeCounts.merge(Constants.FIRE_RUNE, quantity, Integer::sum);
				break;
			case Constants.LAVA_RUNE:
				runeCounts.merge(Constants.EARTH_RUNE, quantity, Integer::sum);
				runeCounts.merge(Constants.FIRE_RUNE, quantity, Integer::sum);
				break;
		}
	}

	/**
	 * Add rune pouch contents to the rune count map.
	 */
	private void addRunePouchCounts(Map<Integer, Integer> runeCounts)
	{
		for (int i = 0; i < 3; i++)
		{
			int runeId = runeIdFromVarbit(client.getVarbitValue(Constants.RUNE_POUCH_RUNE_VARBITS[i]));
			int amount = client.getVarbitValue(Constants.RUNE_POUCH_AMOUNT_VARBITS[i]);

			if (runeId > 0 && amount > 0)
			{
				runeCounts.merge(runeId, amount, Integer::sum);
				addCombinationRuneCounts(runeCounts, runeId, amount);
			}
		}
	}

	/**
	 * Convert varbit value to rune item ID.
	 */
	private int runeIdFromVarbit(int varbitValue)
	{
		switch (varbitValue)
		{
			case 1: return Constants.AIR_RUNE;
			case 2: return Constants.WATER_RUNE;
			case 3: return Constants.EARTH_RUNE;
			case 4: return Constants.FIRE_RUNE;
			case 5: return Constants.MIND_RUNE;
			case 6: return Constants.BODY_RUNE;
			case 7: return Constants.DEATH_RUNE;
			case 8: return 561; // Nature rune
			case 9: return Constants.CHAOS_RUNE;
			case 10: return 563; // Law rune
			case 11: return 564; // Cosmic rune
			case 12: return Constants.BLOOD_RUNE;
			case 13: return 566; // Soul rune
			case 14: return 9075; // Astral rune
			case 15: return Constants.MIST_RUNE;
			case 16: return Constants.MUD_RUNE;
			case 17: return Constants.DUST_RUNE;
			case 18: return Constants.LAVA_RUNE;
			case 19: return Constants.STEAM_RUNE;
			case 20: return Constants.SMOKE_RUNE;
			case 21: return Constants.WRATH_RUNE;
			default: return -1;
		}
	}

	/**
	 * Find a combination rune that provides the required element.
	 */
	private int findCombinationRuneFor(int elementRuneId, Set<Integer> availableComboRunes)
	{
		for (int comboId : availableComboRunes)
		{
			if (combinationRuneProvides(comboId, elementRuneId))
			{
				return comboId;
			}
		}
		return -1;
	}

	/**
	 * Check if a combination rune provides a specific element.
	 */
	private boolean combinationRuneProvides(int comboRuneId, int elementRuneId)
	{
		switch (comboRuneId)
		{
			case Constants.MIST_RUNE:
				return elementRuneId == Constants.AIR_RUNE || elementRuneId == Constants.WATER_RUNE;
			case Constants.DUST_RUNE:
				return elementRuneId == Constants.AIR_RUNE || elementRuneId == Constants.EARTH_RUNE;
			case Constants.MUD_RUNE:
				return elementRuneId == Constants.WATER_RUNE || elementRuneId == Constants.EARTH_RUNE;
			case Constants.SMOKE_RUNE:
				return elementRuneId == Constants.AIR_RUNE || elementRuneId == Constants.FIRE_RUNE;
			case Constants.STEAM_RUNE:
				return elementRuneId == Constants.WATER_RUNE || elementRuneId == Constants.FIRE_RUNE;
			case Constants.LAVA_RUNE:
				return elementRuneId == Constants.EARTH_RUNE || elementRuneId == Constants.FIRE_RUNE;
			default:
				return false;
		}
	}

	/**
	 * Deduplicate rune usage list (combo runes providing multiple elements).
	 */
	private List<int[]> deduplicateRuneUsage(List<int[]> runeUsage)
	{
		Map<Integer, Integer> seen = new LinkedHashMap<>();
		for (int[] entry : runeUsage)
		{
			int itemId = entry[0];
			int amount = entry[1];
			if (!seen.containsKey(itemId))
			{
				seen.put(itemId, amount);
			}
		}

		List<int[]> result = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : seen.entrySet())
		{
			result.add(new int[]{entry.getKey(), entry.getValue()});
		}
		return result;
	}
}
