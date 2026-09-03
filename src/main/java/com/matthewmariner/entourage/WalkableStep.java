package com.matthewmariner.entourage;

import javax.annotation.Nullable;
import net.runelite.api.CollisionData;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * "Could a person walk from this tile to the one next door?", answered from the
 * client's own collision map.
 *
 * <p><b>This is a different question from the one {@code StandableGround} answers in
 * {@code ../lively-cities}, and the difference is the whole reason this class
 * exists.</b> That one asks whether a tile can be occupied — it masks with
 * {@code BLOCK_MOVEMENT_FULL} and deliberately ignores the eight directional bits,
 * because a pavement tile with a wall along its north side is still a tile a person
 * stands on. That is the right question for a citizen placed by hand on ground a
 * human vetted. It is the wrong question for a figure that walks: the tile on the
 * far side of that wall is perfectly standable, and a follower that only consulted
 * {@code BLOCK_MOVEMENT_FULL} would walk straight through the wall to reach it.
 * A follower crosses arbitrary terrain that nobody has ever looked at, so the
 * primitive it needs is about the <i>edge</i>, not the tile.
 *
 * <p><b>RuneLite already ships that primitive, and it is not the one the collision
 * flags obviously suggest.</b> {@link WorldArea#canTravelInDirection(WorldView, int, int)}
 * does the whole job. Disassembled from the 1.12.38 API jar, for a 1x1 area and a
 * one-tile step it:
 * <ul>
 *   <li>signums both components, and returns {@code true} immediately for
 *       {@code (0, 0)};</li>
 *   <li>builds the mask {@code BLOCK_MOVEMENT_FULL} plus <b>the bit for the edge the
 *       step enters the destination tile through</b> — stepping west adds
 *       {@code BLOCK_MOVEMENT_EAST} (8), east adds {@code BLOCK_MOVEMENT_WEST} (128),
 *       south adds {@code BLOCK_MOVEMENT_NORTH} (2), north adds
 *       {@code BLOCK_MOVEMENT_SOUTH} (32) — and tests it against the destination
 *       tile's flags;</li>
 *   <li>for a diagonal, also tests the destination's matching corner bit
 *       ({@code BLOCK_MOVEMENT_NORTH_EAST} (4) for a south-west step, and so on) and
 *       then requires both of the two orthogonal half-steps to be legal as well, so
 *       a figure cannot cut the corner of a wall junction.</li>
 * </ul>
 * Reimplementing that from {@code CollisionDataFlag} would have been three of those
 * four rules and a bug.
 *
 * <p><b>What this class adds is the failure handling, which the API has none of.</b>
 * Every one of the following is an unchecked throw out of {@code canTravelInDirection}
 * on live data, verified in the same disassembly:
 * <ul>
 *   <li>the area is outside the loaded scene — it calls
 *       {@code LocalPoint.fromWorld(worldView, x, y)} and then {@code getSceneX()} on
 *       the result with no null check, and {@code fromWorld} returns {@code null} off
 *       the scene: <b>NPE</b>;</li>
 *   <li>the plane has no collision map yet — it indexes
 *       {@code getCollisionMaps()[plane]} and calls {@code getFlags()} on the element
 *       with no null check, and the client allocates that array with four empty slots
 *       before the scene is built: <b>NPE</b>;</li>
 *   <li>the plane is outside that array: <b>ArrayIndexOutOfBoundsException</b>;</li>
 *   <li>the destination tile is off the edge of the flags array — it indexes
 *       {@code flags[sceneX + dx][sceneY + dy]} unconditionally:
 *       <b>ArrayIndexOutOfBoundsException</b>.</li>
 * </ul>
 * It handles exactly one of its own failures, returning {@code false} when
 * {@code getCollisionMaps()} is itself {@code null} — and {@code false} there means
 * "blocked", which is the wrong answer to give a caller who could instead wait.
 * A follower asks this question every game tick from an event handler, so an
 * exception is not an inconvenience: it abandons the rest of the pass. Every one of
 * those cases comes back from here as {@link Verdict#UNKNOWN} instead.
 *
 * <p><b>{@link Verdict#UNKNOWN} is not "probably fine".</b> It is what the caller
 * gets whenever there is no answer, and {@link FollowerWalk} treats it exactly like
 * {@link Verdict#BLOCKED}: the step is skipped. That is the discipline
 * {@code ../lively-cities} established for placement — skip rather than nudge — and
 * it is the same trade here. A follower that misses a step stands still for 600ms.
 * A follower that guesses walks through a doorframe.
 *
 * <p><b>Top-level views only.</b> The client sizes a non-top-level (a
 * {@code WorldEntity}'s) collision map with its origin one tile to the south-west of
 * the scene's — {@code StandableGround} in {@code ../lively-cities} disassembles the
 * two constructor shapes and the {@code isTopLevel()} test that picks between them.
 * {@code canTravelInDirection} indexes with plain scene coordinates either way, so
 * inside a world entity it silently answers about the tile diagonally behind the one
 * asked about. Off-by-one and silent is exactly the shape of wrong that puts a figure
 * half inside a wall, so a view that is not top-level is {@link Verdict#UNKNOWN}
 * here.
 *
 * <p><b>Client thread only</b>, like everything that reads live scene state.
 */
final class WalkableStep
{
	enum Verdict
	{
		/** The collision map says a person could take this step. */
		WALKABLE,

		/** The collision map says something is in the way. */
		BLOCKED,

		/** No answer available — see the class javadoc. Never treated as a yes. */
		UNKNOWN
	}

	private WalkableStep()
	{
	}

	/**
	 * @param worldView the view whose scene the step is judged in — must be the
	 *                  top-level view, or the answer is {@link Verdict#UNKNOWN}
	 * @param from      the tile being left, whose {@code plane} selects the collision
	 *                  map
	 * @param dx        the west/east component of the step; only its sign is used
	 * @param dy        the south/north component of the step; only its sign is used
	 * @return whether a 1x1 figure could make that step. Never throws.
	 */
	static Verdict verdict(@Nullable WorldView worldView, @Nullable WorldPoint from, int dx, int dy)
	{
		if (worldView == null || from == null)
		{
			return Verdict.UNKNOWN;
		}

		int stepX = Integer.signum(dx);
		int stepY = Integer.signum(dy);
		if (stepX == 0 && stepY == 0)
		{
			// Standing where you already are is always allowed, which is also what
			// canTravelInDirection returns before it touches any scene state.
			return Verdict.WALKABLE;
		}

		if (!worldView.isTopLevel())
		{
			return Verdict.UNKNOWN;
		}

		CollisionData[] maps = worldView.getCollisionMaps();
		if (maps == null)
		{
			return Verdict.UNKNOWN;
		}

		int plane = from.getPlane();
		if (plane < 0 || plane >= maps.length)
		{
			return Verdict.UNKNOWN;
		}

		CollisionData map = maps[plane];
		if (map == null)
		{
			// The array is allocated with four slots before the scene is built, so
			// this is the ordinary "not loaded yet" case rather than an error.
			return Verdict.UNKNOWN;
		}

		int[][] flags = map.getFlags();
		if (flags == null)
		{
			return Verdict.UNKNOWN;
		}

		// The same subtraction the API does internally, done here first so that the
		// two array reads it is about to make unguarded are known to be in range.
		// Bounded by getSizeX()/getSizeY() rather than by Constants.SCENE_SIZE: the
		// client allocates the flags array from those two, and WorldPoint.isInScene
		// tests against those two, whereas LocalPoint.isInScene() hardcodes
		// 104 * 128 and would be wrong on an extended scene.
		int sceneX = from.getX() - worldView.getBaseX();
		int sceneY = from.getY() - worldView.getBaseY();
		if (!within(flags, sceneX, sceneY) || !within(flags, sceneX + stepX, sceneY + stepY))
		{
			return Verdict.UNKNOWN;
		}

		// A 1x1 area, because a follower is one tile like every other humanoid. A
		// larger figure would want its real footprint here, and that is a size the
		// NPCComposition it is dressed from could supply — see FollowerAppearance for
		// why this plugin does not read it yet.
		WorldArea footprint = new WorldArea(from, 1, 1);
		return footprint.canTravelInDirection(worldView, stepX, stepY)
			? Verdict.WALKABLE
			: Verdict.BLOCKED;
	}

	private static boolean within(int[][] flags, int sceneX, int sceneY)
	{
		if (sceneX < 0 || sceneX >= flags.length)
		{
			return false;
		}

		int[] column = flags[sceneX];
		return column != null && sceneY >= 0 && sceneY < column.length;
	}
}
