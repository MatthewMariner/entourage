package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The plugin's own lifecycle: the two clocks, and the teardown promise.
 *
 * <p>Constructed directly rather than through Guice. {@code startUp()} and
 * {@code shutDown()} are {@code protected}, which same-package test code can call
 * without a subclass, and the three collaborators are package-private fields for the
 * same reason — Guice injects a package-private field exactly as it injects a private
 * one, and the alternative is a mocking framework (none on the classpath) or reflection
 * (banned).
 */
public class EntouragePluginLifecycleTest
{
	private static final int CLIENT_TICKS_PER_GAME_TICK =
		Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	private static final WorldPoint STANDING = new WorldPoint(3221, 3218, 0);

	private FakeClient client;
	private FakeWorldView view;
	private InlineClientThread clientThread;
	private FakeConfig config;
	private RecordingOverlayRegistry overlays;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs();
		view = FakeWorldView.around(STANDING);
		client.setTopLevelWorldView(view);
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING));
		clientThread = new InlineClientThread();
		config = new FakeConfig();
		overlays = new RecordingOverlayRegistry();
	}

	private RecordingScene recordingScene()
	{
		return new RecordingScene(client, config);
	}

	private EntourageScene scene()
	{
		return new EntourageScene(client, config);
	}

	private static GameStateChanged stateChanged(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private EntouragePlugin plugin(EntourageScene scene)
	{
		EntouragePlugin plugin = new EntouragePlugin();
		plugin.client = client;
		plugin.clientThread = clientThread;
		plugin.scene = scene;
		plugin.overlayRegistry = overlays;

		// A null plugin reference, which is what Overlay(Plugin) is given here and in
		// ../lively-cities' own overlay tests: the base class keeps it for the settings
		// panel's benefit and nothing in this test path reads it.
		plugin.overlay = new EntourageOverlay(null, client, scene, config);
		return plugin;
	}

	// --- The teardown promise ------------------------------------------------

	/**
	 * The non-negotiable, against the client's own registered-object list and with real
	 * followers really spawned first. A teardown test that shuts down an empty plugin
	 * passes whether or not the teardown does anything at all.
	 *
	 * <p><b>The overlay is held to the same standard as the objects</b>, and for the same
	 * reason: one left in the {@code OverlayManager} goes on being drawn after the plugin
	 * that owns it has stopped, over a roster that has been emptied underneath it. It is
	 * counted off {@link RecordingOverlayRegistry} rather than off {@code shutDown()}
	 * being read.
	 */
	@Test
	public void shutDownLeavesZeroRegisteredObjects()
	{
		EntourageScene scene = scene();
		EntouragePlugin plugin = plugin(scene);

		plugin.startUp();
		plugin.onGameTick(new GameTick());
		assertTrue("the figures have to be there before the teardown means anything",
			client.registeredCount() > 0);
		assertEquals("and so does the overlay", 1, overlays.registered.size());

		plugin.shutDown();

		assertEquals("a leaked RuneLiteObject is a figure nothing owns", 0, client.registeredCount());
		assertTrue(scene.getFollowers().isEmpty());
		assertTrue("an overlay left registered goes on drawing after shutdown",
			overlays.registered.isEmpty());
	}

	/**
	 * The overlay that comes back out is the one that went in.
	 *
	 * <p>{@code OverlayManager.remove} is an identity removal, so a {@code shutDown} that
	 * built a second instance to hand back — or handed back a different overlay entirely
	 * — would leave the first one drawing forever while looking, from the outside, exactly
	 * like a teardown that worked. The list emptying is not enough on its own to catch
	 * that; the identity is.
	 */
	@Test
	public void theOverlayRemovedIsTheOverlayThatWasAdded()
	{
		EntourageScene scene = scene();
		EntouragePlugin plugin = plugin(scene);

		plugin.startUp();
		assertSame(plugin.overlay, overlays.registered.get(0));

		plugin.shutDown();

		assertEquals("one add and one remove, not a rebuild", 1, overlays.adds);
		assertEquals(1, overlays.removes);
		assertSame("and it is the same object that came back out", plugin.overlay, overlays.lastRemoved);
	}

	@Test
	public void shutDownWithoutAStartUpIsHarmless()
	{
		EntourageScene scene = scene();
		plugin(scene).shutDown();

		assertEquals(0, client.registeredCount());
		assertTrue(overlays.registered.isEmpty());
	}

	@Test
	public void aSecondShutDownStillLeavesNothing()
	{
		EntourageScene scene = scene();
		EntouragePlugin plugin = plugin(scene);
		plugin.startUp();
		plugin.onGameTick(new GameTick());

		plugin.shutDown();
		plugin.shutDown();

		assertEquals(0, client.registeredCount());
		assertTrue(overlays.registered.isEmpty());
	}

	// --- Startup -------------------------------------------------------------

	@Test
	public void enablingThePluginMidSessionRunsAPassWithoutWaitingForATick()
	{
		RecordingScene scene = recordingScene();
		client.setGameState(GameState.LOGGED_IN);

		plugin(scene).startUp();

		assertEquals(1, scene.gameTicks);
		assertEquals("through the client thread, not straight off the EDT", 1, clientThread.invocations);
	}

	@Test
	public void enablingItAtTheLoginScreenRunsNothing()
	{
		RecordingScene scene = recordingScene();
		client.setGameState(GameState.LOGIN_SCREEN);

		plugin(scene).startUp();

		assertEquals(0, scene.gameTicks);
		assertEquals(0, clientThread.invocations);
	}

	// --- The two clocks ------------------------------------------------------

	@Test
	public void aGameTickIsAGameTickAndAFrameIsAFrame()
	{
		RecordingScene scene = recordingScene();
		EntouragePlugin plugin = plugin(scene);

		plugin.onGameTick(new GameTick());
		plugin.onBeforeRender(new BeforeRender());

		assertEquals(1, scene.gameTicks);
		assertEquals(1, scene.frames);
	}

	@Test
	public void theFrameFractionRunsFromZeroToOneAcrossAGameTick()
	{
		RecordingScene scene = recordingScene();
		EntouragePlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());

		plugin.onBeforeRender(new BeforeRender());
		assertEquals("the frame the tick arrived on is the start of the step",
			0f, scene.fractions.get(0), 0.0001f);

		client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK / 2);
		plugin.onBeforeRender(new BeforeRender());
		assertEquals(0.5f, scene.fractions.get(1), 0.0001f);

		client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK / 2);
		plugin.onBeforeRender(new BeforeRender());
		assertEquals(1f, scene.fractions.get(2), 0.0001f);
	}

	@Test
	public void aLateGameTickDoesNotSendFollowersSlidingPastTheirTile()
	{
		RecordingScene scene = recordingScene();
		EntouragePlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());
		client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK * 4);
		plugin.onBeforeRender(new BeforeRender());

		assertEquals(1f, scene.fractions.get(0), 0.0001f);
	}

	@Test
	public void aFrameBeforeItsGameTickDoesNotDragThemBack()
	{
		RecordingScene scene = recordingScene();
		EntouragePlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());
		client.setGameCycle(990);
		plugin.onBeforeRender(new BeforeRender());

		assertEquals(0f, scene.fractions.get(0), 0.0001f);
	}

	/**
	 * {@code getGameCycle()} is an {@code int} counting 20ms ticks, so it wraps after
	 * about 497 days of client uptime. Two's-complement {@code int} subtraction wraps
	 * with it, which makes the elapsed count come out right across the seam; promoting
	 * either side to {@code long} would turn that one frame into a huge negative elapsed
	 * and freeze every follower mid-step until the next game tick.
	 */
	@Test
	public void theFractionSurvivesTheGameCycleWrappingRoundTheIntegerSeam()
	{
		RecordingScene scene = recordingScene();
		EntouragePlugin plugin = plugin(scene);

		client.setGameCycle(Integer.MAX_VALUE - 5);
		plugin.onGameTick(new GameTick());

		// Ten client ticks later, which crosses the seam.
		client.setGameCycle(Integer.MIN_VALUE + 4);
		plugin.onBeforeRender(new BeforeRender());

		assertEquals(10 / (float) CLIENT_TICKS_PER_GAME_TICK, scene.fractions.get(0), 0.0001f);
	}

	@Test
	public void aFrameWithNoWorldViewDoesNothingRatherThanThrowing()
	{
		RecordingScene scene = recordingScene();
		client.setTopLevelWorldView(null);

		plugin(scene).onBeforeRender(new BeforeRender());

		assertEquals(0, scene.frames);
	}

	// --- Game state ----------------------------------------------------------

	@Test
	public void everyStateThatReplacesTheSceneInvalidatesIt()
	{
		RecordingScene scene = recordingScene();
		EntouragePlugin plugin = plugin(scene);

		for (GameState state : new GameState[]{
			GameState.LOADING, GameState.HOPPING, GameState.LOGGING_IN,
			GameState.LOGIN_SCREEN, GameState.LOGIN_SCREEN_AUTHENTICATOR, GameState.CONNECTION_LOST})
		{
			plugin.onGameStateChanged(stateChanged(state));
		}

		assertEquals(6, scene.invalidations.size());
		assertTrue(scene.invalidations.contains("LOADING"));
		assertTrue(scene.invalidations.contains("HOPPING"));
	}

	/**
	 * {@code LOGGED_IN} fires before the world is usable — {@code getLocalPlayer()} is
	 * still null at that point — so there is deliberately no work on it. Invalidating
	 * there would also throw away the scene the tick handler is about to use.
	 */
	@Test
	public void loggedInDoesNothing()
	{
		RecordingScene scene = recordingScene();
		plugin(scene).onGameStateChanged(stateChanged(GameState.LOGGED_IN));

		assertTrue(scene.invalidations.isEmpty());
		assertEquals(0, scene.gameTicks);
	}

	/**
	 * The {@code OverlayManager}, minus the manager — see {@link OverlayRegistry} for why
	 * the real one cannot be stood in for.
	 *
	 * <p>It keeps the list rather than only counting, because "added once and removed
	 * once" and "left registered" are the same pair of counts when the removal takes the
	 * wrong overlay.
	 */
	private static final class RecordingOverlayRegistry implements OverlayRegistry
	{
		private final List<net.runelite.client.ui.overlay.Overlay> registered = new ArrayList<>();

		private net.runelite.client.ui.overlay.Overlay lastRemoved;
		private int adds;
		private int removes;

		@Override
		public void add(net.runelite.client.ui.overlay.Overlay overlay)
		{
			adds++;
			registered.add(overlay);
		}

		@Override
		public void remove(net.runelite.client.ui.overlay.Overlay overlay)
		{
			removes++;
			lastRemoved = overlay;
			registered.remove(overlay);
		}
	}

	/** The real {@link ClientThread}, minus the thread. */
	private static final class InlineClientThread extends ClientThread
	{
		private int invocations;

		@Override
		public void invoke(Runnable runnable)
		{
			invocations++;
			runnable.run();
		}
	}

	/**
	 * The real {@link EntourageScene} with every entry point counted and none of them
	 * doing anything. Nothing is stubbed out that the plugin does not call, so a new call
	 * from the plugin lands on the real implementation rather than being silently
	 * swallowed.
	 */
	private static final class RecordingScene extends EntourageScene
	{
		private final List<Float> fractions = new ArrayList<>();
		private final List<String> invalidations = new ArrayList<>();

		private int gameTicks;
		private int frames;

		private RecordingScene(net.runelite.api.Client client, EntourageConfig config)
		{
			super(client, config);
		}

		@Override
		void onGameTick()
		{
			gameTicks++;
		}

		@Override
		void onFrame(WorldView worldView, float fraction)
		{
			frames++;
			fractions.add(fraction);
		}

		@Override
		void invalidate(String reason)
		{
			invalidations.add(reason);
		}

		@Override
		int shutdown()
		{
			return 0;
		}
	}
}
