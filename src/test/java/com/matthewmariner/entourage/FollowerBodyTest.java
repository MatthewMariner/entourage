package com.matthewmariner.entourage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Whose body a follower wears, and the value equality the roster comparison rests on.
 */
public class FollowerBodyTest
{
	/** An id nothing else in this suite uses. */
	private static final int CUSTOM = 4931;

	@Test
	public void aPresetWearsItsFiguresOwnNpc()
	{
		FollowerBody body = FollowerBody.preset(EntourageFigure.VANNAKA);

		assertFalse(body.isCustom());
		assertSame(EntourageFigure.VANNAKA, body.getFigure());
		assertEquals(EntourageFigure.VANNAKA.getNpcId(), body.getNpcId());
		assertEquals(EntourageFigure.VANNAKA.label(), body.label());
	}

	@Test
	public void aCustomBodyWearsTheTypedIdAndKeepsItsFallback()
	{
		FollowerBody body = FollowerBody.custom(CUSTOM, EntourageFigure.VANNAKA);

		assertTrue(body.isCustom());
		assertEquals(CUSTOM, body.getNpcId());
		assertNotEquals("the typed id must win over the dropdown",
			EntourageFigure.VANNAKA.getNpcId(), body.getNpcId());
		assertSame("and the dropdown is still what it falls back to",
			EntourageFigure.VANNAKA, body.getFigure());
	}

	@Test
	public void aCustomLabelNamesTheIdRatherThanTheFallback()
	{
		// Pinned to the literal: a log line that said "vannaka (npc 3070)" while the
		// follower was wearing 4931 would send a reader to the wrong cache entry.
		assertEquals("custom (npc 4931)", FollowerBody.custom(CUSTOM, EntourageFigure.VANNAKA).label());
	}

	@Test
	public void anIdOfZeroOrLessIsNotACustomBodyAtAll()
	{
		assertFalse("zero is the box's own empty value",
			FollowerBody.custom(0, EntourageFigure.ROGUE).isCustom());
		assertFalse("and a negative number cannot be a file id",
			FollowerBody.custom(-5, EntourageFigure.ROGUE).isCustom());
		assertEquals("a floored id is the preset, not a body wearing npc -5",
			EntourageFigure.ROGUE.getNpcId(),
			FollowerBody.custom(-5, EntourageFigure.ROGUE).getNpcId());

		assertEquals(FollowerBody.preset(EntourageFigure.ROGUE),
			FollowerBody.custom(0, EntourageFigure.ROGUE));

		// Pinned to the literal rather than to itself.
		assertEquals(0, FollowerBody.NO_CUSTOM_NPC);
	}

	// --- Value equality, which is what makes a settings change a rebuild -------

	@Test
	public void twoPresetsOfTheSameFigureAreEqual()
	{
		assertEquals(FollowerBody.preset(EntourageFigure.HANS),
			FollowerBody.preset(EntourageFigure.HANS));
		assertEquals(FollowerBody.preset(EntourageFigure.HANS).hashCode(),
			FollowerBody.preset(EntourageFigure.HANS).hashCode());
	}

	@Test
	public void twoPresetsOfDifferentFiguresAreNot()
	{
		assertNotEquals(FollowerBody.preset(EntourageFigure.HANS),
			FollowerBody.preset(EntourageFigure.PIRATE));
	}

	/**
	 * The one that matters. {@code EntourageScene} rebuilds the roster when this tick's
	 * list of bodies differs from the one the followers were built for — so a typed id
	 * changed under a follower has to be a different body, or the follower goes on wearing
	 * the old NPC until something else forces a rebuild.
	 */
	@Test
	public void twoCustomBodiesWithDifferentIdsAreNotEqual()
	{
		assertNotEquals(FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE),
			FollowerBody.custom(CUSTOM + 1, EntourageFigure.ROGUE));
	}

	/**
	 * Not the {@code hashCode} contract — unequal objects are allowed to collide — but the
	 * only way to make the id's presence in the hash something a test can break. A hash
	 * that ignored it would be legal and would put every custom body in one bucket.
	 */
	@Test
	public void theTypedIdIsPartOfTheHash()
	{
		assertNotEquals(FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE).hashCode(),
			FollowerBody.custom(CUSTOM + 1, EntourageFigure.ROGUE).hashCode());
	}

	@Test
	public void aCustomBodyIsNotEqualToItsOwnFallback()
	{
		assertNotEquals(FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE),
			FollowerBody.preset(EntourageFigure.ROGUE));
	}

	/**
	 * The same id behind two different dropdowns is two different bodies, because the
	 * dropdown decides what the follower says and what it falls back to.
	 */
	@Test
	public void theFallbackIsPartOfTheIdentity()
	{
		assertNotEquals(FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE),
			FollowerBody.custom(CUSTOM, EntourageFigure.HANS));
	}

	@Test
	public void twoCustomBodiesWithTheSameIdAndFallbackAreEqual()
	{
		assertEquals(FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE),
			FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE));
		assertEquals(FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE).hashCode(),
			FollowerBody.custom(CUSTOM, EntourageFigure.ROGUE).hashCode());
	}

	@Test
	public void equalsSurvivesNullAndAnotherType()
	{
		FollowerBody body = FollowerBody.preset(EntourageFigure.ROGUE);

		assertNotEquals(body, null);
		assertNotEquals(body, EntourageFigure.ROGUE);
		assertEquals(body, body);
	}
}
