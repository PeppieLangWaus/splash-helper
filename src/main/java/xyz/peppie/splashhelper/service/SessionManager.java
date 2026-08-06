package xyz.peppie.splashhelper.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.model.PersistedSession;
import xyz.peppie.splashhelper.model.SplashSession;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.util.Constants;

/**
 * Service responsible for managing splash session lifecycle.
 * Handles session creation, updates, finalization, and history.
 */
@Slf4j
@Singleton
public class SessionManager
{
	private final Client client;
	private final RuneCalculator runeCalculator;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final SplashHelperConfig config;
	private final Gson gson;

	/** The RSN whose history is currently loaded into {@link #sessionHistory}, or null if none is loaded yet. */
	private String currentHistoryUsername = null;

	@Getter
	private SplashSession currentSession = null;

	@Getter
	private SplashSession lastFinalizedSession = null;

	@Getter
	private final List<SplashSession> sessionHistory = new ArrayList<>();

	private int lastCastTick = -1;
	private int lastMagicXp = -1;

	private static final File HISTORY_DIR = new File(RuneLite.RUNELITE_DIR, "splashhelper");

	/** Pre-per-user shared history file, produced by an earlier version of this plugin. Migrated on startup. */
	private static final File LEGACY_SHARED_HISTORY_FILE = new File(HISTORY_DIR, "session-history.json");

	/** Even older history storage, in RSProfile config (limited to 100kb values). Migrated on startup. */
	private static final String LEGACY_CONFIG_GROUP = "splashhelper";
	private static final String LEGACY_CONFIG_KEY = "splashhelper_session_history";

	/** A recently finalized session that can be resumed if the same player returns quickly. */
	/* volatile: read from the Swing EDT for the resume-window countdown */
	private volatile SplashSession resumableSession = null;
	private volatile long resumableSessionExpiryMs = 0;

	/** Called after a new session is successfully started. */
	private Runnable sessionStartedCallback;

	/** Called with the finalized session when a session passes minimum duration and is saved. */
	private java.util.function.Consumer<SplashSession> sessionFinalizedCallback;

	public void setSessionStartedCallback(Runnable callback)
	{
		this.sessionStartedCallback = callback;
	}

	public void setSessionFinalizedCallback(java.util.function.Consumer<SplashSession> callback)
	{
		this.sessionFinalizedCallback = callback;
	}

	@Inject
	public SessionManager(Client client, RuneCalculator runeCalculator, ItemManager itemManager, ConfigManager configManager, SplashHelperConfig config, Gson gson)
	{
		this.client = client;
		this.runeCalculator = runeCalculator;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.config = config;
		this.gson = gson.newBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(Instant.class, new InstantSerializer())
			.registerTypeAdapter(Instant.class, new InstantDeserializer())
			.create();

		// One-time migration of history from older storage schemes into per-user files.
		// The current player's history is then loaded lazily once their RSN is known
		// (see ensureHistoryLoadedForUser), since it isn't available at construction time.
		migrateLegacyHistory();
	}

	/**
	 * Load (or switch to) the session history for the given RSN if it isn't already loaded.
	 * Safe to call repeatedly, e.g. on every login/hop, since a matching username is a no-op.
	 * If switching to a different user, any active session for the previous user is finalized first.
	 */
	public void ensureHistoryLoadedForUser(String username)
	{
		if (username == null || username.trim().isEmpty())
		{
			return;
		}

		String normalized = username.trim();
		if (currentHistoryUsername != null && currentHistoryUsername.equalsIgnoreCase(normalized))
		{
			return;
		}

		// Finalize any active session before switching users, to prevent cross-account
		// session pollution when multiple accounts are logged in simultaneously.
		if (currentSession != null && currentSession.isActive())
		{
			finalizeSession(false);
		}

		currentHistoryUsername = normalized;
		loadHistoryForCurrentUser();
		log.debug("Loaded session history for {}", currentHistoryUsername);
	}

