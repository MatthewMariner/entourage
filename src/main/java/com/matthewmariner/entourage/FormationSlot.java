package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;

/**
 * One heading-relative direction: "behind me", "off my left shoulder", "out to my
 * right". The geometric primitive a formation is built out of.
 *
 * <p><b>This used to be the setting itself.</b> It was four entries — behind, ahead,
 * left, right — and it was the whole of where a single follower stood. It is now eight
 * entries and package-private geometry, because a group needs the diagonals and because
 * "where does follower three of five stand?" is a question about a <i>shape</i> rather
 * than about a direction. {@link EntourageFormation} is the setting now, and it is a
 * table of {@code (slot, rank)} pairs over this. Kept rather than replaced because the
 * hard part of it — rotating an offset by the player's heading without swapping an x for
 * a y — was already written, already tested against every heading, and is exactly what a
 * formation needs more of rather than less.
 *
 * <p><b>A slot is one exact tile, not a radius.</b> Before this existed the rule was
 * "get within a tile of the player and stop", which is eight acceptable tiles and no
 * way to express a preference between them — so "stand on my left" could not be a
 * setting at all. The cost of the change is that a follower whose slot is inside a wall
 * stands next to it rather than settling: {@link FollowerWalk} is greedy stepping, not
 * pathfinding, so it gets as close as a legal step allows and then holds. That reads
 * fine (a companion standing beside you) and it is the honest outcome of the rule.
 *
 * <p><b>"Behind" is measured against the direction the player last travelled, not
 * against the direction the player is facing.</b> Both were available and they behave
 * very differently while standing still: a player who turns on the spot to talk to a
 * shopkeeper would send a facing-based entourage walking a circle around them, once per
 * click, forever. Direction of travel does not change when you stand still, so a
 * formation that has settled stays settled — and while you <i>are</i> moving the two
 * answers agree anyway, because the game turns the player to face the way it walks.
 *
 * <p><b>The rotation is a turn round a compass, not a matrix.</b> Every slot is a whole
 * number of eighths clockwise from "the way the player is going"; the heading is itself
 * one of those eight points; so the world direction is one addition modulo eight, and the
 * offset is that point's unit vector times the distance. Two properties fall straight out
 * of that and both matter:
 * <ul>
 *   <li><b>Chebyshev distance is exactly the distance asked for</b>, whichever way the
 *       player is walking — every one of the eight unit vectors is one tile away in the
 *       way the game measures adjacency, including the diagonals. A rotation done as
 *       {@code a*right + b*forward} instead would stretch a diagonal offset on a diagonal
 *       heading, so "two tiles out" would mean two different things depending on which
 *       way you were pointed.</li>
 *   <li><b>Distinct {@code (slot, distance)} pairs are distinct tiles.</b> Same distance
 *       and different slots differ because the unit vectors differ; same slot and
 *       different distances differ because the Chebyshev lengths differ. That is what
 *       {@link EntourageFormation} leans on to guarantee that no two followers are ever
 *       told to stand on the same tile.</li>
 * </ul>
 *
 * <p><b>The compass here is not {@link StepOrientation}'s compass, and they must not be
 * merged carelessly.</b> That one is the client's own convention for
 * {@code setOrientation} — 0 is south and the angle rises clockwise through 2048. This
 * one is 0 for north rising clockwise through 8, because it is an index into a table of
 * unit steps rather than an angle. The two are related by
 * {@code orientation == ((point + 4) % 8) * 256}, and {@code FormationSlotTest} asserts
 * exactly that for all eight — which is what stops either table being edited alone.
 */
enum FormationSlot
{
	/**
	 * Straight up the heading — a figure walking point.
	 *
	 * <p><b>It leads you without knowing where you are going</b>, and that is worth
	 * saying out loud rather than discovering: the slot is a tile ahead of the direction
	 * you last travelled, so the moment you turn, the follower is beside or behind you
	 * and has to walk round to the front again. Turn on the spot and it circles you,
	 * which is the exact behaviour {@link #BEHIND} is measured against direction of
	 * travel to avoid — here it is the honest consequence of asking for a figure in
	 * front. It is also the one direction that regularly stands between you and what you
	 * are clicking on, which no plugin-drawn object blocks (a {@code RuneLiteObject} takes
	 * no clicks) but which does put a body over the thing you are looking at.
	 */
	AHEAD(0),

	/** An eighth clockwise of the heading: off the front-right corner. */
	AHEAD_RIGHT(1),

	/**
	 * Abreast on the player's right — the heading rotated a quarter turn clockwise.
	 * Walking north puts the follower to the east.
	 */
	RIGHT(2),

	/** Off the back-right corner: the trailing arm of a wedge. */
	BEHIND_RIGHT(3),

	/**
	 * Directly behind, which is what a follower normally does. The heading reversed.
	 */
	BEHIND(4),

