<div align="center">

# Entourage

**You walk through Gielinor alone. This gives you up to five who walk it with you.**

Vannaka, Nieve, the Wise Old Man, a rogue, nineteen more — or any NPC id you care
to type, in any of the five slots, kept on a favourites list so you never have to
remember the number twice. They keep pace with you at a run, arrange themselves in
a formation you pick, hold a pose the moment you stop, and say something over their
heads now and again — or park where they stand and wait for you. Client-side only:
no packet is sent, and nothing about any other player is read.

[![RuneLite](https://img.shields.io/badge/RuneLite-1.12.38-blue)](https://runelite.net)
[![Java](https://img.shields.io/badge/Java-11-orange)](https://runelite.net)
[![License](https://img.shields.io/badge/license-BSD--2--Clause-green)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-659-brightgreen)](#development)

</div>

> [!NOTE]
> **Not on the Plugin Hub yet.** Build and run it yourself — see
> [Development](#development) below.

![Five followers walking behind the player across a stone platform — a Zamorak
mage, a farmer and three Elite Black Knights — each with its name above it, one
of the knights saying "We are watching."](docs/img/hero.png)

---

## What it does

Up to five figures of your choosing walk with you in formation and hold a pose
the moment you stop, rather than standing there as static, sliding meshes.

- **Twenty-three figures to pick from.** Vannaka, Nieve, Steve, Turael, Duradel,
  Mazchna, the Wise Old Man, a White Knight, an Elite Black Knight, Sir Amik
  Varze, Sir Vyvin, Ghommal, Hans and a dozen more. Every one of them is built
  from the game's own cache and carries the stand and walk animations that NPC
  actually uses — so the ones holding a polearm or a walking stick move like it.
- **Or type an NPC id and get that NPC, in any of the five slots.** Any of them,
  not just the twenty-three — the plugin reads that NPC's own stand and walk
  animations straight out of the game's cache, so it moves the way it moves in game
  rather than sliding. Each slot has its own box, so five typed ids is a legal
  roster and so is one typed id and four presets. An id that has no usable pair is
  refused rather than shipped broken; see *Custom NPC ids* below for what that
  looks like.
- **Star the ids you like and they stay on a list.** Find a body you want to keep
  and press the star; it is on the favourites list from then on, by name rather
  than by number, and one press puts it in whichever slot you are looking at.
  Twenty of them, newest first. See *Favourites* below.
- **Or tell them to stay put.** The sidebar has a "Stay put" chip beside a "Follow
  me" one — press the first and the whole entourage parks on the tiles it is
  standing on, along a wall at a boss, out of the way in a bank — and they are
  **not** recalled to you however far you go. They still turn to watch you, hold
  their pose and say things. Press **Follow me** and they walk back.
- **Keeps up when you run.** A running player covers two tiles a game tick; so
  does the follower, with a proper run animation rather than a walk cycle played
  over twice the ground.
- **Stands where you tell it.** Behind you, ahead of you, on your left or on your
  right, one tile away or two. "Behind" means behind the way you were last
  walking, so turning on the spot doesn't send it circling you.
- **Points the way you want.** At you, the same way you're facing — so it stands
  alongside and looks where you look — or a fixed compass direction. While it's
  walking it faces the way it's walking, whatever you picked.
- **Says things.** Three to seven lines per figure, most of them the NPC's own
  words off the wiki, one at a time, roughly once a minute in a colour and font
  you pick. Or type your own list into the settings box. Or turn it off.
- **Holds the pose you pick.** Its own stand, or dance, cheer, wave, clap, bow,
  shrug, flex, panic, sit, lean, arms crossed, smug or nervous — and it drops
  the pose the moment it starts walking.
- **Doesn't walk through walls.** Every step is checked against the game's own
  real collision data before it's taken — the same primitive the client uses — so
  it can't cut through a doorframe to reach you. A run is two steps, so it's two
  checks. Idle, walking and running are three purpose-built animation
  controllers, and the client itself advances them, so nothing plays doubled.
- **Has a panel to pick them in.** The button in the sidebar opens five cards
  saying who walks with you, with a search over the twenty-three that forgives a
  mistyped letter, an add and a remove that treat the roster as the list it is, an
  id box and a favourites list in every slot's picker, and the dials worth changing
  while you are looking at them — including the follow/stay-put switch, which is
  the one you want mid-fight. Nearly everything it writes is a setting the
  plugin's own settings screen shows too, so the two agree — the one exception is
  the star, which writes a hidden setting the settings screen does not show at
  all (see *Favourites* below).
- **Works in instances too** — a Player Owned House, a raid, anywhere the game
  hands out its own private copy of an area — because its position math is
  self-consistent. There's a switch to hide it in them if you'd rather.
- **Is entirely local.** No packet is sent, nobody else's client draws it, and
  nothing about any other player is read.

![Two copies of Nieve flanking the player, one either side, walking in step —
both mid-stride with a spear in hand, each with its name above
it.](docs/img/formation.png)

<sup>Both are carrying Nieve's own walk cycle, spear and shield and all — not a
generic stride played underneath a different model.</sup>

## Settings

<img src="docs/img/settings.png" alt="The Entourage settings panel, showing the Roster,
Movement, Pose and Dialogue sections" align="right" width="230">

**Most of the Roster section has a nicer front door.** The Entourage button in
RuneLite's sidebar opens a roster panel — five cards, a search, an add and a
remove, an id box per slot and a favourites list — which writes these same settings
and nothing else. See [The side panel](#the-side-panel) below. Everything in the
table is still here, still works, and still says what it always said.

One setting is deliberately *not* in the table: the favourites list is stored in a
hidden config item (`favouriteNpcIds`), because nobody keeps a favourite by typing a
comma-separated list of integers into a text box. The star on a card is the whole
interface; the key is only where it's written down.

| Setting | What it does | Default |
|---|---|---|
| **Roster → Followers** | How many figures walk with you, 1 to 5. The figure dropdowns past this number are ignored; RuneLite has no way to blank one, so "how many" and "who" are separate questions. | 1 |
| **Roster → Figure 1**…**5** | Whose body each follower wears — 23 to choose from, each carrying that NPC's own stand and walk. Changing one rebuilds the entourage on the next game tick. | Rogue, Thief, Sorceress, Hero, Necromancer |
| **Roster → Custom NPC id 1**…**5** | Puts *any* NPC in that slot instead of what the "Figure" dropdown beside it says. Zero means "use the dropdown". Non-human bodies are allowed and may look odd; ids that can't animate are refused. Each box sits directly under the dropdown it overrides. See below. | 0 |
| **Hide in instances** | Takes it off the screen anywhere the game hands out its own private copy of an area: a raid, a quest cutscene, the Inferno. Off by default, because a Player Owned House is an instance too and that's where you'd most want to show a follower off. | Off |
| **Movement → Stay put** | Parks the entourage on the tiles it's standing on instead of following you. **The recall is switched off with it** — they are not put back on your tile however far away you go, which is the whole point. They still turn to watch you, hold their pose and talk. Also the first control in the sidebar panel, which is where you'll actually press it. | Off |
| **Movement → Follow distance** | How many tiles away it stands: 1 or 2. Two gives it room and makes it more likely to get caught on a doorway, because it walks greedily towards its spot rather than pathfinding around obstacles. | 1 |
| **Movement → Formation** | The shape the entourage stands in — seven of them: Behind me, Ahead of me, On my left, On my right, Hangout ring, Wedge behind, and Line abreast. The first four put everybody in a single file or rank in one direction; Hangout ring spreads them around you facing inward, Wedge behind trails them in a V, and Line abreast puts them in a row with you in the middle. Every shape is measured against the way you last walked, not the way you're facing — except "Ahead of me", where turning on the spot does send it walking round to the front again. | Behind me |
| **Movement → Faces** | Which way it points once it's stopped: at you, the same way you're facing, or one of the eight compass directions. While it's walking it faces the way it's walking. | At me |
| **Movement → Can run** | Lets it cover two tiles in a game tick when it has fallen behind. Turn it off if you'd rather it never moved faster than a walk — but then a running player outruns it. | On |
| **Movement → Recall at** | How far behind you it may get, in tiles (8–20), before it's put back on your tile instead of walking. This is what stops it being stranded behind a wall it would have to walk *away from* to get around. Ignored entirely while **Stay put** is on. | 12 |
| **Pose → Idle pose** | What it holds while standing still: the figure's own, or one of thirteen looping emotes and stances. | The figure's own |
| **Dialogue → Overhead lines** | Whether it says anything at all. Turning it off empties the screen on the click rather than when the line up there runs out. | On |
| **Dialogue → Custom lines** | Your own lines, comma-separated. **These replace the figure's own** — empty the box to get them back. See below on commas. | *(empty)* |
| **Dialogue → Name above head** | Draws the figure's name above it, in the same colour and font. When it's talking, the line sits above the name. | Off |
| **Dialogue → Colour** | Magenta, violet, pink, blue, green or red. Deliberately a short list: every one of them is a colour the game never uses for a real menu target or for a player's own chat, which is what stops a follower's line being mistaken for somebody talking. | Magenta |
| **Dialogue → Font** | The game's own three faces: small, regular, or large (the bold one). | Regular |
| **Dialogue → Speaks every** | How often it says something, in game ticks — 100 is a minute, 10 is six seconds. It only ever speaks when it's on screen with nothing already up. | 100 |
| **Dialogue → Stays up for** | How long a line stays on screen, in game ticks — 8 is just under five seconds. Always shortened to less than "Speaks every", because a line that outlasts the gap between lines never clears. | 8 |

Only looping poses are offered, on purpose — a one-shot emote plays once and then
freezes on its last frame, which looks like a bug rather than a pose.

### The side panel

**The button in RuneLite's sidebar is the roster, as a roster.** The table above
is fifteen controls in a list; the panel is the five that answer "who walks with
me", laid out as five cards you can press.

- **Five slot cards.** Each says which slot it is, who is in it, and whether it is
  walking with you. Press one to change who is in it. **Slots past the follower
  count are greyed rather than hidden** — they keep the figure they name, because
  the count and the roster are separate questions and a card that vanished would
  take a setting off screen with it.
- **A search over the twenty-three.** Type part of a name; a word from the middle
  works, and so does a name off by a letter — "vanaka" finds Vannaka and "duradal"
  finds Duradel. Exact names come first, then names that start with what you typed,
  then names that contain it, then the near misses.
- **Add and remove that behave like a list.** The dashed card at the bottom adds a
  follower. The **×** on a card takes that one out and moves everybody behind it up
  a slot — which is what removing the third of five means and what five dropdowns
  cannot do in one gesture. The last follower has no **×** at all, because a roster
  of nobody is the plugin's own on/off switch in the plugin list.
- **The typed NPC id, as a card — in every slot's picker.** It is at the top, with
  the three things a bare number box has nowhere to say: that an id which cannot
  walk is refused, that the figure below is what you get when it is, and that
  emptying the box gives the dropdown back. Type a number, press **Use**, and that
  slot wears that NPC.
- **A star on the id card, and the favourites list under it.** Press the hollow star
  and the id you are wearing is kept; press the filled one and it isn't. The list
  below shows every kept id — by the NPC's own name once the client has told the
  panel what it is, and as **NPC 3598** until then, which is the number you'd type
  anyway. Press a row to put that id in the slot you're picking for; press its **×**
  to stop keeping it. The same list is in all five pickers, because it isn't a
  property of a slot.
- **Quick settings.** The follow/stay-put switch first, then followers, formation
  and follow distance, behind a heading that folds. The switch is first because it
  is the one control here you press in the middle of something. Deliberately a short
  list — everything else is set once and left, and a panel that duplicated the
  settings screen would be a second settings screen with less room.
- **Text at the client's normal size.** The panel used to be drawn in RuneScape
  Small throughout, which read as fine print next to the rest of the client. Body
  text, headings, row labels and the favourites are the normal face now; the one
  thing still small is the "in this slot" mark, which is an annotation on the name
  beside it rather than a line of its own.

**The settings screen stays the source of truth.** Every control on the panel
writes an ordinary setting through RuneLite's own profile mechanism, so a change
made in one shows up in the other, a profile switch is picked up by both, and
nothing here is stored anywhere else.

### Custom NPC ids

Type a number into **Roster → Custom NPC id 2** and the second follower wears that
NPC instead of whichever preset the "Figure 2" dropdown names. Zero — where every
one of them starts — means "use the dropdown". There is one box per slot, so five
typed ids is a legal roster, and so is one typed id and four presets.

**The first slot's key is spelled differently, on purpose.** RuneLite writes config
keys into your profile, and renaming one silently resets that setting for everybody
who had it. When the typed id applied to slot 1 alone it was called `customNpcId`,
with no number, and that is what is sitting in the profile of everyone who has ever
used it. So the four new keys are numbered from *two* — `customNpcId2` through
`customNpcId5` — and the first keeps the name it always had. The settings screen
labels it "Custom NPC id 1" like the others; only the stored key is asymmetric. The
figure dropdowns carry the same scar for the same reason: `figure`, then `figure2`
through `figure5`.

**Why it can refuse you.** The twenty-three presets each carry a hand-verified
pair of animations, because the client's NPC data hands over models and colours
and no animation ids at all. An arbitrary id has no such pair, so the plugin
reads that NPC's record out of the game cache itself and takes its stand, its
walk and — if it has one — its run. Plenty of NPCs have nothing usable there:
Spria declares no animations at all, Krystilia's stand and walk are the *same*
id. A follower given either would slide along the ground, which is the single
most visible way this plugin can look broken, so it doesn't ship one.

**What a refused id looks like.** The follower is that slot's own dropdown
figure — so if you type an id into slot 1 and the Rogue is still standing there, the
id was turned down. There is a line in the client log saying which id and why. The three
ways to get one: no such NPC in the cache, a record the plugin can't read, and a
record whose animations are missing, half-missing or identical.

**What an id that isn't refused still can't do.** Anything non-human renders at
its default size on a one-tile footprint — a big NPC won't be big, and won't
reserve the tiles it should — and its animations sit on whatever skeleton the
cache gives it, which this plugin cannot check without the game running. Only a
live client can tell you whether a particular body looks right walking. If
**Name above head** is on, a working id draws that NPC's own name and a refused
one draws the dropdown figure's, which is the quickest way to tell them apart.

**If nothing appears at all**, the id exists and animates but its models won't
build — an NPC with no models of its own. Clear the box.

**Picking a preset for a slot in the panel clears that slot's typed id**, because
the id overrides that dropdown — without it the card would change, the setting would
change, and the figure on screen would not. Taking a follower out of the roster with
the **×** moves the ids up with the figures, so removing the second of three leaves
the third wearing its own id rather than the one you just removed.

### Favourites

Type `3598`, decide you like what turns up, and press the **☆** on the card. It's
kept from then on, and the favourites list under the id box shows it — by the NPC's
own name, which the panel asks the game client for, rather than by a number you'd
have to write down. Press a row to put that id in the slot you're picking for.

**Twenty of them, newest first.** Under the hood, starring an id already on the list
moves it back to the front rather than duplicating it — though there is no way to
actually trigger that from this panel: an already-starred row's star is always filled
in, and pressing a filled star un-stars it rather than starring it again. At twenty
the list is full and the star says so instead of quietly dropping the oldest one you
saved.

**They're stored as ids, not names, and that is deliberate.** A list in one config
value needs a delimiter, and a delimiter is only safe if the values can't contain it —
which is exactly the problem the custom-lines box has, where a comma always starts a
new line and a line of your own therefore can't contain one. NPC names are full of
commas ("Guard, Falador"). Ids aren't, so the collision is impossible rather than
merely unlikely, and the name is asked of the game rather than stored alongside the
number, so it can never go stale.

**A name that hasn't arrived yet is drawn as the id.** Turning 3598 into "Gummy"
goes through the game client, which throws if it's read from any thread but its own —
and a side panel is not drawn on that thread. So the panel asks on the client thread
and takes the answer back to Swing, and until it lands the row says **NPC 3598**,
which is true. At the login screen nothing resolves and every row is a number; open
the panel again once you're in and they fill in.

**A hand-edited list can't break anything.** The stored value is coerced piece by
piece: a blank entry, a stray comma, whitespace, a negative, a zero, a duplicate, a
name somebody pasted in, or a number too big for an `int` each cost that one entry
and leave the rest of the list alone. A list longer than twenty is not one more
entry lost the same way — it's everything past the twentieth, cut to the cap.

### Stay put

**Movement → Stay put**, or the first control in the sidebar panel, parks the whole
entourage on the tiles it's standing on. It's for parking them along a wall at a boss,
or anywhere a group underfoot is a nuisance.

**The recall goes off with the walking, and that's the feature rather than an
oversight.** *Recall at* exists to rescue a follower stranded behind a wall by putting
it back on your tile, and it fires at twelve tiles by default — so a freeze that left
it running would mean the whole point of parking them is that they teleport into the
fight with you. While **Stay put** is on, they are never recalled, at any distance.

**What still works:** they turn to watch you if *Faces* is "At me", they hold their
idle pose, and they still say things. Freezing mid-stride finishes the tile they were
walking to and settles into the pose — no half-played walk cycle and nothing sliding.

**Turning it off resumes the ordinary follow**, recall included: if you've walked a
long way, a recall is exactly the right thing to happen and it happens on the next
tick.

**Walk out of the region and they stay where you left them.** The tile is a world
coordinate, so it survives the game loading a new scene around you. If the tile isn't
in the loaded map any more they are simply **not drawn** — never moved somewhere else,
never pulled to the edge of what's loaded — and they reappear the moment it's loaded
again.

**Crossing into an instance re-parks them on you.** A raid or a quest cutscene is a
private copy of an area built out of template chunks, so a tile recorded outside one
doesn't name the same place inside it. Rather than risk a follower appearing in a
corner of a raid nobody put it in, the parked tile is dropped at that boundary and
they re-park where you're standing — still parked, just here rather than there.
(What this does **not** catch is one instance handing you to another — the
Gauntlet room to room, a Chambers of Xeric chamber to chamber — because the check
is "is this an instance", not "is this the *same* one", and both answer yes to the
first. See *Known limitations*.)

**Changing who's in the roster doesn't un-park anybody any more.** Swapping a
figure, typing an id over one, or removing a slot with the **×** all keep every
surviving slot's own pin — matched against what actually changed (a swap, an
add, or a slot removed with the tail shifted up to close the gap), and only
falling back to the raw slot number when an edit's shape cannot be told apart
from another. So a removal slides the survivors up into the same tiles rather
than teleporting the whole group onto you. The one gap: a slot that didn't
exist a moment ago was never parked anywhere, so raising the count parks the
new one wherever it spawns, the
same as a fresh install's first tick.

**Logging out changes nothing, for the rest of that session.** **Stay put** is an
ordinary setting, so it's still on when you come back, and the tile is remembered
too — log back in where you logged out, in the same running client, and they're
standing on it. The tile itself is never written to disk, though: restart the
client, or disable and re-enable the plugin, and the pin is gone along with
everything else this session held in memory — they form up on you again the next
time they're drawn, the same as any other roster rebuild with nothing to carry
over. Log in somewhere else, or teleport away, and they're not drawn until you go
back or turn the setting off, which is the same rule as walking out of the
region.

### About the lines

**Most of them are the NPC's own words, and the rest are ours — here's which.**
Eleven figures have dialogue recorded on the [Old School RuneScape
Wiki](https://oldschool.runescape.wiki), and their lines are quoted from it
exactly: **Vannaka, Nieve, Steve, Turael, Duradel, Mazchna, the Wise Old Man,
Hans, Sir Amik Varze, Sir Vyvin and Ghommal**. The other twelve — the rogue, the
thief, the farmer, the knights, the mages — are generic bodies out of the cache
with no recorded speech at all, so **their lines were written for this plugin**.
Nothing here is a paraphrase presented as a quote.

![Three followers spread along a stone path — a sorceress sitting down on the
verge, Hans standing behind the player, and a thief saying "Look
busy."](docs/img/hangout.png)

<sup>Hans is one of the eleven whose lines come off the wiki; the thief is one of
the twelve whose don't. The sorceress is sitting because that's the idle pose she
was given, and she'll stand the moment the group moves off.</sup>

Two notes on the quoted ones. The six slayer masters share a script in game —
*"'Ello, and what are you after then?"* and two more are word for word the same
on all of them, and Nieve, Duradel and Mazchna have almost nothing else short
enough to draw over a head. And Sir Amik's lines come from the quests he speaks
in, because his own page is flagged incomplete.

**Commas** split the custom-lines box, with no escape — a line of your own can't
contain one, so use a dash. (The shipped lines can, because they aren't typed in
there.) Lines are trimmed, blanks dropped, anything past 60 characters cut, and
the first twelve used. **Only one line is ever on screen**, however many figures
walk with you later: a companion that never shuts up is worse than one that says
nothing.

## Why

The brief was three words wide: *"group of people follow you or post up cool
anime style."* The first slice was one figure walking correctly, because a
working walk cycle and honest wall-awareness turned out to be the two hardest
problems in a follower plugin. Everything since hangs off it: who the figure is,
where it stands, which way it points, and now what it says. Every model comes out
of the game's own cache; nothing copyrighted or trademarked is imitated, named or
reproduced. The technique is inherited from
[`../lively-cities`](../lively-cities), the same author's cosmetic-humanoid
plugin live on the Plugin Hub.

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
- **Five is the ceiling**, and only one line is ever on screen however many walk
  with you — a group that all talk at once is noise rather than company.
- **"Ahead of me" circles you when you turn on the spot.** The slot is a tile
  along the way you last walked, so turning round leaves the follower behind you
  and it walks back to the front. That is the honest consequence of asking for a
  figure in front; the other three slots don't do it.
- **The lines don't know what you're doing.** They're a rotation on a timer, not
  a reaction — nothing here reads your skills, your location or your quest state,
  and nothing ever will read anything about anybody else.
- **It cannot wear your own outfit.** The game exposes no single call that turns
  a player's equipped items into a buildable model array the way it does for
  NPCs. There *is* a cache decoder in here now, and it is deliberately no help:
  it reads four animation ids out of one NPC record and knows nothing about
  items.
- **You cannot mix a pose with walking**, and you cannot right-click the figure
  at all. The client's second animation slot is never advanced for a plugin-drawn
  object, so a walk parked in it would freeze on its first frame; and a menu entry
  needs the deprioritised, clearly-labelled treatment `../lively-cities` gives its
  citizens, which hasn't been built here yet.
- **A typed NPC id is not checked for whether it looks good**, only for whether
  it can be made to walk. Size, footprint and skeleton are all left at the
  defaults every preset uses, which are right for a human and are not right for a
  dragon. See *Custom NPC ids* above.
- **Its frame cost hasn't been measured.** With one mostly-moving figure there is
  nothing to compare against `../lively-cities`' numbers, which describe a
  mostly-*idle* crowd instead.
- **A typed NPC id reads as a number until the client answers.** Turning 4931 into
  a name goes through the game client, and the client throws when it is read from
  any thread but its own — which is not the thread a Swing panel is drawn on. The
  panel asks on the right thread and takes the answer back, so a name does arrive;
  what it will not do is block waiting for one. At the login screen, or on a cold
  cache, every id is drawn as "NPC 4931", which is true.
- **A parked entourage crossing into or out of an instance is parked on you, not
  where you left them.** An instance is a private copy of an area, so a tile
  recorded outside one doesn't address the same place inside it. Crossing that
  boundary drops the parked tile and re-parks them where you're standing. Parking
  them inside an instance and staying in that same instance works normally.
- **One instance handing you to another is not detected as a boundary at all.**
  The check is "is the new scene an instance", not "is it the *same* instance",
  so the Gauntlet passing you from one room to the next, or a Chambers of Xeric
  raid from one chamber to the next, both answer yes to the first question and
  the parked tile is honoured — with coordinates recorded in the *previous*
  room's space. Comparing the scene's own coordinates instead of its
  instance-ness was considered and rejected: an ordinary region load moves those
  coordinates too, which would re-park the group on every region boundary you
  ever crossed rather than only at the one that matters.
- **Favourites are per profile, like every other setting here.** They ride
  RuneLite's own profile mechanism, so switching profile switches the list, and
  nothing is written to disk by this plugin.
- **The panel shows no preview of a figure**, only its name. Drawing a model into a
  side panel means rendering one outside the game's own scene, which is a different
  piece of work from anything this plugin does now.
- **Removing a follower can make the group flicker as it re-forms.** Taking one out
  with the **×** writes several settings in a row rather than one, and on rare
  occasions the game reads the roster mid-write and rebuilds the group against a
  shape that was never what you asked for — a follower that briefly shows the wrong
  body, say. It always corrects itself well within a second, once every setting has
  been written.

## Found a bug?

Please open an issue on GitHub. The most useful report says where you were
standing, what you expected the follower to do, and whether it was walking or
holding a pose at the time. If it's a crash or a figure that never appears,
`~/.runelite/logs/client.log` filtered to `entourage` usually shows why.

---

## Development

```bash
./gradlew build     # compile and run the 659 tests
./gradlew test      # tests only, every name printed
./gradlew run       # launch a dev client with the plugin loaded
```

`./gradlew run` starts RuneLite with `--developer-mode --debug`. Log in with a
Jagex account per RuneLite's own
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide.

The client version is pinned in `build.gradle` (1.12.38) rather than tracking
`latest.release`, so a local build is reproducible. The Plugin Hub's packager
substitutes its own, so the pin affects local builds only. **Filing the Hub
submission?** Swap the "not on the Plugin Hub yet" callout above for install
instructions in the same change.

### Testing discipline

Every guard here is proven by deliberately breaking the thing it claims to catch
and confirming the test goes red first, with the mutation shown to have landed
rather than assumed. Eight passes so far — 51, 117, 26, 52, 31, 64, 80 and most
recently 12 — have turned up twenty-two real gaps, all covered now, including a
square test fixture whose geometry had been hiding six axis-mix-up bugs, and a
test that took its expected value from the very constant it was checking, so the
cap it was named for could be raised to 99 without it going red. The seventh pass
found five of its nine gaps in the same shape: **one rule written twice**, in two
places that each answered for the other, so that neither copy could be broken on
its own. Every one of those was collapsed to a single falsifiable copy rather than
left as coverage that was not there.

The ninth pass covered the five per-slot NPC ids, the favourites and the panel's
font. It found the same "one rule written twice" shape the seventh pass did, in the
place it does the most damage: "is this slot wearing a typed id?" was answered
independently by `RosterView` and by `EntourageSettings`, so the panel and the game
each had their own copy of the decision. Two copies at one slot was survivable; five
slots would have made it ten, and the day they diverged the panel would have named
one follower while the world drew another. It was collapsed to
`EntourageSettings.bodyAt` before the feature was built on top of it, and the suite
was re-run green on the refactor alone to prove the collapse changed nothing.

It also added a guard this repo did not have. `ContrastGuardTest` walks the built
component tree and computes real WCAG ratios; `TruncationGuardTest` now walks the
same tree, lays it out at the real 225-pixel width, and fails if any label asks for
more room than it is given — either because a sibling squeezed it, or because it runs
off the panel's own edge. It caught a live bug in the favourites card the first time
it ran, and it goes red for both of the mutations it exists to catch: widening the
card's wrap constant so it forgets the column the **×** takes, and dropping the wrap
from the slot subtitle, which is the exact truncation the wrap was added to fix.

The eighth pass covered the side panel and found the same shape again, twice:
deleting the search's ranking outright left **both** tests named for that ranking
green, because each of their queries matched exactly one figure and so had no order
to get wrong. They are written now against the queries where the ranking is the
only thing that can produce the answer — "kn" for a prefix that beats an earlier
substring, "dura" for a real match that beats an earlier near miss — and against a
whole result list rather than its first row, which is what pins the tie-break
nothing else would have noticed breaking. Two survivors remain that no test can
catch, both written up as equivalent mutants where they live. The case-by-case
detail sits in the test source beside each guard.

### Wanted from a real client

Nothing below can be settled without the game running, and none of it is guessed
at in the code — each is stated as an open question exactly where it lives:

1. **Does the run read as a run on the Tier B figures?** Every preset's stand and
   walk were read off the NPC that plays them; the *run* was not, because those
   NPCs never run. It sits on the same human framemap, so the geometry is sound —
   what is unverified is whether a figure holding a halberd running with its arms
   swinging free looks acceptable. `EntourageFigure`'s constructor is the one
   place to change if not.
2. Does the walk read as a walk — frame pacing, and one tile per game tick?
3. Does a follower that runs still get recalled in practice, and if so where?
4. Is one tile of following distance too close, and does two feel better?
5. Do the sitting and lying poses put the figure at the right height?
6. **Are the three fonts legible over a head at a normal zoom**, and is the text
   height right for a human-scale figure? Both are judgements, and the font
   mapping is the one line of this plugin no test exercises.
7. **Does the cache decoder read a real record?** Every byte width in it is
   transcribed from `runelite-cache`'s own `NpcLoader` and pinned by tests
   against records this repo writes itself, which proves the widths agree with
   that loader and not that the client hands back what that loader expects. One
   working typed id settles it.
8. **What does an arbitrary NPC actually look like following you?** Non-human
   bodies render at human scale on a one-tile footprint, and their animations sit
   on whatever skeleton the cache gives them. Nothing here can judge either.
9. **Does the side panel look right in the sidebar?** Every colour is
   `ColorScheme`'s and every font is `FontManager`'s, and both are exercised
   headless — which proves they were asked for and proves nothing about how five
   cards and a folded section read at 225 pixels wide, or whether the dashed add
   card is legible against RuneLite's own theme. `EntourageRosterPanel` is the one
   place to change if not.
10. **Does the panel's own icon read at toolbar size?** It is three figures in a
    wedge, drawn at 24×24 in the client's brand orange. It has been eyeballed at
    ten times that and nowhere else.
11. **Does a change made in RuneLite's settings screen redraw an open panel?** The
    wiring is asserted — a `ConfigChanged` in this plugin's group refreshes and
    somebody else's does not — but the event only really arrives with a client
    running.
12. **Does a favourite's name actually arrive, and how quickly?** The lookup is
    asserted against a fake — asked for on one thread, delivered on the other,
    once per id — but only a live client can say whether the row fills in fast
    enough to read as "loading" rather than as "broken", and what a name that never
    resolves looks like sitting there as a number. `EntourageRosterPanel`'s
    `retryUnresolvedNames` is the one place to change if it needs another attempt.
13. **Do the ★ and ☆ glyphs render?** They are drawn in the client's default bold
    face rather than the game's bitmap font, which is the same reason the **×**
    already is. Checked once by eye, on the machine this was written on, where the
    substituted font draws both without issue — nothing in this repo asserts it,
    since which face a JVM substitutes for a missing glyph is a runtime answer no
    test here can pin. A JVM that substituted a font without them would draw two
    empty boxes.
14. **Is the normal font the right size in the sidebar?** Every wrap width is now
    derived from the space that exists and is held to it by a test, so nothing can
    silently ellipsise — but "it fits" is not "it reads well at 225 pixels", and
    five cards plus a favourites list is a taller panel than it was.
15. **Does a parked entourage stay put across a real region load?** The tile is a
    world coordinate and the arithmetic is tested against a fake scene, but only a
    live client walks far enough to load a new one — and only a live client can say
    whether "not drawn because the tile isn't loaded" reads as deliberate.

## License

BSD 2-Clause. See `LICENSE`.

---

<div align="center">
<sub>Cosmetic and local. Sends nothing to the server. Not affiliated with Jagex.</sub>
</div>
