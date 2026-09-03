package com.matthewmariner.entourage;

import javax.annotation.Nullable;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Where the entourage forms up: the tile the local player is <b>drawn on</b>, this
 * frame, or a stated reason why there isn't one.
 *
 * <p><b>Why not {@code Actor.getWorldLocation()}.</b> Disassembling 1.12.38's
 * injected client, the actor class {@code dh} builds that {@code WorldPoint} out of
 * {@code pathX[0]} and {@code pathY[0]} — the head of the actor's <i>movement
 * queue</i>, i.e. the tile the server has already moved the player to and the client
 * is still animating towards. {@code dh.getLocalLocation()}, by contrast, reads the
 * actor's own {@code x}/{@code y} render fields directly. Those two disagree for the
 * whole of every step, and by more than one tile while running, because a run is two
 * queued tiles in one game tick. Anchoring a formation to the first one puts it where
 * the player <i>will be</i>, which reads as the group drifting ahead of the person it
 * is supposed to be following. Only a live client can say how many tiles that looks
 * like at a run; that it is the wrong field is settled here.
 *
 * <p>The tile therefore comes from the render position, rounded down to the tile it
 * sits in — {@code sceneX = localX >> 7}. That is the tile a bystander would say the
 * player is standing on, and it is the coordinate space the collision flags are
 * indexed in.
 *
 * <p><b>UNKNOWN is the common case, not the edge case.</b> A follower spends real
 * time with no anchor: at the login screen, through every scene load, and any time
 * the player is inside a {@code WorldEntity} — a boat, or anything else the game
 * draws as its own little world. Each of those is a named {@link Resolution} rather
 * than a null nobody looked at, and {@link EntourageScene} has one branch for all of
 * them: <b>the entourage is not drawn.</b> Not frozen in place — a
 * {@code RuneLiteObject} left active through a scene load is standing at a
 * {@code LocalPoint} that now means somewhere else entirely, which is the leaked
 * artefact this plugin's teardown contract exists to prevent.
 *
 * <p><b>Foreign world views are refused rather than supported.</b> A
 * {@code RuneLiteObject} can be placed into a {@code WorldEntity}'s view — its
 * {@code setLocation} takes the view off the {@code LocalPoint} — but the collision
 * map for such a view is origin-shifted by one tile and cannot be read with scene
 * coordinates (see {@link WalkableStep}). A follower there would walk through walls
 * on a boat. Refusing is the deliberate branch; supporting it is a later slice with
 * its own collision path.
 *
 * <p>Nothing here calls the client. It takes the player and the view it is judged
 * against as arguments, so the whole of it is testable with no game running.
 */
final class FollowerAnchor
{
	enum Resolution
	{
		/** There is a tile. */
		RESOLVED,

		/** No local player, or no top-level view — logged out, or logging in. */
		NO_PLAYER,

		/**
		 * The player is somewhere this plugin will not draw into: a
		 * {@code WorldEntity}'s world view rather than the top-level one.
		 */
		FOREIGN_WORLD_VIEW,

		/**
		 * The player's render position is outside the loaded scene. Not expected in
		 * practice — the scene is built around the player — but it is one array index
		 * away from every collision read this plugin makes, so it is checked rather
		 * than assumed.
		 */
		OUTSIDE_SCENE
	}

	private static final FollowerAnchor NO_PLAYER = new FollowerAnchor(Resolution.NO_PLAYER, null);
	private static final FollowerAnchor FOREIGN_WORLD_VIEW =
		new FollowerAnchor(Resolution.FOREIGN_WORLD_VIEW, null);
	private static final FollowerAnchor OUTSIDE_SCENE =
		new FollowerAnchor(Resolution.OUTSIDE_SCENE, null);

	private final Resolution resolution;

	@Nullable
	private final WorldPoint tile;

	private FollowerAnchor(Resolution resolution, @Nullable WorldPoint tile)
	{
		this.resolution = resolution;
		this.tile = tile;
	}

	/**
	 * @param player   the local player, which is {@code null} until well after
	 *                 {@code LOGGED_IN}
	 * @param topLevel the client's top-level world view
	 * @return the anchor, or an unresolved one carrying the reason. Never
	 * {@code null}, and never throws.
	 */
	static FollowerAnchor of(@Nullable Player player, @Nullable WorldView topLevel)
	{
		if (player == null || topLevel == null)
		{
			return NO_PLAYER;
		}

		LocalPoint render = player.getLocalLocation();
		if (render == null)
		{
			// The injected client's own implementation cannot return null, but Player
			// is an interface and this is one null check against a whole pass.
			return NO_PLAYER;
		}

		if (render.getWorldView() != topLevel.getId())
		{
			// LocalPoint carries the id of the view it is in, and the player's render
			// position carries the player's. A mismatch is the player being inside a
			// world entity while we are holding the top-level view.
			return FOREIGN_WORLD_VIEW;
		}

		// Bounded by the view's own size rather than by LocalPoint.isInScene(), which
		// hardcodes 104 * 128 and is therefore wrong on an extended scene.
		int sceneX = render.getSceneX();
		int sceneY = render.getSceneY();
		if (sceneX < 0 || sceneX >= topLevel.getSizeX() || sceneY < 0 || sceneY >= topLevel.getSizeY())
		{
			return OUTSIDE_SCENE;
		}

		WorldPoint tile = new WorldPoint(
			topLevel.getBaseX() + sceneX,
			topLevel.getBaseY() + sceneY,
			topLevel.getPlane());

		return new FollowerAnchor(Resolution.RESOLVED, tile);
	}

	boolean isResolved()
	{
		return resolution == Resolution.RESOLVED;
	}

	Resolution getResolution()
	{
		return resolution;
	}

	/**
	 * @return the tile the player is drawn on, or {@code null} when this anchor is
	 * unresolved
	 */
	@Nullable
	WorldPoint getTile()
	{
		return tile;
	}

	@Override
	public String toString()
	{
		return isResolved() ? "FollowerAnchor{" + tile + "}" : "FollowerAnchor{" + resolution + "}";
	}
}
