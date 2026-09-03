package com.matthewmariner.entourage;

import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.NpcID;

/**
 * A figure the entourage can be made of: whose body it wears, and the two animations
 * that body plays.
 *
 * <p><b>Everything here comes out of the game's own cache.</b> No model is authored,
 * no likeness is imitated, and no name outside {@code net.runelite.api.gameval} is
 * used. "Anime style" in this plugin's brief means a deliberate stance and a
 * coordinated formation — a vibe — and never a character: shipping a recognisable
 * copyrighted or trademarked design would be a licensing problem and a Plugin Hub
 * rejection, in that order.
 *
 * <p><b>An NPC id rather than raw model ids.</b> Raw model ids are unnamed numbers a
 * game update can renumber underneath a plugin, which is what killed the predecessor
 * of {@code ../lively-cities}; an NPC id is a generated constant in
 * {@code net.runelite.api.gameval.NpcID}, reviewable by anyone with the jar, and one
 * indirection further from the geometry an artist reworks.
 *
 * <p><b>What a live client still has to confirm.</b> That an NPC's composition
 * resolves, and that its models sit on the framemap the animations below are rigged
 * to, cannot be checked without the cache. The argument for {@link #ROGUE} is
 * second-hand but not thin: {@code ../lively-cities} already dresses a figure from
 * {@code NpcID.ROGUE} and animates it with {@code NervousIdle}
 * ({@code AnimationID.NERVOUS_IDLE}, a framemap-0 human pose), and it is live on the
 * Plugin Hub doing so — so 526 is a human-rigged composition, and
 * {@link EntourageAnimation#HUMAN_STAND} and {@link EntourageAnimation#HUMAN_WALK}
 * are on that same framemap. {@link #FARMER} is the fallback if that turns out to be
 * wrong in the client: {@code ../lively-cities} ships {@code NpcID.FARMER1} with
 * {@code HumanIdle}/{@code HumanWalk} — the exact pair below — on a figure that
 * wanders, so that combination is field-proven rather than argued. Swapping is one
 * entry in {@link #DEFAULT_ROSTER}.
 */
enum EntourageFigure
{
	/**
	 * A rogue: a human in dark clothes, carrying nothing that an animation would have
	 * to account for.
	 *
	 * <p>Chosen over the alternatives on look — an entourage should read as a crew
	 * rather than as passers-by — and because an empty-handed body is the one case
	 * where a plain stand and a plain walk cannot look like a mime.
	 */
	ROGUE(NpcID.ROGUE, EntourageAnimation.HUMAN_STAND, EntourageAnimation.HUMAN_WALK),

	/**
	 * A farmer, and the reason it is in this enum is not that anybody wants an
	 * entourage of farmers.
	 *
	 * <p>It is the one NPC id that is <i>proven</i> to work with the pair below, by a
	 * plugin that ships it. If {@link #ROGUE} turns out badly in a live client — a
	 * composition that will not resolve, a body on the wrong rig — this is the known
	 * good answer to fall back to while a better-looking one is found, and
	 * {@code EntourageFigureTest} keeps it honest rather than letting it rot.
	 */
	FARMER(NpcID.FARMER1, EntourageAnimation.HUMAN_STAND, EntourageAnimation.HUMAN_WALK);

	/**
	 * Who actually spawns.
	 *
	 * <p><b>One.</b> The point of this slice is a single follower that walks
	 * correctly, with a walk animation, without clipping through walls, anchored where
	 * it looks like it should be. Four figures doing that wrong is not four times the
	 * feature. Formation shapes and pose variety extend this list; nothing else has to
	 * change to make them appear, which is the property this seam exists to have.
	 */
	static final List<EntourageFigure> DEFAULT_ROSTER = Collections.singletonList(ROGUE);

	private final int npcId;
	private final EntourageAnimation idleAnimation;
	private final EntourageAnimation walkAnimation;

	EntourageFigure(int npcId, EntourageAnimation idleAnimation, EntourageAnimation walkAnimation)
	{
		this.npcId = npcId;
		this.idleAnimation = idleAnimation;
		this.walkAnimation = walkAnimation;
	}

	/** @return the NPC whose models and recolours this figure wears */
	int getNpcId()
	{
		return npcId;
	}

	/**
	 * @return the pose it holds while standing. Per-figure rather than a constant on
	 * purpose: pose variety is the next slice, and a figure on a different rig would
	 * need a different pair — see {@link EntourageAnimation} on why a stand and a walk
	 * from two framemaps do not join up.
	 */
	EntourageAnimation getIdleAnimation()
	{
		return idleAnimation;
	}

	/** @return the animation it plays while walking */
	EntourageAnimation getWalkAnimation()
	{
		return walkAnimation;
	}

	/**
	 * @return what this figure is called in a log line. Deliberately not a
	 * player-facing name: nothing in this plugin puts text in the world, and the day
	 * something does, it has to say what the figure is rather than give it an
	 * identity that could be mistaken for a real one.
	 */
	String label()
	{
		return name().toLowerCase() + " (npc " + npcId + ")";
	}
}
