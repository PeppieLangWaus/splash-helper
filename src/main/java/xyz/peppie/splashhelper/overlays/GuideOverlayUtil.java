package xyz.peppie.splashhelper.overlays;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;

/**
 * Shared drawing helpers for the guide overlays.
 */
final class GuideOverlayUtil
{
	private GuideOverlayUtil() {}

	/**
	 * Draws a downward-pointing arrow hovering {@code gap}px above the top-centre of {@code bounds},
	 * its tip aimed at the highlight. {@code size} is the arrow's total height in pixels; the width
	 * scales from it — a narrow shaft on top widening into the head — and the border is a thin 1px
	 * outline. Colour matches the highlight it annotates.
	 */
	static void arrowAbove(Graphics2D graphics, Rectangle bounds, Color color, int size, int gap)
	{
		if (bounds == null || size <= 0)
		{
			return;
		}

		int cx = bounds.x + bounds.width / 2;
		int tipY = bounds.y - gap;
		int topY = tipY - size;
		int shoulderY = topY + Math.round(size * 0.45f); // where the shaft meets the head wings

		int headHalf = Math.max(2, Math.round(size * 0.40f));
		int shaftHalf = Math.max(1, Math.round(size * 0.13f)); // narrow at the top

		Polygon arrow = new Polygon();
		arrow.addPoint(cx - shaftHalf, topY);        // shaft top-left
		arrow.addPoint(cx - shaftHalf, shoulderY);   // shaft down to the head, left
		arrow.addPoint(cx - headHalf, shoulderY);    // head wing left
		arrow.addPoint(cx, tipY);                    // tip (points at the highlight)
		arrow.addPoint(cx + headHalf, shoulderY);    // head wing right
		arrow.addPoint(cx + shaftHalf, shoulderY);   // shaft down to the head, right
		arrow.addPoint(cx + shaftHalf, topY);        // shaft top-right

		Stroke prev = graphics.getStroke();
		graphics.setColor(color);
		graphics.fillPolygon(arrow);
		graphics.setStroke(new BasicStroke(1f));
		graphics.setColor(Color.BLACK);
		graphics.drawPolygon(arrow);
		graphics.setStroke(prev);
	}
}
