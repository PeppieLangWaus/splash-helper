package xyz.peppie.splashhelper.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-ready session data structure for persistence.
 * Includes UUID and timestamps for server synchronization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistedSession
{
	/**
	 * Unique identifier for this session (for server sync)
	 */
	private String sessionId;

	/**
	 * When this session was created (for server ordering)
	 */
	private long createdTimestamp;

	/**
	 * When this session was finalized
	 */
	private long finalizedTimestamp;

	/**
	 * Whether this session has been synced to server
	 */
	private boolean syncedToServer;

	/**
	 * The actual session data
	 */
	private SplashSession session;

	/**
	 * Create a new persisted session from a SplashSession
	 */
	public static PersistedSession fromSession(SplashSession session)
	{
		return PersistedSession.builder()
			.sessionId(UUID.randomUUID().toString())
			.createdTimestamp(session.getStartTime().toEpochMilli())
			.finalizedTimestamp(session.getEndTime() != null ? session.getEndTime().toEpochMilli() : Instant.now().toEpochMilli())
			.syncedToServer(session.isSynced())
			.session(session)
			.build();
	}
}
