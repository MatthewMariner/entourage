<div align="center">

# Entourage

**You walk through Gielinor alone. This gives you one companion who walks it with you.**

One figure — Vannaka, Nieve, the Wise Old Man, a rogue, twenty more — that keeps
pace with you at a run and holds whatever pose you pick the moment you stop.
Client-side only: no packet is sent, and nothing about any other player is read.

[![RuneLite](https://img.shields.io/badge/RuneLite-1.12.38-blue)](https://runelite.net)
[![Java](https://img.shields.io/badge/Java-11-orange)](https://runelite.net)
[![License](https://img.shields.io/badge/license-BSD--2--Clause-green)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-215-brightgreen)](#development)

</div>

> [!NOTE]
> **Not on the Plugin Hub yet.** Build and run it yourself — see
> [Development](#development) below.

<!-- SCREENSHOT: the follower mid-stride a tile behind the player, ideally caught on a
     diagonal step so the facing reads clearly. Save as docs/img/follower.png and replace
     this comment with:  ![Follower mid-walk](docs/img/follower.png) -->

---

## What it does

One figure of your choosing walks with you and holds a pose the moment you stop,
rather than standing there as a static, sliding mesh.

- **Twenty-three figures to pick from.** Vannaka, Nieve, Steve, Turael, Duradel,
  Mazchna, the Wise Old Man, a White Knight, an Elite Black Knight, Sir Amik
  Varze, Sir Vyvin, Ghommal, Hans and a dozen more. Every one of them is built
  from the game's own cache and carries the stand and walk animations that NPC
  actually uses — so the ones holding a polearm or a walking stick move like it.
- **Keeps up when you run.** A running player covers two tiles a game tick; so
  does the follower, with a proper run animation rather than a walk cycle played
  over twice the ground.
- **Stands where you tell it.** Behind you, on your left or on your right, one
  tile away or two. "Behind" means behind the way you were last walking, so
  turning on the spot doesn't send it circling you.
- **Holds the pose you pick.** Its own stand, or dance, cheer, wave, clap, bow,
  shrug, flex, panic, sit, lean, arms crossed, smug or nervous — and it drops
  the pose the moment it starts walking.
- **Poses properly.** Idle, walking and running are three purpose-built animation
  controllers, not a re-used mesh with nothing driving it, and the client itself
  advances them — never at the wrong speed, never doubled.
- **Doesn't walk through walls.** Every step is checked against the game's own
  real collision data before it's taken — the same primitive the client itself
  uses — so a follower can't cut through a doorframe or a wall to reach you. A
  run is two steps, so it's two checks.
- **Works in instances too** — a Player Owned House, a raid, anywhere else the
  game hands out its own private copy of a normal area — because its position
  math is self-consistent rather than assuming it always stands on the overworld
  map.
- **Is entirely local.** No packet is sent, nobody else's client draws it, and
  nothing about any other player is read.

## Settings

| Setting | What it does | Default |
|---|---|---|
| **Figure** | Whose body the follower wears — 23 to choose from. Changing it rebuilds the follower on the next game tick. | Rogue |
| **Movement → Follow distance** | How many tiles away it stands: 1 or 2. Two gives it room and makes it more likely to get caught on a doorway, because it walks greedily towards its spot rather than pathfinding around obstacles. | 1 |
| **Movement → Stands** | Behind me, on my left, or on my right. Measured against the way you last walked, not the way you're facing. | Behind me |
| **Movement → Can run** | Lets it cover two tiles in a game tick when it has fallen behind. Turn it off if you'd rather it never moved faster than a walk — but then a running player outruns it. | On |
| **Movement → Recall at** | How far behind you it may get, in tiles (6–20), before it's put back on your tile instead of walking. This is what stops it being stranded behind a wall it would have to walk *away from* to get around. | 12 |
| **Pose → Idle pose** | What it holds while standing still: the figure's own, or one of thirteen looping emotes and stances. | The figure's own |

Only looping poses are offered, on purpose — a one-shot emote plays once and then
freezes on its last frame, which looks like a bug rather than a pose.

## Why

The brief was three words wide: *"group of people follow you or post up cool
anime style."* The first slice was one figure walking correctly, because a
working walk cycle and honest wall-awareness turned out to be the two hardest
problems in a follower plugin, not the easy part on the way to the interesting
one. This one is everything that hangs off it: who the figure is, where it
stands, what it does while it waits, and a run so that travelling with it is not
a recall every seven seconds. Every model comes out of the game's own cache;
nothing copyrighted or trademarked is imitated, named or reproduced.

The technique is inherited from [`../lively-cities`](../lively-cities), the same
author's cosmetic-humanoid plugin live on the Plugin Hub, which is where the
collision and animation groundwork below comes from.

## Known limitations

- **This is greedy stepping, not pathfinding.** A follower tries the diagonal
  toward its spot, then each straight-line component; if none is legal it stands
  still, and it can get stuck on the wrong side of a wall it would need to walk
  *away from* to get around. The recall distance is what stops that being
  permanent. Setting the follow distance to two makes it a little more likely,
  because a spot two tiles out is more often on the far side of something.
- **A recall is still a pop, not an entrance.** The follower appears on your tile
  and steps off it the next tick. It happens far less than it used to — a
  follower that can run is not outrun by a running player — but a proper entrance
  effect is later work.
- **One figure.** Formation shapes for a second and third are a later slice; the
  slot arithmetic that would place them already exists.
- **It cannot wear your own outfit.** The game exposes no single call that turns
  a player's equipped items into a buildable model array the way it does for
  NPCs, so cloning your own appearance means reproducing that resolution by
  hand — a cache decoder, and the next slice.
- **You cannot mix a pose with walking.** The pose is dropped the moment the
  follower moves. The client does have a second animation slot, but it is never
  advanced for a plugin-drawn object, so a walk parked in it would freeze on its
  first frame — which is worse than not offering it.
- **No menu entries at all — you cannot right-click it.** Doing this safely
  needs deprioritised, clearly-labelled entries the way `../lively-cities`
  handles its own citizens, and that hasn't been built yet.
- **Its frame cost hasn't been measured.** With one mostly-moving figure there is
  nothing to compare against `../lively-cities`' own numbers, which describe a
  mostly-*idle* crowd instead — before the roster grows, a moving figure's cost
  needs its own measurement.

## Found a bug?

Please open an issue on GitHub. The most useful report says where you were
standing, what you expected the follower to do, and whether it was walking or
holding a pose at the time — nearly everything here is positional or timing, so
that context is usually decisive on its own.

If it's a crash or a figure that never appears, `~/.runelite/logs/client.log`
filtered to `entourage` usually shows why, and pasting it saves a round trip.

---

## Development

```bash
./gradlew build     # compile and run the 215 tests
./gradlew test      # tests only, every name printed
./gradlew run       # launch a dev client with the plugin loaded
```

`./gradlew run` starts RuneLite with `--developer-mode --debug`. Log in with a
Jagex account per RuneLite's own
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide.

The client version is pinned in `build.gradle` (1.12.38) rather than tracking
`latest.release`, so a local build is reproducible and the version this doc
quotes is the version actually compiled against. The Plugin Hub's packager
substitutes its own `build.gradle` for `build=standard` submissions, so the pin
affects local dev and test builds only.

**Filing the Hub submission?** Swap the "not on the Plugin Hub yet" callout above
for the standard install instructions in the same change — that line stops being
true the moment this is listed.

### Testing discipline

Every guard here is proven by deliberately breaking the thing it claims to catch
and confirming the test goes red first. Five mutation passes have been run so
far: an initial 51 mutations (one survivor, a real test gap, since fixed); an
independent second pass of 117 mutations against 134 tests, where 28 survived
and 6 were real defects — among them a stationary-to-walking transition no test
had ever exercised, and a square test fixture whose geometry hid six
axis-mix-up bugs until a second, rectangular fixture could tell the axes apart;
a follow-up pass of 26 mutations on those fixes and their neighbours, none of
which survived; a pass of 52 against the settings, the presets, the formation and
the run, where one survived — the check that stops a staircase being mistaken for
a direction of travel; and a pass of 31 against the guards that predate all of
that, to confirm no new constraint had quietly made an older one vacuous, which
turned up four more untested lines. All five gaps are covered now, and a
follow-up pass of 21 on the fixes and their neighbours left no survivors. The case-by-case detail lives in the test source alongside each
guard.

### Wanted from a real client

Nothing below can be settled without the game running, and none of it is guessed
at in the code — each is stated as an open question exactly where it lives:

1. **Does the run read as a run on the Tier B figures?** Every preset's stand and
   walk were read off the NPC that plays them. The *run* was not, because those
   NPCs never run — `AnimationID.HUMAN_RUNNING` is used for all of them. It sits
   on the same human framemap as their walks, so the geometry is sound; what is
   unverified is whether a figure holding a halberd running with its arms
   swinging free looks acceptable. `EntourageFigure`'s constructor is the one
   place to change if not.
2. Does the walk read as a walk — frame pacing, and whether the stride matches
   one tile per game tick?
3. How far ahead does the server tile actually run during a full sprint? The
   follower keeps station against the *drawn* player tile.
4. Does a follower that runs still get recalled in practice, and if so where?
5. Is one tile of following distance too close, and does two feel better or just
   further away?
6. Do the sitting and lying poses put the figure at the right height, or does the
   ground offset need a per-pose correction?

## License

BSD 2-Clause. See `LICENSE`.

---

<div align="center">
<sub>Cosmetic and local. Sends nothing to the server. Not affiliated with Jagex.</sub>
</div>
