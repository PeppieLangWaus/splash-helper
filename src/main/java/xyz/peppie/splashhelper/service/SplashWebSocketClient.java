package xyz.peppie.splashhelper.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.model.SplashSession;

/**
 * Manages a persistent WebSocket connection to the Splash Helper server.
 * Handles authentication, session lifecycle messaging, and automatic reconnection.
 */
@Slf4j
@Singleton
public class SplashWebSocketClient
{
	private final SplashHelperConfig config;
	private final ConfigManager configManager;
	private final Gson gson;
	private final Client client;
	private final ClientThread clientThread;
	private final HttpClient httpClient;
	private volatile ScheduledExecutorService executor;

	private volatile WebSocket webSocket;
	private final AtomicBoolean connected = new AtomicBoolean(false);
	private final AtomicBoolean authenticated = new AtomicBoolean(false);
	private final AtomicBoolean connecting = new AtomicBoolean(false);
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
	private volatile boolean intentionalDisconnect = false;
	private volatile String activeUsername;
	private int reconnectAttempts = 0;

	/** Minimum milliseconds between SESSION_UPDATE messages sent to the server. */
	private static final long UPDATE_THROTTLE_MS = 10_000L;
	private volatile long lastUpdateSentMs = 0;

	/** The most recently received setup link URL, or null if none. */
	@Getter
	private volatile String setupLink;

	/** Epoch-second expiry of the current setup link JWT, or 0 if unknown. */
	private volatile long setupLinkExpiry;

	/** Last setup link that was logged, to avoid repeating the message on reconnects. */
	private volatile String lastLoggedSetupLink = null;

	/** True once we have logged the setup-required message for the current connection. Cleared on each new open. */
	private volatile boolean setupLinkLoggedThisConnection = false;

	/** Called once whenever AUTH_SUCCESS is received (initial auth or re-auth). */
	private volatile Runnable onAuthSuccessCallback;

	public void setOnAuthSuccessCallback(Runnable callback)
	{
		this.onAuthSuccessCallback = callback;
	}

	/** A SESSION_START that arrived before authentication completed; sent once auth succeeds. */
	private volatile SplashSession pendingSessionStart = null;

	/** Returns true if the WebSocket is currently connected. */
	public boolean isConnected()
	{
		return connected.get();
	}

	/** Returns true if the WebSocket is connected and authenticated. */
	public boolean isAuthenticated()
	{
		return authenticated.get();
	}

	/**
	 * Tracks the result of the most recent AUTH_SUCCESS:
	 * null = no auth response yet, true = account needs setup, false = already set up.
	 */
	@Getter
	private volatile Boolean lastAuthSetupRequired = null;

	private static final String TOKEN_CONFIG_KEY = "playerToken";
	private static final String CONFIG_GROUP = "splashhelper";
	private static final int[] RECONNECT_DELAYS_SECONDS = {2, 4, 8, 16, 30};

	@Inject
	public SplashWebSocketClient(SplashHelperConfig config, ConfigManager configManager,
								 Gson gson, Client client, ClientThread clientThread)
	{
		this.config = config;
		this.configManager = configManager;
		this.gson = gson;
		this.client = client;
		this.clientThread = clientThread;
		this.httpClient = HttpClient.newHttpClient();
	}

