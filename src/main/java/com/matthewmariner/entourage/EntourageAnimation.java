package com.matthewmariner.entourage;

import net.runelite.api.gameval.AnimationID;

/**
 * The animation ids this plugin plays, and the argument for each one.
 *
 * <p><b>An {@code NPCComposition} cannot supply these, and that is the gap this
 * class exists to close.</b> Dressing a figure from an NPC — which is what
 * {@link FollowerAppearance} does — gives you {@code getModels()} and the recolour
 * pairs, and nothing else. There is no {@code getStandAnimation()} on it.
 * {@code javap net.runelite.api.NPCComposition} against 1.12.38 lists, in full:
 * {@code getName}, {@code getModels}, {@code getChatheadModels}, {@code getOps},
 * {@code getActions}, {@code isInteractible}, {@code isMinimapVisible}, {@code getId},
 * {@code getCombatLevel}, {@code getConfigs}, {@code transform}, {@code getSize},
 * {@code isFollower}, {@code getColorToReplace}, {@code getColorToReplaceWith},
 * {@code getWidthScale}, {@code getHeightScale}, {@code getFootprintSize} and
 * {@code getStats} — plus whatever {@code ParamHolder} contributes, which is
 * parameters rather than anything sequence-shaped. Not one of them is a sequence id.
 * (An earlier version of this list stopped at {@code getSize} and so omitted the two
 * recolour accessors {@link FollowerAppearance} itself calls, which was an odd thing
 * for a list attributed to {@code javap} to be missing.) A figure built that way and
 * left alone is a static mesh. Standing still, that reads as a statue; <b>walking, it
 * is a body sliding across the ground</b>, which is the single most visible way a
 * follower plugin can look broken.
 *
 * <p><b>That absent accessor is also why {@link EntourageFigure} carries a triple per
 * preset rather than one shared pair.</b> The cache does record what each NPC stands
 * and walks with; the API just refuses to hand it over, so the numbers have to be read
 * out of the cache once by a human and written down here. Every id below was read that
 * way and resolved to the {@code gameval} constant that names it.
 *
 * <p><b>{@link NpcRecord} now reads the same numbers at runtime, and the presets still
 * do not use it.</b> That decoder exists because a typed NPC id has nobody to write its
 * pair down for it — see {@link FollowerBody} — and reading the archive is a cache read
 * with its own failure modes: it can come back empty on a cold login, it costs a retry
 * budget, and it can be refused. A preset needs none of that. The ids below are already
 * known, already named by a {@code gameval} constant a reviewer can check, and already
 * pinned by {@code EntourageAnimationTest}; routing them through a decoder would swap a
 * fact for a lookup that can fail.
 *
 * <p><b>Where the ids come from.</b> {@code ../lively-cities} carries a 128-entry
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
 * with its hands working at nothing. The stands below are all {@code _READY} poses,
 * which is the cache's own word for the held pose rather than the transition into it.
 *
 * <p><b>The {@code POSE_} block is the other half, and it has a rule of its own: every
 * one of them loops.</b> A one-shot emote plays once and then freezes on its last
 * frame, so a figure posed with the plain dance emote is a figure stuck mid-step for
 * the rest of the session. The cache spells the difference out — the {@code _LOOP}
 * variants are the ones authored to repeat — and the four that are not named
 * {@code _LOOP} ({@link #POSE_LEAN}, {@link #POSE_CROSSED_ARMS}, {@link #POSE_SMUG},
 * {@link #POSE_NERVOUS}) are held idles rather than emotes, which is the same property
 * under a different naming convention. {@code EntouragePoseTest} is where that rule is
 * enforced rather than merely described.
 */
enum EntourageAnimation
{
	// --- The human rig's own three ------------------------------------------

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
	HUMAN_WALK(AnimationID.HUMAN_WALK_F),

	/**
	 * The matching run, and the third leg of the rig's own set.
	 *
	 * <p>{@code AnimationID.HUMAN_RUNNING}. 143 NPCs in the cache carry the exact
	 * triple {@code HUMAN_READY} / {@code HUMAN_WALK_F} / this, which makes it the
	 * most-used run animation in the game and the one every Tier A figure in
	 * {@link EntourageFigure} is <i>declared</i> to have rather than guessed into.
	 *
	 * <p>A follower needs one at all because a running player covers two tiles a game
	 * tick. Covering two tiles with a walk cycle is the sliding-mesh failure at half
	 * speed — the legs move at a walk while the ground moves at a run — so the run is
	 * not decoration on top of the two-tile step, it is the half of it that makes the
	 * step look like anything.
	 */
	HUMAN_RUN(AnimationID.HUMAN_RUNNING),

	// --- Stands for bodies that are holding something -----------------------

	/**
	 * A human at rest with a staff or a stick in hand.
	 *
	 * <p>{@code AnimationID.HUMAN_STAFFREADY}. Declared by the Wise Old Man, Nieve,
	 * Steve and Turael, none of which stands the way an empty-handed human does — put
	 * {@link #HUMAN_STAND} on any of them and the staff hangs in mid-air beside a body
	 * that is not holding it.
	 */
	STAFF_STAND(AnimationID.HUMAN_STAFFREADY),

