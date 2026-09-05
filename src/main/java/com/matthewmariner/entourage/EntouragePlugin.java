package com.matthewmariner.entourage;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.inject.Singleton;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * A cosmetic figure that walks with you and holds a pose when you stop.
 *
 * <p>{@link EntourageScene} spawns one to five figures — whichever ones
 * {@link EntourageConfig}'s five figure slots name, as many of them as
 * {@link EntourageConfig#followers()} says — standing in the shape
 * {@link EntourageConfig#formation()} names. The user-facing strings,
 * {@code @PluginDescriptor}'s {@code description} below and
 * {@code runelite-plugin.properties}, are byte-identical to each other and are changed
 * together; they said "a small group" in two slightly different wordings, while the
 * plugin shipped one figure, until a review noticed.
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
 *   <li><b>The overlay is registered here and nowhere else.</b>
 *       {@link EntourageOverlay} draws whatever the followers are saying, so it is the
 *       other thing this plugin hands to the client that has to be handed back:
 *       {@code startUp} adds it and {@code shutDown} removes it, through
 *       {@link OverlayRegistry} so that the pair can be asserted rather than read.</li>
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
	// Byte-identical to runelite-plugin.properties. This is the in-client panel's
	// copy of a string the hub listing also carries; the two used to differ from
	// each other, and both used to promise a group while the plugin shipped one
	// figure. Change them together — and only when the roster really does what
	// they say, which since the formation slice it does.
	description = "Up to five cosmetic figures of your choosing that walk with you in formation, pose when you stop and say the odd thing",
	tags = {"cosmetic", "follower", "entourage", "immersion", "npc", "dialogue", "formation"}
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

	@Inject
	OverlayRegistry overlayRegistry;

	@Inject
	EntourageOverlay overlay;

	@Inject
	SidePanel sidePanel;

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

		overlayRegistry.add(overlay);

		// The third thing handed to the client that has to be handed back — and the only one
		// the user can see is missing. Added here rather than lazily so the button appears
		// the moment the plugin is enabled, which is the only affordance on this plugin that
		// announces itself.
		//
		// Straight from this thread on purpose: PluginManager calls startUp() on the event
		// dispatch thread, and ClientToolbar.addNavigation posts its own work through
		// SwingUtilities.invokeLater, so it is safe from any thread and needs no hop of ours.
		// Nothing here reads the client — that would be the mistake this method cannot make
		// twice, because a client read off the client thread throws.
		sidePanel.show();

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
		// Removed before the scene is torn down, and synchronously: an overlay left in the
		// OverlayManager goes on being drawn, and it would be drawing from a roster that
		// is being emptied underneath it. It is the same promise as the one below about
		// registered objects — nothing this plugin put somewhere may outlive it — and
		// EntouragePluginLifecycleTest pins both against a registry rather than against
		// this method being read.
		overlayRegistry.remove(overlay);

		// And the button, for the same reason and a plainer one: a navigation button left in
		// the toolbar opens a panel that goes on writing settings for a plugin that is not
		// running, and unlike a leaked overlay the user can see it sitting there.
		sidePanel.hide();

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
	 * A setting moved: redraw the side panel.
	 *
	 * <p><b>This does not contradict the paragraph above about there being no
	 * {@code ConfigChanged} handler.</b> That paragraph is about the <i>scene</i>, and it
	 * still holds — nothing here touches {@link EntourageScene}, which goes on re-reading
	 * every setting at the top of each game tick and noticing a roster swap by comparing it.
	 * What this handler is for is the one thing a per-tick re-read cannot do: tell a Swing
	 * panel that the numbers it is drawing are out of date. The panel writes through
	 * {@link ConfigWriter} and redraws itself, so the case this exists for is the other
	 * direction — a dial moved in RuneLite's own settings screen, or a profile switched
	 * underneath both.
	 *
	 * <p>Filtered to this plugin's own group, because this event fires for every setting in
	 * the client and a redraw per keystroke in somebody else's plugin is a panel rebuilt a
	 * few hundred times for nothing.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (EntourageConfig.GROUP.equals(event.getGroup()))
		{
			// The hop to the event dispatch thread is the panel's own — see SidePanel.
			sidePanel.refresh();
		}
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

	/**
	 * The plugin's only write path into its own settings — see {@link ConfigWriter}.
	 *
	 * <p>This method is the whole reason that interface exists: {@code ConfigManager}'s
	 * constructor is private, so anything taking one directly would be a class no test could
	 * construct. Behind this one lambda, everything that decides <i>what</i> to write is
	 * testable against a map.
	 */
	@Provides
	ConfigWriter provideConfigWriter(ConfigManager configManager)
	{
		return (key, value) ->
		{
			if (value == null)
			{
				configManager.unsetConfiguration(EntourageConfig.GROUP, key);
			}
			else
			{
				configManager.setConfiguration(EntourageConfig.GROUP, key, value);
			}
		};
	}

	/**
	 * The roster panel's place in the sidebar — see {@link SidePanel}.
	 *
	 * <p><b>The button is built once and added and removed, rather than rebuilt on every
	 * toggle.</b> {@code ClientToolbar} keys its navigation off the button instance, so a
	 * second one built on the way back in would leave the first behind — a plugin disabled
	 * and re-enabled five times would leave five buttons in the toolbar, four of them dead.
	 *
	 * <p>{@code @Singleton} is what makes "built once" structural rather than a property of
	 * this method happening to be asked once. Without it a second injection point added later
	 * would run this again, build a second button around a second panel, and leave the first
	 * one in the toolbar forever — the exact leak the paragraph above is about, arriving by a
	 * different door.
	 *
	 * <p>The priority puts it below RuneLite's own panels rather than above them; this is a
	 * cosmetic plugin and it should not outrank the config screen.
	 */
	@Provides
	@Singleton
	SidePanel provideSidePanel(ClientToolbar clientToolbar, EntourageRosterPanel panel)
	{
		final NavigationButton button = NavigationButton.builder()
			.tooltip("Entourage — who walks with you")
			.icon(ImageUtil.loadImageResource(EntourageRosterPanel.class, "panel_icon.png"))
			.priority(7)
			.panel(panel)
			.build();

		return new SidePanel()
		{
			@Override
			public void show()
			{
				clientToolbar.addNavigation(button);
			}

			@Override
			public void hide()
			{
				clientToolbar.removeNavigation(button);
			}

			@Override
			public void refresh()
			{
				panel.refresh();
			}
		};
	}

	/**
	 * The plugin's only overlay registration path — see {@link OverlayRegistry} for why
	 * the manager is behind an interface at all.
	 */
	@Provides
	OverlayRegistry provideOverlayRegistry(OverlayManager overlayManager)
	{
		return new OverlayRegistry()
		{
			@Override
			public void add(Overlay overlay)
			{
				overlayManager.add(overlay);
			}

			@Override
			public void remove(Overlay overlay)
			{
				overlayManager.remove(overlay);
			}
		};
	}
}
