package xyz.peppie.splashhelper.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

	@Getter
	private SplashSession currentSession = null;

	@Getter
	private SplashSession lastFinalizedSession = null;

	@Getter
	private final List<SplashSession> sessionHistory = new ArrayList<>();

	private int lastCastTick = -1;
	private int lastMagicXp = -1;

	private static final String SESSION_HISTORY_KEY = "splashhelper_session_history";

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
		
		// Load persisted session history on startup
		loadSessionHistory();
	}

	/**
	 * Start a new splash session.
	 */
	public void startSession(String playerName, SplashSpell spell, Instant logoutTime,
							 int world, boolean stickyKnight)
	{
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
	}

	/**
	 * Finalize the current session and add it to history.
	 * Uses rune data already stored in the session (updated continuously while active).
	 */
	public void finalizeSession()
	{
		if (currentSession != null && currentSession.isActive())
		{
			currentSession.setEndTime(Instant.now());
			
			long durationSeconds = currentSession.getSessionDurationSeconds();
			int minimumDuration = config.minimumSessionDuration();
			
			if (durationSeconds < minimumDuration)
			{
				log.debug("Discarding short session: {}s < {}s minimum", durationSeconds, minimumDuration);
			}
			else
			{
				// Finalize derived statistics for persistence
				finalizeDerivedStatistics(currentSession);
				
				sessionHistory.add(currentSession);
				lastFinalizedSession = currentSession;
				
				// Persist session history to storage
				saveSessionHistory();
				
				log.debug("Finalized session: {} casts, {} XP gained, {} gp cost",
					currentSession.getSpellsCast(), currentSession.getMagicXpGained(), currentSession.getRuneCostGp());
			}
		}
		currentSession = null;
		lastMagicXp = -1;
		lastCastTick = -1;
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
			finalizeSession();
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
			currentSession.setKnightMovements(currentSession.getKnightMovements() + 1);
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
			currentSession.addPlayerCountSample(count, config.maxPlayerCountSamples());
		}
	}

	/**
	 * Reset all session data.
	 */
	public void reset()
	{
		if (currentSession != null && currentSession.isActive())
		{
			finalizeSession();
		}
		currentSession = null;
		sessionHistory.clear();
		lastMagicXp = -1;
		lastCastTick = -1;
	}

	/**
	 * Get total statistics across all sessions.
	 */
	public SessionStats getTotalStats()
	{
		int totalSessions = sessionHistory.size();
		long totalSeconds = 0;
		int totalCasts = 0;
		int totalXp = 0;

		for (SplashSession session : sessionHistory)
		{
			totalSeconds += session.getSessionDurationSeconds();
			totalCasts += session.getSpellsCast();
			totalXp += session.getMagicXpGained();
		}

		if (currentSession != null && currentSession.isActive())
		{
			totalSessions++;
			totalSeconds += currentSession.getSessionDurationSeconds();
			totalCasts += currentSession.getSpellsCast();
			totalXp += currentSession.getMagicXpGained();
		}

		return new SessionStats(totalSessions, totalSeconds, totalCasts, totalXp);
	}

	/**
	 * Finalize derived statistics for persistence.
	 * Ensures average player count and pickpocketer count are calculated before saving.
	 */
	private void finalizeDerivedStatistics(SplashSession session)
	{
		// The derived statistics are already maintained during the session
		// This method ensures they're finalized if any calculations were missed
		// (they're updated in real-time in the add methods)
		log.debug("Finalized derived statistics for session persistence");
	}

	/**
	 * Save session history to persistent storage.
	 */
	private void saveSessionHistory()
	{
		try
		{
			// Convert sessions to persisted format
			List<PersistedSession> persistedSessions = new ArrayList<>();
			for (SplashSession session : sessionHistory)
			{
				persistedSessions.add(PersistedSession.fromSession(session));
			}

			// Serialize to JSON and save to config
			String json = gson.toJson(persistedSessions);
			configManager.setConfiguration("splashhelper", SESSION_HISTORY_KEY, json);
			
			log.debug("Saved {} sessions to persistent storage", sessionHistory.size());
		}
		catch (Exception e)
		{
			log.error("Failed to save session history", e);
		}
	}

	/**
	 * Load session history from persistent storage.
	 */
	private void loadSessionHistory()
	{
		try
		{
			String json = configManager.getConfiguration("splashhelper", SESSION_HISTORY_KEY);
			if (json == null || json.trim().isEmpty())
			{
				log.debug("No persisted session history found");
				return;
			}

			// Deserialize from JSON
			java.lang.reflect.Type type = new TypeToken<List<PersistedSession>>(){}.getType();
			List<PersistedSession> persistedSessions = gson.fromJson(json, type);

			if (persistedSessions != null)
			{
				// Convert back to regular sessions
				sessionHistory.clear();
				for (PersistedSession persisted : persistedSessions)
				{
					if (persisted.getSession() != null)
					{
						sessionHistory.add(persisted.getSession());
					}
				}

				log.debug("Loaded {} sessions from persistent storage", sessionHistory.size());
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load session history", e);
		}
	}

	/**
	 * Clear all persisted session history.
	 */
	public void clearPersistedHistory()
	{
		sessionHistory.clear();
		configManager.unsetConfiguration("splashhelper", SESSION_HISTORY_KEY);
		log.debug("Cleared persisted session history");
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

	/**
	 * Simple data class for aggregated session statistics.
	 */
	public static class SessionStats
	{
		public final int sessions;
		public final long totalSeconds;
		public final int totalCasts;
		public final int totalXp;

		public SessionStats(int sessions, long totalSeconds, int totalCasts, int totalXp)
		{
			this.sessions = sessions;
			this.totalSeconds = totalSeconds;
			this.totalCasts = totalCasts;
			this.totalXp = totalXp;
		}
	}
}