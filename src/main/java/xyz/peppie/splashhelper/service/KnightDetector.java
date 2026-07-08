package xyz.peppie.splashhelper.service;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
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
}
