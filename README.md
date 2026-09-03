# Entourage

A small group of cosmetic figures that walk with you and hold a pose when you
stop. Client-side only: nobody else's client draws them, no packet is sent, and
nothing about any other player is read.

Built against RuneLite client **1.12.38**.

## Why

The brief was three words wide: *"group of people follow you or post up cool
anime style."* A crew that moves with you and arranges itself when you stand
still. "Anime style" means a deliberate stance and a coordinated formation — a
vibe. It never means a character: every model here comes out of the game's own
cache through an `NPCComposition`, and nothing copyrighted or trademarked is
imitated, named or reproduced.

The direct technical ancestor is [`../lively-cities`](../lively-cities), which
is live on the Plugin Hub and renders cosmetic humanoids from `RuneLiteObject`.
The techniques below are reused from it heavily and the debts are named where
they are owed.

## What is implemented

One follower, walking correctly. That is the whole of this slice, and the reason
it is the whole of it is that the two hardest problems in a follower plugin are
both in that sentence.

### The movement system

`RuneLiteObject` has **no walk API at all** — no destination, no speed, no path.
A plugin that moves one has to supply both halves itself, and putting both on the
same clock is exactly how it goes wrong:

- **`FollowerWalk.tick(..)` is game-tick work.** It decides whether to move,
  takes at most one tile, and works out which way that faces. One tile per game
  tick is the speed the game walks at.
- **`FollowerWalk.localPoint(..)` is frame work.** It slides the drawn position
  between the tile the follower left and the tile it is walking to, and
  `EntouragePlugin.onBeforeRender` supplies the fraction. Without this, a
  follower is redrawn one whole tile to the side every 600ms — a figure
  *teleporting* rather than walking. A movement system built on `onGameTick`
  alone produces exactly that and nothing else.

The interpolation fraction comes from `client.getGameCycle()`, the client's own
20ms counter, and not from wall-clock time — which would also work, right up
until the first time the client is paused or the machine sleeps.

**The animation is advanced by the client, never by this plugin.**
`RuneLiteObject.tick(ticksSinceLastFrame)` is called once per frame for every
registered object; a second caller runs every animation at double speed.
`FollowerTest.thePluginNeverAdvancesTheAnimationItself` pins the count at zero.

### The animations

`NPCComposition` gives you models and recolours and **no animation ids at all** —
`javap` on the 1.12.38 interface lists name, models, chathead models, ops,
actions, interactible, minimap-visible, id, combat level, configs, `transform()`
and size, and not one of those is a sequence. A figure dressed that way and left
alone is a static mesh: standing still that reads as a statue, and walking it is
a body sliding across the ground, which is the single most visible way this
plugin could look broken.

So `EntourageAnimation` names the ids through `net.runelite.api.gameval.AnimationID`
— `HUMAN_READY` for the pose, `HUMAN_WALK_F` for the walk — rather than as
literals, which is what this repo's `AGENTS.md` requires and what
`../lively-cities` verified its own table against. Two controllers are built once
and kept, because constructing one (or calling `setAnimation` on one) resets its
frame to zero; rebuilding on every idle-to-walk switch is what turns a walk cycle
into a stutter.

### Collision: the edge, not the tile

`../lively-cities`' `StandableGround` answers *"could a person be standing on
this tile"* — it masks with `BLOCK_MOVEMENT_FULL` and deliberately ignores the
eight directional bits, because a pavement tile with a wall along its north side
is still a tile a person stands on. That is the right question for a figure
placed by hand on ground a human vetted, and the wrong one for a figure that
walks: the tile on the far side of that wall is perfectly standable, and a
follower consulting only `BLOCK_MOVEMENT_FULL` walks straight through the wall to
reach it.

`WalkableStep` asks the other question, and it turns out RuneLite already ships
the primitive: **`WorldArea.canTravelInDirection(WorldView, dx, dy)`**. For a 1x1
step it tests the destination tile against `BLOCK_MOVEMENT_FULL` *plus the bit
for the edge the step enters through*, and for a diagonal it also tests the
corner bit and both orthogonal half-steps, so a figure cannot cut the corner of a
doorframe. Reimplementing that from `CollisionDataFlag` would have been three of
those four rules and a bug.

What `WalkableStep` adds is the failure handling, which the API has none of. Each
of these throws out of `canTravelInDirection` on live data — verified by
disassembly, and two of them are pinned by tests that assert the raw API really
does throw:

