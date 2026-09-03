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
 * <p><b>Client thread only.</b> Every method here reaches live client state, directly
 * or through {@link Follower}.
 */
@Slf4j
@Singleton
class EntourageScene
{
	private final Client client;
	private final List<Follower> followers = new ArrayList<>();

	/**
	 * The last resolution reported, so the log says "the anchor went away" once rather
	 * than sixteen hundred times a minute.
	 */
	@Nullable
	private FollowerAnchor.Resolution lastResolution;

	@Inject
	EntourageScene(Client client)
	{
		this.client = client;
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

		WorldPoint tile = anchor.getTile();
		for (Follower follower : roster())
		{
			try
			{
				follower.onGameTick(tile, worldView);
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
	 * @return how many followers were actually deactivated. Counted from the client's
	 * own registered-object list rather than from local bookkeeping, so it is evidence
	 * rather than an assertion about our own intentions.
	 */
	int shutdown()
	{
		int deactivated = despawnAll();
		followers.clear();
		lastResolution = null;
		log.debug("shutdown deactivated {} follower(s)", deactivated);
		return deactivated;
	}

	/** @return the followers this scene holds, for the tests. Never null. */
	List<Follower> getFollowers()
	{
		return followers;
	}

	/**
	 * Builds the roster on first use rather than in the constructor, so that a plugin
	 * that is enabled and never logged in has allocated nothing.
	 */
	private List<Follower> roster()
	{
		if (followers.isEmpty())
		{
			for (EntourageFigure figure : EntourageFigure.DEFAULT_ROSTER)
			{
				// The starting tile is a placeholder: a follower that is not active
				// places itself on the anchor before it spawns, every tick, so this is
				// only ever the value held for the few microseconds before that
				// happens.
				followers.add(new Follower(client, figure, new WorldPoint(0, 0, 0)));
			}
			log.debug("roster is {} follower(s)", followers.size());
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
