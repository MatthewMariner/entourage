package com.matthewmariner.entourage;

/**
 * Whose body one follower wears: one of {@link EntourageFigure}'s presets, or an NPC id
 * the user typed.
 *
 * <p><b>Why a value type rather than a twenty-fourth enum constant.</b> A {@code CUSTOM}
 * entry in {@link EntourageFigure} was the obvious move and it is the wrong one, for the
 * reason {@link EntourageConfig}'s javadoc already gives about a hypothetical "none"
 * entry: every other constant there is a body that resolves, carries three animations and
 * has a display name, and every test that walks the enum would have to grow an exception
 * for the one member with none of those. A custom body has no fixed NPC id — that is the
 * whole point of it — and no fixed animations, because they are read out of the cache at
 * spawn time. Putting it in the enum would make {@code getNpcId()} a lie for one
 * constant. Putting it here keeps the enum meaning exactly one thing.
 *
 * <p><b>It carries a preset as well as the id, and that is not padding.</b> The preset is
 * what the follower falls back to when the id turns out not to work — see
 * {@link NpcRecord#hasWalkCycle()} for the three ways it can — and it is where the lines
 * over the figure's head come from, because {@link FigureLines} has nothing to say about
 * an arbitrary NPC and inventing it something would be writing dialogue for a body this
 * plugin has never seen.
 *
 * <p><b>Immutable, with value equality, because the roster is compared by value.</b>
 * {@link EntourageScene} notices a settings change by comparing this tick's list of
 * bodies against the one the followers were built for. A custom id changed from 1234 to
 * 5678 has to be a different roster, or the follower would go on wearing the old NPC
 * until something else forced a rebuild — and it is, because these are equal only when
 * both halves are.
 */
final class FollowerBody
{
	/**
	 * The value of the custom-id setting that means "use the dropdown": zero.
	 *
	 * <p>Zero rather than {@code -1} because the setting is a number typed into a spinner
	 * that starts at nothing, and because zero is not an NPC id anybody wants — file 0 of
	 * the NPC archive exists, but it is not a body a person picks on purpose. A separate
	 * on/off switch beside the number was the alternative, and it would only ever be a way
	 * for the two controls to disagree.
	 */
	static final int NO_CUSTOM_NPC = 0;

	private final EntourageFigure figure;

	/** {@link #NO_CUSTOM_NPC} when this is a plain preset. */
	private final int customNpcId;

	private FollowerBody(EntourageFigure figure, int customNpcId)
	{
		this.figure = figure;
		this.customNpcId = customNpcId;
	}

	/** @param figure the preset this follower wears */
	static FollowerBody preset(EntourageFigure figure)
	{
		return new FollowerBody(figure, NO_CUSTOM_NPC);
	}

	/**
	 * A body built from an id the user typed.
	 *
	 * <p><b>This is the one place a typed id is floored</b>, and it is here rather than in
	 * {@link EntourageSettings} because it is the factory every caller goes through:
	 * {@code @Range} bounds the settings spinner and nothing else, so a hand-edited profile
	 * really can hold a negative number, and a second floor at the reading end would be the
	 * same rule written twice with neither copy falsifiable on its own. Deliberately no
	 * ceiling — the NPC archive's highest file id moves with every game update, and
	 * {@link NpcArchive} asks the archive itself rather than a number written down here.
	 *
	 * @param npcId  the NPC to wear. Anything at or below {@link #NO_CUSTOM_NPC} is not a
	 *               custom body at all and gives the preset back, so a caller does not
	 *               have to test the setting before calling this.
	 * @param figure the preset to fall back on, and the source of this follower's lines
	 */
	static FollowerBody custom(int npcId, EntourageFigure figure)
	{
		return new FollowerBody(figure, Math.max(NO_CUSTOM_NPC, npcId));
	}

	/** @return whether this body is an id the user typed rather than a preset */
	boolean isCustom()
	{
		return customNpcId != NO_CUSTOM_NPC;
	}

	/**
	 * @return the preset behind this body: the figure itself when this is a preset, and
	 * the fallback when it is not. Never {@code null}, which is what lets
	 * {@link FigureLines} and {@link EntouragePose} go on taking an
	 * {@link EntourageFigure} rather than growing a second code path for a body that has
	 * no lines and no declared stand.
	 */
	EntourageFigure getFigure()
	{
		return figure;
	}

	/**
	 * @return the NPC whose models and recolours this follower wears — the typed id for a
	 * custom body, and the preset's own for everything else
	 */
	int getNpcId()
	{
		return isCustom() ? customNpcId : figure.getNpcId();
	}

	/**
	 * @return what this body is called in a log line. Deliberately not
	 * {@link EntourageFigure#getDisplayName()}: a log wants the id, so that a warning
	 * about a figure that would not resolve can be traced back to a cache entry.
	 */
	String label()
	{
		return isCustom() ? "custom (npc " + customNpcId + ")" : figure.label();
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof FollowerBody))
		{
			return false;
		}

		FollowerBody that = (FollowerBody) other;
		return customNpcId == that.customNpcId && figure == that.figure;
	}

	/**
	 * Present because {@link #equals(Object)} is, and mixing the id in because a hash that
	 * ignored half the state would be a legal hash and a bad one — see
	 * {@code FollowerBodyTest}, which pins two bodies differing only in their id to two
	 * different values so that this line is something a test can break.
	 */
	@Override
	public int hashCode()
	{
		return 31 * figure.hashCode() + customNpcId;
	}

	/** Matches {@link #label()}: this is what a roster-change log line prints. */
	@Override
	public String toString()
	{
		return label();
	}
}
