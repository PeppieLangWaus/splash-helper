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
 * Fills the entire game canvas with a translucent color when triggered.
 */
public class VisualNotificationOverlay extends Overlay
{
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
		if (plugin.isShowVisualNotification())
		{
			Color originalColor = graphics.getColor();
			
			// Use configured color with some transparency
			Color notificationColor = config.visualNotificationColor();
			Color transparentColor = new Color(
				notificationColor.getRed(),
				notificationColor.getGreen(),
				notificationColor.getBlue(),
				100  // Alpha for transparency
			);
			
			graphics.setColor(transparentColor);
			graphics.fill(new Rectangle(client.getCanvas().getSize()));
			graphics.setColor(originalColor);
		}
		return null;
	}
}
