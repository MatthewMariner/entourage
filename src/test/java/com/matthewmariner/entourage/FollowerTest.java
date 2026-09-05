package com.matthewmariner.entourage;

import java.util.Arrays;
import net.runelite.api.AnimationController;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * One figure bound to one {@code RuneLiteObject}: what it does to the client, how often,
 * and what it does when the cache will not give it what it needs.
 *
 * <p>The objects under test are real {@link net.runelite.api.RuneLiteObject}s running
 * their real code — {@code setActive} really does call the client, {@code isActive}
 * really does ask it — so "is it registered?" is a question about the client's own list
 * rather than about this plugin's bookkeeping.
 *
 * <p><b>{@link #settled} is the fixture most of these use, and its shape is not
 * arbitrary.</b> A follower always spawns on the player's own tile and always steps off
 * it, and its slot is measured against the direction the player last travelled — which
 * a player who has never moved does not have. So the fixture spends four ticks getting
 * the follower to a known standstill with a known heading: the player ends up one tile
 * east of the follower, having walked east to get there, so {@link FormationSlot#BEHIND}
 * puts the slot on the follower's own row. Moving the player further east from there is
 * a walk due east, one tile a tick, which is what keeps every "one tile, in local units"
 * assertion below about the frame pass rather than about whichever diagonal the geometry
 * happened to produce.
 */
public class FollowerTest
{
	/** Where the follower ends up standing, once {@link #settled} has run. */
	private static final WorldPoint ANCHOR = new WorldPoint(3221, 3218, 0);

	/** Where the player ends up standing: one tile east, having walked east. */
	private static final WorldPoint PLAYER = ANCHOR.dx(1);

	/**
	 * A player further east again, close enough not to trip the recall. The slot is one
	 * west of it, which is due east of the follower.
	 */
	private static final WorldPoint EAST = ANCHOR.dx(7);

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
		return new Follower(client, FollowerBody.preset(EntourageFigure.ROGUE), 0, ANCHOR);
	}

	/**
	 * The player standing on a tile, as the anchor a tick pass takes.
	 *
	 * <p>Built through the real {@link FollowerAnchor#of} rather than by hand, so every
	 * tick in this class goes down the same path the plugin does — including the
	 * render-position arithmetic that turns a {@code LocalPoint} back into a tile. The
	 * resolution is asserted because an anchor that quietly failed to resolve would make
	 * every follower in the test form up on {@code null}.
	 */
	private FollowerAnchor at(WorldPoint tile)
	{
		return at(tile, view);
	}

	private FollowerAnchor at(WorldPoint tile, FakeWorldView worldView)
	{
		return anchor(tile, worldView, 0);
	}

	/** The same, with the player pointed somewhere in particular. */
	private FollowerAnchor facing(WorldPoint tile, int orientation)
	{
		return anchor(tile, view, orientation);
	}

	private static FollowerAnchor anchor(WorldPoint tile, FakeWorldView worldView, int orientation)
	{
		FollowerAnchor anchor = FollowerAnchor.of(
			FakePlayer.standingOn(worldView, tile).facing(orientation), worldView);
		assertTrue("the fixture's anchor has to resolve, or the test proves nothing",
			anchor.isResolved());
		return anchor;
	}

	private static EntourageSettings defaults()
	{
		return FakeConfig.defaults();
	}

	/** The shipped settings with running switched off, so a step is always one tile. */
	private static EntourageSettings walkOnly()
	{
		return new FakeConfig().setCanRun(false).settings();
	}

	private Follower settled(EntourageSettings settings)
	{
		return settled(EntourageFigure.ROGUE, settings);
	}

	/**
	 * Spawns the follower and walks it to a standstill on {@link #ANCHOR}, with the
	 * player standing on {@link #PLAYER} and an eastward heading. See the class javadoc
	 * for why that takes four ticks.
	 *
	 * @return the follower, active and standing still
	 */
	private Follower settled(EntourageFigure figure, EntourageSettings settings)
	{
		Follower follower = new Follower(client, FollowerBody.preset(figure), 0, ANCHOR);
		follower.onGameTick(at(ANCHOR), view, settings);
		follower.onGameTick(at(ANCHOR), view, settings);
		follower.onGameTick(at(PLAYER), view, settings);
		follower.onGameTick(at(PLAYER), view, settings);

		assertTrue("the fixture is supposed to leave it on screen", follower.isActive());
		assertFalse("and standing still", follower.getWalk().isMoving());
		assertEquals("on a known tile", ANCHOR, follower.getWalk().currentTile());
		return follower;
	}

	// --- Coming and going ----------------------------------------------------

	@Test
	public void aTickSpawnsItAndTheClientKnows()
	{
		Follower follower = follower();
		assertFalse("nothing is registered before the first tick", follower.isActive());

		follower.onGameTick(at(ANCHOR), view, defaults());

		assertTrue(follower.isActive());
		assertEquals(1, client.registeredCount());
		assertNotNull("it dressed from the composition", follower.getAppearance());
	}

	@Test
	public void itFormsUpOnTheAnchorRatherThanResumingAStaleTile()
	{
		Follower follower = follower();
		WorldPoint elsewhere = ANCHOR.dx(30).dy(-20);

		follower.onGameTick(at(elsewhere), view, defaults());

		assertEquals("a follower that was not on screen arrives where the player is",
			elsewhere, follower.getWalk().currentTile());
	}

	/**
	 * <b>And then it gets off it.</b> A follower spawns on the player's own tile, which
	 * is the one place it must never settle — the slot is at least one tile out by
	 * construction, so the tick after a spawn is always a step. Under the old
	 * "anywhere within a tile is fine" rule this did not happen: a player who spawned a
	 * follower and then stood still had it standing inside them indefinitely.
	 */
	@Test
	public void itStepsOffThePlayersTileEvenIfThePlayerNeverMoves()
	{
		EntourageSettings settings = defaults();
		Follower follower = follower();

		follower.onGameTick(at(ANCHOR), view, settings);
		assertEquals(ANCHOR, follower.getWalk().currentTile());

		follower.onGameTick(at(ANCHOR), view, settings);

		assertTrue(follower.getWalk().isMoving());
		assertEquals(follower.getWalk().stationTile(ANCHOR, settings),
			follower.getWalk().currentTile());
	}

	@Test
	public void despawnDeregistersAndIsIdempotent()
	{
		Follower follower = follower();
		follower.onGameTick(at(ANCHOR), view, defaults());

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

		follower.onGameTick(at(ANCHOR), view, defaults());
		assertTrue("setActive(true) not taking is a structural failure", follower.isBroken());

		int mergesBefore = client.mergeCalls();
		for (int i = 0; i < 50; i++)
		{
			follower.onGameTick(at(ANCHOR), view, defaults());
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
			follower.onGameTick(at(ANCHOR.dx(tick * 3)), view, defaults());
			for (int frame = 0; frame < 30; frame++)
			{
				follower.advanceFrame(view, frame / 30f);
			}
		}

		assertEquals("the client owns the animation clock", 0, client.lastObject().tickCalls());
	}

	/**
	 * Installing a controller resets its frame to zero — {@code setAnimation} calls
	 * {@code reset()}. So the number of installs has to be the number of pose-to-walk
	 * switches, not the number of game ticks: a controller reinstalled every tick is an
	 * animation that restarts 1.6 times a second, which is what "it needs animation
	 * smoothing" looks like from the outside.
	 */
	@Test
	public void aControllerIsInstalledOncePerSwitchAndNotOncePerTick()
	{
		EntourageSettings settings = walkOnly();
		Follower follower = settled(settings);

		int installs = client.lastObject().animationControllerInstalls();
		assertEquals("the pose on the spawn, the walk on the way to the slot, the pose again",
			3, installs);

		for (int i = 0; i < 10; i++)
		{
			follower.onGameTick(at(PLAYER), view, settings);
		}
		assertEquals("ten ticks of standing still install nothing new",
			installs, client.lastObject().animationControllerInstalls());

		// Walk five tiles.
		for (int i = 0; i < 5; i++)
		{
			follower.onGameTick(at(EAST), view, settings);
			assertTrue("this half of the test needs it actually walking",
				follower.getWalk().isMoving());
		}
		assertEquals("one switch into the walk, however many tiles it covers",
			installs + 1, client.lastObject().animationControllerInstalls());
	}

	@Test
	public void itAsksForItsFiguresPoseAndItsWalk()
	{
		Follower follower = settled(walkOnly());
		follower.onGameTick(at(EAST), view, walkOnly());

		assertTrue("the idle pose", client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getIdleAnimation().getId()));
		assertTrue("the walk", client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getWalkAnimation().getId()));
		assertFalse("and not the run, because it walked",
			client.animationsLoaded().contains(EntourageFigure.ROGUE.getRunAnimation().getId()));
	}

	/**
	 * <b>A run is a third animation, not the walk played faster.</b> Covering two tiles
	 * with a walk cycle is the sliding-mesh failure at half speed — the legs move at a
	 * walk while the ground moves at a run — so the follower has to ask for something
	 * different when it runs, and be seen to.
	 */
	@Test
	public void aRunningFollowerAsksForTheRunAndNotForTheWalk()
	{
		Follower follower = follower();
		follower.onGameTick(at(ANCHOR), view, defaults());
		follower.onGameTick(at(ANCHOR.dx(10)), view, defaults());

		assertTrue("this test needs it actually running", follower.getWalk().isRunning());
		assertTrue(client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getRunAnimation().getId()));
		assertFalse("a run is not a walk", client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getWalkAnimation().getId()));
	}

	/**
	 * The run and the walk are different controllers as well as different ids: sharing
	 * one would mean every change of pace reset the animation's frame to zero.
	 */
	@Test
	public void theRunAndTheWalkAreTwoControllers()
	{
		Follower follower = settled(defaults());

		follower.onGameTick(at(EAST), view, defaults());
		assertTrue("this test needs it running", follower.getWalk().isRunning());
		AnimationController running = follower.getInstalledController();
		assertNotNull(running);

		// The same journey with running switched off, which is a walk.
		follower.onGameTick(at(EAST), view, walkOnly());
		assertTrue(follower.getWalk().isMoving());
		assertFalse(follower.getWalk().isRunning());

		assertNotSame("a change of pace must not reuse one controller",
			running, follower.getInstalledController());
	}

	/**
	 * A figure whose run will not load keeps walking rather than freezing into a static
	 * model mid-stride — and the fallback is the walk rather than the pose, because a
	 * walk cycle over two tiles slides half as much as no cycle at all.
	 */
	@Test
	public void aFollowerWhoseRunWillNotLoadFallsBackToItsWalk()
	{
		client.withUnloadableAnimations(EntourageFigure.ROGUE.getRunAnimation().getId());
		Follower follower = settled(walkOnly());

		follower.onGameTick(at(EAST), view, walkOnly());
		AnimationController walking = follower.getInstalledController();
		assertNotNull("this test needs a walk controller to fall back to", walking);

		follower.onGameTick(at(EAST), view, defaults());
		assertTrue("this test needs it trying to run", follower.getWalk().isRunning());

		assertSame("a missing run leaves the walk installed", walking,
			follower.getInstalledController());
	}

	/**
	 * <b>A figure whose walk will not load keeps standing rather than freezing into a
	 * prop.</b> The alternative is installing nothing, which draws the base model
	 * unanimated — a body sliding across the ground, which is the single most visible way
	 * a follower plugin can look broken. Still wrong, but wrong in the way that says
	 * "this figure is idle" rather than "this figure is scenery".
	 *
	 * <p>Untested until a mutation pass deleted the fallback and the whole suite stayed
	 * green: the failed-animation tests either side of this one exercise the <i>idle</i>
	 * and the <i>run</i>, and neither notices the middle one.
	 */
	@Test
	public void aFollowerWhoseWalkWillNotLoadKeepsHoldingItsPose()
	{
		client.withUnloadableAnimations(EntourageFigure.ROGUE.getWalkAnimation().getId());
		Follower follower = settled(walkOnly());

		AnimationController pose = follower.getInstalledController();
		assertNotNull("this test needs a pose to fall back to", pose);

		follower.onGameTick(at(EAST), view, walkOnly());
		assertTrue("this test needs it actually walking", follower.getWalk().isMoving());

		assertSame("a missing walk leaves the pose installed rather than nothing", pose,
			follower.getInstalledController());
	}

	/**
	 * <b>A follower latched broken is touched no further, even if it is still on
	 * screen.</b> {@code EntourageScene} marks one broken and then despawns it, and the
	 * despawn can itself fail — a client that throws out of {@code removeRuneLiteObject}
	 * leaves a broken follower registered. From then on, every tick that reached it would
	 * step its walk and hand its object a new animation controller, which is the plugin
	 * driving an object it has already decided it cannot trust.
	 *
	 * <p>Untested until a mutation pass deleted the latch and nothing went red: the
	 * spawn path has a second {@code broken} check of its own, so every existing test —
	 * all of which latch a follower that is <i>inactive</i> — was covered by that one.
	 *
	 * <p><b>Driven through the failed despawn rather than through {@code markBroken()}</b>,
	 * because that is the path a client actually takes here and because the latch inside
	 * {@code despawn}'s own catch was the second thing that mutation pass found untested.
	 * A despawn that threw without latching leaves a follower being stepped every tick
	 * while its deactivation goes on failing — and while there is no anchor, that is a
	 * warning per tick as well.
	 */
	@Test
	public void aDespawnThatThrewLatchesTheFollowerRatherThanLeavingItRunning()
	{
		Follower follower = settled(walkOnly());
		client.refusingDeactivation();

		assertFalse("the client will not let go of the object", follower.despawn());
		assertTrue("so the follower is latched out of every later pass", follower.isBroken());
		assertTrue("while still being registered", follower.isActive());

		int installs = client.lastObject().animationControllerInstalls();
		int animations = client.animationsLoaded().size();
		WorldPoint where = follower.getWalk().currentTile();

		for (int i = 0; i < 10; i++)
		{
			follower.onGameTick(at(EAST), view, walkOnly());
		}

		assertFalse("it must not walk", follower.getWalk().isMoving());
		assertEquals("nor move", where, follower.getWalk().currentTile());
		assertEquals("nor be handed a controller", installs,
			client.lastObject().animationControllerInstalls());
		assertEquals("nor ask the cache for anything", animations,
			client.animationsLoaded().size());
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

		follower.onGameTick(at(ANCHOR), view, defaults());
		assertTrue("a figure with no animation is still better than no figure", follower.isActive());
		assertNull("nothing inert was installed", follower.getInstalledController());

		client.clearUnloadableAnimations();
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS + 2; i++)
		{
			follower.onGameTick(at(ANCHOR), view, defaults());
		}

		assertNotNull("once the cache warms up, the pose arrives", follower.getInstalledController());
	}

	// --- The idle pose setting ------------------------------------------------

	@Test
	public void theIdlePoseSettingIsWhatItHoldsWhileStandingStill()
	{
		EntourageSettings dancing = new FakeConfig()
			.setIdlePose(EntouragePose.DANCE)
			.setCanRun(false)
			.settings();

		settled(dancing);

		assertTrue("it asked for the pose the user picked", client.animationsLoaded()
			.contains(EntourageAnimation.POSE_DANCE.getId()));
		assertFalse("and not for the figure's own stand", client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getIdleAnimation().getId()));
	}

	/**
	 * <b>Changing the pose while the follower is standing there has to take effect.</b>
	 * The idle controller is cached — it has to be, or the animation would restart every
	 * tick — so a naive cache would leave the old pose playing until something else
	 * happened to invalidate it, which for a follower standing next to a stationary
	 * player is never. Cheap, too: the model, the object and the retry budget are all
	 * untouched.
	 */
	@Test
	public void changingThePoseSwapsTheControllerWithoutRebuildingTheModel()
	{
		EntourageSettings own = new FakeConfig().setCanRun(false).settings();
		Follower follower = settled(own);

		AnimationController before = follower.getInstalledController();
		int installs = client.lastObject().animationControllerInstalls();
		int merges = client.mergeCalls();

		EntourageSettings waving = new FakeConfig()
			.setIdlePose(EntouragePose.WAVE)
			.setCanRun(false)
			.settings();
		follower.onGameTick(at(PLAYER), view, waving);

		assertNotSame("the pose changed, so the controller has to have", before,
			follower.getInstalledController());
		assertEquals("exactly one install for one change", installs + 1,
			client.lastObject().animationControllerInstalls());
		assertTrue(client.animationsLoaded().contains(EntourageAnimation.POSE_WAVE.getId()));
		assertEquals("and nothing was rebuilt", merges, client.mergeCalls());

		// And it stays put once it has settled on the new pose.
		for (int i = 0; i < 10; i++)
		{
			follower.onGameTick(at(PLAYER), view, waving);
		}
		assertEquals("a pose that has not changed must not be reinstalled", installs + 1,
			client.lastObject().animationControllerInstalls());
	}

	@Test
	public void theDefaultPoseIsWhateverTheFigureItselfStandsWith()
	{
		settled(new FakeConfig().setCanRun(false).settings());

		assertTrue(client.animationsLoaded()
			.contains(EntourageFigure.ROGUE.getIdleAnimation().getId()));
	}

	// --- The frame clock -----------------------------------------------------

	@Test
	public void aStandingFollowerIsNotRePlacedEveryFrame()
	{
		Follower follower = settled(walkOnly());

		int afterTick = client.lastObject().setLocationCalls();
		follower.advanceFrame(view, 0f);
		int afterFirstFrame = client.lastObject().setLocationCalls();
		assertEquals("the first frame after a tick always places it", afterTick + 1, afterFirstFrame);

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
		Follower follower = settled(walkOnly());
		follower.onGameTick(at(EAST), view, walkOnly());
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
		Follower follower = settled(walkOnly());

		// Let the frame pass settle it, which is the state the next game tick has to undo.
		for (int frame = 0; frame < 30; frame++)
		{
			follower.advanceFrame(view, frame / 30f);
		}
		int whileStanding = client.lastObject().setLocationCalls();

		follower.onGameTick(at(EAST), view, walkOnly());
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
		Follower follower = settled(walkOnly());
		follower.onGameTick(at(EAST), view, walkOnly());

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
		follower.onGameTick(at(ANCHOR), view, defaults());
		follower.despawn();

		int before = client.lastObject().setLocationCalls();
		for (int frame = 0; frame < 30; frame++)
		{
			follower.advanceFrame(view, frame / 30f);
		}

		assertEquals(before, client.lastObject().setLocationCalls());
	}

	// --- Which way it points -------------------------------------------------

	@Test
	public void theObjectIsFacedTheWayTheWalkSaysItIsGoing()
	{
		Follower follower = settled(walkOnly());
		follower.onGameTick(at(EAST), view, walkOnly());

		assertEquals(StepOrientation.forStep(1, 0), follower.getRenderOrientation());
	}

	/**
	 * <b>The default, and the fixture is chosen so that it cannot pass by accident.</b>
	 * An earlier version of this test — it lived in {@code FollowerWalkTest} — walked a
	 * follower east and then asserted it faced east, which is what the last step had
	 * already left it facing; deleting the turn-to-face entirely left it green. Here the
	 * {@link #settled} fixture leaves the follower having walked <i>north</i> onto a slot
	 * that is <i>west</i> of the player, so "the way it was walking" and "at the player"
	 * are different answers and only one of them passes.
	 */
	@Test
	public void aFollowerThatHasStoppedLooksAtThePlayer()
	{
		Follower follower = settled(walkOnly());

		assertEquals("the fixture has to arrive from the south, or this proves nothing",
			StepOrientation.forStep(0, 1), follower.getWalk().getOrientation());
		assertEquals("and then turn to the player, who is one tile east of it",
			StepOrientation.forStep(1, 0), follower.getRenderOrientation());
	}

	/**
	 * <b>A fixed compass facing is held whoever is where.</b> West is chosen because the
	 * player is due east: a follower facing west is facing away, which is the one answer
	 * {@link FollowerFacing#AT_ME} could never produce.
	 */
	@Test
	public void aFixedFacingIsHeldWhereverThePlayerIs()
	{
		EntourageSettings west = new FakeConfig()
			.setFacing(FollowerFacing.WEST)
			.setCanRun(false)
			.settings();

		Follower follower = settled(west);

		assertEquals(StepOrientation.forStep(-1, 0), follower.getRenderOrientation());
		assertNotEquals("which is the opposite of looking at the player",
			StepOrientation.forStep(1, 0), follower.getRenderOrientation());
	}

	/**
	 * <b>"The way I am" is the player's own orientation, copied exactly.</b> The number
	 * is deliberately not one of {@link StepOrientation}'s eight — 1300 sits between
	 * north-east and east — so a follower that rounded it to a compass point would fail,
	 * and it is not {@link FakePlayer#INTERPOLATED_ORIENTATION} either, so a follower
	 * that read {@code getCurrentOrientation()} would fail with a different number again.
	 */
	@Test
	public void theWayIAmCopiesThePlayersOwnFacing()
	{
		EntourageSettings mirror = new FakeConfig()
			.setFacing(FollowerFacing.AS_I_AM)
			.setCanRun(false)
			.settings();

		Follower follower = settled(mirror);
		follower.onGameTick(facing(PLAYER, 1300), view, mirror);

		assertFalse("this test is about a follower that is standing still",
			follower.getWalk().isMoving());
		assertEquals(1300, follower.getRenderOrientation());
	}

	/**
	 * <b>A follower that is walking faces the way it is walking, whatever the setting
	 * says.</b> The alternative is a figure moonwalking east while pointing north, which
	 * no dropdown should be able to ask for.
	 */
	@Test
	public void aWalkingFollowerIgnoresTheFacingSetting()
	{
		EntourageSettings north = new FakeConfig()
			.setFacing(FollowerFacing.NORTH)
			.setCanRun(false)
			.settings();

		Follower follower = settled(north);
		assertEquals("the fixture has to be holding the fixed facing first",
			StepOrientation.forStep(0, 1), follower.getRenderOrientation());

		follower.onGameTick(at(EAST), view, north);

		assertTrue("this test needs it actually walking", follower.getWalk().isMoving());
		assertEquals("it faces its step, not its setting",
			StepOrientation.forStep(1, 0), follower.getRenderOrientation());
	}

	/**
	 * <b>The facing is applied on the tick the figure appears</b>, not on the one after
	 * it. A spawn places the follower on the player's own tile and then points it, and
	 * without that second step a figure with a fixed facing would appear pointing south
	 * — the orientation a fresh walk carries — and swing round 600ms later.
	 */
	@Test
	public void aFreshlySpawnedFollowerAlreadyPointsTheRightWay()
	{
		EntourageSettings west = new FakeConfig().setFacing(FollowerFacing.WEST).settings();
		Follower follower = follower();

		follower.onGameTick(at(ANCHOR), view, west);

		assertTrue("this test needs it to have actually spawned", follower.isActive());
		assertEquals(StepOrientation.forStep(-1, 0), follower.getRenderOrientation());
	}

	/**
	 * The player standing on the follower's own tile — which happens for a tick after a
	 * recall — has no direction from one to the other. Snapping to the table's
	 * {@code NOT_MOVING} sentinel would hand {@code setOrientation} a {@code -1}.
	 */
	@Test
	public void aPlayerStandingOnTheFollowerDoesNotSnapItsFacing()
	{
		Follower follower = settled(walkOnly());
		int before = follower.getRenderOrientation();
		assertEquals("the fixture has to be facing somewhere first",
			StepOrientation.forStep(1, 0), before);

		// A recall: the player is far enough away that the follower is put back onto the
		// player's own tile, where "at me" has no answer.
		follower.onGameTick(at(ANCHOR.dx(EntourageSettings.MAX_RECALL_DISTANCE + 1)), view,
			new FakeConfig().setRecallDistance(EntourageSettings.MIN_RECALL_DISTANCE).settings());

		assertEquals("it is standing on the player", ANCHOR.dx(EntourageSettings.MAX_RECALL_DISTANCE + 1),
			follower.getWalk().currentTile());
		assertEquals("so it keeps the facing it had rather than being handed -1",
			before, follower.getRenderOrientation());
	}

	// --- What it is saying ---------------------------------------------------

	/**
	 * <b>A follower that has left the screen is not saying anything.</b> The overlay draws
	 * between game ticks and reads the line straight off the follower, so a line left set
	 * across a despawn is text drawn over empty ground until the next tick clears it.
	 * Cleared in {@code despawn()} rather than by the chatter, so there is no window at
	 * all.
	 */
	@Test
	public void aDespawnedFollowerStopsTalkingImmediately()
	{
		Follower follower = settled(walkOnly());
		follower.getRemarks().say(0, 100, FigureLines.of(EntourageFigure.ROGUE));
		assertTrue("the fixture has to be talking first", follower.getRemarks().isTalking());

		follower.despawn();

		assertFalse("a figure nobody can see is not saying anything",
			follower.getRemarks().isTalking());
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
		follower.onGameTick(at(ANCHOR), view, defaults());

		assertTrue(follower.isActive());
		assertNotNull("a registered object with no base model is a figure that draws nothing",
			client.lastObject().getBaseModel());
	}

	/**
	 * <b>The follower is lit for the world, not for an interface.</b>
	 * {@code ModelData.light()} with no arguments is the widget rig — disassembled from
	 * the 1.12.38 injected client it is one call to {@code light(64, 768, -50, -10, -50)},
	 * which is {@code ModelData}'s own {@code DEFAULT_*} constants — and this plugin
	 * called it, so a figure walking through Varrock was lit like a model in a panel. The
	 * five below are what the same client lights a player and an NPC with: the
	 * {@code PlayerComposition} and {@code NPCComposition} implementations both call
	 * {@code light(64, 850, -30, -50, -30)}.
	 */
	@Test
	public void theModelIsLitTheWayTheClientLightsAFigureInTheWorld()
	{
		follower().onGameTick(at(ANCHOR), view, defaults());

		FakeModelData merged = client.lastMerged();
		assertNotNull(merged);
		assertTrue(merged.wasLit());
		assertNotNull("the no-argument overload is the interface rig, not the world one",
			merged.lighting());
		assertEquals("the world lighting rig, as the client itself uses it",
			Arrays.toString(new int[]{64, 850, -30, -50, -30}),
			Arrays.toString(merged.lighting()));
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
		Follower follower = new Follower(client, FollowerBody.preset(EntourageFigure.ROGUE), 0, upstairs);

		follower.onGameTick(at(upstairs, upstairsView), upstairsView, walkOnly());

		assertTrue(follower.isActive());
		assertEquals("the spawn places it on its own plane, not on the ground floor",
			2, client.lastObject().getLevel());

		follower.onGameTick(at(upstairs.dx(7), upstairsView), upstairsView, walkOnly());
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

		follower.onGameTick(at(ANCHOR), view, defaults());

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
			follower.onGameTick(at(ANCHOR), view, defaults());
		}

		assertEquals("three attempts a scene load, not one a tick",
			Follower.MAX_ATTEMPTS, client.npcDefinitionsRequested().size());
	}

	@Test
	public void theRetriesAreSpacedRatherThanSpentInThreeConsecutiveTicks()
	{
		client.setCacheCold(true);
		Follower follower = follower();

		follower.onGameTick(at(ANCHOR), view, defaults());
		follower.onGameTick(at(ANCHOR), view, defaults());
		follower.onGameTick(at(ANCHOR), view, defaults());

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
			follower.onGameTick(at(ANCHOR), view, defaults());
		}
		assertEquals(Follower.MAX_ATTEMPTS, client.npcDefinitionsRequested().size());

		follower.onSceneEntered();
		for (int i = 0; i < 400; i++)
		{
			follower.onGameTick(at(ANCHOR), view, defaults());
		}

		assertEquals("crossing a region border is the right moment to re-test a cold cache",
			Follower.MAX_ATTEMPTS * 2, client.npcDefinitionsRequested().size());
	}

	@Test
	public void aCacheThatWarmsUpProducesTheFigure()
	{
		client.setCacheCold(true);
		Follower follower = follower();
		follower.onGameTick(at(ANCHOR), view, defaults());
		assertFalse(follower.isActive());

		client.setCacheCold(false);
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS + 2; i++)
		{
			follower.onGameTick(at(ANCHOR), view, defaults());
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

		follower.onGameTick(at(ANCHOR), view, defaults());

		assertFalse("a figure with no torso is worse than no figure", follower.isActive());
		assertFalse(follower.isBroken());
		assertEquals("and nothing half-built was merged", 0, client.mergeCalls());
	}

	/**
	 * <b>The composition is resolved once and remembered, and the retry path is the only
	 * thing that can prove it.</b> Everywhere else, "what models does this figure wear?"
	 * is asked exactly once, because the answer is consumed straight into a model that is
	 * then cached — so a memo that did nothing would look identical. The case that tells
	 * them apart is an NPC that resolves while its <i>models</i> miss: the follower comes
	 * back for another go at the models, and it must not spend that go re-asking a
	 * question it already has the answer to.
	 *
	 * <p>Untested until a mutation pass deleted the memo and nothing went red.
	 */
	@Test
	public void anAppearanceThatResolvedIsNotResolvedAgainOnARetry()
	{
		client = new FakeClient()
			.withNpc(EntourageFigure.ROGUE.getNpcId(), FakeNpcComposition.of("Rogue", 11, 22, 33))
			.withUnloadableModels(22);
		client.setTopLevelWorldView(view);
		Follower follower = follower();

		follower.onGameTick(at(ANCHOR), view, defaults());
		assertEquals("the composition resolved", 1, client.npcDefinitionsRequested().size());
		int modelsAsked = client.modelsLoaded().size();

		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS + 2; i++)
		{
			follower.onGameTick(at(ANCHOR), view, defaults());
		}

		assertTrue("this test needs the models to have been retried",
			client.modelsLoaded().size() > modelsAsked);
		assertEquals("but the composition is remembered, not asked for again",
			1, client.npcDefinitionsRequested().size());
	}

	// --- Dressing ------------------------------------------------------------

	@Test
	public void everyModelPartTheCompositionNamesIsMerged()
	{
		client = new FakeClient()
			.withNpc(EntourageFigure.ROGUE.getNpcId(), FakeNpcComposition.of("Rogue", 11, 22, 33));
		client.setTopLevelWorldView(view);

		follower().onGameTick(at(ANCHOR), view, defaults());

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

		follower().onGameTick(at(ANCHOR), view, defaults());

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
		follower().onGameTick(at(ANCHOR), view, defaults());

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
		follower.onGameTick(at(ANCHOR), view, defaults());
		follower.despawn();
		follower.onGameTick(at(ANCHOR), view, defaults());

		assertTrue(follower.isActive());
		assertEquals("walking out of the world and back is an activate, not a rebuild",
			1, client.mergeCalls());
	}

	/**
	 * A figure that is not the default, built end to end. Every other test in this class
	 * uses the rogue, whose animations are the human rig's own — so a follower that
	 * ignored its figure's declared pair and always played 808/819 would pass all of
	 * them.
	 */
	@Test
	public void aFigureWithItsOwnAnimationsPlaysThoseAndNotTheHumanOnes()
	{
		Follower follower = settled(EntourageFigure.NIEVE, walkOnly());
		follower.onGameTick(at(EAST), view, walkOnly());

		assertTrue(follower.isActive());
		assertTrue("her own stand", client.animationsLoaded()
			.contains(EntourageAnimation.STAFF_STAND.getId()));
		assertTrue("her own walk", client.animationsLoaded()
			.contains(EntourageAnimation.HALBERD_WALK.getId()));
		assertFalse("and not the human rig's", client.animationsLoaded()
			.contains(EntourageAnimation.HUMAN_WALK.getId()));
	}
}
