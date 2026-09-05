package com.matthewmariner.entourage;

import static com.matthewmariner.entourage.NpcRecordBytes.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

/**
 * A follower wearing an NPC id the user typed rather than one of the twenty-three
 * presets.
 *
 * <p><b>The thing under test is mostly what happens when the id does not work.</b>
 * Dressing a figure from an arbitrary composition is the easy half and the client does
 * it; the hard half is that {@code NPCComposition} carries no animations, so the pair has
 * to come out of the cache — and an arbitrary NPC may have no walk, may walk with the
 * same animation it stands with, or may not exist at all. Every one of those has to end
 * as the slot's own preset standing there and a line in the log, rather than as a body
 * sliding across the ground.
 *
 * <p>The other half is the split this class inherits from {@link Follower}: "the cache
 * has not answered yet" is retryable and must never latch, and "the archive does not have
 * this id" is permanent and must never be retried.
 */
public class FollowerCustomNpcTest
{
	/** Where the follower stands. */
	private static final WorldPoint ANCHOR = new WorldPoint(3221, 3218, 0);

	/** A player far enough east to make the follower run. */
	private static final WorldPoint EAST = ANCHOR.dx(10);

	/** The typed id. Nothing in {@link EntourageFigure} uses it. */
	private static final int CUSTOM = 4931;

	/** The custom NPC's own animations, none of them shared with the fallback preset. */
	private static final int CUSTOM_STAND = 5101;
	private static final int CUSTOM_WALK = 5102;
	private static final int CUSTOM_RUN = 5103;

	/** The model the custom composition is built from. */
	private static final int CUSTOM_MODEL = 60_001;

	/** Whose dropdown the custom id is sitting on top of, and what it falls back to. */
	private static final EntourageFigure FALLBACK = EntourageFigure.NIEVE;

