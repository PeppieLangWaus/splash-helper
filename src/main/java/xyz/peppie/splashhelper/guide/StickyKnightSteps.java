package xyz.peppie.splashhelper.guide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.runelite.api.coords.WorldPoint;

import xyz.peppie.splashhelper.guide.GuideStep.StepPhase;

import static xyz.peppie.splashhelper.guide.GuideStep.Flag.ARMOR_MUST_BE_OFF;
import static xyz.peppie.splashhelper.guide.GuideStep.Flag.SKIPPABLE;
import static xyz.peppie.splashhelper.guide.GuideStep.Flag.SUPPRESS_TIMEOUT_HINT;

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
import static xyz.peppie.splashhelper.guide.GuideConstants.SLOT_HEAD;
import static xyz.peppie.splashhelper.guide.GuideConstants.SLOT_LEGS;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_ENTANGLE;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_BODY;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_FEET;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_HEAD;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIP_LEGS;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_EQUIPMENT_TAB;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_INVENTORY_TAB;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_SPEC_BUTTON;
import static xyz.peppie.splashhelper.guide.GuideConstants.WIDGET_SPELL_TAB;

import static xyz.peppie.splashhelper.guide.GuideTiles.ALT;
import static xyz.peppie.splashhelper.guide.GuideTiles.ALT2;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR1;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR2;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR3;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR4;
import static xyz.peppie.splashhelper.guide.GuideTiles.DSPEAR5;
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
 * <p>Faithful to the hand-authored spec. The westward push is authored as one step per spec
 * (specs 1-4 spend the first bar, specs 5-7 spend the refill), each cued in sequence — arm the
 * special, spec the knight, then follow onto the tile it vacated. The exact per-spec landing
 * tiles are subject to live in-game tuning.</p>
 *
 * <p>Movement model established during design: the knight walks 1 tile/tick, the player follows
 * and always rests one tile <em>east</em> of the knight along the push lane. A full spec bar =
 * 4 dragon-spear specs (25% each); phase 1 spends the bar, the alt's Lunar Energy Transfer
 * refills it, phase 2 spends 3 more push specs, and the trap shove (step 23) spends the 4th.</p>
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

		steps.add(step("2", "Take your opening tile",
			phase("Stand on the marked tile — the knight should settle just east of you.",
				playerAndKnight(KNIGHT, PLAYER),
				StepHighlight.tile(KNIGHT))));

		steps.add(step("3", "Draw the knight onto the tile",
			phase("Step onto this tile and wait for the knight to move onto the tile you left.",
				knightOn(KNIGHT),
				StepHighlight.tile(POSITION))));

		steps.add(step("4", "Line up east of the knight",
			phase("Move here — you should end up directly east of the knight.",
				playerOn(PLAYER),
				StepHighlight.tile(PLAYER))));

		// 5 — Strip the -magic gear so Entangle can actually land later. Only occupied slots light up.
		steps.add(step("5", "Unequip head, body, legs & boots",
			phase("Open the Equipment tab and unequip your head, body, legs and boots.",
				equipmentSlotsEmpty(SLOT_HEAD, SLOT_BODY, SLOT_LEGS, SLOT_FEET),
				StepHighlight.widget(WIDGET_EQUIPMENT_TAB),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_HEAD, SLOT_HEAD),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_BODY, SLOT_BODY),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_LEGS, SLOT_LEGS),
				StepHighlight.equipmentSlot(WIDGET_EQUIP_FEET, SLOT_FEET))));

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

		// 8-11 — First spec bar: four dragon-spear shoves west (one tile per spec, 25% each). The
		//        knight starts on KNIGHT with the player one tile east on PLAYER; the first shove
		//        lands the knight on DSPEAR1. Each spec cues in sequence — arm the spec, spec the
		//        knight, then follow onto the tile it vacated (one tile east of where it ends).
		steps.add(specStep("8", "1/7", KNIGHT, DSPEAR1));
		steps.add(specStep("9", "2/7", DSPEAR1, DSPEAR2));
		steps.add(specStep("10", "3/7", DSPEAR2, DSPEAR3));
		steps.add(specStep("11", "4/7", DSPEAR3, DSPEAR4));

		// 12 — The bar is empty after four shoves; the alt refills it via Lunar Energy Transfer.
		//      We can only detect "spec back to full". The alt casts from the ALT tile, then moves
		//      one tile north of DSPEAR7 (ALT2) to steady the knight's otherwise-inconsistent walk-off.
		steps.add(step("12", "Wait for spec transfer",
			phase("Wait on-spot while your alt casts Energy Transfer to refill your special.",
				specFull(),
				StepHighlight.tile(ALT, "Alt"),
				StepHighlight.otherPlayer(ALT, null),
				StepHighlight.tile(ALT2, "Move alt here after transfer"))));

		// 13 — Second bind. Engine restricts Entangle's target to the knight while this step is active.
		steps.add(step("13", "Entangle the knight again", flags(ARMOR_MUST_BE_OFF),
			phase("Cast Entangle on the knight again.",
				knightHit(1),
				StepHighlight.widget(WIDGET_SPELL_TAB),
				StepHighlight.widget(WIDGET_ENTANGLE),
				StepHighlight.knight())));

		// 14-16 — Second (refilled) bar: three more shoves west. The fourth spec of this bar is
		//         held back for the trap shove (step 25).
		steps.add(specStep("14", "5/7", DSPEAR4, DSPEAR5));
		steps.add(specStep("15", "6/7", DSPEAR5, DSPEAR6));
		steps.add(specStep("16", "7/7", DSPEAR6, DSPEAR7));

		// 17 — Re-equip the splasher gear (staff + shield + the head/body/legs/boots we stripped).
		steps.add(step("17", "Re-equip splasher gear",
			phase("Re-equip your splasher gear.",
				gearRestored(),
				StepHighlight.tileEmphasized(DSPEAR6, "EQUIP GEAR"),
				StepHighlight.savedGear())));

		// 18 — Splash the knight once so it walks off predictably (the alt north of DSPEAR7 blocks it).
		steps.add(step("18", "Splash the knight",
			phase("Splash the knight once.",
				knightSplash(),
				StepHighlight.knight())));

		// 19 — Deterministic now: with the player blocking east and the alt blocking north, the knight
		//      (dragged outside its wander range) can only shuffle back the way it came until it
		//      settles on STOP1 at the edge of its wander range. Always the same tile.
		steps.add(step("19", "Wait for the knight to move off",
			phase("WAIT HERE — the knight will walk itself back to the stop tile.",
				knightOn(STOP1),
				StepHighlight.tileEmphasized(DSPEAR6, "WAIT HERE"),
				StepHighlight.tile(STOP1, "Knight stops here"))));

		steps.add(step("20", "Move to the push tile",
			phase("Move to this tile.",
				playerOn(PUSH),
				StepHighlight.tile(PUSH))));

		// 21 — Splash once to halt the knight. Skippable: if it's already moving on arrival at PUSH,
		//      the engine skips this step (see the step-20 note in the spec).
		steps.add(step("21", "Splash to stop the knight", flags(SKIPPABLE),
			phase("Splash the knight once, then WAIT HERE.",
				knightOnAny(STOP2, SPLASHER),
				StepHighlight.tileEmphasized(PUSH, "WAIT HERE"))));

		steps.add(step("22", "Move to the pull tile",
			phase("Move to this tile.",
				playerOn(PULL),
				StepHighlight.tile(PULL))));

		steps.add(step("23", "Splash to pull the knight",
			phase("Splash the knight once to pull it forward.",
				knightOn(PRE_TRAP),
				StepHighlight.knight())));

		steps.add(step("24", "Move to the spear tile",
			phase("Move here while the knight stays on its tile.",
				playerAndKnight(DSPEAR8, PRE_TRAP),
				StepHighlight.tile(DSPEAR8))));

		// 25 — Final spec (last of the refilled bar) shoves the knight into the trap.
		steps.add(step("25", "Spec into the trap",
			phase("Arm the special attack.",
				specArmed(),
				StepHighlight.widget(WIDGET_SPEC_BUTTON)),
			phase("Spec the knight into the trap tile.",
				knightOn(TRAP),
				StepHighlight.knight())));

		steps.add(step("26", "Move to your splashing tile",
			phase("Move to your splashing tile.",
				playerOn(SPLASHER),
				StepHighlight.tile(SPLASHER))));

		steps.add(step("27", "Splash — setup complete",
			phase("Splash the knight — the first splash means setup is complete!",
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

	/**
	 * One push-lane spec, cued in sequence: arm the special, spec the knight one tile west, then
	 * follow onto the tile it vacated ({@code playerTile}, one tile east of where the knight ends).
	 */
	private static GuideStep specStep(String label, String progress, WorldPoint playerTile, WorldPoint knightTile)
	{
		return step(label, "Dragon spear spec (" + progress + ")", flags(ARMOR_MUST_BE_OFF),
			phase("Arm the special attack.",
				specArmed(),
				StepHighlight.widget(WIDGET_SPEC_BUTTON)),
			phase("Special-attack the knight to shove it one tile west.",
				specDropped(),
				StepHighlight.knight()),
			phase("Follow the knight onto this tile.",
				playerAndKnight(playerTile, knightTile),
				StepHighlight.tile(playerTile)));
	}
}