	/**
	 * A human at rest with a two-handed weapon shouldered.
	 *
	 * <p>{@code AnimationID.HUMAN_DH_WEAPON_READY}. Vannaka's and the White Knight's
	 * own stand.
	 */
	WEAPON_STAND(AnimationID.HUMAN_DH_WEAPON_READY),

	/**
	 * The stand that goes with {@link #SWORD_WALK}.
	 *
	 * <p>{@code AnimationID.DH_SWORD_UPDATE_READY}. Note the pair is numbered the
	 * "wrong" way round in the cache — the ready pose is 7053 and its walk is 7052 —
	 * which is exactly the sort of thing a reader corrects by eye and breaks. The
	 * constants are named, so it cannot be corrected by eye here.
	 */
	SWORD_STAND(AnimationID.DH_SWORD_UPDATE_READY),

	// --- Walks for bodies that are holding something ------------------------

	/**
	 * Walking with a stick.
	 *
	 * <p>{@code AnimationID.WALK_WALKINGSTICK}, which is what the Wise Old Man
	 * declares. It is the walk that makes him read as an old man rather than as
	 * somebody carrying a staff for no reason.
	 */
	WALKING_STICK_WALK(AnimationID.WALK_WALKINGSTICK),

	/**
	 * Walking with a polearm held across the body.
	 *
	 * <p>{@code AnimationID.HUMAN_HALBERDWALK_F} — the forward one again, for the same
	 * reason {@link #HUMAN_WALK} is. Nieve's, Steve's and Turael's declared walk.
	 */
	HALBERD_WALK(AnimationID.HUMAN_HALBERDWALK_F),

	/** {@code AnimationID.HUMAN_DH_WEAPON_WALK}, the walk that goes with {@link #WEAPON_STAND}. */
	WEAPON_WALK(AnimationID.HUMAN_DH_WEAPON_WALK),

	/** {@code AnimationID.DH_SWORD_UPDATE_WALK}, the walk that goes with {@link #SWORD_STAND}. */
	SWORD_WALK(AnimationID.DH_SWORD_UPDATE_WALK),

	// --- Poses a user can choose instead of a figure's own stand -------------
	//
	// Every one of these loops. See the class javadoc, and EntouragePoseTest, which is
	// what stops a one-shot emote getting in here and freezing a figure on its last
	// frame for the rest of the session.

	/** {@code AnimationID.EMOTE_DANCE_LOOP}. */
	POSE_DANCE(AnimationID.EMOTE_DANCE_LOOP),

	/** {@code AnimationID.EMOTE_CHEER_LOOP}. */
	POSE_CHEER(AnimationID.EMOTE_CHEER_LOOP),

	/** {@code AnimationID.EMOTE_WAVE_LOOP}. */
	POSE_WAVE(AnimationID.EMOTE_WAVE_LOOP),

	/** {@code AnimationID.EMOTE_SHRUG_LOOP}. */
	POSE_SHRUG(AnimationID.EMOTE_SHRUG_LOOP),

	/** {@code AnimationID.EMOTE_FLEX_LOOP}. */
	POSE_FLEX(AnimationID.EMOTE_FLEX_LOOP),

	/** {@code AnimationID.EMOTE_SIT_LOOP}. */
	POSE_SIT(AnimationID.EMOTE_SIT_LOOP),

	/** {@code AnimationID.EMOTE_CLAP_LOOP}. */
	POSE_CLAP(AnimationID.EMOTE_CLAP_LOOP),

	/** {@code AnimationID.EMOTE_PANIC_LOOP}. */
	POSE_PANIC(AnimationID.EMOTE_PANIC_LOOP),

	/** {@code AnimationID.EMOTE_BOW_LOOP}. */
	POSE_BOW(AnimationID.EMOTE_BOW_LOOP),

	/**
	 * {@code AnimationID.HUMAN_LEAN_READY}. Not an emote at all — a {@code _READY}
	 * pose, held indefinitely by whatever plays it, which is the same property the
	 * {@code _LOOP} emotes have.
	 */
	POSE_LEAN(AnimationID.HUMAN_LEAN_READY),

	/** {@code AnimationID.RD_KNIGHT_CROSSED_ARMS}, a held stance rather than an emote. */
	POSE_CROSSED_ARMS(AnimationID.RD_KNIGHT_CROSSED_ARMS),

	/** {@code AnimationID.HUMAN_SMUG_IDLE}, an idle rather than an emote. */
	POSE_SMUG(AnimationID.HUMAN_SMUG_IDLE),

	/**
	 * {@code AnimationID.NERVOUS_IDLE}. The one pose in this block with a track record:
	 * {@code ../lively-cities} ships it on {@code Sludgellama}, a figure built from
	 * {@code NpcID.ROGUE}, live on the Plugin Hub.
	 */
	POSE_NERVOUS(AnimationID.NERVOUS_IDLE);

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
