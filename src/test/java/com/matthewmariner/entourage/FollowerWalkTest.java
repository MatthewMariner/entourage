package com.matthewmariner.entourage;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The movement system: a tile or two per game tick, interpolated per frame, and never
 * through a wall.
 *
 * <p>Everything here runs against a {@link FakeWorldView} — a scene rectangle and four
 * collision maps — with no client anywhere. That is the point of the split: the decision
 * half of the movement system is arithmetic, and arithmetic can be held to account.
 *
 * <p><b>A note on the fixtures.</b> A follower walks to one exact slot tile, and the
 * slot depends on the direction the player last travelled — which starts out as north,
 * because a player who has never moved has no direction of travel. Tests that want the
 * follower and its slot on the same row therefore use {@link FormationSlot#LEFT}, which
 * with a northward heading sits due west of the player: that keeps the geometry
 * collinear and one-dimensional, so an assertion about a step is about the step rather
 * than about the diagonal it happens to be on. Tests about the slot itself use
 * {@link FollowerWalk#stationTile} rather than restating the arithmetic
 * {@code FormationSlotTest} already pins.
 */
public class FollowerWalkTest
{
	private static final WorldPoint START = new WorldPoint(3221, 3218, 0);

	private static FakeWorldView scene()
	{
		return FakeWorldView.around(START);
	}

	private static EntourageSettings defaults()
	{
		return FakeConfig.defaults();
	}

	/**
	 * A slot due west of the player, so that a follower starting west of the player and
	 * the tile it is walking to share a row. See the class javadoc.
	 */
	private static EntourageSettings collinear()
	{
		return new FakeConfig().setFormationSlot(FormationSlot.LEFT).settings();
	}

	// --- One tile per game tick, or two -------------------------------------

	@Test
	public void aTickMovesAtMostOneTileWhenTheFollowerMayNotRun()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		EntourageSettings settings = new FakeConfig().setCanRun(false).settings();

		WorldPoint before = walk.currentTile();
		walk.tick(START.dx(6).dy(6), view, settings);

		assertEquals("a diagonal step is still one tile", 1, walk.currentTile().distanceTo(before));
		assertFalse(walk.isRunning());
	}

	@Test
	public void aTickMovesAtMostTwoTilesWhenItMay()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);

		WorldPoint before = walk.currentTile();
		walk.tick(START.dx(6).dy(6), view, defaults());

		assertEquals("two tiles a tick is a run, and a run is never three",
			FollowerWalk.RUN_STEPS_PER_TICK, walk.currentTile().distanceTo(before));
		assertTrue(walk.isRunning());
		assertTrue("a run is also a move", walk.isMoving());
	}

	@Test
	public void itClosesTheDistanceAndThenStopsOnItsSlot()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		EntourageSettings settings = defaults();
		WorldPoint anchor = START.dx(5);

		for (int i = 0; i < 20; i++)
		{
			walk.tick(anchor, view, settings);
		}

		assertEquals("it stops on its slot, not merely near the player",
			walk.stationTile(anchor, settings), walk.currentTile());
		assertFalse("and it stands still once it is there", walk.isMoving());
		assertNotEquals("which is never the player's own tile", anchor, walk.currentTile());
	}

	@Test
	public void itDoesNotMoveWhenItIsAlreadyOnItsSlot()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		WorldPoint anchor = START;

		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint slot = walk.stationTile(anchor, settings);
		walk.placeAt(slot);

		walk.tick(anchor, view, settings);

		assertEquals(slot, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	/**
	 * <b>The behaviour change a slot buys, stated as a test.</b> Under the old rule —
	 * "get within a tile of the player and stop" — a follower standing anywhere in the
	 * ring around the player was finished, so three of the eight tiles it could be on
	 * satisfied "behind me", "on my left" and "on my right" at once and the setting could
	 * not mean anything. A follower adjacent to the player but on the wrong tile now
	 * moves.
	 */
	@Test
	public void aFollowerAdjacentToThePlayerButOffItsSlotStillMovesOntoIt()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		WorldPoint anchor = START;

		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint slot = walk.stationTile(anchor, settings);

		// A neighbour of the player that is not the slot: the old rule called this done.
		WorldPoint wrongNeighbour = anchor.dx(1).dy(1);
		assertNotEquals("this test needs a tile that is adjacent but not the slot",
			slot, wrongNeighbour);
		assertEquals(1, wrongNeighbour.distanceTo(anchor));
		walk.placeAt(wrongNeighbour);

		walk.tick(anchor, view, settings);

		assertTrue("adjacency is no longer good enough", walk.isMoving());
		assertEquals(slot, walk.currentTile());
	}

	@Test
	public void theFollowDistanceSettingIsHowFarOutItStops()
	{
		for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
			distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
		{
			FakeWorldView view = scene();
			EntourageSettings settings = new FakeConfig().setFollowDistance(distance).settings();
			FollowerWalk walk = new FollowerWalk(START);
			WorldPoint anchor = START.dx(6);

			for (int i = 0; i < 20; i++)
			{
				walk.tick(anchor, view, settings);
			}

			assertFalse("distance " + distance, walk.isMoving());
			assertEquals("a follow distance of " + distance + " stops it " + distance + " out",
				distance, walk.currentTile().distanceTo(anchor));
		}
	}

	/**
	 * The three slots put the follower on three different tiles for the same journey,
	 * which is the whole of what the setting promises. Asserted against the tiles rather
	 * than against {@code stationTile}, so a slot that silently agreed with another one
	 * cannot pass by agreeing with itself.
	 */
	@Test
	public void theFormationSettingDecidesWhichSideItWalksOn()
	{
		Set<WorldPoint> restingPlaces = new LinkedHashSet<>();

		for (FormationSlot slot : FormationSlot.values())
		{
			FakeWorldView view = scene();
			EntourageSettings settings = new FakeConfig().setFormationSlot(slot).settings();
			FollowerWalk walk = new FollowerWalk(START);
			WorldPoint anchor = START.dx(5);

			for (int i = 0; i < 20; i++)
			{
				walk.tick(anchor, view, settings);
			}

			assertFalse(slot.name(), walk.isMoving());
			assertTrue(slot.name() + " settled where another slot did",
				restingPlaces.add(walk.currentTile()));
		}

		assertEquals(FormationSlot.values().length, restingPlaces.size());
	}

	// --- The heading, which is what "behind" is measured against -------------

	/**
	 * <b>Direction of travel, not direction of facing.</b> The player's facing was
	 * available and was deliberately not used: a player who turns on the spot would send
	 * a facing-based entourage walking a circle around them, once per click. This pins
	 * that the heading comes from two consecutive anchors.
	 */
	@Test
	public void theHeadingIsThePlayersLastStepAndTheSlotFollowsIt()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(START);

		assertEquals("a player who has never moved is treated as heading north", 0, walk.getHeadingX());
		assertEquals(1, walk.getHeadingY());

		walk.tick(START, view, settings);
		walk.tick(START.dx(1), view, settings);

		assertEquals("the player stepped east", 1, walk.getHeadingX());
		assertEquals(0, walk.getHeadingY());
		assertEquals("so behind them is now west",
			START.dx(1).dx(-1), walk.stationTile(START.dx(1), settings));
	}

	@Test
	public void aPlayerStandingStillKeepsTheHeadingTheyArrivedWith()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START, view, settings);
		walk.tick(START.dy(1), view, settings);
		assertEquals("this test needs a heading that is not the initial one", -1 + 1, walk.getHeadingX());
		assertEquals(1, walk.getHeadingY());

		WorldPoint stationary = START.dy(1);
		for (int i = 0; i < 30; i++)
		{
			walk.tick(stationary, view, settings);
		}

		assertEquals("a zero delta must not zero the heading", 0, walk.getHeadingX());
		assertEquals(1, walk.getHeadingY());
		assertFalse("and the follower must not orbit a player who is standing still",
			walk.isMoving());
	}

	/**
	 * <b>A staircase is not a direction, and this is the guard nothing else exercises.</b>
	 * The heading is a difference between two anchors, and across a plane change that
	 * difference is a coordinate jump rather than a step: climb the stairs in Lumbridge
	 * and the player's tile moves a dozen tiles sideways in the same tick. Fabricating a
	 * heading out of that puts the follower on a side of the player they were never
	 * walking towards, for as long as they then stand still on the new floor — and every
	 * other test in this file is on one plane, so the check that prevents it was
	 * unfalsifiable until this one existed. (It was: an early mutation pass deleted the
	 * plane comparison and the whole suite stayed green.)
	 */
	@Test
	public void aPlaneChangeIsNotADirectionAndDoesNotBecomeOne()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START, view, settings);
		walk.tick(START.dx(1), view, settings);
		assertEquals("this test needs a heading to preserve", 1, walk.getHeadingX());
		assertEquals(0, walk.getHeadingY());

		// Up a staircase: a long sideways jump, on a different plane, which would read as
		// a north-west step to anything that only subtracted the coordinates.
		WorldPoint upstairs = new WorldPoint(START.getX() - 11, START.getY() + 12, 1);
		walk.tick(upstairs, view, settings);

		assertEquals("the heading survives the climb", 1, walk.getHeadingX());
		assertEquals(0, walk.getHeadingY());
		assertEquals("and the follower went up with the player", upstairs, walk.currentTile());

		// Standing still on the new floor keeps it, and the next real step on that floor
		// replaces it — so the guard is a pause, not a freeze.
		walk.tick(upstairs, view, settings);
		assertEquals(1, walk.getHeadingX());

		walk.tick(upstairs.dy(1), view, settings);
		assertEquals("a step taken on the new floor is a direction again", 0, walk.getHeadingX());
		assertEquals(1, walk.getHeadingY());
	}

	/**
	 * A heading of {@code (0, 0)} has no left and no right — every slot collapses onto
	 * the player's own tile — so a player who stops walking must not zero it. Checked
	 * from the other end: whatever the anchor does, the slot is never the anchor.
	 */
	@Test
	public void theSlotIsNeverThePlayersOwnTileHoweverTheAnchorMoves()
	{
		FakeWorldView view = scene();
		Random random = new Random(20260904L);

		for (FormationSlot slot : FormationSlot.values())
		{
			EntourageSettings settings = new FakeConfig().setFormationSlot(slot).settings();
			FollowerWalk walk = new FollowerWalk(START);
			WorldPoint anchor = START;

			for (int tick = 0; tick < 200; tick++)
			{
				anchor = anchor.dx(random.nextInt(3) - 1).dy(random.nextInt(3) - 1);
				walk.tick(anchor, view, settings);

				assertNotEquals(slot.name() + " collapsed onto the player at tick " + tick,
					anchor, walk.stationTile(anchor, settings));
			}
		}
	}

	@Test
	public void aNullAnchorHoldsTheTile()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(null, view, defaults());

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
		assertFalse(walk.isRunning());
	}

	// --- The run -------------------------------------------------------------

	/**
	 * <b>The guard that makes a run honest.</b> Two steps means two collision reads, from
	 * two different tiles. A run that checked only its first tile would walk through
	 * every second wall in the game — and the failure would look like the follower
	 * occasionally clipping through doorframes, which is exactly the kind of thing that
	 * gets written off as "the follower is a bit glitchy".
	 */
	@Test
	public void theSecondStepOfARunIsCollisionCheckedFromTheTileTheFirstOneReached()
	{
		EntourageSettings settings = collinear();
		WorldPoint anchor = START.dx(10);

		FakeWorldView open = scene();
		FollowerWalk unobstructed = new FollowerWalk(START);
		unobstructed.tick(anchor, open, settings);
		assertEquals("with nothing in the way it covers two tiles",
			START.dx(2), unobstructed.currentTile());
		assertTrue(unobstructed.isRunning());

		// The same journey with the second tile filled in.
		FakeWorldView walled = scene().block(START.dx(2));
		FollowerWalk blocked = new FollowerWalk(START);
		blocked.tick(anchor, walled, settings);

		assertEquals("the first step is legal and the second is not",
			START.dx(1), blocked.currentTile());
		assertTrue("it still moved", blocked.isMoving());
		assertFalse("but it did not run", blocked.isRunning());
	}

	@Test
	public void aFollowerThatMayNotRunNeverCoversTwoTiles()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = new FakeConfig().setCanRun(false).settings();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(10);

		for (int tick = 0; tick < 12; tick++)
		{
			WorldPoint before = walk.currentTile();
			walk.tick(anchor, view, settings);
			assertFalse("canRun is off", walk.isRunning());
			assertTrue("one tile a tick at most", walk.currentTile().distanceTo(before) <= 1);
		}
	}

	/**
	 * The threshold is "one step will not get me there", which is what keeps a follower
	 * from sprinting alongside a player who is walking. One tile out it walks; two tiles
	 * out it runs.
	 */
	@Test
	public void itBreaksIntoARunOnlyWhenOneStepWouldNotReachTheSlot()
	{
		EntourageSettings settings = collinear();
		WorldPoint anchor = START.dx(10);

		FollowerWalk oneOut = new FollowerWalk(START.dx(8));
		oneOut.tick(anchor, scene(), settings);
		assertEquals("the slot is one tile away", START.dx(9), oneOut.currentTile());
		assertFalse("so one step gets there and there is nothing to run for", oneOut.isRunning());

		FollowerWalk twoOut = new FollowerWalk(START.dx(7));
		twoOut.tick(anchor, scene(), settings);
		assertEquals(START.dx(9), twoOut.currentTile());
		assertTrue("two tiles out is where the run starts", twoOut.isRunning());

		assertEquals("the numbers above are this constant's", 2, FollowerWalk.RUN_THRESHOLD);
	}

	@Test
	public void aFollowerThatCannotTakeItsFirstStepDoesNotReportARun()
	{
		FakeWorldView view = scene()
			.block(START.dx(1))
			.block(START.dx(1).dy(-1))
			.block(START.dy(-1));
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5), view, defaults());

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
		assertFalse(walk.isRunning());
	}

	/**
	 * <b>The limitation this slice exists to remove.</b> Before the run, a player moving
	 * two tiles a tick against a follower moving one opened a tile of gap every tick
	 * without bound, so the follower was recalled every twelve ticks — 7.2 seconds — for
	 * as long as anybody kept running, which is how people travel. It now keeps station.
	 *
	 * <p>The scene is long on purpose. On the client's own 104-tile square the player
	 * runs off the edge inside half a minute, every collision read after that is
	 * {@code UNKNOWN}, the follower stops stepping at all and the measurement is of the
	 * fixture rather than of the plugin.
	 */
	@Test
	public void aRunningPlayerIsKeptUpWithAndNeverLeavesTheFollowerBehind()
	{
		FakeWorldView view = FakeWorldView.rectangular(3200, 3072, 400, 104, 0);
		WorldPoint start = view.tileAt(10, 50);
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(start);
		WorldPoint anchor = start;

		int recalls = 0;
		for (int tick = 1; tick <= 96; tick++)
		{
			// Two tiles a tick, which is what a run is.
			anchor = anchor.dx(2);
			WorldPoint before = walk.currentTile();
			walk.tick(anchor, view, settings);

			if (!walk.isMoving() && !walk.currentTile().equals(before))
			{
				recalls++;
			}

			if (tick > 2)
			{
				assertEquals("it holds its slot at a run, tick " + tick,
					walk.stationTile(anchor, settings), walk.currentTile());
				assertTrue("and it is running to do it", walk.isRunning());
			}
		}

		assertEquals("no recall is needed any more, at any distance", 0, recalls);
		assertEquals("and it finishes exactly on station",
			settings.getFollowDistance(), walk.currentTile().distanceTo(anchor));
	}

	/**
	 * The same journey with running switched off, which is the old behaviour kept as a
	 * measurement rather than deleted: this is what the setting turns back on, and the
	 * recall cadence is a property of the recall distance rather than a magic number.
	 */
	@Test
	public void aRunningPlayerStillOutrunsAFollowerThatIsNotAllowedToRun()
	{
		EntourageSettings settings = new FakeConfig().setCanRun(false).settings();
		FakeWorldView view = FakeWorldView.rectangular(3200, 3072, 400, 104, 0);
		WorldPoint start = view.tileAt(10, 50);
		FollowerWalk walk = new FollowerWalk(start);
		WorldPoint anchor = start;

		int recalls = 0;
		int lastRecallTick = 0;
		for (int tick = 1; tick <= 96; tick++)
		{
			anchor = anchor.dx(2);
			WorldPoint before = walk.currentTile();
			walk.tick(anchor, view, settings);

			if (!walk.isMoving() && !walk.currentTile().equals(before))
			{
				assertEquals("a recall lands on the player's own tile", anchor, walk.currentTile());
				if (lastRecallTick > 0)
				{
					assertEquals("they come round every recall distance's worth of ticks",
						settings.getRecallDistance(), tick - lastRecallTick);
				}
				lastRecallTick = tick;
				recalls++;
			}
		}

		assertTrue("a follower that cannot run is still outrun", recalls > 0);
	}

	/**
	 * The case that always worked: one tile a tick each, so the follower keeps station on
	 * its own legs. It is here to make the two tests above statements about running
	 * rather than about the recall distance — and to pin that a <i>walking</i> player
	 * never makes the follower run, which is what would happen if the threshold were one
	 * tile instead of two.
	 */
	@Test
	public void aWalkingPlayerIsNeverOutrunAndNeverMakesTheFollowerRun()
	{
		FakeWorldView view = FakeWorldView.rectangular(3200, 3072, 400, 104, 0);
		WorldPoint start = view.tileAt(10, 50);
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(start);
		WorldPoint anchor = start;

		for (int tick = 1; tick <= 96; tick++)
		{
			anchor = anchor.dx(1);
			walk.tick(anchor, view, settings);

			assertTrue("the tail never grows past a tile of slack",
				walk.currentTile().distanceTo(anchor) <= settings.getFollowDistance() + 1);
			assertFalse("a walking player must not make it sprint", walk.isRunning());
			if (tick > 1)
			{
				assertTrue("and the follower keeps pace, a tile a tick", walk.isMoving());
			}
		}

		assertEquals("it finishes exactly on station",
			settings.getFollowDistance(), walk.currentTile().distanceTo(anchor));
	}

	// --- The interpolation, which is what stops it teleporting ---------------

	/**
	 * The whole reason there is a frame clock. Without {@link FollowerWalk#localPoint},
	 * a follower would be drawn at one tile for 600ms and then at the next — a figure
	 * teleporting rather than walking.
	 */
	@Test
	public void theDrawnPositionMovesSmoothlyAcrossTheTick()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), view, collinear());
		assertTrue("this test needs a step in flight", walk.isMoving());

		Set<Integer> xs = new LinkedHashSet<>();
		int previous = Integer.MIN_VALUE;
		for (int frame = 0; frame <= 30; frame++)
		{
			LocalPoint drawn = walk.localPoint(view, frame / 30f);
			assertNotNull(drawn);
			assertTrue("the drawn position must never go backwards", drawn.getX() >= previous);
			previous = drawn.getX();
			xs.add(drawn.getX());
		}

		assertTrue("a tile crossed in fewer than ten distinct positions is a jump, not a walk",
			xs.size() >= 10);
	}

	@Test
	public void theStepStartsWhereTheLastOneEndedAndEndsOnTheNewTile()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), view, collinear());

		LocalPoint from = LocalPoint.fromWorld(view, walk.stepStartTile());
		LocalPoint to = LocalPoint.fromWorld(view, walk.currentTile());

		assertEquals("fraction 0 is the tile it left", from, walk.localPoint(view, 0f));
		assertEquals("fraction 1 is the tile it is walking to", to, walk.localPoint(view, 1f));
		assertEquals("half way is half way",
			(from.getX() + to.getX()) / 2, walk.localPoint(view, 0.5f).getX());
	}

	/**
	 * <b>A two-tile step is interpolated across the whole two tiles.</b>
	 * {@code localPoint} slides {@code from} to {@code to} whatever the gap, which is why
	 * the run needed nothing added to it — and which is exactly the claim that would be
	 * false if a run had been built by taking one step and doubling the drawn distance.
	 */
	@Test
	public void aRunIsDrawnAcrossBothOfItsTiles()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(10), view, collinear());
		assertTrue("this test needs a run in flight", walk.isRunning());

		int start = walk.localPoint(view, 0f).getX();
		int middle = walk.localPoint(view, 0.5f).getX();
		int end = walk.localPoint(view, 1f).getX();

		assertEquals("two tiles, in local units", 256, end - start);
		assertEquals("and the halfway frame is a tile along, not two", 128, middle - start);
	}

	@Test
	public void theFractionIsClamped()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), view, collinear());

		assertEquals("a late frame must not slide past the tile",
			walk.localPoint(view, 1f), walk.localPoint(view, 4.5f));
		assertEquals("an early one must not drag back past the tile it came from",
			walk.localPoint(view, 0f), walk.localPoint(view, -3f));
	}

	@Test
	public void aStandingFollowerIsDrawnOnItsTileWhateverTheFraction()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		LocalPoint tile = LocalPoint.fromWorld(view, START);

		assertEquals(tile, walk.localPoint(view, 0f));
		assertEquals(tile, walk.localPoint(view, 0.5f));
		assertEquals(tile, walk.localPoint(view, 1f));
	}

	/**
	 * <b>The view has to have an id of its own for this to mean anything.</b> The first
	 * version of this test asked the top-level view — whose id is
	 * {@link WorldView#TOPLEVEL}, and that constant is {@code 0} — and
	 * {@code new LocalPoint(x, y)} compiles to {@code <init>(x, y, 0)}, so the assertion
	 * read {@code assertEquals(0, 0)} and the two constructors were indistinguishable.
	 * Replacing the three-argument call in {@code localPoint} with the two-argument one
	 * left it green. It only became a test once the point was built against a view
	 * carrying a non-zero id.
	 */
	@Test
	public void theInterpolatedPointCarriesTheViewItWasBuiltAgainst()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), view, collinear());

		// RuneLiteObject.setLocation deactivates and reactivates the object whenever the
		// point's world view differs from the object's, so a mismatch here would churn
		// the client's registered-object list once per frame per follower.
		assertEquals(view.getId(), walk.localPoint(view, 0.5f).getWorldView());

		// The same scene rectangle under a different view id. localPoint stamps whatever
		// it is handed, so this is the assertion that can fail.
		FakeWorldView identified = scene().asWorldEntityView();
		assertNotEquals("a view whose id is zero cannot tell the two constructors apart",
			WorldView.TOPLEVEL, identified.getId());
		assertEquals("the point carries the view it was built against, id and all",
			identified.getId(), walk.localPoint(identified, 0.5f).getWorldView());
	}

	/**
	 * <b>Every other interpolation test here checks a single step.</b> The second one is
	 * where the origin has to be handed forward: the tile the follower is walking to
	 * becomes the tile it is walking from, and a {@code tick()} that did not do that
	 * would interpolate from two tiles back — a figure that jumps a tile backwards and
	 * then crosses two, every tick, for the whole of every walk.
	 */
	@Test
	public void aSecondConsecutiveStepStartsFromTheTileTheFirstOneReached()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		EntourageSettings settings = new FakeConfig()
			.setFormationSlot(FormationSlot.LEFT)
			.setCanRun(false)
			.settings();
		WorldPoint anchor = START.dx(5);

		walk.tick(anchor, view, settings);
		assertEquals(START.dx(1), walk.currentTile());

		walk.tick(anchor, view, settings);
		assertEquals("two ticks, two tiles", START.dx(2), walk.currentTile());
		assertEquals("and the step in flight starts where the last one ended",
			START.dx(1), walk.stepStartTile());

		assertEquals("the frame at the start of the second tick is drawn on the first tile",
			LocalPoint.fromWorld(view, START.dx(1)), walk.localPoint(view, 0f));
		assertEquals("and the last frame of it on the second",
			LocalPoint.fromWorld(view, START.dx(2)), walk.localPoint(view, 1f));
		assertEquals("which is one tile of travel across the tick, not two", 128,
			walk.localPoint(view, 1f).getX() - walk.localPoint(view, 0f).getX());
	}

	/**
	 * The same claim held across a whole walk rather than at two points of it: the drawn
	 * position never goes backwards, and never covers more ground in a tick than the
	 * follower actually moved.
	 */
	@Test
	public void theDrawnPositionNeverDoublesBackAcrossConsecutiveSteps()
	{
		FakeWorldView view = FakeWorldView.rectangular(3200, 3072, 400, 104, 0);
		WorldPoint start = view.tileAt(10, 50);
		EntourageSettings running = new FakeConfig()
			.setFormationSlot(FormationSlot.LEFT).settings();
		EntourageSettings walking = new FakeConfig()
			.setFormationSlot(FormationSlot.LEFT).setCanRun(false).settings();
		FollowerWalk walk = new FollowerWalk(start);
		WorldPoint anchor = start.dx(11);

		int previous = LocalPoint.fromWorld(view, start).getX();
		for (int tick = 0; tick < 6; tick++)
		{
			// Three ticks of running and then three of walking, so that both step
			// lengths are exercised by one continuous journey rather than by two tests
			// that could disagree about where a tick begins.
			walk.tick(anchor, view, tick < 3 ? running : walking);
			assertTrue("this test needs it actually walking", walk.isMoving());

			int span = walk.isRunning() ? 256 : 128;
			int atStartOfTick = walk.localPoint(view, 0f).getX();
			assertEquals("a tick begins where the last one left the figure drawn",
				previous, atStartOfTick);

			for (int frame = 0; frame <= 30; frame++)
			{
				int drawn = walk.localPoint(view, frame / 30f).getX();
				assertTrue("the drawn position must never go backwards", drawn >= previous);
				assertTrue("nor cover more ground than the step it is drawing",
					drawn - atStartOfTick <= span);
				previous = drawn;
			}
		}
	}

	// --- Walls ---------------------------------------------------------------

	@Test
	public void itWillNotWalkThroughAFilledTile()
	{
		FakeWorldView view = scene().block(START.dx(1));
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5), view, collinear());

		assertEquals("the only tile towards the slot is filled, so it stays put",
			START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	/**
	 * The case a "can something stand here" primitive gets wrong. The tile to the east is
	 * empty — perfectly standable — and has a wall along the edge between it and the
	 * follower.
	 */
	@Test
	public void itWillNotWalkThroughAWall()
	{
		FakeWorldView view = scene().setFlags(START.dx(1), CollisionDataFlag.BLOCK_MOVEMENT_WEST);
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5), view, collinear());

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	@Test
	public void aBlockedDiagonalFallsBackToAnAxisThatStillClosesTheDistance()
	{
		// A wall on the north side only. The north-east diagonal cuts its corner and is
		// refused; east is clear and still gets the follower closer.
		FakeWorldView view = scene().block(START.dy(1));
		EntourageSettings settings = new FakeConfig()
			.setFormationSlot(FormationSlot.LEFT)
			.setCanRun(false)
			.settings();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(5).dy(5);
		WorldPoint slot = walk.stationTile(anchor, settings);

		walk.tick(anchor, view, settings);

		assertEquals("it took the east half of the diagonal", START.dx(1), walk.currentTile());
		assertTrue(walk.isMoving());

		// Not "closer" in Chebyshev terms — an orthogonal step towards a diagonal target
		// leaves the Chebyshev distance alone, because the other axis is still the
		// larger of the two. What it must not do is get further away, and what it does
		// do is close the gap on the axis it moved along, which is what eventually lines
		// the follower up for a clean diagonal.
		assertTrue("a fallback must never increase the distance",
			walk.currentTile().distanceTo(slot) <= START.distanceTo(slot));
		assertEquals("and it closed the gap on the axis it moved along",
			Math.abs(slot.getX() - START.getX()) - 1,
			Math.abs(slot.getX() - walk.currentTile().getX()));
	}

	@Test
	public void itSkipsRatherThanNudgingWhenNothingTowardsTheSlotIsLegal()
	{
		FakeWorldView view = scene()
			.block(START.dx(1))
			.block(START.dy(1))
			.block(START.dx(1).dy(1));
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5).dy(5), view, defaults());

		assertEquals("no sidestep, no back-up, no nudge — it stands still",
			START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	/**
	 * "I cannot answer" has to cost the same as "no". The way a follower actually gets an
	 * {@code UNKNOWN} is a tile off the edge of the loaded scene or a world entity's view;
	 * a view with no collision maps at all is not something the injected client does, and
	 * is used here because it is the cheapest way to put the verdict in front of the
	 * step. What is being pinned is the branch, not the cause: a follower that walked on
	 * regardless would be walking on a guess whichever way the answer went missing.
	 */
	@Test
	public void anUnknownVerdictStopsTheStepExactlyAsABlockedOneDoes()
	{
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), scene().withoutCollisionData(), defaults());

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	@Test
	public void aWorldEntityViewStopsTheStepToo()
	{
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), scene().asWorldEntityView(), defaults());

		assertEquals(START, walk.currentTile());
	}

	// --- Recall --------------------------------------------------------------

	@Test
	public void itIsPutBackWhenItFallsTooFarBehind()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint far = START.dx(settings.getRecallDistance() + 1);

		walk.tick(far, view, settings);

		assertEquals("a follower that has lost the player forms up on him again",
			far, walk.currentTile());
		assertFalse("and does not spend the tick mid-step", walk.isMoving());
		assertFalse(walk.isRunning());
		assertEquals("the step it was on is over", far, walk.stepStartTile());
	}

	@Test
	public void exactlyTheRecallDistanceStillWalks()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = collinear();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint edge = START.dx(settings.getRecallDistance());

		walk.tick(edge, view, settings);

		assertEquals("two tiles of running, not a recall", START.dx(2), walk.currentTile());
		assertTrue(walk.isMoving());
	}

	/**
	 * <b>Chebyshev, not Manhattan.</b> A follower twelve tiles north-east of the player
	 * is twelve tiles away the way the game counts distance and twenty-four the way the
	 * axes add up. A recall measured the second way would fire while the follower was
	 * comfortably inside its own recall distance — one pop per second on any diagonal
	 * journey — and every straight-line test in this file would stay green.
	 */
	@Test
	public void theRecallDistanceIsChebyshevAndNotManhattan()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(START);

		int distance = settings.getRecallDistance();
		WorldPoint diagonal = START.dx(distance).dy(distance);
		assertEquals("this test needs a tile that is on the edge in Chebyshev terms",
			distance, diagonal.distanceTo(START));

		walk.tick(diagonal, view, settings);

		assertTrue("a diagonal at the recall distance walks, it does not pop", walk.isMoving());
		assertNotEquals(diagonal, walk.currentTile());
	}

	@Test
	public void theRecallDistanceSettingDecidesWhenItPops()
	{
		FakeWorldView view = scene();
		EntourageSettings tight = new FakeConfig()
			.setRecallDistance(EntourageSettings.MIN_RECALL_DISTANCE).settings();
		EntourageSettings loose = new FakeConfig()
			.setRecallDistance(EntourageSettings.MAX_RECALL_DISTANCE).settings();

		WorldPoint anchor = START.dx(EntourageSettings.MIN_RECALL_DISTANCE + 1);

		FollowerWalk popped = new FollowerWalk(START);
		popped.tick(anchor, view, tight);
		assertEquals("six tiles is past a tight recall", anchor, popped.currentTile());
		assertFalse(popped.isMoving());

		FollowerWalk walked = new FollowerWalk(START);
		walked.tick(anchor, view, loose);
		assertTrue("and comfortably inside a loose one", walked.isMoving());
		assertNotEquals(anchor, walked.currentTile());
	}

	@Test
	public void aPlaneChangeRecallsHoweverCloseItLooks()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint upstairs = new WorldPoint(START.getX(), START.getY(), 1);

		walk.tick(upstairs, view, defaults());

		assertEquals("a staircase is not a distance", upstairs, walk.currentTile());
		assertEquals(1, walk.currentTile().getPlane());
	}

	// --- Facing --------------------------------------------------------------

	/**
	 * Two followers rather than one turned round, because turning the player round turns
	 * the heading with them and moves the slot off the row: the point here is the facing
	 * a step produces, not the geometry of a U-turn.
	 */
	@Test
	public void itFacesTheWayItIsWalking()
	{
		EntourageSettings settings = collinear();

		FollowerWalk eastwards = new FollowerWalk(START);
		eastwards.tick(START.dx(5), scene(), settings);
		assertTrue(eastwards.isMoving());
		assertEquals(StepOrientation.forStep(1, 0), eastwards.getOrientation());

		FollowerWalk westwards = new FollowerWalk(START);
		westwards.tick(START.dx(-5), scene(), settings);
		assertTrue(westwards.isMoving());
		assertEquals(StepOrientation.forStep(-1, 0), westwards.getOrientation());
	}

	/**
	 * <b>The player has to end up on a different side of the follower for this to mean
	 * anything.</b> An earlier version of this test walked the follower east and then
	 * asserted it was facing east, which is what it was already facing from the last step
	 * it took — deleting the turn-to-face entirely left the test green. Here the follower
	 * walks <i>east</i> onto a slot that is <i>south</i> of the player, so "the way it was
	 * walking" and "the way the player is" are different answers and only one of them
	 * passes.
	 */
	@Test
	public void itTurnsToFaceThePlayerWhenItStops()
	{
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(5);

		// The slot is due south of a player who has never moved, and the follower is due
		// west of it, so the last leg of the journey is eastward.
		assertEquals(anchor.dy(-1), walk.stationTile(anchor, settings));

		for (int i = 0; i < 20 && !walk.currentTile().equals(anchor.dy(-1)); i++)
		{
			walk.tick(anchor, view, settings);
		}
		assertEquals("it should have arrived long before twenty ticks",
			anchor.dy(-1), walk.currentTile());
		assertEquals("it walked east to get here",
			StepOrientation.forStep(1, 0), walk.getOrientation());

		walk.tick(anchor, view, settings);

		assertFalse("on its slot, so it must not have moved", walk.isMoving());
		assertEquals("on station it looks at the player, not at wherever it was walking",
			StepOrientation.forStep(0, 1), walk.getOrientation());
	}

	@Test
	public void aBlockedFollowerStillLooksAtThePlayer()
	{
		FakeWorldView view = scene().block(START.dy(1)).block(START.dx(1).dy(1)).block(START.dx(1));
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5).dy(5), view, defaults());

		assertFalse(walk.isMoving());
		assertEquals(StepOrientation.forStep(1, 1), walk.getOrientation());
	}

	/**
	 * The one case where there is no direction to face: the follower is standing on the
	 * player's own tile — which it may cross when the formation flips to the far side —
	 * and cannot step off it. Snapping the orientation to the table's {@code NOT_MOVING}
	 * sentinel would hand {@code setOrientation} a {@code -1}.
	 */
	@Test
	public void aPlayerStandingOnTheFollowerDoesNotSnapItsFacing()
	{
		EntourageSettings settings = collinear();
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5), scene(), settings);
		int facing = walk.getOrientation();
		assertEquals("this test needs a facing that is not the initial zero",
			StepOrientation.forStep(1, 0), facing);

		WorldPoint onTop = walk.currentTile();
		FakeWorldView boxedIn = scene();
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					boxedIn.block(onTop.dx(dx).dy(dy));
				}
			}
		}

		walk.tick(onTop, boxedIn, settings);

		assertFalse(walk.isMoving());
		assertEquals("there is no direction to face, so keep the one it had",
			facing, walk.getOrientation());
	}

	// --- The long run --------------------------------------------------------

	/**
	 * Two thousand ticks of a player wandering a scene with a hundred blocked tiles in
	 * it. Nothing here asserts about a particular tile; the invariants are the ones a
	 * follower must never break however the geometry works out.
	 *
	 * <p>Note what is <b>not</b> asserted any more: that the follower never steps onto
	 * the player's own tile. It may, and it has to be allowed to — when the player
	 * doubles back, the slot flips to the far side of them and the only way to it is
	 * through. What it never does is <i>settle</i> there, because a slot is at least one
	 * tile off the anchor by construction, and that is pinned separately.
	 */
	@Test
	public void overTwoThousandTicksItNeverJumpsAndNeverStandsSomewhereFilled()
	{
		Random random = new Random(20260903L);
		FakeWorldView view = scene();
		EntourageSettings settings = defaults();

		Set<WorldPoint> blocked = new LinkedHashSet<>();
		for (int i = 0; i < 100; i++)
		{
			WorldPoint tile = START.dx(random.nextInt(21) - 10).dy(random.nextInt(21) - 10);
			if (tile.equals(START))
			{
				continue;
			}
			blocked.add(tile);
			view.block(tile);
		}

		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START;

		for (int tick = 0; tick < 2000; tick++)
		{
			// The player wanders, ignoring collision, the way a player does — kept
			// inside twenty tiles of the start so the whole run stays on the fake
			// scene and every collision read is a real answer rather than an UNKNOWN
			// off the edge of it.
			anchor = clamp(anchor.dx(random.nextInt(3) - 1).dy(random.nextInt(3) - 1));

			WorldPoint before = walk.currentTile();
			walk.tick(anchor, view, settings);
			WorldPoint after = walk.currentTile();

			if (walk.isMoving())
			{
				int covered = after.distanceTo(before);
				if (walk.isRunning())
				{
					// Two steps, so at most two tiles — and sometimes only one, because
					// each step is aimed independently: a run that is turned onto an axis
					// by a wall and then takes the diagonal it wanted ends up one tile
					// away having moved twice. What it can never do is cover more ground
					// than two steps of one tile.
					assertTrue("a run covered " + covered + " tiles", covered >= 1 && covered <= 2);
				}
				else
				{
					assertEquals("a walk is exactly one tile", 1, covered);
				}
				assertFalse("and never onto a filled tile", blocked.contains(after));
			}
			else if (!after.equals(before))
			{
				assertEquals("the only other way to move is a recall, and it lands on the anchor",
					anchor, after);
			}

			assertTrue("a run implies a move", !walk.isRunning() || walk.isMoving());
			assertTrue("it never falls further behind than the recall distance",
				after.distanceTo(anchor) <= settings.getRecallDistance());
		}
	}

	private static WorldPoint clamp(WorldPoint tile)
	{
		return new WorldPoint(
			Math.min(Math.max(tile.getX(), START.getX() - 20), START.getX() + 20),
			Math.min(Math.max(tile.getY(), START.getY() - 20), START.getY() + 20),
			tile.getPlane());
	}
}
