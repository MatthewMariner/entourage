package com.matthewmariner.entourage;

/**
 * Every change {@link EntourageRosterPanel} can make, as writes into the user's profile.
 *
 * <p><b>This is the panel, minus the Swing.</b> The panel below it owns layout, colour and
 * mouse handling and decides nothing; every decision — what a removal does to the four
 * slots after it, when a typed id has to go, what happens at the ends of the range — is
 * here, static, offline and under test. That split is the same one
 * {@code ../gunnars-tools} draws between {@code MonsterLookupPanel} and
 * {@code MonsterIndex}, and it is what lets this be proven on a build machine with no
 * display.
 *
 * <p><b>The config is the source of truth and this never caches it.</b> Every method takes
 * the {@link RosterView} the panel was drawn from, writes, and leaves the panel to read
 * the profile back. There is no second store, no in-memory roster, and nothing to go stale
 * when the same settings are changed from RuneLite's own config screen.
 *
 * <h2>What "remove" means, given that a slot cannot be blank</h2>
 *
 * <p>{@link EntourageConfig}'s javadoc rules out a "none" entry in {@link EntourageFigure},
 * and RuneLite has no way to blank a dropdown — so the config's answer to "stop showing
 * this one" is the follower count, and the count only ever cuts from the <i>end</i>. That
 * is honest and it is not what somebody means when they press × on the third of five
 * cards.
 *
 * <p>So a removal here is a list removal: everybody after the removed slot moves up one,
 * the count drops by one, and the slot that fell off the end is unset so it goes back to
 * its shipped default rather than sitting in the profile as a duplicate of its neighbour.
 * Five dropdowns cannot express that in one gesture, which is the point of the panel; the
 * config screen still shows exactly what happened, because all of it is ordinary writes to
 * the ordinary keys.
 */
final class RosterEdit
{
	/**
	 * The profile key behind each slot, 0-based.
	 *
	 * <p>Every entry is a constant from {@link EntourageConfig} rather than a string
	 * spelled out here, and the reason is that the failure is silent: a panel that wrote
	 * {@code "figure3"} into a profile whose reader looks at {@code figure4} would have a
	 * card that appears to work, persists nothing, and reports nothing. The constants are
	 * the same objects the {@code @ConfigItem} annotations use, so there is one spelling.
	 *
	 * <p>Slot 0's key is {@code figure} and not {@code figure1}, which is the one place
	 * this array is not the boring thing it looks like.
	 */
	private static final String[] FIGURE_KEYS = {
		EntourageConfig.KEY_FIGURE,
		EntourageConfig.KEY_FIGURE_2,
		EntourageConfig.KEY_FIGURE_3,
		EntourageConfig.KEY_FIGURE_4,
		EntourageConfig.KEY_FIGURE_5,
	};

	private RosterEdit()
	{
	}

	/**
	 * @param index which slot, 0-based
	 * @return the profile key that slot's figure lives under
	 * @throws IllegalArgumentException for an index outside 0..{@link RosterView#SLOTS}-1.
	 * A throw rather than a clamp because there is no caller for which writing the wrong
	 * slot is better than not writing: every one of them comes from a card this panel
	 * built, and an index off the end is a bug in the panel rather than a value a user
	 * typed.
	 */
	static String figureKey(int index)
	{
		if (index < 0 || index >= FIGURE_KEYS.length)
		{
			throw new IllegalArgumentException("no such roster slot: " + index);
		}

		return FIGURE_KEYS[index];
	}

	/**
	 * Puts a figure in a slot.
	 *
	 * <p><b>Assigning to the first slot clears a typed NPC id, and only then.</b> The typed
	 * id replaces whatever "Figure 1" says, so picking a preset for slot 1 while an id is in
	 * force would change the dropdown and change nothing on screen — the control would look
	 * broken while working exactly as documented. Choosing a figure for that slot is a
	 * statement about who stands there, so the id that was overriding it goes.
	 *
	 * @param view  the roster as the panel drew it, for whether an id is in force
	 * @param index which slot, 0-based
	 */
	static void assign(ConfigWriter writer, RosterView view, int index, EntourageFigure figure)
	{
		writer.write(figureKey(index), figure.name());

		if (index == 0 && view.isCustom())
		{
			clearCustomNpcId(writer);
		}
	}

