package xyz.peppie.splashhelper.guide;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.InventoryID;
import xyz.peppie.splashhelper.SplashHelperConfig;
import xyz.peppie.splashhelper.util.Constants;

/**
 * Drives the sticky-knight setup guide: tracks the current step/phase, evaluates advance
 * conditions each tick, and exposes state for the overlays. Implements {@link GuideContext}
 * so the declarative conditions in {@link StickyKnightSteps} can query live game state.
 */
@Slf4j
@Singleton
public class GuideEngine implements GuideContext
{
	public enum State
	{
		INACTIVE,
		AWAITING_ACK,
		RUNNING,
		FINISHED
	}

	private static final int KNIGHT_SEARCH_RADIUS = 15;

	private final Client client;
	private final SplashHelperConfig config;

	private final List<GuideStep> steps = StickyKnightSteps.build();

	@Getter
	private State state = State.INACTIVE;

	@Getter
	private int stepIndex = 0;
	@Getter
	private int phaseIndex = 0;

	/** The tracked sticky knight (nearest female knight to its spawn), or null. */
	@Getter
	private NPC knight;

	private int specEnergyAtPhaseStart;
	private Integer pendingKnightHit;
	private Instant stepEnteredAt = Instant.now();

	// Splasher-gear snapshot captured when step "1" completes (item id per equipment slot).
	private int[] gearSnapshot;

	// Previous-tick knight tile, for detecting movement (skippable step 19).
	private WorldPoint lastKnightPos;

	@Inject
	public GuideEngine(Client client, SplashHelperConfig config)
	{
		this.client = client;
		this.config = config;
	}

	// ==================== lifecycle ====================

	/** Begin the guide. Shows the acknowledgement screen first unless already acknowledged. */
	public void start()
	{
		stepIndex = 0;
		phaseIndex = 0;
		gearSnapshot = null;
		pendingKnightHit = null;
		lastKnightPos = null;

		if (!config.requirementsAcknowledged())
		{
			state = State.AWAITING_ACK;
			return;
		}
		beginRunning();
	}

	private void beginRunning()
	{
		state = State.RUNNING;
		onStepEnter();
	}

	/** Acknowledge the requirements screen and start running (also persists the flag). */
	public void acknowledge()
	{
		if (state != State.AWAITING_ACK)
		{
			return;
		}
		config.setRequirementsAcknowledged(true);
		beginRunning();
	}

	public void stop()
	{
		state = State.INACTIVE;
		knight = null;
	}

	public boolean isActive()
	{
		return state == State.RUNNING;
	}

	/** True while the guide is showing the ack screen or running (i.e. a stop would do something). */
	public boolean isStartedOrAck()
	{
		return state == State.RUNNING || state == State.AWAITING_ACK;
	}

	// ==================== per-tick ====================

	public void onGameTick()
	{
		if (state != State.RUNNING)
		{
			return;
		}
		if (client.getTopLevelWorldView() == null || client.getLocalPlayer() == null)
		{
			return;
		}

		updateKnight();

		GuideStep step = currentStep();
		GuideStep.StepPhase phase = currentPhase();
		if (phase != null && phase.advance.isMet(this))
		{
			advancePhase();
		}

		lastKnightPos = knight != null ? knight.getWorldLocation() : null;
	}

	public void onHitsplat(HitsplatApplied event)
	{
		if (state == State.RUNNING && knight != null && event.getActor() == knight)
		{
			pendingKnightHit = event.getHitsplat().getAmount();
		}
	}

	// ==================== manual controls (hotkeys) ====================

	/** Skip the current step (or acknowledge the requirements screen). */
	public void next()
	{
		if (state == State.AWAITING_ACK)
		{
			acknowledge();
			return;
		}
		if (state == State.RUNNING)
		{
			advanceStep();
		}
	}

	/** Go back one step. */
	public void back()
	{
		if (state != State.RUNNING || stepIndex == 0)
		{
			return;
		}
		stepIndex--;
		phaseIndex = 0;
		onStepEnter();
	}

	// ==================== transitions ====================

	private void advancePhase()
	{
		phaseIndex++;
		if (phaseIndex >= currentStep().phases.size())
		{
			advanceStep();
		}
		else
		{
			onPhaseEnter();
		}
	}

	private void advanceStep()
	{
		// Capture the splasher-gear loadout at the moment step 1 completes.
		if ("1".equals(currentStep().label))
		{
			captureGearSnapshot();
		}

		stepIndex++;
		phaseIndex = 0;

		if (stepIndex >= steps.size())
		{
			finish();
			return;
		}

		onStepEnter();

		// Auto-skip a skippable step whose situation is already resolved on entry
		// (step 19: if the knight is already walking when we arrive at the push tile).
		if (currentStep().skippable() && knightIsMoving())
		{
			advanceStep();
		}
	}