	/**
	 * Start a new splash session, or resume the last one if the same player returns
	 * within the configured session resume window.
	 */
	public void startSession(String playerName, SplashSpell spell, Instant logoutTime,
							 int world, boolean stickyKnight)
	{
		ensureHistoryLoadedForUser(playerName);

		// Resume the last session if the same player returns within the resume window
		long resumeWindowMs = (long) config.sessionResumeWindowSeconds() * 1000;
		if (resumeWindowMs > 0
			&& resumableSession != null
			&& System.currentTimeMillis() < resumableSessionExpiryMs
			&& playerName != null
			&& playerName.equalsIgnoreCase(resumableSession.getPlayerName()))
		{
			resumeSession(resumableSession, logoutTime, world);
			return;
		}
		// Window missed/expired without being caught by checkResumableSessionExpiry() yet
		// (e.g. it lapsed on this very tick) - finalize it now instead of discarding silently.
		expireResumableSession();
		int startXp = client.getSkillExperience(Skill.MAGIC);
		lastMagicXp = startXp;
		lastCastTick = client.getTickCount();

		currentSession = new SplashSession(
			playerName,
			spell,
			logoutTime,
			world,
			stickyKnight,
			startXp
		);

		currentSession.setStartingRuneCount(runeCalculator.getRemainingCasts(spell));
		currentSession.setCurrentRuneCount(currentSession.getStartingRuneCount());

		log.debug("Started splash session for {} with spell {}", playerName,
			spell != null ? spell.getName() : "unknown");

		if (sessionStartedCallback != null)
		{
			sessionStartedCallback.run();
		}
	}

	/**
	 * Resume a previously finalized session, updating the retirement timer and world.
	 */
	private void resumeSession(SplashSession session, Instant newLogoutTime, int newWorld)
	{
		// The session may have already been persisted to history when it was finalized.
		// Remove it now so it is not duplicated when it is finalized again after resuming.
		if (sessionHistory.remove(session))
		{
			saveSessionHistory();
		}
		session.resume();
		session.setLogoutTime(newLogoutTime);
		session.setWorld(newWorld);
		currentSession = session;
		resumableSession = null;
		resumableSessionExpiryMs = 0;
		// Re-sync XP baseline so no spurious cast is counted for the gap
		lastMagicXp = client.getSkillExperience(Skill.MAGIC);
		lastCastTick = client.getTickCount();
		log.debug("Resumed splash session for {} (interrupted briefly)", session.getPlayerName());
		if (sessionStartedCallback != null)
		{
			sessionStartedCallback.run(); // re-sends SESSION_START to server
		}
	}

	/**
	 * Finalize the current session and optionally allow it to be resumed.
	 * Uses rune data already stored in the session (updated continuously while active).
	 */
	public void finalizeSession(boolean allowResume)
	{
		if (currentSession == null || !currentSession.isActive())
		{
			return;
		}

		currentSession.setEndTime(Instant.now());

		// Always capture the session so the backend can be notified, even if we
		// discard it locally for being below the minimum duration threshold.
		SplashSession toNotify = currentSession;
		SplashSession finalized = null;

		long durationSeconds = currentSession.getSessionDurationSeconds();
		int minimumDuration = config.minimumSessionDuration();

		if (durationSeconds < minimumDuration)
		{
			log.debug("Discarding short session locally: {}s < {}s minimum", durationSeconds, minimumDuration);
		}
		else
		{
			// Flush the in-progress player-count bucket before persisting
			if (currentSession.getPlayerCountSeries() != null)
			{
				currentSession.getPlayerCountSeries().seal();
			}

			sessionHistory.add(currentSession);
			lastFinalizedSession = currentSession;
			finalized = currentSession;

			// Persist session history to storage
			saveSessionHistory();

			log.debug("Finalized session: {} casts, {} XP gained, {} gp cost",
				currentSession.getSpellsCast(), currentSession.getMagicXpGained(), currentSession.getRuneCostGp());
		}

		// Only keep a finalized session resumable for short interruption flows.
		long resumeWindowMs = (long) config.sessionResumeWindowSeconds() * 1000;
		boolean stashedAsResumable = false;
		if (allowResume && resumeWindowMs > 0)
		{
			resumableSession = toNotify;
			resumableSessionExpiryMs = System.currentTimeMillis() + resumeWindowMs;
			stashedAsResumable = true;
		}
		else
		{
			resumableSession = null;
			resumableSessionExpiryMs = 0;
		}

		currentSession = null;
		lastMagicXp = -1;
		lastCastTick = -1;

		// Notify the backend only once the session is truly done, regardless of whether it
		// was persisted locally. A session stashed as resumable is NOT reported yet - it may
		// still be resumed by the same player - it is reported later by
		// checkResumableSessionExpiry()/expireResumableSession() once the window lapses (or
		// the plugin shuts down) without a resume happening.
		if (!stashedAsResumable && sessionFinalizedCallback != null)
		{
			sessionFinalizedCallback.accept(toNotify);
		}
	}

