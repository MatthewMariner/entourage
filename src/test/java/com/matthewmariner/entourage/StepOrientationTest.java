package com.matthewmariner.entourage;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The eight facings, checked against the client's convention rather than against the
 * table they came from.
 *
 * <p>A test that read the same array the implementation reads would pass a transposed
 * table, a table rotated by a quarter turn, and a table with south and north swapped.
 * So the four cardinals are asserted as the four quarter turns the client's own
 * {@code Angle.getNearestDirection()} buckets them into, and each diagonal is asserted
 * to be the exact midpoint of the two cardinals either side of it — which is a property
 * of the geometry, not a copy of the numbers.
 */
public class StepOrientationTest
{
	private static final int SOUTH = 0;
	private static final int WEST = 512;
	private static final int NORTH = 1024;
	private static final int EAST = 1536;

	@Test
	public void theFourCardinalsAreTheClientsQuarterTurns()
	{
		// dy is north-positive in both world and local coordinates, and the angle rises
		// as the facing turns clockwise from south: south, west, north, east.
		assertEquals("south", SOUTH, StepOrientation.forStep(0, -1));
		assertEquals("west", WEST, StepOrientation.forStep(-1, 0));
		assertEquals("north", NORTH, StepOrientation.forStep(0, 1));
		assertEquals("east", EAST, StepOrientation.forStep(1, 0));
	}

	@Test
	public void aQuarterTurnIsAQuarterOfTheTurnUnits()
	{
		assertEquals("the four cardinals have to be evenly spaced or nothing else lines up",
			StepOrientation.TURN_UNITS / 4, WEST - SOUTH);
		assertEquals(StepOrientation.TURN_UNITS / 4, NORTH - WEST);
		assertEquals(StepOrientation.TURN_UNITS / 4, EAST - NORTH);
	}

	@Test
	public void eachDiagonalSitsExactlyBetweenItsTwoCardinals()
	{
		assertEquals("south-west is half way from south to west",
			(SOUTH + WEST) / 2, StepOrientation.forStep(-1, -1));
		assertEquals("north-west is half way from west to north",
			(WEST + NORTH) / 2, StepOrientation.forStep(-1, 1));
		assertEquals("north-east is half way from north to east",
			(NORTH + EAST) / 2, StepOrientation.forStep(1, 1));
		assertEquals("south-east is half way from east back round to south",
			(EAST + StepOrientation.TURN_UNITS) / 2, StepOrientation.forStep(1, -1));
	}

	@Test
	public void standingStillIsNotADirection()
	{
		assertEquals(StepOrientation.NOT_MOVING, StepOrientation.forStep(0, 0));
	}

	/**
	 * A transposed table — {@code [dy][dx]} instead of {@code [dx][dy]} — would make
	 * west read as south and north read as east. Asserting that the two axes give
	 * different answers is what makes that impossible to get away with, and it is the
	 * reason the fixture is asymmetric.
	 */
	@Test
	public void theTableIsIndexedByTheWestEastComponentFirst()
	{
		assertNotEquals("west and south must not be the same facing",
			StepOrientation.forStep(-1, 0), StepOrientation.forStep(0, -1));
		assertNotEquals("north and east must not be the same facing",
			StepOrientation.forStep(0, 1), StepOrientation.forStep(1, 0));
	}

	@Test
	public void everyRealFacingIsInsideOneTurn()
	{
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				int facing = StepOrientation.forStep(dx, dy);
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				assertTrue("(" + dx + "," + dy + ") -> " + facing + " is outside 0..2047",
					facing >= 0 && facing < StepOrientation.TURN_UNITS);
			}
		}
	}

	@Test
	public void allEightFacingsAreDistinct()
	{
		boolean[] seen = new boolean[StepOrientation.TURN_UNITS];
		int count = 0;
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				int facing = StepOrientation.forStep(dx, dy);
				assertTrue("two different steps face the same way: " + facing, !seen[facing]);
				seen[facing] = true;
				count++;
			}
		}
		assertEquals("there are exactly eight steps", 8, count);
	}

	/**
	 * A caller holding a two-tile delta must get the same answer as one that narrowed it
	 * first, or the follower would face the wrong way exactly when it is furthest from
	 * the player.
	 */
	@Test
	public void magnitudeDoesNotChangeTheAnswer()
	{
		assertEquals(StepOrientation.forStep(1, 0), StepOrientation.forStep(9, 0));
		assertEquals(StepOrientation.forStep(-1, 1), StepOrientation.forStep(-4, 7));
		assertEquals(StepOrientation.forStep(0, -1), StepOrientation.forStep(0, -12));
	}
}
