package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>The one thing that does rebuild is a change of roster</b>, and it has to: a
 * figure decides which {@code NPCComposition} its model was merged out of, so a new
 * figure is a new model, and a follower's place in the roster decides which station of
 * the formation it stands on. Both are noticed here, at the top of the tick, by comparing
 * the configured list of figures against the one the roster was built for — rather than
 * by subscribing to {@code ConfigChanged}. One list compare per tick cannot miss an
 * event, cannot fire in the wrong order relative to the tick that is about to use the
 * roster, and cannot fire while the player is logged out; and the tick is 600ms, which
 * is as immediate as a settings change needs to be. The retirement goes through the
 * same "only forget what really came off the screen" path {@link #shutdown()} uses,
 * because a roster swap must not be a way to leak the old figures.
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
	 * Who is talking, and the cap on how many may be. Owned here rather than by
	 * {@link Follower} because "how many lines are on screen" is a property of the
	 * entourage rather than of any one figure — see {@link EntourageChatter}.
	 */
	private final EntourageChatter chatter = new EntourageChatter();

	/**
	 * The bodies {@link #followers} was built for, in order, or {@code null} when there
	 * is no roster.
	 *
	 * <p>Kept separately from the followers' own bodies so that a retirement which could
	 * not let go of its objects — see {@link #retire()} — does not turn into a rebuild
	 * attempt on every subsequent tick, and one warning per tick with it.
	 *
	 * <p><b>The whole list, compared by value.</b> The roster changes when a figure
	 * changes, when the count changes, and when two figures swap places — all three are a
	 * different set of bodies in a different order, and all three need the same rebuild.
	 * A comparison that only watched the count would leave the wrong figures on screen;
	 * one that only watched the first slot would leave four of them. It is also what
	 * makes a change to any of the five typed NPC ids a rebuild:
	 * {@link FollowerBody} has value equality precisely so that the typed id is part of
	 * this comparison rather than a second thing to remember to check.
	 */
	@Nullable
	private List<FollowerBody> rosterBodies;

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

		if (settings.hideInInstances() && worldView.isInstance())
		{
			// A raid, a quest cutscene, the Inferno. Deactivating rather than merely not
			// drawing, for the same reason the no-anchor branch does: an object left
			// registered is a figure standing in an instance that this pass has stopped
			// looking after. Asked of the view rather than of Client.isInInstancedRegion(),
			// which answers for whichever view the client currently has selected — this
			// one is the view the anchor was judged against.
			despawnAll();
			return;
		}

		for (Follower follower : roster(settings))
		{
			try
			{
				follower.onGameTick(anchor, worldView, settings);
			}
			catch (RuntimeException e)
			{
				// One follower's bad luck costs that follower. Letting this out of an
				// EventBus handler abandons every follower after it in the pass —
				// including any that were supposed to deactivate. Latched as well as
				// caught: a follower that throws every tick would otherwise be a warning
				// every tick, forever.
				log.warn("{}: threw during the tick pass, not retrying",
					follower.label(), e);
				follower.markBroken();
				follower.despawn();
			}
		}

		// After the followers, not before: a follower that spawned this tick is on screen
		// by now, and one that was latched broken above is off it, so the chatter's
		// isActive() check sees this tick's answer rather than last tick's.
		chatter.onGameTick(followers, settings);
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
					follower.label(), e);
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

		// A fresh world starts a fresh cadence rather than inheriting a phase from the
		// last one — and nothing is left holding a line said before the scene changed.
		chatter.reset(followers);

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
		chatter.reset(followers);
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
		rosterBodies = null;

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
				follower.label(), e);
			return true;
		}
	}

	/** @return the followers this scene holds, for the tests. Never null. */
	List<Follower> getFollowers()
	{
		return followers;
	}

	/**
	 * @return the dialogue cadence, for the tests. Never null.
	 *
	 * <p>Its clock is the only part of a scene invalidation that has no other visible
	 * effect: the lines themselves are cleared by the despawn that comes with it, so
	 * "the phase restarts on a scene load" cannot be seen from outside without asking.
	 */
	EntourageChatter getChatter()
	{
		return chatter;
	}

	/**
	 * The followers to run this tick, built on first use and rebuilt when the configured
	 * roster changes.
	 *
	 * <p>Built lazily rather than in the constructor so that a plugin which is enabled
	 * and never logged in has allocated nothing.
	 *
	 * <p><b>Each follower keeps the index it was built with</b>, and that index is what
	 * {@link EntourageFormation} turns into a station — so the roster order in the
	 * settings really is the order the figures stand in. Rebuilding rather than
	 * renumbering is what makes that safe: a follower's index never changes under it, so
	 * nothing has to reason about a figure whose station moved without it walking there.
	 *
	 * <p><b>A parked pin survives the rebuild; nothing else does.</b> Every other field a
	 * follower carries is specific to the object the model was merged into, so throwing it
	 * away and starting cold is correct — but {@link Follower#getFrozenTile()} names a place
	 * in the world the user chose on purpose, and a roster edit is not the same event as
	 * asking the group to walk again. {@link #matchPins} works out which new index each old
	 * pin belongs to, and {@link Follower#adoptPin} hands it over before the new object's
	 * first tick, so the same-tick re-spawn below never sees a null pin it should not have.
	 */
	private List<Follower> roster(EntourageSettings settings)
	{
		List<FollowerBody> bodies = settings.getBodies();
		Map<Integer, Pin> pins = null;

		if (rosterBodies != null && !rosterBodies.equals(bodies))
		{
			log.debug("roster changed from {} to {}, retiring it", rosterBodies, bodies);

			// Matched before retire() clears followers below. If retire() cannot let go of
			// every object — see its own javadoc — followers stays non-empty, the
			// construction loop below never runs, and this snapshot is discarded unused
			// rather than adopted by a rebuild that did not actually happen.
			pins = matchPins(rosterBodies, followers, bodies);
			retire();
		}

		if (followers.isEmpty())
		{
			for (int index = 0; index < bodies.size(); index++)
			{
				// The starting tile is a placeholder: a follower that is not active places
				// itself on the anchor before it spawns, every tick, so this is only ever
				// the value held for the few microseconds before that happens.
				Follower follower = new Follower(client, bodies.get(index), index,
					new WorldPoint(0, 0, 0));

				Pin pin = pins == null ? null : pins.get(index);
				if (pin != null)
				{
					follower.adoptPin(pin.tile, pin.inInstance);
				}

				followers.add(follower);
			}

			// The list out of EntourageSettings is unmodifiable, so keeping the reference
			// is keeping a snapshot rather than aliasing something that can change.
			rosterBodies = bodies;
			log.debug("roster is {} follower(s): {}", followers.size(), bodies);
		}

		return followers;
	}

	/**
	 * Matches this tick's bodies against the ones {@code oldFollowers} was built for, so a
	 * parked pin travels to whichever new index its own figure lands on rather than staying
	 * nailed to a slot number that may now name someone else.
	 *
	 * <p><b>The three shapes a roster edit takes in this plugin, and only these:</b> a value
	 * swapped in at one or more slots with the count unchanged ({@link RosterEdit#assign},
	 * {@link RosterEdit#setCustomNpcId}), one slot added at the end
	 * ({@link RosterEdit#add}), or one slot removed with the tail shifted down to close the
	 * gap ({@link RosterEdit#remove} — "everybody after it moves up", in its own words).
	 * Nothing here produces any other shape in a single settings change, so this does not
	 * have to solve the general list-diff problem — only tell these three apart and refuse
	 * to guess at anything it cannot.
	 *
	 * <p><b>Falls back to the slot number rather than to nothing — and each old pin is
	 * handed out at most once.</b> An earlier version of this method refused outright on
	 * any shape it could not verify, reasoning that handing a pin to the wrong figure is
	 * worse than handing out none. That is the wrong way round here, and the reason is what
	 * "none" actually does: a slot left without a pin does not stay where it is, it re-parks
	 * on the player — see {@link Follower#respawnTile} — which is the exact defect this
	 * method exists to close, and it is every bit as silent as a misattribution. So an index
	 * the shapes above cannot vouch for takes a pin rather than nothing: its own index's, if
	 * that pin has not already been handed to a different new index, and otherwise the
	 * lowest-numbered old index with a pin that has not. The {@code consumed} bookkeeping
	 * below — shared with the prefix loop and the shrink branch above, not private to the
	 * fallback — is what makes that safe: two new indices can never draw the same tile,
	 * because each old pin leaves the pool the first time it is handed out.
	 *
	 * <p>Correcting an earlier version of this same javadoc: the cost of a fallback index is
	 * <b>not</b> "a figure standing on a tile one of its neighbours was standing on" — that
	 * described a neighbour that had not moved, which is exactly the duplicate this method
	 * now refuses to produce. What it can still cost is a live pin moving to a different new
	 * index than the one it was recorded under: an index whose own old counterpart has
	 * nothing to give — because that counterpart never parked, or its slot was the one the
	 * edit actually removed — takes the next available pin instead, inside a group that is
	 * already clustered together and was put there on purpose. The alternative is still what
	 * it always was: five figures appearing on top of the player at a boss, which is the one
	 * thing "stay put" promises will not happen. Only an index past the end of the old
	 * roster, or one for which no old pin is left unclaimed at all, ends with none.
	 *
	 * @param oldBodies    the roster {@code oldFollowers} was built for
	 * @param oldFollowers the followers about to be retired, parallel to {@code oldBodies}
	 * @param newBodies    this tick's roster
	 * @return this tick's pins, keyed by the new index each belongs to. An index is absent
	 * only when nothing was parked there and no other old pin was left to give it — never
	 * merely because the edit's shape could not be identified, and never the same pin as any
	 * other index in the map.
	 */
	private static Map<Integer, Pin> matchPins(List<FollowerBody> oldBodies,
		List<Follower> oldFollowers, List<FollowerBody> newBodies)
	{
		int oldSize = oldBodies.size();
		int newSize = newBodies.size();
		int delta = oldSize - newSize;

		// Every index before the first divergence is untouched by whatever edit this was —
		// true regardless of which of the three shapes above it turns out to be — so its
		// pin is still exactly right.
		int prefix = 0;
		int limit = Math.min(oldSize, newSize);
		while (prefix < limit && oldBodies.get(prefix).equals(newBodies.get(prefix)))
		{
			prefix++;
		}

		Map<Integer, Pin> pins = new HashMap<>();

		// Which old indices have already supplied a pin — checked before the fallback below
		// hands one out a second time. Marked only inside addPin, and only when a pin was
		// actually recorded, so an old follower with nothing parked is never "used up" and
		// stays out of everyone's way.
		boolean[] consumed = new boolean[oldSize];

		for (int index = 0; index < prefix; index++)
		{
			addPin(pins, index, index, oldFollowers, consumed);
		}

		if (delta == 0)
		{
			// Same count, so nothing shifted — RosterEdit only ever moves the count when it
			// moves a figure. Whatever changed at or after the first divergence is a value
			// swapped in at that slot, and the slot keeps its own tile regardless of who is
			// standing on it now, which is what "a figure changes in one slot" means for a
			// pin — see Follower#frozenTile.
			for (int index = prefix; index < newSize; index++)
			{
				addPin(pins, index, index, oldFollowers, consumed);
			}
		}
		else if (delta > 0 && prefix + delta <= oldSize
			&& oldBodies.subList(prefix + delta, oldSize).equals(newBodies.subList(prefix, newSize)))
		{
			// A shrink that really is "one run removed, the tail shifted down to close the
			// gap". Verified rather than assumed: the plain RuneLite config screen can also
			// cut the follower count directly, which drops the tail without shifting
			// anything — that shape fails this check and falls through to the fallback
			// below instead of being shifted anyway.
			for (int index = prefix; index < newSize; index++)
			{
				addPin(pins, index, index + delta, oldFollowers, consumed);
			}
		}

		// A growth (delta < 0) needs nothing further from a shape of its own: RosterEdit.add()
		// only ever appends, so the prefix loop above already covers every index that survived.

		// Whatever is still unaccounted for takes a pin rather than nothing — this is the
		// floor described above, and it is what makes "a roster edit never puts the group
		// back on the player" true for every shape rather than only the three recognised
		// ones. First choice is the index's own old counterpart, if nothing has claimed it
		// yet; failing that, the lowest-numbered old index with a pin still unclaimed. The
		// search resumes from where it left off rather than restarting at zero each time —
		// an index once found unclaimed-and-empty or already consumed stays that way, so
		// re-checking it for a later index can only ever repeat the same answer.
		int nextUnclaimed = 0;
		for (int index = prefix; index < newSize; index++)
		{
			if (pins.containsKey(index))
			{
				continue;
			}

			if (index < oldSize && !consumed[index] && oldFollowers.get(index).getFrozenTile() != null)
			{
				addPin(pins, index, index, oldFollowers, consumed);
				continue;
			}

			while (nextUnclaimed < oldSize
				&& (consumed[nextUnclaimed] || oldFollowers.get(nextUnclaimed).getFrozenTile() == null))
			{
				nextUnclaimed++;
			}

			if (nextUnclaimed < oldSize)
			{
				addPin(pins, index, nextUnclaimed, oldFollowers, consumed);
			}
		}

		return pins;
	}

	/**
	 * Records old index {@code oldIndex}'s pin under new index {@code newIndex}, unless it
	 * has none — and marks {@code oldIndex} spent in {@code consumed} when it does, so
	 * nothing else in {@link #matchPins} can hand the same pin to a second new index.
	 */
	private static void addPin(Map<Integer, Pin> pins, int newIndex, int oldIndex,
		List<Follower> oldFollowers, boolean[] consumed)
	{
		Follower oldFollower = oldFollowers.get(oldIndex);
		WorldPoint tile = oldFollower.getFrozenTile();
		if (tile != null)
		{
			pins.put(newIndex, new Pin(tile, oldFollower.isFrozenInInstance()));
			consumed[oldIndex] = true;
		}
	}

	/**
	 * One follower's frozen tile and the instance-ness it was recorded under, carried as a
	 * pair so {@link Follower#adoptPin} can never receive one without the other — a tile
	 * carried without its flag could be honoured under the wrong instance-ness.
	 */
	private static final class Pin
	{
		private final WorldPoint tile;
		private final boolean inInstance;

		private Pin(WorldPoint tile, boolean inInstance)
		{
			this.tile = tile;
			this.inInstance = inInstance;
		}
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
