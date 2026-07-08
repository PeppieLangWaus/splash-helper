package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import xyz.peppie.splashhelper.guide.GuideConstants;
import xyz.peppie.splashhelper.guide.GuideEngine;
import xyz.peppie.splashhelper.guide.GuideStep;
import xyz.peppie.splashhelper.guide.StepHighlight;

/**
 * Draws boxes around interface elements for the active guide phase: inventory/spell/equipment
 * tabs, the special-attack orb, the Entangle spell, a specific inventory item, an equipment
 * slot, or every saved-gear item currently sitting in the inventory.
 */
public class GuideWidgetOverlay extends Overlay
{
	private static final Color BOX = new Color(0, 200, 255, 255);
	private static final int PAD = 2;

	private final Client client;
	private final GuideEngine engine;

	@Inject
	private GuideWidgetOverlay(Client client, GuideEngine engine)
	{
		this.client = client;
		this.engine = engine;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	@SuppressWarnings("deprecation")
	public Dimension render(Graphics2D graphics)
	{
		GuideStep.StepPhase phase = engine.currentPhase();
		if (phase == null)
		{
			return null;
		}

		for (StepHighlight h : phase.highlights)
		{
			switch (h.type)
			{
				case WIDGET:
				case EQUIPMENT_SLOT:
					box(graphics, widgetBounds(client.getWidget(h.id)));
					break;
				case INVENTORY_ITEM:
					box(graphics, inventoryItemBounds(h.id));
					break;
				case SAVED_GEAR:
					for (int itemId : engine.savedGearItemIds())
					{
						box(graphics, inventoryItemBounds(itemId));
					}
					break;
				default:
					break; // scene-layer highlights handled by GuideSceneOverlay
			}
		}
		return null;
	}

	private Rectangle widgetBounds(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		return widget.getBounds();
	}

	@SuppressWarnings("deprecation")
	private Rectangle inventoryItemBounds(int itemId)
	{
		Widget inv = client.getWidget(GuideConstants.WIDGET_INVENTORY);
		if (inv == null || inv.isHidden())
		{
			return null;
		}
		Widget[] children = inv.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			if (child != null && child.getItemId() == itemId)
			{
				return child.getBounds();
			}
		}
		return null;
	}

	private void box(Graphics2D graphics, Rectangle bounds)
	{
		if (bounds == null)
		{
			return;
		}
		graphics.setColor(BOX);
		graphics.drawRect(bounds.x - PAD, bounds.y - PAD, bounds.width + PAD * 2, bounds.height + PAD * 2);
	}
}
