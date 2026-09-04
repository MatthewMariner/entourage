package com.matthewmariner.entourage;

import java.util.EnumSet;
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
	private final EnumSet<EntourageAnimation> pinned = EnumSet.noneOf(EntourageAnimation.class);

	@Test
	public void everyIdIsTheConstantAndTheNumber()
	{
		// The human rig's own three.
		pin(EntourageAnimation.HUMAN_STAND, AnimationID.HUMAN_READY, 808);
		pin(EntourageAnimation.HUMAN_WALK, AnimationID.HUMAN_WALK_F, 819);
		pin(EntourageAnimation.HUMAN_RUN, AnimationID.HUMAN_RUNNING, 824);

		// Stands and walks for bodies that are holding something.
		pin(EntourageAnimation.STAFF_STAND, AnimationID.HUMAN_STAFFREADY, 813);
		pin(EntourageAnimation.WEAPON_STAND, AnimationID.HUMAN_DH_WEAPON_READY, 2561);
		pin(EntourageAnimation.SWORD_STAND, AnimationID.DH_SWORD_UPDATE_READY, 7053);
		pin(EntourageAnimation.WALKING_STICK_WALK, AnimationID.WALK_WALKINGSTICK, 1146);
		pin(EntourageAnimation.HALBERD_WALK, AnimationID.HUMAN_HALBERDWALK_F, 1205);
		pin(EntourageAnimation.WEAPON_WALK, AnimationID.HUMAN_DH_WEAPON_WALK, 2562);
		pin(EntourageAnimation.SWORD_WALK, AnimationID.DH_SWORD_UPDATE_WALK, 7052);

		// Poses.
		pin(EntourageAnimation.POSE_DANCE, AnimationID.EMOTE_DANCE_LOOP, 10048);
		pin(EntourageAnimation.POSE_CHEER, AnimationID.EMOTE_CHEER_LOOP, 196);
		pin(EntourageAnimation.POSE_WAVE, AnimationID.EMOTE_WAVE_LOOP, 197);
		pin(EntourageAnimation.POSE_SHRUG, AnimationID.EMOTE_SHRUG_LOOP, 12056);
		pin(EntourageAnimation.POSE_FLEX, AnimationID.EMOTE_FLEX_LOOP, 12064);
		pin(EntourageAnimation.POSE_SIT, AnimationID.EMOTE_SIT_LOOP, 10061);
		pin(EntourageAnimation.POSE_CLAP, AnimationID.EMOTE_CLAP_LOOP, 3193);
		pin(EntourageAnimation.POSE_PANIC, AnimationID.EMOTE_PANIC_LOOP, 12050);
		pin(EntourageAnimation.POSE_BOW, AnimationID.EMOTE_BOW_LOOP, 192);
		pin(EntourageAnimation.POSE_LEAN, AnimationID.HUMAN_LEAN_READY, 916);
		pin(EntourageAnimation.POSE_CROSSED_ARMS, AnimationID.RD_KNIGHT_CROSSED_ARMS, 2256);
		pin(EntourageAnimation.POSE_SMUG, AnimationID.HUMAN_SMUG_IDLE, 14000);
		pin(EntourageAnimation.POSE_NERVOUS, AnimationID.NERVOUS_IDLE, 10680);

		assertEquals("an animation with no line above is one nobody has checked the id of",
			EntourageAnimation.values().length, pinned.size());
	}

	private void pin(EntourageAnimation animation, int constant, int literal)
	{
		assertEquals(animation + " names a different gameval constant than documented",
			constant, animation.getId());
		assertEquals(animation + ": that constant has been renumbered under the enum",
			literal, animation.getId());
		assertTrue(animation + " is pinned twice", pinned.add(animation));
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
	 * The same claim for the halberd walk, which the cache also ships in four
	 * directions. A follower that walked forwards with the halberd's <i>backwards</i>
	 * animation is a figure moon-walking with a polearm, which is a worse version of the
	 * exact failure the plain walk's neighbours test exists to prevent.
	 */
	@Test
	public void theHalberdWalkIsTheForwardOneToo()
	{
		int walk = EntourageAnimation.HALBERD_WALK.getId();
		assertEquals(AnimationID.HUMAN_HALBERDWALK_F, walk);
		assertNotEquals("backwards", AnimationID.HUMAN_HALBERDWALK_B, walk);
		assertNotEquals("left", AnimationID.HUMAN_HALBERDWALK_L, walk);
		assertNotEquals("right", AnimationID.HUMAN_HALBERDWALK_R, walk);
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
