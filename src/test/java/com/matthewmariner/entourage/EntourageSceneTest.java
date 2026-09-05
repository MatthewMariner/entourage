package com.matthewmariner.entourage;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
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

	/**
	 * What a fresh install spawns: one follower. Written out rather than derived, so a
	 * shipped default that grew the roster is a red test rather than a silent change.
	 */
	private static final int ROSTER_SIZE = 1;

	/**
	 * The full roster the owner asked for: five. A literal for the same reason —
	 * {@code MAX_FOLLOWERS} would follow the constant anywhere, including to a cap somebody
	 * raised without meaning to.
	 */
	private static final int FULL_ROSTER = 5;

	/** Five different figures, so a test that loses one can tell which. */
	private static final EntourageFigure[] FIVE = {
		EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.VANNAKA,
		EntourageFigure.PIRATE, EntourageFigure.TURAEL
	};

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

	// --- The roster is the configured figures --------------------------------

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
	 * <b>Five figures, five objects on the client's own list, in the order the settings
	 * name them.</b> The order is not decoration: a follower's index is what
	 * {@link EntourageFormation} turns into a station, so a roster built out of order is a
	 * formation with the wrong people in the wrong places.
	 */
	@Test
	public void afullRosterPutsFiveDifferentFiguresOnScreenInOrder()
	{
		config.setRoster(FIVE);
		EntourageScene scene = scene();

		scene.onGameTick();

		assertEquals(FULL_ROSTER, client.registeredCount());
		assertEquals(FULL_ROSTER, scene.getFollowers().size());

		for (int index = 0; index < FULL_ROSTER; index++)
		{
			Follower follower = scene.getFollowers().get(index);
			assertEquals("slot " + index + " wears the wrong body", FIVE[index], follower.getFigure());
			assertEquals("slot " + index + " was numbered wrong", index, follower.getIndex());
			assertTrue("slot " + index + " never dressed from its own NPC",
				client.npcDefinitionsRequested().contains(FIVE[index].getNpcId()));
		}
	}

	/**
	 * <b>Five followers form up on five different tiles, and stay on them as the player
	 * moves.</b> This is the whole feature stated end to end: pick a formation, pick five
	 * figures, walk, and there are still five figures each on its own tile.
	 *
	 * <p>The tiles are compared against each other rather than against a table of expected
	 * ones — that table is {@code EntourageFormationTest}'s job — so this cannot pass by
	 * agreeing with the same arithmetic it is checking.
	 */
	@Test
	public void aFullRosterInAFormationSettlesOnFiveDistinctTilesAndStaysThere()
	{
		config.setRoster(FIVE).setFormation(EntourageFormation.HANGOUT);
		EntourageScene scene = scene();

		// Walk east for a while, then stand still long enough for everybody to arrive.
		WorldPoint player = STANDING;
		for (int step = 0; step < 8; step++)
		{
			client.setLocalPlayer(FakePlayer.standingOn(view, player));
			scene.onGameTick();
			player = player.dx(1);
		}

		WorldPoint standing = player.dx(-1);
		for (int settle = 0; settle < 10; settle++)
		{
			scene.onGameTick();
		}

		Set<WorldPoint> tiles = new HashSet<>();
		for (Follower follower : scene.getFollowers())
		{
			WorldPoint tile = follower.getWalk().currentTile();

			assertTrue("two followers settled on " + tile, tiles.add(tile));
			assertNotEquals("a follower settled on the player's own tile", standing, tile);
			assertFalse("and it is still walking, so it has not settled anywhere",
				follower.getWalk().isMoving());
		}

		assertEquals(FULL_ROSTER, tiles.size());
		assertEquals("everybody is still on screen", FULL_ROSTER, client.registeredCount());
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
	 * <b>Turning the count up is the same rebuild, and it must not leak either.</b> The
	 * roster is compared as a whole list, so this is the case a comparison that only
	 * watched the first slot would miss entirely.
	 */
	@Test
	public void changingTheRosterSizeRebuildsWithoutLeavingTheOldFollowersRegistered()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		assertEquals(ROSTER_SIZE, client.registeredCount());

		config.setRoster(FIVE);
		scene.onGameTick();

		assertEquals(FULL_ROSTER, client.registeredCount());
		assertEquals(FULL_ROSTER, scene.getFollowers().size());

		config.setRoster(EntourageFigure.HANS, EntourageFigure.VANNAKA);
		scene.onGameTick();

		assertEquals("four objects were left registered by the shrink", 2, client.registeredCount());
		assertEquals(2, scene.getFollowers().size());
		assertEquals(EntourageFigure.HANS, scene.getFollowers().get(0).getFigure());
		assertEquals(EntourageFigure.VANNAKA, scene.getFollowers().get(1).getFigure());
	}

	/**
	 * <b>The same two figures in the other order is a different roster.</b> A follower's
	 * index decides which station of the formation it stands on, so swapping two slots has
	 * to move the bodies — and a comparison that only counted, or only looked at the set of
	 * figures, would leave them where they were.
	 */
	@Test
	public void swappingTwoFiguresRoundRebuildsTheRoster()
	{
		config.setRoster(EntourageFigure.HANS, EntourageFigure.VANNAKA);
		EntourageScene scene = scene();
		scene.onGameTick();
		Follower first = scene.getFollowers().get(0);

		config.setRoster(EntourageFigure.VANNAKA, EntourageFigure.HANS);
		scene.onGameTick();

		assertEquals("still two figures on screen", 2, client.registeredCount());
		assertNotEquals("and they really were rebuilt", first, scene.getFollowers().get(0));
		assertEquals(EntourageFigure.VANNAKA, scene.getFollowers().get(0).getFigure());
		assertEquals(EntourageFigure.HANS, scene.getFollowers().get(1).getFigure());
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

	// --- Instances -----------------------------------------------------------

	/**
	 * A raid, a quest cutscene, the Inferno. Deactivating rather than merely not drawing,
	 * for the same reason the no-anchor branch does: an object left registered is a figure
	 * standing in an instance that this pass has stopped looking after.
	 */
	@Test
	public void insideAnInstanceNothingIsDrawnWhenTheSettingIsOn()
	{
		config.setHideInInstances(true);
		EntourageScene scene = scene();
		scene.onGameTick();
		assertTrue("it has to be on screen before hiding it means anything",
			client.registeredCount() > 0);

		view.asInstance();
		scene.onGameTick();

		assertEquals(0, client.registeredCount());
	}

	/**
	 * <b>And off by default, which is the shipped behaviour and not an accident.</b> A
	 * Player Owned House is an instance too, and it is where a cosmetic follower is most
	 * wanted — so the fixture below is the same instance with the setting left alone.
	 */
	@Test
	public void insideAnInstanceTheFollowerStaysUnlessAskedToGo()
	{
		EntourageScene scene = scene();
		view.asInstance();

		scene.onGameTick();

		assertEquals("the plugin has always worked in instances and still does",
			ROSTER_SIZE, client.registeredCount());
	}

	@Test
	public void aFollowerHiddenInAnInstanceStopsTalkingToo()
	{
		config.setHideInInstances(true);
		EntourageScene scene = scene();
		scene.onGameTick();

		Follower follower = scene.getFollowers().get(0);
		follower.getRemarks().say(0, Integer.MAX_VALUE, FigureLines.of(follower.getFigure()));
		assertTrue("the fixture has to be talking first", follower.getRemarks().isTalking());

		view.asInstance();
		scene.onGameTick();

		assertFalse("a line left up is text drawn over an empty tile",
			follower.getRemarks().isTalking());
	}

	// --- Dialogue ------------------------------------------------------------

	/**
	 * The chatter is driven by the scene's own tick, so a follower that is on screen
	 * eventually says one of its lines without anything else being wired up.
	 */
	@Test
	public void theSceneDrivesTheChatter()
	{
		EntourageScene scene = scene();
		scene.onGameTick();
		Follower follower = scene.getFollowers().get(0);

		String said = null;
		for (int tick = 0; tick < EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS + 1
			&& said == null; tick++)
		{
			scene.onGameTick();
			said = follower.getRemarks().text();
		}

		assertNotNull("nothing drives the chatter", said);
		assertTrue("and it is one of the figure's own lines",
			FigureLines.of(follower.getFigure()).contains(said));
	}

	/**
	 * A scene load empties the line as well as the screen, and restarts the cadence.
	 *
	 * <p><b>The clock is the half that needs asking about.</b> The line is cleared by the
	 * despawn the invalidation already does — {@code Follower.despawn()} clears its own —
	 * so an invalidation that forgot the chatter entirely would still leave nothing on
	 * screen and pass an assertion about the text. What it would leave is a follower
	 * inheriting a phase from the world it was in before, so that it either speaks the
	 * instant it arrives in the next one or waits out most of an interval it already
	 * spent. A mutation pass deleting the reset went green until this asked.
	 */
	@Test
	public void invalidateStopsTheTalkingAndRestartsTheCadence()
	{
		EntourageScene scene = scene();
		for (int tick = 0; tick < 5; tick++)
		{
			scene.onGameTick();
		}
		assertTrue("the fixture has to have run the cadence first", scene.getChatter().getTick() > 0);

		Follower follower = scene.getFollowers().get(0);
		follower.getRemarks().say(0, Integer.MAX_VALUE, FigureLines.of(follower.getFigure()));

		scene.invalidate("LOADING");

		assertFalse(follower.getRemarks().isTalking());
		assertEquals("a fresh world starts a fresh phase", 0, scene.getChatter().getTick());
	}

	/** And so does a teardown, for the same reason. */
	@Test
	public void shutdownRestartsTheCadenceToo()
	{
		EntourageScene scene = scene();
		for (int tick = 0; tick < 5; tick++)
		{
			scene.onGameTick();
		}
		assertTrue(scene.getChatter().getTick() > 0);

		scene.shutdown();

		assertEquals(0, scene.getChatter().getTick());
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
	 * <b>The same promise across a full roster, which is the one that matters now.</b> A
	 * teardown that deactivated the first follower and stopped would pass the test above
	 * unchanged — one figure in, one figure out — and leave four standing in the world with
	 * nothing owning them. The count is a literal for the same reason
	 * {@link #FULL_ROSTER} is.
	 */
	@Test
	public void shutdownLeavesZeroRegisteredObjectsWithAFullRoster()
	{
		config.setRoster(FIVE);
		EntourageScene scene = scene();
		scene.onGameTick();
		assertEquals("all five have to be there before the teardown means anything",
			FULL_ROSTER, client.registeredCount());

		int deactivated = scene.shutdown();

		assertEquals("every one of them came off the client's own list", 5, deactivated);
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
		config.setFormation(EntourageFormation.LEFT).setCanRun(false);
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
		config.setFormation(EntourageFormation.LEFT);
		EntourageScene scene = scene();
		scene.onGameTick();

		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING.dx(10)));
		scene.onGameTick();

		FollowerWalk walk = scene.getFollowers().get(0).getWalk();
		assertTrue("canRun is on by default", walk.isRunning());
		assertEquals(STANDING.dx(2), walk.currentTile());
	}
}