	/**
	 * Check if session should timeout due to inactivity.
	 * @return true if session was timed out
	 */
	public boolean checkSessionTimeout()
	{
		if (currentSession == null || !currentSession.isActive())
		{
			return false;
		}

		if (lastCastTick < 0)
		{
			return false;
		}

		int currentTick = client.getTickCount();
		int ticksSinceLastCast = currentTick - lastCastTick;
		if (ticksSinceLastCast >= Constants.SESSION_TIMEOUT_TICKS)
		{
			log.debug("Session timeout - no cast in {} ticks", ticksSinceLastCast);
			finalizeSession(true);
			return true;
		}

		return false;
	}

	/**
	 * Record a spell cast based on XP gain.
	 * @param currentXp Current magic XP
	 * @param spell The spell being cast
	 * @return XP gained from this cast, or 0 if no cast detected
	 */
	public int recordCast(int currentXp, SplashSpell spell)
	{
		if (lastMagicXp < 0)
		{
			lastMagicXp = currentXp;
			return 0;
		}

		int xpGained = currentXp - lastMagicXp;
		lastMagicXp = currentXp;

		if (xpGained > 0 && currentSession != null && currentSession.isActive())
		{
			currentSession.incrementSpellsCast();
			currentSession.setCurrentMagicXp(currentXp);

			if (spell != null)
			{
				currentSession.setCurrentRuneCount(runeCalculator.getRemainingCasts(spell));
				
				// Track rune usage for this specific cast
				java.util.List<int[]> runesUsedThisCast = runeCalculator.getActualRuneUsage(spell);
				
				// Create a deep copy to avoid reference issues
				java.util.List<int[]> runesCopy = new java.util.ArrayList<>();
				for (int[] rune : runesUsedThisCast)
				{
					runesCopy.add(new int[]{rune[0], rune[1]});
				}
				
				long costThisCast = calculateRuneCostForUsage(runesCopy);
				currentSession.addRuneUsageForCast(runesCopy, costThisCast);
			}

			lastCastTick = client.getTickCount();
			return xpGained;
		}

		return 0;
	}

	/**
	 * Calculate GP cost for a list of rune usage.
	 */
	private long calculateRuneCostForUsage(java.util.List<int[]> runeUsage)
	{
		if (runeUsage == null || runeUsage.isEmpty())
		{
			return 0;
		}

		long totalCost = 0;
		for (int[] usage : runeUsage)
		{
			int itemId = usage[0];
			int amount = usage[1];
			int price = itemManager.getItemPrice(itemId);
			totalCost += (long) price * amount;
		}
		return totalCost;
	}

	/**
	 * Update session spell if different.
	 */
	public void updateSpell(SplashSpell spell)
	{
		if (currentSession != null && spell != null && currentSession.getSpell() != spell)
		{
			currentSession.setSpell(spell);
		}
	}

	/**
	 * Update the sticky-knight flag for the active session when target detection changes.
	 */
	public void updateStickyKnight(boolean stickyKnight)
	{
		if (currentSession != null)
		{
			currentSession.setStickyKnight(stickyKnight);
		}
	}

