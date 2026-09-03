package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which of the player's two positions the entourage forms up on, and what happens when
 * there isn't one.
 */
public class FollowerAnchorTest
{
	private static final WorldPoint STANDING = new WorldPoint(3221, 3218, 0);

	/** See {@link FakeWorldView#rectangular} for why a scene no client builds earns its place. */
	private static final int RECT_BASE_X = 3200;
	private static final int RECT_BASE_Y = 3072;
	private static final int RECT_SIZE_X = 104;
	private static final int RECT_SIZE_Y = 72;

	private static FakeWorldView rectangular()
	{
		return FakeWorldView.rectangular(RECT_BASE_X, RECT_BASE_Y, RECT_SIZE_X, RECT_SIZE_Y, 0);
	}

	@Test
	public void aStationaryPlayerAnchorsOnTheTileHeIsStandingOn()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		FollowerAnchor anchor = FollowerAnchor.of(FakePlayer.standingOn(view, STANDING), view);

		assertTrue(anchor.isResolved());
		assertEquals(FollowerAnchor.Resolution.RESOLVED, anchor.getResolution());
		assertEquals(STANDING, anchor.getTile());
	}

	/**
	 * The headline case, and the reason this class exists.
	 *
	 * <p>The player is one fifth of the way through a two-tile run. The client has
	 * already put the destination tile at the head of the movement queue, so
	 * {@code getWorldLocation()} reads as two tiles further north than the player is
	 * drawn. Anchoring on that would put the entourage two tiles ahead of the person it
	 * is following, for the whole of every step, the entire time anybody is running.
	 */
	@Test
	public void aRunningPlayerAnchorsWhereHeIsDrawnAndNotWhereTheServerHasHim()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		WorldPoint serverTile = STANDING.dy(2);
		FakePlayer player = FakePlayer.partWayFrom(view, STANDING, serverTile, 0.2f);

		FollowerAnchor anchor = FollowerAnchor.of(player, view);

		assertTrue(anchor.isResolved());
		assertEquals("the anchor is the tile the player is drawn in", STANDING, anchor.getTile());
		assertNotEquals("which is not the tile the server has him on", serverTile, anchor.getTile());
		assertEquals("two tiles apart, which is what a run costs", 2, serverTile.distanceTo(STANDING));
	}

	/**
	 * The stronger version of the same claim. The test above would still pass if the
	 * anchor read {@code getWorldLocation()} and then corrected it somehow; this one
	 * says it never asks.
	 */
	@Test
	public void theAnchorNeverReadsTheServerTileAtAll()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		FakePlayer player = FakePlayer.partWayFrom(view, STANDING, STANDING.dy(2), 0.2f);

		FollowerAnchor.of(player, view);

		assertEquals("getWorldLocation() is the wrong field and must not be consulted",
			0, player.worldLocationReads());
	}

	@Test
	public void theAnchorCrossesTheTileBoundaryWhereTheDrawnPositionDoes()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		WorldPoint destination = STANDING.dy(1);

		assertEquals("just before half way it is still on the tile it left", STANDING,
			FollowerAnchor.of(FakePlayer.partWayFrom(view, STANDING, destination, 0.4f), view).getTile());
		assertEquals("just after half way it is on the tile it is walking to", destination,
			FollowerAnchor.of(FakePlayer.partWayFrom(view, STANDING, destination, 0.6f), view).getTile());
	}

	@Test
	public void thePlaneComesFromTheView()
	{
		WorldPoint upstairs = new WorldPoint(3221, 3218, 2);
		FakeWorldView view = FakeWorldView.around(upstairs);
		FollowerAnchor anchor = FollowerAnchor.of(FakePlayer.standingOn(view, upstairs), view);

		assertEquals(2, anchor.getTile().getPlane());
	}

	// --- The four ways of not having an anchor -------------------------------

	@Test
	public void noPlayerIsNamedRatherThanNull()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);

		assertUnresolved(FollowerAnchor.Resolution.NO_PLAYER, FollowerAnchor.of(null, view));
		assertUnresolved(FollowerAnchor.Resolution.NO_PLAYER,
			FollowerAnchor.of(FakePlayer.standingOn(view, STANDING), null));
		assertUnresolved(FollowerAnchor.Resolution.NO_PLAYER, FollowerAnchor.of(null, null));
	}

	/**
	 * A player on a boat is in a {@code WorldEntity}'s world view. This plugin refuses
	 * to draw there rather than drawing there badly — the collision map for such a view
	 * cannot be read with scene coordinates, so a follower would walk through the
	 * gunwale.
	 */
	@Test
	public void aPlayerInsideAWorldEntityIsRefusedRatherThanFollowedBadly()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		assertUnresolved(FollowerAnchor.Resolution.FOREIGN_WORLD_VIEW,
			FollowerAnchor.of(FakePlayer.inForeignWorldView(view, STANDING, 7), view));
	}

	@Test
	public void aRenderPositionOffTheSceneIsRefused()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		assertUnresolved(FollowerAnchor.Resolution.OUTSIDE_SCENE,
			FollowerAnchor.of(FakePlayer.outsideTheScene(view, STANDING), view));
	}

	// --- The two axes are not interchangeable --------------------------------

	/**
	 * Every test above runs on {@link FakeWorldView#around}, which centres a square
	 * scene — and the client's centring arithmetic gives a tile near {@code (3221, 3218)}
	 * a base of 3168 on <i>both</i> axes. On that fixture {@code getBaseX()} and
	 * {@code getBaseY()} are the same number, so a tile built out of them the wrong way
	 * round comes out identical and nothing here could tell. This one has bases that
	 * differ.
	 */
	@Test
	public void theTileTakesEachCoordinateFromItsOwnBase()
	{
		FakeWorldView view = rectangular();
		FollowerAnchor anchor = FollowerAnchor.of(FakePlayer.atSceneTile(view, 90, 20), view);

		assertTrue(anchor.isResolved());
		assertEquals(new WorldPoint(RECT_BASE_X + 90, RECT_BASE_Y + 20, 0), anchor.getTile());
	}

	/**
	 * And the same for the bounds check. A square scene cannot tell {@code getSizeX()}
	 * from {@code getSizeY()}; a rectangular one refuses in one direction and resolves in
	 * the other, so both halves of the comparison have to be on their own axis.
	 */
	@Test
	public void eachSceneCoordinateIsBoundedByItsOwnAxis()
	{
		FakeWorldView view = rectangular();

		assertTrue("a scene x of 90 is inside the long axis",
			FollowerAnchor.of(FakePlayer.atSceneTile(view, 90, 20), view).isResolved());
		assertUnresolved("a scene y of 90, off the end of the short axis,",
			FollowerAnchor.Resolution.OUTSIDE_SCENE,
			FollowerAnchor.of(FakePlayer.atSceneTile(view, 20, 90), view));

		assertTrue("the last row still counts as inside it",
			FollowerAnchor.of(FakePlayer.atSceneTile(view, 20, RECT_SIZE_Y - 1), view).isResolved());
		assertUnresolved("the row past the last one",
			FollowerAnchor.Resolution.OUTSIDE_SCENE,
			FollowerAnchor.of(FakePlayer.atSceneTile(view, 20, RECT_SIZE_Y), view));
	}

	@Test
	public void anUnresolvedAnchorCarriesNoTile()
	{
		assertNull(FollowerAnchor.of(null, null).getTile());
	}

	@Test
	public void toStringSaysWhichItIs()
	{
		FakeWorldView view = FakeWorldView.around(STANDING);
		assertTrue(FollowerAnchor.of(FakePlayer.standingOn(view, STANDING), view)
			.toString().contains("3221"));
		assertTrue(FollowerAnchor.of(null, null).toString().contains("NO_PLAYER"));
	}

	private static void assertUnresolved(FollowerAnchor.Resolution expected, FollowerAnchor anchor)
	{
		assertUnresolved("this anchor", expected, anchor);
	}

	private static void assertUnresolved(
		String what, FollowerAnchor.Resolution expected, FollowerAnchor anchor)
	{
		assertFalse(what + " must not resolve", anchor.isResolved());
		assertEquals(what, expected, anchor.getResolution());
		assertNull("an unresolved anchor must not hand out a tile", anchor.getTile());
	}
}
