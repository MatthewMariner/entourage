package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;

/**
 * The shape the entourage stands in: where each of one to five followers keeps station,
 * relative to the player and to the way the player is going.
 *
 * <p><b>A formation is a table, not a formula.</b> Each entry below writes out, by hand,
 * where every follower stands at <i>each</i> roster size from one to five — because a
 * shape that is only checked at five is a shape that looks broken four times out of five,
 * and because "spread N evenly round a ring" is arithmetic whose failures (two followers
 * on one tile, a lopsided four) are much harder to see in a formula than in a list. The
 * arithmetic is confined to the one place it belongs: {@link FormationSlot} turns a
 * direction and a distance into a tile, and it does the rotation.
 *
 * <p><b>Every station is a {@code (slot, rank)} pair, and that is what makes the
 * no-two-followers-on-one-tile guarantee structural rather than hopeful.</b> The rank is
 * the ring: rank 1 sits at the configured follow distance, rank 2 one tile beyond it, and
 * so on. {@link FormationSlot} guarantees that distinct {@code (slot, distance)} pairs are
 * distinct tiles for every heading, so a table whose pairs are distinct is a formation
 * whose followers can never <i>settle</i> on top of each other or on the player. They can
 * still cross — when you double back, the whole shape flips through you and the only route
 * to the far side is through — and that is deliberate, exactly as it is for a single
 * follower crossing your own tile. What no formation can promise is where two followers
 * end up when <i>neither</i> can reach its station: greedy stepping stops where it is
 * blocked, and two figures blocked in the same doorway share a tile until one of them can
 * move. That is the same limitation as a follower stranded behind a wall, and the README
 * says so.
 *
 * <p><b>The four single-direction entries are the setting this plugin already had</b>, and
 * with one follower they are the tile they always were — a profile that says
 * {@code LEFT} still means one figure at your left shoulder. Their names are therefore
 * fixed: {@code ConfigManager} stores an enum by its constant name, so renaming one here
 * silently resets that setting for anybody who had it. With more than one follower they
 * extend the only way a single direction can: into a column, one follower per rank. That
 * makes them the deep formations — five followers behind you at a follow distance of two
 * puts the last of them six tiles back, which greedy stepping strands more often than it
 * strands a follower at one. No group entry reaches past four tiles at any roster size
 * and two of the three never pass three, which is most of why they are worth having.
 *
 * <p><b>Which way the figures point is not decided here.</b> That is the {@code Faces}
 * setting, whose default — "At me" — is what makes {@link #HANGOUT} a ring facing inward
 * rather than a ring of people staring past each other. A formation that overrode it would
 * make an existing setting silently dead, and two dropdowns that fight is worse than two
 * that compose: pick the shape here, pick where they look there, pick what they hold in
 * the pose setting.
 *
 * <p>Nothing here touches the client.
 */
public enum EntourageFormation
{
	/**
	 * A column directly behind you, nearest first. With one follower this is the shipped
	 * default and the plugin's oldest behaviour.
	 */
	BEHIND("Behind me", column(FormationSlot.BEHIND)),

	/** A column directly in front of you — see {@link FormationSlot#AHEAD} on the cost. */
	AHEAD("Ahead of me", column(FormationSlot.AHEAD)),

	/** A rank out to your left, nearest first. */
	LEFT("On my left", column(FormationSlot.LEFT)),

	/** A rank out to your right. */
	RIGHT("On my right", column(FormationSlot.RIGHT)),