	/**
	 * Check if there's an active session.
	 */
	public boolean hasActiveSession()
	{
		return currentSession != null && currentSession.isActive();
	}

	/**
	 * Get the session to display in UI - current if active, otherwise last finalized.
	 */
	public SplashSession getDisplayableSession()
	{
		if (currentSession != null)
		{
			return currentSession;
		}
		return lastFinalizedSession;
	}

	/**
	 * Record a knight movement in the current session.
	 */
	public void recordKnightMovement()
	{
		if (currentSession != null)
		{
			currentSession.incrementKnightMovements();
		}
	}

	/**
	 * Record a nearby player death in the current session.
	 */
	public void recordPlayerDeath()
	{
		if (currentSession != null)
		{
			currentSession.incrementPlayerDeaths();
		}
	}

	/**
	 * Add a pickpocketer to the current session.
	 */
	public void addPickpocketer(String playerName)
	{
		if (currentSession != null && playerName != null)
		{
			currentSession.addPickpocketer(playerName);
		}
	}

	/**
	 * Record a player count sample for the current session.
	 */
	public void recordPlayerCountSample(int count)
	{
		if (currentSession != null)
		{
			currentSession.addPlayerCountSample(count);
		}
	}

	/**
	 * Update the current session's live nearby-player count.
	 */
	public void updateCurrentPlayerCount(int count)
	{
		if (currentSession != null)
		{
			currentSession.setCurrentPlayerCount(count);
		}
	}

	/**
	 * Whether a recently finalized session is still within its resume window.
	 */
	public boolean hasResumableSession()
	{
		return resumableSession != null && System.currentTimeMillis() < resumableSessionExpiryMs;
	}

	/**
	 * Milliseconds until the resume window closes, or 0 if none is open.
	 */
	public long getResumableSessionRemainingMs()
	{
		if (!hasResumableSession())
		{
			return 0;
		}
		return Math.max(0, resumableSessionExpiryMs - System.currentTimeMillis());
	}

	/**
	 * Poll whether a pending resumable session's window has lapsed and, if so, finalize
	 * it to the backend. Intended to be called regularly (e.g. every game tick) so
	 * SESSION_END is still sent even if the player never returns to start a new session.
	 */
	public void checkResumableSessionExpiry()
	{
		if (resumableSession != null && System.currentTimeMillis() >= resumableSessionExpiryMs)
		{
			expireResumableSession();
		}
	}

	/**
	 * Finalize any pending resumable session to the backend right now, without waiting
	 * for its window to lapse naturally. Used when the window expiry is detected early
	 * (e.g. missed on the same tick a new session would have resumed it) and on plugin
	 * shutdown, where the resume window can no longer be observed expiring on its own.
	 */
	public void expireResumableSession()
	{
		SplashSession session = resumableSession;
		resumableSession = null;
		resumableSessionExpiryMs = 0;
		if (session != null && sessionFinalizedCallback != null)
		{
			sessionFinalizedCallback.accept(session);
		}
	}

	/**
	 * Save session history to the current user's file on disk.
	 * If pruning is enabled, removes the oldest sessions beyond the configured on-disk
	 * retention count. A session is only ever pruned once it is confirmed synced to the
	 * server (or immediately if server sync is disabled), so enabling sync never races
	 * with pruning to lose data that hasn't reached the server yet.
	 */
	private void saveSessionHistory()
	{
		if (currentHistoryUsername == null)
		{
			log.debug("Skipping session history save - no user loaded yet");
			return;
		}

		if (config.enableSessionPruning())
		{
			pruneSessionHistory();
		}

		List<PersistedSession> persistedSessions = new ArrayList<>();
		for (SplashSession session : sessionHistory)
		{
			persistedSessions.add(PersistedSession.fromSession(session));
		}

		writePersistedSessionsToFile(historyFileFor(currentHistoryUsername), persistedSessions);
		log.debug("Saved {} sessions to persistent storage for {}", sessionHistory.size(), currentHistoryUsername);
	}

