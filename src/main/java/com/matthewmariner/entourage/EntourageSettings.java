package com.matthewmariner.entourage;

/**
 * One game tick's worth of {@link EntourageConfig}, read once and handed down.
 *
 * <p><b>Why a snapshot rather than passing the config itself.</b> Three reasons, and
 * the third is the one that matters:
 * <ul>
 *   <li>A {@code Config} is a dynamic proxy over {@code ConfigManager}, so every
 *       getter is a map lookup and a string parse. {@link FollowerWalk#tick} would
 *       otherwise make four of them per follower per tick.</li>
 *   <li>Reading each value once per tick means a setting changed mid-tick cannot be
 *       half-applied — the follower cannot pick a slot at one distance and then measure
 *       the recall at another.</li>
 *   <li><b>The numbers have to be clamped somewhere, and this is the only place that
 *       can.</b> {@code @Range} is a hint to the settings <i>panel</i>: it bounds the
 *       spinner, and it is not enforced on the way out of the proxy. A profile edited
 *       by hand — or carried forward from a build where the bounds were different — can
 *       hand this plugin {@code followDistance=0}, which is a follower standing inside
 *       the player, or {@code recallDistance=900}, which is a follower that walks to
 *       the edge of the loaded scene and is never brought back. Both are silent. So
 *       every number is clamped here, against the same constants the annotations
 *       use.</li>
 * </ul>
 *
 * <p>Immutable, and built per tick. Nothing holds one across ticks, because a held one
 * is a setting that stops taking effect.
 */
final class EntourageSettings
{
	/**
	 * The closest a follower may be told to stand: one tile.
	 *
	 * <p>Zero would put it on the player's own tile, which is not a formation but a
	 * figure inside you.
	 */
	static final int MIN_FOLLOW_DISTANCE = 1;

	/**
	 * The furthest: two tiles.
	 *
	 * <p>Not an arbitrary ceiling. The step search in {@link FollowerWalk} is greedy —
	 * it tries the diagonal and then each axis component, and never walks away from its
	 * slot to get around something — so the further out the slot sits, the more of the
	 * time it is on the far side of something the follower cannot reason its way past.
	 * At one and two tiles the slot is inside the player's own clearance nearly always.
	 */
	static final int MAX_FOLLOW_DISTANCE = 2;

	/** One tile behind, which is what a follower normally does. */
	static final int DEFAULT_FOLLOW_DISTANCE = 1;

	/**
	 * The shortest recall distance offered: six tiles.
	 *
	 * <p>Below this a recall stops being a rescue and becomes the normal way the
	 * follower travels — six tiles is already about the width of a room, and a follower
	 * that pops back to you every time you round a corner reads as broken rather than
	 * as attentive.
	 */
	static final int MIN_RECALL_DISTANCE = 6;

	/**
	 * The longest: twenty tiles.
	 *
	 * <p>The bound is the loaded scene, not taste. A recall places the follower on the
	 * player's own tile and it steps off from there, so the follower has to have been
	 * somewhere this plugin could still address — and at twenty tiles it is comfortably
	 * inside the 104-tile scene however close to the edge the player is standing.
	 */
	static final int MAX_RECALL_DISTANCE = 20;

	/** Twelve tiles: far enough that a recall is a rescue, close enough to be one. */
	static final int DEFAULT_RECALL_DISTANCE = 12;

	private final EntourageFigure figure;
	private final int followDistance;
	private final int recallDistance;
	private final FormationSlot formationSlot;
	private final boolean canRun;
	private final EntouragePose idlePose;

	private EntourageSettings(EntourageFigure figure, int followDistance, int recallDistance,
		FormationSlot formationSlot, boolean canRun, EntouragePose idlePose)
	{
		this.figure = figure;
		this.followDistance = followDistance;
		this.recallDistance = recallDistance;
		this.formationSlot = formationSlot;
		this.canRun = canRun;
		this.idlePose = idlePose;
	}

	/**
	 * Reads every setting once and clamps the two numbers.
	 *
	 * <p>The three enums are not clamped and cannot be: {@code ConfigManager} resolves
	 * an enum key by name and falls back to the interface default when the stored name
	 * matches nothing, so a garbage value never reaches this method. They are
	 * null-checked all the same — the proxy is not this plugin's code, and a null figure
	 * would be an NPE inside a game-tick handler rather than a wrong-looking follower.
	 */
	static EntourageSettings from(EntourageConfig config)
	{
		EntourageFigure figure = config.figure();
		FormationSlot slot = config.formationSlot();
		EntouragePose pose = config.idlePose();

		return new EntourageSettings(
			figure == null ? EntourageFigure.DEFAULT : figure,
			clamp(config.followDistance(), MIN_FOLLOW_DISTANCE, MAX_FOLLOW_DISTANCE),
			clamp(config.recallDistance(), MIN_RECALL_DISTANCE, MAX_RECALL_DISTANCE),
			slot == null ? FormationSlot.BEHIND : slot,
			config.canRun(),
			pose == null ? EntouragePose.FIGURE_DEFAULT : pose);
	}

	private static int clamp(int value, int min, int max)
	{
		return value < min ? min : (value > max ? max : value);
	}

	/** @return whose body the follower wears */
	EntourageFigure getFigure()
	{
		return figure;
	}

	/** @return how many tiles out the formation slot sits, 1..2 */
	int getFollowDistance()
	{
		return followDistance;
	}

	/** @return how far behind the player the follower may get before it is put back, 6..20 */
	int getRecallDistance()
	{
		return recallDistance;
	}

	/** @return which side of the player the follower wants to be on */
	FormationSlot getFormationSlot()
	{
		return formationSlot;
	}

	/** @return whether the follower may cover two tiles in a game tick to keep up */
	boolean canRun()
	{
		return canRun;
	}

	/** @return the pose it holds while standing still */
	EntouragePose getIdlePose()
	{
		return idlePose;
	}
}
