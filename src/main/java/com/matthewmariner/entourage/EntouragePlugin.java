package com.matthewmariner.entourage;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * A cosmetic figure that walks with you and holds a pose when you stop.
 *
 * <p>Singular on purpose: {@link EntourageScene} spawns one figure — whichever one
 * {@link EntourageConfig#figure()} names — and the plugin is named for what it grows
 * into rather than for what it currently spawns. The user-facing strings —
 * {@code @PluginDescriptor}'s {@code description} below and
 * {@code runelite-plugin.properties} — say one for the same reason, and they said
 * "a small group" in two slightly different wordings until a review noticed.
 *
 * <p><b>The settings are read by the scene, not by this class.</b> There is a
 * {@code @Provides} for the config interface at the bottom of this file and deliberately
 * no {@code ConfigChanged} handler: {@link EntourageScene} re-reads every setting at the
 * top of each game tick and notices a change of figure by comparing it, which cannot
 * miss an event, cannot race the tick that is about to use the answer, and cannot fire
 * while nobody is logged in.
 *
 * <p>Client-side only. Nothing here is visible to anybody else, no packet is sent, and
 * no information about any other player is read.
 *
 * <p>Lifecycle, and why it is shaped this way:
 *
 * <ul>
 *   <li><b>Work happens on {@link GameTick}, not on {@code LOGGED_IN}.</b>
 *       {@code LOGGED_IN} fires before the world is usable — {@code getLocalPlayer()}
 *       is still null at that point — so the state handler only invalidates, and the
 *       tick handler does the work behind {@link FollowerAnchor}'s own null
 *       checks.</li>
 *   <li><b>{@link BeforeRender} is the second clock.</b> It is posted from the
 *       client's own frame loop, once per rendered frame, and it is where a walking
 *       follower's position is interpolated between tiles. Nothing else happens per
 *       frame: the animations are advanced by the client, which calls
 *       {@code RuneLiteObject.tick(ticksSinceLastFrame)} on every registered object as
 *       it draws it. Calling that here as well would run every animation at double
 *       speed.</li>
 *   <li><b>Everything reaches {@link EntourageScene} on the client thread.</b>
 *       {@code @Subscribe} runs on the posting thread, so the handlers wrap their calls
 *       in {@link ClientThread#invoke} — which runs inline when we are already on the
 *       client thread and defers when we are not. The frame handler is the exception:
 *       it is already on the client thread by construction, and queueing a task per
 *       frame would be a queue that never drains.</li>
 *   <li><b>No menu entries.</b> A figure that walks with you sits inside your click
 *       radius all day, so several of them would add rows to nearly every right-click
 *       in the game — and a follower mistaken for a real NPC is worse than a static
 *       townsperson mistaken for one, because it is where you are looking. A
 *       {@code RuneLiteObject} is not clickable unless a plugin synthesises entries for
 *       it, so the cheapest correct answer for this slice is to synthesise none. When
 *       that changes, the treatment to copy is {@code CitizenMenu} in
 *       {@code ../lively-cities}: deprioritised, {@code MenuAction.RUNELITE}-typed, and
 *       an Examine that says out loud what the figure is.</li>
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "Entourage",
	// Singular, and byte-identical to runelite-plugin.properties. This is the
	// in-client panel's copy of a string the hub listing also carries; the two
	// used to differ from each other, and both used to promise a group while the
	// plugin shipped one figure. Change them together.
	description = "A cosmetic figure of your choosing that walks with you and holds a pose when you stop",
	tags = {"cosmetic", "follower", "entourage", "immersion", "npc"}
)
public class EntouragePlugin extends Plugin
{
	/**
	 * Client ticks in one game tick: 600ms / 20ms.
	 *
	 * <p>The denominator for the interpolation fraction. Taken from the client's own
	 * constants rather than written as 30, because both halves are named there and a
	 * divisor that is silently wrong shows up as followers that finish their step early
	 * and then stand still, which is hard to attribute.
	 */
	private static final int CLIENT_TICKS_PER_GAME_TICK =
		Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	// Package-private rather than private so the tests in this package can wire their
	// own fakes in. Guice injects a package-private field exactly as it injects a
	// private one, so this costs the runtime nothing — and the alternative is either a
	// mocking framework (none on the classpath) or reflection (banned).
	@Inject
	Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	EntourageScene scene;

	/**
	 * {@code getGameCycle()} at the last game tick that was processed.
	 *
	 * <p>The frame handler needs to know how far through the current game tick it is.
	 * {@code getGameCycle()} increments every 20ms — the client's own clock, which stops
	 * when the client stops — so the difference divided by
	 * {@link #CLIENT_TICKS_PER_GAME_TICK} is that fraction. Wall-clock time would also
	 * work, right up until the first time the client is paused or the machine sleeps.
	 *
	 * <p><b>Written only by {@link #onGameTick}.</b> It is the interpolation clock's
	 * origin, so anything else that reset it would restart every follower's step from
	 * wherever it had got to.
	 */
	private int cycleAtLastTick;

	@Override
	protected void startUp()
	{
		log.debug("Entourage starting");

		// Enabling the plugin mid-session is the common case in dev, and there is no
		// state change coming to trigger the first pass.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// The braces are load-bearing. ClientThread overloads invoke() on Runnable
			// and BooleanSupplier, and a BooleanSupplier is re-queued every tick until
			// it returns true. A block lambda whose body is a bare statement has no
			// value, so only Runnable fits — which is what stops one first pass
			// silently becoming a retry loop if the method it calls ever grows a
			// boolean return.
			clientThread.invoke(() ->
			{
				tick();
			});
		}
	}

	@Override
	protected void shutDown()
	{
		// Not blocking: invoke() runs inline on the client thread and defers otherwise.
		// The count lands in the log either way.
		//
		// A block lambda rather than `scene::shutdown`, for the same reason the braces
		// above are load-bearing. That method reference binds to the Runnable overload
		// today only because shutdown() returns int; give it a boolean return one day
		// and it silently rebinds to BooleanSupplier, which ClientThread re-queues every
		// tick until it returns true — a teardown that runs forever, with no warning and
		// no call site changed. A block lambda whose body is a bare statement has no
		// value, so only Runnable ever fits.
		clientThread.invoke(() ->
		{
			scene.shutdown();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();

		switch (state)
		{
			// The scene is being replaced or is gone. Every LocalPoint held is about to
			// mean something else, so nothing may stay active.
			case LOADING:
			case HOPPING:
			case LOGGING_IN:
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
			case CONNECTION_LOST:
				clientThread.invoke(() -> scene.invalidate(state.name()));
				break;

			// Deliberately no work on LOGGED_IN: the local player may still be null
			// here. onGameTick picks it up.
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick();
	}

	/**
	 * The per-frame hook. Posted by the client from its draw loop, so this is already
	 * on the client thread.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		scene.onFrame(worldView, tickFraction());
	}

	/**
	 * The one place that touches the scene per game tick, and the only writer of
	 * {@link #cycleAtLastTick}.
	 */
	private void tick()
	{
		// Restarting the interpolation clock is a game-tick-only act: the frame handler
		// measures from here, so writing it anywhere else would drop every follower
		// back to the start of its step.
		cycleAtLastTick = client.getGameCycle();
		scene.onGameTick();
	}

	/**
	 * @return how far through the current game tick this frame is, clamped to 0..1.
	 * Clamped at 1 because a dropped or delayed game tick would otherwise send a
	 * follower sliding past the tile it was walking to, and clamped at 0 because a frame
	 * drawn before the tick that would explain it must not drag one back past the tile
	 * it came from.
	 *
	 * <p><b>The subtraction stays in {@code int} on purpose.</b>
	 * {@code getGameCycle()} is an {@code int} counting 20ms ticks, so it wraps after
	 * about 497 days of client uptime — and two's-complement {@code int} subtraction
	 * wraps with it, which makes the elapsed count come out right across the seam.
	 * Promoting either side to {@code long} would turn that one frame into a huge
	 * negative elapsed, trip the {@code <= 0} guard, and freeze every follower mid-step
	 * until the next game tick.
	 */
	private float tickFraction()
	{
		int elapsed = client.getGameCycle() - cycleAtLastTick;
		if (elapsed <= 0)
		{
			return 0f;
		}
		if (elapsed >= CLIENT_TICKS_PER_GAME_TICK)
		{
			return 1f;
		}
		return elapsed / (float) CLIENT_TICKS_PER_GAME_TICK;
	}

	/**
	 * The Guice binding for the settings interface.
	 *
	 * <p>{@code ConfigManager.getConfig} builds a proxy over the user's profile;
	 * {@code EntourageScene} takes the interface, so a test can hand it a plain
	 * implementation instead. There is deliberately no {@code ConfigChanged} handler:
	 * the scene re-reads every setting at the top of each game tick and notices a figure
	 * swap by comparing it, which cannot miss an event or race the tick that is about to
	 * use the answer.
	 */
	@Provides
	EntourageConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EntourageConfig.class);
	}
}
