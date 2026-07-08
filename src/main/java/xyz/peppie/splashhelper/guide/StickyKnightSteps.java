package xyz.peppie.splashhelper.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import xyz.peppie.splashhelper.guide.GuideStep.StepPhase;

import static xyz.peppie.splashhelper.guide.GuideStep.Flag.ARMOR_MUST_BE_OFF;
import static xyz.peppie.splashhelper.guide.GuideStep.Flag.SKIPPABLE;
import static xyz.peppie.splashhelper.guide.GuideStep.Flag.SUPPRESS_TIMEOUT_HINT;

import static xyz.peppie.splashhelper.guide.AdvanceCondition.and;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.equipmentSlotsEmpty;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.gearRestored;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.itemEquipped;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.knightAnyHit;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.knightHit;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.knightOn;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.knightOnAny;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.knightSplash;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.playerAndKnight;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.playerOn;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.specArmed;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.specDropped;
import static xyz.peppie.splashhelper.guide.AdvanceCondition.specFull;

import static xyz.peppie.splashhelper.guide.GuideConstants.DRAGON_SPEAR;
import static xyz.peppie.splashhelper.guide.GuideConstants.SLOT_BODY;
import static xyz.peppie.splashhelper.guide.GuideConstants.SLOT_FEET;
import static xyz.peppie.splashhelper.guide.GuideConstants.SLOT_LEGS;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_ENTANGLE;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_BODY;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_FEET;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_LEGS;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIPMENT_TAB;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_INVENTORY_TAB;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_SPEC_BUTTON;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_SPELL_TAB;

import static xyz.peppie.splashhelper.guide.GuideTiles.ALT;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR1;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR2;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR3;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR4;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR6;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR7;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR8;
import static xyz.peppie.splashhelper.guide.GuideTiles.KNIGHT;
import static xyz.peppie.splashhelper.guide.GuideTiles.PLAYER;
import static xyz.peppie.splashhelper.guide.GuideTiles.POSITION;
import static xyz.peppie.splashhelper.guide.GuideTiles.PRE_TRAP;
import static xyz.peppie.splashhelper.guide.GuideTiles.PULL;
import static xyz.peppie.splashhelper.guide.GuideTiles.PUSH;
import static xyz.peppie.splashhelper.guide.GuideTiles.SPLASHER;
import static xyz.peppie.splashhelper.guide.GuideTiles.STOP1;
import static xyz.peppie.splashhelper.guide.GuideTiles.STOP2;
import static xyz.peppie.splashhelper.guide.GuideTiles.TRAP;

/**
 * The sticky-knight setup, as an ordered list of {@link GuideStep}s (Entangle variant).
 *
 * <p>Faithful to the hand-authored spec. A few things are deliberately marked for live
 * tuning — see the TODO(live) notes. In particular the westward push is authored as two
 * grouped steps (specs 2-4 and specs 5-7) because only the end-of-group checkpoints were
 * pinned down; once verified in-game these can be split into per-spec sub-steps.</p>
 *
 * <p>Movement model established during design: the knight walks 1 tile/tick, the player
 * follows and always rests one tile <em>east</em> of the knight along the push lane. A
 * full spec bar = 4 dragon-spear specs (25% each); phase 1 spends the bar, the alt's
 * Energy Transfer refills it, phase 2 spends 3 more.</p>
 */
public final class StickyKnightSteps
{
	private StickyKnightSteps() {}

