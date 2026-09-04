package com.matthewmariner.entourage;

import javax.annotation.Nullable;

/**
 * What the follower does while it is standing still.
 *
 * <p><b>Only poses that loop are in here, and that is the whole rule.</b> An
 * {@code AnimationController} built by {@link Follower} is told to loop on finishing,
 * but looping a one-shot emote is not what the emote was authored for: the cache's
 * plain emotes are a run-up, the gesture, and a return to standing, so repeating one
 * is a figure that jerks back to attention twice a second. Worse, several of them
 * simply end — the last frame is held and nothing else happens — which is a figure
 * frozen mid-gesture for the rest of the session, indistinguishable from a crash.
 *
 * <p>The cache marks the difference itself. The nine emotes below are the
 * {@code _LOOP} variants, which are the ones authored to repeat; the four after them
 * are {@code _READY} poses and idles, which are held rather than played and have the
 * same property under a different naming convention. {@code EntouragePoseTest} enforces
 * that rather than trusting this paragraph — every entry has to be one or the other.
 *
 * <p><b>{@link #FIGURE_DEFAULT} is not a pose and carries no animation.</b> It means
 * "whatever this figure's own body stands with", which differs per figure — the Wise
 * Old Man leans on a stick and Vannaka shoulders a two-hander — and is the only choice
 * that stays right when the figure is changed underneath it. Anything else here
 * overrides the figure's stand with the same pose whoever is wearing it.
 *
 * <p><b>Emote-while-walking is deliberately absent, and it is not an oversight.</b> The
 * client really does have two animation slots — {@code Client.applyTransformations}
 * blends an animation controller and a pose controller — but
 * {@code RuneLiteObjectController.tick(int)} advances only the first of them. A walk
 * parked in the pose slot would therefore never advance a frame: the figure would slide
 * along the ground frozen in its first walk frame, which is the exact failure this
 * plugin's animation work exists to prevent. Making that work is a new invariant with
 * its own design and its own review, not a fifteenth entry in this enum.
 */
public enum EntouragePose
{
	/**
	 * The figure's own stand — the default, and the only entry that changes meaning
	 * when the figure does.
	 */
	FIGURE_DEFAULT("The figure's own", null),

	/** {@code EMOTE_DANCE_LOOP}. */
	DANCE("Dance", EntourageAnimation.POSE_DANCE),

	/** {@code EMOTE_CHEER_LOOP}. */
	CHEER("Cheer", EntourageAnimation.POSE_CHEER),

	/** {@code EMOTE_WAVE_LOOP}. */
	WAVE("Wave", EntourageAnimation.POSE_WAVE),

	/** {@code EMOTE_CLAP_LOOP}. */
	CLAP("Clap", EntourageAnimation.POSE_CLAP),

	/** {@code EMOTE_BOW_LOOP}. */
	BOW("Bow", EntourageAnimation.POSE_BOW),

	/** {@code EMOTE_SHRUG_LOOP}. */
	SHRUG("Shrug", EntourageAnimation.POSE_SHRUG),

	/** {@code EMOTE_FLEX_LOOP}. */
	FLEX("Flex", EntourageAnimation.POSE_FLEX),

	/** {@code EMOTE_PANIC_LOOP}. */
	PANIC("Panic", EntourageAnimation.POSE_PANIC),

	/** {@code EMOTE_SIT_LOOP}. */
	SIT("Sit down", EntourageAnimation.POSE_SIT),

	/** {@code HUMAN_LEAN_READY} — a held stance, not an emote. */
	LEAN("Lean", EntourageAnimation.POSE_LEAN),

	/** {@code RD_KNIGHT_CROSSED_ARMS} — a held stance, not an emote. */
	CROSSED_ARMS("Arms crossed", EntourageAnimation.POSE_CROSSED_ARMS),

	/** {@code HUMAN_SMUG_IDLE} — an idle, not an emote. */
	SMUG("Smug", EntourageAnimation.POSE_SMUG),

	/** {@code NERVOUS_IDLE} — an idle, not an emote. */
	NERVOUS("Nervous", EntourageAnimation.POSE_NERVOUS);

	private final String displayName;

	/** {@code null} for {@link #FIGURE_DEFAULT}, and for nothing else. */
	@Nullable
	private final EntourageAnimation animation;

	EntouragePose(String displayName, @Nullable EntourageAnimation animation)
	{
		this.displayName = displayName;
		this.animation = animation;
	}

	/**
	 * @param figure the figure holding the pose, consulted only by
	 *               {@link #FIGURE_DEFAULT}
	 * @return the animation to hold while standing still. Never {@code null}: a pose
	 * that resolved to nothing would leave {@link Follower} unable to tell "the user
	 * picked no pose" from "the cache has not warmed up", and those two have to be
	 * handled differently — one is permanent and one is retried.
	 */
	EntourageAnimation animationFor(EntourageFigure figure)
	{
		return animation == null ? figure.getIdleAnimation() : animation;
	}

	/**
	 * @return the animation this pose overrides the figure's stand with, or
	 * {@code null} for {@link #FIGURE_DEFAULT}. For the tests: "every pose is loopable"
	 * is a claim about the thirteen that carry one, and the fourteenth has nothing to
	 * check.
	 */
	@Nullable
	EntourageAnimation getAnimation()
	{
		return animation;
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}
}
