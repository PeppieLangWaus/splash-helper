package xyz.peppie.splashhelper.guide;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * One step of the guide. A step is an ordered list of {@link StepPhase}s so that a single
 * conceptual action ("spec the knight") can walk the player through sub-cues in order
 * (arm spec &rarr; click knight &rarr; move to the next tile). The step is complete when
 * its last phase completes.
 */
public final class GuideStep
{
	/** Per-step behaviour toggles. */
	public enum Flag
	{
		/** Suppress the "you can go back a step" timeout hint (e.g. while locating the knight). */
		SUPPRESS_TIMEOUT_HINT,
		/** The engine may auto-skip this step when its completion is already satisfied on entry. */
		SKIPPABLE,
		/**
		 * The -magic body/legs/boots must stay unequipped for this step. Applies across the
		 * spec/entangle window (steps 7-16); the engine warns if any of those slots is filled.
		 * Note: step 23's spec is deliberately NOT flagged — it runs with the splasher gear on.
		 */
		ARMOR_MUST_BE_OFF
	}

	/** A single cue within a step: what to highlight, what to tell the player, and when it's done. */
	public static final class StepPhase
	{
		public final String instruction;
		public final AdvanceCondition advance;
		public final List<StepHighlight> highlights;

		public StepPhase(String instruction, AdvanceCondition advance, StepHighlight... highlights)
		{
			this.instruction = instruction;
			this.advance = advance;
			this.highlights = Collections.unmodifiableList(Arrays.asList(highlights));
		}
	}

	public final String label;   // e.g. "1", "9-11"
	public final String title;   // short human description
	public final List<StepPhase> phases;
	private final Set<Flag> flags;

	public GuideStep(String label, String title, Set<Flag> flags, List<StepPhase> phases)
	{
		this.label = label;
		this.title = title;
		this.flags = flags;
		this.phases = Collections.unmodifiableList(phases);
	}

	public boolean suppressTimeoutHint()
	{
		return flags.contains(Flag.SUPPRESS_TIMEOUT_HINT);
	}

	public boolean skippable()
	{
		return flags.contains(Flag.SKIPPABLE);
	}

	public boolean armorMustBeOff()
	{
		return flags.contains(Flag.ARMOR_MUST_BE_OFF);
	}
}
