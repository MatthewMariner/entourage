package com.matthewmariner.entourage;

import javax.annotation.Nullable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * One follower's movement: which tile it is walking to, which tile it left, and
 * where it should be drawn part-way between the two.
 *
 * <p><b>The per-tick / per-frame split lives here, and it is the whole design.</b>
 * {@code RuneLiteObject} has no walk API at all — no destination, no speed, no path
 * — so a plugin that moves one has to supply both halves itself, and putting both on
 * the same clock is how it goes wrong:
 *
 * <ul>
 *   <li>{@link #tick(WorldPoint, WorldView, EntourageSettings)} is <b>game-tick
 *       work</b>: decide whether to move, take one tile or two, work out which way that
 *       faces. A tile per game tick is the speed the game walks at and two is the speed
 *       it runs at. Doing it per frame would make a follower's speed depend on the
 *       machine's frame rate.</li>
 *   <li>{@link #localPoint(WorldView, float)} is <b>frame work</b>: it slides the
 *       drawn position between the tile the follower left and the tile it is walking
 *       to. Without it, a follower is redrawn one whole tile to the side every 600ms
 *       — a figure teleporting rather than walking, which is what a movement system
 *       built on {@code onGameTick} alone actually produces. It interpolates
 *       {@code fromX/fromY} to {@code x/y} whatever the gap between them is, so a
 *       two-tile step needed nothing added to it.</li>
 * </ul>
 *
 * <p><b>The animation is the other half of "smooth", and this class owns none of
 * it.</b> {@code RuneLiteObject.tick(ticksSinceLastFrame)} is called <i>by the
 * client</i>, once per frame, for every registered object — its javadoc says "Called
 * every frame the RuneLiteObject is registered and in the scene" — and that is what
 * advances the {@code AnimationController}'s frame. This plugin must never call it:
 * a second caller runs every animation at double speed. {@link Follower} does the
 * idle/walk/run switching, off {@link #isMoving()} and {@link #isRunning()};
 * {@code FollowerTest} pins the tick count at zero.
 *
 * <p><b>Where the follower wants to be is one exact tile, not a radius.</b>
 * {@link FormationSlot} names it, off the player's tile, the configured follow
 * distance, and the direction the player last travelled — which this class keeps,
 * because nothing else sees the anchor on consecutive ticks. The follower walks to that
 * tile and stops on it. The older rule ("get within a tile and stop") was eight
 * acceptable tiles with no way to prefer one, so "stand on my left" could not be
 * expressed at all.
 *
 * <p><b>Blocked means skip, never nudge.</b> A step is taken only when
 * {@link WalkableStep} returns {@link WalkableStep.Verdict#WALKABLE}; both
 * {@code BLOCKED} and {@code UNKNOWN} leave the follower where it is. <b>Two steps
 * means two checks</b> — the second tile is judged from the tile the first one reached,
 * exactly as the first was — because a run that only checked its first tile would walk
 * through every second wall. The only search this does is to try the two axis
 * components of a blocked diagonal: neither can increase the Chebyshev distance to the
 * slot, and each closes the gap on the axis it moves along. (An orthogonal step towards
 * a <i>diagonal</i> target does not <i>reduce</i> the Chebyshev distance — the other
 * axis is still the larger of the two — which is why the guarantee is stated as two
 * halves rather than as "it always gets closer".) It will not sidestep, back up, or take
 * a tile that moves it away from the slot on either axis. The cost of that discipline is
 * stated rather than hidden: this is greedy stepping, not pathfinding, so a follower
 * <b>will</b> get stuck on the wrong side of a wall it has to walk away from to get
 * around. The recall distance is what stops that being permanent.
 *
 * <p><b>It may cross the player's own tile, and that is deliberate.</b> When the player
 * doubles back, the slot flips to the far side of them and the only route to it is
 * through. Refusing that step would leave a follower standing one tile east of a
 * stationary player whose slot is one tile west, forever. It never <i>settles</i> there,
 * because the slot is at least one tile off the anchor by construction.
 *
 * <p><b>Client-thread-free.</b> {@link #tick} reads collision through
 * {@link WalkableStep} and {@link #localPoint} reads the view's scene rectangle;
 * neither touches the client, which is what lets {@code FollowerWalkTest} drive
 * thousands of ticks across a fake scene with no game running.
 */
final class FollowerWalk
{
	/**
	 * How many tiles a follower may cover in one game tick when it is allowed to run.
	 *
	 * <p>Two, because that is what a run is in this game: the server moves a running
	 * player two tiles per tick and one per tick at a walk. Three would be a follower
	 * that closes gaps faster than the player can open them, which looks like a figure
	 * being dragged on a string.
	 */
	static final int RUN_STEPS_PER_TICK = 2;

	/**
	 * How far from its slot the follower has to be before it breaks into a run.
	 *
	 * <p>Two, which is exactly "one step will not get me there". At a walking pace the
	 * slot moves one tile a tick and the follower is never more than one tile off it, so
	 * this never fires and the follower never runs alongside a walking player. A running
	 * player moves the slot two tiles a tick, this fires every tick, and the follower
	 * keeps station instead of shedding a tile a tick until it is recalled.
	 */
	static final int RUN_THRESHOLD = 2;

	/**
	 * Which way the player is treated as heading before they have taken a step.
	 *
	 * <p>North, which is arbitrary and deterministic rather than meaningful: it decides
	 * only which side of a player who has never moved the follower forms up on, and the
	 * player's first step replaces it. It is not zero, because a zero heading has no
	 * left and no right and would collapse every slot onto the player's own tile.
	 */
	private static final int INITIAL_HEADING_X = 0;
	private static final int INITIAL_HEADING_Y = 1;

	/** The tile the follower is walking to over the current game tick. */
	private int x;
	private int y;
	private int plane;

	/** The tile it left at the start of that step; equal to x,y while standing. */
	private int fromX;
	private int fromY;

	private boolean moving;

	/** True when this tick covered two tiles rather than one. */
	private boolean running;

	private int orientation;

	/**
	 * The direction the player last travelled, as a pair of signums, which is what
	 * {@link FormationSlot} rotates to find a slot.
	 *
	 * <p>Kept here rather than read from the player because it needs two consecutive
	 * observations of the anchor and this is the only thing that gets them. It survives
	 * standing still on purpose — see {@link FormationSlot} on why direction of travel
	 * beats direction of facing.
	 */
	private int headingX = INITIAL_HEADING_X;
	private int headingY = INITIAL_HEADING_Y;

	/** The anchor as of the previous tick, for the heading. */
	private int lastAnchorX;
	private int lastAnchorY;
	private int lastAnchorPlane;
	private boolean hasLastAnchor;

	/**
	 * @param start the tile to begin on, normally the anchor's — a follower that has
	 *              just been spawned has not walked anywhere yet
	 */
	FollowerWalk(WorldPoint start)
	{
		placeAt(start);
	}

	/**
	 * One game tick of following.
	 *
	 * @param anchor    the tile to form up on, or {@code null} when there is no
	 *                  anchor this tick — see {@link FollowerAnchor}
	 * @param worldView the view the follower is walking in, for the collision read
	 * @param settings  the distances, the slot and whether running is allowed
	 */
	void tick(@Nullable WorldPoint anchor, @Nullable WorldView worldView, EntourageSettings settings)
	{
		// Whatever happens below, the step that was in flight is over: the drawn
		// position has caught up with the tile, and the next interpolation starts
		// from here.
		fromX = x;
		fromY = y;
		moving = false;
		running = false;

		if (anchor == null)
		{
			// No anchor. Hold the tile — EntourageScene deactivates the follower for
			// this case, so this is the state it will be re-spawned out of rather
			// than a position anybody sees.
			return;
		}

		// Before the recall check, so that a follower which is put back still knows which
		// way the player was going and forms up on the right side of them.
		updateHeading(anchor);

		int toAnchor = chebyshevTo(anchor.getX(), anchor.getY());
		if (anchor.getPlane() != plane || toAnchor > settings.getRecallDistance())
		{
			recallTo(anchor);
			return;
		}

		WorldPoint station = stationTile(anchor, settings);
		int toStation = chebyshevTo(station.getX(), station.getY());

		if (toStation == 0)
		{
			// On its slot. Turn to face the player and hold the pose.
			faceThe(anchor);
			return;
		}

		// The second step is judged from the tile the first one reached, so a run makes
		// two collision reads rather than one. It also stops the moment a step is
		// refused: a follower that could not take its first step would find exactly the
		// same nothing on its second, from the same tile, towards the same slot.
		int allowed = settings.canRun() && toStation >= RUN_THRESHOLD ? RUN_STEPS_PER_TICK : 1;
		int taken = 0;
		while (taken < allowed && stepTowards(worldView, station))
		{
			taken++;
		}

		if (taken == 0)
		{
			// Nothing legal, or nothing knowable. Stand still and keep looking at the
			// player, which is what a figure that cannot get to you would do.
			faceThe(anchor);
			return;
		}

		running = taken > 1;
	}

	/**
	 * Where to draw the follower right now.
	 *
	 * @param worldView the view the follower is being placed in
	 * @param fraction  how far through the current game tick this frame is, 0..1
	 * @return the interpolated local position, or {@code null} if the tile it is
	 * walking to is outside the loaded scene — the caller then leaves the object where
	 * it was rather than moving it somewhere meaningless
	 */
	@Nullable
	LocalPoint localPoint(WorldView worldView, float fraction)
	{
		LocalPoint to = LocalPoint.fromWorld(worldView, new WorldPoint(x, y, plane));
		if (to == null)
		{
			return null;
		}

		if (!moving || (fromX == x && fromY == y))
		{
			return to;
		}

		LocalPoint from = LocalPoint.fromWorld(worldView, new WorldPoint(fromX, fromY, plane));
		if (from == null)
		{
			// It stepped in from outside the scene. Snapping to the tile it is walking
			// to is one tile of pop at the far edge of the render distance, instead of
			// a frame drawn at a stale position.
			return to;
		}

		float clamped = fraction < 0f ? 0f : (fraction > 1f ? 1f : fraction);
		int lx = from.getX() + Math.round((to.getX() - from.getX()) * clamped);
		int ly = from.getY() + Math.round((to.getY() - from.getY()) * clamped);

		// The (int, int, WorldView) constructor rather than an id, so the world view
		// on the point this returns matches the one on the points either side of it.
		// That is load-bearing: RuneLiteObject.setLocation deactivates and reactivates
		// the object whenever the point's world view differs from the object's, so a
		// mismatch here would churn the client's registered-object list once per frame
		// per follower.
		return new LocalPoint(lx, ly, worldView);
	}

	/** Puts the follower on a tile outright, ending any step in flight. */
	void placeAt(WorldPoint tile)
	{
		this.x = tile.getX();
		this.y = tile.getY();
		this.plane = tile.getPlane();
		this.fromX = x;
		this.fromY = y;
		this.moving = false;
		this.running = false;
	}

	/**
	 * @param anchor   the tile the player is on
	 * @param settings the slot and the follow distance
	 * @return the tile this follower is trying to stand on
	 */
	WorldPoint stationTile(WorldPoint anchor, EntourageSettings settings)
	{
		return settings.getFormationSlot()
			.tileFor(anchor, headingX, headingY, settings.getFollowDistance());
	}

	/** @return the tile the follower is on, or walking onto */
	WorldPoint currentTile()
	{
		return new WorldPoint(x, y, plane);
	}

	/** @return the tile the current step started from */
	WorldPoint stepStartTile()
	{
		return new WorldPoint(fromX, fromY, plane);
	}

	/** @return true while a step is in progress, i.e. this tick moved the follower */
	boolean isMoving()
	{
		return moving;
	}

	/**
	 * @return true when this tick covered two tiles rather than one. Always implies
	 * {@link #isMoving()}; {@link Follower} uses it to pick the run animation, and a
	 * follower that reported it wrongly would either slide (a walk cycle over two tiles)
	 * or sprint on the spot (a run cycle over one).
	 */
	boolean isRunning()
	{
		return running;
	}

	/** @return the direction the follower is facing, in 0..2047 */
	int getOrientation()
	{
		return orientation;
	}

	/** @return the west/east component of the player's last direction of travel */
	int getHeadingX()
	{
		return headingX;
	}

	/** @return the south/north component of the player's last direction of travel */
	int getHeadingY()
	{
		return headingY;
	}

	/**
	 * Remembers which way the player is going.
	 *
	 * <p>A tick on which the player did not move leaves the heading alone rather than
	 * zeroing it — a zero heading has no left and no right, and would collapse every
	 * slot onto the player's own tile the moment they stopped walking. A plane change is
	 * not a direction either, so the heading is not updated across one.
	 */
	private void updateHeading(WorldPoint anchor)
	{
		if (hasLastAnchor && lastAnchorPlane == anchor.getPlane())
		{
			int dx = Integer.signum(anchor.getX() - lastAnchorX);
			int dy = Integer.signum(anchor.getY() - lastAnchorY);
			if (dx != 0 || dy != 0)
			{
				headingX = dx;
				headingY = dy;
			}
		}

		lastAnchorX = anchor.getX();
		lastAnchorY = anchor.getY();
		lastAnchorPlane = anchor.getPlane();
		hasLastAnchor = true;
	}

	/**
	 * Chebyshev distance from the follower's tile, which is how the game measures
	 * adjacency: a diagonal neighbour is one tile away, not one-and-a-half.
	 */
	private int chebyshevTo(int tileX, int tileY)
	{
		return Math.max(Math.abs(tileX - x), Math.abs(tileY - y));
	}

	/**
	 * Puts the follower on the player's own tile.
	 *
	 * <p>The anchor tile rather than the slot, and rather than a search for a free tile
	 * next to it. The player is standing on it, so it is by definition ground a figure
	 * can be on, and no "find somewhere nearby that works" pass can pick somewhere
	 * wrong; the slot, by contrast, is a tile nobody has vetted and may be inside a
	 * wall. The follower steps off onto its slot on the next tick, so the overlap lasts
	 * one game tick. Making that arrival look deliberate is what an entrance effect
	 * would be for, and that is a later slice.
	 */
	private void recallTo(WorldPoint anchor)
	{
		placeAt(anchor);
	}

	/**
	 * One tile towards a tile, trying the diagonal first and then each of its two axis
	 * components.
	 *
	 * @return true if the follower moved
	 */
	private boolean stepTowards(@Nullable WorldView worldView, WorldPoint target)
	{
		int dx = Integer.signum(target.getX() - x);
		int dy = Integer.signum(target.getY() - y);

		if (step(worldView, dx, dy))
		{
			return true;
		}

		// A blocked diagonal is usually a wall on one of the two axes, and the
		// half-step along the other one is both legal and still towards the slot.
		// This is the only searching this class does, and neither candidate can
		// increase the distance to the slot.
		return dx != 0 && dy != 0 && (step(worldView, dx, 0) || step(worldView, 0, dy));
	}

	/**
	 * Takes one tile, if the collision map says so.
	 *
	 * @return true if the follower moved
	 */
	private boolean step(@Nullable WorldView worldView, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return false;
		}

		// UNKNOWN fails this comparison exactly as BLOCKED does, and that is the
		// point: an answer this plugin could not get is not an answer it may assume.
		if (WalkableStep.verdict(worldView, currentTile(), dx, dy) != WalkableStep.Verdict.WALKABLE)
		{
			return false;
		}

		x += dx;
		y += dy;
		moving = true;
		orientation = StepOrientation.forStep(dx, dy);
		return true;
	}

	/** Turns to look at the player without moving. */
	private void faceThe(WorldPoint anchor)
	{
		face(Integer.signum(anchor.getX() - x), Integer.signum(anchor.getY() - y));
	}

	/**
	 * Turns to face a direction without moving. A zero delta — the player standing on
	 * the follower's own tile — leaves the facing alone rather than snapping it south.
	 */
	private void face(int dx, int dy)
	{
		int facing = StepOrientation.forStep(dx, dy);
		if (facing != StepOrientation.NOT_MOVING)
		{
			orientation = facing;
		}
	}
}
