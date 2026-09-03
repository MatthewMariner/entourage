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
 * The movement system: a tile per game tick, interpolated per frame, and never through a
 * wall.
 *
 * <p>Everything here runs against a {@link FakeWorldView} — a scene rectangle and four
 * collision maps — with no client anywhere. That is the point of the split: the decision
 * half of the movement system is arithmetic, and arithmetic can be held to account.
 */
public class FollowerWalkTest
{
	private static final WorldPoint START = new WorldPoint(3221, 3218, 0);

	private static FakeWorldView scene()
	{
		return FakeWorldView.around(START);
	}

	// --- One tile per game tick ---------------------------------------------

	@Test
	public void aTickMovesAtMostOneTile()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(6).dy(6);

		WorldPoint before = walk.currentTile();
		walk.tick(anchor, view);

		assertEquals("a diagonal step is still one tile", 1, walk.currentTile().distanceTo(before));
	}

	@Test
	public void itClosesTheDistanceAndThenStops()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(5);

		for (int i = 0; i < 20; i++)
		{
			walk.tick(anchor, view);
		}

		assertEquals("it stops when it is on station, not on top of the player",
			FollowerWalk.STATION_DISTANCE, walk.currentTile().distanceTo(anchor));
		assertFalse("and it stands still once it is there", walk.isMoving());
	}

	@Test
	public void itDoesNotMoveWhenItIsAlreadyOnStation()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(1);

		walk.tick(anchor, view);

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	/**
	 * <b>Every other station test is orthogonal, and orthogonal geometry cannot tell
	 * Chebyshev from Manhattan.</b> A diagonal neighbour is one tile away the way the
	 * game counts adjacency and two the way the axes add up, so a follower measuring the
	 * wrong one reads a diagonal neighbour as off-station, takes the diagonal step it is
	 * already standing next to — and lands on the player's own tile. That is the failure
	 * {@link FollowerWalk#STATION_DISTANCE}'s own javadoc names: "a follower that stopped
	 * at zero would stand inside the player."
	 */
	@Test
	public void aDiagonalNeighbourIsOnStationAndDoesNotStepOntoThePlayer()
	{
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 || dy == 0)
				{
					continue;
				}

				FakeWorldView view = scene();
				FollowerWalk walk = new FollowerWalk(START);
				WorldPoint anchor = START.dx(dx).dy(dy);

				walk.tick(anchor, view);

				assertEquals("(" + dx + "," + dy + ") is one tile away, not two",
					START, walk.currentTile());
				assertFalse("(" + dx + "," + dy + ") must not start a step", walk.isMoving());
				assertNotEquals("and it must never end up standing inside the player",
					anchor, walk.currentTile());
			}
		}
	}

	/**
	 * The same measurement seen from the other side: a knight's-move away is two tiles in
	 * Chebyshev and three in Manhattan, and either way it walks — so this pins that the
	 * follower is not simply refusing to move.
	 */
	@Test
	public void aFollowerTwoTilesOutDiagonallyStillCloses()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(2).dy(2);

		walk.tick(anchor, view);

		assertEquals("it takes the diagonal", START.dx(1).dy(1), walk.currentTile());
		assertTrue(walk.isMoving());

		walk.tick(anchor, view);
		assertEquals("and then it is a diagonal neighbour, which is on station",
			START.dx(1).dy(1), walk.currentTile());
		assertFalse(walk.isMoving());
	}

	@Test
	public void aNullAnchorHoldsTheTile()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(null, view);

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
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
		walk.tick(START.dx(5), view);
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
		walk.tick(START.dx(5), view);

		LocalPoint from = LocalPoint.fromWorld(view, walk.stepStartTile());
		LocalPoint to = LocalPoint.fromWorld(view, walk.currentTile());

		assertEquals("fraction 0 is the tile it left", from, walk.localPoint(view, 0f));
		assertEquals("fraction 1 is the tile it is walking to", to, walk.localPoint(view, 1f));
		assertEquals("half way is half way",
			(from.getX() + to.getX()) / 2, walk.localPoint(view, 0.5f).getX());
	}

	@Test
	public void theFractionIsClamped()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), view);

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
		walk.tick(START.dx(5), view);

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
		WorldPoint anchor = START.dx(5);

		walk.tick(anchor, view);
		assertEquals(START.dx(1), walk.currentTile());

		walk.tick(anchor, view);
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
	 * position never goes backwards, and never covers more than a tile in a tick.
	 */
	@Test
	public void theDrawnPositionNeverDoublesBackAcrossConsecutiveSteps()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(9);

		int previous = LocalPoint.fromWorld(view, START).getX();
		for (int tick = 0; tick < 6; tick++)
		{
			walk.tick(anchor, view);
			assertTrue("this test needs it actually walking", walk.isMoving());

			int atStartOfTick = walk.localPoint(view, 0f).getX();
			assertEquals("a tick begins where the last one left the figure drawn",
				previous, atStartOfTick);

			for (int frame = 0; frame <= 30; frame++)
			{
				int drawn = walk.localPoint(view, frame / 30f).getX();
				assertTrue("the drawn position must never go backwards", drawn >= previous);
				assertTrue("nor cover more than a tile in a tick", drawn - atStartOfTick <= 128);
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

		walk.tick(START.dx(5), view);

		assertEquals("the only tile towards the anchor is filled, so it stays put",
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

		walk.tick(START.dx(5), view);

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	@Test
	public void aBlockedDiagonalFallsBackToAnAxisThatStillClosesTheDistance()
	{
		// A wall on the north side only. The north-east diagonal cuts its corner and is
		// refused; east is clear and still gets the follower closer.
		FakeWorldView view = scene().block(START.dy(1));
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint anchor = START.dx(5).dy(5);

		walk.tick(anchor, view);

		assertEquals("it took the east half of the diagonal", START.dx(1), walk.currentTile());
		assertTrue(walk.isMoving());

		// Not "closer" in Chebyshev terms — an orthogonal step towards a diagonal target
		// leaves the Chebyshev distance alone, because the other axis is still the
		// larger of the two. What it must not do is get further away, and what it does
		// do is close the gap on the axis it moved along, which is what eventually lines
		// the follower up for a clean diagonal.
		assertTrue("a fallback must never increase the distance",
			walk.currentTile().distanceTo(anchor) <= START.distanceTo(anchor));
		assertEquals("and it closed the gap on the axis it moved along",
			Math.abs(anchor.getX() - START.getX()) - 1,
			Math.abs(anchor.getX() - walk.currentTile().getX()));
	}

	@Test
	public void itSkipsRatherThanNudgingWhenNothingTowardsTheAnchorIsLegal()
	{
		FakeWorldView view = scene()
			.block(START.dx(1))
			.block(START.dy(1))
			.block(START.dx(1).dy(1));
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5).dy(5), view);

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
		walk.tick(START.dx(5), scene().withoutCollisionData());

		assertEquals(START, walk.currentTile());
		assertFalse(walk.isMoving());
	}

	@Test
	public void aWorldEntityViewStopsTheStepToo()
	{
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), scene().asWorldEntityView());

		assertEquals(START, walk.currentTile());
	}

	// --- Recall --------------------------------------------------------------

	@Test
	public void itIsPutBackWhenItFallsTooFarBehind()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint far = START.dx(FollowerWalk.RECALL_DISTANCE + 1);

		walk.tick(far, view);

		assertEquals("a follower that has lost the player forms up on him again",
			far, walk.currentTile());
		assertFalse("and does not spend the tick mid-step", walk.isMoving());
		assertEquals("the step it was on is over", far, walk.stepStartTile());
	}

	@Test
	public void exactlyTheRecallDistanceStillWalks()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint edge = START.dx(FollowerWalk.RECALL_DISTANCE);

		walk.tick(edge, view);

		assertEquals("one tile of walking, not a recall", START.dx(1), walk.currentTile());
		assertTrue(walk.isMoving());
	}

	/**
	 * <b>The limitation, pinned as a number rather than left as a feeling.</b> A running
	 * player covers two tiles a game tick and the follower covers one, so the gap grows
	 * by a tile a tick without bound: twelve ticks after a recall the follower is thirteen
	 * tiles behind and is recalled again. Seven and a fifth seconds, on open ground, for as
	 * long as anybody is running — and running is how people travel.
	 *
	 * <p>This is not a bug report against {@link FollowerWalk#RECALL_DISTANCE}; the recall
	 * is doing exactly what it is for. It is the follower having no run speed, which is a
	 * design change and not this slice's. The test is here so that the README's
	 * "Known limitations" entry cannot quietly stop being true: give the follower a run
	 * and this goes red, which is the moment that bullet has to be deleted.
	 *
	 * <p>The scene is long on purpose. On the client's own 104-tile square the player
	 * runs off the edge inside half a minute, every collision read after that is
	 * {@code UNKNOWN}, the follower stops stepping at all and the recalls bunch up —
	 * which measures the fixture rather than the plugin.
	 */
	@Test
	public void aRunningPlayerIsRecalledEveryTwelveTicksBecauseAFollowerCannotRun()
	{
		assertEquals("the numbers below are this constant's; change it and change them",
			12, FollowerWalk.RECALL_DISTANCE);

		FakeWorldView view = FakeWorldView.rectangular(3200, 3072, 400, 104, 0);
		WorldPoint start = view.tileAt(10, 50);
		FollowerWalk walk = new FollowerWalk(start);
		WorldPoint anchor = start;

		int recalls = 0;
		int lastRecallTick = 0;
		for (int tick = 1; tick <= 96; tick++)
		{
			// Two tiles a tick, which is what a run is.
			anchor = anchor.dx(2);
			WorldPoint before = walk.currentTile();
			walk.tick(anchor, view);

			if (!walk.isMoving() && !walk.currentTile().equals(before))
			{
				assertEquals("a recall lands on the player's own tile", anchor, walk.currentTile());
				assertEquals("and they come round every twelve ticks",
					12, tick - lastRecallTick);
				lastRecallTick = tick;
				recalls++;
			}
		}

		assertEquals("eight recalls in ninety-six ticks, which is one every 7.2 seconds",
			8, recalls);
	}

	/**
	 * The same journey at walking pace, which is the case that works: one tile a tick
	 * each, so the follower keeps station and the recall never fires. This is what makes
	 * the test above a statement about running rather than about the recall distance.
	 */
	@Test
	public void aWalkingPlayerIsNeverOutrunAndNeverRecalledAwayFrom()
	{
		FakeWorldView view = FakeWorldView.rectangular(3200, 3072, 400, 104, 0);
		WorldPoint start = view.tileAt(10, 50);
		FollowerWalk walk = new FollowerWalk(start);
		WorldPoint anchor = start;

		for (int tick = 1; tick <= 96; tick++)
		{
			anchor = anchor.dx(1);
			walk.tick(anchor, view);

			assertTrue("the tail never grows past a tile of slack",
				walk.currentTile().distanceTo(anchor) <= FollowerWalk.STATION_DISTANCE + 1);
			if (tick > 1)
			{
				// A recall ends the tick standing still, so this also says none happened.
				assertTrue("and the follower keeps pace, a tile a tick", walk.isMoving());
			}
		}

		assertEquals("it finishes exactly on station", FollowerWalk.STATION_DISTANCE,
			walk.currentTile().distanceTo(anchor));
	}

	@Test
	public void aPlaneChangeRecallsHoweverCloseItLooks()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint upstairs = new WorldPoint(START.getX(), START.getY(), 1);

		walk.tick(upstairs, view);

		assertEquals("a staircase is not a distance", upstairs, walk.currentTile());
		assertEquals(1, walk.currentTile().getPlane());
	}

	// --- Facing --------------------------------------------------------------

	@Test
	public void itFacesTheWayItIsWalking()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5), view);
		assertEquals(StepOrientation.forStep(1, 0), walk.getOrientation());

		walk.tick(START.dx(-5), view);
		assertEquals(StepOrientation.forStep(-1, 0), walk.getOrientation());
	}

	/**
	 * <b>The player has to end up on the other side of the follower for this to mean
	 * anything.</b> The first version of this test walked the follower east and then
	 * asserted it was facing east, which is what it was already facing from the last step
	 * it took — deleting the turn-to-face entirely left the test green. It only became a
	 * test when the anchor moved past the follower, so that "the way it was walking" and
	 * "the way the player is" are different answers.
	 */
	@Test
	public void itTurnsToFaceThePlayerWhenItStops()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		WorldPoint eastOfIt = START.dx(5);

		for (int i = 0; i < 20 && (walk.isMoving() || walk.currentTile().equals(START)); i++)
		{
			walk.tick(eastOfIt, view);
		}
		assertFalse("it should have arrived long before twenty ticks", walk.isMoving());
		assertEquals("it walked east to get here",
			StepOrientation.forStep(1, 0), walk.getOrientation());

		// The player walks past it, ending up one tile to the west — still on station,
		// so the follower does not move, but now behind it.
		WorldPoint westOfIt = walk.currentTile().dx(-1);
		walk.tick(westOfIt, view);

		assertFalse("still on station, so it must not have moved", walk.isMoving());
		assertEquals("on station it looks at the player, not at wherever it was walking",
			StepOrientation.forStep(-1, 0), walk.getOrientation());
	}

	@Test
	public void aBlockedFollowerStillLooksAtThePlayer()
	{
		FakeWorldView view = scene().block(START.dy(1)).block(START.dx(1).dy(1)).block(START.dx(1));
		FollowerWalk walk = new FollowerWalk(START);

		walk.tick(START.dx(5).dy(5), view);

		assertEquals(StepOrientation.forStep(1, 1), walk.getOrientation());
	}

	@Test
	public void aPlayerStandingOnTheFollowerDoesNotSnapItsFacing()
	{
		FakeWorldView view = scene();
		FollowerWalk walk = new FollowerWalk(START);
		walk.tick(START.dx(5), view);
		int facing = walk.getOrientation();

		walk.tick(walk.currentTile(), view);

		assertEquals("there is no direction to face, so keep the one it had",
			facing, walk.getOrientation());
	}

	// --- The long run --------------------------------------------------------

	/**
	 * Two thousand ticks of a player wandering a scene with a hundred blocked tiles in
	 * it. Nothing here asserts about a particular tile; the invariants are the ones a
	 * follower must never break however the geometry works out.
	 */
	@Test
	public void overTwoThousandTicksItNeverJumpsAndNeverStandsSomewhereFilled()
	{
		Random random = new Random(20260903L);
		FakeWorldView view = scene();

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
			walk.tick(anchor, view);
			WorldPoint after = walk.currentTile();

			if (walk.isMoving())
			{
				assertEquals("a walking follower moves exactly one tile a tick",
					1, after.distanceTo(before));
				assertFalse("and never onto a filled tile", blocked.contains(after));

				// A step is only ever taken from further out than STATION_DISTANCE, so one
				// tile of it cannot land on the anchor. A recall does land there, on
				// purpose, and a recall is the !isMoving() branch below.
				assertNotEquals("and never onto the player's own tile", anchor, after);
			}
			else if (!after.equals(before))
			{
				assertEquals("the only other way to move is a recall, and it lands on the anchor",
					anchor, after);
			}

			assertTrue("it never falls further behind than the recall distance",
				after.distanceTo(anchor) <= FollowerWalk.RECALL_DISTANCE);
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
