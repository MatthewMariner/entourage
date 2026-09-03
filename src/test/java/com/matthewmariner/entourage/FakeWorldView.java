package com.matthewmariner.entourage;

import javax.annotation.Nullable;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * The {@link WorldView} methods this plugin and {@code LocalPoint.fromWorld} need: the
 * plane, the scene rectangle, the view id, and the collision maps.
 *
 * <p>{@link #around} places the scene the way the client does — a
 * {@link Constants#SCENE_SIZE}-tile square with the player's chunk in the middle — so a
 * tile near the player resolves to a {@code LocalPoint} for the same reason it does in
 * game rather than because the fake is permissive.
 *
 * <p><b>Collision defaults to open ground and is allocated lazily.</b> Zero means
 * walkable in the client's own convention, so a test that does not care about collision
 * gets a scene a follower can cross, and pays nothing for it.
 */
final class FakeWorldView extends StubWorldView
{
	private final int baseX;
	private final int baseY;
	private int plane;

	/** Which world view this is. 0 is {@link WorldView#TOPLEVEL}. */
	private int id = WorldView.TOPLEVEL;

	/**
	 * One map per plane, matching the client's own four-slot array, or {@code null}
	 * while the scene has not produced any.
	 */
	@Nullable
	private FakeCollisionData[] collisionMaps;

	private boolean collisionDataAvailable = true;

	private FakeWorldView(int baseX, int baseY, int plane)
	{
		this.baseX = baseX;
		this.baseY = baseY;
		this.plane = plane;
	}

	/** A scene centred on the given tile's chunk, the way the client centres it. */
	static FakeWorldView around(WorldPoint centre)
	{
		return new FakeWorldView(
			chunkAlignedBase(centre.getX()), chunkAlignedBase(centre.getY()), centre.getPlane());
	}

	private static int chunkAlignedBase(int coordinate)
	{
		// The client's own arithmetic: the centre chunk sits six chunks in.
		return ((coordinate / Constants.CHUNK_SIZE) - 6) * Constants.CHUNK_SIZE;
	}

	void setPlane(int plane)
	{
		this.plane = plane;
	}

	@Override
	public int getPlane()
	{
		return plane;
	}

	@Override
	public int getBaseX()
	{
		return baseX;
	}

	@Override
	public int getBaseY()
	{
		return baseY;
	}

	@Override
	public int getSizeX()
	{
		return Constants.SCENE_SIZE;
	}

	@Override
	public int getSizeY()
	{
		return Constants.SCENE_SIZE;
	}

	@Override
	public int getId()
	{
		return id;
	}

	/**
	 * {@code isTopLevel()} in the injected client is compiled to {@code getId() == 0},
	 * so this derives from the id rather than being a second settable flag that could
	 * disagree with it.
	 */
	@Override
	public boolean isTopLevel()
	{
		return id == WorldView.TOPLEVEL;
	}

	/**
	 * Makes this a {@code WorldEntity}'s view rather than the top-level one — the case
	 * where the client's collision map is origin-shifted and cannot be read with scene
	 * coordinates. See {@link WalkableStep}.
	 */
	FakeWorldView asWorldEntityView()
	{
		this.id = 7;
		return this;
	}

	// --- Collision ------------------------------------------------------------

	@Override
	@Nullable
	public CollisionData[] getCollisionMaps()
	{
		return collisionDataAvailable ? maps() : null;
	}

	/**
	 * Fills a tile in outright — a wall, a counter, or open water.
	 *
	 * @throws IllegalArgumentException if the tile is outside this scene, so a fixture
	 * that thought it was blocking something and was not fails loudly rather than
	 * passing for the wrong reason
	 */
	FakeWorldView block(WorldPoint tile)
	{
		return setFlags(tile, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
	}

	/** ORs an arbitrary flag mask into one tile — a wall along one of its edges. */
	FakeWorldView setFlags(WorldPoint tile, int mask)
	{
		int sceneX = tile.getX() - baseX;
		int sceneY = tile.getY() - baseY;
		if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE)
		{
			throw new IllegalArgumentException(
				"tile " + tile + " is outside the fake scene at " + baseX + "," + baseY);
		}

		maps()[tile.getPlane()].set(sceneX, sceneY, mask);
		return this;
	}

	/** The client has not built any collision maps yet. */
	FakeWorldView withoutCollisionData()
	{
		collisionDataAvailable = false;
		return this;
	}

	/** The array exists but this plane's map does not — the client's own lazy state. */
	FakeWorldView withoutCollisionMapFor(int planeToDrop)
	{
		maps()[planeToDrop] = null;
		return this;
	}

	private FakeCollisionData[] maps()
	{
		if (collisionMaps == null)
		{
			// Four, because the client allocates one map per plane.
			collisionMaps = new FakeCollisionData[4];
			for (int i = 0; i < collisionMaps.length; i++)
			{
				collisionMaps[i] = new FakeCollisionData(Constants.SCENE_SIZE, Constants.SCENE_SIZE);
			}
		}
		return collisionMaps;
	}
}
