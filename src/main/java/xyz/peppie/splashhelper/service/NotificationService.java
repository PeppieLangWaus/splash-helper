package xyz.peppie.splashhelper.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import xyz.peppie.splashhelper.SplashHelperConfig;

import java.awt.Color;
import java.awt.TrayIcon;

/**
 * Service for handling all notification logic.
 */
@Slf4j
@Singleton
public class NotificationService
{
	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	@Inject
	private SplashHelperConfig config;

	private boolean notificationsMuted = false;
	private VisualNotificationCallback visualNotificationCallback;

	public interface VisualNotificationCallback
	{
		void triggerVisualNotification();
	}

	public void setVisualNotificationCallback(VisualNotificationCallback callback)
	{
		this.visualNotificationCallback = callback;
	}

	/**
	 * Send a timer notification.
	 */
	public void sendTimerNotification(String message)
	{
		if (!config.enableTimerNotification())
		{
			return;
		}
		if (notificationsMuted)
		{
			return;
		}
		sendNotificationInternal(message);
	}

	/**
	 * Send a boundary notification.
	 */
	public void sendBoundaryNotification(String message)
	{
		if (!config.enableBoundaryNotification())
		{
			return;
		}
		if (notificationsMuted)
		{
			return;
		}
		sendNotificationInternal(message);
	}

	/**
	 * Send an HP notification.
	 */
	public void sendHpNotification(String message)
	{
		if (!config.enableHpNotification())
		{
			return;
		}
		if (notificationsMuted)
		{
			return;
		}
		sendNotificationInternal(message);
	}

	/**
	 * Send a generic notification.
	 */
	public void sendNotification(String message)
	{
		sendNotificationInternal(message);
	}

	/**
	 * Mute all notifications.
	 */
	public void muteNotifications()
	{
		notificationsMuted = true;
	}

	/**
	 * Unmute all notifications.
	 */
	public void unmuteNotifications()
	{
		notificationsMuted = false;
	}

	/**
	 * Check if notifications are muted.
	 */
	public boolean areNotificationsMuted()
	{
		return notificationsMuted;
	}

	/**
	 * Internal method to send notification.
	 */
	private void sendNotificationInternal(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "Splash Helper", message, null);
		
		if (config.useVisualNotification())
		{
			// Trigger visual notification overlay in main plugin
			if (visualNotificationCallback != null)
			{
				visualNotificationCallback.triggerVisualNotification();
			}
			log.debug("Visual notification triggered: {}", message);
		}
		else
		{
			// Send system notification with auto-dismiss enabled
			Notification notification = new Notification(
				true,
				true,
				true,  // Enable auto-dismiss
				false,
				TrayIcon.MessageType.WARNING,
				RequestFocusType.OFF,
				NotificationSound.CUSTOM,
				null,
				client.getMusicVolume(),
				1,
				true,
				FlashNotification.DISABLED,
				Color.GREEN,
				false
			);
			notifier.notify(notification, message);
		}
	}
}