| Situation | Raw API | `WalkableStep` |
| --- | --- | --- |
| Tile outside the loaded scene | `NullPointerException` | `UNKNOWN` |
| Destination off the edge of the flags array | `ArrayIndexOutOfBoundsException` | `UNKNOWN` |
| No collision map for that plane yet | `NullPointerException` | `UNKNOWN` |
| Plane outside the map array | `ArrayIndexOutOfBoundsException` | `UNKNOWN` |
| `getCollisionMaps()` itself null | `false`, i.e. "blocked" | `UNKNOWN` |
| Non-top-level (`WorldEntity`) view | answers about the wrong tile, silently | `UNKNOWN` |

That last row is the one that matters most. The client sizes a non-top-level
collision map with its origin one tile to the south-west, and
`canTravelInDirection` indexes with plain scene coordinates either way — so
inside a world entity it answers confidently about the tile diagonally behind the
one asked about. Off-by-one and silent is exactly the shape of wrong that puts a
figure half inside a wall.

**`UNKNOWN` is not "probably fine".** `FollowerWalk` treats it exactly as it
treats `BLOCKED`: the step is skipped. That is the discipline `../lively-cities`
established — skip rather than nudge — and it is the same trade here. A follower
that misses a step stands still for 600ms; a follower that guesses walks through
a doorframe.

### The anchor

`Actor.getWorldLocation()` is the **server** tile. Disassembling 1.12.38's
injected client, the actor class builds that `WorldPoint` out of `pathX[0]` and
`pathY[0]` — the head of the movement queue, i.e. where the server has already
put the player and the client is still animating towards. `getLocalLocation()`
reads the actor's own render coordinates instead. The two disagree for the whole
of every step, and by more than one tile while running, because a run is two
queued tiles in one game tick.

`FollowerAnchor` therefore takes the render position and rounds it down to the
tile it sits in. `FollowerAnchorTest` pins both halves: that a running player
anchors on the tile he is *drawn* in rather than the one the server has him on,
and — the stronger claim — that `getWorldLocation()` is never read at all.

### "I don't know where the player is" is a branch, not an accident

For a follower that is a routine state: the login screen, every scene load, and
any time the player is inside a `WorldEntity` (a boat, say). `FollowerAnchor`
names four resolutions, and `EntourageScene` has one rule for all of them: **the
entourage is not drawn.** Not frozen in place — a `RuneLiteObject` left active
through a scene load stands at a `LocalPoint` that now addresses somewhere else
entirely, and that is a figure in the world nothing owns.

Foreign world views are refused rather than half-supported. A `RuneLiteObject`
*can* be placed into a `WorldEntity`'s view, but its collision map cannot be read
with scene coordinates, so a follower on a boat would walk through the gunwale.
Supporting it is a later slice with its own collision path.

## Files

| File | What it is |
| --- | --- |
| `EntouragePlugin.java` | Plugin descriptor, the two clocks (`GameTick` / `BeforeRender`), the interpolation fraction, and teardown |
| `EntourageScene.java` | Owns every follower; resolves the anchor once a tick and decides whether anything is drawn at all |
| `Follower.java` | One figure bound to one `RuneLiteObject`: model build, both animation controllers, spawn/despawn, cache-retry budget |
| `FollowerWalk.java` | The movement system — the per-tick decision and the per-frame interpolation |
| `WalkableStep.java` | "Can a figure walk from here to there", from the client's collision map, with `UNKNOWN` for every way of not knowing |
| `FollowerAnchor.java` | Where the entourage forms up: the tile the player is *drawn* on, or a named reason there isn't one |
| `StepOrientation.java` | The eight facings, in the client's 0..2047 turn units |
| `EntourageAnimation.java` | The animation ids, named through `gameval` and argued for |
| `EntourageFigure.java` | Which NPC a figure wears and which two animations it plays; the shipped roster |
| `FollowerAppearance.java` | One NPC's models and recolour pairs, read off the client, with every failure handled |

The test source set adds `Stub*` classes (every abstract method of an API
interface, throwing — mechanically generated from `javap` against the 1.12.38 jar
and checked in) and `Fake*` classes (the handful of methods this plugin actually
calls). `EntouragePluginTest` is the `./gradlew run` entry point and lives there
because dev tooling has no business in a shipped jar.

## Known limitations