	public static List<GuideStep> build()
	{
		List<GuideStep> steps = new ArrayList<>();

		// 1 — Locate & tag the knight. Timeout hint suppressed (finding it can take a while).
		//     The engine captures the splasher-gear snapshot when this step completes,
		//     which guarantees the player is wearing the splashing loadout at that moment.
		steps.add(step("1", "Find the sticky knight", flags(SUPPRESS_TIMEOUT_HINT),
			phase("Find and splash the sticky knight to tag it.",
				knightAnyHit(),
				StepHighlight.knight())));

		// 2 — Take the opening tile; the knight should settle one tile east.
		steps.add(step("2", "Take your opening tile",
			phase("Stand on the marked tile — the knight should settle just east of you.",
				playerAndKnight(KNIGHT, PLAYER),
				StepHighlight.tile(KNIGHT))));

		// 3 — Bump south so the knight steps onto the tile you vacated.
		steps.add(step("3", "Draw the knight onto the tile",
			phase("Step onto this tile and wait for the knight to move onto the tile you left.",
				knightOn(KNIGHT),
				StepHighlight.tile(POSITION))));

		// 4 — Line up directly east of the knight, ready to shove.
		steps.add(step("4", "Line up east of the knight",
			phase("Move here — you should end up directly east of the knight.",
				playerOn(PLAYER),
				StepHighlight.tile(PLAYER))));

		// 5 — Strip the -magic gear so Entangle can actually land later.
		steps.add(step("5", "Unequip body, legs & boots",
			phase("Open the Equipment tab and unequip your body, legs and boots.",
				equipmentSlotsEmpty(SLOT_BODY, SLOT_LEGS, SLOT_FEET),
				StepHighlight.widget(WIDGET_EQUIPMENT_TAB),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_BODY),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_LEGS),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_FEET))));

		// 6 — Equip the spear (this also clears the weapon/shield slots).
		steps.add(step("6", "Equip the dragon spear",
			phase("Open your inventory and equip the dragon spear.",
				itemEquipped(DRAGON_SPEAR),
				StepHighlight.widget(WIDGET_INVENTORY_TAB),
				StepHighlight.inventoryItem(DRAGON_SPEAR))));

		// 7 — First bind. A >0 hit means the bind actually applied.
		steps.add(step("7", "Entangle the knight", flags(ARMOR_MUST_BE_OFF),
			phase("Open the spellbook and cast Entangle on the knight.",
				knightHit(1),
				StepHighlight.widget(WIDGET_SPELL_TAB),
				StepHighlight.widget(WIDGET_ENTANGLE),
				StepHighlight.knight())));

		// 8 — First spec, spelled out phase-by-phase as the template for the push lane.
		steps.add(step("8", "Dragon spear spec (1/7)", flags(ARMOR_MUST_BE_OFF),
			phase("Arm the special attack.",
				specArmed(),
				StepHighlight.widget(WIDGET_SPEC_BUTTON)),
			phase("Special-attack the knight to shove it west.",
				specDropped(),
				StepHighlight.knight()),
			phase("Follow the knight onto this tile.",
				playerAndKnight(DSPEAR1, DSPEAR2),
				StepHighlight.tile(DSPEAR1))));

		// 9-11 — Specs 2-4 (rest of the first bar). TODO(live): split into 3 per-spec steps
		//        once the intermediate landing tiles are confirmed in-game.
		steps.add(step("9-11", "Dragon spear specs (2-4/7)", flags(ARMOR_MUST_BE_OFF),
			phase("Keep specing and shuffling west until the knight reaches the marked tile.",
				playerAndKnight(DSPEAR3, DSPEAR4),
				StepHighlight.widget(WIDGET_SPEC_BUTTON),
				StepHighlight.knight(),
				StepHighlight.tile(DSPEAR3))));

		// 12 — Alt refills the spec bar via Energy Transfer. We only detect "spec back to full".
		steps.add(step("12", "Wait for spec transfer",
			phase("Wait on-spot while your alt casts Energy Transfer to refill your special.",
				specFull(),
				StepHighlight.tile(ALT, "Alt"))));

		// 13 — Second bind. Engine restricts Entangle's target to the knight while this step is active.
		steps.add(step("13", "Entangle the knight again", flags(ARMOR_MUST_BE_OFF),
			phase("Cast Entangle on the knight again.",
				knightHit(1),
				StepHighlight.widget(WIDGET_SPELL_TAB),
				StepHighlight.widget(WIDGET_ENTANGLE),
				StepHighlight.knight())));

		// 14-16 — Specs 5-7. TODO(live): split into per-spec steps once tiles are confirmed.
		steps.add(step("14-16", "Dragon spear specs (5-7/7)", flags(ARMOR_MUST_BE_OFF),
			phase("Keep specing and shuffling west until the knight reaches the marked tile.",
				playerAndKnight(DSPEAR6, DSPEAR7),
				StepHighlight.widget(WIDGET_SPEC_BUTTON),
				StepHighlight.knight(),
				StepHighlight.tile(DSPEAR6))));

		// 17 — Wait for the bind to wear off; re-equip the splasher gear meanwhile.
		steps.add(step("17", "Wait & re-equip splasher gear",
			phase("WAIT HERE and re-equip your splasher gear while the knight walks off.",
				and(knightOn(STOP1), gearRestored()),
				StepHighlight.tileEmphasized(DSPEAR6, "WAIT HERE & EQUIP GEAR"),
				StepHighlight.savedGear())));

		// 18 — Move to the push tile.
		steps.add(step("18", "Move to the push tile",
			phase("Move to this tile.",
				playerOn(PUSH),
				StepHighlight.tile(PUSH))));

		// 19 — Splash once to halt the knight. Skippable: if it's already moving on arrival at PUSH,
		//      the engine skips this step (see the step-18 note in the spec).
		steps.add(step("19", "Splash to stop the knight", flags(SKIPPABLE),
			phase("Splash the knight once, then WAIT HERE.",
				knightOnAny(STOP2, SPLASHER),
				StepHighlight.tileEmphasized(PUSH, "WAIT HERE"))));

		// 20 — Reposition to the pull tile.
		steps.add(step("20", "Move to the pull tile",
			phase("Move to this tile.",
				playerOn(PULL),
				StepHighlight.tile(PULL))));

		// 21 — Splash to pull the knight forward into the pre-trap tile.
		steps.add(step("21", "Splash to pull the knight",
			phase("Splash the knight once to pull it forward.",
				knightOn(PRE_TRAP),
				StepHighlight.knight())));

		// 22 — Slide to the spear tile while the knight stays put.
		steps.add(step("22", "Move to the spear tile",
			phase("Move here while the knight stays on its tile.",
				playerAndKnight(DSPEAR8, PRE_TRAP),
				StepHighlight.tile(DSPEAR8))));

		// 23 — Final spec shoves the knight into the trap.
		steps.add(step("23", "Spec into the trap",
			phase("Arm the special attack.",
				specArmed(),
				StepHighlight.widget(WIDGET_SPEC_BUTTON)),
			phase("Spec the knight into the trap tile.",
				knightOn(TRAP),
				StepHighlight.knight())));

		// 24 — Take the splashing tile.
		steps.add(step("24", "Move to your splashing tile",
			phase("Move to your splashing tile.",
				playerOn(SPLASHER),
				StepHighlight.tile(SPLASHER))));

		// 25 — Done: the first 0 confirms you're splashing the trapped knight.
		steps.add(step("25", "Splash — setup complete",
			phase("Splash the knight — the first 0 means setup is complete!",
				knightSplash(),
				StepHighlight.knight())));

		return steps;
	}

	// ==================== small builder helpers ====================

	private static GuideStep step(String label, String title, StepPhase... phases)
	{
		return new GuideStep(label, title, EnumSet.noneOf(GuideStep.Flag.class), Arrays.asList(phases));
	}

	private static GuideStep step(String label, String title, Set<GuideStep.Flag> flags, StepPhase... phases)
	{
		return new GuideStep(label, title, flags, Arrays.asList(phases));
	}

	private static Set<GuideStep.Flag> flags(GuideStep.Flag... f)
	{
		return f.length == 0 ? EnumSet.noneOf(GuideStep.Flag.class) : EnumSet.copyOf(Arrays.asList(f));
	}

	private static StepPhase phase(String instruction, AdvanceCondition advance, StepHighlight... highlights)
	{
		return new StepPhase(instruction, advance, highlights);
	}
}
