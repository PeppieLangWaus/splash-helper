package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import xyz.peppie.splashhelper.SplashHelperPlugin;

public class MagicBonusWarningOverlay extends Overlay
{
	private final SplashHelperPlugin plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	public MagicBonusWarningOverlay(SplashHelperPlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isHasBadMagicBonus())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("MAGIC BONUS WARNING")
			.color(Color.RED)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Your magic attack bonus is too high!")
			.leftColor(Color.ORANGE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("You will NOT splash reliably")
			.leftColor(Color.ORANGE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Required: -64 or lower")
			.leftColor(Color.YELLOW)
			.build());

		panelComponent.setPreferredSize(new Dimension(250, 0));

		return panelComponent.render(graphics);
	}
}
