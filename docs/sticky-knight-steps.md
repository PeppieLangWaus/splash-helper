# Sticky Knight Setup — Step Spec

Fill this in the way you'd explain the setup to a new splasher. Don't worry about
being "technical" — just describe the procedure. For each step, the only column I
really need help with is **"Done when"**: the observable thing that means the step
is finished and the helper should advance to the next one.

If you don't know how the plugin could detect a step is done, **leave "Done when"
blank or write `???`** and add a note. I'll figure out whether there's a signal for
it, or we'll make that step advance manually (player clicks "Next").

---

## The steps

Duplicate/delete rows as needed. Order matters — this is the sequence the helper
walks through top to bottom.

| # | Player action (what they should do) | Highlight | Done when (advance condition) | Notes / unsure? |
|---|-------------------------------------|-----------|-------------------------------|-----------------|
| 1 |                                     |           |                               |                 |
| 2 |                                     |           |                               |                 |
| 3 |                                     |           |                               |                 |
| 4 |                                     |           |                               |                 |
| 5 |                                     |           |                               |                 |
| 6 |                                     |           |                               |                 |

**Highlight** = what should glow on screen during this step. Examples:
- an inventory item (e.g. "dragon spear")
- a UI element (e.g. "special attack orb", "autocast spell")
- a tile on the ground (e.g. "the tile the player should stand on", "the knight's trap tile")
- an NPC (e.g. "the knight, to spec it")
- nothing (just a text instruction)

---

## Tricky steps — expand here

For any step where the "Done when" is fuzzy, describe it in more detail below.
The more concretely you can say "the game state changes from X to Y," the better.

### Step __: <name>
- **What's happening in-game:**
- **How I'd know it worked (as a human watching):**
- **What could go wrong / how does the player retry:**

### Step __: <name>
- **What's happening in-game:**
- **How I'd know it worked (as a human watching):**
- **What could go wrong / how does the player retry:**

---

## Reference — signals the plugin can read

When you write a "Done when" condition, if you can phrase it in terms of one of
these, it's cleanly detectable. If it's *not* in this list, flag it and we'll find
a proxy or make it manual.

- **Player position** — exact tile the player is standing on (`WorldPoint`).
- **Knight position** — exact tile the sticky knight is on. Already tracked.
- **Special attack energy** — 0–100% (internally 0–1000; 25% = 250).
- **Special attack toggled on** — whether the spec is armed for the next attack.
- **Inventory contents** — which items are in the inventory (by item).
- **Equipment contents** — what's worn (e.g. is the dragon spear equipped).
- **Animation** — the player's current animation, e.g. the dragon spear spec ("Shove") animation — lets me detect the shove actually fired.
- **Attack target** — which NPC the player is currently attacking.
- **Autocast / spellbook state** — whether a splash spell is set to autocast.
- **Distance / adjacency** — whether player and knight are adjacent, N tiles apart, etc.

Things I generally **cannot** cleanly detect (call these out — we'll go manual or find a workaround):
- "the knight is now permanently stuck" (no direct flag — I can infer from position + it stopping movement)
- anything about intent ("the player meant to…")
- subtle visual cues that aren't backed by a game variable

---

## The alt account

The setup needs a second (alt) account standing in a specific spot, with some
requirements of its own. Constraint: **the alt must NOT need splash-helper installed.**

What the plugin *can* detect on the alt's behalf (all readable from your client):
- **Any player standing on a given tile** — via the local player list, each other
  player has a `WorldPoint`. So "a player is stood on tile T" is a clean signal.

What the plugin **cannot** verify about the alt (these live on the alt's client, out of reach):
- the alt's gear / inventory / stats / spell setup
- that it's actually *your* alt vs. a random passing player
- anything the alt is doing beyond "is on this tile"

So the plan is: treat **"a player (other than you) is standing on the alt tile"** as
the satisfied condition, and put the real alt requirements in the acknowledgement
screen (below) so the human confirms them. Fill in:

- **Alt's tile:** (the exact spot it must stand on — we can add a "set alt tile" like the existing boundary/knight tiles)
- **Alt's other requirements** (gear, spell, facing, whatever — human-checked, listed on the ack screen):
  -
  -
- **If the player leaves the tile mid-session**, should the helper warn / pause / do nothing?

---

## Requirements acknowledgement

Before the helper runs, the user must have read the requirements and pressed a button
to acknowledge them.

- **Show it only on first run** (persisted via config flag), or every session, or a
  re-show button in settings? (default plan: first run only, with a "show requirements
  again" config toggle)
- **What text should the acknowledgement screen show?** List everything the user must
  have set up before starting — including the alt requirements above:
  -
  -
  -
- **Where should it appear** — the side panel, or a blocking overlay on the game screen?

---

## Anything else

- Does the sequence ever need to loop or reset mid-way? (e.g. spec missed → go back to "build spec")
- Are there prerequisites before step 1 even starts? (location, gear, spell unlocked)
- Should the whole thing auto-arm when it detects you're at the knight, or start on a hotkey / button?
