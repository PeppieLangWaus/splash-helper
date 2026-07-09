package xyz.peppie.splashhelper.overlays;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.guide.GuideConstants;
import xyz.peppie.splashhelper.guide.GuideEngine;
import xyz.peppie.splashhelper.guide.GuideStep;
import xyz.peppie.splashhelper.guide.StepHighlight;

/**
 * Draws the interface cues for the active guide phase. Tabs, the special-attack orb and the
 * Entangle spell get a simple box; actual items — inventory items, equipment-slot items and the
 * saved splasher gear — are traced with a tinted, semi-transparent silhouette of the item itself.
 */
public class GuideWidgetOverlay extends Overlay
{
	private static final Color BOX = new Color(0, 200, 255, 255);
	private static final Color ITEM_HIGHLIGHT = new Color(0, 200, 255, 255);
	private static final int ITEM_FILL_ALPHA = 80; // semi-transparent fill over the item shape
	private static final int PAD = 2;

	private final Client client;
	private final GuideEngine engine;
	private final SplashHelperConfig config;
	private final ItemManager itemManager;

	// Cache the tinted fill/outline silhouettes per (itemId, colour) so we scan pixels only once.
	private final Map<Long, ItemGlyph> glyphCache = new HashMap<>();

	@Inject
	private GuideWidgetOverlay(Client client, GuideEngine engine, SplashHelperConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.engine = engine;
		this.config = config;
		this.itemManager = itemManager;
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
					box(graphics, widgetBounds(client.getWidget(h.id)));
					break;
				case EQUIPMENT_SLOT:
					// Only draw a slot that actually holds an item — empty slots need no unequipping.
					if (!engine.equipmentSlotEmpty(h.slotIndex))
					{
						Widget slot = client.getWidget(h.id);
						if (slot != null && !slot.isHidden())
						{
							itemHighlight(graphics, equippedItemId(h.slotIndex, slot), slot.getBounds());
						}
					}
					break;
				case INVENTORY_ITEM:
					itemHighlight(graphics, h.id, inventoryItemBounds(h.id));
					break;
				case SAVED_GEAR:
					for (int itemId : engine.savedGearItemIds())
					{
						itemHighlight(graphics, itemId, inventoryItemBounds(itemId));
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

	/** A simple box + arrow, for non-item interface elements (tabs, spec orb, spell icon). */
	private void box(Graphics2D graphics, Rectangle bounds)
	{
		if (bounds == null)
		{
			return;
		}
		Rectangle padded = new Rectangle(
			bounds.x - PAD, bounds.y - PAD, bounds.width + PAD * 2, bounds.height + PAD * 2);
		graphics.setColor(BOX);
		graphics.drawRect(padded.x, padded.y, padded.width, padded.height);
		widgetArrow(graphics, padded);
	}

	/** Traces the item's own shape: a semi-transparent fill under a crisp outline, plus an arrow. */
	private void itemHighlight(Graphics2D graphics, int itemId, Rectangle bounds)
	{
		if (bounds == null || itemId <= 0)
		{
			return;
		}
		ItemGlyph glyph = glyph(itemId);
		if (glyph != null)
		{
			graphics.drawImage(glyph.fill, bounds.x, bounds.y, null);
			graphics.drawImage(glyph.outline, bounds.x, bounds.y, null);
		}
		else
		{
			// Item sprite not ready yet — fall back to a box so the cue still shows this frame.
			graphics.setColor(ITEM_HIGHLIGHT);
			graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}
		widgetArrow(graphics, bounds);
	}

	/** Worn item id for a slot, preferring the equipment container and falling back to the widget. */
	private int equippedItemId(int slotIndex, Widget slot)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item item = worn.getItem(slotIndex);
			if (item != null && item.getId() > 0)
			{
				return item.getId();
			}
		}
		return slot.getItemId();
	}

	private void widgetArrow(Graphics2D graphics, Rectangle bounds)
	{
		// Interface items/spells are small — a smaller arrow that sits close to (into) the element.
		int size = Math.max(5, Math.round(config.guideArrowSize() * 0.75f));
		GuideOverlayUtil.arrowAbove(graphics, bounds, BOX, size, -10);
	}

	private ItemGlyph glyph(int itemId)
	{
		long key = ((long) itemId << 32) | (ITEM_HIGHLIGHT.getRGB() & 0xFFFFFFFFL);
		ItemGlyph cached = glyphCache.get(key);
		if (cached != null)
		{
			return cached;
		}
		BufferedImage src = itemManager.getImage(itemId, 1, false);
		if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0)
		{
			return null;
		}
		ItemGlyph glyph = buildGlyph(src);
		if (glyph != null)
		{
			glyphCache.put(key, glyph);
		}
		return glyph; // null while the async sprite is still blank — retried next frame
	}

	/** Builds the tinted fill and edge outline from an item sprite's alpha channel. */
	private ItemGlyph buildGlyph(BufferedImage src)
	{
		int w = src.getWidth();
		int h = src.getHeight();
		int rgb = ITEM_HIGHLIGHT.getRGB() & 0x00FFFFFF;
		int fillArgb = (ITEM_FILL_ALPHA << 24) | rgb;
		int lineArgb = (0xFF << 24) | rgb;

		BufferedImage fill = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		BufferedImage outline = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		boolean any = false;

		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				if (!opaque(src, x, y))
				{
					continue;
				}
				any = true;
				fill.setRGB(x, y, fillArgb);
				// An edge pixel is opaque but borders a transparent pixel (or the sprite edge).
				if (!opaque(src, x - 1, y) || !opaque(src, x + 1, y)
					|| !opaque(src, x, y - 1) || !opaque(src, x, y + 1))
				{
					outline.setRGB(x, y, lineArgb);
				}
			}
		}
		return any ? new ItemGlyph(fill, outline) : null;
	}

	private static boolean opaque(BufferedImage img, int x, int y)
	{
		if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight())
		{
			return false;
		}
		return (img.getRGB(x, y) >>> 24) > 40;
	}

	private static final class ItemGlyph
	{
		private final BufferedImage fill;
		private final BufferedImage outline;

		private ItemGlyph(BufferedImage fill, BufferedImage outline)
		{
			this.fill = fill;
			this.outline = outline;
		}
	}
}
