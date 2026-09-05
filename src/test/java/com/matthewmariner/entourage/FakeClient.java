package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Animation;
import net.runelite.api.GameState;
import net.runelite.api.IndexDataBase;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;

/**
 * A client that keeps the registered-object list and the model cache in fields.
 *
 * <p>This is what makes the lifecycle testable without a game running. In 1.12.38
 * (verified by disassembling the API jar) {@code RuneLiteObject} is not a black box:
 * {@code isActive()} is exactly {@code client.isRuneLiteObjectRegistered(this)} and
 * {@code setActive(b)} is exactly {@code client.registerRuneLiteObject(this)} /
 * {@code removeRuneLiteObject(this)}. So a {@link Set} standing in for the client's list
 * turns "did teardown actually deactivate anything?" into an assertion, and the objects
 * under test are real {@code RuneLiteObject}s running their real code.
 *
 * <p>Everything this plugin does not call is inherited from {@link StubClient} and
 * throws.
 *
 * <p>{@link #getNpcDefinition(int)} is modelled on the real one's <i>failure</i> shape
 * rather than on convenience — an unknown id throws. See that method.
 */
final class FakeClient extends StubClient
{
	/** Stands in for the client's registered-object list. */
	private final Set<RuneLiteObjectController> registered = new LinkedHashSet<>();

	/** Model ids that resolve to nothing, whatever the cache is doing. */
	private final Set<Integer> unloadableModels = new HashSet<>();

	/** Animation ids that resolve to nothing. */
	private final Set<Integer> unloadableAnimations = new HashSet<>();

	/**
	 * Animation ids whose load blows up rather than returning null.
	 *
	 * <p>{@code new AnimationController(client, id)} calls {@code client.loadAnimation}
	 * straight through, so this is a real path by which a throw reaches
	 * {@code Follower.onGameTick} from outside its own try/catch — which is what
	 * {@code EntourageSceneTest} needs in order to prove the scene's pass survives one.
	 */
	private final Set<Integer> throwingAnimations = new HashSet<>();

	/** NPC compositions this client knows about, keyed by id. */
	private final Map<Integer, NPCComposition> npcCompositions = new HashMap<>();

	/** NPC ids whose lookup returns null rather than throwing. */
	private final Set<Integer> nullNpcCompositions = new HashSet<>();

	/** Animation ids that were asked for, in order. */
	private final List<Integer> animationsLoaded = new ArrayList<>();

	/** Model ids that were asked for, in order. */
	private final List<Integer> modelsLoaded = new ArrayList<>();

	/** NPC ids that were asked about, in order. */
	private final List<Integer> npcDefinitionsRequested = new ArrayList<>();

	/** True while the cache is pretending to be cold: models, animations and NPCs miss. */
	private boolean cacheCold;

	/** Accepts the register call and does nothing with it. */
	private boolean refuseRegistration;

	/** Throws out of {@code removeRuneLiteObject}, leaving the object registered. */
	private boolean throwFromRemoval;

	/** Throws out of {@code isRuneLiteObjectRegistered}. */
	private boolean throwFromRegistrationCheck;

	private FakeRuneLiteObject lastObject;
	private FakeModelData lastMerged;
	private int mergeCalls;
	private int lastMergePartCount;

	private int gameCycle;
	private GameState gameState = GameState.LOGGED_IN;

	@Nullable
	private Player localPlayer;

	@Nullable
	private WorldView topLevelWorldView;

	// --- The registered-object list ------------------------------------------

	@Override
	public RuneLiteObject createRuneLiteObject()
	{
		lastObject = new FakeRuneLiteObject(this);
		return lastObject;
	}

	/** @return the most recently created object, for the tests that ask it what happened */
	FakeRuneLiteObject lastObject()
	{
		return lastObject;
	}

	@Override
	public void registerRuneLiteObject(RuneLiteObjectController controller)
	{
		if (refuseRegistration)
		{
			return;
		}
		registered.add(controller);
	}

	@Override
	public void removeRuneLiteObject(RuneLiteObjectController controller)
	{
		removalAttempts++;
		if (throwFromRemoval)
		{
			throw new IllegalStateException("the client will not let go of this object");
		}
		registered.remove(controller);
	}

