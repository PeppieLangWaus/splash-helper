package xyz.peppie.splashhelper.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.guide.GuideEngine;
import xyz.peppie.splashhelper.guide.GuideStep;
import xyz.peppie.splashhelper.guide.StepHighlight;

/**
 * Draws the in-world cues for the active guide phase: ground tiles (with optional text)
 * and an outline around the tracked knight.
 */
public class GuideSceneOverlay extends Overlay
{
	private static final Color TILE_COLOR = new Color(0, 200, 255, 255);
	private static final Color EMPHASIZE_COLOR = new Color(255, 230, 0, 255);
	private static final Color KNIGHT_COLOR = new Color(255, 90, 90, 255);
	private static final Color OTHER_PLAYER_COLOR = new Color(80, 220, 120, 255);
	private static final Color FILL = new Color(0, 0, 0, 90);

	private final Client client;
	private final GuideEngine engine;
	private final SplashHelperConfig config;

	@Inject
	private GuideSceneOverlay(Client client, GuideEngine engine, SplashHelperConfig config)
	{
		this.client = client;
		this.engine = engine;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		GuideStep.StepPhase phase = engine.currentPhase();
		if (phase == null)
		{
			return null;
		}

		Stroke original = graphics.getStroke();
		for (StepHighlight h : phase.highlights)
		{
			switch (h.type)
			{
				case TILE:
					renderTile(graphics, h, original);
					break;
				case KNIGHT:
					renderKnight(graphics);
					break;
				case OTHER_PLAYER:
					renderOtherPlayer(graphics, h);
					break;
				default:
					break; // widget-layer highlights handled by GuideWidgetOverlay
			}
		}
		graphics.setStroke(original);
		return null;
	}

	private void renderTile(Graphics2D graphics, StepHighlight h, Stroke original)
	{
		if (h.tile == null || client.getTopLevelWorldView() == null)
		{
			return;
		}
		LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), h.tile);
		if (lp == null)
		{
			return;
		}
		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return;
		}

		Color color = h.color != null ? h.color : (h.emphasize ? EMPHASIZE_COLOR : TILE_COLOR);
		graphics.setColor(FILL);
		graphics.fillPolygon(poly);
		graphics.setStroke(new BasicStroke(h.emphasize ? 3 : 2));
		graphics.setColor(color);
		graphics.drawPolygon(poly);

		if (h.label != null)
		{
			Point textLoc = Perspective.getCanvasTextLocation(client, graphics, lp, h.label, 0);
			if (textLoc != null)
			{
				OverlayUtil.renderTextLocation(graphics, textLoc, h.label, color);
			}
		}

		sceneArrow(graphics, poly.getBounds(), color);
	}

	private void renderKnight(Graphics2D graphics)
	{
		NPC knight = engine.getKnight();
		if (knight == null)
		{
			return;
		}
		// Outline the knight's model rather than the tile it stands on, so the cue tracks the NPC.
		Shape model = knight.getConvexHull();
		if (model == null)
		{
			return;
		}
		graphics.setColor(FILL);
		graphics.fill(model);
		graphics.setStroke(new BasicStroke(2));
		graphics.setColor(KNIGHT_COLOR);
		graphics.draw(model);

		sceneArrow(graphics, model.getBounds(), KNIGHT_COLOR);
	}

	private void renderOtherPlayer(Graphics2D graphics, StepHighlight h)
	{
		if (h.tile == null || client.getTopLevelWorldView() == null)
		{
			return;
		}
		Player local = client.getLocalPlayer();
		Player found = null;
		for (Player p : client.getTopLevelWorldView().players())
		{
			if (p != null && p != local && h.tile.equals(p.getWorldLocation()))
			{
				found = p;
				break;
			}
		}
		if (found == null)
		{
			return; // no one on the tile yet; the accompanying tile highlight still marks the spot
		}
		Shape model = found.getConvexHull();
		if (model == null)
		{
			return;
		}
		graphics.setColor(FILL);
		graphics.fill(model);
		graphics.setStroke(new BasicStroke(2));
		graphics.setColor(OTHER_PLAYER_COLOR);
		graphics.draw(model);

		sceneArrow(graphics, model.getBounds(), OTHER_PLAYER_COLOR);

		if (h.label != null)
		{
			Rectangle b = model.getBounds();
			Point textLoc = new Point(b.x + b.width / 2, b.y);
			OverlayUtil.renderTextLocation(graphics, textLoc, h.label, OTHER_PLAYER_COLOR);
		}
	}

	/** In-world arrow, sized from the configured base with a proportional gap above the target. */
	private void sceneArrow(Graphics2D graphics, Rectangle bounds, Color color)
	{
		int size = config.guideArrowSize();
		GuideOverlayUtil.arrowAbove(graphics, bounds, color, size, Math.max(5, size / 5));
	}
}
