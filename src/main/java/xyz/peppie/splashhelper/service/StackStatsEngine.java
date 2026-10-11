package xyz.peppie.splashhelper.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class StackStatsEngine
{
	public enum KnightLocation
	{
		KNIGHT_TILE, STACK_TILE, OUTSIDE, UNKNOWN
	}

	public enum StackEventType
	{
		ENTER, LEAVE, DESPAWN
	}

	public enum ExcursionFirstStep
	{
		STACK_TILE, OUTSIDE
	}

	public enum ExcursionOutcome
	{
		COMPLETED, UNKNOWN
	}

	public enum WindowCloseReason
	{
		ENTER_EVENT, SHUTDOWN
	}

	@Getter
	@RequiredArgsConstructor
	public enum WindowLengthBucket
	{
		SHORT("1-2"), MEDIUM("3-60"), LONG(">60");

		private final String label;

		public static WindowLengthBucket forLength(int lengthTicks)
		{
			if (lengthTicks <= 2)
			{
				return SHORT;
			}
			return lengthTicks <= 60 ? MEDIUM : LONG;
		}
	}

	public interface RowSink
	{
		void onEvent(EventRow row);

		void onWindowClosed(WindowRow row);

		void onExcursionClosed(ExcursionRow row);
	}

	@Getter
	@AllArgsConstructor
	public static final class EventRow
	{
		private final int tick;
		private final long epochMillis;
		private final StackEventType type;
		private final int playerId;
		private final int stackSizeAfter;
		private final Long windowId;
	}

	@Getter
	@AllArgsConstructor
	public static final class WindowRow
	{
		private final long windowId;
		private final int startTick;
		private final int length;
		private final int exposedTicks;
		private final int leavers;
		private final int enters;
		private final boolean knightMoved;
		private final Integer moveTick;
		private final WindowLengthBucket bucket;
		private final WindowCloseReason closeReason;
		private final boolean blockingAttempted;
	}

	@Getter
	@AllArgsConstructor
	public static final class ExcursionRow
	{
		private final long excursionId;
		private final int startTick;
		private final int endTick;
		private final int lengthTicks;
		private final ExcursionFirstStep firstStep;
		private final boolean wentOutside;
		private final int stackSizeAtStart;
		private final int entersDuring;
		private final int leavesDuring;
		private final ExcursionOutcome outcome;
	}

	public static final class BucketStat
	{
		public int windowCount;
		public long exposedTicks;
		public long movesStarted;
	}

	@Getter
	@AllArgsConstructor
	public static final class Summary
	{
		private final long ticksObserved;
		private final long flagClearTicks;
		private final long exposedTicks;
		private final long excursionCount;
		private final Map<WindowLengthBucket, BucketStat> bucketStats;
		private final double meanExcursionLength;
		private final double medianExcursionLength;
	}

	private static final class OpenWindow
	{
		final long id;
		final int startTick;
		int exposedTicks;
		int leavers;
		int enters;
		boolean knightMoved;
		Integer moveTick;
		boolean blockingAttempted;

		OpenWindow(long id, int startTick)
		{
			this.id = id;
			this.startTick = startTick;
		}
	}

	private static final class OpenExcursion
	{
		final long id;
		final int startTick;
		final ExcursionFirstStep firstStep;
		boolean wentOutside;
		final int stackSizeAtStart;
		int entersDuring;
		int leavesDuring;

		OpenExcursion(long id, int startTick, ExcursionFirstStep firstStep, boolean wentOutside, int stackSizeAtStart)
		{
			this.id = id;
			this.startTick = startTick;
			this.firstStep = firstStep;
			this.wentOutside = wentOutside;
			this.stackSizeAtStart = stackSizeAtStart;
		}
	}

	private final RowSink sink;

	private Set<Integer> prevStackIds = new TreeSet<>();
	private boolean hasSeenFirstEnter = false;
	private OpenWindow openWindow = null;
	private long nextWindowId = 1;

	private KnightLocation prevKnightLocation = KnightLocation.UNKNOWN;
	private OpenExcursion openExcursion = null;
	private long nextExcursionId = 1;

	private long ticksObserved = 0;
	private long flagClearTicks = 0;
	private long exposedTicks = 0;
	private long excursionCount = 0;
	private final Map<WindowLengthBucket, BucketStat> bucketStats = new EnumMap<>(WindowLengthBucket.class);
	private final List<Integer> excursionLengths = new ArrayList<>();
	private final Map<Long, Integer> pendingExcursionStartsByWindow = new HashMap<>();

	public StackStatsEngine(RowSink sink)
	{
		this.sink = sink;
	}

	public void tick(int tick, KnightLocation knightLocation, Set<Integer> idsOnStackTile, Set<Integer> allIdsInScene,
		long epochMillis, Integer blockingResetterId)
	{
		if (knightLocation != KnightLocation.UNKNOWN)
		{
			ticksObserved++;
		}

		if (openExcursion != null && knightLocation == KnightLocation.UNKNOWN)
		{
			closeExcursion(tick, ExcursionOutcome.UNKNOWN);
		}
		else
		{
			if (openExcursion == null && knightLocation != KnightLocation.UNKNOWN
				&& knightLocation != KnightLocation.KNIGHT_TILE
				&& prevKnightLocation == KnightLocation.KNIGHT_TILE)
			{
				startExcursion(tick, knightLocation);
			}

			if (openExcursion != null && knightLocation == KnightLocation.OUTSIDE)
			{
				openExcursion.wentOutside = true;
			}
		}

		processStackDiff(tick, idsOnStackTile, allIdsInScene, epochMillis);

		if (openWindow != null && blockingResetterId != null && idsOnStackTile.contains(blockingResetterId))
		{
			openWindow.blockingAttempted = true;
		}

		if (openWindow != null)
		{
			flagClearTicks++;
			if (knightLocation == KnightLocation.KNIGHT_TILE)
			{
				openWindow.exposedTicks++;
				exposedTicks++;
			}
		}

		if (openExcursion != null && knightLocation == KnightLocation.KNIGHT_TILE && openExcursion.entersDuring >= 1)
		{
			closeExcursion(tick, ExcursionOutcome.COMPLETED);
		}

		prevKnightLocation = knightLocation;
		prevStackIds = new TreeSet<>(idsOnStackTile);
	}

	private void processStackDiff(int tick, Set<Integer> idsOnStackTile, Set<Integer> allIdsInScene, long epochMillis)
	{
		Set<Integer> leftIds = new TreeSet<>(prevStackIds);
		leftIds.removeAll(idsOnStackTile);
		Set<Integer> enteredIds = new TreeSet<>(idsOnStackTile);
		enteredIds.removeAll(prevStackIds);

		int runningStackSize = prevStackIds.size();

		for (int id : leftIds)
		{
			runningStackSize--;
			if (!allIdsInScene.contains(id))
			{
				sink.onEvent(new EventRow(tick, epochMillis, StackEventType.DESPAWN, id, runningStackSize, currentWindowId()));
				continue;
			}

			if (openExcursion != null)
			{
				openExcursion.leavesDuring++;
			}
			if (openWindow == null && hasSeenFirstEnter)
			{
				openWindow = new OpenWindow(nextWindowId++, tick);
			}
			if (openWindow != null)
			{
				openWindow.leavers++;
			}

			sink.onEvent(new EventRow(tick, epochMillis, StackEventType.LEAVE, id, runningStackSize, currentWindowId()));
		}

		for (int id : enteredIds)
		{
			runningStackSize++;
			hasSeenFirstEnter = true;

			if (openExcursion != null)
			{
				openExcursion.entersDuring++;
			}
			if (openWindow != null)
			{
				openWindow.enters++;
			}

			sink.onEvent(new EventRow(tick, epochMillis, StackEventType.ENTER, id, runningStackSize, currentWindowId()));

			if (openWindow != null)
			{
				closeWindow(tick, WindowCloseReason.ENTER_EVENT);
			}
		}
	}

	private void startExcursion(int tick, KnightLocation location)
	{
		ExcursionFirstStep firstStep = location == KnightLocation.STACK_TILE
			? ExcursionFirstStep.STACK_TILE : ExcursionFirstStep.OUTSIDE;
		openExcursion = new OpenExcursion(nextExcursionId++, tick, firstStep, location == KnightLocation.OUTSIDE,
			prevStackIds.size());
		excursionCount++;

		if (openWindow != null)
		{
			if (!openWindow.knightMoved)
			{
				openWindow.knightMoved = true;
				openWindow.moveTick = tick;
			}
			pendingExcursionStartsByWindow.merge(openWindow.id, 1, Integer::sum);
		}
	}

	private void closeWindow(int tick, WindowCloseReason reason)
	{
		OpenWindow w = openWindow;
		int length = Math.max(1, tick - w.startTick);
		WindowLengthBucket bucket = WindowLengthBucket.forLength(length);
		int movesStarted = pendingExcursionStartsByWindow.getOrDefault(w.id, 0);
		pendingExcursionStartsByWindow.remove(w.id);

		BucketStat stat = bucketStats.computeIfAbsent(bucket, b -> new BucketStat());
		stat.windowCount++;
		stat.exposedTicks += w.exposedTicks;
		stat.movesStarted += movesStarted;

		sink.onWindowClosed(new WindowRow(w.id, w.startTick, length, w.exposedTicks, w.leavers, w.enters,
			w.knightMoved, w.moveTick, bucket, reason, w.blockingAttempted));
		openWindow = null;
	}

	private void closeExcursion(int tick, ExcursionOutcome outcome)
	{
		OpenExcursion ex = openExcursion;
		int length = Math.max(0, tick - ex.startTick);
		excursionLengths.add(length);

		sink.onExcursionClosed(new ExcursionRow(ex.id, ex.startTick, tick, length, ex.firstStep, ex.wentOutside,
			ex.stackSizeAtStart, ex.entersDuring, ex.leavesDuring, outcome));
		openExcursion = null;
	}

	private Long currentWindowId()
	{
		return openWindow == null ? null : openWindow.id;
	}

	public void shutdown(int tick)
	{
		if (openWindow != null)
		{
			closeWindow(tick, WindowCloseReason.SHUTDOWN);
		}
		if (openExcursion != null)
		{
			closeExcursion(tick, ExcursionOutcome.UNKNOWN);
		}
	}

	public void reset()
	{
		prevStackIds = new TreeSet<>();
		hasSeenFirstEnter = false;
		openWindow = null;
		nextWindowId = 1;
		prevKnightLocation = KnightLocation.UNKNOWN;
		openExcursion = null;
		nextExcursionId = 1;
		ticksObserved = 0;
		flagClearTicks = 0;
		exposedTicks = 0;
		excursionCount = 0;
		bucketStats.clear();
		excursionLengths.clear();
		pendingExcursionStartsByWindow.clear();
	}

	public Summary getSummary()
	{
		double mean = 0;
		double median = 0;
		if (!excursionLengths.isEmpty())
		{
			List<Integer> sorted = new ArrayList<>(excursionLengths);
			Collections.sort(sorted);
			int n = sorted.size();
			mean = sorted.stream().mapToInt(Integer::intValue).average().orElse(0);
			median = (n % 2 == 1) ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
		}

		return new Summary(ticksObserved, flagClearTicks, exposedTicks, excursionCount,
			Collections.unmodifiableMap(bucketStats), mean, median);
	}
}
