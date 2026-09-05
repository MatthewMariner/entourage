package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The shapes: where every follower of every roster size stands, for every heading and
 * both follow distances.
 *
 * <p><b>The anchor's two coordinates differ.</b> A formation is a rotation with a table on
 * top of it, and a rotation is exactly where an x and a y get swapped by accident —
 * invisible on a fixture whose coordinates happen to match. Every expectation below is
 * written so that transposing the axes changes the answer.
 *
 * <p><b>The hand-written shapes are pinned tile by tile, and the generated one is pinned
 * by property.</b> Three of the seven formations are tables somebody typed out, and a
 * typo in one of those is a follower standing in the wrong place with nothing else wrong
 * — so those are written out here a second time, in a different form (offsets from the
 * player rather than {@code (slot, rank)} pairs), which is what makes the two able to
 * disagree. The four column formations are generated from one loop, so a property is the
 * honest guard for them: follower {@code i} is on rank {@code i + 1} in the one direction
 * the formation names.
 */
public class EntourageFormationTest
{
	private static final WorldPoint ANCHOR = new WorldPoint(3221, 3218, 0);

	/** The eight compass directions the heading is quantised to. */
	private static final int[][] HEADINGS = {
		{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
	};

	/** The player walking north, which is the frame every pinned shape below is written in. */
	private static final int[] NORTH = {0, 1};

	/** The three shapes that were typed out rather than generated. */
	private static final EntourageFormation[] GROUPS = {
		EntourageFormation.HANGOUT, EntourageFormation.WEDGE, EntourageFormation.LINE
	};

	/** The four that came from the single-follower setting this enum replaced. */
	private static final EntourageFormation[] COLUMNS = {
		EntourageFormation.BEHIND, EntourageFormation.AHEAD,
		EntourageFormation.LEFT, EntourageFormation.RIGHT
	};

	/**
	 * The furthest out a group formation puts anybody: four tiles, which is a line abreast
	 * of five at the widest follow distance. A literal, because it is the claim the enum's
	 * javadoc makes to justify the group shapes existing at all — "the columns get deep and
	 * these do not" — and a value derived from the tables could not contradict them.
	 */
	private static final int GROUP_MAX_DISTANCE = 4;

	// --- The hand-written shapes, tile by tile --------------------------------

	@Test
	public void theHangoutRingIsTheShapeItIsDocumentedAs()
	{
		shape(EntourageFormation.HANGOUT, 1, offsets(-1, 1));
		shape(EntourageFormation.HANGOUT, 2, offsets(-1, 0, 1, 0));
		shape(EntourageFormation.HANGOUT, 3, offsets(0, -1, -1, 1, 1, 1));
		shape(EntourageFormation.HANGOUT, 4, offsets(-1, -1, 1, -1, -1, 1, 1, 1));
		shape(EntourageFormation.HANGOUT, 5, offsets(0, -1, -1, -1, 1, -1, -1, 1, 1, 1));
	}

	@Test
	public void theWedgeIsTheShapeItIsDocumentedAs()
	{
		shape(EntourageFormation.WEDGE, 1, offsets(-1, -1));
		shape(EntourageFormation.WEDGE, 2, offsets(-1, -1, 1, -1));
		shape(EntourageFormation.WEDGE, 3, offsets(-1, -1, 1, -1, 0, -2));
		shape(EntourageFormation.WEDGE, 4, offsets(-1, -1, 1, -1, -2, -2, 2, -2));
		shape(EntourageFormation.WEDGE, 5, offsets(-1, -1, 1, -1, -2, -2, 2, -2, 0, -2));
	}

	@Test
	public void theLineIsTheShapeItIsDocumentedAs()
	{
		shape(EntourageFormation.LINE, 1, offsets(-1, 0));
		shape(EntourageFormation.LINE, 2, offsets(-1, 0, 1, 0));
		shape(EntourageFormation.LINE, 3, offsets(-1, 0, 1, 0, -2, 0));
		shape(EntourageFormation.LINE, 4, offsets(-1, 0, 1, 0, -2, 0, 2, 0));
		shape(EntourageFormation.LINE, 5, offsets(-1, 0, 1, 0, -2, 0, 2, 0, -3, 0));
	}

	/**
	 * The generated shape, as the property it was generated from: follower {@code i}
	 * stands {@code i + 1} ranks out in the one direction the formation names, and a rank
	 * is one tile past the last.
	 */
	@Test
	public void eachColumnIsOneFollowerPerRankInItsOwnDirection()
	{
		column(EntourageFormation.BEHIND, FormationSlot.BEHIND);
		column(EntourageFormation.AHEAD, FormationSlot.AHEAD);
		column(EntourageFormation.LEFT, FormationSlot.LEFT);
		column(EntourageFormation.RIGHT, FormationSlot.RIGHT);
	}

	private void column(EntourageFormation formation, FormationSlot direction)
	{
		for (int[] heading : HEADINGS)
		{
			for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
				distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
			{
				for (int count = 1; count <= EntourageSettings.MAX_FOLLOWERS; count++)
				{
					for (int index = 0; index < count; index++)
					{
						assertEquals(formation.name() + " follower " + index + " of " + count
								+ " at distance " + distance,
							direction.tileFor(ANCHOR, heading[0], heading[1], distance + index),
							formation.tileFor(ANCHOR, heading[0], heading[1], index, count, distance));
					}
				}
			}
		}
	}

	// --- What every formation has to be true of --------------------------------

	/**
	 * <b>The guarantee the whole design rests on.</b> Two followers told to stand on the
	 * same tile is two figures merged into one for as long as they stand there, and it is
	 * the failure a formation is the only thing that can prevent — {@link FollowerWalk}
	 * deliberately does not check where the other followers are, because refusing to cross
	 * them deadlocks a pair that has to swap sides when the player doubles back.
	 *
	 * <p>Checked for every formation, at every roster size, for every heading and both
	 * follow distances, because a collapse is usually specific to one of those: a table
	 * that repeats a {@code (slot, rank)} pair fails everywhere, but a rotation bug that
	 * folds two directions onto one fails only on the four diagonal headings.
	 */
	@Test
	public void noTwoFollowersAreEverSentToTheSameTile()
	{
		forEveryShape((formation, count, heading, distance, stations) ->
		{
			Set<WorldPoint> seen = new LinkedHashSet<>();
			for (WorldPoint station : stations)
			{
				assertTrue(describe(formation, count, heading, distance)
					+ " puts two followers on " + station, seen.add(station));
			}
		});
	}

	/**
	 * The player's own tile is the other place a follower must never settle. Every station
	 * is at least one rank out by construction; this is what says the construction holds.
	 */
	@Test
	public void noFollowerIsEverSentToThePlayersOwnTile()
	{
		forEveryShape((formation, count, heading, distance, stations) ->
		{
			for (WorldPoint station : stations)
			{
				assertNotEquals(describe(formation, count, heading, distance)
					+ " collapsed onto the player", ANCHOR, station);
			}
		});
	}

	/**
	 * <b>Every formation places every follower, at every roster size.</b> A shape that only
	 * existed at five would be a shape that looked broken four times out of five, and a
	 * shape with a hole in it would be two followers standing on the same station.
	 */
	@Test
	public void everyFormationHasAPlaceForEveryFollowerAtEveryRosterSize()
	{
		for (EntourageFormation formation : EntourageFormation.values())
		{
			assertEquals(formation.name() + " has no shape for a full roster",
				EntourageSettings.MAX_FOLLOWERS, formation.maxRosterSize());

			for (int count = 1; count <= EntourageSettings.MAX_FOLLOWERS; count++)
			{
				assertEquals(formation.name() + " at " + count + " names the wrong number of places",
					count, formation.stationCount(count));
			}
		}
	}

	/**
	 * A station is on the player's plane. {@link FollowerWalk} recalls unconditionally on a
	 * plane change and then walks to a station, so one that carried a stale plane would be
	 * a follower walking to a tile on a floor it is not on.
	 */
	@Test
	public void everyStationIsOnThePlayersOwnPlane()
	{
		WorldPoint upstairs = new WorldPoint(ANCHOR.getX(), ANCHOR.getY(), 2);

		for (EntourageFormation formation : EntourageFormation.values())
		{
			for (int index = 0; index < EntourageSettings.MAX_FOLLOWERS; index++)
			{
				assertEquals(formation.name(), 2, formation
					.tileFor(upstairs, 1, -1, index, EntourageSettings.MAX_FOLLOWERS, 1)
					.getPlane());
			}
		}
	}

	// --- The distances, which is what the recall floor is derived from ---------

	/**
	 * <b>No formation puts a follower further out than the recall would tolerate.</b> A
	 * recall fires when a follower is more than {@code recallDistance} tiles from the
	 * player, and the floor on that setting is
	 * {@link EntourageSettings#MIN_RECALL_DISTANCE} — so a shape that reached past it would
	 * recall a follower for standing exactly where it was told to, on every tick, forever.
	 * The bound is measured off the tables here rather than restated from the constants,
	 * which is what lets a deeper shape fail rather than silently move the bound with it.
	 */
	@Test
	public void nothingIsEverStationedFurtherOutThanTheRecallWouldAllow()
	{
		int furthest = 0;

		for (EntourageFormation formation : EntourageFormation.values())
		{
			for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
				distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
			{
				for (int count = 1; count <= EntourageSettings.MAX_FOLLOWERS; count++)
				{
					for (WorldPoint station : stations(formation, count, NORTH, distance))
					{
						furthest = Math.max(furthest, station.distanceTo(ANCHOR));
					}
				}
			}
		}

		assertEquals("the deepest shape is a column of five at the widest follow distance",
			EntourageSettings.MAX_STATION_DISTANCE, furthest);
		assertTrue("a follower standing where it was told to must never be recalled for it",
			furthest < EntourageSettings.MIN_RECALL_DISTANCE);
	}

	/**
	 * <b>The group shapes stay tight, and that is most of why they exist.</b> The four
	 * column formations run to {@link EntourageSettings#MAX_STATION_DISTANCE}, where greedy
	 * stepping strands followers around corners; these three never do, at any roster size
	 * or follow distance.
	 */
	@Test
	public void theGroupShapesNeverReachAsDeepAsAColumn()
	{
		for (EntourageFormation formation : GROUPS)
		{
			for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
				distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
			{
				for (int count = 1; count <= EntourageSettings.MAX_FOLLOWERS; count++)
				{
					for (WorldPoint station : stations(formation, count, NORTH, distance))
					{
						assertTrue(describe(formation, count, NORTH, distance)
								+ " reaches " + station.distanceTo(ANCHOR) + " tiles out",
							station.distanceTo(ANCHOR) <= GROUP_MAX_DISTANCE);
					}
				}
			}
		}

		assertTrue("a group bound that is not tighter than a column's says nothing",
			GROUP_MAX_DISTANCE < EntourageSettings.MAX_STATION_DISTANCE);
	}

	/**
	 * The follow distance is the nearest rank, and every rank behind it is one tile
	 * further. Checked on the wedge, which is the only hand-written shape with two ranks in
	 * it — so a rank that was being ignored, or added twice, shows up here.
	 */
	@Test
	public void theFollowDistanceMovesTheWholeShapeOutwards()
	{
		List<WorldPoint> close = stations(EntourageFormation.WEDGE, 5, NORTH, 1);
		List<WorldPoint> far = stations(EntourageFormation.WEDGE, 5, NORTH, 2);

		for (int index = 0; index < close.size(); index++)
		{
			assertEquals("follower " + index + " did not move out a tile with the setting",
				close.get(index).distanceTo(ANCHOR) + 1, far.get(index).distanceTo(ANCHOR));
		}
	}

	// --- The shapes are shapes -------------------------------------------------

	/**
	 * <b>Mirror the world about the line the player is walking along and the shape is the
	 * same shape.</b> That is what makes a ring a ring and a wedge a wedge rather than a
	 * lopsided handful of tiles, and it is the property a single mistyped arm breaks while
	 * leaving every count and distance assertion above green.
	 *
	 * <p>One follower is the honest exception in all three: there is nowhere symmetric to
	 * put one figure except directly in front of or behind the player, and both of those
	 * are their own entries in the dropdown already. The line abreast is the other
	 * exception, at every odd size, because the player is standing in the tile that would
	 * balance it.
	 */
	@Test
	public void theGroupShapesAreSymmetricAboutTheWayYouAreWalking()
	{
		symmetric(EntourageFormation.HANGOUT, 2, 3, 4, 5);
		symmetric(EntourageFormation.WEDGE, 2, 3, 4, 5);
		symmetric(EntourageFormation.LINE, 2, 4);

		asymmetric(EntourageFormation.HANGOUT, 1);
		asymmetric(EntourageFormation.WEDGE, 1);
		asymmetric(EntourageFormation.LINE, 1, 3, 5);
	}

	private void symmetric(EntourageFormation formation, int... counts)
	{
		for (int count : counts)
		{
			assertEquals(formation.name() + " at " + count + " is lopsided",
				mirrored(formation, count), new HashSet<>(stations(formation, count, NORTH, 1)));
		}
	}

	private void asymmetric(EntourageFormation formation, int... counts)
	{
		for (int count : counts)
		{
			assertNotEquals(formation.name() + " at " + count
					+ " is symmetric, so this expectation is stale",
				mirrored(formation, count), new HashSet<>(stations(formation, count, NORTH, 1)));
		}
	}

	/** The shape reflected west-to-east about the player, for a northward heading. */
	private Set<WorldPoint> mirrored(EntourageFormation formation, int count)
	{
		Set<WorldPoint> flipped = new HashSet<>();
		for (WorldPoint station : stations(formation, count, NORTH, 1))
		{
			flipped.add(new WorldPoint(
				ANCHOR.getX() * 2 - station.getX(), station.getY(), station.getPlane()));
		}
		return flipped;
	}

	/**
	 * <b>The hangout ring never stands between you and what you are clicking on.</b> The
	 * tile directly ahead is the one a figure is most in the way on — see
	 * {@link FormationSlot#AHEAD} — and leaving it open is what makes a ring readable as a
	 * ring rather than as a wall. Checked at every heading, because "directly ahead" turns
	 * with the player.
	 */
	@Test
	public void theHangoutRingLeavesTheTileDirectlyAheadOfYouOpen()
	{
		for (int[] heading : HEADINGS)
		{
			for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
				distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
			{
				WorldPoint ahead = FormationSlot.AHEAD
					.tileFor(ANCHOR, heading[0], heading[1], distance);

				for (int count = 1; count <= EntourageSettings.MAX_FOLLOWERS; count++)
				{
					assertFalse(describe(EntourageFormation.HANGOUT, count, heading, distance)
							+ " put somebody directly in front of you",
						stations(EntourageFormation.HANGOUT, count, heading, distance)
							.contains(ahead));
				}
			}
		}
	}

	/**
	 * <b>Seven entries in the dropdown, and at a full roster no two of them are the same
	 * shape.</b> Two that agreed would be one setting with two labels — the same failure
	 * the four-slot version of this enum was checked against, restated for shapes.
	 *
	 * <p>At <i>one</i> follower they are allowed to agree, and three pairs do: a wedge of
	 * one is a figure off your shoulder, a line abreast of one is a figure beside you, and
	 * there are only eight tiles to put a single figure on. That is stated below rather
	 * than left as a gap, because a reader who found it would otherwise think it a bug.
	 */
	@Test
	public void noTwoFormationsAreTheSameShapeAtAFullRoster()
	{
		Set<List<WorldPoint>> shapes = new LinkedHashSet<>();
		for (EntourageFormation formation : EntourageFormation.values())
		{
			assertTrue(formation.name() + " is another formation under a second name",
				shapes.add(stations(formation, EntourageSettings.MAX_FOLLOWERS, NORTH, 1)));
		}

		assertEquals(EntourageFormation.values().length, shapes.size());

		assertEquals("a line abreast of one is a figure at your left shoulder",
			stations(EntourageFormation.LEFT, 1, NORTH, 1),
			stations(EntourageFormation.LINE, 1, NORTH, 1));
		assertNotEquals("but they part company the moment there are two",
			stations(EntourageFormation.LEFT, 2, NORTH, 1),
			stations(EntourageFormation.LINE, 2, NORTH, 1));
	}

	// --- The dropdown ----------------------------------------------------------

	/**
	 * <b>The four original constants keep their names.</b> {@code ConfigManager} stores an
	 * enum by its constant name, so renaming one of these silently resets that setting for
	 * everybody who had it — a profile that said {@code LEFT} would come back as the
	 * default with nothing logged. The literals below are the four names already written
	 * into profiles.
	 */
	@Test
	public void theOriginalFourKeepTheNamesAlreadyWrittenIntoProfiles()
	{
		assertEquals("BEHIND", EntourageFormation.BEHIND.name());
		assertEquals("AHEAD", EntourageFormation.AHEAD.name());
		assertEquals("LEFT", EntourageFormation.LEFT.name());
		assertEquals("RIGHT", EntourageFormation.RIGHT.name());
	}

	/**
	 * And they still mean the tile they always meant, for one follower — which is the
	 * other half of "an existing profile keeps working". Asserted against
	 * {@link FormationSlot} rather than against literal offsets, because that enum's own
	 * test is what pins the offsets.
	 */
	@Test
	public void theOriginalFourStillPlaceOneFollowerWhereTheyAlwaysDid()
	{
		for (int[] heading : HEADINGS)
		{
			for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
				distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
			{
				same(EntourageFormation.BEHIND, FormationSlot.BEHIND, heading, distance);
				same(EntourageFormation.AHEAD, FormationSlot.AHEAD, heading, distance);
				same(EntourageFormation.LEFT, FormationSlot.LEFT, heading, distance);
				same(EntourageFormation.RIGHT, FormationSlot.RIGHT, heading, distance);
			}
		}
	}

	private void same(EntourageFormation formation, FormationSlot slot, int[] heading, int distance)
	{
		assertEquals(formation.name() + " moved a lone follower",
			slot.tileFor(ANCHOR, heading[0], heading[1], distance),
			formation.tileFor(ANCHOR, heading[0], heading[1], 0, 1, distance));
	}

	@Test
	public void everyFormationHasItsOwnPlayerFacingName()
	{
		Set<String> names = new HashSet<>();
		for (EntourageFormation formation : EntourageFormation.values())
		{
			assertNotEquals(formation.name() + " is showing its enum constant to the user",
				formation.name(), formation.toString());
			assertTrue(formation.name() + " shares a display name", names.add(formation.toString()));
		}
	}

	@Test
	public void theDefaultIsTheOneTheSettingAlwaysHad()
	{
		assertEquals("the config default and the enum have to agree on the shipped shape",
			EntourageFormation.BEHIND, EntourageFormation.DEFAULT);
		assertEquals(EntourageFormation.DEFAULT, new FakeConfig().formation());
	}

	// --- The out-of-range answers ----------------------------------------------

	/**
	 * <b>An index or a count outside the tables is answered, not thrown.</b> The state this
	 * is for is real: a roster the client refused to let go of keeps followers numbered
	 * against the size it had then, while the settings have already moved to a smaller one.
	 * An {@code ArrayIndexOutOfBoundsException} out of a game-tick handler abandons the
	 * rest of the pass, including whatever was supposed to be deactivated in it.
	 */
	@Test
	public void anIndexOrCountOutsideTheTableIsClampedRatherThanThrown()
	{
		for (EntourageFormation formation : EntourageFormation.values())
		{
			assertEquals(formation.name() + ": a stale index does not fall off the end",
				formation.tileFor(ANCHOR, 0, 1, 2, 3, 1),
				formation.tileFor(ANCHOR, 0, 1, 40, 3, 1));
			assertEquals(formation.name() + ": nor a negative one off the front",
				formation.tileFor(ANCHOR, 0, 1, 0, 3, 1),
				formation.tileFor(ANCHOR, 0, 1, -7, 3, 1));
			assertEquals(formation.name() + ": a roster larger than any shape uses the largest",
				formation.tileFor(ANCHOR, 0, 1, 0, EntourageSettings.MAX_FOLLOWERS, 1),
				formation.tileFor(ANCHOR, 0, 1, 0, 900, 1));
			assertEquals(formation.name() + ": a roster of nobody uses the shape for one",
				formation.tileFor(ANCHOR, 0, 1, 0, 1, 1),
				formation.tileFor(ANCHOR, 0, 1, 0, 0, 1));

			assertEquals(formation.name(), 1, formation.stationCount(0));
			assertEquals(formation.name(),
				EntourageSettings.MAX_FOLLOWERS, formation.stationCount(900));
		}
	}

	// --- Fixtures ---------------------------------------------------------------

	/** What one formation names for one roster size, in roster order. */
	private static List<WorldPoint> stations(EntourageFormation formation, int count,
		int[] heading, int followDistance)
	{
		List<WorldPoint> tiles = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
		{
			tiles.add(formation.tileFor(ANCHOR, heading[0], heading[1], index, count, followDistance));
		}
		return tiles;
	}

	/**
	 * Asserts one hand-written shape, in roster order, for a player walking north at the
	 * shortest follow distance.
	 *
	 * @param expected {@code dx, dy} pairs off the player's own tile
	 */
	private void shape(EntourageFormation formation, int count, List<WorldPoint> expected)
	{
		assertEquals(formation.name() + " at " + count + " followers",
			expected, stations(formation, count, NORTH, 1));
	}

	/** Turns alternating {@code dx, dy} arguments into tiles off the anchor. */
	private static List<WorldPoint> offsets(int... deltas)
	{
		if (deltas.length % 2 != 0)
		{
			throw new IllegalArgumentException("offsets come in pairs: " + Arrays.toString(deltas));
		}

		List<WorldPoint> tiles = new ArrayList<>(deltas.length / 2);
		for (int i = 0; i < deltas.length; i += 2)
		{
			tiles.add(ANCHOR.dx(deltas[i]).dy(deltas[i + 1]));
		}
		return tiles;
	}

	/** Runs a check over every formation, roster size, heading and follow distance. */
	private void forEveryShape(ShapeCheck check)
	{
		for (EntourageFormation formation : EntourageFormation.values())
		{
			for (int[] heading : HEADINGS)
			{
				for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
					distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
				{
					for (int count = 1; count <= EntourageSettings.MAX_FOLLOWERS; count++)
					{
						check.run(formation, count, heading, distance,
							stations(formation, count, heading, distance));
					}
				}
			}
		}
	}

	private interface ShapeCheck
	{
		void run(EntourageFormation formation, int count, int[] heading, int followDistance,
			List<WorldPoint> stations);
	}

	private static String describe(EntourageFormation formation, int count, int[] heading,
		int distance)
	{
		return formation.name() + " with " + count + " at distance " + distance
			+ " heading (" + heading[0] + "," + heading[1] + ")";
	}
}
