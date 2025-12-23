package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import xyz.peppie.splashhelper.SplashHelperConfig;

/**
 * Overlay to warn players about grief-prone settings.
 * Shows a red warning in the top-left corner if Accept Aid is on or Private Chat is set to All.
 */
public class GriefPreventionOverlay extends Overlay
{
	private static final int ACCEPT_AID_VARP = 427;
	private static final int PRIVATE_CHAT_VARP = 287;
	
	// Private chat values: 0 = Off, 1 = Friends, 2 = On (All)
	private static final int PRIVATE_CHAT_ON = 2;
	
	private final Client client;
	private final SplashHelperConfig config;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	public GriefPreventionOverlay(Client client, SplashHelperConfig config)
	{
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.client = client;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// Only show if grief prevention is enabled in config
		if (!config.enableGriefPrevention())
		{
			return null;
		}

		boolean acceptAidOn = client.getVarpValue(ACCEPT_AID_VARP) == 1;
		int privateChatSetting = client.getVarpValue(PRIVATE_CHAT_VARP);
		// Private chat: 0 = Off, 1 = Friends, 2 = On (All)
		boolean privateChatAll = privateChatSetting == PRIVATE_CHAT_ON;

		// Only show overlay if either setting is problematic
		if (!acceptAidOn && !privateChatAll)
		{
			return null;
		}

		panelComponent.getChildren().clear();
		
		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Grief Prevention")
			.color(Color.RED)
			.build());

		// Warning messages
		if (acceptAidOn)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("⚠ Accept Aid: ON")
				.leftColor(Color.RED)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  Turn it OFF")
				.leftColor(Color.ORANGE)
				.build());
		}

		if (privateChatAll)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("⚠ Private Chat: All")
				.leftColor(Color.RED)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  Set to Friends/Off")
				.leftColor(Color.ORANGE)
				.build());
		}

		panelComponent.setBackgroundColor(new Color(70, 0, 0, 200));
		panelComponent.setPreferredSize(new Dimension(200, 0));

		return panelComponent.render(graphics);
	}
}
