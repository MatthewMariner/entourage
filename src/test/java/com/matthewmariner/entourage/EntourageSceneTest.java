package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The one place that decides whether the entourage is on screen at all, and which figure
 * it is made of.
 *
 * <p>Every count here comes off {@link FakeClient}'s stand-in for the client's own
 * registered-object list, which is what {@code RuneLiteObject.isActive()} really reads.
 * A test that counted the scene's own bookkeeping would pass a teardown that forgot the
 * objects without deactivating them, which is precisely the leak that matters.
 */
public class EntourageSceneTest
{
	private static final WorldPoint STANDING = new WorldPoint(3221, 3218, 0);

	/** One follower. Written out rather than derived, so growing the roster is a red test. */
	private static final int ROSTER_SIZE = 1;

	private FakeClient client;
	private FakeWorldView view;
	private FakeConfig config;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs();
		view = FakeWorldView.around(STANDING);
		client.setTopLevelWorldView(view);
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING));
		config = new FakeConfig();
	}

	private EntourageScene scene()
	{
		return new EntourageScene(client, config);
	}

	@Test
	public void aResolvedAnchorPutsTheWholeRosterOnScreen()
	{
		EntourageScene scene = scene();

		scene.onGameTick();

		assertEquals(ROSTER_SIZE, client.registeredCount());
		assertEquals(ROSTER_SIZE, scene.getFollowers().size());
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

	// --- The roster is the configured figure ---------------------------------

	@Test
	public void theRosterIsWhicheverFigureTheSettingNames()
	{
		config.setFigure(EntourageFigure.VANNAKA);
		EntourageScene scene = scene();

		scene.onGameTick();

		assertEquals(ROSTER_SIZE, scene.getFollowers().size());
		assertEquals(EntourageFigure.VANNAKA, scene.getFollowers().get(0).getFigure());
		assertTrue("it dressed from Vannaka's own NPC",
			client.npcDefinitionsRequested().contains(EntourageFigure.VANNAKA.getNpcId()));
	}

	/**
	 * <b>Changing the figure has to rebuild, and rebuilding must not leak.</b> The figure
	 * decides which {@code NPCComposition} the model was merged out of, so a new figure is
	 * a new model — and the old {@code RuneLiteObject} has to come off the client's list
	 * before the reference to it is dropped, or it is a figure standing in the world that
	 * nothing owns.
	 */
	@Test
	public void changingTheFigureSwapsTheFollowerWithoutLeavingTheOldOneRegistered()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertEquals(EntourageFigure.ROGUE, scene.getFollowers().get(0).getFigure());
		Follower first = scene.getFollowers().get(0);
		assertEquals(ROSTER_SIZE, client.registeredCount());

		config.setFigure(EntourageFigure.WISE_OLD_MAN);
		scene.onGameTick();

		assertEquals("still exactly one figure on screen", ROSTER_SIZE, client.registeredCount());
		assertEquals(ROSTER_SIZE, scene.getFollowers().size());
		assertNotEquals("and it is a different follower", first, scene.getFollowers().get(0));
		assertEquals(EntourageFigure.WISE_OLD_MAN, scene.getFollowers().get(0).getFigure());
		assertTrue(client.npcDefinitionsRequested()
			.contains(EntourageFigure.WISE_OLD_MAN.getNpcId()));
	}

	/**
	 * The compare is per tick, so a figure that has <i>not</i> changed must not cost a
	 * rebuild: a follower re-merged every 600ms is a model built 100 times a minute for
	 * nothing.
	 */
	@Test
	public void leavingTheFigureAloneRebuildsNothing()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		Follower first = scene.getFollowers().get(0);
		int merges = client.mergeCalls();

		for (int i = 0; i < 50; i++)
		{
			scene.onGameTick();
		}

		assertEquals("the same follower throughout", first, scene.getFollowers().get(0));
		assertEquals("and one model, built once", merges, client.mergeCalls());
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

		assertEquals(ROSTER_SIZE, client.registeredCount());
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

		assertEquals(ROSTER_SIZE, deactivated);
		assertEquals(0, client.registeredCount());
		assertTrue("and nothing is left holding a lit model", scene.getFollowers().isEmpty());
	}

	/**
	 * <b>The leak that lives in the gap between "we tried" and "it worked".</b>
	 * {@code Follower.despawn()} catches its own {@code RuntimeException}, marks the
	 * follower broken and returns {@code false} — so a client that threw out of
	 * {@code removeRuneLiteObject} leaves the object registered, and a teardown that then
	 * cleared its list unconditionally would drop the last reference to it. Nothing could
	 * ever remove it after that short of a client restart, which is the one artefact this
	 * plugin's teardown contract exists to make impossible.
	 *
	 * <p>No realistic in-client trigger for the throw is known. That makes it latent, not
	 * acceptable: the promise is unconditional, so it has to hold against a client that
	 * misbehaves.
	 */
	@Test
	public void aFollowerThatCouldNotBeDeactivatedIsKeptRatherThanLeaked()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		client.refusingDeactivation();
		assertEquals("nothing came off the screen", 0, scene.shutdown());

		assertEquals("the client still has it", ROSTER_SIZE, client.registeredCount());
		assertEquals("so something still holds the reference to it",
			ROSTER_SIZE, scene.getFollowers().size());
	}

	/**
	 * The same promise on the figure-swap path, which is the other caller of the same
	 * retirement. A swap that cleared the list unconditionally would be a second, quieter
	 * way to produce the artefact the teardown contract forbids — and one that a user can
	 * trigger from a dropdown rather than only at shutdown.
	 */
	@Test
	public void aFigureSwapThatCouldNotDeactivateTheOldFollowerKeepsItToo()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		client.refusingDeactivation();
		config.setFigure(EntourageFigure.HANS);
		scene.onGameTick();

		assertEquals("the client still has the old object", ROSTER_SIZE, client.registeredCount());
		assertEquals("so something still holds the reference to it",
			ROSTER_SIZE, scene.getFollowers().size());
		assertEquals("and no second figure was built on top of it",
			EntourageFigure.ROGUE, scene.getFollowers().get(0).getFigure());

		// And it gives up rather than noticing the same mismatch every tick. Without the
		// latch this is a deactivation attempt and a warning 100 times a minute for the
		// rest of the session, which is the shape of "harmless" that fills a log.
		int attempts = client.removalAttempts();
		for (int i = 0; i < 20; i++)
		{
			scene.onGameTick();
		}
		assertEquals("a retirement that could not let go must not retry on every tick",
			attempts, client.removalAttempts());
	}

	/** And a client that will not even say is treated as one that still has it. */
	@Test
	public void aFollowerTheClientWillNotAnswerAboutIsKeptToo()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue(client.registeredCount() > 0);

		client.withThrowingRegistrationChecks();
		scene.shutdown();

		assertEquals("keeping it costs a pointer; dropping it costs a figure nobody can remove",
			ROSTER_SIZE, scene.getFollowers().size());
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

		assertEquals(ROSTER_SIZE, scene.shutdown());
		assertEquals("there is nothing left to deactivate the second time", 0, scene.shutdown());
		assertEquals(0, client.registeredCount());
	}

	/**
	 * A scene that was shut down and then ticked again builds a fresh roster rather than
	 * staying empty — which is what enabling the plugin, disabling it and enabling it
	 * again does.
	 */
	@Test
	public void aSceneThatWasShutDownComesBackOnTheNextTick()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		scene.shutdown();
		assertEquals(0, client.registeredCount());

		scene.onGameTick();

		assertEquals(ROSTER_SIZE, client.registeredCount());
		assertEquals(ROSTER_SIZE, scene.getFollowers().size());
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
		assertEquals(Follower.MAX_ATTEMPTS * ROSTER_SIZE, spent);

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
		config.setCanRun(false);
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
		config.setFormationSlot(FormationSlot.LEFT).setCanRun(false);
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

	/**
	 * The settings really do reach the follower through the scene, rather than the scene
	 * reading a copy of the defaults. Checked on the setting whose effect is largest and
	 * easiest to see from outside: a follower that may run covers two tiles a tick.
	 */
	@Test
	public void theSettingsReachTheFollower()
	{
		config.setFormationSlot(FormationSlot.LEFT);
		EntourageScene scene = scene();
		scene.onGameTick();

		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING.dx(10)));
		scene.onGameTick();

		FollowerWalk walk = scene.getFollowers().get(0).getWalk();
		assertTrue("canRun is on by default", walk.isRunning());
		assertEquals(STANDING.dx(2), walk.currentTile());
	}
}
