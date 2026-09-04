package com.matthewmariner.entourage;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The idle-pose dropdown: which animation each entry holds, and the one rule that makes
 * an entry safe to be in it.
 *
 * <p><b>The rule is "it has to loop", and it cannot be checked by looking at the id.</b>
 * A one-shot emote and a looping one are both positive integers; the difference is in
 * what the cache authored, and the only trace of it available at compile time is the
 * constant's name. Reflection is banned in this repo, so the enforceable version of the
 * rule is the pinning test below: every pose has to be named here against a {@code _LOOP}
 * emote or a {@code _READY}/{@code _IDLE} held pose, and a new entry with no line here
 * fails. That turns "is this loopable?" into a question a reviewer is made to answer
 * rather than one that can be skipped.
 */
public class EntouragePoseTest
{
	private final EnumSet<EntouragePose> pinned = EnumSet.noneOf(EntouragePose.class);

	@Test
	public void everyPoseHoldsTheLoopingAnimationItIsDocumentedAsHolding()
	{
		// The nine _LOOP emotes. The cache's own suffix is what says these repeat.
		pin(EntouragePose.DANCE, EntourageAnimation.POSE_DANCE, AnimationID.EMOTE_DANCE_LOOP, 10048);
		pin(EntouragePose.CHEER, EntourageAnimation.POSE_CHEER, AnimationID.EMOTE_CHEER_LOOP, 196);
		pin(EntouragePose.WAVE, EntourageAnimation.POSE_WAVE, AnimationID.EMOTE_WAVE_LOOP, 197);
		pin(EntouragePose.CLAP, EntourageAnimation.POSE_CLAP, AnimationID.EMOTE_CLAP_LOOP, 3193);
		pin(EntouragePose.BOW, EntourageAnimation.POSE_BOW, AnimationID.EMOTE_BOW_LOOP, 192);
		pin(EntouragePose.SHRUG, EntourageAnimation.POSE_SHRUG, AnimationID.EMOTE_SHRUG_LOOP, 12056);
		pin(EntouragePose.FLEX, EntourageAnimation.POSE_FLEX, AnimationID.EMOTE_FLEX_LOOP, 12064);
		pin(EntouragePose.PANIC, EntourageAnimation.POSE_PANIC, AnimationID.EMOTE_PANIC_LOOP, 12050);
		pin(EntouragePose.SIT, EntourageAnimation.POSE_SIT, AnimationID.EMOTE_SIT_LOOP, 10061);

		// The four held poses, which are not emotes at all — a _READY or an _IDLE is a
		// pose the cache expects to be held rather than played through.
		pin(EntouragePose.LEAN, EntourageAnimation.POSE_LEAN, AnimationID.HUMAN_LEAN_READY, 916);
		pin(EntouragePose.CROSSED_ARMS, EntourageAnimation.POSE_CROSSED_ARMS,
			AnimationID.RD_KNIGHT_CROSSED_ARMS, 2256);
		pin(EntouragePose.SMUG, EntourageAnimation.POSE_SMUG, AnimationID.HUMAN_SMUG_IDLE, 14000);
		pin(EntouragePose.NERVOUS, EntourageAnimation.POSE_NERVOUS, AnimationID.NERVOUS_IDLE, 10680);

		assertEquals("a pose with no line above is one nobody has checked loops",
			EntouragePose.values().length - 1, pinned.size());
	}

	private void pin(EntouragePose pose, EntourageAnimation animation, int constant, int literal)
	{
		assertSame(pose + " does not hold the animation it is documented as holding",
			animation, pose.getAnimation());
		assertEquals(pose + " names a different gameval constant than documented",
			constant, animation.getId());
		assertEquals(pose + ": that constant has been renumbered",
			literal, animation.getId());
		assertTrue(pose + " is pinned twice", pinned.add(pose));
	}

