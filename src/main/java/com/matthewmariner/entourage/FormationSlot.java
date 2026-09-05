package com.matthewmariner.entourage;

import net.runelite.api.coords.WorldPoint;

/**
 * Where the follower wants to stand relative to the player.
 *
 * <p><b>A slot is one exact tile, not a radius.</b> Before this existed the rule was
 * "get within a tile of the player and stop", which is eight acceptable tiles and no
 * way to express a preference between them — so "stand on my left" could not be a
 * setting at all. The cost of the change is that a follower whose slot is inside a wall
 * stands next to it rather than settling: {@link FollowerWalk} is greedy stepping, not
 * pathfinding, so it gets as close as a legal step allows and then holds. That reads
 * fine (a companion standing beside you) and it is the honest outcome of the rule.
 *
 * <p><b>"Behind" is measured against the direction the player last travelled, not
 * against the direction the player is facing.</b> Both were available and they behave
 * very differently while standing still: a player who turns on the spot to talk to a
 * shopkeeper would send a facing-based entourage walking a circle around them, once per
 * click, forever. Direction of travel does not change when you stand still, so a
 * formation that has settled stays settled — and while you <i>are</i> moving the two
 * answers agree anyway, because the game turns the player to face the way it walks.
 *
 * <p>{@link FollowerWalk} keeps the heading, which is why this class takes it as a pair
 * of components rather than reading it from anywhere. Each component is a signum, so
 * the eight compass directions are the only inputs and a diagonal heading yields a
 * diagonal offset of the same Chebyshev length as a straight one — two tiles behind is
 * two tiles behind whichever way you are walking.
 */
public enum FormationSlot
{
	/**
	 * Directly behind, which is what a follower normally does. Offset is the heading
	 * reversed.
	 */
	BEHIND("Behind me"),

	/**
	 * Directly in front, on the heading itself — a figure walking point.
	 *
	 * <p><b>It leads you without knowing where you are going</b>, and that is worth
	 * saying out loud rather than discovering: the slot is a tile ahead of the direction
	 * you last travelled, so the moment you turn, the follower is beside or behind you
	 * and has to walk round to the front again. Turn on the spot and it circles you,
	 * which is the exact behaviour {@link #BEHIND} is measured against direction of
	 * travel to avoid — here it is the honest consequence of asking for a figure in
	 * front. It is also the one slot that regularly stands between you and what you are
	 * clicking on, which no plugin-drawn object blocks (a {@code RuneLiteObject} takes no
	 * clicks) but which does put a body over the thing you are looking at.
	 */
	AHEAD("Ahead of me"),

	/**
	 * Abreast on the player's left, i.e. the heading rotated a quarter turn
	 * anticlockwise. Walking north puts the follower to the west.
	 */
	LEFT("On my left"),

	/**
	 * Abreast on the player's right — the heading rotated a quarter turn clockwise.
	 * Walking north puts the follower to the east.
	 */
	RIGHT("On my right");

	private final String displayName;

	FormationSlot(String displayName)
	{
		this.displayName = displayName;
	}

	/**
	 * @param anchor   the tile the player is on
	 * @param headingX the west/east component of the player's direction of travel, -1,
	 *                 0 or 1
	 * @param headingY the south/north component, -1, 0 or 1
	 * @param distance how many tiles out the slot sits, at least 1 — a slot of zero
	 *                 would be the player's own tile, which is the one place a follower
	 *                 must never settle
	 * @return the tile this slot names, on the anchor's plane
	 */
	WorldPoint tileFor(WorldPoint anchor, int headingX, int headingY, int distance)
	{
		return new WorldPoint(
			anchor.getX() + offsetX(headingX, headingY) * distance,
			anchor.getY() + offsetY(headingX, headingY) * distance,
			anchor.getPlane());
	}

	/**
	 * The west/east component of the unit offset.
	 *
	 * <p>Split from {@link #offsetY} rather than returning a point, so that a test can
	 * name one axis at a time. A rotation is exactly where an x and a y get swapped by
	 * accident, and a helper that returned both together would hide the swap inside an
	 * equality on the result.
	 */
	private int offsetX(int headingX, int headingY)
	{
		switch (this)
		{
			case AHEAD:
				// The heading itself, unturned.
				return headingX;
			case LEFT:
				// A quarter turn anticlockwise: (x, y) -> (-y, x).
				return -headingY;
			case RIGHT:
				// A quarter turn clockwise: (x, y) -> (y, -x).
				return headingY;
			default:
				return -headingX;
		}
	}

	/** The south/north component of the unit offset — see {@link #offsetX}. */
	private int offsetY(int headingX, int headingY)
	{
		switch (this)
		{
			case AHEAD:
				return headingY;
			case LEFT:
				return headingX;
			case RIGHT:
				return -headingX;
			default:
				return -headingY;
		}
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}
}
