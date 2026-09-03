package com.matthewmariner.entourage;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The animation ids, pinned twice over.
 *
 * <p>Once against the {@code gameval} constant, which is what {@link EntourageAnimation}
 * actually names — that catches a constant being renamed underneath the enum. And once
 * against the literal number, which catches the id being <i>renumbered</i>: the constant
 * would still compile, the follower would play whatever animation now sits at that id,
 * and nothing else in the build would notice. Neither assertion is redundant with the
 * other, and the second is the one that would have caught the failure that killed
 * {@code ../lively-cities}' predecessor plugin.
 */
public class EntourageAnimationTest
{
	@Test
	public void theStandIsTheHumanRigsOwnReadyPose()
	{
		assertEquals(AnimationID.HUMAN_READY, EntourageAnimation.HUMAN_STAND.getId());
		assertEquals("HUMAN_READY has been 808 for the life of this cache",
			808, EntourageAnimation.HUMAN_STAND.getId());
	}

	@Test
	public void theWalkIsTheHumanRigsOwnForwardWalk()
	{
		assertEquals(AnimationID.HUMAN_WALK_F, EntourageAnimation.HUMAN_WALK.getId());
		assertEquals("HUMAN_WALK_F has been 819 for the life of this cache",
			819, EntourageAnimation.HUMAN_WALK.getId());
	}

	/**
	 * The forward walk, specifically. This plugin turns a follower to face its direction
	 * of travel before it moves, so it is always walking forwards; the backwards and
	 * sideways walks would make a figure moon-walk.
	 */
	@Test
	public void theWalkIsTheForwardOneAndNotOneOfItsThreeNeighbours()
	{
		int walk = EntourageAnimation.HUMAN_WALK.getId();
		assertNotEquals("backwards", AnimationID.HUMAN_WALK_B, walk);
		assertNotEquals("left", AnimationID.HUMAN_WALK_L, walk);
		assertNotEquals("right", AnimationID.HUMAN_WALK_R, walk);
	}

	/**
	 * A figure whose idle and walk are the same id is a figure that is not animated by
	 * moving, which is the exact failure this whole class exists to prevent.
	 */
	@Test
	public void everyAnimationIsDistinct()
	{
		Set<Integer> ids = new HashSet<>();
		for (EntourageAnimation animation : EntourageAnimation.values())
		{
			assertTrue(animation + " duplicates an id already in the enum", ids.add(animation.getId()));
		}
		assertEquals(EntourageAnimation.values().length, ids.size());
	}

	@Test
	public void everyIdIsPlausible()
	{
		for (EntourageAnimation animation : EntourageAnimation.values())
		{
			// Zero is the client's own "no animation", and it is a real trap: it looks
			// like a valid id, loads as null, and leaves a figure static forever.
			assertTrue(animation + " has a non-positive id", animation.getId() > 0);
			assertTrue(animation + " is outside the cache's sequence range",
				animation.getId() < 30_000);
		}
	}
}
