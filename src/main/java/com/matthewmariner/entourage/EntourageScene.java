package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Every {@link Follower} there is, and the one place that decides whether any of them
 * is on screen.
 *
 * <p><b>Two clocks, and they do different work.</b> {@link #onGameTick()} resolves the
 * anchor and steps each follower one tile; {@link #onFrame(WorldView, float)} slides
 * the drawn positions between tiles and does nothing else. See {@link FollowerWalk}
 * for why that split is the design rather than an optimisation.
 *
 * <p><b>The whole of the "no anchor" branch is here, and it is one rule: nothing is
 * drawn.</b> {@link FollowerAnchor} names four ways of not having a tile and three of
 * them are ordinary — logging in, loading a scene, standing on a boat. The alternative
 * to deactivating is leaving a {@code RuneLiteObject} registered across a scene load,
 * at a {@code LocalPoint} that now addresses somewhere else entirely. That is a figure
 * standing in the world that nothing owns, which is exactly the leak
 * {@link #shutdown()} exists to make impossible; it would be strange to forbid it at
 * teardown and permit it at every border crossing.
 *
 * <p><b>Followers are built once and kept.</b> Deactivating is cheap and rebuilding a
 * model is not, so walking out of the world and back costs an activate/deactivate
 * rather than a merge and a light.
 *
 * <p><b>The one thing that does rebuild is a change of figure</b>, and it has to: the
 * figure decides which {@code NPCComposition} the model was merged out of, so a new one
 * is a new model. It is noticed here, at the top of the tick, by comparing the
 * configured figure against the one the roster was built for — rather than by
 * subscribing to {@code ConfigChanged}. One reference compare per tick cannot miss an
 * event, cannot fire in the wrong order relative to the tick that is about to use the
 * roster, and cannot fire while the player is logged out; and the tick is 600ms, which
 * is as immediate as a settings change needs to be. The retirement goes through the
 * same "only forget what really came off the screen" path {@link #shutdown()} uses,
 * because a figure swap must not be a way to leak the old figure.
 *
 * <p><b>Client thread only.</b> Every method here reaches live client state, directly
 * or through {@link Follower}.
 */
@Slf4j
@Singleton
class EntourageScene
{
	private final Client client;
	private final EntourageConfig config;
	private final List<Follower> followers = new ArrayList<>();

	/**
	 * The figure {@link #followers} was built for, or {@code null} when there is no
	 * roster.
	 *
	 * <p>Kept separately from {@code followers.get(0).getFigure()} so that a retirement
	 * which could not let go of its object — see {@link #retire()} — does not turn into
	 * a rebuild attempt on every subsequent tick, and one warning per tick with it.
	 */
	@Nullable
	private EntourageFigure rosterFigure;

	/**
	 * The last resolution reported, so the log says "the anchor went away" once rather
	 * than sixteen hundred times a minute.
	 */
	@Nullable
	private FollowerAnchor.Resolution lastResolution;

	@Inject
	EntourageScene(Client client, EntourageConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/**
	 * One game tick: work out where the player is being drawn, and either form the
	 * entourage up on it or take the entourage off the screen.
	 */
	void onGameTick()
	{
		WorldView worldView = client.getTopLevelWorldView();
		FollowerAnchor anchor = FollowerAnchor.of(client.getLocalPlayer(), worldView);

		if (anchor.getResolution() != lastResolution)
		{
			log.debug("anchor is now {}", anchor);
			lastResolution = anchor.getResolution();
		}

		if (!anchor.isResolved())
		{
			despawnAll();
			return;
		}

		// Read once per tick, after the anchor check so that a plugin nobody is logged
		// into does not touch the config proxy at all. See EntourageSettings on why this
		// is a snapshot rather than the config itself.
		EntourageSettings settings = EntourageSettings.from(config);

		WorldPoint tile = anchor.getTile();
		for (Follower follower : roster(settings))
		{
			try
			{
				follower.onGameTick(tile, worldView, settings);
			}
			catch (RuntimeException e)
			{
				// One follower's bad luck costs that follower. Letting this out of an
				// EventBus handler abandons every follower after it in the pass —
				// including any that were supposed to deactivate. Latched as well as
				// caught: a follower that throws every tick would otherwise be a warning
				// every tick, forever.
				log.warn("{}: threw during the tick pass, not retrying",
					follower.getFigure().label(), e);
				follower.markBroken();
				follower.despawn();
			}
		}
	}

	/**
	 * One rendered frame: slide every walking follower between its two tiles.
	 *
	 * <p>Deliberately no anchor resolution and no spawning. This runs at the frame rate
	 * — potentially a couple of hundred times a second — and the only thing that has to
	 * happen at that rate is the interpolation.
	 *
	 * @param fraction how far through the current game tick this frame is, 0..1
	 */
	void onFrame(WorldView worldView, float fraction)
	{
		// An index loop rather than a for-each, and the field rather than roster(): this
		// runs at the frame rate, so it allocates no iterator and builds nothing.
		for (int i = 0; i < followers.size(); i++)
		{
			Follower follower = followers.get(i);
			try
			{
				follower.advanceFrame(worldView, fraction);
			}
			catch (RuntimeException e)
			{
				log.warn("{}: threw during the frame pass, not retrying",
					follower.getFigure().label(), e);
				follower.markBroken();
				follower.despawn();
			}
		}
	}

	/**
	 * The scene is being replaced or is gone: every {@code LocalPoint} held is about to
	 * mean something else, so nothing may stay active.
	 *
	 * @param reason the game state that caused it, for the log line
	 */
	void invalidate(String reason)
	{
		int deactivated = despawnAll();
		for (Follower follower : followers)
		{
			// A scene load is the right granularity at which to re-test a cold cache.
			follower.onSceneEntered();
		}

		lastResolution = null;

		if (deactivated > 0)
		{
			log.debug("{}: deactivated {} follower(s)", reason, deactivated);
		}
	}

	/**
	 * Takes the entourage off the screen for good.
	 *
	 * <p><b>Only the followers that really came off the screen are forgotten.</b>
	 * {@link Follower#despawn()} catches its own {@code RuntimeException}, marks the
	 * follower broken and returns {@code false}, so a client that threw out of
	 * {@code removeRuneLiteObject} would leave an object registered — and an
	 * unconditional {@code clear()} here would then drop the last reference to it. That
	 * is precisely the artefact this method exists to prevent: a figure standing in the
	 * world that nothing owns and nothing short of a client restart can remove. Keeping
	 * the reference costs a pointer and leaves a later pass something to try again with.
	 *
	 * @return how many followers were actually deactivated. Counted from the client's
	 * own registered-object list rather than from local bookkeeping, so it is evidence
	 * rather than an assertion about our own intentions.
	 */
	int shutdown()
	{
		int deactivated = retire();
		lastResolution = null;

		log.debug("shutdown deactivated {} follower(s)", deactivated);
		return deactivated;
	}

	/**
	 * Takes every follower off the screen and forgets the ones that really went.
	 *
	 * <p>Shared by {@link #shutdown()} and by the figure swap, because they need exactly
	 * the same guarantee: a follower whose object could not be deactivated is kept, so
	 * that something still holds the reference and a later pass can try again. The
	 * alternative — clearing the list unconditionally — drops the last reference to an
	 * object the client still has, which is a figure standing in the world that nothing
	 * owns and nothing short of a client restart can remove.
	 *
	 * @return how many followers were actually deactivated
	 */
	private int retire()
	{
		int deactivated = despawnAll();

		followers.removeIf(follower -> !stillRegistered(follower));
		rosterFigure = null;

		if (!followers.isEmpty())
		{
			log.warn("could not deactivate {} follower(s) — holding the reference(s) "
				+ "rather than leaking the object(s)", followers.size());
		}

		return deactivated;
	}

	/**
	 * @return whether the client still has this follower's object, treating a throw as
	 * "yes". A client that will not answer is not one to take at its word, and the two
	 * mistakes are not symmetrical: a follower still held can be despawned again, one
	 * already dropped cannot.
	 */
	private static boolean stillRegistered(Follower follower)
	{
		try
		{
			return follower.isActive();
		}
		catch (RuntimeException e)
		{
			log.warn("{}: threw when asked whether it is still registered",
				follower.getFigure().label(), e);
			return true;
		}
	}

	/** @return the followers this scene holds, for the tests. Never null. */
	List<Follower> getFollowers()
	{
		return followers;
	}

	/**
	 * The followers to run this tick, built on first use and rebuilt when the configured
	 * figure changes.
	 *
	 * <p>Built lazily rather than in the constructor so that a plugin which is enabled
	 * and never logged in has allocated nothing.
	 *
	 * <p><b>One follower.</b> Getting a single figure to walk correctly is what this
	 * plugin does today; a formation of four figures doing it wrong is not four times
	 * the feature. This loop is the seam a formation extends — it is already a list, and
	 * {@link FormationSlot} already turns a slot into a tile — and nothing else has to
	 * change to make a second one appear.
	 */
	private List<Follower> roster(EntourageSettings settings)
	{
		EntourageFigure figure = settings.getFigure();

		if (rosterFigure != null && rosterFigure != figure)
		{
			log.debug("figure changed from {} to {}, retiring the roster", rosterFigure, figure);
			retire();
		}

		if (followers.isEmpty())
		{
			// The starting tile is a placeholder: a follower that is not active places
			// itself on the anchor before it spawns, every tick, so this is only ever
			// the value held for the few microseconds before that happens.
			followers.add(new Follower(client, figure, new WorldPoint(0, 0, 0)));
			rosterFigure = figure;
			log.debug("roster is {} follower(s): {}", followers.size(), figure.label());
		}

		return followers;
	}

	private int despawnAll()
	{
		int deactivated = 0;
		for (int i = 0; i < followers.size(); i++)
		{
			if (followers.get(i).despawn())
			{
				deactivated++;
			}
		}
		return deactivated;
	}
}
