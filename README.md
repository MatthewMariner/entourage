<div align="center">

# Entourage

**You walk through Gielinor alone. This gives you one companion who walks it with you.**

One rogue-shaped figure — not a group, for now — that walks with you and holds a
pose the moment you stop. Client-side only: no packet is sent, and nothing about
any other player is read.

[![RuneLite](https://img.shields.io/badge/RuneLite-1.12.38-blue)](https://runelite.net)
[![Java](https://img.shields.io/badge/Java-11-orange)](https://runelite.net)
[![License](https://img.shields.io/badge/license-BSD--2--Clause-green)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-151-brightgreen)](#development)

</div>

> [!NOTE]
> **Not on the Plugin Hub yet.** Build and run it yourself — see
> [Development](#development) below.

<!-- SCREENSHOT: the follower mid-stride a tile behind the player, ideally caught on a
     diagonal step so the facing reads clearly. Save as docs/img/follower.png and replace
     this comment with:  ![Follower mid-walk](docs/img/follower.png) -->

---

## What it does

One figure — a rogue, today — walks a tile behind you and holds a pose the moment
you stop, rather than standing there as a static, sliding mesh.

- **Walks at your pace.** One tile per game tick, the same speed the game itself
  walks at, with its drawn position smoothly interpolated between tiles rather
  than snapping from one to the next.
- **Poses properly.** Idle and walking are two purpose-built animation
  controllers, not a re-used mesh with nothing driving it, and the client itself
  advances them — never at the wrong speed, never doubled.
- **Doesn't walk through walls.** Every step is checked against the game's own
  real collision data before it's taken — the same primitive the client itself
  uses — so a follower can't cut through a doorframe or a wall to reach you.
- **Works in instances too** — a Player Owned House, a raid, anywhere else the
  game hands out its own private copy of a normal area — because its position
  math is self-consistent rather than assuming it always stands on the overworld
  map.
- **Is entirely local.** No packet is sent, nobody else's client draws it, and
  nothing about any other player is read.

`EntourageFigure.DEFAULT_ROSTER` is one entry today. More figures, poses, or a
formation are later slices — see Known limitations.

## Settings

None yet. There is no configuration panel — nothing to toggle, tune or hide.
`EntourageFigure.DEFAULT_ROSTER` is the seam a settings surface (which figure,
how many, what formation) will eventually hang off, but none of that exists in
this slice.

## Why

The brief was three words wide: *"group of people follow you or post up cool
anime style."* What shipped is the first slice of that — one figure, walking
correctly — because a working walk cycle and honest wall-awareness turned out to
be the two hardest problems in a follower plugin, not the easy part on the way to
the interesting one. Every model comes out of the game's own cache; nothing
copyrighted or trademarked is imitated, named or reproduced.

The technique is inherited from [`../lively-cities`](../lively-cities), the same
author's cosmetic-humanoid plugin live on the Plugin Hub, which is where the
collision and animation groundwork below comes from.

## Known limitations

- **This is greedy stepping, not pathfinding.** A follower tries the diagonal
  toward you, then each straight-line component; if none is legal it stands
  still, and it can get stuck on the wrong side of a wall it would need to walk
  *away from* to get around. A recall at twelve tiles' distance is what stops
  that being permanent.
- **It cannot run, so a running player permanently outruns it.** A run is two
  tiles a game tick; the follower walks one, so the gap never stops growing. At
  the twelve-tile recall distance that is a recall every 7.2 seconds for as long
  as you keep running — no recall-distance tweak fixes this, since a bigger
  number just makes each absence longer. The real fix is giving the follower a
  run speed, a design change of its own.
- **A recall is a pop, not an entrance.** The follower appears on your tile and
  steps off it the next tick. Because of the limitation above, that is what
  travelling with it looks like most of the time, not an edge case — a proper
  entrance effect is later work.
- **One figure.** Formation shapes, pose variety and any settings surface are
  later slices; the roster field is where they'll attach.
- **No menu entries at all — you cannot right-click it.** Doing this safely
  needs deprioritised, clearly-labelled entries the way `../lively-cities`
  handles its own citizens, and that hasn't been built yet.
- **It cannot wear your own outfit.** The game exposes no single call that turns
  a player's equipped items into a buildable model array the way it does for
  NPCs, so cloning your own appearance means reproducing that resolution by
  hand — nobody has done that work yet.
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
./gradlew build     # compile and run the 151 tests
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
and confirming the test goes red first. Three mutation passes have been run so
far: an initial 51 mutations (one survivor, a real test gap, since fixed); an
independent second pass of 117 mutations against 134 tests, where 28 survived
and 6 were real defects — among them a stationary-to-walking transition no test
had ever exercised, and a square test fixture whose geometry hid six
axis-mix-up bugs until a second, rectangular fixture could tell the axes apart;
and a follow-up pass of 26 mutations on those fixes and their neighbours, none
of which survived. The case-by-case detail lives in the test source alongside
each guard.

### Wanted from a real client

Nothing below can be settled without the game's own cache, and none of it is
guessed at in the code — each is stated as an open question exactly where it
lives:

1. **Does `NpcID.ROGUE` (526) walk on a human rig?** It's proven, live on the
   Hub, to render as a human *pose* — nothing has ever made it walk. If it turns
   out wrong, `EntourageFigure.FARMER` is a field-proven fallback: `../lively-cities`
   already walks that exact model.
2. Does the walk read as a walk — frame pacing, and whether the stride matches
   one tile per game tick?
3. How far ahead does the server tile actually run during a full sprint?
4. Does the recall look acceptable, or does reappearing on your tile every
   ~7 seconds while running read as a glitch worth fixing sooner?
5. Is one tile of following distance too close?

## License

BSD 2-Clause. See `LICENSE`.

---

<div align="center">
<sub>Cosmetic and local. Sends nothing to the server. Not affiliated with Jagex.</sub>
</div>
