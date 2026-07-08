package xyz.peppie.splashhelper.guide;

/**
 * IDs and magic numbers used by the sticky-knight setup guide.
 *
 * <p>Item and widget IDs were provided from an in-client dump; keep them here so the
 * step definitions in {@link StickyKnightSteps} stay readable.</p>
 */
public final class GuideConstants
{
	private GuideConstants() {}

	// ==================== Items ====================
	public static final int DRAGON_SPEAR = 1249;

	// ==================== Widgets (packed IDs) ====================
	public static final int WIDGET_INVENTORY_TAB = 10747959;
	public static final int WIDGET_SPELL_TAB = 10747962;
	public static final int WIDGET_EQUIPMENT_TAB = 10747960;
	public static final int WIDGET_INVENTORY = 9764864;
	public static final int WIDGET_ENTANGLE = 14286917;
	public static final int WIDGET_SNARE = 14286890;
	public static final int WIDGET_SPEC_BUTTON = 10485796;
	// Worn-equipment interface (group 387) slot components. child = 15 head, 19 body, 21 legs, 23 boots.
	public static final int WIDGET_EQUIP_HEAD = 25362447;
	public static final int WIDGET_EQUIP_BODY = 25362451;
	public static final int WIDGET_EQUIP_LEGS = 25362453;
	public static final int WIDGET_EQUIP_FEET = 25362455;

	// ==================== Equipment slot indices (EquipmentInventorySlot) ====================
	public static final int SLOT_HEAD = 0;
	public static final int SLOT_BODY = 4;
	public static final int SLOT_LEGS = 7;
	public static final int SLOT_FEET = 10;

	// ==================== Special attack varps ====================
	// 0..1000 (i.e. 25% == 250). One dragon-spear spec costs 25%, so a full bar = 4 specs.
	public static final int VARP_SPEC_ENERGY = 300;
	// 0/1 — whether the special attack is armed for the next attack.
	public static final int VARP_SPEC_ARMED = 301;

	public static final int SPEC_FULL = 1000;
	public static final int SPEC_PER_ATTACK = 250;

	// ==================== Spot animations ====================
	// The spot-anim (NPC inspector "G") applied to a target when a spell splashes on it.
	public static final int SPOTANIM_SPLASH = 85;

	// ==================== Requirements (shown on the first-run acknowledgement screen) ====================
	public static final String[] MAIN_REQUIREMENTS = {
		"On the standard spellbook",
		"Entangle unlocked, with runes in inventory/pouch",
		"A full special attack bar",
		"A dragon spear in your inventory",
		"Wearing your splasher gear (it will be saved on step 1)",
	};

	public static final String[] ALT_REQUIREMENTS = {
		"On the Lunar spellbook",
		"Standing on the alt tile (highlighted in-world)",
		"Energy Transfer unlocked, with runes",
		"Energy Transfer selected and ready to cast on you",
		"(The alt does NOT need Splash Helper installed)",
	};
}
