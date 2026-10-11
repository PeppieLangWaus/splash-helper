package xyz.peppie.splashhelper.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
@Singleton
public class StackStatsCsvWriter
{
	private static final File DATA_DIR = new File(RuneLite.RUNELITE_DIR, "splashhelper/knightstack");
	private static final DateTimeFormatter FILENAME_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
		.withZone(ZoneId.systemDefault());

	private ExecutorService executor;
	private boolean logEvents;
	private File eventsFile;
	private File windowsFile;
	private File excursionsFile;

	public synchronized void start(boolean logEvents)
	{
		this.logEvents = logEvents;
		if (executor == null || executor.isShutdown())
		{
			executor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "splash-helper-knightstack-io");
				t.setDaemon(true);
				return t;
			});
		}

		String stamp = FILENAME_STAMP.format(Instant.now());
		eventsFile = new File(DATA_DIR, "events-" + stamp + ".csv");
		windowsFile = new File(DATA_DIR, "windows-" + stamp + ".csv");
		excursionsFile = new File(DATA_DIR, "excursions-" + stamp + ".csv");

		executor.execute(() -> {
			try
			{
				Files.createDirectories(DATA_DIR.toPath());
				if (logEvents)
				{
					writeHeader(eventsFile, "tick,timestamp,event_type,player_id,stack_size_after,window_id");
				}
				writeHeader(windowsFile, "window_id,start_tick,length,exposed_ticks,leavers,enters,knight_moved,"
					+ "move_tick,length_bucket,blocking_attempted");
				writeHeader(excursionsFile, "excursion_id,start_tick,end_tick,length_ticks,went_outside,"
					+ "stack_size_at_start,enters_during,leaves_during,outcome");
			}
			catch (IOException e)
			{
				log.error("Failed to initialize stack tracking CSV files", e);
			}
		});
	}

	public void writeEvent(StackStatsEngine.EventRow row)
	{
		if (!logEvents)
		{
			return;
		}
		String timestamp = Instant.ofEpochMilli(row.getEpochMillis()).toString();
		append(eventsFile, row.getTick() + "," + timestamp + "," + row.getType() + "," + row.getPlayerId() + ","
			+ row.getStackSizeAfter() + "," + (row.getWindowId() == null ? "" : row.getWindowId()));
	}

	public void writeWindow(StackStatsEngine.WindowRow row)
	{
		append(windowsFile, row.getWindowId() + "," + row.getStartTick() + "," + row.getLength() + ","
			+ row.getExposedTicks() + "," + row.getLeavers() + "," + row.getEnters() + "," + row.isKnightMoved() + ","
			+ (row.getMoveTick() == null ? "" : row.getMoveTick()) + "," + row.getBucket().getLabel() + ","
			+ row.isBlockingAttempted());
	}

	public void writeExcursion(StackStatsEngine.ExcursionRow row)
	{
		append(excursionsFile, row.getExcursionId() + "," + row.getStartTick() + "," + row.getEndTick() + ","
			+ row.getLengthTicks() + "," + row.isWentOutside() + ","
			+ row.getStackSizeAtStart() + "," + row.getEntersDuring() + "," + row.getLeavesDuring() + ","
			+ row.getOutcome());
	}

	private void writeHeader(File file, String header) throws IOException
	{
		Files.write(file.toPath(), (header + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private void append(File file, String line)
	{
		ExecutorService ex = this.executor;
		if (ex == null || ex.isShutdown() || file == null)
		{
			return;
		}
		ex.execute(() -> {
			try
			{
				Files.write(file.toPath(), (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			catch (IOException e)
			{
				log.error("Failed to append to {}", file, e);
			}
		});
	}

	public synchronized void shutdown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
		}
	}
}