	/**
	 * A loose ring around you, everybody at the same distance, facing inward — the
	 * "hangout" the brief asked for.
	 *
	 * <p>Every size but one is symmetric about the line you are walking along, and no size
	 * ever uses the tile <i>directly</i> ahead of you, so the ring is a ring rather than a
	 * wall between you and what you are clicking on. One follower stands off your
	 * front-left corner rather than behind you: a hangout of one is somebody stood next to
	 * you, and "behind me" is already its own entry.
	 */
	HANGOUT("Hangout ring", shapes(
		shape(
			at(FormationSlot.AHEAD_LEFT, 1)),
		shape(
			at(FormationSlot.LEFT, 1),
			at(FormationSlot.RIGHT, 1)),
		shape(
			at(FormationSlot.BEHIND, 1),
			at(FormationSlot.AHEAD_LEFT, 1),
			at(FormationSlot.AHEAD_RIGHT, 1)),
		shape(
			at(FormationSlot.BEHIND_LEFT, 1),
			at(FormationSlot.BEHIND_RIGHT, 1),
			at(FormationSlot.AHEAD_LEFT, 1),
			at(FormationSlot.AHEAD_RIGHT, 1)),
		shape(
			at(FormationSlot.BEHIND, 1),
			at(FormationSlot.BEHIND_LEFT, 1),
			at(FormationSlot.BEHIND_RIGHT, 1),
			at(FormationSlot.AHEAD_LEFT, 1),
			at(FormationSlot.AHEAD_RIGHT, 1)))),

	/**
	 * A V trailing behind you, arms first and the middle filled last.
	 *
	 * <p>The two arms grow together, so every size but one is symmetric about the line you
	 * are walking along — the odd sizes by putting a figure on that line rather than by
	 * leaving one arm short. Nothing here goes past rank two, so the furthest station is
	 * three tiles out even at the widest follow distance.
	 */
	WEDGE("Wedge behind", shapes(
		shape(
			at(FormationSlot.BEHIND_LEFT, 1)),
		shape(
			at(FormationSlot.BEHIND_LEFT, 1),
			at(FormationSlot.BEHIND_RIGHT, 1)),
		shape(
			at(FormationSlot.BEHIND_LEFT, 1),
			at(FormationSlot.BEHIND_RIGHT, 1),
			at(FormationSlot.BEHIND, 2)),
		shape(
			at(FormationSlot.BEHIND_LEFT, 1),
			at(FormationSlot.BEHIND_RIGHT, 1),
			at(FormationSlot.BEHIND_LEFT, 2),
			at(FormationSlot.BEHIND_RIGHT, 2)),
		shape(
			at(FormationSlot.BEHIND_LEFT, 1),
			at(FormationSlot.BEHIND_RIGHT, 1),
			at(FormationSlot.BEHIND_LEFT, 2),
			at(FormationSlot.BEHIND_RIGHT, 2),
			at(FormationSlot.BEHIND, 2)))),

	/**
	 * A rank abreast with you standing in it, filling outwards from your shoulders.
	 *
	 * <p>With one follower this is the same tile as {@link #LEFT}, and it has to be: a line
	 * abreast of one person is one person beside you. From two up it is a line across with
	 * you in the middle of it; the even sizes are symmetric and the odd ones are lopsided
	 * by one, because you are standing in the only place a fifth figure could go to
	 * balance it.
	 */
	LINE("Line abreast", shapes(
		shape(
			at(FormationSlot.LEFT, 1)),
		shape(
			at(FormationSlot.LEFT, 1),
			at(FormationSlot.RIGHT, 1)),
		shape(
			at(FormationSlot.LEFT, 1),
			at(FormationSlot.RIGHT, 1),
			at(FormationSlot.LEFT, 2)),
		shape(
			at(FormationSlot.LEFT, 1),
			at(FormationSlot.RIGHT, 1),
			at(FormationSlot.LEFT, 2),
			at(FormationSlot.RIGHT, 2)),
		shape(
			at(FormationSlot.LEFT, 1),
			at(FormationSlot.RIGHT, 1),
			at(FormationSlot.LEFT, 2),
			at(FormationSlot.RIGHT, 2),
			at(FormationSlot.LEFT, 3))));

	/**
	 * Which formation a fresh install stands in.
	 *
	 * <p>Named here rather than written into {@code EntourageConfig.formation()}'s default
	 * so that the two cannot drift apart, and it is the entry that reproduces the plugin's
	 * behaviour before formations existed.
	 */
	public static final EntourageFormation DEFAULT = BEHIND;

	private final String displayName;

