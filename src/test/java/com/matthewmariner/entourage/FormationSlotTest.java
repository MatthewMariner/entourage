package com.matthewmariner.entourage;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Where a slot puts a figure, for every heading the player can have.
 *
 * <p><b>The anchor's two coordinates differ, and so do the two distances used.</b> A
 * rotation is exactly the arithmetic that swaps an x for a y by accident, and it is
 * invisible on a square fixture — the same reason {@code FakeWorldView.rectangular}
 * exists for the scene. Every assertion below is written so that transposing the two
 * axes changes the answer.
 *
 * <p><b>The four assertions this file opens with are the ones it had when this enum was
 * the setting itself</b>, unchanged, and they are load-bearing for a reason that is not
 * nostalgia: the rotation was reimplemented from scratch — a switch over four cases
 * became one addition round a compass — and these four cases are what says the new
 * arithmetic still produces the old answers. A profile that says {@code LEFT} has to keep
 * meaning the tile it always meant.
 */
public class FormationSlotTest
{
	private static final WorldPoint ANCHOR = new WorldPoint(3221, 3218, 0);

	/** The eight compass directions the heading is quantised to. */
	private static final int[][] HEADINGS = {
		{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
	};

	// --- The four original slots, spelled out for a player walking north ------

	@Test
	public void behindIsTheOppositeOfTheWayThePlayerIsGoing()
	{
		assertEquals("walking north, behind is south",
			ANCHOR.dy(-1), FormationSlot.BEHIND.tileFor(ANCHOR, 0, 1, 1));
		assertEquals("walking east, behind is west",
			ANCHOR.dx(-1), FormationSlot.BEHIND.tileFor(ANCHOR, 1, 0, 1));
		assertEquals("walking north-east, behind is south-west",
			ANCHOR.dx(-2).dy(-2), FormationSlot.BEHIND.tileFor(ANCHOR, 1, 1, 2));
	}

	@Test
	public void aheadIsTheWayThePlayerIsGoing()
	{
		assertEquals("walking north, ahead is north",
			ANCHOR.dy(1), FormationSlot.AHEAD.tileFor(ANCHOR, 0, 1, 1));
		assertEquals("walking east, ahead is east",
			ANCHOR.dx(1), FormationSlot.AHEAD.tileFor(ANCHOR, 1, 0, 1));
		assertEquals("walking north-east, ahead is north-east",
			ANCHOR.dx(2).dy(2), FormationSlot.AHEAD.tileFor(ANCHOR, 1, 1, 2));
	}

	@Test
	public void leftIsAQuarterTurnAnticlockwiseFromTheWayThePlayerIsGoing()
	{
		assertEquals("walking north, the player's left is west",
			ANCHOR.dx(-1), FormationSlot.LEFT.tileFor(ANCHOR, 0, 1, 1));
		assertEquals("walking east, the player's left is north",
			ANCHOR.dy(2), FormationSlot.LEFT.tileFor(ANCHOR, 1, 0, 2));
		assertEquals("walking south, the player's left is east",
			ANCHOR.dx(1), FormationSlot.LEFT.tileFor(ANCHOR, 0, -1, 1));
	}

	@Test
	public void rightIsAQuarterTurnClockwise()
	{
		assertEquals("walking north, the player's right is east",
			ANCHOR.dx(1), FormationSlot.RIGHT.tileFor(ANCHOR, 0, 1, 1));
		assertEquals("walking east, the player's right is south",
			ANCHOR.dy(-2), FormationSlot.RIGHT.tileFor(ANCHOR, 1, 0, 2));
		assertEquals("walking south, the player's right is west",
			ANCHOR.dx(-1), FormationSlot.RIGHT.tileFor(ANCHOR, 0, -1, 1));
	}

	// --- The four corners, which are new ---------------------------------------

	/**
	 * The diagonals, spelled out on two headings apiece. A corner is where a rotation goes
	 * wrong quietly: get the turn one eighth out and a wedge's left arm becomes its right,
	 * which is symmetric enough to look plausible in a screenshot.
	 */
	@Test
	public void theFourCornersSitBetweenTheSlotsTheyAreNamedFor()
	{
		assertEquals("walking north, the front-left corner is north-west",
			ANCHOR.dx(-1).dy(1), FormationSlot.AHEAD_LEFT.tileFor(ANCHOR, 0, 1, 1));
		assertEquals("walking north, the front-right corner is north-east",
			ANCHOR.dx(1).dy(1), FormationSlot.AHEAD_RIGHT.tileFor(ANCHOR, 0, 1, 1));
		assertEquals("walking north, the back-left corner is south-west",
			ANCHOR.dx(-2).dy(-2), FormationSlot.BEHIND_LEFT.tileFor(ANCHOR, 0, 1, 2));
		assertEquals("walking north, the back-right corner is south-east",
			ANCHOR.dx(2).dy(-2), FormationSlot.BEHIND_RIGHT.tileFor(ANCHOR, 0, 1, 2));

		assertEquals("walking east, the front-left corner is north-east",
			ANCHOR.dx(1).dy(1), FormationSlot.AHEAD_LEFT.tileFor(ANCHOR, 1, 0, 1));
		assertEquals("walking east, the back-left corner is north-west",
			ANCHOR.dx(-1).dy(1), FormationSlot.BEHIND_LEFT.tileFor(ANCHOR, 1, 0, 1));

		assertEquals("walking north-east, the front-left corner is due north",
			ANCHOR.dy(1), FormationSlot.AHEAD_LEFT.tileFor(ANCHOR, 1, 1, 1));
		assertEquals("walking north-east, the back-right corner is due south",
			ANCHOR.dy(-1), FormationSlot.BEHIND_RIGHT.tileFor(ANCHOR, 1, 1, 1));
	}

	// --- The properties every slot has to have, for every heading -------------

	/**
	 * A slot's Chebyshev distance from the player is the distance asked for, whichever way
	 * the player is walking — including diagonally, where a rotation done as
	 * {@code a*right + b*forward} would land a corner slot one-and-a-bit tiles away and
	 * make "one tile out" mean two different things depending on which way you were
	 * pointed. This is the property the compass rotation exists to have.
	 */
	@Test
	public void everySlotSitsExactlyTheDistanceAskedForFromThePlayer()
	{
		for (int[] heading : HEADINGS)
		{
			for (FormationSlot slot : FormationSlot.values())
			{
				for (int distance = 1; distance <= EntourageSettings.MAX_STATION_DISTANCE; distance++)
				{
					WorldPoint tile = slot.tileFor(ANCHOR, heading[0], heading[1], distance);
					assertEquals(describe(slot, heading, distance),
						distance, tile.distanceTo(ANCHOR));
				}
			}
		}
	}

	/**
	 * The player's own tile is the one place a follower must never settle, and it is
	 * where every slot lands if the distance is ever allowed to reach zero — which is
	 * what {@link EntourageSettings#MIN_FOLLOW_DISTANCE} is for.
	 */
	@Test
	public void noSlotIsEverThePlayersOwnTile()
	{
		for (int[] heading : HEADINGS)
		{
			for (FormationSlot slot : FormationSlot.values())
			{
				assertNotEquals(describe(slot, heading, 1),
					ANCHOR, slot.tileFor(ANCHOR, heading[0], heading[1], 1));
			}
		}
	}

	/**
	 * <b>Two slots that produced the same tile would be one slot with two names</b>, and
	 * a formation built out of them would put two followers on one tile. Checked for every
	 * heading, because the collapse a rotation bug produces is usually heading-specific —
	 * a swapped sign puts LEFT on top of RIGHT for the four diagonals and leaves the four
	 * straight ones looking right.
	 */
	@Test
	public void everySlotIsItsOwnTileForEveryHeading()
	{
		for (int[] heading : HEADINGS)
		{
			Set<WorldPoint> tiles = new HashSet<>();
			for (FormationSlot slot : FormationSlot.values())
			{
				assertTrue(describe(slot, heading, 1) + " duplicates another slot",
					tiles.add(slot.tileFor(ANCHOR, heading[0], heading[1], 1)));
			}

			assertEquals("every direction a formation can name has to be somewhere of its own",
				FormationSlot.values().length, tiles.size());
		}
	}

	/**
	 * Left and right are each other's opposite about the player, which is the property
	 * that makes them a pair rather than two unrelated offsets — and the one a
	 * copy-pasted rotation breaks. The same for ahead and behind, and for each pair of
	 * opposite corners.
	 */
	@Test
	public void everySlotIsTheReflectionOfTheOneOppositeIt()
	{
		reflect(FormationSlot.LEFT, FormationSlot.RIGHT);
		reflect(FormationSlot.AHEAD, FormationSlot.BEHIND);
		reflect(FormationSlot.AHEAD_LEFT, FormationSlot.BEHIND_RIGHT);
		reflect(FormationSlot.AHEAD_RIGHT, FormationSlot.BEHIND_LEFT);
	}

	private void reflect(FormationSlot one, FormationSlot other)
	{
		for (int[] heading : HEADINGS)
		{
			WorldPoint here = one.tileFor(ANCHOR, heading[0], heading[1], 2);
			WorldPoint there = other.tileFor(ANCHOR, heading[0], heading[1], 2);

			assertEquals(describe(one, heading, 2) + " against " + other.name(),
				ANCHOR.getX() * 2 - here.getX(), there.getX());
			assertEquals(describe(one, heading, 2) + " against " + other.name(),
				ANCHOR.getY() * 2 - here.getY(), there.getY());
			assertNotEquals("a slot that is the player's own tile is not a slot", ANCHOR, here);
		}
	}

	/**
	 * A slot is on the player's plane. {@link FollowerWalk} recalls unconditionally on a
	 * plane change and then walks to a station, so a station that carried a stale plane
	 * would be a follower walking to a tile on a floor it is not on.
	 */
	@Test
	public void everySlotIsOnThePlayersOwnPlane()
	{
		WorldPoint upstairs = new WorldPoint(ANCHOR.getX(), ANCHOR.getY(), 2);
		for (FormationSlot slot : FormationSlot.values())
		{
			assertEquals(slot.name(), 2, slot.tileFor(upstairs, 1, -1, 1).getPlane());
		}
	}

	// --- The compass itself ----------------------------------------------------

	/**
	 * <b>The two tables in this plugin that both claim to know the eight directions are
	 * tied to each other here, and nowhere else.</b> This enum indexes unit steps
	 * clockwise from north through eight; {@link StepOrientation} maps the same eight
	 * steps onto the client's own angle convention, which starts at south and rises
	 * clockwise through 2048. The relation is exact —
	 * {@code orientation == ((point + 4) % 8) * 256} — and a copy-pasted row in either
	 * table breaks it. Without this, each table is only checked against itself.
	 */
	@Test
	public void theCompassAgreesWithTheOrientationTable()
	{
		for (int point = 0; point < FormationSlot.COMPASS_POINTS; point++)
		{
			int[] step = FormationSlot.unitStep(point);

			assertEquals("point " + point + " is not a unit step",
				1, Math.max(Math.abs(step[0]), Math.abs(step[1])));
			assertEquals("point " + point + " and StepOrientation disagree about that step",
				((point + 4) % FormationSlot.COMPASS_POINTS) * 256,
				StepOrientation.forStep(step[0], step[1]));
		}
	}

	/**
	 * Every slot is a whole number of eighths clockwise of "ahead", and no two share one.
	 * A duplicated turn is two dropdown-visible shapes that are secretly the same shape.
	 */
	@Test
	public void everySlotIsItsOwnEighthOfATurn()
	{
		Set<Integer> turns = new HashSet<>();
		for (FormationSlot slot : FormationSlot.values())
		{
			assertTrue(slot.name() + " turns " + slot.getTurn() + ", which is off the compass",
				slot.getTurn() >= 0 && slot.getTurn() < FormationSlot.COMPASS_POINTS);
			assertTrue(slot.name() + " shares a turn with another slot", turns.add(slot.getTurn()));
		}

		assertEquals("eight directions, eight slots",
			FormationSlot.COMPASS_POINTS, FormationSlot.values().length);
		assertEquals(0, FormationSlot.AHEAD.getTurn());
		assertEquals("a quarter turn clockwise", 2, FormationSlot.RIGHT.getTurn());
		assertEquals("half a turn", 4, FormationSlot.BEHIND.getTurn());
		assertEquals("three quarters", 6, FormationSlot.LEFT.getTurn());
	}

	/**
	 * <b>A player with no direction of travel is treated as heading north.</b>
	 * {@link FollowerWalk} never asks with a zero heading — it starts north and only ever
	 * replaces the heading with a non-zero step — but this enum does not get to assume
	 * that, and the alternative to substituting a direction is every slot collapsing onto
	 * the player's own tile, which is the one place a follower must never be.
	 */
	@Test
	public void aHeadingThatIsNotADirectionIsTreatedAsNorthRatherThanCollapsing()
	{
		for (FormationSlot slot : FormationSlot.values())
		{
			assertEquals(slot.name() + " does not fall back to north",
				slot.tileFor(ANCHOR, 0, 1, 1), slot.tileFor(ANCHOR, 0, 0, 1));
			assertNotEquals(slot.name() + " collapsed onto the player",
				ANCHOR, slot.tileFor(ANCHOR, 0, 0, 1));
		}
	}

	/**
	 * Only the sign of the heading matters. {@link FollowerWalk} hands over signums, but a
	 * caller with a two-tile delta in hand should not have to remember to narrow it —
	 * {@link StepOrientation#forStep} makes the same promise for the same reason.
	 */
	@Test
	public void onlyTheSignOfTheHeadingMatters()
	{
		for (FormationSlot slot : FormationSlot.values())
		{
			assertEquals(slot.name(),
				slot.tileFor(ANCHOR, 1, -1, 2), slot.tileFor(ANCHOR, 9, -4, 2));
		}
	}

	/** The two components are read separately, so a test can name one axis at a time. */
	@Test
	public void theTwoOffsetComponentsAreNotEachOther()
	{
		assertEquals("walking north, behind is south: no x movement", 0,
			FormationSlot.BEHIND.offsetX(0, 1));
		assertEquals(-1, FormationSlot.BEHIND.offsetY(0, 1));

		assertEquals("walking north, left is west: no y movement", -1,
			FormationSlot.LEFT.offsetX(0, 1));
		assertEquals(0, FormationSlot.LEFT.offsetY(0, 1));
	}

	/** The unit table hands back a copy, so a caller cannot repaint the compass. */
	@Test
	public void theUnitTableCannotBeEditedByItsCallers()
	{
		int[] north = FormationSlot.unitStep(0);
		north[0] = 99;

		assertEquals("the table handed out its own array", 0, FormationSlot.unitStep(0)[0]);
	}

	private static String describe(FormationSlot slot, int[] heading, int distance)
	{
		return slot.name() + " at " + distance + " heading (" + heading[0] + "," + heading[1] + ")";
	}
}
