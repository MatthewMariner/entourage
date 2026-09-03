package com.matthewmariner.entourage;

import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The roster, and the two claims made about it.
 */
public class EntourageFigureTest
{
	/**
	 * One follower. Getting a single figure to walk correctly is this slice; a formation
	 * of four figures doing it wrong is not four times the feature. If this goes red, it
	 * should be because somebody meant to grow the roster and updated the plan with it.
	 */
	@Test
	public void theDefaultRosterIsOneFigure()
	{
		assertEquals(1, EntourageFigure.DEFAULT_ROSTER.size());
		assertEquals(EntourageFigure.ROGUE, EntourageFigure.DEFAULT_ROSTER.get(0));
	}

	@Test
	public void theRosterIsImmutable()
	{
		try
		{
			EntourageFigure.DEFAULT_ROSTER.add(EntourageFigure.FARMER);
			fail("the roster must not be something a caller can grow by accident");
		}
		catch (UnsupportedOperationException expected)
		{
			// Collections.singletonList.
		}
	}

	@Test
	public void everyFigureInTheRosterIsARealFigure()
	{
		for (EntourageFigure figure : EntourageFigure.DEFAULT_ROSTER)
		{
			assertTrue(figure.name(), figure.getNpcId() > 0);
		}
	}

	/**
	 * The two NPC ids, named. {@link EntourageFigure#FARMER} in particular is only worth
	 * keeping if it is still the id {@code ../lively-cities} proves out with this exact
	 * animation pair, so it is pinned rather than left to rot as a comment.
	 */
	@Test
	public void theNpcIdsAreTheOnesTheJavadocClaims()
	{
		assertEquals(NpcID.ROGUE, EntourageFigure.ROGUE.getNpcId());
		assertEquals(NpcID.FARMER1, EntourageFigure.FARMER.getNpcId());
		assertEquals("../lively-cities dresses a figure from 526 and animates it on the "
			+ "human framemap", 526, EntourageFigure.ROGUE.getNpcId());
		assertEquals("../lively-cities ships 3114 with this exact stand/walk pair",
			3114, EntourageFigure.FARMER.getNpcId());
	}

	@Test
	public void noFigureIdlesAndWalksWithTheSameAnimation()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertNotEquals(figure + " would slide along the ground",
				figure.getIdleAnimation(), figure.getWalkAnimation());
		}
	}

	@Test
	public void everyFigureHasBothAnimations()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertTrue(figure + " has no idle", figure.getIdleAnimation().getId() > 0);
			assertTrue(figure + " has no walk", figure.getWalkAnimation().getId() > 0);
		}
	}

	@Test
	public void theLabelNamesTheNpcSoALogLineCanBeTracedToACacheId()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertTrue(figure.label(), figure.label().contains(String.valueOf(figure.getNpcId())));
		}
	}
}
