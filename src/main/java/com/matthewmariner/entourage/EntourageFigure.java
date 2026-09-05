package com.matthewmariner.entourage;

import net.runelite.api.gameval.NpcID;

/**
 * A figure the entourage can be made of: whose body it wears, and the three animations
 * that body plays.
 *
 * <p><b>Everything here comes out of the game's own cache.</b> No model is authored,
 * no likeness is imitated, and no name outside {@code net.runelite.api.gameval} is
 * used. "Anime style" in this plugin's brief means a deliberate stance and a
 * coordinated formation — a vibe — and never a character: shipping a recognisable
 * copyrighted or trademarked design would be a licensing problem and a Plugin Hub
 * rejection, in that order. The labels below name Jagex's own NPCs, which is the
 * opposite of the problem that rule is about.
 *
 * <p><b>An NPC id rather than raw model ids.</b> Raw model ids are unnamed numbers a
 * game update can renumber underneath a plugin, which is what killed the predecessor
 * of {@code ../lively-cities}; an NPC id is a generated constant in
 * {@code net.runelite.api.gameval.NpcID}, reviewable by anyone with the jar, and one
 * indirection further from the geometry an artist reworks.
 *
 * <p><b>Every preset carries its own animations, and it has to.</b> {@code
 * NPCComposition} exposes no sequence accessor at all (see {@link EntourageAnimation}),
 * so "what does this NPC stand and walk with?" cannot be asked at runtime — it has to
 * be read out of the cache once and written down. The <b>Tier A</b> block below is
 * every preset that declares the human rig's own triple, {@code HUMAN_READY} /
 * {@code HUMAN_WALK_F} / {@code HUMAN_RUNNING}; the <b>Tier B</b> block is the
 * presets that declare something else, each with the pair the cache says it uses.
 *
 * <p><b>The one thing in this enum that was not read off the NPC that plays it is the
 * run on Tier B.</b> Nothing in the cache gives Nieve a run animation, because Nieve
 * never runs. {@link EntourageAnimation#HUMAN_RUN} is used for every figure here, and
 * the argument for it on Tier B is a rig argument rather than an observation: every
 * Tier B walk below is a human-framemap animation — a human holding a polearm, a
 * walking stick, a two-hander — so the human rig's own run sits on the same skeleton
 * and will not deform the model. What is genuinely unverified is whether it
 * <i>looks</i> right: a figure holding a halberd running with its arms swinging free
 * is coherent geometry and odd staging. It is still much better than the alternative,
 * which is playing a walk cycle while covering two tiles, and that one is not a risk
 * but a certainty. See the README's "Wanted from a real client".
 *
 * <p><b>What a live client still has to confirm.</b> That an NPC's composition
 * resolves, and that its models sit on the framemap the animations below are rigged
 * to, cannot be checked without the cache.
 *
 * <p><b>{@link #ROGUE} answers this repo's oldest open question.</b> It shipped as the
 * default on the strength of {@code ../lively-cities} using NPC 526 once, on a
 * {@code StationaryCitizen} with <b>no {@code moveAnimation} at all</b> — which
 * established that 526 resolves and that a framemap-0 human pose sits on it correctly,
 * and said nothing about a <em>walk</em>, because nothing over there has ever walked
 * one. The cache now answers it directly: <b>526 declares 808/819</b>, so it walks on
 * the human rig by the game's own reckoning rather than by inference from a pose.
 *
 * <p>{@link #FARMER} stays anyway. It is the id {@code ../lively-cities} ships as
 * {@code Rufus}, a {@code WanderingCitizen} with {@code HumanIdle}/{@code HumanWalk} on
 * a figure that actually moves, which is a different kind of evidence — a plugin on the
 * Plugin Hub doing this exact thing — and worth keeping as the known-good fallback.
 */
public enum EntourageFigure
{
	// --- Tier A: the human triple, 808 / 819 / 824 ---------------------------

	/**
	 * A rogue: a human in dark clothes, carrying nothing that an animation would have
	 * to account for.
	 *
	 * <p>The default, and chosen over the alternatives on look — an entourage should
	 * read as a crew rather than as passers-by — and because an empty-handed body is
	 * the one case where a plain stand and a plain walk cannot look like a mime.
	 */
	ROGUE("Rogue", NpcID.ROGUE),

	/** A slayer master with a two-hander. Declares 2561/819 rather than the human pair. */
	VANNAKA("Vannaka", NpcID.SLAYER_MASTER_3,
		EntourageAnimation.WEAPON_STAND, EntourageAnimation.HUMAN_WALK),

