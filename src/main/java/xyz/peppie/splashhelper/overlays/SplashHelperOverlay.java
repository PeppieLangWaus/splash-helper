package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.SplashHelperPlugin;
import xyz.peppie.splashhelper.model.TimerAlertLevel;
import xyz.peppie.splashhelper.service.StackStatsEngine;
import xyz.peppie.splashhelper.service.StackStatsTracker;
import xyz.peppie.splashhelper.service.TileManager;

public class SplashHelperOverlay extends Overlay
{
	/** Shared with VisualNotificationOverlay so text and tint change together. */
	public static final Color WARNING_COLOR = new Color(255, 140, 0);
	public static final Color CRITICAL_COLOR = Color.RED;

	private final SplashHelperConfig config;
	private final StackStatsTracker stackStatsTracker;
	private SplashHelperPlugin plugin;
	private TileManager tileManager;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	private SplashHelperOverlay(SplashHelperConfig config, StackStatsTracker stackStatsTracker)
	{
		this.config = config;
		this.stackStatsTracker = stackStatsTracker;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	public void setPlugin(SplashHelperPlugin plugin, TileManager tileManager)
	{
		this.plugin = plugin;
		this.tileManager = tileManager;
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
		if (tileManager.getBoundaryTile() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Boundary:")
				.right("SET")
				.rightColor(Color.GREEN)
				.build());
		}
		
		// Show movement tracking stats
		if (tileManager.getKnightTile() != null && tileManager.getStackTile() != null)
		{
			double movementsPerHour = tileManager.getMovementsPerHour();
			String movementText = String.format("%.1f/hr", movementsPerHour);

			Color movementColor = Color.CYAN;
			if (movementsPerHour > 0)
			{
				movementColor = Color.GREEN;
			}

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Movements:")
				.right(movementText)
				.rightColor(movementColor)
				.build());
		}

		if (config.enableStackTracking() && config.showStackTrackingOverlay())
		{
			addStackTrackingLines();
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

			// Same thresholds as the visual notification tint
			TimerAlertLevel level = plugin.getTimerAlertLevel();
			Color timeColor = Color.GREEN;
			if (level == TimerAlertLevel.WARNING)
			{
				timeColor = WARNING_COLOR;
			}
			else if (level == TimerAlertLevel.CRITICAL || level == TimerAlertLevel.EXPIRED)
			{
				timeColor = CRITICAL_COLOR;
			}

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Time Remaining:")
				.right(timeString)
				.rightColor(timeColor)
				.build());
		}

		return panelComponent.render(graphics);
	}

	private void addStackTrackingLines()
	{
		StackStatsEngine.Summary summary = stackStatsTracker.getSummary();
		double hours = summary.getTicksObserved() * 0.6 / 3600.0;

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Clear/hr:")
			.right(String.format("%.1f", StackStatsTracker.perHour(summary.getFlagClearTicks(), hours)))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Knight Moves/hr:")
			.right(String.format("%.2f", StackStatsTracker.perHour(summary.getExcursionCount(), hours)))
			.build());
	}
}
