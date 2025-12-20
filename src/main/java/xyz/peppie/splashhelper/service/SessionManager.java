package xyz.peppie.splashhelper.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import xyz.peppie.splashhelper.SplashSession;
import xyz.peppie.splashhelper.SplashSpell;
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

	@Getter
	private SplashSession currentSession = null;

	@Getter
	private SplashSession lastFinalizedSession = null;

	@Getter
	private final List<SplashSession> sessionHistory = new ArrayList<>();

	private Instant lastCastTime = null;
	private int lastMagicXp = -1;

	@Inject
	public SessionManager(Client client, RuneCalculator runeCalculator)
	{
		this.client = client;
		this.runeCalculator = runeCalculator;
	}

	/**
	 * Start a new splash session.
	 */
	public void startSession(String playerName, SplashSpell spell, Instant logoutTime,
							 int world, boolean stickyKnight)
	{
		int startXp = client.getSkillExperience(Skill.MAGIC);
		lastMagicXp = startXp;
		lastCastTime = Instant.now();

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

		log.info("Started splash session for {} with spell {}", playerName,
			spell != null ? spell.getName() : "unknown");
	}

	/**
	 * Finalize the current session and add it to history.
	 */
	public void finalizeSession()
	{
		if (currentSession != null && currentSession.isActive())
		{
			currentSession.setEndTime(Instant.now());
			sessionHistory.add(currentSession);
			lastFinalizedSession = currentSession;
			log.info("Finalized session: {} casts, {} XP gained",
				currentSession.getSpellsCast(), currentSession.getMagicXpGained());
		}
		currentSession = null;
		lastMagicXp = -1;
		lastCastTime = null;
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

		if (lastCastTime == null)
		{
			return false;
		}

		Instant now = Instant.now();
		if (Duration.between(lastCastTime, now).getSeconds() >= Constants.SESSION_TIMEOUT_SECONDS)
		{
			log.info("Session timeout - no cast in {} seconds", Constants.SESSION_TIMEOUT_SECONDS);
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
			}

			lastCastTime = Instant.now();
			return xpGained;
		}

		return 0;
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
			currentSession.addPlayerCountSample(count);
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
		lastCastTime = null;
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