	/**
	 * @return how many times anything asked the client to deactivate an object,
	 * successful or not.
	 *
	 * <p>Counted because "we gave up trying" is otherwise unobservable. A retirement that
	 * could not let go of its object has to stop retrying — a scene which noticed the
	 * same figure mismatch on every tick would attempt a deactivation, and log a warning,
	 * 100 times a minute for the rest of the session — and the only trace of the latch
	 * that stops it is the call that does not happen.
	 */
	int removalAttempts()
	{
		return removalAttempts;
	}

	private int removalAttempts;

	@Override
	public boolean isRuneLiteObjectRegistered(RuneLiteObjectController controller)
	{
		if (throwFromRegistrationCheck)
		{
			throw new IllegalStateException("the client will not say whether it has this object");
		}
		return registered.contains(controller);
	}

	/** @return how many objects the client currently has registered */
	int registeredCount()
	{
		return registered.size();
	}

	/** The client takes {@code setActive(true)} and does not register the object. */
	FakeClient refusingRegistration()
	{
		refuseRegistration = true;
		return this;
	}

	/**
	 * {@code setActive(false)} blows up and the object stays registered — the state in
	 * which forgetting a follower leaks the object it was holding. No realistic in-client
	 * trigger for this is known; it is here because the teardown promise is unconditional,
	 * and a promise nothing can falsify is not one.
	 */
	FakeClient refusingDeactivation()
	{
		throwFromRemoval = true;
		return this;
	}

	/** The client will not even say whether it has an object. */
	FakeClient withThrowingRegistrationChecks()
	{
		throwFromRegistrationCheck = true;
		return this;
	}

	// --- The model and animation cache ---------------------------------------

	@Override
	@Nullable
	public ModelData loadModelData(int id)
	{
		modelsLoaded.add(id);
		return cacheCold || unloadableModels.contains(id) ? null : new FakeModelData();
	}

	@Override
	@Nullable
	public ModelData mergeModels(ModelData[] parts, int count)
	{
		mergeCalls++;
		lastMergePartCount = count;
		lastMerged = new FakeModelData();
		return lastMerged;
	}

	@Override
	@Nullable
	public Animation loadAnimation(int id)
	{
		animationsLoaded.add(id);

		if (throwingAnimations.contains(id))
		{
			throw new IllegalStateException("animation " + id + " blew up on the way out of the cache");
		}

		return cacheCold || unloadableAnimations.contains(id) ? null : new FakeAnimation(id);
	}

	/**
	 * {@code Client.getNpcDefinition(int)} as 1.12.38 actually behaves.
	 *
	 * <p>Three outcomes, and each is one this plugin has to cope with:
	 * <ul>
	 *   <li><b>Known id</b> — the composition that was registered.</li>
	 *   <li><b>Explicitly null</b> — see {@link #withNullNpcComposition}. The accessor is
	 *       a cache read, so a null is possible even though it is not the usual
	 *       failure.</li>
	 *   <li><b>Anything else, including while the cache is cold</b> — throws. That is the
	 *       real path for an id whose archive entry is absent: the composition
	 *       constructor is handed a null buffer inside the client's own
	 *       {@code catch (RuntimeException)} and rethrown wrapped. A permissive fake here
	 *       would make {@link FollowerAppearance}'s try/catch dead code that no test
	 *       could tell from an empty method body.</li>
	 * </ul>
	 */
	@Override
	@Nullable
	public NPCComposition getNpcDefinition(int id)
	{
		npcDefinitionsRequested.add(id);

		if (nullNpcCompositions.contains(id))
		{
			return null;
		}

		NPCComposition composition = cacheCold ? null : npcCompositions.get(id);
		if (composition == null)
		{
			throw new IllegalStateException("no NPC composition for id " + id);
		}
		return composition;
	}

	FakeClient withNpc(int id, NPCComposition composition)
	{
		npcCompositions.put(id, composition);
		return this;
	}

