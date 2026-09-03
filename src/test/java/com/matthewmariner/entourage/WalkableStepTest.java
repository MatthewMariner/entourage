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
 * <p><b>Two fixtures, because one of them cannot see half of what this class does.</b>
 * {@link #scene()} is the client's own shape: a 104-tile square centred on {@link #HERE},
 * which puts the tile under test off both diagonals — {@code sceneX} 53, {@code sceneY}
 * 50 — so the walls, placed one edge at a time, catch a reader that tested the source
 * tile instead of the destination or that confused one edge bit for another.
 *
 * <p>What it cannot catch is anything about the <i>scene</i> being square, and that is
 * most of the arithmetic. The centring puts the base at 3168 on <b>both</b> axes and the
 * size at 104 on both, so on that fixture {@code getBaseX()} and {@code getBaseY()} are
 * interchangeable, {@code flags.length} and {@code column.length} are interchangeable,
 * and {@code within(flags, sceneX, sceneY)} and {@code within(flags, sceneY, sceneX)} are
 * the same function. {@link #rectangularScene()} is where those stop being the same —
 * see {@link FakeWorldView} for why a fixture no client would ever hand over is the right
 * tool for it.
 */
public class WalkableStepTest
{
	private static final WorldPoint HERE = new WorldPoint(3221, 3218, 0);

	/**
	 * The asymmetric scene's rectangle. The bases differ, the sizes differ, and the
	 * short axis is short enough that a scene coordinate valid on the long one is off
	 * the end of the other.
	 */
	private static final int RECT_BASE_X = 3200;
	private static final int RECT_BASE_Y = 3072;
	private static final int RECT_SIZE_X = 104;
	private static final int RECT_SIZE_Y = 72;

	private static WorldPoint north()
	{
		return HERE.dy(1);
	}

	private static FakeWorldView scene()
	{
		return FakeWorldView.around(HERE);
	}

	private static FakeWorldView rectangularScene()
	{
		return rectangularScene(0);
	}

	private static FakeWorldView rectangularScene(int plane)
	{
		return FakeWorldView.rectangular(RECT_BASE_X, RECT_BASE_Y, RECT_SIZE_X, RECT_SIZE_Y, plane);
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

	/**
	 * The half of the bounds check the two tests above cannot reach. They step
	 * <i>outwards</i> from a tile that is on the scene, or <i>further out</i> from one
	 * that is off it; both of those fail on the destination. This one steps <b>inwards
	 * from outside</b>, so the destination is perfectly in range and only the source is
	 * not — and the raw API still throws, because it dereferences a
	 * {@code LocalPoint.fromWorld} of the source that came back null.
	 *
	 * <p>A follower should not get here: the recall keeps it within twelve tiles of a
	 * player the scene is built around. "Should not" is the reason to test it rather
	 * than the reason not to — this is the class whose entire job is that no way of not
	 * knowing reaches the caller as an exception.
	 */
	@Test
	public void aStepInwardsFromOutsideTheSceneIsUnknownWhereTheRawApiThrows()
	{
		FakeWorldView view = scene();
		WorldPoint justOutside = new WorldPoint(view.getBaseX() - 1, view.getBaseY() + 10, 0);

		assertEquals("the destination of this step is on the scene, so only the source is not",
			WalkableStep.Verdict.WALKABLE,
			WalkableStep.verdict(view, new WorldPoint(view.getBaseX(), view.getBaseY() + 10, 0), 1, 0));

		try
		{
			new WorldArea(justOutside, 1, 1).canTravelInDirection(view, 1, 0);
			fail("this test is worthless if the API stopped throwing here — re-derive it");
		}
		catch (NullPointerException expected)
		{
			// LocalPoint.fromWorld(view, x, y) is null off the scene and getSceneX()
			// is called on it unguarded.
		}

		assertEquals(WalkableStep.Verdict.UNKNOWN, WalkableStep.verdict(view, justOutside, 1, 0));
	}

	// --- The scene that is not square ----------------------------------------

	/**
	 * A tile whose scene coordinates only fit the rectangle one way round.
	 *
	 * <p>{@code sceneX} 90 is comfortably inside the long axis and off the end of the
	 * short one, so every way of confusing the two axes turns this answer into
	 * {@code UNKNOWN}: subtracting the wrong base, or asking {@code within} about
	 * {@code (sceneY, sceneX)}. On the square fixture all of those are the same
	 * arithmetic and none of them can be caught.
	 */
	@Test
	public void aTileThatOnlyFitsTheRectangleOneWayRoundIsStillAnswered()
	{
		FakeWorldView view = rectangularScene();
		WorldPoint tile = view.tileAt(90, 20);

		assertTrue("the fixture only means something if the two axes disagree here",
			90 < view.getSizeX() && 90 >= view.getSizeY());

		assertEquals(WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(view, tile, 1, 0));
		assertEquals("and a wall on the destination is still read off the right tile",
			WalkableStep.Verdict.BLOCKED,
			WalkableStep.verdict(rectangularScene().block(view.tileAt(91, 20)), tile, 1, 0));
	}

	/**
	 * The flags array is {@code int[sizeX][sizeY]}, so the two axes run out at different
	 * places. Stepping north off the last row is off the end of a column; the same
	 * number as a scene {@code x} is nowhere near the end of the array.
	 */
	@Test
	public void eachAxisOfARectangularSceneRunsOutAtItsOwnEdge()
	{
		FakeWorldView view = rectangularScene();

		assertEquals("north out of the top row is off the end of that column",
			WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(view, view.tileAt(50, RECT_SIZE_Y - 1), 0, 1));
		assertEquals("and back into the scene is answerable again",
			WalkableStep.Verdict.WALKABLE,
			WalkableStep.verdict(view, view.tileAt(50, RECT_SIZE_Y - 1), 0, -1));
		assertEquals("the same number as a scene x is nowhere near the end of the array",
			WalkableStep.Verdict.WALKABLE,
			WalkableStep.verdict(view, view.tileAt(RECT_SIZE_Y - 1, 50), 1, 0));
		assertEquals("which runs out here instead",
			WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(view, view.tileAt(RECT_SIZE_X - 1, 50), 1, 0));
	}

	/**
	 * Every other test in this class runs on plane 0, where "the map for this tile's
	 * plane" and "the map at index zero" are the same map. A follower goes upstairs.
	 */
	@Test
	public void thePlaneOfTheTileSelectsTheCollisionMap()
	{
		FakeWorldView upstairs = rectangularScene(2);
		WorldPoint tile = upstairs.tileAt(90, 20);
		WorldPoint destination = upstairs.tileAt(91, 20);

		assertEquals(2, tile.getPlane());
		assertEquals(WalkableStep.Verdict.WALKABLE, WalkableStep.verdict(upstairs, tile, 1, 0));

		assertEquals("a wall on the ground floor is not a wall on the second",
			WalkableStep.Verdict.WALKABLE,
			WalkableStep.verdict(
				rectangularScene(2).block(new WorldPoint(destination.getX(), destination.getY(), 0)),
				tile, 1, 0));
		assertEquals("and a wall on this plane is",
			WalkableStep.Verdict.BLOCKED,
			WalkableStep.verdict(rectangularScene(2).block(destination), tile, 1, 0));

		assertEquals("a plane whose map has not been built yet is unknown, however loaded plane 0 is",
			WalkableStep.Verdict.UNKNOWN,
			WalkableStep.verdict(rectangularScene(2).withoutCollisionMapFor(2), tile, 1, 0));
	}

	private static boolean inwardStepWorks(FakeWorldView view, WorldPoint from, int dx, int dy)
	{
		return WalkableStep.verdict(view, from, dx, dy) == WalkableStep.Verdict.WALKABLE;
	}
}
