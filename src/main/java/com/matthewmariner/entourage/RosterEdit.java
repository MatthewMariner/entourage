package com.matthewmariner.entourage;

import java.util.List;

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

	/**
	 * The profile key behind each slot's typed NPC id, 0-based.
	 *
	 * <p>Slot 0's key is {@code customNpcId} and not {@code customNpcId1}, for exactly the
	 * reason slot 0's figure key is {@code figure} and not {@code figure1}: it is the name
	 * that setting has had since it was the only one, and it is sitting in the profile of
	 * everybody who has ever typed an id. See {@link EntourageConfig#KEY_CUSTOM_NPC_ID}.
	 */
	private static final String[] CUSTOM_NPC_ID_KEYS = {
		EntourageConfig.KEY_CUSTOM_NPC_ID,
		EntourageConfig.KEY_CUSTOM_NPC_ID_2,
		EntourageConfig.KEY_CUSTOM_NPC_ID_3,
		EntourageConfig.KEY_CUSTOM_NPC_ID_4,
		EntourageConfig.KEY_CUSTOM_NPC_ID_5,
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
	 * @param index which slot, 0-based
	 * @return the profile key that slot's typed NPC id lives under
	 * @throws IllegalArgumentException for an index outside 0..{@link RosterView#SLOTS}-1,
	 * for the reason {@link #figureKey} throws
	 */
	static String customNpcIdKey(int index)
	{
		if (index < 0 || index >= CUSTOM_NPC_ID_KEYS.length)
		{
			throw new IllegalArgumentException("no such roster slot: " + index);
		}

		return CUSTOM_NPC_ID_KEYS[index];
	}

	/**
	 * Puts a figure in a slot.
	 *
	 * <p><b>Assigning to a slot clears that slot's typed NPC id.</b> The typed id replaces
	 * whatever that slot's dropdown says, so picking a preset while an id is in force would
	 * change the dropdown and change nothing on screen — the control would look broken while
	 * working exactly as documented. Choosing a figure for a slot is a statement about who
	 * stands there, so the id that was overriding it goes.
	 *
	 * <p>This used to read {@code index == 0 && view.isCustom()}, because only the first slot
	 * could wear an id. The condition is now the slot's own, which is the same rule with the
	 * special case taken out of it.
	 *
	 * @param view  the roster as the panel drew it, for whether an id is in force
	 * @param index which slot, 0-based
	 */
	static void assign(ConfigWriter writer, RosterView view, int index, EntourageFigure figure)
	{
		writer.write(figureKey(index), figure.name());

		if (view.getSlot(index).isCustom())
		{
			clearCustomNpcId(writer, index);
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
	 * <p><b>A slot's typed id moves up with its figure, and that is what makes this a list
	 * removal rather than a figure removal.</b> A slot wears a body, and a body is a
	 * dropdown figure plus — sometimes — an id typed over it; shifting one half without the
	 * other would take the third follower out and leave the fourth wearing the third's
	 * number. When the typed id existed only in slot 0 this method could not express that,
	 * and instead cleared the id outright on a removal of slot 0 for a related reason: the id
	 * belonged to the slot rather than to a figure, so leaving it would have moved it onto
	 * whoever walked up. The shift below is the general version of that rule, and it needs no
	 * special case for the first slot.
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

		// Known and accepted: this is up to eleven separate ConfigManager writes — two per
		// shifted slot plus the tail unset and the count — from the event dispatch thread,
		// while EntourageScene reads the whole roster once per 600ms game tick on the client
		// thread. A tick landing between two of these writes sees an intermediate roster that
		// was never the user's intent — e.g. [B,B,C,D] after only the first slot has shifted
		// — which is a full teardown and rebuild of every RuneLiteObject against a shape that
		// is about to change again. Batching these into one write was considered and rejected:
		// ConfigManager gives this plugin no transaction to batch them into, and building one
		// is a bigger, riskier change than is wise this close to submission. The window is
		// bounded rather than left to compound, though — the very next tick reads the fully
		// settled roster this method leaves behind and rebuilds again, correctly, so the
		// visible cost is a rebuild flicker rather than a lasting wrong state.
		for (int slot = index; slot < followers - 1; slot++)
		{
			RosterView.Slot from = view.getSlot(slot + 1);
			writer.write(figureKey(slot), from.getFigure().name());
			writeCustomNpcId(writer, slot, from.getCustomNpcId());
		}

		// The slot that fell off the end is unset rather than left holding a copy of its
		// neighbour: an inactive card showing the same figure as the active one above it
		// reads as the removal having half worked, and a key left in the profile is a user
		// override forever. Unsetting puts the shipped default back, which is what a slot
		// nobody has ever touched shows. Both halves of the body, for the same reason —
		// a stale id left on the tail slot is a number that comes back the next time the
		// count is raised.
		writer.write(figureKey(followers - 1), null);
		clearCustomNpcId(writer, followers - 1);
		writer.write(EntourageConfig.KEY_FOLLOWERS, Integer.toString(followers - 1));
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
	 * Puts a typed NPC id in a slot.
	 *
	 * <p>Anything at or below {@link FollowerBody#NO_CUSTOM_NPC} clears it instead of
	 * writing a zero, because those two are not the same profile: a stored zero is a user
	 * override that happens to equal the default and shows up as one in the config screen
	 * forever, while an absent key is the honest "there is no typed id here". Callers do not
	 * have to test the number first.
	 *
	 * @param index which slot, 0-based
	 */
	static void setCustomNpcId(ConfigWriter writer, int index, int npcId)
	{
		writeCustomNpcId(writer, index, npcId);
	}

	/**
	 * The write itself, shared with {@link #remove}'s shift.
	 *
	 * <p>Separate from the public method only so that the shift cannot drift from what a
	 * user typing into the box does: a removal that wrote {@code "0"} where the box writes
	 * an unset would leave a slot with an explicit zero override, which the config screen
	 * shows forever and which is exactly the state this method exists to avoid.
	 */
	private static void writeCustomNpcId(ConfigWriter writer, int index, int npcId)
	{
		if (npcId <= FollowerBody.NO_CUSTOM_NPC)
		{
			clearCustomNpcId(writer, index);
			return;
		}

		writer.write(customNpcIdKey(index), Integer.toString(npcId));
	}

	/**
	 * Gives one slot its dropdown figure back.
	 *
	 * @param index which slot, 0-based
	 */
	static void clearCustomNpcId(ConfigWriter writer, int index)
	{
		writer.write(customNpcIdKey(index), null);
	}

	/**
	 * Stars an NPC id, or moves it back to the front if it is already starred.
	 *
	 * <p>The whole list is rewritten rather than appended to, because the profile holds one
	 * string: there is no "add" on a config value, only a new value for the key. Read,
	 * transform, write — and the read comes off the {@link RosterView} the panel was drawn
	 * from, so what is starred is what the user was looking at.
	 *
	 * @param view  the roster as the panel drew it, for the list as it stands
	 * @param npcId the id to star. Anything at or below {@link FollowerBody#NO_CUSTOM_NPC}
	 *              writes nothing at all — see {@link Favourites#with} — rather than writing
	 *              the list back unchanged, because a write nothing changed is a
	 *              {@code ConfigChanged} event and a panel redraw for no reason.
	 */
	static void favourite(ConfigWriter writer, RosterView view, int npcId)
	{
		writeFavourites(writer, view, Favourites.with(view.getFavourites(), npcId));
	}

	/**
	 * Un-stars an NPC id.
	 *
	 * @param npcId the id to drop. One that is not on the list writes nothing, for the reason
	 *              {@link #favourite} gives: the panel draws from a snapshot and a stale click
	 *              should cost nothing rather than causing a redraw.
	 */
	static void unfavourite(ConfigWriter writer, RosterView view, int npcId)
	{
		writeFavourites(writer, view, Favourites.without(view.getFavourites(), npcId));
	}

	/**
	 * Writes the favourites list, unless it is the list that is already there.
	 *
	 * <p><b>The no-op check is not an optimisation.</b> Every write here goes to
	 * {@code ConfigManager}, which posts a {@code ConfigChanged} the plugin subscribes to in
	 * order to redraw the panel — so a write that changed nothing is a redraw that changed
	 * nothing, on the event dispatch thread, from inside a mouse listener that is already
	 * about to redraw. Comparing the two lists is cheaper than the round trip and makes
	 * "star an id that is already at the front" cost exactly nothing.
	 */
	private static void writeFavourites(ConfigWriter writer, RosterView view, List<Integer> next)
	{
		if (next.equals(view.getFavourites()))
		{
			return;
		}

		// The empty list is written as an unset rather than as an empty string, for the
		// reason a zero id is: "the user has no favourites" and "the user has explicitly
		// chosen to have none" are the same state, and only one of them is honest about
		// never having been set.
		writer.write(EntourageConfig.KEY_FAVOURITE_NPC_IDS,
			next.isEmpty() ? null : Favourites.format(next));
	}

	/**
	 * Parks the entourage where it stands, or sets it walking again.
	 *
	 * <p>Written as a boolean string, which is what {@code ConfigManager} stores for a
	 * boolean item and what its proxy parses back. Both values are written rather than the
	 * "on" one being an unset: this is a switch somebody flips back and forth mid-fight, and
	 * an off state that means "no key" would make the two directions asymmetric for no gain.
	 */
	static void setStayPut(ConfigWriter writer, boolean stayPut)
	{
		writer.write(EntourageConfig.KEY_STAY_PUT, Boolean.toString(stayPut));
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