	/** Registers a working composition for every figure in the roster. */
	FakeClient withRosterNpcs()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			withNpc(figure.getNpcId(), FakeNpcComposition.of(figure.name(), 100 + figure.ordinal()));
		}
		return this;
	}

	FakeClient withNullNpcComposition(int id)
	{
		nullNpcCompositions.add(id);
		return this;
	}

	FakeClient withUnloadableModels(int... ids)
	{
		for (int id : ids)
		{
			unloadableModels.add(id);
		}
		return this;
	}

	FakeClient withUnloadableAnimations(int... ids)
	{
		for (int id : ids)
		{
			unloadableAnimations.add(id);
		}
		return this;
	}

	void clearUnloadableAnimations()
	{
		unloadableAnimations.clear();
	}

	FakeClient withThrowingAnimations(int... ids)
	{
		for (int id : ids)
		{
			throwingAnimations.add(id);
		}
		return this;
	}

	// --- The config index ----------------------------------------------------

	/**
	 * The config index, or {@code null} — which is what the real client answers before
	 * one is up, and is therefore the honest default for a client nobody has logged into.
	 * A test that wants {@link NpcArchive} to get anywhere has to say so.
	 */
	@Nullable
	private FakeIndexDataBase indexConfig;

	private boolean throwFromIndexConfig;

	@Override
	@Nullable
	public IndexDataBase getIndexConfig()
	{
		if (throwFromIndexConfig)
		{
			throw new IllegalStateException("the config index is not available");
		}
		return indexConfig;
	}

	FakeClient withIndexConfig(FakeIndexDataBase index)
	{
		this.indexConfig = index;
		return this;
	}

	FakeClient withThrowingIndexConfig()
	{
		throwFromIndexConfig = true;
		return this;
	}

	/**
	 * {@code Perspective.getTileHeight} asks the client for the world view by id, so
	 * this is a throw that reaches the frame pass from inside
	 * {@code RuneLiteObject.setLocation}.
	 */
	FakeClient withThrowingWorldViewLookup()
	{
		throwFromGetWorldView = true;
		return this;
	}

	private boolean throwFromGetWorldView;

	/** Nothing at all comes out of the cache, which is a cold login. */
	void setCacheCold(boolean cold)
	{
		cacheCold = cold;
	}

	List<Integer> animationsLoaded()
	{
		return animationsLoaded;
	}

	List<Integer> modelsLoaded()
	{
		return modelsLoaded;
	}

	List<Integer> npcDefinitionsRequested()
	{
		return npcDefinitionsRequested;
	}

	int mergeCalls()
	{
		return mergeCalls;
	}

	int lastMergePartCount()
	{
		return lastMergePartCount;
	}

	@Nullable
	FakeModelData lastMerged()
	{
		return lastMerged;
	}

	// --- The world -----------------------------------------------------------

	/**
	 * {@code RuneLiteObject.setLocation} runs
	 * {@code Perspective.getTileHeight(client, ..)}, which asks the client for the world
	 * view by id and returns height 0 when there is none. Null is therefore the honest
	 * answer for a scene that only exists as numbers, and it keeps the real
	 * {@code setLocation} on its real code path.
	 */
	@Override
	@Nullable
	public WorldView getWorldView(int id)
	{
		if (throwFromGetWorldView)
		{
			throw new IllegalStateException("the scene is being swapped out from under you");
		}
		return null;
	}

	@Override
	@Nullable
	public WorldView getTopLevelWorldView()
	{
		return topLevelWorldView;
	}

	void setTopLevelWorldView(@Nullable WorldView worldView)
	{
		this.topLevelWorldView = worldView;
	}

	@Override
	@Nullable
	public Player getLocalPlayer()
	{
		return localPlayer;
	}

	void setLocalPlayer(@Nullable Player player)
	{
		this.localPlayer = player;
	}

	@Override
	public GameState getGameState()
	{
		return gameState;
	}

	/**
	 * Public and {@code @Override} because {@code Client} declares it — the real client
	 * has a setter here too, and a package-private one would be a narrowing.
	 */
	@Override
	public void setGameState(GameState gameState)
	{
		this.gameState = gameState;
	}

	/**
	 * The client's 20ms clock. An {@code int} on purpose — it is one in the API, and the
	 * plugin's fraction arithmetic has to survive it wrapping.
	 */
	@Override
	public int getGameCycle()
	{
		return gameCycle;
	}

	void setGameCycle(int gameCycle)
	{
		this.gameCycle = gameCycle;
	}

	void advanceGameCycle(int clientTicks)
	{
		gameCycle += clientTicks;
	}
}
