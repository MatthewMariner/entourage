package com.matthewmariner.entourage;

/**
 * Which way a figure faces when it takes one of the eight steps it can take.
 *
 * <p>The convention is the client's own, taken from
 * {@code Angle.getNearestDirection()}: 0 = south, 1 = west, 2 = north, 3 = east —
 * i.e. the angle rises as the facing turns clockwise from south, through a full turn
 * of {@link #TURN_UNITS}. {@code RuneLiteObjectController.setOrientation(int)} takes
 * a value in that space.
 *
 * <p><b>The method, disassembled, is three steps and not one.</b> An earlier version
 * of this javadoc quoted it as {@code (angle >> 9) & 3}, which is the bucketing
 * without the rounding — the part that makes it a <em>nearest</em>-direction:
 * <pre>
 *   int d = angle &gt;&gt;&gt; 9;              // unsigned, not &gt;&gt;
 *   if ((angle &amp; 256) != 0) d++;       // round to the nearer of the two
 *   switch (d &amp; 3) { 0 SOUTH, 1 WEST, 2 NORTH, 3 EAST }
 * </pre>
 * At the four cardinals (0, 512, 1024, 1536) bit 256 is clear, the increment never
 * fires, and the two forms agree — which is why the mapping above was right anyway,
 * and why nothing here behaves differently. They disagree at exactly the four
 * diagonals this table ships: 256 is WEST under the real method and SOUTH under the
 * truncating one, and 768, 1280 and 1792 are each off by one bucket the same way.
 * Nothing in this plugin calls {@code getNearestDirection}, so the cost of the wrong
 * quotation was to anybody checking the table against it.
 *
 * <p>A lookup table rather than {@code atan2(-dx, -dy)} because there are only eight
 * answers, every one of them exact, and a table cannot be a rounding bug.
 *
 * <p><b>Where the table came from, accurately.</b> The same array is
 * {@code CitizenWalk.STEP_ORIENTATION} in {@code ../lively-cities}, and this is a
 * copy of it, not a re-derivation: the literal is identical down to all three
 * trailing comments, with only the field name and {@code -1} spelled as
 * {@link #NOT_MOVING} to tell them apart. It is private there, which is why there
 * are two of it rather than one. The defence against the copy being wrong is not the
 * copying, it is {@code StepOrientationTest}, which checks every entry against the
 * geometry — the four cardinals from the convention above, and each diagonal against
 * the two cardinals it sits between — rather than against the other copy. That test
 * would fail if both copies were wrong together, which is the only property worth
 * having here.
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
