package com.matthewmariner.entourage;

import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which way a follower that has stopped walking points.
 *
 * <p><b>Every tile in this file has a different x from its y.</b> The class under test
 * subtracts two coordinates from two others and hands the pair to a lookup table indexed
 * {@code [dx][dy]}, which is exactly where an x and a y get swapped by accident — and on
 * a tile like {@code (3200, 3200)} a transposed pair comes out identical and nothing
 * fails. This is the same discipline {@link FakeWorldView#rectangular} exists for one
 * layer down; there is no world view here to make rectangular, because nothing in
 * {@link FollowerFacing} reads a scene.
 */
public class FollowerFacingTest
{
	/** The follower's tile. x and y differ, and so do their digits. */
	private static final WorldPoint FOLLOWER = new WorldPoint(3221, 3218, 0);

	/** A facing that is not one of the eight, so a rounded answer cannot pass as a copy. */
	private static final int BETWEEN_COMPASS_POINTS = 1300;

	@Test
	public void atMeLooksAtThePlayerFromEveryDirection()
	{
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}

				WorldPoint player = FOLLOWER.dx(dx).dy(dy);
				assertEquals("player " + dx + "," + dy,
					StepOrientation.forStep(dx, dy),
					FollowerFacing.AT_ME.orientationFor(player, 0, FOLLOWER));
			}
		}
	}

	/**
	 * <b>Reduced to the eight, not an exact bearing.</b> A player five tiles east and one
	 * north is east, because the client's own actors only face those eight — and because
	 * the same table has to answer for a step, where "five east one north" is not a
	 * direction anything can walk in.
	 */
	@Test
	public void atMeIsOneOfTheEightRatherThanAnExactBearing()
	{
		WorldPoint player = FOLLOWER.dx(5).dy(1);

		assertEquals(StepOrientation.forStep(1, 1),
			FollowerFacing.AT_ME.orientationFor(player, 0, FOLLOWER));
	}

	/**
	 * The one answerless case. {@link Follower} keeps the facing it had rather than
	 * handing {@code setOrientation} the {@code -1} that means "not moving".
	 */
	@Test
	public void atMeHasNoAnswerWhenThePlayerIsOnTheFollowersOwnTile()
	{
		assertEquals(StepOrientation.NOT_MOVING,
			FollowerFacing.AT_ME.orientationFor(FOLLOWER, 0, FOLLOWER));
	}

	/**
	 * <b>The player's own facing, copied exactly.</b> Not rounded to the eight: the client
	 * turns a player in smaller increments than a compass point, and a follower that
	 * snapped would jump between facings while the player turned smoothly.
	 */
	@Test
	public void theWayIAmIsThePlayersOwnOrientationUnchanged()
	{
		assertEquals(BETWEEN_COMPASS_POINTS, FollowerFacing.AS_I_AM
			.orientationFor(FOLLOWER.dx(3).dy(-2), BETWEEN_COMPASS_POINTS, FOLLOWER));

		for (int compass = 0; compass < StepOrientation.TURN_UNITS; compass += 256)
		{
			assertEquals(compass,
				FollowerFacing.AS_I_AM.orientationFor(FOLLOWER.dx(1), compass, FOLLOWER));
		}
	}

	/**
	 * And it depends on nothing else. Two followers on opposite sides of the same player
	 * face the same way, which is the whole of what "stand alongside and look where I
	 * look" means.
	 */
	@Test
	public void theWayIAmIgnoresWhereEitherOfThemIsStanding()
	{
		WorldPoint player = FOLLOWER.dx(4).dy(-3);

		int east = FollowerFacing.AS_I_AM
			.orientationFor(player, BETWEEN_COMPASS_POINTS, player.dx(1));
		int west = FollowerFacing.AS_I_AM
			.orientationFor(player, BETWEEN_COMPASS_POINTS, player.dx(-1).dy(2));

		assertEquals(BETWEEN_COMPASS_POINTS, east);
		assertEquals(east, west);
	}

	/**
	 * An orientation from outside 0..2047 is brought inside it rather than passed on.
	 * {@code Actor} is an interface this plugin does not implement, and a figure handed a
	 * negative orientation is one the renderer was never given.
	 */
	@Test
	public void theWayIAmNormalisesAnAngleFromOutsideOneTurn()
	{
		assertEquals(512, FollowerFacing.AS_I_AM
			.orientationFor(FOLLOWER.dx(1), StepOrientation.TURN_UNITS + 512, FOLLOWER));
		assertEquals("a negative angle is a facing, not a sentinel",
			StepOrientation.TURN_UNITS - 512,
			FollowerFacing.AS_I_AM.orientationFor(FOLLOWER.dx(1), -512, FOLLOWER));
	}

	/**
	 * <b>The eight fixed facings are eight different answers, and each is the direction it
	 * is named after.</b> Checked against {@link StepOrientation} rather than against
	 * numbers written out again here — that table is already held to the geometry by its
	 * own test, and a second copy of the numbers would only ever agree with itself.
	 */
	@Test
	public void eachCompassEntryIsTheDirectionItIsNamedAfter()
	{
		assertEquals(StepOrientation.forStep(0, 1), fixed(FollowerFacing.NORTH));
		assertEquals(StepOrientation.forStep(1, 1), fixed(FollowerFacing.NORTH_EAST));
		assertEquals(StepOrientation.forStep(1, 0), fixed(FollowerFacing.EAST));
		assertEquals(StepOrientation.forStep(1, -1), fixed(FollowerFacing.SOUTH_EAST));
		assertEquals(StepOrientation.forStep(0, -1), fixed(FollowerFacing.SOUTH));
		assertEquals(StepOrientation.forStep(-1, -1), fixed(FollowerFacing.SOUTH_WEST));
		assertEquals(StepOrientation.forStep(-1, 0), fixed(FollowerFacing.WEST));
		assertEquals(StepOrientation.forStep(-1, 1), fixed(FollowerFacing.NORTH_WEST));

		Set<Integer> facings = new LinkedHashSet<>();
		for (FollowerFacing facing : FollowerFacing.values())
		{
			if (facing != FollowerFacing.AT_ME && facing != FollowerFacing.AS_I_AM)
			{
				assertTrue(facing + " points the same way as another entry", facings.add(fixed(facing)));
			}
		}
		assertEquals("eight compass points, all different", 8, facings.size());
	}

	/**
	 * A fixed facing is fixed: neither the player's position nor the player's own facing
	 * moves it. Without this, "North" could be quietly wired to the same code as
	 * {@link FollowerFacing#AT_ME} and pass every test that happens to put the player
	 * north.
	 */
	@Test
	public void aFixedFacingIgnoresThePlayerEntirely()
	{
		WorldPoint playerSouth = FOLLOWER.dy(-4);

		assertEquals(StepOrientation.forStep(0, 1),
			FollowerFacing.NORTH.orientationFor(playerSouth, BETWEEN_COMPASS_POINTS, FOLLOWER));
		assertNotEquals("which is not where the player is",
			FollowerFacing.AT_ME.orientationFor(playerSouth, 0, FOLLOWER),
			FollowerFacing.NORTH.orientationFor(playerSouth, 0, FOLLOWER));
	}

	/** No entry may answer with the not-moving sentinel except the one that has to. */
	@Test
	public void onlyAtMeCanFailToAnswer()
	{
		WorldPoint sameTile = FOLLOWER;

		for (FollowerFacing facing : FollowerFacing.values())
		{
			int answer = facing.orientationFor(sameTile, 0, FOLLOWER);
			if (facing == FollowerFacing.AT_ME)
			{
				assertEquals(StepOrientation.NOT_MOVING, answer);
				continue;
			}

			assertNotEquals(facing + " must always have an answer",
				StepOrientation.NOT_MOVING, answer);
			assertTrue(facing + " answered outside one turn",
				answer >= 0 && answer < StepOrientation.TURN_UNITS);
		}
	}

	/** The settings panel renders an enum by its {@code toString()}. */
	@Test
	public void everyEntryHasItsOwnLabel()
	{
		Set<String> labels = new LinkedHashSet<>();
		for (FollowerFacing facing : FollowerFacing.values())
		{
			assertTrue("two entries share a label: " + facing, labels.add(facing.toString()));
			assertLabelled(facing.toString());
		}
		assertEquals(FollowerFacing.values().length, labels.size());
	}

	private static int fixed(FollowerFacing facing)
	{
		// A player standing on the follower's own tile, which is the one arrangement that
		// gives AT_ME no answer — so a fixed facing wired to it would be caught here too.
		return facing.orientationFor(FOLLOWER, 0, FOLLOWER);
	}

	private static void assertLabelled(String label)
	{
		assertTrue("an entry with no label is a blank row in the dropdown",
			label != null && !label.trim().isEmpty());
	}
}