	/**
	 * Takes one follower out of the roster: everybody after it moves up, and the count
	 * drops.
	 *
	 * <p>Nothing happens when the slot is not in the roster to begin with, or when it is
	 * the last follower left — see {@link RosterView#canRemove()} for why one is the floor.
	 * Both are no-ops rather than throws: the panel does not draw the action in either case,
	 * and a stale click is not worth an exception on the event dispatch thread.
	 *
	 * <p><b>Removing the first slot also clears a typed id.</b> The id belongs to slot 0
	 * rather than to a figure, so leaving it would move it onto whoever walked up into that
	 * slot — the removal would visibly not have removed the thing that was there.
	 *
	 * @param view  the roster as the panel drew it
	 * @param index which slot, 0-based
	 */
	static void remove(ConfigWriter writer, RosterView view, int index)
	{
		int followers = view.getFollowers();
		if (index < 0 || index >= followers || !view.canRemove())
		{
			return;
		}

		for (int slot = index; slot < followers - 1; slot++)
		{
			writer.write(figureKey(slot), view.getSlot(slot + 1).getFigure().name());
		}

		// The slot that fell off the end is unset rather than left holding a copy of its
		// neighbour: an inactive card showing the same figure as the active one above it
		// reads as the removal having half worked, and a key left in the profile is a user
		// override forever. Unsetting puts the shipped default back, which is what a slot
		// nobody has ever touched shows.
		writer.write(figureKey(followers - 1), null);
		writer.write(EntourageConfig.KEY_FOLLOWERS, Integer.toString(followers - 1));

		if (index == 0 && view.isCustom())
		{
			clearCustomNpcId(writer);
		}
	}

	/**
	 * Adds a follower: the count goes up by one and the slot that comes into play keeps
	 * whatever figure it already names.
	 *
	 * <p>Nothing happens at five. The new slot is not assigned a figure here on purpose —
	 * "how many" and "who" are separate questions, the card the user is about to see already
	 * names somebody, and picking for them would overwrite a choice they may have made
	 * earlier and then turned off.
	 */
	static void add(ConfigWriter writer, RosterView view)
	{
		if (!view.canAdd())
		{
			return;
		}

		writer.write(EntourageConfig.KEY_FOLLOWERS, Integer.toString(view.getFollowers() + 1));
	}

	/**
	 * Sets the follower count outright, clamped through the same answer the scene uses.
	 *
	 * @param followers how many walk with you, 1..{@link EntourageSettings#MAX_FOLLOWERS}
	 */
	static void setFollowers(ConfigWriter writer, int followers)
	{
		writer.write(EntourageConfig.KEY_FOLLOWERS,
			Integer.toString(EntourageSettings.effectiveFollowers(followers)));
	}

	/**
	 * Puts a typed NPC id in the first slot.
	 *
	 * <p>Anything at or below {@link FollowerBody#NO_CUSTOM_NPC} clears it instead of
	 * writing a zero, because those two are not the same profile: a stored zero is a user
	 * override that happens to equal the default and shows up as one in the config screen
	 * forever, while an absent key is the honest "there is no typed id here". Callers do not
	 * have to test the number first.
	 */
	static void setCustomNpcId(ConfigWriter writer, int npcId)
	{
		if (npcId <= FollowerBody.NO_CUSTOM_NPC)
		{
			clearCustomNpcId(writer);
			return;
		}

		writer.write(EntourageConfig.KEY_CUSTOM_NPC_ID, Integer.toString(npcId));
	}

	/** Gives the first slot its dropdown figure back. */
	static void clearCustomNpcId(ConfigWriter writer)
	{
		writer.write(EntourageConfig.KEY_CUSTOM_NPC_ID, null);
	}

	/** Sets the shape they stand in. */
	static void setFormation(ConfigWriter writer, EntourageFormation formation)
	{
		// name() rather than toString(): ConfigManager stores an enum by its constant name,
		// and toString() here is the label a dropdown shows. Writing "Hangout ring" would
		// store a name nothing resolves, which ConfigManager answers by handing back the
		// interface default — a formation setting that silently reverts to "Behind me".
		writer.write(EntourageConfig.KEY_FORMATION, formation.name());
	}

	/**
	 * Sets how far out the nearest rank stands, clamped through the same answer the scene
	 * uses.
	 */
	static void setFollowDistance(ConfigWriter writer, int tiles)
	{
		writer.write(EntourageConfig.KEY_FOLLOW_DISTANCE,
			Integer.toString(EntourageSettings.effectiveFollowDistance(tiles)));
	}
}
