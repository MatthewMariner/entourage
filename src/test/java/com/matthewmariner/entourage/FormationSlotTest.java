package com.matthewmariner.entourage;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Where a slot puts the follower, for every heading the player can have.
 *
 * <p><b>The anchor's two coordinates differ, and so do the two distances used.</b> A
 * rotation is exactly the arithmetic that swaps an x for a y by accident, and it is
 * invisible on a square fixture — the same reason {@code FakeWorldView.rectangular}
 * exists for the scene. Every assertion below is written so that transposing the two
 * axes changes the answer.
 */
public class FormationSlotTest
{
	private static final WorldPoint ANCHOR = new WorldPoint(3221, 3218, 0);

	/** The eight compass directions the heading is quantised to. */
	private static final int[][] HEADINGS = {
		{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
	};

	// --- The three slots, spelled out for a player walking north --------------

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

	/**
	 * Ahead and behind are each other's opposite about the player, which is what makes
	 * them a pair — and the property a copy-pasted sign breaks. Written the same way as
	 * the left/right reflection below rather than as "ahead is minus behind", so a slot
	 * that returned the anchor itself for both could not satisfy it.
	 */
	@Test
	public void aheadAndBehindAreReflectionsOfEachOtherThroughThePlayer()
	{
		for (int[] heading : HEADINGS)
		{
			WorldPoint ahead = FormationSlot.AHEAD.tileFor(ANCHOR, heading[0], heading[1], 2);
			WorldPoint behind = FormationSlot.BEHIND.tileFor(ANCHOR, heading[0], heading[1], 2);

			assertEquals(describe(FormationSlot.AHEAD, heading, 2),
				ANCHOR.getX() * 2 - ahead.getX(), behind.getX());
			assertEquals(describe(FormationSlot.AHEAD, heading, 2),
				ANCHOR.getY() * 2 - ahead.getY(), behind.getY());
			assertNotEquals("a slot that is the player's own tile is not a slot",
				ANCHOR, ahead);
		}
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

	// --- The properties every slot has to have, for every heading -------------

	/**
	 * A slot's Chebyshev distance from the player is the follow distance, whichever way
	 * the player is walking — including diagonally, where a naive offset would land the
	 * follower one-and-a-bit tiles away and make "follow distance 1" mean two different
	 * things depending on which way you were pointed.
	 */
	@Test
	public void everySlotSitsExactlyTheFollowDistanceFromThePlayer()
	{
		for (int[] heading : HEADINGS)
		{
			for (FormationSlot slot : FormationSlot.values())
			{
				for (int distance = 1; distance <= 2; distance++)
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
	 * <b>Two settings that produced the same tile would be one setting with two
	 * labels.</b> Checked for every heading, because the collapse a rotation bug
	 * produces is usually heading-specific — a swapped sign puts LEFT on top of RIGHT
	 * for the four diagonals and leaves the four straight ones looking right.
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

			assertEquals("every slot the dropdown offers has to be somewhere of its own",
				FormationSlot.values().length, tiles.size());
		}
	}

	/**
	 * Left and right are each other's opposite about the player, which is the property
	 * that makes them a pair rather than two unrelated offsets — and the one a
	 * copy-pasted rotation breaks.
	 */
	@Test
	public void leftAndRightAreReflectionsOfEachOtherThroughThePlayer()
	{
		for (int[] heading : HEADINGS)
		{
			WorldPoint left = FormationSlot.LEFT.tileFor(ANCHOR, heading[0], heading[1], 2);
			WorldPoint right = FormationSlot.RIGHT.tileFor(ANCHOR, heading[0], heading[1], 2);

			assertEquals(describe(FormationSlot.LEFT, heading, 2),
				ANCHOR.getX() * 2 - left.getX(), right.getX());
			assertEquals(describe(FormationSlot.LEFT, heading, 2),
				ANCHOR.getY() * 2 - left.getY(), right.getY());
		}
	}

	/**
	 * A slot is on the player's plane. {@link FollowerWalk} recalls unconditionally on a
	 * plane change and then walks to a slot, so a slot that carried a stale plane would
	 * be a follower walking to a tile on a floor it is not on.
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

	@Test
	public void everySlotHasItsOwnPlayerFacingName()
	{
		Set<String> names = new HashSet<>();
		for (FormationSlot slot : FormationSlot.values())
		{
			assertNotEquals(slot.name() + " is showing its enum constant to the user",
				slot.name(), slot.toString());
			assertTrue(slot.name() + " shares a display name", names.add(slot.toString()));
		}
	}

	private static String describe(FormationSlot slot, int[] heading, int distance)
	{
		return slot.name() + " at " + distance + " heading (" + heading[0] + "," + heading[1] + ")";
	}
}
