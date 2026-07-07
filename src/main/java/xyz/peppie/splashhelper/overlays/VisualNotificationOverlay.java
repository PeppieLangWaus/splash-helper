package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.SplashHelperPlugin;

/**
 * Full-screen visual notification overlay.
 * Fills the game canvas with a translucent tint: orange once the timer passes
 * the warning threshold, red at the critical threshold, and red when a
 * notification fires (timer expiry) — the same trigger times as the timer
 * overlay's text colors.
 */
public class VisualNotificationOverlay extends Overlay
{
	private static final int TINT_ALPHA = 80;
	private static final Color WARNING_TINT = withAlpha(SplashHelperOverlay.WARNING_COLOR);
	private static final Color CRITICAL_TINT = withAlpha(SplashHelperOverlay.CRITICAL_COLOR);

	private final Client client;
	private final SplashHelperPlugin plugin;
	private final SplashHelperConfig config;

	@Inject
	public VisualNotificationOverlay(Client client, SplashHelperPlugin plugin, SplashHelperConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableVisualNotification())
		{
			return null;
		}

		Color tint = null;
		if (plugin.isShowVisualNotification())
		{
			// Notification fired (e.g. timer expired) — strongest alert
			tint = CRITICAL_TINT;
		}
		else
		{
			switch (plugin.getTimerAlertLevel())
			{
				case WARNING:
					tint = WARNING_TINT;
					break;
				case CRITICAL:
				case EXPIRED:
					tint = CRITICAL_TINT;
					break;
				default:
					break;
			}
		}

		if (tint != null)
		{
			Color originalColor = graphics.getColor();
			graphics.setColor(tint);
			graphics.fill(new Rectangle(client.getCanvas().getSize()));
			graphics.setColor(originalColor);
		}
		return null;
	}

	private static Color withAlpha(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), TINT_ALPHA);
	}
}
