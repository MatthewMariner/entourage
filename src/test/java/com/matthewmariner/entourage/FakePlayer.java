package com.matthewmariner.entourage;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * A local player that is somewhere.
 *
 * <p>Two constructors on purpose, and the difference between them is the whole point of
 * {@link FollowerAnchor}:
 *
 * <ul>
 *   <li>{@link #standingOn} puts the render position at the centre of a tile, which is
 *       where a stationary player is drawn;</li>
 *   <li>{@link #partWayFrom} puts it part-way between two tiles <i>and</i> lets the
 *       test set a different {@code getWorldLocation()}, which is what the client does
 *       for the whole of every step: the world location comes off the head of the
 *       movement queue, the render position off the actor's own coordinates.</li>
 * </ul>
 *
 * A fake that could only do the first would make an anchor built from the wrong field
 * indistinguishable from one built from the right one.
 */
final class FakePlayer extends StubPlayer
{
	private final LocalPoint localLocation;
	private final WorldPoint worldLocation;

	private int worldLocationReads;

	private FakePlayer(LocalPoint localLocation, WorldPoint worldLocation)
	{
		this.localLocation = localLocation;
		this.worldLocation = worldLocation;
	}

	/** A player drawn in the middle of a tile, with a matching world location. */
	static FakePlayer standingOn(WorldView view, WorldPoint tile)
	{
		return new FakePlayer(centreOf(view, tile), tile);
	}

	/**
	 * A player drawn {@code fraction} of the way from one tile to the next, whose
	 * {@code getWorldLocation()} already reads as the tile it is heading for — the
	 * client's own behaviour while an actor is moving.
	 */
	static FakePlayer partWayFrom(WorldView view, WorldPoint from, WorldPoint to, float fraction)
	{
		LocalPoint a = centreOf(view, from);
		LocalPoint b = centreOf(view, to);
		LocalPoint drawn = new LocalPoint(
			a.getX() + Math.round((b.getX() - a.getX()) * fraction),
			a.getY() + Math.round((b.getY() - a.getY()) * fraction),
			view);
		return new FakePlayer(drawn, to);
	}

	/** A player whose render position carries a world view id nobody else is holding. */
	static FakePlayer inForeignWorldView(WorldView view, WorldPoint tile, int foreignViewId)
	{
		LocalPoint centre = centreOf(view, tile);
		return new FakePlayer(
			new LocalPoint(centre.getX(), centre.getY(), foreignViewId), tile);
	}

	/** A player whose render position is off the edge of the loaded scene. */
	static FakePlayer outsideTheScene(WorldView view, WorldPoint claimedTile)
	{
		return new FakePlayer(new LocalPoint(-1, -1, view), claimedTile);
	}

	private static LocalPoint centreOf(WorldView view, WorldPoint tile)
	{
		LocalPoint point = LocalPoint.fromWorld(view, tile);
		if (point == null)
		{
			throw new IllegalArgumentException("tile " + tile + " is not in the fake scene");
		}
		return point;
	}

	@Override
	public LocalPoint getLocalLocation()
	{
		return localLocation;
	}

	/**
	 * The server tile — the head of the movement queue, which is what the injected
	 * client builds this from.
	 *
	 * <p>Answered rather than thrown, and counted. {@code StubPlayer} would throw here,
	 * and a throw would make "the anchor never asked" and "the anchor asked and happened
	 * to get the right answer" the same observation, which is exactly the distinction
	 * {@code FollowerAnchorTest} exists to make.
	 */
	@Override
	public WorldPoint getWorldLocation()
	{
		worldLocationReads++;
		return worldLocation;
	}

	/** @return how many times anything asked for the server tile */
	int worldLocationReads()
	{
		return worldLocationReads;
	}
}
