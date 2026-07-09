package xyz.peppie.splashhelper.guide;

import java.awt.Color;
import net.runelite.api.coords.WorldPoint;

/**
 * One thing to draw attention to during a phase: a ground tile, an NPC, an interface
 * widget, an inventory item, an equipment slot, or the saved splasher gear. The engine's
 * overlays interpret these; colors/labels are optional (the overlay supplies defaults).
 */
public final class StepHighlight
{
	public enum Type
	{
		/** The tracked sticky knight NPC. */
		KNIGHT,
		/** Another player standing on {@link #tile} (e.g. the alt), outlined by its model. */
		OTHER_PLAYER,
		/** A ground tile (world point). */
		TILE,
		/** An interface widget by packed id (tabs, spec orb, entangle). */
		WIDGET,
		/** The inventory slot holding a given item id. */
		INVENTORY_ITEM,
		/** A specific equipment slot widget. */
		EQUIPMENT_SLOT,
		/** Every saved-splasher-gear item currently sitting in the inventory (resolved live). */
		SAVED_GEAR
	}

	public final Type type;
	public final WorldPoint tile;
	public final int id;          // widget id, item id, or equipment slot widget id
	public final int slotIndex;   // EquipmentInventorySlot index for EQUIPMENT_SLOT; -1 otherwise
	public final String label;    // optional on-screen text
	public final Color color;     // optional override; null = overlay default
	public final boolean emphasize; // extra-prominent styling (e.g. "WAIT HERE")

	private StepHighlight(Type type, WorldPoint tile, int id, String label, Color color, boolean emphasize)
	{
		this(type, tile, id, -1, label, color, emphasize);
	}

	private StepHighlight(Type type, WorldPoint tile, int id, int slotIndex, String label, Color color, boolean emphasize)
	{
		this.type = type;
		this.tile = tile;
		this.id = id;
		this.slotIndex = slotIndex;
		this.label = label;
		this.color = color;
		this.emphasize = emphasize;
	}

	public static StepHighlight knight()
	{
		return new StepHighlight(Type.KNIGHT, null, -1, null, null, false);
	}

	/** Outline whichever player is standing on {@code tile} (used for the alt). */
	public static StepHighlight otherPlayer(WorldPoint tile, String label)
	{
		return new StepHighlight(Type.OTHER_PLAYER, tile, -1, label, null, false);
	}

	public static StepHighlight tile(WorldPoint tile)
	{
		return new StepHighlight(Type.TILE, tile, -1, null, null, false);
	}

	public static StepHighlight tile(WorldPoint tile, String label)
	{
		return new StepHighlight(Type.TILE, tile, -1, label, null, false);
	}

	/** A tile with prominent styling and text, e.g. "WAIT HERE & EQUIP GEAR". */
	public static StepHighlight tileEmphasized(WorldPoint tile, String label)
	{
		return new StepHighlight(Type.TILE, tile, -1, label, null, true);
	}

	public static StepHighlight widget(int widgetId)
	{
		return new StepHighlight(Type.WIDGET, null, widgetId, null, null, false);
	}

	public static StepHighlight inventoryItem(int itemId)
	{
		return new StepHighlight(Type.INVENTORY_ITEM, null, itemId, null, null, false);
	}

	/** A worn-equipment slot: {@code slotWidgetId} for its on-screen box, {@code slotIndex} to tell
	 *  whether it currently holds an item (only occupied slots are drawn). */
	public static StepHighlight equipmentSlot(int slotWidgetId, int slotIndex)
	{
		return new StepHighlight(Type.EQUIPMENT_SLOT, null, slotWidgetId, slotIndex, null, null, false);
	}

	public static StepHighlight savedGear()
	{
		return new StepHighlight(Type.SAVED_GEAR, null, -1, null, null, false);
	}
}