	/**
	 * <b>The structural half of the loop rule.</b> A pose that reached for a walk or a
	 * run would be a figure playing a locomotion cycle while standing perfectly still —
	 * running on the spot — and one that reached for a figure's stand would silently
	 * duplicate {@link EntouragePose#FIGURE_DEFAULT} under a different name. Keeping the
	 * two sets of animations disjoint is the version of that a test can check.
	 */
	@Test
	public void noPoseBorrowsAnAnimationAFigureMovesWith()
	{
		Set<EntourageAnimation> figureAnimations = new HashSet<>();
		for (EntourageFigure figure : EntourageFigure.values())
		{
			figureAnimations.add(figure.getIdleAnimation());
			figureAnimations.add(figure.getWalkAnimation());
			figureAnimations.add(figure.getRunAnimation());
		}

		for (EntouragePose pose : EntouragePose.values())
		{
			EntourageAnimation animation = pose.getAnimation();
			if (animation == null)
			{
				continue;
			}
			assertFalse(pose + " holds " + animation + ", which is a figure's own animation",
				figureAnimations.contains(animation));
		}
	}

	@Test
	public void figureDefaultIsTheOnlyEntryWithNoAnimationOfItsOwn()
	{
		assertNull(EntouragePose.FIGURE_DEFAULT.getAnimation());

		for (EntouragePose pose : EntouragePose.values())
		{
			if (pose != EntouragePose.FIGURE_DEFAULT)
			{
				assertNotNull(pose + " has nothing to hold", pose.getAnimation());
			}
		}
	}

	/**
	 * <b>The behaviour the setting actually has.</b> {@code FIGURE_DEFAULT} has to
	 * follow the figure, and every other pose has to override it — checked against two
	 * figures whose own stands differ, because a resolver that always returned the
	 * figure's stand and one that always returned the pose's are indistinguishable on a
	 * single figure whose stand happens to match.
	 */
	@Test
	public void figureDefaultFollowsTheFigureAndEveryOtherPoseOverridesIt()
	{
		assertNotEquals("this test needs two figures that stand differently",
			EntourageFigure.ROGUE.getIdleAnimation(), EntourageFigure.NIEVE.getIdleAnimation());

		assertSame(EntourageFigure.ROGUE.getIdleAnimation(),
			EntouragePose.FIGURE_DEFAULT.animationFor(EntourageFigure.ROGUE));
		assertSame(EntourageFigure.NIEVE.getIdleAnimation(),
			EntouragePose.FIGURE_DEFAULT.animationFor(EntourageFigure.NIEVE));

		for (EntouragePose pose : EntouragePose.values())
		{
			if (pose == EntouragePose.FIGURE_DEFAULT)
			{
				continue;
			}

			assertSame(pose + " on a rogue", pose.getAnimation(),
				pose.animationFor(EntourageFigure.ROGUE));
			assertSame(pose + " on Nieve", pose.getAnimation(),
				pose.animationFor(EntourageFigure.NIEVE));
		}
	}

	/**
	 * Never {@code null}, whatever is asked. {@link Follower} tells "the cache has not
	 * warmed up" from "there is no pose" by the controller being null, and a pose that
	 * resolved to nothing would collapse that distinction — one is retried and the other
	 * is permanent.
	 */
	@Test
	public void everyPoseResolvesToSomethingForEveryFigure()
	{
		for (EntouragePose pose : EntouragePose.values())
		{
			for (EntourageFigure figure : EntourageFigure.values())
			{
				assertNotNull(pose + " on " + figure.name(), pose.animationFor(figure));
			}
		}
	}

	@Test
	public void everyPoseHasItsOwnPlayerFacingName()
	{
		Set<String> names = new HashSet<>();
		for (EntouragePose pose : EntouragePose.values())
		{
			String name = pose.toString();
			assertFalse(pose.name() + " has no display name", name.isEmpty());
			assertNotEquals(pose.name() + " is showing its enum constant to the user",
				pose.name(), name);
			assertTrue(pose.name() + " shares a display name with another pose", names.add(name));
		}
	}

	@Test
	public void theDefaultPoseIsTheFiguresOwn()
	{
		assertEquals(EntouragePose.FIGURE_DEFAULT, new FakeConfig().idlePose());
		assertEquals("and it is first in the dropdown",
			EntouragePose.FIGURE_DEFAULT, EntouragePose.values()[0]);
	}
}
