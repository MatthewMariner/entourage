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
 *   <li>{@link #tick(WorldPoint, WorldView)} is <b>game-tick work</b>: decide whether
 *       to move, take at most one tile, work out which way that faces. A tile per
 *       game tick is the speed the game walks at. Doing it per frame would make a
 *       follower's speed depend on the machine's frame rate.</li>
 *   <li>{@link #localPoint(WorldView, float)} is <b>frame work</b>: it slides the
 *       drawn position between the tile the follower left and the tile it is walking
 *       to. Without it, a follower is redrawn one whole tile to the side every 600ms
 *       — a figure teleporting rather than walking, which is what a movement system
 *       built on {@code onGameTick} alone actually produces.</li>
 * </ul>
 *
 * <p><b>The animation is the other half of "smooth", and this class owns none of
 * it.</b> {@code RuneLiteObject.tick(ticksSinceLastFrame)} is called <i>by the
 * client</i>, once per frame, for every registered object — its javadoc says "Called
 * every frame the RuneLiteObject is registered and in the scene" — and that is what
 * advances the {@code AnimationController}'s frame. This plugin must never call it:
 * a second caller runs every animation at double speed. {@link Follower} does the
 * idle/walk switching; {@code FollowerTest} pins the tick count at zero.
 *
 * <p><b>Where the follower wants to be.</b> One rule, for now: get within
 * {@link #STATION_DISTANCE} tiles of the anchor and then stand still and face it.
 * That is the seam a formation replaces — {@link #isStationed} becomes "am I on my
 * assigned slot" and the anchor becomes a slot offset — and it is deliberately the
 * whole of the geometry in this slice, because a formation of four figures that
 * cannot each walk one tile correctly is four times the bug.
 *
 * <p><b>Blocked means skip, never nudge.</b> The step is taken only when
 * {@link WalkableStep} returns {@link WalkableStep.Verdict#WALKABLE}; both
 * {@code BLOCKED} and {@code UNKNOWN} leave the follower where it is for the tick.
 * The only search this does is to try the two axis components of a blocked diagonal:
 * neither can increase the Chebyshev distance to the anchor, and each closes the gap on
 * the axis it moves along. (Note that an orthogonal step towards a <i>diagonal</i>
 * target does not <i>reduce</i> the Chebyshev distance — the other axis is still the
 * larger of the two — which is why the guarantee is stated as two halves rather than as
 * "it always gets closer".) It will not sidestep, back up, or take a tile that moves it
 * away from the anchor on either axis. The cost of that discipline
 * is stated rather than hidden: this is greedy stepping, not pathfinding, so a
 * follower <b>will</b> get stuck on the wrong side of a wall it has to walk away from
 * to get around. {@link #RECALL_DISTANCE} is what stops that being permanent.
 *
 * <p><b>Client-thread-free.</b> {@link #tick} reads collision through
 * {@link WalkableStep} and {@link #localPoint} reads the view's scene rectangle;
 * neither touches the client, which is what lets {@code FollowerWalkTest} drive
 * thousands of ticks across a fake scene with no game running.
 */
final class FollowerWalk
{
	/**
	 * How close is close enough, in tiles, Chebyshev — so a diagonal neighbour counts
	 * as adjacent, the same way the game counts it.
	 *
	 * <p>One. A follower that stopped further out would trail; a follower that stopped
	 * at zero would stand inside the player.
	 */
	static final int STATION_DISTANCE = 1;

	/**
	 * How far the follower may fall behind before it is put back next to the player
	 * rather than walked back.
	 *
	 * <p>This exists because the stepping above is greedy. A follower that has walked
	 * into a dead end cannot reason its way out of one, and the alternative to a
	 * recall is a figure left standing in a doorway in Varrock for the rest of the
	 * session. Twelve tiles is comfortably inside the scene, so the anchor tile is
	 * still one this plugin can place an object on.
	 *
	 * <p><b>It is not comfortably outside the distance a follower falls behind a
	 * running player, and nothing at this distance could be.</b> A run is two tiles a
	 * game tick against the follower's one, so the gap grows by a tile a tick and keeps
	 * growing: whatever number goes here, continuous running reaches it. Twelve tiles
	 * makes that every twelve ticks — one recall every 7.2 seconds, which
	 * {@code FollowerWalkTest} measures against this class rather than asserting from
	 * the arithmetic. The cause is that a follower has no run speed; giving it one is a
	 * design change with its own review, and until then the honest statement is that a
	 * recall is a normal part of travelling rather than an edge case. Raising this
	 * number would only make each recall a longer absence.
	 *
	 * <p>A plane change recalls unconditionally. A staircase is not a distance.
	 */
	static final int RECALL_DISTANCE = 12;

	/** The tile the follower is walking to over the current game tick. */
	private int x;
	private int y;
	private int plane;

	/** The tile it left at the start of that step; equal to x,y while standing. */
	private int fromX;
	private int fromY;

	private boolean moving;
	private int orientation;

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
	 */
	void tick(@Nullable WorldPoint anchor, @Nullable WorldView worldView)
	{
		// Whatever happens below, the step that was in flight is over: the drawn
		// position has caught up with the tile, and the next interpolation starts
		// from here.
		fromX = x;
		fromY = y;
		moving = false;

		if (anchor == null)
		{
			// No anchor. Hold the tile — EntourageScene deactivates the follower for
			// this case, so this is the state it will be re-spawned out of rather
			// than a position anybody sees.
			return;
		}

		int distance = chebyshevTo(anchor);
		if (anchor.getPlane() != plane || distance > RECALL_DISTANCE)
		{
			recallTo(anchor);
			return;
		}

		int dx = Integer.signum(anchor.getX() - x);
		int dy = Integer.signum(anchor.getY() - y);

		if (distance <= STATION_DISTANCE)
		{
			// On station. Turn to face the player and hold the pose.
			face(dx, dy);
			return;
		}

		if (step(worldView, dx, dy))
		{
			return;
		}

		// A blocked diagonal is usually a wall on one of the two axes, and the
		// half-step along the other one is both legal and still towards the anchor.
		// This is the only searching this class does, and neither candidate can
		// increase the distance to the anchor.
		if (dx != 0 && dy != 0 && (step(worldView, dx, 0) || step(worldView, 0, dy)))
		{
			return;
		}

		// Nothing legal, or nothing knowable. Stand still and keep looking at the
		// player, which is what a figure that cannot get to you would do.
		face(dx, dy);
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

	/** @return the direction the follower is facing, in 0..2047 */
	int getOrientation()
	{
		return orientation;
	}

	/**
	 * Chebyshev distance, which is how the game measures adjacency: a diagonal
	 * neighbour is one tile away, not one-and-a-half.
	 */
	private int chebyshevTo(WorldPoint anchor)
	{
		return Math.max(Math.abs(anchor.getX() - x), Math.abs(anchor.getY() - y));
	}

	/**
	 * Puts the follower on the player's own tile.
	 *
	 * <p>The anchor tile rather than a search for a free tile next to it. The player
	 * is standing on it, so it is by definition ground a figure can be on, and no
	 * "find somewhere nearby that works" pass can pick somewhere wrong. The follower
	 * steps off it on the next tick, so the overlap lasts one game tick. Making that
	 * arrival look deliberate is what an entrance effect would be for, and that is a
	 * later slice.
	 */
	private void recallTo(WorldPoint anchor)
	{
		placeAt(anchor);
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
