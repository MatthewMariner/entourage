package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One figure bound to one {@code RuneLiteObject}: what it does to the client, how often,
 * and what it does when the cache will not give it what it needs.
 *
 * <p>The objects under test are real {@link net.runelite.api.RuneLiteObject}s running
 * their real code — {@code setActive} really does call the client, {@code isActive}
 * really does ask it — so "is it registered?" is a question about the client's own list
 * rather than about this plugin's bookkeeping.
 */
public class FollowerTest
{
	private static final WorldPoint ANCHOR = new WorldPoint(3221, 3218, 0);

	private FakeClient client;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs();
		view = FakeWorldView.around(ANCHOR);
		client.setTopLevelWorldView(view);
	}

	private Follower follower()
	{
		return new Follower(client, EntourageFigure.ROGUE, ANCHOR);
	}

	// --- Coming and going ----------------------------------------------------

	@Test
	public void aTickSpawnsItAndTheClientKnows()
	{
		Follower follower = follower();
		assertFalse("nothing is registered before the first tick", follower.isActive());

		follower.onGameTick(ANCHOR, view);

		assertTrue(follower.isActive());
		assertEquals(1, client.registeredCount());
		assertNotNull("it dressed from the composition", follower.getAppearance());
	}

	@Test
	public void itFormsUpOnTheAnchorRatherThanResumingAStaleTile()
	{
		Follower follower = follower();
		WorldPoint elsewhere = ANCHOR.dx(30).dy(-20);

		follower.onGameTick(elsewhere, view);

		assertEquals("a follower that was not on screen arrives where the player is",
			elsewhere, follower.getWalk().currentTile());
	}

	@Test
	public void despawnDeregistersAndIsIdempotent()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);

		assertTrue(follower.despawn());
		assertEquals(0, client.registeredCount());
		assertFalse("a second despawn has nothing to do", follower.despawn());
	}

	@Test
	public void aFollowerThatNeverSpawnedHasNothingToDespawn()
	{
		assertFalse(follower().despawn());
		assertEquals(0, client.registeredCount());
	}

	@Test
	public void aClientThatWillNotRegisterTheObjectIsLatchedRatherThanRetriedForever()
	{
		client = new FakeClient().withRosterNpcs().refusingRegistration();
		Follower follower = follower();

		follower.onGameTick(ANCHOR, view);
		assertTrue("setActive(true) not taking is a structural failure", follower.isBroken());

		int mergesBefore = client.mergeCalls();
		for (int i = 0; i < 50; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}
		assertEquals("a broken follower must not go on asking the client for anything",
			mergesBefore, client.mergeCalls());
	}

	// --- The animation contract ----------------------------------------------

	/**
	 * The client calls {@code RuneLiteObject.tick(ticksSinceLastFrame)} once per frame
	 * for every registered object, and that is what advances the animation. A second
	 * caller runs every animation at double speed.
	 */
	@Test
	public void thePluginNeverAdvancesTheAnimationItself()
	{
		Follower follower = follower();
		for (int tick = 0; tick < 5; tick++)
		{
			follower.onGameTick(ANCHOR.dx(tick * 3), view);
			for (int frame = 0; frame < 30; frame++)
			{
				follower.advanceFrame(view, frame / 30f);
			}
		}

		assertEquals("the client owns the animation clock", 0, client.lastObject().tickCalls());
	}

	/**
	 * Installing a controller resets its frame to zero — {@code setAnimation} calls
	 * {@code reset()}. So the number of installs has to be the number of idle-to-walk
	 * switches, not the number of game ticks: a controller reinstalled every tick is an
	 * animation that restarts 1.6 times a second, which is what "it needs animation
	 * smoothing" looks like from the outside.
	 */
	@Test
	public void aControllerIsInstalledOncePerSwitchAndNotOncePerTick()
	{
		Follower follower = follower();

		// Arrive, then stand still for a while.
		follower.onGameTick(ANCHOR, view);
		int afterSpawn = client.lastObject().animationControllerInstalls();
		assertEquals("the spawn installs the idle pose", 1, afterSpawn);

		for (int i = 0; i < 10; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}
		assertEquals("ten ticks of standing still install nothing new",
			afterSpawn, client.lastObject().animationControllerInstalls());

		// Walk five tiles.
		WorldPoint far = ANCHOR.dx(6);
		for (int i = 0; i < 5; i++)
		{
			follower.onGameTick(far, view);
			assertTrue("this half of the test needs it actually walking", follower.getWalk().isMoving());
		}
		assertEquals("one switch into the walk, however many tiles it covers",
			afterSpawn + 1, client.lastObject().animationControllerInstalls());

		// Arrive and stand again.
		follower.onGameTick(far, view);
		assertFalse(follower.getWalk().isMoving());
		assertEquals("one switch back to the pose",
			afterSpawn + 2, client.lastObject().animationControllerInstalls());
	}

	@Test
	public void itAsksForBothOfItsFiguresAnimations()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		follower.onGameTick(ANCHOR.dx(6), view);

		assertTrue("the idle pose", client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getIdleAnimation().getId()));
		assertTrue("the walk", client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getWalkAnimation().getId()));
	}

	/**
	 * An {@code AnimationController} built on a null animation is inert forever — its
	 * {@code tick} and {@code loop} both return immediately — so caching one would freeze
	 * the figure for the rest of the session on a single cold-cache miss.
	 */
	@Test
	public void aFailedAnimationLoadIsNotCachedAsAWorkingController()
	{
		client.withUnloadableAnimations(EntourageFigure.ROGUE.getIdleAnimation().getId());
		Follower follower = follower();

		follower.onGameTick(ANCHOR, view);
		assertTrue("a figure with no animation is still better than no figure", follower.isActive());
		assertNull("nothing inert was installed", follower.getInstalledController());

		client.clearUnloadableAnimations();
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS + 2; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}

		assertNotNull("once the cache warms up, the pose arrives", follower.getInstalledController());
	}

	// --- The frame clock -----------------------------------------------------

	@Test
	public void aStandingFollowerIsNotRePlacedEveryFrame()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);

		int afterSpawn = client.lastObject().setLocationCalls();
		follower.advanceFrame(view, 0f);
		int afterFirstFrame = client.lastObject().setLocationCalls();
		assertEquals("the first frame after a tick always places it", afterSpawn + 1, afterFirstFrame);

		for (int frame = 1; frame < 30; frame++)
		{
			follower.advanceFrame(view, frame / 30f);
		}

		assertEquals("setLocation runs Perspective.getTileHeight against the live scene, "
				+ "and a figure standing still has not moved",
			afterFirstFrame, client.lastObject().setLocationCalls());
	}

	@Test
	public void aWalkingFollowerIsRePlacedEveryFrame()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		follower.onGameTick(ANCHOR.dx(6), view);
		assertTrue(follower.getWalk().isMoving());

		int before = client.lastObject().setLocationCalls();
		for (int frame = 0; frame < 30; frame++)
		{
			follower.advanceFrame(view, frame / 30f);
		}

		assertEquals("thirty frames of a step is thirty positions",
			before + 30, client.lastObject().setLocationCalls());
	}

	/**
	 * <b>The sequence no other test plays: stand still, settle, then walk.</b>
	 * {@code aWalkingFollowerIsRePlacedEveryFrame} goes spawn, walk, frames — so the
	 * "this follower has stopped, skip it" flag is still false from the spawn and the
	 * skip is never armed. {@code aStandingFollowerIsNotRePlacedEveryFrame} arms it and
	 * then never walks. Between the two of them, the line that hands the frame pass back
	 * its work at the end of every game tick could be deleted and leave every test that
	 * existed before this one green — while in a live client that is a follower which
	 * freezes, permanently, the first time the player stands still and then moves off.
	 */
	@Test
	public void aFollowerThatStoodStillIsDrawnMovingAgainWhenItWalks()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);

		// Stand still, and let the frame pass settle it — which is the state the next
		// game tick has to undo.
		follower.onGameTick(ANCHOR, view);
		for (int frame = 0; frame < 30; frame++)
		{
			follower.advanceFrame(view, frame / 30f);
		}
		int whileStanding = client.lastObject().setLocationCalls();

		follower.onGameTick(ANCHOR.dx(6), view);
		assertTrue("this test needs it actually walking", follower.getWalk().isMoving());

		follower.advanceFrame(view, 0f);
		int start = follower.getRenderLocation().getX();
		follower.advanceFrame(view, 1f);
		int end = follower.getRenderLocation().getX();

		assertEquals("a game tick has to hand the frame pass its work back",
			whileStanding + 2, client.lastObject().setLocationCalls());
		assertEquals("one tile, in local units", 128, end - start);
	}

	@Test
	public void theDrawnPositionMovesAcrossTheStep()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		follower.onGameTick(ANCHOR.dx(6), view);

		follower.advanceFrame(view, 0f);
		int start = follower.getRenderLocation().getX();
		follower.advanceFrame(view, 1f);
		int end = follower.getRenderLocation().getX();

		assertEquals("one tile, in local units", 128, end - start);
	}

	@Test
	public void aDeactivatedFollowerIsNotMovedByTheFramePass()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		follower.despawn();

		int before = client.lastObject().setLocationCalls();
		for (int frame = 0; frame < 30; frame++)
		{
			follower.advanceFrame(view, frame / 30f);
		}

		assertEquals(before, client.lastObject().setLocationCalls());
	}

	@Test
	public void theObjectIsFacedTheWayTheWalkSaysItIsGoing()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		follower.onGameTick(ANCHOR.dx(6), view);

		assertEquals(StepOrientation.forStep(1, 0), follower.getRenderOrientation());
	}

	/**
	 * <b>Everything else about the model is asserted on the way in.</b> The merge count,
	 * the recolours, the clone-before-recolour order and the lighting are all read off
	 * {@link FakeModelData} — which is to say off the thing that was <i>built</i>, not off
	 * the object that has to draw it. Nothing asked the object what it ended up holding,
	 * and a registered {@code RuneLiteObject} with a null base model is an entourage that
	 * draws nothing at all.
	 */
	@Test
	public void theLitModelReachesTheObject()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);

		assertTrue(follower.isActive());
		assertNotNull("a registered object with no base model is a figure that draws nothing",
			client.lastObject().getBaseModel());
	}

	/**
	 * <b>Every other test in this class runs on plane 0, where the follower's plane and a
	 * hardcoded zero are the same number.</b> They are not the same number upstairs, and
	 * {@code setLocation}'s second argument is the level the client draws the object on —
	 * so a figure that follows you up a staircase and keeps being placed on the ground
	 * floor is a figure drawn through the floor you are standing on.
	 */
	@Test
	public void theObjectIsPlacedOnThePlaneTheFollowerIsOn()
	{
		WorldPoint upstairs = new WorldPoint(ANCHOR.getX(), ANCHOR.getY(), 2);
		FakeWorldView upstairsView = FakeWorldView.around(upstairs);
		client.setTopLevelWorldView(upstairsView);
		Follower follower = new Follower(client, EntourageFigure.ROGUE, upstairs);

		follower.onGameTick(upstairs, upstairsView);

		assertTrue(follower.isActive());
		assertEquals("the spawn places it on its own plane, not on the ground floor",
			2, client.lastObject().getLevel());

		follower.onGameTick(upstairs.dx(6), upstairsView);
		assertTrue("this test needs it actually walking", follower.getWalk().isMoving());
		follower.advanceFrame(upstairsView, 0.5f);

		assertEquals("and so does every frame of the walk", 2, client.lastObject().getLevel());
	}

	// --- A cold cache --------------------------------------------------------

	@Test
	public void aColdCacheDefersTheSpawnWithoutBreakingTheFollower()
	{
		client.setCacheCold(true);
		Follower follower = follower();

		follower.onGameTick(ANCHOR, view);

		assertFalse("nothing to draw yet", follower.isActive());
		assertFalse("but nothing is wrong with it either", follower.isBroken());
		assertEquals(0, client.registeredCount());
	}

	@Test
	public void aColdCacheIsRetriedABoundedNumberOfTimesAndThenLeftAlone()
	{
		client.setCacheCold(true);
		Follower follower = follower();

		for (int i = 0; i < 400; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}

		assertEquals("three attempts a scene load, not one a tick",
			Follower.MAX_ATTEMPTS, client.npcDefinitionsRequested().size());
	}

	@Test
	public void theRetriesAreSpacedRatherThanSpentInThreeConsecutiveTicks()
	{
		client.setCacheCold(true);
		Follower follower = follower();

		follower.onGameTick(ANCHOR, view);
		follower.onGameTick(ANCHOR, view);
		follower.onGameTick(ANCHOR, view);

		assertEquals("a cache that warms up over seconds is not re-tested three times in two",
			1, client.npcDefinitionsRequested().size());
	}

	@Test
	public void aSceneLoadHandsTheBudgetBack()
	{
		client.setCacheCold(true);
		Follower follower = follower();
		for (int i = 0; i < 400; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}
		assertEquals(Follower.MAX_ATTEMPTS, client.npcDefinitionsRequested().size());

		follower.onSceneEntered();
		for (int i = 0; i < 400; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}

		assertEquals("crossing a region border is the right moment to re-test a cold cache",
			Follower.MAX_ATTEMPTS * 2, client.npcDefinitionsRequested().size());
	}

	@Test
	public void aCacheThatWarmsUpProducesTheFigure()
	{
		client.setCacheCold(true);
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		assertFalse(follower.isActive());

		client.setCacheCold(false);
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS + 2; i++)
		{
			follower.onGameTick(ANCHOR, view);
		}

		assertTrue(follower.isActive());
	}

	@Test
	public void aPartialModelLoadIsNotSpawnedAsAFigureWithMissingParts()
	{
		client = new FakeClient()
			.withNpc(EntourageFigure.ROGUE.getNpcId(), FakeNpcComposition.of("Rogue", 11, 22, 33))
			.withUnloadableModels(22);
		client.setTopLevelWorldView(view);
		Follower follower = follower();

		follower.onGameTick(ANCHOR, view);

		assertFalse("a figure with no torso is worse than no figure", follower.isActive());
		assertFalse(follower.isBroken());
		assertEquals("and nothing half-built was merged", 0, client.mergeCalls());
	}

	// --- Dressing ------------------------------------------------------------

	@Test
	public void everyModelPartTheCompositionNamesIsMerged()
	{
		client = new FakeClient()
			.withNpc(EntourageFigure.ROGUE.getNpcId(), FakeNpcComposition.of("Rogue", 11, 22, 33));
		client.setTopLevelWorldView(view);

		follower().onGameTick(ANCHOR, view);

		assertEquals(1, client.mergeCalls());
		assertEquals(3, client.lastMergePartCount());
	}

	/**
	 * {@code ModelData.recolor}'s own javadoc says to call {@code cloneColors()} first.
	 * "mergeModels hands back a fresh instance" is an observation about an obfuscated
	 * constructor, not a contract — and repainting a shared {@code faceColors} array
	 * would repaint every instance of that model in the world, through the client's own
	 * cache.
	 */
	@Test
	public void theCompositionsColoursAreAppliedAndOnlyAfterTheColoursAreCloned()
	{
		client = new FakeClient().withNpc(EntourageFigure.ROGUE.getNpcId(),
			FakeNpcComposition.recoloured("Rogue", new int[]{11},
				new short[]{4550, 900}, new short[]{100, 200}));
		client.setTopLevelWorldView(view);

		follower().onGameTick(ANCHOR, view);

		FakeModelData merged = client.lastMerged();
		assertNotNull(merged);
		assertTrue("cloneColors() first", merged.colorsCloned());
		assertFalse("and never a recolour before it", merged.recolouredBeforeClone());
		assertEquals(2, merged.recolours().size());
		assertEquals(4550, merged.recolours().get(0)[0]);
		assertEquals(100, merged.recolours().get(0)[1]);
		assertTrue("and it was lit", merged.wasLit());
	}

	@Test
	public void aFigureWithNoRecoloursIsNotCloned()
	{
		follower().onGameTick(ANCHOR, view);

		FakeModelData merged = client.lastMerged();
		assertNotNull(merged);
		assertFalse("cloning colours nothing is going to repaint is wasted work",
			merged.colorsCloned());
		assertTrue(merged.wasLit());
	}

	@Test
	public void theModelIsBuiltOnceAndKeptAcrossADespawn()
	{
		Follower follower = follower();
		follower.onGameTick(ANCHOR, view);
		follower.despawn();
		follower.onGameTick(ANCHOR, view);

		assertTrue(follower.isActive());
		assertEquals("walking out of the world and back is an activate, not a rebuild",
			1, client.mergeCalls());
	}
}
