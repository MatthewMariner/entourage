package com.matthewmariner.entourage;

import net.runelite.api.CollisionData;

/**
 * One plane's collision map, as a plain {@code int[][]}.
 *
 * <p>{@link CollisionData} has exactly one method in 1.12.38 — {@code int[][] getFlags()},
 * confirmed by {@code javap} — so this is the whole interface and there is nothing to
 * stub out.
 *
 * <p><b>Sized and indexed the way the client sizes and indexes it.</b> The injected
 * client allocates the flags array over {@code getSizeX()} then {@code getSizeY()} and
 * every writer in it does {@code flags[x][y] |= ..}, so this is
 * {@code [sceneX][sceneY]} — and a test that got the two the wrong way round would be
 * asserting about the wrong tile. {@code WalkableStepTest} uses a deliberately
 * asymmetric fixture so a transposed reader cannot pass it.
 *
 * <p><b>Zero means walkable.</b> That is the client's own convention — a flag is
 * something that blocks — so a freshly built map is open ground everywhere and a test
 * only has to say what is in the way.
 */
final class FakeCollisionData implements CollisionData
{
	private final int[][] flags;

	FakeCollisionData(int sizeX, int sizeY)
	{
		this.flags = new int[sizeX][sizeY];
	}

	@Override
	public int[][] getFlags()
	{
		return flags;
	}

	/** ORs {@code mask} into one tile's flags. */
	void set(int sceneX, int sceneY, int mask)
	{
		flags[sceneX][sceneY] |= mask;
	}
}
