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
 * <p><b>{@link #rectangular} is the fixture {@link #around} cannot replace.</b> Every
 * <i>top-level</i> scene the client builds is square — 104 a side, or
 * {@link Constants#EXTENDED_SCENE_SIZE} on an extended one — and centred, and the
 * centring arithmetic gives a tile near {@code (3221, 3218)} a base of {@code 3168} on
 * <i>both</i> axes. On numbers like those a reader cannot tell {@code getBaseX()} from
 * {@code getBaseY()}, {@code getSizeX()} from {@code getSizeY()}, or
 * {@code flags[sceneX][sceneY]} from {@code flags[sceneY][sceneX]}; those three
 * confusions are exactly the mistakes {@link WalkableStep} and {@link FollowerAnchor}
 * are one keystroke from making, in code that reads the two axes separately on purpose.
 * So this factory takes two bases and two sizes and insists they differ.
 *
 * <p>It is not a shape out of nowhere. The client's world-view constructor takes
 * {@code sizeX} and {@code sizeY} as separate arguments and a {@code WorldEntity}'s view
 * really is whatever rectangle that entity is — a boat is longer than it is wide. What
 * is true is that the top-level view, the only one this plugin will read from today, is
 * always square. That makes this fixture the shape of a case currently refused rather
 * than a shape that cannot exist, and either way it is the only place a transposed axis
 * has to fail.
 *
 * <p><b>Collision defaults to open ground and is allocated lazily.</b> Zero means
 * walkable in the client's own convention, so a test that does not care about collision
 * gets a scene a follower can cross, and pays nothing for it.
 */
final class FakeWorldView extends StubWorldView
{
	private final int baseX;
	private final int baseY;
	private final int sizeX;
	private final int sizeY;
	private int plane;

	/** Which world view this is. 0 is {@link WorldView#TOPLEVEL}. */
	private int id = WorldView.TOPLEVEL;

	/**
	 * One map per plane, matching the client's own four-slot array, or {@code null} for
	 * a view that will not hand any over. The injected client always hands them over —
	 * see {@link #withoutCollisionData} — so this models an implementation of the
	 * interface rather than a moment in the real one's life.
	 */
	@Nullable
	private FakeCollisionData[] collisionMaps;

	private boolean collisionDataAvailable = true;

	private FakeWorldView(int baseX, int baseY, int sizeX, int sizeY, int plane)
	{
		this.baseX = baseX;
		this.baseY = baseY;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.plane = plane;
	}

	/** A scene centred on the given tile's chunk, the way the client centres it. */
	static FakeWorldView around(WorldPoint centre)
	{
		return new FakeWorldView(
			chunkAlignedBase(centre.getX()), chunkAlignedBase(centre.getY()),
			Constants.SCENE_SIZE, Constants.SCENE_SIZE, centre.getPlane());
	}

	/**
	 * A scene whose two axes cannot be mistaken for each other — see the class javadoc.
	 *
	 * @param baseX the west edge, which must differ from {@code baseY}
	 * @param baseY the south edge
	 * @param sizeX the width in tiles, which must differ from {@code sizeY}
	 * @param sizeY the height in tiles
	 * @param plane the plane this scene is on; {@code LocalPoint.fromWorld} refuses a
	 *              {@code WorldPoint} on any other, so every tile a test uses with this
	 *              view has to be on it too
	 * @throws IllegalArgumentException if either pair matches, because a fixture that
	 * quietly went back to being square would take the whole point of it with it
	 */
	static FakeWorldView rectangular(int baseX, int baseY, int sizeX, int sizeY, int plane)
	{
		if (baseX == baseY || sizeX == sizeY)
		{
			throw new IllegalArgumentException(
				"this fixture exists to be asymmetric: base " + baseX + "," + baseY
					+ " size " + sizeX + "," + sizeY);
		}
		return new FakeWorldView(baseX, baseY, sizeX, sizeY, plane);
	}

	private static int chunkAlignedBase(int coordinate)
	{
		// The client's own arithmetic: the centre chunk sits six chunks in.
		return ((coordinate / Constants.CHUNK_SIZE) - 6) * Constants.CHUNK_SIZE;
	}

	/** @return the world tile at the given scene coordinates in this view */
	WorldPoint tileAt(int sceneX, int sceneY)
	{
		return new WorldPoint(baseX + sceneX, baseY + sceneY, plane);
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
		return sizeX;
	}

	@Override
	public int getSizeY()
	{
		return sizeY;
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
		if (sceneX < 0 || sceneX >= sizeX || sceneY < 0 || sceneY >= sizeY)
		{
			throw new IllegalArgumentException(
				"tile " + tile + " is outside the fake scene at " + baseX + "," + baseY
					+ " sized " + sizeX + "x" + sizeY);
		}

		maps()[tile.getPlane()].set(sceneX, sceneY, mask);
		return this;
	}

	/**
	 * A view that hands back no collision maps at all.
	 *
	 * <p><b>Not a state the injected client is ever in.</b> Its {@code gc[]} field has
	 * exactly one assignment — {@code new gc[4]} in the world view's constructor — so
	 * {@code getCollisionMaps()} never returns {@code null} there. {@code WorldView} is
	 * an interface this plugin does not implement, though, and the raw API's answer for
	 * this case is {@code false}, i.e. "blocked", which is the wrong answer to act on.
	 * The branch exists so that the wrong answer cannot reach a follower; this is what
	 * proves the branch is wired up.
	 */
	FakeWorldView withoutCollisionData()
	{
		collisionDataAvailable = false;
		return this;
	}

	/**
	 * The array exists but this plane's map does not.
	 *
	 * <p>Also not the injected client's state — the same constructor fills all four
	 * slots in a loop before it returns — and here the raw API's answer is an NPE out of
	 * a game-tick handler rather than a wrong verdict. Same reasoning: the check is
	 * cheap, the interface is not ours, and this is what keeps it honest.
	 */
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
				// Sized off this view's own rectangle, the way the client allocates it,
				// so a rectangular scene really does have a rectangular flags array.
				collisionMaps[i] = new FakeCollisionData(sizeX, sizeY);
			}
		}
		return collisionMaps;
	}
}
