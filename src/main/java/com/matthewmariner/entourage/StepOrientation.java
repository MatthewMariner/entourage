package com.matthewmariner.entourage;

/**
 * Which way a figure faces when it takes one of the eight steps it can take.
 *
 * <p>The convention is the client's own, taken from {@code Angle.getNearestDirection()},
 * which buckets {@code (angle >> 9) & 3} as 0 = south, 1 = west, 2 = north, 3 = east
 * — i.e. the angle rises as the facing turns clockwise from south, through a full
 * turn of {@link #TURN_UNITS}. {@code RuneLiteObjectController.setOrientation(int)}
 * takes a value in that space.
 *
 * <p>A lookup table rather than {@code atan2(-dx, -dy)} because there are only eight
 * answers, every one of them exact, and a table cannot be a rounding bug. The same
 * table appears as {@code CitizenWalk.STEP_ORIENTATION} in {@code ../lively-cities};
 * it is private there, so this is a re-derivation rather than a shared class, and
 * {@code StepOrientationTest} re-checks every entry against the four cardinals
 * instead of against the other copy.
 *
 * <p>Nothing here touches the client, so the whole of it is testable with no game
 * running.
 */
final class StepOrientation
{
	/** A full turn, in the units {@code setOrientation} takes: 0..2047. */
	static final int TURN_UNITS = 2048;

	/** The answer for {@code (0, 0)}: a figure that is not moving is not turning. */
	static final int NOT_MOVING = -1;

	/**
	 * Indexed {@code [dx + 1][dy + 1]}, with {@code dy} increasing to the north —
	 * which is the direction world and local coordinates both grow in.
	 */
	private static final int[][] BY_STEP = {
		//        dy = -1 (south)  dy = 0        dy = +1 (north)
		/* dx=-1 */ {256, 512, 768},      // south-west, west, north-west
		/* dx= 0 */ {0, NOT_MOVING, 1024}, // south, (not moving), north
		/* dx=+1 */ {1792, 1536, 1280},   // south-east, east, north-east
	};

	private StepOrientation()
	{
	}

	/**
	 * @param dx the west/east component of the step, of any magnitude
	 * @param dy the south/north component of the step, of any magnitude
	 * @return the orientation to face, in 0..2047, or {@link #NOT_MOVING} when both
	 * components are zero
	 */
	static int forStep(int dx, int dy)
	{
		// Reduced to signs here rather than at every call site. There are eight
		// directions whatever the magnitude, and a caller that has a two-tile delta
		// in hand should not have to remember to narrow it before asking.
		return BY_STEP[Integer.signum(dx) + 1][Integer.signum(dy) + 1];
	}
}