	/** A slayer master with a polearm. Declares 813/1205. */
	NIEVE("Nieve", NpcID.SLAYER_MASTER_NIEVE,
		EntourageAnimation.STAFF_STAND, EntourageAnimation.HALBERD_WALK),

	/**
	 * Nieve's replacement after Monkey Madness II, shipped as his own entry rather than
	 * as a quest-state swap on hers.
	 *
	 * <p><b>Deliberately no quest detection.</b> This is a cosmetic plugin; reading
	 * quest state to decide which body to wear would make the roster depend on the
	 * account, make the setting mean different things to different people, and buy
	 * nothing a second dropdown entry does not. Pick whichever one you want to walk
	 * with.
	 *
	 * <p>Note that the other Steve in the cache — {@code NpcID.WYVERN_CAVE_STEVE},
	 * 6799, on the plain human pair — is somebody else entirely and is not this one.
	 */
	STEVE("Steve", NpcID.SLAYER_MASTER_STEVE,
		EntourageAnimation.STAFF_STAND, EntourageAnimation.HALBERD_WALK),

	/**
	 * The lowest slayer master, and the one that looks like an ordinary human but is
	 * not animated like one: he declares 813/1205, not the human pair. Getting that
	 * backwards is a figure holding a staff with its arms at its sides.
	 */
	TURAEL("Turael", NpcID.SLAYER_MASTER_1_TUREAL,
		EntourageAnimation.STAFF_STAND, EntourageAnimation.HALBERD_WALK),

	/** The highest slayer master, on the plain human triple. */
	DURADEL("Duradel", NpcID.SLAYER_MASTER_5_DURADEL),

	/** The second slayer master, on the plain human triple. */
	MAZCHNA("Mazchna", NpcID.SLAYER_MASTER_2_MAZCHNA),

	/** Declares 813/1146 — the staff stand with the walking-stick walk. */
	WISE_OLD_MAN("Wise Old Man", NpcID.WISE_OLD_MAN,
		EntourageAnimation.STAFF_STAND, EntourageAnimation.WALKING_STICK_WALK),

	/** Declares 2561/2562, the two-handed-weapon pair. */
	WHITE_KNIGHT("White Knight", NpcID.WHITE_KNIGHT,
		EntourageAnimation.WEAPON_STAND, EntourageAnimation.WEAPON_WALK),

	/** Declares 7053/7052 — note the ready pose is the higher id of the two. */
	ELITE_BLACK_KNIGHT("Elite Black Knight", NpcID.ELITE_BLACK_KNIGHT_1,
		EntourageAnimation.SWORD_STAND, EntourageAnimation.SWORD_WALK),

	/** Human triple. */
	SIR_AMIK_VARZE("Sir Amik Varze", NpcID.SIR_AMIK_VARZE),

	/** Human triple. */
	SIR_VYVIN("Sir Vyvin", NpcID.SIR_VYVIN),

	/** Human triple. */
	PALADIN("Paladin", NpcID.ARDOUGNE_PALADIN2),

	/**
	 * Human triple.
	 *
	 * <p>The label is descriptive rather than a claim about the NPC's in-game name:
	 * {@code NpcID.GRILLKNIGHT} is a knight and this plugin has no way to ask the cache
	 * what it is called without a live client. See {@link #getDisplayName()}.
	 */
	GRILL_KNIGHT("Knight", NpcID.GRILLKNIGHT),

	/** Human triple. */
	GHOMMAL("Ghommal", NpcID.WARGUILD_GHOMMAL_NPC),

	/** Human triple. */
	HERO("Hero", NpcID.HERO),

	/** Human triple. Descriptive label — {@code RCU_ZAMMY_MAGE1A} is one of the Abyss mages. */
	ZAMORAK_MAGE("Zamorak mage", NpcID.RCU_ZAMMY_MAGE1A),

	/** Human triple. */
	NECROMANCER("Necromancer", NpcID.NECROMANCER),

	/** Human triple. */
	SORCERESS("Sorceress", NpcID.ARABIAN_SORCERESS),

	/** Human triple. */
	THIEF("Thief", NpcID.THIEF2),

	/** Human triple. */
	PIRATE("Pirate", NpcID.PIRATE1),

	/** Human triple. */
	HANS("Hans", NpcID.HANS),

	/**
	 * A farmer, and the reason it is in this enum is not that anybody wants an
	 * entourage of farmers.
	 *
	 * <p>It is the one NPC id that is <i>proven</i> to work with the human pair by a
	 * plugin that ships it and moves it. If a live client turns up a problem with the
	 * rest of this list — a composition that will not resolve, a body on the wrong rig
	 * — this is the known good answer to fall back to, and
	 * {@code EntourageFigureTest} keeps it honest rather than letting it rot.
	 */
	FARMER("Farmer", NpcID.FARMER1);

