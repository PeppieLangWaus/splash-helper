package xyz.peppie.splashhelper.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import xyz.peppie.splashhelper.util.Constants;

/**
 * Service responsible for detecting and analyzing the Knight of Ardougne NPC.
 * Handles sticky knight detection based on NPC IDs.
 */
@Singleton
public class KnightDetector
{
	@Inject
	public KnightDetector()
	{
	}

	/**
	 * Check if the given actor is a "sticky" knight (female variant).
	 * Sticky knights have a smaller clickbox and are preferred for splashing.
	 * Detection is based on NPC ID.
	 */
	public boolean isStickyKnight(Actor target)
	{
		if (target == null || !(target instanceof NPC))
		{
			return false;
		}

		NPC knight = (NPC) target;
		int npcId = knight.getId();

		return npcId == Constants.FEMALE_KNIGHT_NPC_ID;
	}

	/**
	 * Check if splashing conditions are met for the given knight and tiles.
	 */
	public boolean isSplashingConditionsMet(Actor target, WorldPoint knightTile1, WorldPoint knightTile2)
	{
		if (target == null)
		{
			return false;
		}

		if (knightTile1 == null || knightTile2 == null)
		{
			return false;
		}

		WorldPoint knightPos = target.getWorldLocation();
		if (knightPos == null)
		{
			return false;
		}

		return knightPos.equals(knightTile1) || knightPos.equals(knightTile2);
	}

	/**
	 * Check if knight has moved from its previous position.
	 */
	public boolean hasKnightMoved(Actor target, WorldPoint lastPosition)
	{
		if (target == null || lastPosition == null)
		{
			return false;
		}

		WorldPoint currentPosition = target.getWorldLocation();
		return currentPosition != null && !currentPosition.equals(lastPosition);
	}

	/**
	 * Check if knight is on one of the valid splash tiles.
	 */
	public boolean isOnValidTile(Actor target, WorldPoint tile1, WorldPoint tile2)
	{
		if (target == null)
		{
			return false;
		}

		WorldPoint knightPos = target.getWorldLocation();
		if (knightPos == null)
		{
			return false;
		}

		boolean onTile1 = tile1 != null && knightPos.equals(tile1);
		boolean onTile2 = tile2 != null && knightPos.equals(tile2);

		return onTile1 || onTile2;
	}
}
