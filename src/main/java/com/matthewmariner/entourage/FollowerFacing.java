package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;

/**
 * Which way the follower points once it has stopped walking.
 *
 * <p><b>Only while it is standing still.</b> A figure that is taking a step faces the way
 * the step goes, whatever this setting says — anything else is a figure walking sideways
 * or backwards, which no option should be able to produce. {@link Follower} applies that
 * rule: the walk owns the direction of travel, and this owns what happens when there
 * isn't one.
 *
 * <p><b>Every answer here comes out of {@link StepOrientation}</b>, including the fixed
 * compass points, which are the same eight steps a follower can take with the tile part
 * thrown away. That table is checked against the geometry by
 * {@code StepOrientationTest} rather than against a second copy of itself, and
 * re-deriving the four diagonals with an {@code atan2} here would be a second answer to
 * a question that already has one.
 *
 * <p><b>{@link #AS_I_AM} is the one that reads the player rather than the geometry</b>,
 * and the accessor it ends up using is decided in {@link FollowerAnchor} — see that
 * class for why {@code getOrientation()} is the right field and
 * {@code getCurrentOrientation()} is not.
 */
public enum FollowerFacing
{
	/**
	 * At the player. The default, and what the plugin did before this setting existed.
	 *
	 * <p>The delta is reduced to signs, so this is one of the same eight facings
	 * everything else here uses rather than an exact bearing: a figure two tiles west
	 * and one north of you looks west, not west-north-west, because the client's own
	 * actors only ever face those eight.
	 */
	AT_ME("At me"),

	/**
	 * The way the player is facing — the follower stands alongside and looks where you
	 * look.
	 *
	 * <p>The only entry that is not one of the eight: it copies the player's own
	 * orientation exactly, which the client turns in smaller increments than a compass
	 * point. Rounding it to eight would make a follower snap between facings while the
	 * player turned smoothly, which is a worse-looking answer to "look where I look".
	 */
	AS_I_AM("The way I am"),

	NORTH("North", 0, 1),
	NORTH_EAST("North-east", 1, 1),
	EAST("East", 1, 0),
	SOUTH_EAST("South-east", 1, -1),
	SOUTH("South", 0, -1),
	SOUTH_WEST("South-west", -1, -1),
	WEST("West", -1, 0),
	NORTH_WEST("North-west", -1, 1);

	private final String displayName;

	/**
	 * The step this facing is the direction of, or {@code (0, 0)} for the two entries
	 * that are computed rather than fixed. {@code (0, 0)} is
	 * {@link StepOrientation#NOT_MOVING} in the table, which is why those two never
	 * reach it.
	 */
	private final int dx;
	private final int dy;

	FollowerFacing(String displayName)
	{
		this(displayName, 0, 0);
	}

	FollowerFacing(String displayName, int dx, int dy)
	{
		this.displayName = displayName;
		this.dx = dx;
		this.dy = dy;
	}

	/**
	 * Which way to point a follower that is standing still.
	 *
	 * <p>Takes the three things it needs rather than the anchor object, so the whole of
	 * it is arithmetic a test can state in one line — and so that a follower's own tile,
	 * which only {@link FollowerWalk} knows, does not have to be reachable from here.
	 *
	 * @param anchorTile        the tile the player is drawn on
	 * @param anchorOrientation the player's own facing, in 0..2047
	 * @param followerTile      the tile the follower is standing on
	 * @return the orientation to hold, in 0..2047, or {@link StepOrientation#NOT_MOVING}
	 * when there is no answer — which happens for exactly one case, {@link #AT_ME} with
	 * the player standing on the follower's own tile. The caller keeps the facing it had
	 * rather than snapping to a sentinel.
	 */
	int orientationFor(WorldPoint anchorTile, int anchorOrientation, WorldPoint followerTile)
	{
		switch (this)
		{
			case AT_ME:
				return StepOrientation.forStep(
					anchorTile.getX() - followerTile.getX(),
					anchorTile.getY() - followerTile.getY());
			case AS_I_AM:
				return StepOrientation.normalise(anchorOrientation);
			default:
				return StepOrientation.forStep(dx, dy);
		}
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}
}