	/** Off the back-left corner. */
	BEHIND_LEFT(5),

	/**
	 * Abreast on the player's left, i.e. the heading rotated a quarter turn
	 * anticlockwise. Walking north puts the follower to the west.
	 */
	LEFT(6),

	/** Off the front-left corner. */
	AHEAD_LEFT(7);

	/** How many directions there are: eight, the same eight a figure can step in. */
	static final int COMPASS_POINTS = 8;

	/**
	 * Which point a player with no direction of travel is treated as heading: north, i.e.
	 * point 0.
	 *
	 * <p>{@link FollowerWalk} never asks with a zero heading — it starts north and only
	 * ever replaces the heading with a non-zero step, for exactly the reason below — but
	 * this class does not get to assume that about its caller. A zero heading has no left
	 * and no right, and the alternative to substituting one is every slot collapsing onto
	 * the player's own tile, which is the single place a follower must never be.
	 */
	private static final int NO_HEADING = -1;

	/**
	 * The eight unit steps, indexed by compass point, clockwise from north. {@code dy}
	 * increases to the north, which is the direction world and local coordinates both
	 * grow in.
	 */
	private static final int[][] UNIT = {
		{0, 1},   // 0 north
		{1, 1},   // 1 north-east
		{1, 0},   // 2 east
		{1, -1},  // 3 south-east
		{0, -1},  // 4 south
		{-1, -1}, // 5 south-west
		{-1, 0},  // 6 west
		{-1, 1},  // 7 north-west
	};

	/**
	 * The inverse of {@link #UNIT}: which compass point a step is, indexed
	 * {@code [dx + 1][dy + 1]}. {@code (0, 0)} is {@link #NO_HEADING} because standing
	 * still is not a direction.
	 */
	private static final int[][] POINT_BY_STEP = {
		//        dy = -1 (south)  dy = 0      dy = +1 (north)
		/* dx=-1 */ {5, 6, 7},           // south-west, west, north-west
		/* dx= 0 */ {4, NO_HEADING, 0},  // south, (not a direction), north
		/* dx=+1 */ {3, 2, 1},           // south-east, east, north-east
	};

	/** How many eighths clockwise of the heading this slot sits. */
	private final int turn;

	FormationSlot(int turn)
	{
		this.turn = turn;
	}

	/** @return how many eighths clockwise of the heading this slot sits, 0..7 */
	int getTurn()
	{
		return turn;
	}

	/**
	 * @param anchor   the tile the player is on
	 * @param headingX the west/east component of the player's direction of travel, -1,
	 *                 0 or 1
	 * @param headingY the south/north component, -1, 0 or 1
	 * @param distance how many tiles out the slot sits, at least 1 — a slot of zero
	 *                 would be the player's own tile, which is the one place a follower
	 *                 must never settle
	 * @return the tile this slot names, on the anchor's plane
	 */
	WorldPoint tileFor(WorldPoint anchor, int headingX, int headingY, int distance)
	{
		int point = worldPoint(headingX, headingY);
		return new WorldPoint(
			anchor.getX() + UNIT[point][0] * distance,
			anchor.getY() + UNIT[point][1] * distance,
			anchor.getPlane());
	}

	/**
	 * The west/east component of the unit offset.
	 *
	 * <p>Split from {@link #offsetY} rather than returning a point, so that a test can
	 * name one axis at a time. A rotation is exactly where an x and a y get swapped by
	 * accident, and a helper that returned both together would hide the swap inside an
	 * equality on the result.
	 */
	int offsetX(int headingX, int headingY)
	{
		return UNIT[worldPoint(headingX, headingY)][0];
	}

	/** The south/north component of the unit offset — see {@link #offsetX}. */
	int offsetY(int headingX, int headingY)
	{
		return UNIT[worldPoint(headingX, headingY)][1];
	}

	/** @return which compass point this slot resolves to for a given heading, 0..7 */
	private int worldPoint(int headingX, int headingY)
	{
		return (headingPoint(headingX, headingY) + turn) % COMPASS_POINTS;
	}

	/**
	 * @return which of the eight compass points a heading is, substituting north for a
	 * heading that is not a direction at all — see {@link #NO_HEADING}
	 */
	private static int headingPoint(int headingX, int headingY)
	{
		int point = POINT_BY_STEP[Integer.signum(headingX) + 1][Integer.signum(headingY) + 1];
		return point == NO_HEADING ? 0 : point;
	}

	/**
	 * @param point one of the eight compass points, 0..7
	 * @return the unit step it names, as {@code {dx, dy}}. For the tests; the array is
	 * copied so a caller cannot edit the table.
	 */
	static int[] unitStep(int point)
	{
		int[] step = UNIT[point];
		return new int[]{step[0], step[1]};
	}
}