	/**
	 * Load history for {@link #currentHistoryUsername} from its per-user file into {@link #sessionHistory}.
	 * Deduplicates sessions by comparing actual session data to handle corrupted history files.
	 */
	private void loadHistoryForCurrentUser()
	{
		sessionHistory.clear();

		List<PersistedSession> persistedSessions = readPersistedSessionsFromFile(historyFileFor(currentHistoryUsername));

		// Deduplicate by session content (not ID, since duplicates can have different UUIDs)
		Set<String> seenSessionHashes = new HashSet<>();
		List<PersistedSession> dedupedSessions = new ArrayList<>();
		boolean hadDuplicates = false;

		for (PersistedSession persisted : persistedSessions)
		{
			if (persisted.getSession() != null)
			{
				SplashSession session = persisted.getSession();
				// Hash based on the actual session data (player, timestamps, stats)
				// This catches exact duplicates even if they have different session IDs
				String sessionHash = createSessionHash(session);

				if (!seenSessionHashes.add(sessionHash))
				{
					// Duplicate found (same data, possibly different ID), skip it
					hadDuplicates = true;
					log.debug("Skipped duplicate session data during load: {} from {} to {}",
						session.getPlayerName(), session.getStartTime(), session.getEndTime());
				}
				else
				{
					sessionHistory.add(session);
					dedupedSessions.add(persisted);
				}
			}
		}

		// If we found and removed duplicates, rewrite the file to clean it up
		if (hadDuplicates)
		{
			log.info("Detected {} duplicate session(s) in history file for {}; cleaning up...",
				persistedSessions.size() - dedupedSessions.size(), currentHistoryUsername);
			writePersistedSessionsToFile(historyFileFor(currentHistoryUsername), dedupedSessions);
		}

		if (config.enableSessionPruning())
		{
			pruneSessionHistory();
		}
	}

	/**
	 * Create a hash of session data for duplicate detection.
	 * Two sessions with identical data (player, timestamps, stats) will produce the same hash.
	 */
	private String createSessionHash(SplashSession session)
	{
		// Use player name, start time, and end time to identify unique sessions
		// This detects exact duplicates even if they have different session IDs
		return session.getPlayerName() + "|" +
			   session.getStartTime() + "|" +
			   session.getEndTime() + "|" +
			   session.getSpellsCast() + "|" +
			   session.getStartMagicXp();
	}

	/**
	 * The per-user history file for a given RSN. Usernames are case-insensitive in-game,
	 * so the filename is normalized to avoid two files for the same account.
	 */
	private static File historyFileFor(String username)
	{
		return new File(HISTORY_DIR, "session-history-" + sanitizeUsernameForFilename(username) + ".json");
	}

	private static String sanitizeUsernameForFilename(String username)
	{
		String normalized = username.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		return normalized.isEmpty() ? "unknown" : normalized;
	}

