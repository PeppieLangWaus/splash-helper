package xyz.peppie.splashhelper.overlays;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.service.TileManager;

public class BoundaryTileOverlay extends Overlay
{
	private final Client client;
	private final SplashHelperConfig config;
	private final TileManager tileManager;

	@Inject
	private BoundaryTileOverlay(Client client, SplashHelperConfig config, TileManager tileManager)
	{
		this.client = client;
		this.config = config;
		this.tileManager = tileManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Stroke originalStroke = graphics.getStroke();

		// Render boundary tile
		if (tileManager.getBoundaryTile() != null)
		{
			renderTile(graphics, tileManager.getBoundaryTile(), config.boundaryTileColor(), originalStroke);
		}

		// Render Knight Tile 1
		if (tileManager.getKnightTile1() != null)
		{
			renderTile(graphics, tileManager.getKnightTile1(), config.knightTile1Color(), originalStroke);
		}

		// Render Knight Tile 2
		if (tileManager.getKnightTile2() != null)
		{
			renderTile(graphics, tileManager.getKnightTile2(), config.knightTile2Color(), originalStroke);
		}

		return null;
	}

	private void renderTile(Graphics2D graphics, WorldPoint worldPoint, java.awt.Color color, Stroke originalStroke)
	{
		LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), worldPoint);

		if (localPoint == null)
		{
			return;
		}

		// Get the tile polygon
		Polygon tilePoly = Perspective.getCanvasTilePoly(client, localPoint);

		if (tilePoly == null)
		{
			return;
		}

		// Fill with transparent dark gray
		graphics.setColor(new java.awt.Color(0, 0, 0, 100));
		graphics.fillPolygon(tilePoly);

		// Draw colored border
		graphics.setStroke(new BasicStroke(2));
		graphics.setColor(color);
		graphics.drawPolygon(tilePoly);
		graphics.setStroke(originalStroke);
	}
}
