package com.matthewmariner.entourage;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Dressing a figure from an NPC, and every way that can fail without taking the tick
 * pass down with it.
 */
public class FollowerAppearanceTest
{
	private static final int NPC = 526;

	@Test
	public void aWorkingCompositionSuppliesModelsAndRecolours()
	{
		FakeClient client = new FakeClient().withNpc(NPC, FakeNpcComposition.recoloured(
			"Rogue", new int[]{11, 22, 33}, new short[]{4550, 900}, new short[]{100, 200}));

		FollowerAppearance appearance = FollowerAppearance.resolve(client, NPC, "test");

		assertNotNull(appearance);
		assertEquals("Rogue", appearance.getNpcName());
		assertArrayEquals(new int[]{11, 22, 33}, appearance.getModelIds());
		assertArrayEquals(new short[]{4550, 900}, appearance.getRecolorFind());
		assertArrayEquals(new short[]{100, 200}, appearance.getRecolorReplace());
	}

	/**
	 * The real failure path, and the reason there is a try/catch. An NPC id with no
	 * archive entry does not return null — the client's own composition constructor is
	 * handed a null buffer and the exception is rethrown wrapped.
	 */
	@Test
	public void anIdThatThrowsIsAMissRatherThanAnException()
	{
		assertNull(FollowerAppearance.resolve(new FakeClient(), NPC, "test"));
	}

	@Test
	public void anIdThatResolvesToNullIsAMiss()
	{
		FakeClient client = new FakeClient().withNullNpcComposition(NPC);
		assertNull(FollowerAppearance.resolve(client, NPC, "test"));
	}

	@Test
	public void aCompositionWithNoModelsIsAMiss()
	{
		FakeClient client = new FakeClient()
			.withNpc(NPC, FakeNpcComposition.withNulls("Rogue", null, null, null));
		assertNull("a null model array is not an empty one",
			FollowerAppearance.resolve(client, NPC, "test"));
	}

	@Test
	public void aCompositionWhoseModelsAreAllEmptySlotsIsAMiss()
	{
		FakeClient client = new FakeClient()
			.withNpc(NPC, FakeNpcComposition.of("Rogue", -1, -1, 0));
		assertNull(FollowerAppearance.resolve(client, NPC, "test"));
	}

	/**
	 * A {@code -1} in a composition's model array is the client's own "this slot is
	 * empty", not a missing part, so the rest of the body still dresses. Asking the
	 * client about the sentinel would get a null back that {@link Follower} would read as
	 * a cold cache and retry three times for nothing.
	 */
	@Test
	public void emptySlotsAreDroppedAndTheRestAreKept()
	{
		FakeClient client = new FakeClient()
			.withNpc(NPC, FakeNpcComposition.of("Rogue", 11, -1, 33, 0, 44));

		FollowerAppearance appearance = FollowerAppearance.resolve(client, NPC, "test");

		assertNotNull(appearance);
		assertArrayEquals(new int[]{11, 33, 44}, appearance.getModelIds());
	}

	@Test
	public void nullRecolourArraysBecomeEmptyOnesRatherThanAnException()
	{
		FakeClient client = new FakeClient()
			.withNpc(NPC, FakeNpcComposition.withNulls("Rogue", new int[]{11}, null, null));

		FollowerAppearance appearance = FollowerAppearance.resolve(client, NPC, "test");

		assertNotNull(appearance);
		assertEquals(0, appearance.getRecolorFind().length);
		assertEquals(0, appearance.getRecolorReplace().length);
	}

	/**
	 * The two arrays come out of two separate cache fields and there is nothing in the
	 * format that makes them the same length. A find with no replace would be an index
	 * out of bounds in the middle of a model build.
	 */
	@Test
	public void mismatchedRecolourArraysAreTruncatedToWholePairs()
	{
		FakeClient client = new FakeClient().withNpc(NPC, FakeNpcComposition.recoloured(
			"Rogue", new int[]{11}, new short[]{1, 2, 3}, new short[]{9}));

		FollowerAppearance appearance = FollowerAppearance.resolve(client, NPC, "test");

		assertNotNull(appearance);
		assertArrayEquals(new short[]{1}, appearance.getRecolorFind());
		assertArrayEquals(new short[]{9}, appearance.getRecolorReplace());
	}

	@Test
	public void aNonPositiveIdIsRefusedWithoutAskingTheClient()
	{
		FakeClient client = new FakeClient();

		assertNull(FollowerAppearance.resolve(client, 0, "test"));
		assertNull(FollowerAppearance.resolve(client, -1, "test"));
		assertTrue("the client must not be asked about a sentinel",
			client.npcDefinitionsRequested().isEmpty());
	}

	@Test
	public void theModelArrayIsACopyRatherThanTheCompositionsOwn()
	{
		int[] compositionModels = {11, 22};
		FakeClient client = new FakeClient()
			.withNpc(NPC, FakeNpcComposition.of("Rogue", compositionModels));

		FollowerAppearance appearance = FollowerAppearance.resolve(client, NPC, "test");

		assertNotNull(appearance);
		compositionModels[0] = 999;
		assertEquals("a figure's body must not change when the client's cache entry does",
			11, appearance.getModelIds()[0]);
	}
}
