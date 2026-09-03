package com.matthewmariner.entourage;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
	 * "I cannot answer" has to cost the same as "no". A collision map that has not been
	 * built yet is the ordinary state for the first moments of a scene load, and a
	 * follower that walked on regardless would be walking on a guess.
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
