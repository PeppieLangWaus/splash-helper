package xyz.peppie.splashhelper.guide;

import net.runelite.api.coords.WorldPoint;

/**
 * A predicate that decides when a {@link GuideStep.StepPhase} is complete and the guide
 * should advance. Factory methods keep the step definitions declarative and readable.
 */
@FunctionalInterface
public interface AdvanceCondition
{
	boolean isMet(GuideContext ctx);

	// ==================== Position ====================

	static AdvanceCondition playerOn(WorldPoint tile)
	{
		return ctx -> ctx.playerOn(tile);
	}

	static AdvanceCondition knightOn(WorldPoint tile)
	{
		return ctx -> ctx.knightOn(tile);
	}

	static AdvanceCondition knightOnAny(WorldPoint... tiles)
	{
		return ctx ->
		{
			for (WorldPoint tile : tiles)
			{
				if (ctx.knightOn(tile))
				{
					return true;
				}
			}
			return false;
		};
	}

	/** Player on {@code playerTile} and knight on {@code knightTile} simultaneously. */
	static AdvanceCondition playerAndKnight(WorldPoint playerTile, WorldPoint knightTile)
	{
		return ctx -> ctx.playerOn(playerTile) && ctx.knightOn(knightTile);
	}

	static AdvanceCondition otherPlayerOn(WorldPoint tile)
	{
		return ctx -> ctx.otherPlayerOn(tile);
	}

	// ==================== Special attack ====================

	static AdvanceCondition specArmed()
	{
		return GuideContext::specArmed;
	}

	static AdvanceCondition specDropped()
	{
		return GuideContext::specDroppedSincePhaseStart;
	}

	static AdvanceCondition specEmpty()
	{
		return ctx -> ctx.specEnergy() <= 0;
	}

	static AdvanceCondition specFull()
	{
		return ctx -> ctx.specEnergy() >= GuideConstants.SPEC_FULL;
	}

	// ==================== Hitsplats ====================

	/** A landed hit of at least {@code min} damage on the knight (e.g. entangle bound). */
	static AdvanceCondition knightHit(int min)
	{
		return ctx ->
		{
			Integer hit = ctx.pendingKnightHit();
			return hit != null && hit >= min;
		};
	}

	/** A magic splash on the knight (the splash spot-anim; splashes produce no hitsplat). */
	static AdvanceCondition knightSplash()
	{
		return GuideContext::knightSplashed;
	}

	/** Any interaction landing on the knight — a splash or a damaging/binding hit. */
	static AdvanceCondition knightAnyHit()
	{
		return ctx -> ctx.knightSplashed() || ctx.pendingKnightHit() != null;
	}

	// ==================== Equipment ====================

	static AdvanceCondition equipmentSlotsEmpty(int... equipmentSlotIdx)
	{
		return ctx ->
		{
			for (int slot : equipmentSlotIdx)
			{
				if (!ctx.equipmentSlotEmpty(slot))
				{
					return false;
				}
			}
			return true;
		};
	}

	static AdvanceCondition itemEquipped(int itemId)
	{
		return ctx -> ctx.itemEquipped(itemId);
	}

	static AdvanceCondition gearRestored()
	{
		return GuideContext::gearRestored;
	}

	/** Never auto-completes; the player advances this phase with the manual "next" hotkey. */
	static AdvanceCondition manual()
	{
		return ctx -> false;
	}
}