	/**
	 * Indexed {@code [count - 1][index]}: the stations for a roster of {@code count},
	 * in roster order.
	 */
	private final Station[][] byCount;

	EntourageFormation(String displayName, Station[][] byCount)
	{
		this.displayName = displayName;
		this.byCount = byCount;
	}

	/**
	 * Where one follower of a roster stands.
	 *
	 * <p><b>The two indices are clamped rather than trusted, and that is not paranoia
	 * about the caller's arithmetic.</b> A roster that could not be retired — the client
	 * refusing to let go of a {@code RuneLiteObject}, see {@link EntourageScene#shutdown()}
	 * — keeps followers whose index was assigned against the roster size it had then, while
	 * the settings have already moved on to a smaller one. An
	 * {@code ArrayIndexOutOfBoundsException} out of a game-tick handler is the abandonment
	 * of the rest of the pass, including whatever was supposed to be deactivated in it, so
	 * the out-of-range case is answered rather than thrown: the follower stands on the last
	 * station of the shape, which is a figure in a slightly wrong place instead of a figure
	 * that took the tick down with it.
	 *
	 * @param anchor         the tile the player is on
	 * @param headingX       the west/east component of the player's direction of travel
	 * @param headingY       the south/north component
	 * @param index          which follower this is, 0-based
	 * @param count          how many followers there are
	 * @param followDistance how far out the nearest rank sits, at least 1 — a rank of zero
	 *                       would put a follower on the player's own tile
	 * @return the tile this follower is trying to stand on, on the anchor's plane
	 */
	WorldPoint tileFor(WorldPoint anchor, int headingX, int headingY,
		int index, int count, int followDistance)
	{
		Station[] stations = byCount[clamp(count, 1, byCount.length) - 1];
		Station station = stations[clamp(index, 0, stations.length - 1)];

		return station.slot.tileFor(anchor, headingX, headingY,
			followDistance + station.rank - 1);
	}

	/** @return how many stations this formation names for a roster of {@code count} */
	int stationCount(int count)
	{
		return byCount[clamp(count, 1, byCount.length) - 1].length;
	}

	/** @return the largest roster size this formation has a shape for */
	int maxRosterSize()
	{
		return byCount.length;
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}

	private static int clamp(int value, int min, int max)
	{
		return value < min ? min : (value > max ? max : value);
	}

	// --- Table builders -------------------------------------------------------
	//
	// Static methods rather than static fields, because an enum constant's arguments are
	// evaluated before the enum's own static fields exist. They touch nothing but their
	// arguments and one compile-time constant from EntourageSettings.

	/** One station: a direction, and which ring out it sits on. */
	private static Station at(FormationSlot slot, int rank)
	{
		return new Station(slot, rank);
	}

	/** The stations for one roster size, in roster order. */
	private static Station[] shape(Station... stations)
	{
		return stations;
	}

	/** The shapes for roster sizes one upwards, in order. */
	private static Station[][] shapes(Station[]... byCount)
	{
		return byCount;
	}

	/**
	 * A single file in one direction: follower {@code i} on ring {@code i + 1}.
	 *
	 * <p>The one shape that is generated rather than written out, because it is the same
	 * answer at every roster size and a hand-written copy of it five times over would be
	 * five chances to typo a rank.
	 */
	private static Station[][] column(FormationSlot slot)
	{
		Station[][] byCount = new Station[EntourageSettings.MAX_FOLLOWERS][];
		for (int count = 1; count <= byCount.length; count++)
		{
			Station[] stations = new Station[count];
			for (int index = 0; index < count; index++)
			{
				stations[index] = at(slot, index + 1);
			}
			byCount[count - 1] = stations;
		}
		return byCount;
	}

	/** One follower's place in a shape. Immutable; the tables are shared. */
	private static final class Station
	{
		private final FormationSlot slot;

		/** Which ring out: 1 is the configured follow distance, 2 is one tile beyond it. */
		private final int rank;

		private Station(FormationSlot slot, int rank)
		{
			this.slot = slot;
			this.rank = rank;
		}
	}
}
