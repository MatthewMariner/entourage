package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What {@link EntourageRosterPanel} draws: five slots, the count that decides which of
 * them are in play, and the two movement dials the panel surfaces.
 *
 * <p><b>Everything the panel shows is in here, and nothing in here touches the client.</b>
 * That is the constraint, not a happy accident. {@code PluginManager} calls
 * {@code startUp()} and every Swing listener from the event dispatch thread, and a client
 * read off the client thread throws — {@code IllegalStateException} in a shipped client,
 * an assertion in a development one. A panel that resolved an NPC's real name to put on a
 * card would therefore break the plugin on every enable. So no card is built from
 * anything but the config proxy and two enums, which are safe to read anywhere, and the
 * one thing a live client could tell us — what NPC 4931 is actually called — is
 * deliberately not asked for. See {@link EntourageRosterPanel} for what the panel shows
 * instead.
 *
 * <p><b>A snapshot, built per redraw.</b> Same reasoning as {@link EntourageSettings}, and
 * the same shape: read every value once so that a card cannot be drawn against one answer
 * and clicked against another. Nothing holds one across a redraw.
 *
 * <p><b>It answers the same clamps the scene does rather than its own.</b> The count and
 * the follow distance come back through {@link EntourageSettings#effectiveFollowers(int)}
 * and {@link EntourageSettings#effectiveFollowDistance(int)}, and a slot's figure through
 * {@link EntourageSettings#figureAt}. A profile edited by hand really can say
 * {@code followers=9}; the panel has to draw the roster the scene will actually spawn, not
 * the one the file claims.
 */
final class RosterView
{
	/**
	 * How many slot cards there are: five, always.
	 *
	 * <p><b>Slots past the count are drawn, greyed, rather than hidden</b>, and that is the
	 * config's own shape showing through rather than a decoration. RuneLite has no way to
	 * blank a dropdown, so "how many walk with me" and "who are they" are separate
	 * questions — the README says so — and a card that vanished would leave the figure it
	 * names set to something the user cannot see and cannot change.
	 */
	static final int SLOTS = EntourageSettings.MAX_FOLLOWERS;

	private final int followers;
	private final List<Slot> slots;
	private final EntourageFormation formation;
	private final int followDistance;

	private RosterView(int followers, List<Slot> slots, EntourageFormation formation,
		int followDistance)
	{
		this.followers = followers;
		this.slots = slots;
		this.formation = formation;
		this.followDistance = followDistance;
	}

	/**
	 * Reads the whole panel's worth of settings, once.
	 *
	 * @param config the user's profile, through RuneLite's proxy or a fake
	 */
	static RosterView of(EntourageConfig config)
	{
		int followers = EntourageSettings.effectiveFollowers(config.followers());
		int customNpcId = config.customNpcId();

		List<Slot> slots = new ArrayList<>(SLOTS);
		for (int index = 0; index < SLOTS; index++)
		{
			EntourageFigure figure = EntourageSettings.figureAt(config, index);

			// FollowerBody.custom is where a typed id is floored — the one place, so that a
			// negative in a hand-edited profile reads as "no typed id" here exactly as it
			// does on the tick path. Only slot 0 is offered it, which is the whole of the
			// custom-id rule and is stated once, in EntourageConfig's javadoc.
			FollowerBody body = index == 0
				? FollowerBody.custom(customNpcId, figure)
				: FollowerBody.preset(figure);

			slots.add(new Slot(index, body, index < followers));
		}

		EntourageFormation formation = config.formation();

		return new RosterView(followers, Collections.unmodifiableList(slots),
			formation == null ? EntourageFormation.DEFAULT : formation,
			EntourageSettings.effectiveFollowDistance(config.followDistance()));
	}

	/** @return how many figures walk with you, 1..{@link #SLOTS} */
	int getFollowers()
	{
		return followers;
	}

	/** @return all five slots in roster order, active ones first. Never null, never short. */
	List<Slot> getSlots()
	{
		return slots;
	}

	/**
	 * @param index which slot, 0-based
	 * @return that slot, or the first one for an index outside 0..{@link #SLOTS}-1. Clamped
	 * rather than thrown for the reason {@link EntourageFormation#tileFor} clamps: this is
	 * reached from a mouse listener, and an exception on the event dispatch thread is a
	 * panel that stops responding rather than one that misdraws a card.
	 */
	Slot getSlot(int index)
	{
		return slots.get(index < 0 ? 0 : (index >= SLOTS ? SLOTS - 1 : index));
	}

	/** @return the shape they stand in */
	EntourageFormation getFormation()
	{
		return formation;
	}

	/** @return how far out the nearest rank stands, in tiles */
	int getFollowDistance()
	{
		return followDistance;
	}

	/** @return the typed NPC id in force, or {@link FollowerBody#NO_CUSTOM_NPC} */
	int getCustomNpcId()
	{
		return getSlot(0).getCustomNpcId();
	}

	/** @return whether the first slot is wearing a typed id rather than its dropdown figure */
	boolean isCustom()
	{
		return getSlot(0).isCustom();
	}

	/** @return whether there is room for another follower */
	boolean canAdd()
	{
		return followers < EntourageSettings.MAX_FOLLOWERS;
	}

	/**
	 * @return whether any follower may be taken out of the roster. False at one, because
	 * a roster of nobody is the plugin's own off switch and this config deliberately has
	 * no second one — see {@link EntourageSettings#MIN_FOLLOWERS}.
	 */
	boolean canRemove()
	{
		return followers > EntourageSettings.MIN_FOLLOWERS;
	}

	/** One card: which slot it is, whose body is in it, and whether it is in play. */
	static final class Slot
	{
		private final int index;
		private final FollowerBody body;
		private final boolean active;

		private Slot(int index, FollowerBody body, boolean active)
		{
			this.index = index;
			this.body = body;
			this.active = active;
		}

		/** @return which slot this is, 0-based */
		int getIndex()
		{
			return index;
		}

		/**
		 * @return the preset this slot names — the figure itself, or the fallback behind a
		 * typed id. Never null.
		 */
		EntourageFigure getFigure()
		{
			return body.getFigure();
		}

		/** @return whether this slot is inside the follower count */
		boolean isActive()
		{
			return active;
		}

		/** @return whether this slot is wearing an id the user typed */
		boolean isCustom()
		{
			return body.isCustom();
		}

		/** @return the typed id, or {@link FollowerBody#NO_CUSTOM_NPC} if there is not one */
		int getCustomNpcId()
		{
			return body.isCustom() ? body.getNpcId() : FollowerBody.NO_CUSTOM_NPC;
		}

		/** @return the card's heading: "Slot 1" through "Slot 5", counting the way people do */
		String getHeading()
		{
			return "Slot " + (index + 1);
		}

		/**
		 * @return the big line on the card: the figure's name, or the id when one is typed.
		 *
		 * <p>An id rather than a name for a typed NPC because <b>this plugin cannot know the
		 * name without the client</b>, and the client cannot be read from the thread this
		 * runs on. "NPC 4931" is the true thing it can say; a guessed name would be worse
		 * than a number.
		 */
		String getTitle()
		{
			return body.isCustom() ? "NPC " + getCustomNpcId() : getFigure().getDisplayName();
		}

		/**
		 * @return the muted line under the title.
		 *
		 * <p>Three states and they are the three that matter: this slot is walking with you,
		 * this slot is set but the count does not reach it, or this slot is wearing a typed
		 * id whose fallback is worth naming because a refused id is what you see instead.
		 * The refusal path is the README's, restated in seven words rather than re-explained.
		 */
		String getSubtitle()
		{
			if (!active)
			{
				return "Not walking — raise the count";
			}

			return body.isCustom()
				? "Typed id, or " + getFigure().getDisplayName() + " if refused"
				: "Walking with you";
		}
	}
}
