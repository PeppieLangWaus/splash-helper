package xyz.peppie.splashhelper.service;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import xyz.peppie.splashhelper.SplashHelperConfig;

@Singleton
public class StackStatsTracker
{
	@Inject
	private SplashHelperConfig config;

	@Inject
	private StackStatsCsvWriter csvWriter;

	private final StackStatsEngine engine = new StackStatsEngine(new RowSinkImpl());

	private boolean started = false;

	public void onGameTick(int tick, WorldPoint knightTile, WorldPoint stackTile, WorldPoint knightPos,
		Set<Integer> idsOnStackTile, Integer blockingResetterId)
	{
		if (!config.enableStackTracking() || knightTile == null || stackTile == null)
		{
			return;
		}

		if (!started)
		{
			csvWriter.start(config.logStackTrackingEvents());
			started = true;
		}

		engine.tick(tick, classify(knightPos, knightTile, stackTile), idsOnStackTile, System.currentTimeMillis(),
			blockingResetterId);
	}

	private StackStatsEngine.KnightLocation classify(WorldPoint knightPos, WorldPoint knightTile, WorldPoint stackTile)
	{
		if (knightPos == null)
		{
			return StackStatsEngine.KnightLocation.UNKNOWN;
		}
		if (knightPos.equals(knightTile))
		{
			return StackStatsEngine.KnightLocation.KNIGHT_TILE;
		}
		if (knightPos.equals(stackTile))
		{
			return StackStatsEngine.KnightLocation.STACK_TILE;
		}
		return StackStatsEngine.KnightLocation.OUTSIDE;
	}

	public void shutdown(int tick)
	{
		if (!started)
		{
			return;
		}
		engine.shutdown(tick);
		csvWriter.shutdown();
		started = false;
	}

	public void reset()
	{
		engine.reset();
	}

	public StackStatsEngine.Summary getSummary()
	{
		return engine.getSummary();
	}

	public static double perHour(long count, double hours)
	{
		return hours > 0 ? count / hours : 0;
	}

	private class RowSinkImpl implements StackStatsEngine.RowSink
	{
		@Override
		public void onEvent(StackStatsEngine.EventRow row)
		{
			csvWriter.writeEvent(row);
		}

		@Override
		public void onWindowClosed(StackStatsEngine.WindowRow row)
		{
			csvWriter.writeWindow(row);
		}

		@Override
		public void onExcursionClosed(StackStatsEngine.ExcursionRow row)
		{
			csvWriter.writeExcursion(row);
		}
	}
}