	private void finish()
	{
		state = State.FINISHED;
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			"<col=00ff00>Sticky knight setup complete!</col>", null);
	}

	private void onStepEnter()
	{
		stepEnteredAt = Instant.now();
		onPhaseEnter();
	}

	private void onPhaseEnter()
	{
		specEnergyAtPhaseStart = specEnergy();
		pendingKnightHit = null;
	}

	// ==================== accessors for overlays ====================

	public GuideStep currentStep()
	{
		return state == State.RUNNING && stepIndex < steps.size() ? steps.get(stepIndex) : null;
	}

	public GuideStep.StepPhase currentPhase()
	{
		GuideStep step = currentStep();
		if (step == null || phaseIndex >= step.phases.size())
		{
			return null;
		}
		return step.phases.get(phaseIndex);
	}

	public int totalSteps()
	{
		return steps.size();
	}

	/** The armor-off invariant is being violated on the current step. */
	public boolean armorViolation()
	{
		GuideStep step = currentStep();
		return step != null && step.armorMustBeOff()
			&& !(equipmentSlotEmpty(GuideConstants.SLOT_BODY)
				&& equipmentSlotEmpty(GuideConstants.SLOT_LEGS)
				&& equipmentSlotEmpty(GuideConstants.SLOT_FEET));
	}

	/** Player has been stuck on the current step long enough to hint they can go back. */
	public boolean timeoutHintActive()
	{
		GuideStep step = currentStep();
		if (step == null || step.suppressTimeoutHint())
		{
			return false;
		}
		return Duration.between(stepEnteredAt, Instant.now()).getSeconds() >= config.guideStepTimeoutSeconds();
	}

	/** True while a step involves casting Entangle, so the menu filter restricts it to the knight. */
	public boolean restrictEntangleToKnight()
	{
		GuideStep.StepPhase phase = currentPhase();
		if (phase == null)
		{
			return false;
		}
		for (StepHighlight h : phase.highlights)
		{
			if (h.type == StepHighlight.Type.WIDGET && h.id == GuideConstants.WIDGET_ENTANGLE)
			{
				return true;
			}
		}
		return false;
	}

	/** Item ids of the saved splasher gear that we strip and later re-equip (body/legs/boots). */
	public List<Integer> savedGearItemIds()
	{
		List<Integer> ids = new ArrayList<>();
		if (gearSnapshot != null)
		{
			for (int slot : new int[]{GuideConstants.SLOT_BODY, GuideConstants.SLOT_LEGS, GuideConstants.SLOT_FEET})
			{
				if (slot < gearSnapshot.length && gearSnapshot[slot] > 0)
				{
					ids.add(gearSnapshot[slot]);
				}
			}
		}
		return ids;
	}

	// ==================== GuideContext ====================

	@Override
	public boolean playerOn(WorldPoint tile)
	{
		Player local = client.getLocalPlayer();
		return local != null && tile.equals(local.getWorldLocation());
	}

	@Override
	public boolean knightOn(WorldPoint tile)
	{
		return knight != null && tile.equals(knight.getWorldLocation());
	}

	@Override
	public boolean otherPlayerOn(WorldPoint tile)
	{
		if (client.getTopLevelWorldView() == null)
		{
			return false;
		}
		Player local = client.getLocalPlayer();
		for (Player p : client.getTopLevelWorldView().players())
		{
			if (p != null && p != local && tile.equals(p.getWorldLocation()))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public int specEnergy()
	{
		return client.getVarpValue(GuideConstants.VARP_SPEC_ENERGY);
	}

	@Override
	public boolean specArmed()
	{
		return client.getVarpValue(GuideConstants.VARP_SPEC_ARMED) == 1;
	}

	@Override
	public boolean specDroppedSincePhaseStart()
	{
		return specEnergy() < specEnergyAtPhaseStart;
	}

	@Override
	public boolean equipmentSlotEmpty(int equipmentSlotIdx)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return true;
		}
		Item item = worn.getItem(equipmentSlotIdx);
		return item == null || item.getId() <= 0;
	}

	@Override
	public boolean itemEquipped(int itemId)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return false;
		}
		for (Item item : worn.getItems())
		{
			if (item != null && item.getId() == itemId)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean gearRestored()
	{
		if (gearSnapshot == null)
		{
			return false;
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return false;
		}
		for (int slot : new int[]{GuideConstants.SLOT_BODY, GuideConstants.SLOT_LEGS, GuideConstants.SLOT_FEET})
		{
			int expected = slot < gearSnapshot.length ? gearSnapshot[slot] : -1;
			if (expected <= 0)
			{
				continue; // slot was empty at snapshot time — nothing to restore
			}
			Item item = worn.getItem(slot);
			if (item == null || item.getId() != expected)
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public Integer pendingKnightHit()
	{
		return pendingKnightHit;
	}

	// ==================== helpers ====================

	private void captureGearSnapshot()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return;
		}
		Item[] items = worn.getItems();
		gearSnapshot = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			gearSnapshot[i] = items[i] != null ? items[i].getId() : -1;
		}
		log.debug("Captured splasher-gear snapshot ({} slots)", items.length);
	}

	private void updateKnight()
	{
		if (client.getTopLevelWorldView() == null)
		{
			knight = null;
			return;
		}

		// If our tracked knight is still valid and nearby, keep it.
		if (knight != null && knight.getWorldLocation() != null
			&& knight.getId() == Constants.FEMALE_KNIGHT_NPC_ID)
		{
			return;
		}

		NPC nearest = null;
		int nearestDist = Integer.MAX_VALUE;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null || npc.getId() != Constants.FEMALE_KNIGHT_NPC_ID)
			{
				continue;
			}
			WorldPoint loc = npc.getWorldLocation();
			if (loc == null)
			{
				continue;
			}
			int dist = loc.distanceTo(GuideTiles.KNIGHT_SPAWN);
			if (dist <= KNIGHT_SEARCH_RADIUS && dist < nearestDist)
			{
				nearest = npc;
				nearestDist = dist;
			}
		}
		knight = nearest;
	}

	private boolean knightIsMoving()
	{
		return knight != null && lastKnightPos != null
			&& knight.getWorldLocation() != null
			&& !knight.getWorldLocation().equals(lastKnightPos);
	}
}
