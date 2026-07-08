package xyz.peppie.splashhelper.guide;

import net.runelite.api.coords.WorldPoint;

/**
 * A snapshot-ish view of game state that {@link AdvanceCondition}s query.
 *
 * <p>The engine implements this against the live client so the step definitions never
 * touch RuneLite plumbing directly. All "on tile" checks compare exact world tiles.</p>
 */
public interface GuideContext
{
	/** True if the local player is standing exactly on {@code tile}. */
	boolean playerOn(WorldPoint tile);

	/** True if the tracked sticky knight is standing exactly on {@code tile}. */
	boolean knightOn(WorldPoint tile);

	/** True if any player other than the local player is standing on {@code tile} (used for the alt). */
	boolean otherPlayerOn(WorldPoint tile);

	/** Special attack energy, 0..1000 (25% == 250). */
	int specEnergy();

	/** Whether the special attack is armed for the next attack. */
	boolean specArmed();

	/** True once spec energy has dropped since the current phase began (i.e. a spec just fired). */
	boolean specDroppedSincePhaseStart();

	/** True if the given equipment slot (EquipmentInventorySlot index) is empty. */
	boolean equipmentSlotEmpty(int equipmentSlotIdx);

	/** True if the given item id is currently equipped. */
	boolean itemEquipped(int itemId);

	/** True if current equipment matches the splasher-gear snapshot captured at step 1. */
	boolean gearRestored();

	/**
	 * Amount of the most recent hitsplat applied to the knight since the current phase
	 * began, or {@code null} if none. Only a landed hit (e.g. entangle/snare binding)
	 * produces a hitsplat; a magic splash produces no hitsplat at all — use
	 * {@link #knightSplashed()} for that.
	 */
	Integer pendingKnightHit();

	/**
	 * True if the knight has shown the magic-splash spot animation since the current phase
	 * began. A splash never produces a hitsplat, so this is the only reliable splash signal.
	 */
	boolean knightSplashed();
}
