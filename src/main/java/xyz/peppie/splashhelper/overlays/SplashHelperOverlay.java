package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.SplashHelperPlugin;

public class SplashHelperOverlay extends Overlay
{
	private SplashHelperPlugin plugin;
	private final SplashHelperConfig config;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	private SplashHelperOverlay(Client client, SplashHelperConfig config)
	{
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		this.config = config;
	}

	public void setPlugin(SplashHelperPlugin plugin)
	{
		this.plugin = plugin;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		String overlayTitle = "Splash Timer";

		// Add title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(overlayTitle)
			.color(Color.CYAN)
			.build());

		panelComponent.setPreferredSize(new Dimension(
            graphics.getFontMetrics().stringWidth(overlayTitle) + 90,
            0));

		// Check if plugin is initialized
		if (plugin == null)
		{
			return null;
		}
		
		// Show boundary tile status
		if (plugin.getBoundaryTile() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Boundary:")
				.right("SET")
				.rightColor(Color.GREEN)
				.build());
		}
		
		// Show movement tracking stats
		if (plugin.getKnightTile1() != null && plugin.getKnightTile2() != null)
		{
			double movementsPerMin = plugin.getMovementsPerMinute();
			String movementText = String.format("%.1f/min", movementsPerMin);
			
			Color movementColor = Color.CYAN;
			if (movementsPerMin > 0)
			{
				movementColor = Color.GREEN;
			}
			
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Movements:")
				.right(movementText)
				.rightColor(movementColor)
				.build());
		}
		
		// Check if timer is initialized
		if (plugin.getTimerEnd() == null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Timer:")
				.right("Not started")
				.rightColor(Color.GRAY)
				.build());
			return panelComponent.render(graphics);
		}

		// Calculate remaining time
		Duration remaining = Duration.between(Instant.now(), plugin.getTimerEnd());
		
		if (remaining.isNegative() || remaining.isZero())
		{
			// Timer expired
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Time:")
				.right("EXPIRED")
				.rightColor(Color.RED)
				.build());
		}
		else
		{
			// Format time as MM:SS
			long totalSeconds = remaining.getSeconds();
			long minutes = totalSeconds / 60;
			long seconds = totalSeconds % 60;
			String timeString = String.format("%02d:%02d", minutes, seconds);

			Color timeColor = Color.GREEN;
			if (minutes < 1)
			{
				timeColor = Color.YELLOW;
			}
			if (seconds < 30 && minutes == 0)
			{
				timeColor = Color.RED;
			}

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Time Remaining:")
				.right(timeString)
				.rightColor(timeColor)
				.build());
		}

		return panelComponent.render(graphics);
	}
}
