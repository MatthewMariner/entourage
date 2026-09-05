package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * One {@link EntourageFigure} bound to one {@link RuneLiteObject}.
 *
 * <p><b>Every method here must run on the client thread.</b> All of it reaches into
 * live client state: {@code loadModelData} and {@code mergeModels} read the model
 * cache, {@code light} allocates against it, {@code setActive} adds to and removes
 * from the client's registered-object list, and
 * {@code RuneLiteObject.setLocation(LocalPoint, int)} runs
 * {@code Perspective.getTileHeight(client, ..)} against the live scene to work out
 * {@code z}. That last one is the {@link RuneLiteObject} override — the base
 * {@code RuneLiteObjectController.setLocation} really does nothing but
 * {@code setX}/{@code setY}/{@code setWorldView}/{@code setLevel}, so reading only
 * the base class makes the call look thread-safe when it is not. The client does the
 * height fix-up; this plugin never writes {@code z}.
 *
 * <p><b>Movement is split across two clocks, and which half does what is not an
 * implementation detail:</b>
 * <ul>
 *   <li>{@link #onGameTick} runs once per game tick. It steps the walk one tile or two,
 *       points the follower — the way it is going while it is going somewhere, and
 *       {@link FollowerFacing}'s answer while it is not — and switches between the idle,
 *       walk and run animations.</li>
 *   <li>{@link #advanceFrame} runs once per rendered frame. It slides the drawn
 *       position between the tile the follower left and the tile it is heading for.
 *       <b>Nothing else per frame</b> — in particular not
 *       {@code RuneLiteObject.tick(..)}, which the client already calls once per frame
 *       for every registered object and which is what advances the animation. A
 *       second caller runs every animation at double speed; the API javadoc says as
 *       much and {@code FollowerTest} pins the count at zero.</li>
 * </ul>
 *
 * <p><b>All three animation controllers are built once and kept.</b> An
 * {@code AnimationController} <i>is</i> the animation's playback position:
 * constructing one, or calling {@code setAnimation} on one, resets its frame to zero.
 * Re-creating a controller on every idle-to-walk switch is what turns a walk cycle
 * into a stutter — the client advances the frames between game ticks and something
 * then throws that progress away. Holding all three means a follower that stops and
 * starts resumes its stride instead of restarting it.
 *
 * <p><b>The idle controller is the one exception, and only when the pose changes.</b>
 * {@link EntouragePose} is a setting, so the animation the idle slot should hold can
 * change while the follower is standing there. The controller therefore remembers which
 * animation it was built for and is thrown away when that answer changes — which is a
 * rebuild the user asked for, once, rather than one per tick.
 *
 * <p><b>Two kinds of failure, kept apart, because caching the wrong one loses the
 * follower for the whole session:</b>
 * <ul>
 *   <li><b>Structural</b> — the client refuses to create an object, the merge returns
 *       nothing, lighting returns nothing, {@code setActive(true)} does not take, or
 *       anything throws. Nothing about the next tick will be different, so the
 *       follower is marked {@link #broken} and never retried. That is what stops a bad
 *       model producing one warning per game tick forever.</li>
 *   <li><b>Transient</b> — {@code loadModelData} returned null, the composition would
 *       not resolve, or {@code loadAnimation} returned null. On a cold cache all three
 *       are routine and say nothing about the id, so none is latched; each is retried,
 *       at most {@link #MAX_ATTEMPTS} times per {@link #onSceneEntered()} and spaced by
 *       {@link #RETRY_BACKOFF_TICKS}.</li>
 * </ul>
 * The difference is which way the follower fails while it waits. A missing model means
 * no figure at all, so the spawn is deferred. A missing animation means a figure in the
 * right place holding still, which is better than an absent one — so it spawns, and the
 * animation is picked up whenever the cache produces it.
 */
@Slf4j
final class Follower
{
	/**
	 * How many times a cache miss is retried before this follower gives up until the
	 * next scene load. Three, spaced by {@link #RETRY_BACKOFF_TICKS}, so a genuinely
	 * absent id costs three attempts and one warning per scene load rather than one
	 * per tick.
	 */
	static final int MAX_ATTEMPTS = 3;

	/**
	 * Game ticks between retries. The cache this is waiting on warms up over seconds,
	 * not ticks: three attempts on three consecutive ticks would spend the whole
	 * budget inside two seconds of a cold login and then leave the follower waiting for
	 * a border crossing. At 600ms a tick this spreads them over roughly fifteen
	 * seconds.
	 */
	static final int RETRY_BACKOFF_TICKS = 25;

	// --- The lighting rig for a figure standing in the world -----------------
	//
	// Not ModelData's DEFAULT_* constants, and the difference is visible. Those five
	// (64, 768, -50, -10, -50) are what ModelData.light() with no arguments uses, and
	// that is the *widget* rig: disassembled from the 1.12.38 injected client, the
	// ModelData implementation's no-argument light() is a single call to
	// light(64, 768, -50, -10, -50). This plugin called it, so a follower walking
	// through Varrock was lit like a model in an interface.
	//
	// The five below are what the client lights a figure in the world with, read out of
	// the same disassembly rather than copied off another plugin: the PlayerComposition
	// implementation lights an assembled player with exactly (64, 850, -30, -50, -30),
	// and the NPCComposition implementation lights an NPC with the same five. The two
	// hub-published plugins that build world figures this way — follower-buddy and
	// jebscape — both use these numbers, which is corroboration rather than the source.
	//
	// Written out rather than named because ModelData carries constants for the widget
	// rig only; there is no WORLD_AMBIENT on the interface to point at.

	/** Ambient light. The one value the two rigs agree on. */
	private static final int WORLD_AMBIENT = 64;

	/** Contrast: 850 in the world against the interface rig's 768. */
	private static final int WORLD_CONTRAST = 850;

	/** The light vector's x. */
	private static final int WORLD_LIGHT_X = -30;

	/** The light vector's y. */
	private static final int WORLD_LIGHT_Y = -50;

	/** The light vector's z. */
	private static final int WORLD_LIGHT_Z = -30;

	private final Client client;
	private final FollowerBody body;

	/**
	 * Which follower of the roster this is, 0-based.
	 *
	 * <p>It is the whole of what makes one follower different from another beyond the body
	 * it wears: {@link EntourageFormation} turns it into a station, and
	 * {@link FollowerRemarks} mixes it into the seed so that two followers wearing the
	 * <i>same</i> figure still draw from two streams and still become due on two different
	 * ticks. Fixed for this follower's whole life, because a roster whose size or
	 * membership changes is retired and rebuilt rather than renumbered — see
	 * {@link EntourageScene}.
	 */
	private final int index;

	private final FollowerWalk walk;

	/**
	 * What this follower is saying. Always present — every figure ships lines, and a
	 * follower with nothing to say is one whose dialogue is switched off rather than one
	 * that lacks the machinery.
	 */
	private final FollowerRemarks remarks;

	private RuneLiteObject object;
	private Model model;
	private boolean broken;

	@Nullable
	private FollowerAppearance appearance;

	/**
	 * The three controllers, built on first use and then kept — see the class javadoc.
	 *
	 * <p><b>Only ever assigned a controller that actually has an animation.</b>
	 * {@link #looping} returns null on a failed load and these stay null, so the next
	 * call tries again. Caching a controller whose animation is null is caching a
	 * permanently inert object: its {@code tick}, {@code loop} and
	 * {@code getPackedFrame} all return immediately, and the figure would draw its base
	 * model unanimated for the rest of the session on one cold-cache miss.
	 */
	private AnimationController idleController;
	private AnimationController walkController;
	private AnimationController runController;

	/**
	 * Which animation {@link #idleController} was built for, so that changing the pose
	 * setting throws away exactly the controller that is now wrong.
	 *
	 * <p>Kept alongside the controller rather than derived from it because
	 * {@code AnimationController} will not say which id it was given: it exposes the
	 * {@code Animation} it resolved, and this plugin has no way to turn one back into a
	 * sequence id. Only ever read while {@link #idleController} is non-null.
	 *
	 * <p>A sequence id rather than an {@link EntourageAnimation}, because a custom body's
	 * stand is a number out of the cache and has no enum constant naming it.
	 */
	private int installedIdleAnimationId;

	/**
	 * The animations read out of the cache for a custom body, or {@code null} — for a
	 * preset, for a custom body whose id has not resolved yet, and for one that was
	 * refused. {@link EntourageFigure}'s own triple is used whenever this is null, which
	 * makes "wear the preset instead" a single assignment rather than a second code path.
	 */
	@Nullable
	private NpcRecord customAnimations;

	/**
	 * True once a typed NPC id has been turned down for good, at which point this follower
	 * is the preset its slot's dropdown names.
	 *
	 * <p><b>Latched, because both reasons for it are permanent.</b> Either the archive
	 * does not have the id at all, or the id's record declares no usable stand-and-walk
	 * pair — see {@link NpcRecord#hasWalkCycle()}. Neither changes on the next tick, and
	 * retrying either would be one warning per tick forever. The retryable case — the
	 * cache not having answered yet — never reaches here; it goes through the same attempt
	 * budget as a cold model cache.
	 */
	private boolean customRejected;

	/** The controller currently handed to the object, for the identity compare. */
	private AnimationController installed;

	/**
	 * True once the object sits at a position that cannot change again before the next
	 * game tick — the follower has stopped and the frame pass has already placed it.
	 *
	 * <p>Cleared by every spawn, every despawn and every tick, so the follower is
	 * placed at least once per game tick and is skipped only while it is genuinely
	 * standing still. {@code setLocation} is not free: it runs
	 * {@code Perspective.getTileHeight} against the live scene.
	 */
	private boolean positionSettled;

	private int attempts;
	private int ticksSinceAttempt;

	Follower(Client client, FollowerBody body, int index, WorldPoint start)
	{
		this.client = client;
		this.body = body;
		this.index = index;
		this.walk = new FollowerWalk(start);
		this.remarks = new FollowerRemarks(body.getFigure(), index);
	}

	/** @return whose body this follower wears — a preset, or an id the user typed */
	FollowerBody getBody()
	{
		return body;
	}

	/**
	 * @return the preset behind this follower: the figure itself for a preset body, and
	 * the fallback for a custom one. What it says and what it falls back to both come from
	 * here, so a custom follower still has lines and still has a stand to hold.
	 */
	EntourageFigure getFigure()
	{
		return body.getFigure();
	}

	/**
	 * @return what this follower is called in a log line. Follows the body it is actually
	 * wearing: a custom id that was refused says the preset's name from then on, because
	 * that is what is standing there.
	 */
	String label()
	{
		return customRejected ? body.getFigure().label() : body.label();
	}

	/**
	 * @return the NPC this follower is dressed from: the typed id while it is still in
	 * play, and the slot's own preset once that id has been refused. Read after
	 * {@link #resolveBody()}, which is what makes the second answer possible.
	 */
	private int npcId()
	{
		return customRejected ? body.getFigure().getNpcId() : body.getNpcId();
	}

	/**
	 * @return what to draw over this follower's head when the name label is switched on.
	 *
	 * <p><b>A custom body answers with the NPC's own name, and that is the one visible
	 * confirmation the feature has.</b> A typed id that worked puts that NPC's name over
	 * the figure; one that was refused puts the dropdown figure's name there instead, so
	 * "did my id take?" is answerable without opening a log. The composition's name is
	 * used only when it is a real one — the cache's own placeholder for an unnamed NPC is
	 * the four characters {@code null}, and a follower labelled "null" would read as a bug
	 * rather than as an unnamed body.
	 */
	String getDisplayName()
	{
		if (body.isCustom() && !customRejected && appearance != null)
		{
			String npcName = appearance.getNpcName();
			if (npcName != null && !npcName.isEmpty() && !"null".equals(npcName))
			{
				return npcName;
			}
		}

		return body.getFigure().getDisplayName();
	}

	/** @return which follower of the roster this is, 0-based */
	int getIndex()
	{
		return index;
	}

	FollowerWalk getWalk()
	{
		return walk;
	}

	/** @return what this follower is saying, and whether it is saying anything */
	FollowerRemarks getRemarks()
	{
		return remarks;
	}

	boolean isBroken()
	{
		return broken;
	}

	/**
	 * Latches this follower out of every later pass, without a log line — the caller has
	 * more context and does the logging.
	 *
	 * <p>For {@link EntourageScene} to use when a throw came from outside {@link #spawn}
	 * and {@link #despawn}, which already latch their own. Without it, a follower that
	 * throws on every tick is a warning on every tick, forever.
	 */
	void markBroken()
	{
		broken = true;
	}

	/**
	 * @return whether the client currently has this object registered. Asks the client
	 * rather than trusting local bookkeeping, so teardown evidence is real.
	 */
	boolean isActive()
	{
		return object != null && object.isActive();
	}

	/**
	 * Hands back the retry budget. Called when the scene is rebuilt: a scene load is
	 * the right granularity at which to re-test a cold cache.
	 */
	void onSceneEntered()
	{
		attempts = 0;
		ticksSinceAttempt = 0;
	}

	/**
	 * Builds (once) and activates the object at the follower's current tile.
	 *
	 * @return true if the object is active when this returns
	 */
	private boolean spawn(WorldView worldView, EntourageSettings settings)
	{
		try
		{
			return trySpawn(worldView, settings);
		}
		catch (RuntimeException e)
		{
			// This runs from an EventBus handler. Letting it out abandons the rest of
			// the pass — including anything that was supposed to deactivate — and does
			// it again next tick, because nothing would have marked the offender.
			broken = true;
			log.warn("{}: threw while spawning, not retrying", label(), e);
			return false;
		}
	}

	/**
	 * Deactivates the object if it is active.
	 *
	 * @return true if this call actually deactivated something
	 */
	boolean despawn()
	{
		try
		{
			if (!isActive())
			{
				return false;
			}

			object.setActive(false);
			positionSettled = false;

			// A follower that is not on screen is not saying anything. Cleared here rather
			// than left for the next game tick, because the overlay draws between ticks:
			// this is what makes an orphaned line — text over ground with no figure under
			// it — impossible rather than merely brief.
			remarks.clear();

			log.debug("despawned {}", label());
			return true;
		}
		catch (RuntimeException e)
		{
			// Same reasoning as spawn(), and the stakes are higher: this runs from the
			// teardown loop that must reach every other follower.
			broken = true;
			log.warn("{}: threw while despawning", label(), e);
			return false;
		}
	}

	/**
	 * One game tick of being a follower: arrive if not here yet, otherwise step the
	 * walk, face the direction of travel, and switch animations.
	 *
	 * <p><b>A follower that is not active forms up on the anchor rather than resuming
	 * the tile it last held.</b> The only reasons it is inactive are that it has never
	 * spawned, that the scene it was standing in has been thrown away, or that its
	 * model is still coming out of a cold cache — and in all three the remembered tile
	 * either means nothing or means somewhere else now.
	 *
	 * <p>Re-selecting the controller every tick is also what retries an animation that
	 * missed: {@link #looping} is asked again, subject to the retry budget, until it
	 * hands back something real. There is no separate retry path, because a second one
	 * is a second definition of when a follower is allowed to touch the cache.
	 *
	 * @param anchor    where the player is and which way they are facing, resolved — see
	 *                  {@link FollowerAnchor}. Taken whole rather than as a tile because
	 *                  {@link FollowerFacing#AS_I_AM} needs the other half of it, and a
	 *                  second route from the player to here would be a second answer to
	 *                  "where is the player" — which is the mistake that class exists to
	 *                  make once.
	 * @param worldView the view the follower is walking in
	 * @param settings  this tick's configuration — the slot, the distances, the facing,
	 *                  the pose
	 */
	void onGameTick(FollowerAnchor anchor, WorldView worldView, EntourageSettings settings)
	{
		if (broken)
		{
			return;
		}

		// Ages the retry backoff whether or not anything else happens below — a
		// follower waiting on a cold model cache is inactive, and a counter that only
		// advanced for active followers would never let it try again.
		ticksSinceAttempt++;

		if (!isActive())
		{
			walk.placeAt(anchor.getTile());
			if (spawn(worldView, settings))
			{
				// A figure that appears already pointing the right way, rather than one
				// that appears facing south and turns 600ms later.
				object.setOrientation(orientationFor(anchor, settings));
			}
			return;
		}

		walk.tick(anchor.getTile(), worldView, settings, index);

		// select() compares controllers by identity, so a follower mid-walk re-selects
		// the one it already has and the object is left alone — which is what keeps the
		// animation's frame counter intact.
		select(controllerFor(settings));

		object.setOrientation(orientationFor(anchor, settings));

		// This tick may have moved the follower — including onto the tile it was
		// heading for, which is where it stops — so the frame pass has to take at least
		// one look before it may skip it again.
		positionSettled = false;
	}

	/**
	 * Which way to point the figure this tick.
	 *
	 * <p><b>A follower that moved faces the way it moved, whatever the setting says.</b>
	 * That is not a special case grudgingly carved out of the facing options — it is the
	 * rule, and the setting is what happens in its absence. A figure walking east while
	 * pointing north is moonwalking, and no dropdown should be able to ask for it.
	 *
	 * <p>{@link FollowerFacing#AT_ME} has one answerless case: the player standing on the
	 * follower's own tile, which happens for a tick after a recall and while the slot
	 * flips to the far side of a player who doubled back. There is no direction from a
	 * tile to itself, so the follower keeps the facing it had rather than being handed
	 * {@link StepOrientation#NOT_MOVING}, which is {@code -1} and not an orientation at
	 * all.
	 *
	 * <p><b>"The facing it had" is read back off the object rather than off the walk</b>,
	 * and the two are different answers now that the walk keeps the direction of travel:
	 * a follower that arrived from the south and turned east to look at you has a walk
	 * saying north and an object saying east. Falling back to the walk would spin it back
	 * to north for the one tick the player spends standing on it, which is a visible flick
	 * every time the follower is recalled.
	 */
	private int orientationFor(FollowerAnchor anchor, EntourageSettings settings)
	{
		if (walk.isMoving())
		{
			return walk.getOrientation();
		}

		int facing = settings.getFacing()
			.orientationFor(anchor.getTile(), anchor.getOrientation(), walk.currentTile());
		return facing == StepOrientation.NOT_MOVING ? object.getOrientation() : facing;
	}

	/**
	 * One frame of visual interpolation. This is the part that has to happen per frame
	 * rather than per game tick: without it a follower jumps a whole tile every 600ms.
	 *
	 * @param fraction how far through the current game tick this frame is, 0..1
	 */
	void advanceFrame(WorldView worldView, float fraction)
	{
		if (broken || !isActive() || positionSettled)
		{
			return;
		}

		LocalPoint location = walk.localPoint(worldView, fraction);
		if (location == null)
		{
			// Walking to a tile the client has not loaded. Leaving the object where it
			// is beats moving it somewhere that does not mean anything.
			return;
		}

		object.setLocation(location, walk.currentTile().getPlane());
		positionSettled = !walk.isMoving();
	}

	/** @return the controller driving the model, or {@code null} for a static one */
	@Nullable
	AnimationController getInstalledController()
	{
		return installed;
	}

	/**
	 * @return the appearance this follower is wearing, or {@code null} if it has not
	 * resolved one yet. For the tests: "it used the NPC's models" and "it is still
	 * waiting" are two outcomes that otherwise look identical from outside.
	 */
	@Nullable
	FollowerAppearance getAppearance()
	{
		return appearance;
	}

	// Two narrow read-only accessors for what the client is about to draw, rather than
	// handing out the RuneLiteObject itself. This class is the only writer of that
	// object's state, and a caller that could reach setActive or setLocation would be a
	// second definition of the lifecycle and of how fast a follower walks. They read the
	// object rather than the walk on purpose: the walk holds the tile, the object holds
	// the position the frame pass last put it at, and the difference between those two is
	// the whole of the interpolation.

	/**
	 * @return the local position the object currently holds, or {@code null} if it has
	 * never been placed. {@code RuneLiteObjectController.getLocation()} builds this from
	 * the x/y it was last given, so it is the frame pass's own answer rather than a
	 * second computation of it.
	 */
	@Nullable
	LocalPoint getRenderLocation()
	{
		return object == null ? null : object.getLocation();
	}

	/**
	 * @return the orientation the object is drawn at, in 0..2047. Falls back to the
	 * walk's own answer when there is no object yet.
	 */
	int getRenderOrientation()
	{
		return object == null ? walk.getOrientation() : object.getOrientation();
	}

	private boolean trySpawn(WorldView worldView, EntourageSettings settings)
	{
		if (broken)
		{
			return false;
		}

		if (isActive())
		{
			return true;
		}

		LocalPoint location = walk.localPoint(worldView, 1f);
		if (location == null)
		{
			// Outside the loaded scene. Not an error.
			return false;
		}

		if (object == null)
		{
			object = client.createRuneLiteObject();
			if (object == null)
			{
				log.warn("{}: client refused to create a RuneLiteObject", label());
				broken = true;
				return false;
			}
		}

		if (model == null)
		{
			if (!resolveBody())
			{
				// A custom id the cache has not answered for yet. Transient: nothing
				// latched, and nothing dressed from an id that may still be refused.
				return false;
			}

			List<ModelData> parts = loadParts();
			if (parts == null)
			{
				// Transient: nothing cached, nothing latched, try again later.
				return false;
			}

			model = assemble(parts);
			if (model == null)
			{
				broken = true;
				return false;
			}

			object.setModel(model);
			select(idleControllerOrNull(settings));
		}

		object.setOrientation(walk.getOrientation());
		object.setLocation(location, walk.currentTile().getPlane());
		object.setActive(true);

		if (!object.isActive())
		{
			// The client took the call and still does not have the object. That is not
			// going to change next tick, and leaving it unlatched is one warning per
			// tick forever.
			log.warn("{}: setActive(true) did not take, not retrying", label());
			broken = true;
			return false;
		}

		positionSettled = false;
		log.debug("spawned {} at {}", label(), walk.currentTile());
		return true;
	}

	/**
	 * Hands the object a controller, but only when it is not the one it already has.
	 * The identity compare is the whole point — see the class javadoc.
	 */
	private void select(@Nullable AnimationController controller)
	{
		if (controller == installed)
		{
			return;
		}

		installed = controller;
		object.setAnimationController(controller);
	}

	/**
	 * Picks the controller for what the walk says the follower is doing this tick.
	 *
	 * <p>Three states, in the order they degrade: a run falls back to the walk, and the
	 * walk falls back to the pose. Every fallback is a figure that is in the right place
	 * looking slightly wrong, which is always better than a figure that is not there.
	 */
	@Nullable
	private AnimationController controllerFor(EntourageSettings settings)
	{
		if (walk.isRunning())
		{
			return runControllerOrNull(settings);
		}
		return walk.isMoving() ? walkControllerOrNull(settings) : idleControllerOrNull(settings);
	}

	/**
	 * The idle controller, rebuilt if — and only if — the pose setting now names a
	 * different animation from the one it is holding.
	 */
	@Nullable
	private AnimationController idleControllerOrNull(EntourageSettings settings)
	{
		int wanted = idleAnimationId(settings);

		if (idleController != null && wanted != installedIdleAnimationId)
		{
			// The pose changed under a follower that is already standing there. Dropping
			// the controller is the whole cost: the model, the object and the retry
			// budget are all untouched.
			idleController = null;
		}

		if (idleController == null)
		{
			idleController = looping(wanted, "idle");
			// Unconditional, including when the load missed. It is only ever read
			// alongside a non-null controller — the reset above is guarded on one — so
			// "remember nothing on a miss" would be a branch no test could tell from this
			// line, and a cold-cache miss is retried either way by the null controller.
			installedIdleAnimationId = wanted;
		}

		return idleController;
	}

	/**
	 * @param settings this tick's configuration, for the pose
	 * @return the sequence to hold while standing still: whatever {@link EntouragePose}
	 * names, or — for {@link EntouragePose#FIGURE_DEFAULT} — the body's own stand, which
	 * is the preset's for a preset and the cache's for a custom id
	 */
	private int idleAnimationId(EntourageSettings settings)
	{
		EntourageAnimation pose = settings.getIdlePose().getAnimation();
		if (pose != null)
		{
			return pose.getId();
		}

		return customAnimations == null
			? body.getFigure().getIdleAnimation().getId()
			: customAnimations.getStandingAnimation();
	}

	/** @return the sequence to play while covering one tile in a game tick */
	private int walkAnimationId()
	{
		return customAnimations == null
			? body.getFigure().getWalkAnimation().getId()
			: customAnimations.getWalkingAnimation();
	}

	/**
	 * @return the sequence to play while covering two tiles in a game tick, or something
	 * {@link NpcRecord#isLoadable(int)} refuses when the body declares none. Plenty of
	 * NPCs do not run, and that is what {@link #runControllerOrNull}'s fallback is for.
	 */
	private int runAnimationId()
	{
		return customAnimations == null
			? body.getFigure().getRunAnimation().getId()
			: customAnimations.getRunAnimation();
	}

	@Nullable
	private AnimationController walkControllerOrNull(EntourageSettings settings)
	{
		if (walkController == null)
		{
			walkController = looping(walkAnimationId(), "walk");
		}

		// A figure that could not load its walk keeps standing rather than freezing
		// into a static model mid-step. Still wrong-looking, but wrong in the way that
		// says "this figure is idle" instead of "this figure is a prop".
		return walkController == null ? idleControllerOrNull(settings) : walkController;
	}

	/**
	 * The run controller, falling back to the walk.
	 *
	 * <p>The fallback is not cosmetic. A follower covering two tiles with no run
	 * animation would slide, and a follower covering two tiles with a <i>walk</i> cycle
	 * slides half as much — which is the better of the two, and is what the client
	 * itself does to an NPC that has no run.
	 */
	@Nullable
	private AnimationController runControllerOrNull(EntourageSettings settings)
	{
		if (runController == null)
		{
			runController = looping(runAnimationId(), "run");
		}

		return runController == null ? walkControllerOrNull(settings) : runController;
	}

	/**
	 * Builds a looping controller, or returns {@code null} without caching anything if
	 * the client would not give us the animation.
	 *
	 * <p>Not {@code setAnimation(..)}: that is sugar for exactly this, and the looping
	 * half of the old API ({@code setShouldLoop}) is deprecated. An
	 * {@code AnimationController} defaults to {@code AnimationController::loop}, but
	 * say so explicitly so a future default change cannot silently make every follower
	 * freeze on its last frame.
	 *
	 * <p><b>The null check is the whole point.</b> The constructor swallows a failed
	 * load — it calls {@code client.loadAnimation(id)} and hands the result, null or
	 * not, straight to {@code setAnimation} — so the only way to tell is to ask the
	 * controller what animation it ended up with.
	 *
	 * @param animationId the sequence to play
	 * @param what        which slot this is, for the log line — {@code idle}, {@code walk}
	 *                    or {@code run}. A plain word rather than the enum constant,
	 *                    because a custom body's animations are numbers out of the cache
	 *                    and have no constant naming them.
	 */
	@Nullable
	private AnimationController looping(int animationId, String what)
	{
		if (!NpcRecord.isLoadable(animationId))
		{
			// Not an id the cache could answer for. The ordinary case is a custom body
			// that declares no run at all, which is most NPCs — see NpcRecord. Refused
			// before the budget is consulted rather than after: asking the client for
			// sequence -1 spends one of the three attempts the model and the walk need,
			// on a question whose answer is already known.
			return null;
		}

		if (!attemptAllowed())
		{
			return null;
		}

		AnimationController controller = new AnimationController(client, animationId);
		if (controller.getAnimation() == null)
		{
			spendAttempt();
			if (attempts == 1)
			{
				log.warn("{}: the {} animation (id {}) did not load — drawing it static for now; "
						+ "a cold cache is the usual cause, so it will be retried up to {} time(s) "
						+ "per scene load",
					label(), what, animationId, MAX_ATTEMPTS);
			}
			return null;
		}

		controller.setOnFinished(AnimationController::loop);
		return controller;
	}

	/**
	 * Works out what a custom body wears and how it moves, once.
	 *
	 * <p><b>Before the models, not after</b>, because the answer decides which NPC's
	 * models are asked for: a refused id falls all the way back to the preset, and
	 * resolving after {@link #loadParts()} would dress the follower from an id that is
	 * about to be turned down.
	 *
	 * <p>The three outcomes are {@link NpcArchive}'s, and each becomes one of this class's
	 * two failure kinds:
	 * <ul>
	 *   <li>not in the cache yet — <b>transient</b>. Costs an attempt, latches nothing.</li>
	 *   <li>not in the archive at all — <b>structural</b>, but structural about the
	 *       <i>id</i> rather than about the follower. The follower is not broken; it wears
	 *       the preset from its dropdown instead.</li>
	 *   <li>read, but with no usable stand-and-walk pair — the same. This is the case
	 *       {@link NpcRecord#hasWalkCycle()} exists for, and refusing it here is what stops
	 *       a typed id producing a figure that slides along the ground.</li>
	 * </ul>
	 *
	 * @return whether this follower now knows what it is wearing. Always true for a
	 * preset, and for a custom body that has been read or refused; false is the retryable
	 * case, and the caller must not spawn on it.
	 */
	private boolean resolveBody()
	{
		if (!body.isCustom() || customAnimations != null || customRejected)
		{
			return true;
		}

		if (!attemptAllowed())
		{
			return false;
		}

		NpcArchive read = NpcArchive.read(client, body.getNpcId(), body.label());

		if (read.getOutcome() == NpcArchive.Outcome.UNAVAILABLE)
		{
			spendAttempt();
			if (attempts == 1)
			{
				log.warn("{}: the cache has not produced npc {} yet — not spawning; a cold cache "
						+ "is the usual cause, so it will be retried up to {} time(s) per scene load",
					body.label(), body.getNpcId(), MAX_ATTEMPTS);
			}
			return false;
		}

		if (read.getOutcome() == NpcArchive.Outcome.ABSENT)
		{
			refuseCustomBody("there is no npc " + body.getNpcId() + " in the cache");
			return true;
		}

		NpcRecord record = read.getRecord();
		if (record == null || !record.hasWalkCycle())
		{
			refuseCustomBody("npc " + body.getNpcId() + " declares " + record
				+ ", which is not a stand and a walk a follower can be made to move with");
			return true;
		}

		customAnimations = record;
		log.debug("{}: npc {} animates as {}", body.label(), body.getNpcId(), record);
		return true;
	}

	/**
	 * Turns a typed id down for good and puts the slot's own figure back.
	 *
	 * <p><b>A warning rather than a silent fallback, and a fallback rather than an empty
	 * slot.</b> Refusing to spawn anything would leave the user staring at nothing with no
	 * way to tell a bad id from a plugin that had stopped working; wearing the preset shows
	 * that the setting was read and not used. The name label — see
	 * {@link #getDisplayName()} — is the half of this the user can see without a log.
	 */
	private void refuseCustomBody(String why)
	{
		customRejected = true;
		log.warn("{}: {}. Wearing {} instead — clear the \"Custom NPC id\" box, or try another id.",
			body.label(), why, body.getFigure().getDisplayName());
	}

	/**
	 * Loads every model this follower is made of.
	 *
	 * @return all of the requested parts, or {@code null} if even one did not load —
	 * never a partial list. A partial resolve is how a figure ends up with no head or
	 * no boots, and since the model is cached it would stay that way for the session.
	 */
	@Nullable
	private List<ModelData> loadParts()
	{
		if (!attemptAllowed())
		{
			return null;
		}

		int[] modelIds = modelIdsToBuild();
		if (modelIds == null)
		{
			return null;
		}

		List<ModelData> parts = new ArrayList<>(modelIds.length);
		StringBuilder missing = new StringBuilder();

		for (int modelId : modelIds)
		{
			ModelData part = client.loadModelData(modelId);
			if (part == null)
			{
				if (missing.length() > 0)
				{
					missing.append(", ");
				}
				missing.append(modelId);
				continue;
			}
			parts.add(part);
		}

		if (parts.size() == modelIds.length)
		{
			return parts;
		}

		spendAttempt();

		// One line for the follower, not one per id: a cold cache misses whole
		// handfuls at once.
		if (attempts == 1)
		{
			log.warn("{}: only {} of {} model part(s) loaded, missing id(s) {} — not spawning; "
					+ "a cold cache is the usual cause, so it will be retried up to {} time(s) "
					+ "per scene load",
				label(), parts.size(), modelIds.length, missing, MAX_ATTEMPTS);
		}

		return null;
	}

	/**
	 * @return the model ids to build, or {@code null} if the NPC composition would not
	 * resolve. Never falls back to anything: a different body in the right place is
	 * worse than no body.
	 */
	@Nullable
	private int[] modelIdsToBuild()
	{
		if (appearance != null)
		{
			return appearance.getModelIds();
		}

		FollowerAppearance resolved = FollowerAppearance.resolve(client, npcId(), label());
		if (resolved == null)
		{
			spendAttempt();
			if (attempts == 1)
			{
				log.warn("{}: npc {} would not resolve to an appearance — not spawning; a cold "
						+ "cache is one cause and a renumbered NPC id is the other, so it will be "
						+ "retried up to {} time(s) per scene load",
					label(), npcId(), MAX_ATTEMPTS);
			}
			return null;
		}

		appearance = resolved;
		log.debug("{}: dressed from '{}' — {} model(s), {} recolour pair(s)",
			label(), resolved.getNpcName(),
			resolved.getModelIds().length, resolved.getRecolorFind().length);
		return resolved.getModelIds();
	}

	/**
	 * Merges, recolours and lights a complete set of parts.
	 *
	 * @return the lit model, or {@code null} for a structural failure
	 */
	@Nullable
	private Model assemble(List<ModelData> parts)
	{
		// Always merge, even for a single part: mergeModels returns a fresh ModelData,
		// and recolour below mutates in place. Recolouring a bare loadModelData result
		// would corrupt the client's shared cache entry for every other user of that
		// model.
		ModelData combined = client.mergeModels(parts.toArray(new ModelData[0]), parts.size());
		if (combined == null)
		{
			log.warn("{}: mergeModels returned null for {} part(s), cannot spawn",
				label(), parts.size());
			return null;
		}

		short[] find = appearance == null ? new short[0] : appearance.getRecolorFind();
		short[] replace = appearance == null ? new short[0] : appearance.getRecolorReplace();
		if (find.length > 0)
		{
			// ModelData.recolor's own javadoc says to call cloneColors() first, and
			// "mergeModels hands back a fresh instance" is an observation about an
			// obfuscated constructor rather than a contract. A shared faceColors array
			// repainted here would repaint every instance of that model in the world,
			// through the client's own cache.
			combined.cloneColors();
			for (int i = 0; i < find.length; i++)
			{
				combined.recolor(find[i], replace[i]);
			}
		}

		// The rig the client itself lights a figure in the world with — see the five
		// constants above. The no-argument overload this used to call is the interface
		// one, so the follower was lit like a model in a widget while standing in a
		// field.
		Model lit = combined.light(
			WORLD_AMBIENT, WORLD_CONTRAST, WORLD_LIGHT_X, WORLD_LIGHT_Y, WORLD_LIGHT_Z);
		if (lit == null)
		{
			log.warn("{}: lighting produced no model, cannot spawn", label());
			return null;
		}

		return lit;
	}

	/** @return whether a cache attempt may be spent this pass */
	private boolean attemptAllowed()
	{
		if (attempts >= MAX_ATTEMPTS)
		{
			return false;
		}

		return attempts == 0 || ticksSinceAttempt >= RETRY_BACKOFF_TICKS;
	}

	private void spendAttempt()
	{
		attempts++;
		ticksSinceAttempt = 0;
	}
}
