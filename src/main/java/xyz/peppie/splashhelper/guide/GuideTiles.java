package xyz.peppie.splashhelper.guide;

import net.runelite.api.coords.WorldPoint;

/**
 * The named tiles for the sticky-knight setup, in world coordinates.
 *
 * <p>All tiles were provided in region-local form (region 10547, plane 0). Region 10547
 * has a world base of (2624, 3264): {@code base = ((id >> 8) << 6, (id & 0xFF) << 6)}.
 * So {@code world = (2624 + regionX, 3264 + regionY)}. Sanity check: the knight spawn
 * (regionX 45, regionY 34) resolves to (2669, 3298), matching the reported spawn tile.</p>
 */
public final class GuideTiles
{
	private GuideTiles() {}

	// Where the alt account stands (main only detects "a player is on this tile").
	public static final WorldPoint ALT = new WorldPoint(2647, 3290, 0);

	// Opening positions.
	public static final WorldPoint PLAYER = new WorldPoint(2652, 3289, 0);
	public static final WorldPoint POSITION = new WorldPoint(2651, 3290, 0);
	public static final WorldPoint KNIGHT = new WorldPoint(2651, 3289, 0);
	public static final WorldPoint KNIGHT_SPAWN = new WorldPoint(2669, 3298, 0);

	// Westward dragon-spear push lane (player rests one tile east of the knight).
	public static final WorldPoint DSPEAR1 = new WorldPoint(2650, 3289, 0);
	public static final WorldPoint DSPEAR2 = new WorldPoint(2649, 3289, 0);
	public static final WorldPoint DSPEAR3 = new WorldPoint(2648, 3289, 0);
	public static final WorldPoint DSPEAR4 = new WorldPoint(2647, 3289, 0);
	public static final WorldPoint DSPEAR5 = new WorldPoint(2646, 3289, 0);
	public static final WorldPoint DSPEAR6 = new WorldPoint(2645, 3289, 0);
	public static final WorldPoint DSPEAR7 = new WorldPoint(2644, 3289, 0);

	// Herding / trap phase.
	public static final WorldPoint PUSH = new WorldPoint(2647, 3280, 0);
	public static final WorldPoint STOP1 = new WorldPoint(2648, 3281, 0);
	public static final WorldPoint STOP2 = new WorldPoint(2655, 3285, 0);
	public static final WorldPoint PULL = new WorldPoint(2654, 3287, 0);
	public static final WorldPoint DSPEAR8 = new WorldPoint(2653, 3286, 0);
	public static final WorldPoint PRE_TRAP = new WorldPoint(2654, 3286, 0);
	public static final WorldPoint TRAP = new WorldPoint(2655, 3287, 0);
	public static final WorldPoint SPLASHER = new WorldPoint(2655, 3286, 0);
}
