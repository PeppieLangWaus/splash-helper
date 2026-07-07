package xyz.peppie.splashhelper.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * Fixed-size time series of player counts for one session.
 *
 * Samples are aggregated into time buckets of {@link #bucketSeconds} width,
 * storing the rounded average per bucket. When a session outgrows
 * {@link #MAX_BUCKETS}, adjacent buckets are merged and the bucket width
 * doubles, so any session length fits in a bounded, graph-friendly payload
 * (≤360 ints regardless of duration). Buckets with no samples (e.g. a logout
 * gap inside the resume window) are stored as {@link #NO_DATA} so graphs can
 * render them as breaks instead of fake values.
 */
public class PlayerCountSeries
{
	public static final int NO_DATA = -1;
	private static final int MAX_BUCKETS = 360;
	private static final int INITIAL_BUCKET_SECONDS = 10;

	@Getter
	private int bucketSeconds = INITIAL_BUCKET_SECONDS;

	private List<Integer> buckets = new ArrayList<>();

	// In-progress bucket accumulation; not persisted (finalized sessions are sealed)
	private transient int pendingIndex = -1;
	private transient long pendingSum = 0;
	private transient int pendingCount = 0;

	/**
	 * Record one player-count sample taken at the given offset from session start.
	 */
	public void addSample(long elapsedSeconds, int count)
	{
		if (elapsedSeconds < 0)
		{
			return;
		}

		int index = (int) (elapsedSeconds / bucketSeconds);
		while (index >= MAX_BUCKETS)
		{
			compress();
			index = (int) (elapsedSeconds / bucketSeconds);
		}

		if (index == pendingIndex)
		{
			pendingSum += count;
			pendingCount++;
		}
		else
		{
			flushPending();
			pendingIndex = index;
			pendingSum = count;
			pendingCount = 1;
		}
	}

	/**
	 * Flush the in-progress bucket. Call when the session is finalized.
	 */
	public void seal()
	{
		flushPending();
	}

	/**
	 * Snapshot of the finalized buckets (including the in-progress one), for
	 * serialization to the server. Never null.
	 */
	public List<Integer> getBuckets()
	{
		List<Integer> snapshot = new ArrayList<>(buckets);
		if (pendingCount > 0)
		{
			while (snapshot.size() < pendingIndex)
			{
				snapshot.add(NO_DATA);
			}
			snapshot.add((int) Math.round((double) pendingSum / pendingCount));
		}
		return snapshot;
	}

	private void flushPending()
	{
		if (pendingCount == 0)
		{
			return;
		}
		// Pad any skipped buckets (no samples arrived for them) as gaps
		while (buckets.size() < pendingIndex)
		{
			buckets.add(NO_DATA);
		}
		buckets.add((int) Math.round((double) pendingSum / pendingCount));
		pendingIndex = -1;
		pendingSum = 0;
		pendingCount = 0;
	}

	/**
	 * Halve the resolution: merge adjacent bucket pairs and double the bucket width.
	 */
	private void compress()
	{
		flushPending();
		List<Integer> merged = new ArrayList<>((buckets.size() + 1) / 2);
		for (int i = 0; i < buckets.size(); i += 2)
		{
			int a = buckets.get(i);
			int b = (i + 1 < buckets.size()) ? buckets.get(i + 1) : NO_DATA;
			if (a == NO_DATA && b == NO_DATA)
			{
				merged.add(NO_DATA);
			}
			else if (a == NO_DATA)
			{
				merged.add(b);
			}
			else if (b == NO_DATA)
			{
				merged.add(a);
			}
			else
			{
				merged.add((int) Math.round((a + b) / 2.0));
			}
		}
		buckets = merged;
		bucketSeconds *= 2;
	}
}