	/**
	 * Returns the executor, creating a new one if necessary (e.g. after a previous shutdown).
	 * The singleton survives plugin disable/enable cycles so the executor must be recreatable.
	 */
	private synchronized ScheduledExecutorService executor()
	{
		if (executor == null || executor.isShutdown())
		{
			executor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "splash-helper-ws");
				t.setDaemon(true);
				return t;
			});
		}
		return executor;
	}

	/**
	 * Connect to the configured WebSocket server as the given username.
	 * No-op if server sync is disabled in config.
	 */
	public void connect(String username)
	{
		if (!config.enableServerSync())
		{
			return;
		}
		if (username == null || username.trim().isEmpty())
		{
			return;
		}

		String trimmedUsername = username.trim();
		if (trimmedUsername.equals(activeUsername) && (connected.get() || connecting.get()))
		{
			return;
		}

		authenticated.set(false);
		this.activeUsername = trimmedUsername;
		this.intentionalDisconnect = false;
		reconnectScheduled.set(false);
		// Set connecting BEFORE submitting to the executor so that a second connect()
		// call arriving before doConnect() runs is blocked by the guard above.
		connecting.set(true);
		executor().execute(this::doConnect);
	}

	/**
	 * Disconnect from the server and cancel any pending reconnects.
	 * The actual WS close is submitted to the executor so any already-queued
	 * messages (e.g. SESSION_END) are sent before the connection is torn down.
	 */
	public void disconnect()
	{
		intentionalDisconnect = true;
		authenticated.set(false);
		connecting.set(false);
		reconnectScheduled.set(false);
		// Submit the real close to the executor so it runs *after* any pending
		// SESSION_END (or other) messages that were already queued before this call.
		ScheduledExecutorService ex = this.executor;
		if (ex != null && !ex.isShutdown())
		{
			ex.execute(this::doDisconnect);
		}
		else
		{
			doDisconnect();
		}
	}

	private void doDisconnect()
	{
		WebSocket ws = this.webSocket;
		connecting.set(false);
		pendingSessionStart = null;
		if (ws != null && connected.getAndSet(false))
		{
			try
			{
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "plugin shutdown");
			}
			catch (Exception e)
			{
				log.debug("WS close error: {}", e.getMessage());
			}
		}
		webSocket = null;
	}

	/**
	 * Disconnect and shut down the background executor.
	 * The executor will be lazily recreated if connect() is called again.
	 */
	public void shutdown()
	{
		// Flush any pending messages (SESSION_END etc.) before shutting down.
		intentionalDisconnect = true;
		authenticated.set(false);
		ScheduledExecutorService ex = this.executor;
		if (ex != null && !ex.isShutdown())
		{
			ex.execute(this::doDisconnect);
			ex.shutdown(); // graceful — waits for queued tasks to finish
		}
		else
		{
			doDisconnect();
		}
	}

	/**
	 * Returns true if a setup link has been received and its JWT has not expired.
	 */
	public boolean isSetupLinkValid()
	{
		if (setupLink == null)
		{
			return false;
		}
		if (setupLinkExpiry <= 0)
		{
			return true; // could not parse expiry — assume valid
		}
		return Instant.now().getEpochSecond() < setupLinkExpiry;
	}

	/**
	 * Re-authenticates with the server to request a fresh setup link.
	 * If not connected, attempts to connect first (auth happens on connect).
	 */
	public void requestSetupLink()
	{
		lastAuthSetupRequired = null; // reset so caller can detect a fresh response
		if (connected.get())
		{
			executor().execute(this::sendAuth);
		}
		else if (activeUsername != null)
		{
			intentionalDisconnect = false;
			executor().execute(this::doConnect);
		}
	}

	/** Send a SESSION_START message for the given session. */
	public void sendSessionStart(SplashSession session)
	{
		if (session == null)
		{
			return;
		}
		if (!authenticated.get())
		{
			// Not authenticated yet — queue so it is sent as soon as AUTH_SUCCESS arrives
			if (!intentionalDisconnect)
			{
				pendingSessionStart = session;
			}
			return;
		}
		pendingSessionStart = null;
		executor().execute(() -> {
			JsonObject msg = new JsonObject();
			msg.addProperty("type", "SESSION_START");
			msg.add("sessionData", buildSessionData(session));
			sendJson(msg);
		});
	}

	/**
	 * Send a SESSION_UPDATE message, throttled to at most once every
	 * {@link #UPDATE_THROTTLE_MS} milliseconds.
	 */
	public void sendSessionUpdate(SplashSession session)
	{
		if (!authenticated.get() || session == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastUpdateSentMs < UPDATE_THROTTLE_MS)
		{
			return;
		}
		lastUpdateSentMs = now;
		executor().execute(() -> {
			JsonObject msg = new JsonObject();
			msg.addProperty("type", "SESSION_UPDATE");
			msg.add("sessionData", buildSessionData(session));
			sendJson(msg);
		});
	}

	/** Send a SESSION_END message for the given finalized session. */
	public void sendSessionEnd(SplashSession session)
	{
		// Check connected (not authenticated) — disconnect() clears authenticated before
		// the executor runs, so SESSION_END would be silently dropped otherwise.
		if (!connected.get() || session == null)
		{
			return;
		}
		executor().execute(() -> {
			JsonObject msg = new JsonObject();
			msg.addProperty("type", "SESSION_END");
			msg.add("sessionData", buildSessionData(session));
			sendJson(msg);
		});
	}

	// ==================== Internal helpers ====================

	private void doConnect()
	{
		if (intentionalDisconnect)
		{
			connecting.set(false);
			return;
		}

		// connecting is already set to true by connect() or scheduleReconnect().
		// Guard against any path that skipped that (shouldn't happen, but be safe).
		connecting.set(true);

		// If an old WebSocket is still open (e.g. world hop), close it cleanly first
		if (connected.get() && webSocket != null)
		{
			try
			{
				webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnecting");
			}
			catch (Exception e)
			{
				log.debug("WS close before reconnect: {}", e.getMessage());
			}
			connected.set(false);
			authenticated.set(false);
			webSocket = null;
		}
		String rawUrl = config.serverUrl();
		String url = (rawUrl == null || rawUrl.trim().isEmpty()) ? "ws://localhost:3000" : rawUrl.trim();

		try
		{
			httpClient.newWebSocketBuilder()
				.buildAsync(URI.create(url), new WsListener())
				.whenComplete((ws, ex) -> {
					connecting.set(false);
					if (ex != null)
					{
						log.debug("WS connect failed to {}: {}", url, ex.getMessage());
						if (!intentionalDisconnect)
						{
							scheduleReconnect();
						}
					}
					else
					{
						webSocket = ws;
					}
				});
		}
		catch (Exception e)
		{
			connecting.set(false);
			log.warn("WS connect error: {}", e.getMessage());
			if (!intentionalDisconnect)
			{
				scheduleReconnect();
			}
		}
	}

	private void scheduleReconnect()
	{
		if (intentionalDisconnect || connected.get() || connecting.get())
		{
			return;
		}
		if (!reconnectScheduled.compareAndSet(false, true))
		{
			return;
		}

		// Mirror connect(): set the flag before the task runs to block concurrent races.
		connecting.set(true);
		int delayIdx = Math.min(reconnectAttempts, RECONNECT_DELAYS_SECONDS.length - 1);
		int delaySeconds = RECONNECT_DELAYS_SECONDS[delayIdx];
		reconnectAttempts++;
		log.debug("WS reconnect in {}s (attempt {})", delaySeconds, reconnectAttempts);
		executor().schedule(() -> {
			reconnectScheduled.set(false);
			doConnect();
		}, delaySeconds, TimeUnit.SECONDS);
	}

	private void sendAuth()
	{
		String token = getOrCreateToken();
		JsonObject msg = new JsonObject();
		msg.addProperty("type", "AUTH");
		msg.addProperty("username", activeUsername != null ? activeUsername : "");
		msg.addProperty("token", token);
		sendJson(msg);
	}

	private void sendJson(JsonObject msg)
	{
		WebSocket ws = this.webSocket;
		if (ws == null || !connected.get())
		{
			return;
		}
		String json = gson.toJson(msg);
		ws.sendText(json, true).exceptionally(ex -> {
			log.warn("WS send failed: {}", ex.getMessage());
			return null;
		});
	}

	private void handleMessage(String text)
	{
		try
		{
			JsonObject msg = gson.fromJson(text, JsonObject.class);
			if (msg == null || !msg.has("type"))
			{
				return;
			}
			String type = msg.get("type").getAsString();
			switch (type)
			{
				case "AUTH_SUCCESS":
					authenticated.set(true);
					reconnectScheduled.set(false);
					reconnectAttempts = 0;
					boolean setupRequired = msg.has("setupRequired") && msg.get("setupRequired").getAsBoolean();
					lastAuthSetupRequired = setupRequired;
					if (setupRequired && msg.has("setupLink"))
					{
						String link = msg.get("setupLink").getAsString();
						setupLink = link;
						setupLinkExpiry = parseJwtExpiry(link);
						// Only log once per connection to avoid spamming the console on reconnects
						// (each reconnect generates a new JWT so link-equality checks don't help).
						if (!setupLinkLoggedThisConnection)
						{
							log.info("Account setup required. Link: {}", link);
							setupLinkLoggedThisConnection = true;
							lastLoggedSetupLink = link;
						}
						else
						{
							log.debug("Account setup required (reconnect). Link: {}", link);
							lastLoggedSetupLink = link;
						}
					}
					else
					{
						// Account already set up — clear any stale link
						setupLink = null;
						setupLinkExpiry = 0;
						lastLoggedSetupLink = null;
					}
					log.debug("WS authenticated for {}", activeUsername);
					// Flush any SESSION_START that arrived before authentication completed
					SplashSession pending = pendingSessionStart;
					if (pending != null)
					{
						pendingSessionStart = null;
						sendSessionStart(pending);
					}
					// Notify the plugin so it can re-send SESSION_START for any ongoing session
					Runnable cb = onAuthSuccessCallback;
					if (cb != null)
					{
						cb.run();
					}
					break;

				case "AUTH_FAILURE":
					String reason = msg.has("reason") ? msg.get("reason").getAsString() : "unknown";
					if ("Not authenticated".equalsIgnoreCase(reason))
					{
						log.debug("WS received pre-auth rejection, retrying AUTH for {}", activeUsername);
						if (connected.get())
						{
							executor().execute(this::sendAuth);
						}
						break;
					}

					log.warn("WS auth failed for {}: {}", activeUsername, reason);
					authenticated.set(false);
					if ("Invalid token".equalsIgnoreCase(reason)
						|| "username and token are required".equalsIgnoreCase(reason)
						|| "Missing message type".equalsIgnoreCase(reason)
						|| "Invalid JSON".equalsIgnoreCase(reason))
					{
						// Fatal auth failures should halt reconnect loops until user action.
						intentionalDisconnect = true;
					}
					break;

				case "ACK":
					log.debug("WS ACK received");
					break;

				default:
					log.debug("Unknown WS message type: {}", type);
					break;
			}
		}
		catch (Exception e)
		{
			log.error("Error handling WS message", e);
		}
	}

	private JsonObject buildSessionData(SplashSession session)
	{
		JsonObject obj = new JsonObject();
		obj.addProperty("playerName", session.getPlayerName());
		obj.addProperty("spell", session.getSpell() != null ? session.getSpell().getName() : "Unknown");
		obj.addProperty("runeCostPerCast", session.getRuneCostPerCast());
		obj.addProperty("startTime", session.getStartTime().toString());
		Instant logoutTime = session.getLogoutTime();
		obj.addProperty("logoutTime", logoutTime != null ? logoutTime.toString() : session.getStartTime().toString());
		obj.addProperty("world", session.getWorld());
		obj.addProperty("stickyKnight", session.isStickyKnight());
		obj.addProperty("spellsCast", session.getSpellsCast());
		obj.addProperty("startMagicXp", session.getStartMagicXp());
		obj.addProperty("currentMagicXp", session.getCurrentMagicXp());
		obj.addProperty("knightMovements", session.getKnightMovements());
		obj.addProperty("highestPlayerCount", session.getHighestPlayerCount());
		obj.addProperty("averagePlayerCount", session.getAveragePlayerCount());
		obj.addProperty("pickpocketerCount", session.getPickpocketerCount());
		obj.addProperty("startingRuneCount", session.getStartingRuneCount());
		obj.addProperty("currentRuneCount", session.getCurrentRuneCount());
		JsonObject runeMap = new JsonObject();
		for (Map.Entry<Integer, Integer> entry : session.getRuneUsageMap().entrySet())
		{
			runeMap.addProperty(entry.getKey().toString(), entry.getValue());
		}
		obj.add("runeUsageMap", runeMap);
		obj.addProperty("runeCostGp", session.getRuneCostGp());
		return obj;
	}

	/**
	 * Extract the 'exp' claim from a setup link URL containing a JWT token query parameter.
	 * Returns epoch seconds, or 0 if unparseable.
	 */
	private long parseJwtExpiry(String link)
	{
		try
		{
			int idx = link.indexOf("token=");
			if (idx < 0) return 0;
			String jwt = link.substring(idx + 6);
			int ampIdx = jwt.indexOf('&');
			if (ampIdx > 0) jwt = jwt.substring(0, ampIdx);

			String[] parts = jwt.split("\\.");
			if (parts.length < 2) return 0;

			String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
			JsonObject obj = gson.fromJson(payload, JsonObject.class);
			if (obj != null && obj.has("exp"))
			{
				return obj.get("exp").getAsLong();
			}
		}
		catch (Exception e)
		{
			log.debug("Could not parse JWT expiry from setup link", e);
		}
		return 0;
	}

	private String getOrCreateToken()
	{
		String token = configManager.getConfiguration(CONFIG_GROUP, TOKEN_CONFIG_KEY);
		if (token == null || token.trim().isEmpty())
		{
			token = UUID.randomUUID().toString();
			configManager.setConfiguration(CONFIG_GROUP, TOKEN_CONFIG_KEY, token);
			log.info("Generated new player token for server sync");
		}
		return token;
	}

	// ==================== WebSocket listener ====================

	private class WsListener implements WebSocket.Listener
	{
		private final StringBuilder buffer = new StringBuilder();

		@Override
		public void onOpen(WebSocket ws)
		{
			// Set webSocket BEFORE sendAuth — onOpen fires before whenComplete,
			// so this.webSocket would be null if we only set it in whenComplete.
			webSocket = ws;
			connected.set(true);
			authenticated.set(false);
			reconnectScheduled.set(false);
			// Reset the per-connection setup-link log flag so the first AUTH_SUCCESS
			// on this new connection logs at info level.
			setupLinkLoggedThisConnection = false;
			log.debug("WS connected to server, authenticating...");
			ws.request(1);
			sendAuth();
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last)
		{
			buffer.append(data);
			if (last)
			{
				String text = buffer.toString();
				buffer.setLength(0);
				handleMessage(text);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason)
		{
			log.debug("WS closed ({}): {}", statusCode, reason);
			connecting.set(false);
			connected.set(false);
			authenticated.set(false);
			if (!intentionalDisconnect)
			{
				scheduleReconnect();
			}
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error)
		{
			log.debug("WS error: {}", error.getMessage());
			connecting.set(false);
			connected.set(false);
			authenticated.set(false);
			if (!intentionalDisconnect)
			{
				scheduleReconnect();
			}
		}
	}
}
