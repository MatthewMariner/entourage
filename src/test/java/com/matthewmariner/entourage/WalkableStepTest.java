package com.matthewmariner.entourage;

import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The collision primitive: can a figure walk from here to there, and what happens when
 * there is no way to know.
 *
 * <p>The fixture is deliberately asymmetric — the tile under test is not on a diagonal of
 * the scene and the walls are placed on one edge at a time — so a reader that transposed
 * {@code [sceneX][sceneY]}, or that tested the source tile instead of the destination,
 * cannot pass.
 */
public class WalkableStepTest
{
	private static final WorldPoint HERE = new WorldPoint(3221, 3218, 0);

	private static WorldPoint north()
	{
		return HERE.dy(1);
	}

	private static WorldPoint east()
	{
		return HERE.dx(1);
	}

	private static FakeWorldView scene()
	{
		return FakeWorldView.around(HERE);
	}

	// --- Open ground ---------------------------------------------------------

	@Test
	public void openGroundIsWalkableInAllEightDirections()
	{
		FakeWorldView view = scene();
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				assertEquals("(" + dx + "," + dy + ") over open ground",
					WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, HERE, dx, dy));
			}
		}
	}

	@Test
	public void standingStillIsAlwaysAllowed()
	{
		assertEquals(WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(scene(), HERE, 0, 0));
	}

	@Test
	public void onlyTheSignOfTheStepMatters()
	{
		FakeWorldView view = scene().block(north());
		assertEquals("a delta of 5 north is still a step north",
			WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(view, HERE, 0, 5));
	}

	// --- Tiles that cannot be occupied ---------------------------------------

	@Test
	public void aFilledDestinationTileIsBlocked()
	{
		FakeWorldView view = scene().block(north());
		assertEquals(WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(view, HERE, 0, 1));
	}

	/**
	 * The follower may be standing on a tile the collision map calls unoccupiable — a
	 * recall puts it on the player's tile, and a bank counter's tile is
	 * {@code BLOCK_MOVEMENT_OBJECT} on the client's own map while a player stands beside
	 * it. Getting off such a tile has to stay legal, or a recall could strand the figure
	 * for good.
	 */
	@Test
	public void aFilledSourceTileDoesNotStopTheFollowerLeavingIt()
	{
		FakeWorldView view = scene().block(HERE);
		assertEquals(WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, HERE, 0, 1));
	}

	// --- Walls, which is the whole reason this is not StandableGround --------

	/**
	 * The headline case. A wall along the south edge of the tile to the north is a tile
	 * that is perfectly <i>standable</i> — nothing fills it — and that a figure
	 * nonetheless cannot walk into from here. A primitive built on
	 * {@code BLOCK_MOVEMENT_FULL} alone would walk straight through it.
	 */
	@Test
	public void aWallOnTheEdgeBeingEnteredBlocksTheStep()
	{
		FakeWorldView view = scene().setFlags(north(), CollisionDataFlag.BLOCK_MOVEMENT_SOUTH);

		assertEquals("that tile is still standable, and still not reachable from here",
			WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(view, HERE, 0, 1));
		assertEquals("and nothing about it fills the tile",
			0, view.getCollisionMaps()[0].getFlags()
				[north().getX() - view.getBaseX()][north().getY() - view.getBaseY()]
				& CollisionDataFlag.BLOCK_MOVEMENT_FULL);
	}

	@Test
	public void aWallOnTheFarEdgeOfTheDestinationDoesNotBlockTheStep()
	{
		FakeWorldView view = scene().setFlags(north(), CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
		assertEquals("a wall on the far side of a tile does not stop you entering it",
			WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, HERE, 0, 1));
	}

	@Test
	public void eachCardinalIsBlockedByTheEdgeItEnters()
	{
		assertEquals(WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(
			scene().setFlags(HERE.dy(1), CollisionDataFlag.BLOCK_MOVEMENT_SOUTH), HERE, 0, 1));
		assertEquals(WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(
			scene().setFlags(HERE.dy(-1), CollisionDataFlag.BLOCK_MOVEMENT_NORTH), HERE, 0, -1));
		assertEquals(WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(
			scene().setFlags(HERE.dx(1), CollisionDataFlag.BLOCK_MOVEMENT_WEST), HERE, 1, 0));
		assertEquals(WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(
			scene().setFlags(HERE.dx(-1), CollisionDataFlag.BLOCK_MOVEMENT_EAST), HERE, -1, 0));
	}

	/**
	 * A doorframe is two walls meeting at a corner. Walking the diagonal between them
	 * would put the figure through the join, so a diagonal needs both of its orthogonal
	 * half-steps to be legal as well.
	 */
	@Test
	public void aDiagonalCannotCutTheCornerOfAWall()
	{
		FakeWorldView view = scene().block(north());

		assertEquals("north itself is obviously blocked",
			WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(view, HERE, 0, 1));
		assertEquals("and so is the north-east diagonal that would slide past it",
			WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(view, HERE, 1, 1));
		assertEquals("but east is untouched",
			WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, HERE, 1, 0));
	}

	@Test
	public void aFilledDiagonalDestinationIsBlockedEvenWhenBothAxesAreClear()
	{
		FakeWorldView view = scene().block(HERE.dx(1).dy(1));

		assertEquals(WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, HERE, 1, 0));
		assertEquals(WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, HERE, 0, 1));
		assertEquals(WalkableStep.Verdict.BLOCKED, WalkableStep.verdict(view, HERE, 1, 1));
	}

	// --- Every way of not knowing --------------------------------------------

	@Test
	public void aMissingViewOrTileIsUnknown()
	{
		assertEquals(WalkableStep.Verdict.UNKNOWN, WalkableStep.verdict(null, HERE, 0, 1));
		assertEquals(WalkableStep.Verdict.UNKNOWN, WalkableStep.verdict(scene(), null, 0, 1));
	}

	/**
	 * Inside a {@code WorldEntity} the client's collision map is origin-shifted by one
	 * tile, so scene coordinates address the tile diagonally behind the one asked about.
	 * The API would answer confidently and wrongly; this has to say it does not know.
	 */
	@Test
	public void aWorldEntityViewIsUnknownRatherThanAnsweredWithTheWrongTile()
	{
		FakeWorldView view = scene().asWorldEntityView();
		assertEquals(WalkableStep.Verdict.UNKNOWN, WalkableStep.verdict(view, HERE, 0, 1));
	}

	@Test
	public void aSceneWithNoCollisionMapsYetIsUnknown()
	{
		assertEquals(WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(scene().withoutCollisionData(), HERE, 0, 1));
	}

	@Test
	public void aPlaneWithNoMapYetIsUnknown()
	{
		assertEquals(WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(scene().withoutCollisionMapFor(0), HERE, 0, 1));
	}

	@Test
	public void aPlaneOutsideTheMapArrayIsUnknown()
	{
		FakeWorldView view = scene();
		assertEquals(WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(view, new WorldPoint(HERE.getX(), HERE.getY(), 9), 0, 1));
		assertEquals(WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(view, new WorldPoint(HERE.getX(), HERE.getY(), -1), 0, 1));
	}

	/**
	 * The first of the two cases the API throws on. The wrapper exists for this: an
	 * exception out of a collision read abandons the rest of the tick pass, including
	 * whatever was supposed to be deactivated in it.
	 */
	@Test
	public void aTileOutsideTheSceneIsUnknownWhereTheRawApiThrows()
	{
		FakeWorldView view = scene();
		WorldPoint offScene = new WorldPoint(view.getBaseX() - 5, view.getBaseY() - 5, 0);

		try
		{
			new WorldArea(offScene, 1, 1).canTravelInDirection(view, 0, 1);
			fail("this test is worthless if the API stopped throwing here — re-derive it");
		}
		catch (NullPointerException expected)
		{
			// LocalPoint.fromWorld returns null off the scene and the API dereferences it.
		}

		assertEquals(WalkableStep.Verdict.UNKNOWN, WalkableStep.verdict(view, offScene, 0, 1));
	}

	/** The second: the destination is one tile past the edge of the flags array. */
	@Test
	public void aStepOffTheEdgeOfTheSceneIsUnknownWhereTheRawApiThrows()
	{
		FakeWorldView view = scene();
		WorldPoint lastColumn = new WorldPoint(
			view.getBaseX() + Constants.SCENE_SIZE - 1, view.getBaseY() + 10, 0);

		try
		{
			new WorldArea(lastColumn, 1, 1).canTravelInDirection(view, 1, 0);
			fail("this test is worthless if the API stopped throwing here — re-derive it");
		}
		catch (ArrayIndexOutOfBoundsException expected)
		{
			// flags[sceneX + dx] with sceneX + dx == flags.length.
		}

		assertEquals(WalkableStep.Verdict.UNKNOWN, WalkableStep.verdict(view, lastColumn, 1, 0));
		assertEquals("and the step back into the scene is still answerable",
			WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, lastColumn, -1, 0));
	}

	@Test
	public void theEdgeOfTheSceneIsWalkableInwardsOnEverySide()
	{
		FakeWorldView view = scene();
		int maxX = view.getBaseX() + Constants.SCENE_SIZE - 1;
		int maxY = view.getBaseY() + Constants.SCENE_SIZE - 1;

		assertTrue(inwardStepWorks(view, new WorldPoint(view.getBaseX(), view.getBaseY(), 0), 1, 1));
		assertTrue(inwardStepWorks(view, new WorldPoint(maxX, maxY, 0), -1, -1));
		assertEquals("and the step outwards is unknown, not blocked",
			WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(view, new WorldPoint(view.getBaseX(), view.getBaseY(), 0), -1, -1));
	}

	private static boolean inwardStepWorks(FakeWorldView view, WorldPoint from, int dx, int dy)
	{
		return WalkableStep.verdict(view, from, dx, dy) == WalkableStep.Verdict.WALKABLE;
	}
}