	private FakeClient client;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs()
			.withNpc(CUSTOM, FakeNpcComposition.of("Cave goblin guard", CUSTOM_MODEL));
		// Asymmetric on purpose — a scene whose axes cannot be mistaken for each other,
		// which is the fixture this repo uses for anything that touches coordinates.
		view = FakeWorldView.rectangular(3200, 3190, 64, 48, 0);
		client.setTopLevelWorldView(view);
	}

	// --- The id works ---------------------------------------------------------

	@Test
	public void aWorkingIdDressesTheFollowerFromThatNpc()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));

		Follower follower = tick(custom());

		assertTrue(follower.isActive());
		assertNotNull(follower.getAppearance());
		assertTrue("the typed id's models, not the dropdown's",
			client.modelsLoaded().contains(CUSTOM_MODEL));
		assertTrue("and its composition was the one asked for",
			client.npcDefinitionsRequested().contains(CUSTOM));
		assertFalse("the fallback's was never wanted",
			client.npcDefinitionsRequested().contains(FALLBACK.getNpcId()));
	}

	@Test
	public void aWorkingIdPlaysThatNpcsOwnStandAndWalk()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));

		Follower follower = custom();
		EntourageSettings walkOnly = new FakeConfig().setCanRun(false).setCustomNpcId(CUSTOM)
			.setFigure(FALLBACK).settings();
		follower.onGameTick(anchor(ANCHOR), view, walkOnly);
		follower.onGameTick(anchor(ANCHOR.dx(3)), view, walkOnly);

		assertTrue("this test needs it walking", follower.getWalk().isMoving());
		assertTrue("the cache's stand for that id", client.animationsLoaded().contains(CUSTOM_STAND));
		assertTrue("and the cache's walk", client.animationsLoaded().contains(CUSTOM_WALK));
		assertFalse("and not the dropdown figure's",
			client.animationsLoaded().contains(FALLBACK.getWalkAnimation().getId()));
	}

	/**
	 * The cache's own run, rather than the human rig's — which is what every preset gets,
	 * because no preset was ever read for one. Opcodes 114 and 115 declare it.
	 */
	@Test
	public void aWorkingIdPlaysThatNpcsOwnRun()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));

		Follower follower = tick(custom());
		follower.onGameTick(anchor(EAST), view, settings());

		assertTrue("this test needs it running", follower.getWalk().isRunning());
		assertTrue(client.animationsLoaded().contains(CUSTOM_RUN));
		assertFalse("and not the fallback's",
			client.animationsLoaded().contains(FALLBACK.getRunAnimation().getId()));
	}

	/**
	 * Most NPCs never run and declare no run animation, which arrives here as {@code -1}.
	 * Asking the client for sequence {@code -1} can only fail, and failing costs one of
	 * the three attempts the model and the walk are relying on.
	 */
	@Test
	public void anIdWithNoRunFallsBackToItsWalkWithoutSpendingAnAttempt()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, record()
			.standing(CUSTOM_STAND)
			.walking(CUSTOM_WALK)
			.end()));

		Follower follower = tick(custom());
		follower.onGameTick(anchor(EAST), view, settings());

		assertTrue("this test needs it running", follower.getWalk().isRunning());
		for (int loaded : client.animationsLoaded())
		{
			assertTrue("asked the cache for sequence " + loaded, loaded >= 0);
		}
		assertTrue("it moves on its walk instead", client.animationsLoaded().contains(CUSTOM_WALK));
		assertNotNull("and it is still animated", follower.getInstalledController());
	}

	/**
	 * The name label is the one confirmation a user gets without opening a log: the NPC's
	 * own name over the figure means the id took.
	 */
	@Test
	public void aWorkingIdIsNamedAfterTheNpcRatherThanTheDropdown()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));

		assertEquals("Cave goblin guard", tick(custom()).getDisplayName());
	}

	@Test
	public void anUnnamedNpcFallsBackToTheDropdownsName()
	{
		// "null" is the cache's own placeholder for an NPC with no name, and a follower
		// labelled with it would read as a bug rather than as an unnamed body.
		client = new FakeClient().withRosterNpcs()
			.withNpc(CUSTOM, FakeNpcComposition.of("null", CUSTOM_MODEL))
			.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));
		client.setTopLevelWorldView(view);

		assertEquals(FALLBACK.getDisplayName(), tick(custom()).getDisplayName());
	}

	// --- The id does not work -------------------------------------------------

	@Test
	public void anIdTheArchiveDoesNotHaveFallsBackToTheDropdownFigure()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM + 1, workingRecord()));

		Follower follower = tick(custom());

		assertTrue("something is standing there", follower.isActive());
		assertTrue("wearing the dropdown figure",
			client.npcDefinitionsRequested().contains(FALLBACK.getNpcId()));
		assertFalse("and not the typed id",
			client.npcDefinitionsRequested().contains(CUSTOM));
		assertEquals("which is also what the name label says",
			FALLBACK.getDisplayName(), follower.getDisplayName());
	}

	/**
	 * Krystilia's shape: one id in both fields. A follower given it would hold its
	 * standing pose while covering ground, which is the sliding-mesh failure with an extra
	 * step.
	 */
	@Test
	public void anIdWhoseWalkIsItsStandIsRefused()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, record()
			.standing(CUSTOM_STAND)
			.walking(CUSTOM_STAND)
			.end()));

		assertWoreTheFallback(tick(custom()));
	}

	/** Spria's shape: neither animation declared at all. */
	@Test
	public void anIdWithNoAnimationsAtAllIsRefused()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, record().end()));

		assertWoreTheFallback(tick(custom()));
	}

	@Test
	public void anIdThatStandsButDoesNotWalkIsRefused()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, record()
			.standing(CUSTOM_STAND)
			.end()));

		assertWoreTheFallback(tick(custom()));
	}

	@Test
	public void anIdWhoseRecordIsUnreadableIsRefused()
	{
		// A record that ends before its terminator. Whatever it declares cannot be
		// trusted, so the id is turned down rather than half-used.
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, record()
			.u8(1).u8(255)
			.standing(CUSTOM_STAND)
			.walking(CUSTOM_WALK)
			.end()));

		assertWoreTheFallback(tick(custom()));
	}

	/**
	 * A refusal is permanent and must be latched. Retrying it would be one archive read
	 * and one warning per game tick for the rest of the session.
	 */
	@Test
	public void aRefusedIdIsNotAskedAboutAgain()
	{
		FakeIndexDataBase index = new FakeIndexDataBase().withNpc(CUSTOM, record().end());
		client.withIndexConfig(index);

		Follower follower = custom();
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS * 3; i++)
		{
			follower.onGameTick(anchor(ANCHOR), view, settings());
		}

		assertTrue(follower.isActive());
		assertEquals("the archive was read once and the answer kept", 1, index.loads().size());
	}

	/**
	 * The same latch, for the follower that cannot get on screen at all.
	 *
	 * <p>The test above only proves the refusal sticks because a spawned follower never
	 * re-enters the spawn path. A follower whose <i>fallback</i> also cannot build a model
	 * does re-enter it, on every allowed attempt — and that is the case where an unlatched
	 * refusal would read the archive and warn again each time.
	 */
	@Test
	public void aRefusedIdIsNotAskedAboutAgainEvenWhileTheFallbackCannotSpawn()
	{
		FakeIndexDataBase index = new FakeIndexDataBase().withNpc(CUSTOM, record().end());
		client.withIndexConfig(index)
			.withUnloadableModels(100 + FALLBACK.ordinal());

		Follower follower = custom();
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS * (Follower.MAX_ATTEMPTS + 1); i++)
		{
			follower.onGameTick(anchor(ANCHOR), view, settings());
		}

		assertFalse("this test needs a follower that never gets on screen", follower.isActive());
		assertEquals("the archive was read once and the answer kept", 1, index.loads().size());
	}

	// --- The cache has not answered yet ---------------------------------------

	/**
	 * {@code loadData} answering null means the group is not in memory and the client has
	 * queued a fetch. Latching a refusal on it would lose the typed id for the session on
	 * a slow login — so nothing is dressed, nothing is refused, and it is tried again.
	 */
	@Test
	public void anIdThatIsNotResidentYetIsRetriedRatherThanRefused()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpcNotResident(CUSTOM));

		Follower follower = tick(custom());

		assertFalse("nothing may be dressed from an id that has not been read",
			follower.isActive());
		assertTrue("and no models were asked for either", client.modelsLoaded().isEmpty());
	}

	@Test
	public void anIdThatArrivesLateIsPickedUpOnARetry()
	{
		FakeIndexDataBase index = new FakeIndexDataBase().withNpcNotResident(CUSTOM);
		client.withIndexConfig(index);

		Follower follower = tick(custom());
		assertFalse(follower.isActive());

		// The client's fetch lands.
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));
		for (int i = 0; i < Follower.RETRY_BACKOFF_TICKS + 2; i++)
		{
			follower.onGameTick(anchor(ANCHOR), view, settings());
		}

		assertTrue("once the cache warms up, the typed id takes", follower.isActive());
		assertTrue(client.modelsLoaded().contains(CUSTOM_MODEL));
	}

	@Test
	public void noConfigIndexAtAllIsRetriedRatherThanRefused()
	{
		Follower follower = tick(custom());

		assertFalse(follower.isActive());
		assertTrue("nothing was dressed", client.modelsLoaded().isEmpty());
	}

	// --- The teardown promise -------------------------------------------------

	/**
	 * The rule is unconditional, and a custom body is a new way of reaching the spawn
	 * path — so it gets the same assertion the presets do, against the client's own
	 * registered-object list rather than against this plugin's bookkeeping.
	 */
	@Test
	public void aCustomFollowerStillComesOffTheScreenCompletely()
	{
		client.withIndexConfig(new FakeIndexDataBase().withNpc(CUSTOM, workingRecord()));

		Follower follower = tick(custom());
		assertEquals(1, client.registeredCount());

		assertTrue(follower.despawn());
		assertEquals("nothing left registered", 0, client.registeredCount());
	}

	// --- Fixtures --------------------------------------------------------------

	private Follower custom()
	{
		return new Follower(client, FollowerBody.custom(CUSTOM, FALLBACK), 0, ANCHOR);
	}

	private Follower tick(Follower follower)
	{
		follower.onGameTick(anchor(ANCHOR), view, settings());
		return follower;
	}

	private EntourageSettings settings()
	{
		return new FakeConfig().setCustomNpcId(CUSTOM).setFigure(FALLBACK).settings();
	}

	private FollowerAnchor anchor(WorldPoint tile)
	{
		FollowerAnchor anchor = FollowerAnchor.of(FakePlayer.standingOn(view, tile), view);
		assertTrue("the fixture's anchor has to resolve, or the test proves nothing",
			anchor.isResolved());
		return anchor;
	}

	/** A record declaring a stand, a walk and a run, all different. */
	private static byte[] workingRecord()
	{
		return record()
			.standing(CUSTOM_STAND)
			.walking(CUSTOM_WALK)
			.u8(114).u16(CUSTOM_RUN)
			.end();
	}

	private void assertWoreTheFallback(Follower follower)
	{
		assertTrue("a refused id still leaves a figure standing there", follower.isActive());
		assertTrue("dressed from the dropdown",
			client.npcDefinitionsRequested().contains(FALLBACK.getNpcId()));
		assertFalse("and never from the typed id",
			client.npcDefinitionsRequested().contains(CUSTOM));
		assertFalse("nor animated with the id's own numbers",
			client.animationsLoaded().contains(CUSTOM_WALK));
	}
}
