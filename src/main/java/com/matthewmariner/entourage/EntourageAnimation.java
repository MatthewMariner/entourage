package com.matthewmariner.entourage;

import net.runelite.api.gameval.AnimationID;

/**
 * The animation ids this plugin plays, and the argument for each one.
 *
 * <p><b>An {@code NPCComposition} cannot supply these, and that is the gap this
 * class exists to close.</b> Dressing a figure from an NPC — which is what
 * {@link FollowerAppearance} does — gives you {@code getModels()} and the recolour
 * pairs, and nothing else. There is no {@code getStandAnimation()} on it: the
 * interface in 1.12.38 exposes name, models, chathead models, ops, actions,
 * interactible, minimap-visible, id, combat level, configs, {@code transform()} and
 * size, and none of those is a sequence id. A figure built that way and left alone
 * is a static mesh. Standing still, that reads as a statue; <b>walking, it is a body
 * sliding across the ground</b>, which is the single most visible way a follower
 * plugin can look broken.
 *
 * <p><b>Where the ids come from.</b> {@code ../lively-cities} carries a 150-entry
 * name-to-id table because the region dataset it loads stores animations by name, so
 * the mapping has to exist somewhere; the ids in it were read out of
 * {@code net.runelite.api.gameval.AnimationID} with {@code javap} and pinned by a
 * test. This plugin has no vendored dataset and no name to resolve, so the honest
 * version of that same path is to name the constant directly — which is also what
 * this repo's {@code AGENTS.md} requires ("use {@code net.runelite.api.gameval}
 * package constants; never hardcode magic numbers when gameval constants can be used
 * instead"). The numbers below are therefore not in this file at all;
 * {@code EntourageAnimationTest} pins what they resolve to, so a client-side
 * renumbering is a red test rather than a figure doing the wrong thing.
 *
 * <p><b>The rule for choosing one: a figure that stands there gets a pose, never an
 * action.</b> An action animation assumes the item it was authored around — a
 * fishing animation assumes a rod, an alchemy animation assumes a staff — and a
 * figure with no such model in its composition plays it as a mime, bent at the waist
 * with its hands working at nothing. The two below are the human rig's own stand and
 * forward walk, which is the pair the game itself installs on an ordinary person.
 */
enum EntourageAnimation
{
	/**
	 * The standing pose of an ordinary human.
	 *
	 * <p>{@code AnimationID.HUMAN_READY}. The {@code _READY} suffix is the cache's own
	 * word for the held pose rather than the transition into it, which is what makes
	 * it safe to loop, and this is the id {@code ../lively-cities} ships as
	 * {@code HumanIdle} on human-modelled figures that are live on the Plugin Hub.
	 */
	HUMAN_STAND(AnimationID.HUMAN_READY),

	/**
	 * The matching forward walk.
	 *
	 * <p>{@code AnimationID.HUMAN_WALK_F} — the forward one of the four the cache
	 * names ({@code _F}, {@code _B}, {@code _L}, {@code _R}, ids 819 to 822), and the
	 * only one that is right here: this plugin turns a follower to face the direction
	 * it is travelling before it moves it, so it is always walking forwards.
	 * Backwards and sideways walks exist for actors that keep a facing while moving,
	 * which no figure here does.
	 *
	 * <p>Same rig as {@link #HUMAN_STAND}, which is what makes the pair coherent — a
	 * stand and a walk from different framemaps on the same body is how a figure ends
	 * up snapping between two poses that do not join up.
	 */
	HUMAN_WALK(AnimationID.HUMAN_WALK_F);

	private final int id;

	EntourageAnimation(int id)
	{
		this.id = id;
	}

	int getId()
	{
		return id;
	}
}
