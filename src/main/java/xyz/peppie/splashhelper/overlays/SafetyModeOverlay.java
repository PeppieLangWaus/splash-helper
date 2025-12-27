package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;
import xyz.peppie.splashhelper.SplashHelperPlugin;

/**
 * Overlay that displays safety mode status in the top-left corner.
 * Shows red when safety mode is disabled, green when enabled.
 */
public class SafetyModeOverlay extends Overlay
{
	private final SplashHelperPlugin plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	public SafetyModeOverlay(SplashHelperPlugin plugin)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.plugin = plugin;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();

		// More toned down colors
		Color backgroundColor = plugin.isSafetyModeEnabled() ? new Color(100, 150, 100) : new Color(150, 100, 100);
		Color textColor = new Color(220, 220, 220);

		String status = plugin.isSafetyModeEnabled() ? "Enabled" : "Disabled";

		panelComponent.getChildren().add(
			net.runelite.client.ui.overlay.components.LineComponent.builder()
				.left("Safety Mode: " + status)
				.leftColor(textColor)
				.build()
		);

		panelComponent.setBackgroundColor(backgroundColor);
		panelComponent.setPreferredSize(new Dimension(140, 0));

		return panelComponent.render(graphics);
	}
}
