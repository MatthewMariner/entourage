package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The one place that decides whether the entourage is on screen at all.
 *
 * <p>Every count here comes off {@link FakeClient}'s stand-in for the client's own
 * registered-object list, which is what {@code RuneLiteObject.isActive()} really reads.
 * A test that counted the scene's own bookkeeping would pass a teardown that forgot the
 * objects without deactivating them, which is precisely the leak that matters.
 */
public class EntourageSceneTest
{
	private static final WorldPoint STANDING = new WorldPoint(3221, 3218, 0);

	private FakeClient client;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs();
		view = FakeWorldView.around(STANDING);
		client.setTopLevelWorldView(view);
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING));
	}

	private EntourageScene scene()
	{
		return new EntourageScene(client);
	}

	@Test
	public void aResolvedAnchorPutsTheWholeRosterOnScreen()
	{
		EntourageScene scene = scene();

		scene.onGameTick();

		assertEquals(EntourageFigure.DEFAULT_ROSTER.size(), client.registeredCount());
		assertEquals(EntourageFigure.DEFAULT_ROSTER.size(), scene.getFollowers().size());
	}

	@Test
	public void theEntourageFormsUpOnThePlayerRatherThanWhereverItWasBuilt()
	{
		EntourageScene scene = scene();
		scene.onGameTick();

		for (Follower follower : scene.getFollowers())
		{
			assertEquals(STANDING, follower.getWalk().currentTile());
		}
	}

	// --- The UNKNOWN branch --------------------------------------------------

	@Test
	public void nothingIsDrawnBeforeThereIsAPlayer()
	{
		client.setLocalPlayer(null);
		EntourageScene scene = scene();

		scene.onGameTick();

		assertEquals(0, client.registeredCount());
	}

	@Test
	public void nothingIsDrawnWithoutATopLevelView()
	{
		client.setTopLevelWorldView(null);
		scene().onGameTick();

		assertEquals(0, client.registeredCount());
	}

	/**
	 * The state that actually happens: the player walks onto a boat, and the anchor stops
	 * resolving. Leaving the figures registered would leave them standing at a
	 * {@code LocalPoint} that now addresses somewhere else.
	 */
	@Test
	public void anAnchorGoingAwayTakesTheEntourageOffTheScreen()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		client.setLocalPlayer(FakePlayer.inForeignWorldView(view, STANDING, 7));
		scene.onGameTick();

		assertEquals("not frozen in place — not drawn", 0, client.registeredCount());
	}

	@Test
	public void anAnchorComingBackPutsThemBackWithoutRebuildingTheModel()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		int mergesAfterFirstSpawn = client.mergeCalls();

		client.setLocalPlayer(null);
		scene.onGameTick();
		assertEquals(0, client.registeredCount());

		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING));
		scene.onGameTick();

		assertEquals(EntourageFigure.DEFAULT_ROSTER.size(), client.registeredCount());
		assertEquals("coming back is an activate, not a rebuild",
			mergesAfterFirstSpawn, client.mergeCalls());
	}

	// --- Teardown ------------------------------------------------------------

	/**
	 * The non-negotiable. A leaked {@code RuneLiteObject} is a figure standing in the
	 * world that nothing owns and nothing short of a client restart can remove.
	 */
	@Test
	public void shutdownLeavesZeroRegisteredObjects()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue("the state has to be there before the teardown means anything",
			client.registeredCount() > 0);

		int deactivated = scene.shutdown();

		assertEquals(EntourageFigure.DEFAULT_ROSTER.size(), deactivated);
		assertEquals(0, client.registeredCount());
		assertTrue("and nothing is left holding a lit model", scene.getFollowers().isEmpty());
	}

	@Test
	public void shutdownOnASceneThatNeverRanIsHarmless()
	{
		assertEquals(0, scene().shutdown());
		assertEquals(0, client.registeredCount());
	}

	@Test
	public void shutdownIsIdempotent()
	{
		EntourageScene scene = scene();
		scene.onGameTick();

		assertEquals(EntourageFigure.DEFAULT_ROSTER.size(), scene.shutdown());
		assertEquals("there is nothing left to deactivate the second time", 0, scene.shutdown());
		assertEquals(0, client.registeredCount());
	}

	@Test
	public void invalidateDeactivatesAndHandsTheRetryBudgetBack()
	{
		client.setCacheCold(true);
		EntourageScene scene = scene();

		for (int i = 0; i < 400; i++)
		{
			scene.onGameTick();
		}
		int spent = client.npcDefinitionsRequested().size();
		assertEquals(Follower.MAX_ATTEMPTS * EntourageFigure.DEFAULT_ROSTER.size(), spent);

		scene.invalidate("LOADING");
		for (int i = 0; i < 400; i++)
		{
			scene.onGameTick();
		}

		assertEquals("a scene load is the right moment to re-test a cold cache",
			spent * 2, client.npcDefinitionsRequested().size());
	}

	@Test
	public void invalidateTakesTheEntourageOffTheScreen()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		scene.invalidate("HOPPING");

		assertEquals(0, client.registeredCount());
	}

	// --- One follower's bad luck costs that follower -------------------------

	/**
	 * {@code new AnimationController(client, id)} calls {@code client.loadAnimation}
	 * straight through, so a cache that throws reaches {@code Follower.onGameTick} from
	 * outside its own try/catch. The pass has to survive it, and the follower has to be
	 * latched rather than throwing again on every tick for the rest of the session.
	 */
	@Test
	public void aFollowerThatThrowsIsDeactivatedAndLatchedRatherThanTakingThePassDown()
	{
		client.withThrowingAnimations(EntourageFigure.ROGUE.getWalkAnimation().getId());
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		// Walking is what asks for the animation that blows up.
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING.dx(6)));
		scene.onGameTick();

		assertEquals("it is off the screen rather than half-drawn", 0, client.registeredCount());
		for (Follower follower : scene.getFollowers())
		{
			assertTrue(follower.isBroken());
		}

		int animationsAsked = client.animationsLoaded().size();
		for (int i = 0; i < 20; i++)
		{
			scene.onGameTick();
		}
		assertEquals("a broken follower asks for nothing else",
			animationsAsked, client.animationsLoaded().size());
	}

	@Test
	public void aFramePassThatThrowsIsSurvivedTheSameWay()
	{
		EntourageScene scene = scene();
		scene.onGameTick();

		// Start a step, so the frame pass has something to do.
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING.dx(6)));
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		client.withThrowingWorldViewLookup();
		scene.onFrame(view, 0.5f);

		assertEquals(0, client.registeredCount());
		for (Follower follower : scene.getFollowers())
		{
			assertTrue(follower.isBroken());
		}
	}

	@Test
	public void theFramePassDoesNotSpawnAnything()
	{
		EntourageScene scene = scene();

		scene.onFrame(view, 0.5f);

		assertEquals("spawning is game-tick work; the frame clock only interpolates",
			0, client.registeredCount());
		assertTrue(scene.getFollowers().isEmpty());
	}

	@Test
	public void aSceneThatNeverSawAPlayerAllocatesNoFollowers()
	{
		client.setLocalPlayer(null);
		EntourageScene scene = scene();

		scene.onGameTick();

		assertTrue(scene.getFollowers().isEmpty());
	}

	@Test
	public void aWalkingEntourageIsMovedByTheFramePass()
	{
		EntourageScene scene = scene();
		scene.onGameTick();

		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING.dx(6)));
		scene.onGameTick();
		assertFalse(scene.getFollowers().isEmpty());
		assertTrue(scene.getFollowers().get(0).getWalk().isMoving());

		scene.onFrame(view, 0f);
		int start = scene.getFollowers().get(0).getRenderLocation().getX();
		scene.onFrame(view, 1f);
		int end = scene.getFollowers().get(0).getRenderLocation().getX();

		assertEquals("one tile in local units, spread over the frames of one game tick",
			128, end - start);
	}
}
