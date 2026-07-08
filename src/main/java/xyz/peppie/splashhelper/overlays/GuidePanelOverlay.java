package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.guide.GuideConstants;
import xyz.peppie.splashhelper.guide.GuideEngine;
import xyz.peppie.splashhelper.guide.GuideStep;

/**
 * Text panel for the guide: the acknowledgement screen before start, then per-step progress,
 * the current instruction, and the armor / stuck warnings.
 */
public class GuidePanelOverlay extends OverlayPanel
{
	private final GuideEngine engine;
	private final SplashHelperConfig config;

	@Inject
	private GuidePanelOverlay(GuideEngine engine, SplashHelperConfig config)
	{
		this.engine = engine;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		switch (engine.getState())
		{
			case AWAITING_ACK:
				return renderAck(graphics);
			case RUNNING:
				return renderRunning(graphics);
			default:
				return null;
		}
	}

	private Dimension renderAck(Graphics2D graphics)
	{
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Sticky Knight Setup")
			.color(Color.CYAN)
			.build());

		addHeader("Main account:");
		for (String req : GuideConstants.MAIN_REQUIREMENTS)
		{
			addBullet(req);
		}
		addHeader("Alt account:");
		for (String req : GuideConstants.ALT_REQUIREMENTS)
		{
			addBullet(req);
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Press [" + config.guideNextHotkey().toString() + "] to acknowledge & begin")
			.leftColor(Color.YELLOW)
			.build());

		panelComponent.setPreferredSize(new Dimension(320, 0));
		return super.render(graphics);
	}

	private Dimension renderRunning(Graphics2D graphics)
	{
		GuideStep step = engine.currentStep();
		if (step == null)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Step " + step.label + " / " + engine.totalSteps())
			.color(Color.CYAN)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(step.title)
			.leftColor(Color.WHITE)
			.build());

		GuideStep.StepPhase phase = engine.currentPhase();
		if (phase != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(phase.instruction)
				.leftColor(Color.LIGHT_GRAY)
				.build());
		}

		if (engine.armorViolation())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Take OFF body/legs/boots!")
				.leftColor(Color.RED)
				.build());
		}

		if (engine.timeoutHintActive())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Stuck? Press [" + config.guideBackHotkey().toString() + "] to go back")
				.leftColor(Color.ORANGE)
				.build());
		}

		panelComponent.setPreferredSize(new Dimension(280, 0));
		return super.render(graphics);
	}

	private void addHeader(String text)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(text)
			.leftColor(Color.ORANGE)
			.build());
	}

	private void addBullet(String text)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left("- " + text)
			.leftColor(Color.LIGHT_GRAY)
			.build());
	}
}