	/**
	 * Read a list of persisted sessions from a JSON file, or an empty list if it doesn't
	 * exist, is empty, or fails to parse.
	 */
	private List<PersistedSession> readPersistedSessionsFromFile(File file)
	{
		try
		{
			if (!file.isFile())
			{
				return new ArrayList<>();
			}

			String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			if (json.trim().isEmpty())
			{
				return new ArrayList<>();
			}

			java.lang.reflect.Type type = new TypeToken<List<PersistedSession>>(){}.getType();
			List<PersistedSession> persistedSessions = gson.fromJson(json, type);
			return persistedSessions != null ? persistedSessions : new ArrayList<>();
		}
		catch (Exception e)
		{
			log.error("Failed to read session history from {}", file, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Write a list of persisted sessions to a JSON file, via a temp file + atomic move so a
	 * crash mid-write can't leave a corrupt/truncated history file behind.
	 */
	private void writePersistedSessionsToFile(File file, List<PersistedSession> persistedSessions)
	{
		try
		{
			File parentDir = file.getParentFile();
			Files.createDirectories(parentDir.toPath());

			String json = gson.toJson(persistedSessions);
			File tempFile = File.createTempFile("session-history", ".tmp", parentDir);
			Files.write(tempFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
			Files.move(tempFile.toPath(), file.toPath(),
				StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.error("Failed to write session history to {}", file, e);
		}
	}

	/**
	 * One-time migration of session history from older storage schemes (RSProfile config,
	 * then a single shared local file) into the current per-user local files. Each legacy
	 * session is routed to the file matching its own recorded player name, since a single
	 * legacy blob may contain sessions for more than one account.
	 */
	private void migrateLegacyHistory()
	{
		migrateLegacyConfigHistory();
		migrateLegacySharedFileHistory();
	}

	private void migrateLegacyConfigHistory()
	{
		try
		{
			String json = configManager.getConfiguration(LEGACY_CONFIG_GROUP, LEGACY_CONFIG_KEY);
			if (json == null || json.trim().isEmpty())
			{
				return;
			}

			java.lang.reflect.Type type = new TypeToken<List<PersistedSession>>(){}.getType();
			List<PersistedSession> legacySessions = gson.fromJson(json, type);
			if (legacySessions != null && !legacySessions.isEmpty())
			{
				distributeSessionsToUserFiles(legacySessions);
				log.info("Migrated {} session(s) from RSProfile config storage to per-user local files", legacySessions.size());
			}

			configManager.unsetConfiguration(LEGACY_CONFIG_GROUP, LEGACY_CONFIG_KEY);
		}
		catch (Exception e)
		{
			log.error("Failed to migrate legacy config-based session history", e);
		}
	}

	private void migrateLegacySharedFileHistory()
	{
		if (!LEGACY_SHARED_HISTORY_FILE.isFile())
		{
			return;
		}

		try
		{
			List<PersistedSession> legacySessions = readPersistedSessionsFromFile(LEGACY_SHARED_HISTORY_FILE);
			if (!legacySessions.isEmpty())
			{
				distributeSessionsToUserFiles(legacySessions);
				log.info("Migrated {} session(s) from shared local history file to per-user files", legacySessions.size());
			}
		}
		finally
		{
			try
			{
				Files.deleteIfExists(LEGACY_SHARED_HISTORY_FILE.toPath());
			}
			catch (IOException e)
			{
				log.error("Failed to remove migrated legacy shared history file", e);
			}
		}
	}

	/**
	 * Merge a batch of legacy persisted sessions into the appropriate per-user files,
	 * grouped by each session's own recorded player name, deduplicated by session ID.
	 */
	private void distributeSessionsToUserFiles(List<PersistedSession> legacySessions)
	{
		Map<String, List<PersistedSession>> byUser = new LinkedHashMap<>();
		for (PersistedSession persisted : legacySessions)
		{
			String name = persisted.getSession() != null ? persisted.getSession().getPlayerName() : null;
			String key = (name == null || name.trim().isEmpty()) ? "unknown" : name.trim();
			byUser.computeIfAbsent(key, k -> new ArrayList<>()).add(persisted);
		}

		for (Map.Entry<String, List<PersistedSession>> entry : byUser.entrySet())
		{
			File targetFile = historyFileFor(entry.getKey());
			List<PersistedSession> merged = readPersistedSessionsFromFile(targetFile);

			Set<String> existingIds = new HashSet<>();
			for (PersistedSession persisted : merged)
			{
				if (persisted.getSessionId() != null)
				{
					existingIds.add(persisted.getSessionId());
				}
			}

			for (PersistedSession persisted : entry.getValue())
			{
				if (persisted.getSessionId() == null || existingIds.add(persisted.getSessionId()))
				{
					merged.add(persisted);
				}
			}

			merged.sort(Comparator.comparingLong(PersistedSession::getCreatedTimestamp));
			prunePersistedSessions(merged);

			writePersistedSessionsToFile(targetFile, merged);
		}
	}

	/**
	 * Same retention policy as {@link #pruneSessionHistory()} (opt-in, sync-aware), applied
	 * to an arbitrary oldest-first list of {@link PersistedSession} rather than the live
	 * {@link #sessionHistory} field. Used when merging migrated history for an account that
	 * may not be the currently loaded user.
	 */
	private void prunePersistedSessions(List<PersistedSession> sessions)
	{
		if (!config.enableSessionPruning())
		{
			return;
		}

		int maxStored = Math.max(1, config.maxStoredSessions());
		boolean requireSync = config.enableServerSync();
		while (sessions.size() > maxStored)
		{
			PersistedSession oldest = sessions.get(0);
			if (requireSync && !oldest.isSyncedToServer())
			{
				break;
			}
			sessions.remove(0);
		}
	}

	/**
	 * Trim {@link #sessionHistory} down to the configured retention count, oldest first.
	 * A session is only removed once it is safe to lose: either server sync is disabled
	 * entirely, or the session has been confirmed synced to the server. Pruning stops as
	 * soon as it reaches a session that isn't synced yet, so unsynced sessions are never
	 * silently deleted out from under an in-progress (or not-yet-attempted) sync.
	 */
	private void pruneSessionHistory()
	{
		int maxStored = Math.max(1, config.maxStoredSessions());
		boolean requireSync = config.enableServerSync();
		while (sessionHistory.size() > maxStored)
		{
			SplashSession oldest = sessionHistory.get(0);
			if (requireSync && !oldest.isSynced())
			{
				log.debug("Pausing session pruning: oldest session for {} is not yet synced to server", oldest.getPlayerName());
				break;
			}
			sessionHistory.remove(0);
		}
	}

	/**
	 * Finalized sessions in history that have not yet been confirmed synced to the server.
	 * Used to retry SESSION_END delivery after a (re)connect so pruning can eventually
	 * catch up on sessions that were finalized while offline.
	 */
	public List<SplashSession> getUnsyncedSessions()
	{
		List<SplashSession> unsynced = new ArrayList<>();
		for (SplashSession session : sessionHistory)
		{
			// Exclude a session still sitting in its resume window: it hasn't been reported
			// to the backend as ended yet on purpose (it may still be resumed), so a
			// reconnect happening mid-window must not prematurely notify it as finalized.
			if (!session.isSynced() && session != resumableSession)
			{
				unsynced.add(session);
			}
		}
		return unsynced;
	}

	/**
	 * Delete a specific session from history.
	 */
	public void deleteSession(SplashSession session)
	{
		if (session != null && sessionHistory.remove(session))
		{
			saveSessionHistory();
			log.debug("Deleted session from history: {}", session.getPlayerName());
		}
	}

	/**
	 * Export all session history as a JSON string for REST API upload.
	 * @return JSON string of all persisted sessions
	 */
	public String exportSessionsAsJson()
	{
		List<PersistedSession> persistedSessions = new ArrayList<>();
		for (SplashSession session : sessionHistory)
		{
			persistedSessions.add(PersistedSession.fromSession(session));
		}

		// Include active session if present
		if (currentSession != null && currentSession.isActive())
		{
			persistedSessions.add(PersistedSession.fromSession(currentSession));
		}

		return gson.toJson(persistedSessions);
	}

	/**
	 * Export all session history to a file.
	 * @param file The file to write to
	 * @throws java.io.IOException if writing fails
	 */
	public void exportSessionsToFile(java.io.File file) throws java.io.IOException
	{
		String json = exportSessionsAsJson();
		try (java.io.FileWriter writer = new java.io.FileWriter(file))
		{
			writer.write(json);
		}
		log.debug("Exported {} sessions to {}", sessionHistory.size(), file.getAbsolutePath());
	}

	/**
	 * Custom serializer for Instant to avoid Java module access issues.
	 */
	private static class InstantSerializer implements JsonSerializer<Instant>
	{
		@Override
		public JsonElement serialize(Instant src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context)
		{
			return new JsonPrimitive(src.toString());
		}
	}

	/**
	 * Custom deserializer for Instant to avoid Java module access issues.
	 */
	private static class InstantDeserializer implements JsonDeserializer<Instant>
	{
		@Override
		public Instant deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context)
		{
			return Instant.parse(json.getAsString());
		}
	}

}