	/**
	 * Which figure a fresh install walks with.
	 *
	 * <p>Named here rather than written into {@code EntourageConfig.figure()}'s default
	 * so that the roster and the setting cannot drift apart, and so the enum owns the
	 * answer to "who ships?". The roster is config-driven — up to five figures, whichever
	 * ones the dropdowns say — and this is the value of the first of those dropdowns
	 * before anybody touches it.
	 */
	public static final EntourageFigure DEFAULT = ROGUE;

	/**
	 * Whose body each of the five figure slots wears before anybody touches it.
	 *
	 * <p><b>Five different bodies, so that turning the roster up shows five different
	 * people rather than five copies of the same one</b> — a default that made a crowd out
	 * of one figure would read as the setting not having worked. All five are Tier A: they
	 * declare the human rig's own stand, walk and run, so a default group moves as one
	 * group rather than as one figure with a halberd out of step with four without.
	 *
	 * <p>Slot 0 is {@link #DEFAULT} and always will be — it is the {@code figure} key the
	 * plugin has always had, and a profile written before the roster existed has to keep
	 * meaning what it meant.
	 *
	 * @param index which slot, 0-based. Anything outside 0..4 answers {@link #DEFAULT},
	 *              because this is reached from a config fallback path and a throw there
	 *              would be an exception inside a game-tick handler.
	 */
	public static EntourageFigure defaultAt(int index)
	{
		switch (index)
		{
			case 1:
				return THIEF;
			case 2:
				return SORCERESS;
			case 3:
				return HERO;
			case 4:
				return NECROMANCER;
			default:
				return DEFAULT;
		}
	}

	private final String displayName;
	private final int npcId;
	private final EntourageAnimation idleAnimation;
	private final EntourageAnimation walkAnimation;
	private final EntourageAnimation runAnimation;

	/** A Tier A figure: the human rig's own stand, walk and run. */
	EntourageFigure(String displayName, int npcId)
	{
		this(displayName, npcId, EntourageAnimation.HUMAN_STAND, EntourageAnimation.HUMAN_WALK);
	}

	/**
	 * A Tier B figure, which declares its own pair.
	 *
	 * <p>The run is not a parameter because there is nothing to put in it that was read
	 * off the NPC — see the class javadoc.
	 */
	EntourageFigure(String displayName, int npcId,
		EntourageAnimation idleAnimation, EntourageAnimation walkAnimation)
	{
		this.displayName = displayName;
		this.npcId = npcId;
		this.idleAnimation = idleAnimation;
		this.walkAnimation = walkAnimation;
		this.runAnimation = EntourageAnimation.HUMAN_RUN;
	}

	/** @return the NPC whose models and recolours this figure wears */
	int getNpcId()
	{
		return npcId;
	}

	/**
	 * @return the pose it holds while standing, unless the user has picked one of
	 * {@link EntouragePose}'s instead. Per-figure rather than a constant because a
	 * figure on a different rig needs a different pair — see {@link EntourageAnimation}
	 * on why a stand and a walk from two framemaps do not join up.
	 */
	EntourageAnimation getIdleAnimation()
	{
		return idleAnimation;
	}

	/** @return the animation it plays while covering one tile in a game tick */
	EntourageAnimation getWalkAnimation()
	{
		return walkAnimation;
	}

	/** @return the animation it plays while covering two tiles in a game tick */
	EntourageAnimation getRunAnimation()
	{
		return runAnimation;
	}

	/**
	 * @return what this figure is called in the settings dropdown.
	 *
	 * <p>Player-facing, which the log label below is not. It names Jagex's own NPC
	 * where this plugin can be sure of the name, and describes the body where it cannot
	 * — {@code NpcID.GRILLKNIGHT} is a knight, and "Knight" is a true thing to write on
	 * a dropdown, while guessing at its in-game name would not be. Nothing here is ever
	 * drawn in the world: the figure itself has no name over its head, no menu entry and
	 * no examine, which is the guarantee {@code AGENTS.md} asks for.
	 */
	public String getDisplayName()
	{
		return displayName;
	}

	/**
	 * @return what this figure is called in a log line — the enum constant and the NPC
	 * id, so a warning can be traced back to a cache entry. Deliberately different from
	 * {@link #getDisplayName()}: a log line wants the id, and a dropdown wants the name.
	 */
	String label()
	{
		return name().toLowerCase() + " (npc " + npcId + ")";
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}
}