- **This is greedy stepping, not pathfinding.** A follower tries the diagonal
  towards the anchor and then each of its two axis components; if none is legal
  it stands still. It will get stuck on the wrong side of a wall it would have to
  walk *away* from to get around. `FollowerWalk.RECALL_DISTANCE` is what stops
  that being permanent: at twelve tiles the follower is put back on the player's
  own tile — ground the player is standing on, so ground a figure can be on —
  rather than a search for a free tile nearby, which is how a deterministic
  placement stops being deterministic.
- **A recall is a pop, not an entrance.** The follower appears on the player's
  tile and steps off it on the next tick. Making that look deliberate is what an
  entrance effect would be for, and that is a later slice.
- **One figure.** Formation shapes, pose variety and any config surface are later
  slices. `EntourageFigure.DEFAULT_ROSTER` is the seam they extend.
- **No menu entries at all.** A `RuneLiteObject` is not clickable unless a plugin
  synthesises entries for it, and several figures permanently inside your click
  radius would add rows to nearly every right-click in the game. When that
  changes, the treatment to copy is `CitizenMenu` in `../lively-cities`:
  deprioritised, `MenuAction.RUNELITE`-typed, and an Examine that says out loud
  what the figure is. With figures that follow *you*, mistaken identity matters
  more than it does for static townsfolk.
- **An entourage of yous is not a feature and has not been promised.**
  `PlayerComposition` exposes equipment and body-part-colour ids, but there is no
  counterpart to `NPCComposition.getModels()` — no one call that hands back a
  buildable model array — so cloning the player's own outfit means reproducing
  the client's item-to-model resolution. That is a spike somebody has to do the
  reading for.
- **Nothing here has been measured for frame cost.** `../lively-cities`'
  `RenderPolicy` caps active objects and budgets model builds per frame, and its
  published figures describe a *mostly-idle* crowd — which cannot be assumed to
  describe a figure that is moving most of the time. With one follower there is
  nothing to cap; before the roster grows, the frame cost of a moving figure
  needs measuring rather than inheriting.

### Wanted from a real client

Nothing below can be settled without the cache, and none of it is guessed at in
the code — each is stated as an open question where it lives.

1. **Does `NpcID.ROGUE` (526) dress a human-rigged body?** The argument for it is
   second-hand but not thin: `../lively-cities` already dresses a figure from 526
   and animates it with a framemap-0 human pose, live on the Plugin Hub, and
   `HUMAN_READY`/`HUMAN_WALK_F` are on that same framemap. If it turns out wrong,
   `EntourageFigure.FARMER` (`NpcID.FARMER1`) is the fallback: `../lively-cities`
   ships it with this *exact* stand/walk pair on a figure that wanders, so that
   combination is field-proven rather than argued. Swapping is one entry in
   `DEFAULT_ROSTER`.
2. **Does the walk read as a walk?** Frame pacing, and whether the stride matches
   the tile crossing at one tile per game tick.
3. **How far ahead does the server tile actually run?** That
   `getWorldLocation()` is the wrong field is settled by disassembly. How many
   tiles of error that is worth at a full run is an observation.
4. **Does the recall look acceptable?** Twelve tiles is reasoned, not measured.
5. **Is one tile of following distance right?** A follower at Chebyshev distance
   1 may read as crowding.

## Development

```bash
./gradlew build     # compile and run the tests
./gradlew test      # tests only, every name printed
./gradlew run       # launch a dev client with the plugin loaded
```

`./gradlew run` starts RuneLite with `--developer-mode --debug`. Log in with a
Jagex account per
<https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>.

The client version is pinned in `build.gradle` rather than tracking
`latest.release`, so a local build is reproducible and the version the docs quote
is the version actually compiled against. The Plugin Hub's packager substitutes
its own `build.gradle` for `build=standard` submissions, so the pin affects local
dev and test builds only.

### Testing discipline

**A guard is proven by breaking it.** Before a test is trusted to mean what it
claims, the thing it exists to catch is deliberately broken — the call deleted,
the condition flipped, the return hardcoded — and the test is confirmed to go
red. A test that stays green through the mutation it exists to catch is worse
than no test, because it reads as coverage that is not there.

Fifty-one mutations have been run against this tree. One survived, and it was a
real defect in the test rather than in the mutation:
`itTurnsToFaceThePlayerWhenItStops` walked the follower east and then asserted it
was facing east — which is what it was already facing from its last step, so
deleting the turn-to-face entirely left the test green. It only became a test
once the anchor moved *past* the follower, so that "the way it was walking" and
"the way the player is" are different answers.

## License

BSD 2-Clause. See `LICENSE`.
