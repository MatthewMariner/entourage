package com.matthewmariner.entourage;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Every change the side panel can make, and the promise that all of it round-trips.
 *
 * <p><b>{@link FakeConfig} is both the writer and the reader here, on purpose.</b> A write
 * goes in through {@link ConfigWriter} exactly as it would through {@code ConfigManager} —
 * an enum by {@code name()}, an int as a string, {@code null} to remove the key — and then
 * comes back out through the same getters {@link EntourageSettings} reads on the game-tick
 * path. So "the setting the panel wrote is the setting the plugin uses" is asserted rather
 * than assumed, and a key spelled wrong fails here instead of in a live client.
 */
public class RosterEditTest
{
	private final FakeConfig config = new FakeConfig();

	/** @return the roster as the panel would next draw it */
	private RosterView view()
	{
		return config.view();
	}

	private void assertRoster(int followers, EntourageFigure... figures)
	{
		RosterView view = view();
		assertEquals("follower count", followers, view.getFollowers());
		for (int index = 0; index < figures.length; index++)
		{
			assertSame("slot " + (index + 1), figures[index], view.getSlot(index).getFigure());
		}
	}

	// --- the keys ------------------------------------------------------------

	/**
	 * <b>Each slot's key, spelled out.</b> A panel that wrote {@code figure3} where the
	 * reader looks at {@code figure4} would save, reload and change nothing, with no error
	 * anywhere — so the mapping is pinned as literals rather than compared against the array
	 * that produces it.
	 */
	@Test
	public void eachSlotWritesTheKeyThatSlotIsStoredUnder()
	{
		assertEquals("the first slot predates the other four", "figure", RosterEdit.figureKey(0));
		assertEquals("figure2", RosterEdit.figureKey(1));
		assertEquals("figure3", RosterEdit.figureKey(2));
		assertEquals("figure4", RosterEdit.figureKey(3));
		assertEquals("figure5", RosterEdit.figureKey(4));
	}

	@Test
	public void aSlotThatDoesNotExistIsARefusalRatherThanAWrongWrite()
	{
		for (int index : new int[]{-1, RosterView.SLOTS, 99})
		{
			try
			{
				RosterEdit.figureKey(index);
				fail("slot " + index + " must not resolve to a key");
			}
			catch (IllegalArgumentException expected)
			{
				// writing the wrong slot is worse than not writing
			}
		}
	}

	// --- assigning -----------------------------------------------------------

	@Test
	public void assigningAFigureLandsInThatSlotAndOnlyThatSlot()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE);

		RosterEdit.assign(config, view(), 1, EntourageFigure.GHOMMAL);

		assertRoster(3, EntourageFigure.ROGUE, EntourageFigure.GHOMMAL, EntourageFigure.PIRATE);
	}

	/** And the tick path reads what the panel wrote, which is the whole promise. */
	@Test
	public void whatThePanelWritesIsWhatTheSceneSpawns()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS);

		RosterEdit.assign(config, view(), 1, EntourageFigure.WISE_OLD_MAN);

		assertEquals(Arrays.asList(
				FollowerBody.preset(EntourageFigure.ROGUE),
				FollowerBody.preset(EntourageFigure.WISE_OLD_MAN)),
			config.settings().getBodies());
	}

	@Test
	public void everyFigureSurvivesTheTripThroughTheProfile()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			RosterEdit.assign(config, view(), 0, figure);
			assertSame(figure.getDisplayName() + " did not survive the write",
				figure, view().getSlot(0).getFigure());
		}
	}

	/**
	 * <b>Choosing a figure for the first slot clears a typed id.</b> The id replaces
	 * whatever "Figure 1" says, so without this the card would change, the profile would
	 * change, and the figure on screen would not — a control that looks broken while working
	 * exactly as documented.
	 */
	@Test
	public void pickingAFigureForTheFirstSlotTakesTheTypedIdOffIt()
	{
		config.setCustomNpcId(4931);

		RosterEdit.assign(config, view(), 0, EntourageFigure.GHOMMAL);

		assertFalse("the id is gone", view().isCustom());
		assertSame(EntourageFigure.GHOMMAL, view().getSlot(0).getFigure());
		assertEquals("Ghommal", view().getSlot(0).getTitle());
	}

	/** The other four slots have no id to clear, and must not clear the first slot's. */
	@Test
	public void pickingAFigureForAnyOtherSlotLeavesTheTypedIdAlone()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS).setCustomNpcId(4931);

		RosterEdit.assign(config, view(), 1, EntourageFigure.GHOMMAL);

		assertTrue("slot 2 has nothing to do with slot 1's typed id", view().isCustom());
		assertEquals(4931, view().getCustomNpcId());
	}

	/** Nothing is written that does not need to be. */
	@Test
	public void assigningToTheFirstSlotWithNoTypedIdWritesOnlyTheFigure()
	{
		config.clearWrites();

		RosterEdit.assign(config, view(), 0, EntourageFigure.GHOMMAL);

		assertEquals(java.util.Collections.singletonList("figure=GHOMMAL"), config.writes());
	}

	// --- removing ------------------------------------------------------------

	/**
	 * <b>What "remove" means when a slot cannot be blank.</b> Everybody after the removed
	 * one moves up and the count drops — a list removal, which is what pressing × on the
	 * third of five cards means and which five dropdowns cannot express in one gesture.
	 */
	@Test
	public void removingASlotMovesEverybodyAfterItUpAndDropsTheCount()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.VANNAKA,
			EntourageFigure.PIRATE, EntourageFigure.TURAEL);

		RosterEdit.remove(config, view(), 1);

		assertRoster(4, EntourageFigure.ROGUE, EntourageFigure.VANNAKA, EntourageFigure.PIRATE,
			EntourageFigure.TURAEL);
	}

	@Test
	public void removingTheLastFollowerJustDropsTheCount()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.VANNAKA);

		RosterEdit.remove(config, view(), 2);

		assertRoster(2, EntourageFigure.ROGUE, EntourageFigure.HANS);
	}

	/**
	 * <b>The slot that falls off the end goes back to its shipped default.</b> Left holding
	 * a copy of its neighbour it would read as the removal having half worked — two cards,
	 * one active and one greyed, both saying "Turael" — and a key left in the profile is a
	 * user override forever.
	 */
	@Test
	public void theSlotThatFallsOffTheEndIsUnsetRatherThanLeftAsADuplicate()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.TURAEL);

		RosterEdit.remove(config, view(), 0);

		assertRoster(2, EntourageFigure.HANS, EntourageFigure.TURAEL);
		assertSame("slot 3 is back to what a slot nobody has touched shows",
			EntourageFigure.defaultAt(2), view().getSlot(2).getFigure());
		assertTrue("and it is an unset rather than a write of the default",
			config.writes().contains("figure3="));
	}

	/**
	 * <b>Removing the first slot takes its typed id with it.</b> The id belongs to the slot
	 * rather than to a figure, so leaving it would move it onto whoever walked up into that
	 * slot — a removal that visibly did not remove the thing that was there.
	 */
	@Test
	public void removingTheFirstSlotTakesTheTypedIdWithIt()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS).setCustomNpcId(4931);

		RosterEdit.remove(config, view(), 0);

		assertRoster(1, EntourageFigure.HANS);
		assertFalse("the id must not survive onto Hans", view().isCustom());
	}

	/** Removing anybody else leaves the first slot, and its id, exactly as they were. */
	@Test
	public void removingASlotBehindTheFirstLeavesTheTypedIdInPlace()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE)
			.setCustomNpcId(4931);

		RosterEdit.remove(config, view(), 1);

		assertRoster(2, EntourageFigure.ROGUE, EntourageFigure.PIRATE);
		assertTrue(view().isCustom());
		assertEquals(4931, view().getCustomNpcId());
	}

	/**
	 * One follower is the floor. A roster of nobody is the plugin's own toggle in the plugin
	 * list, and a second control meaning the same thing is only a way for the two to
	 * disagree.
	 */
	@Test
	public void theLastFollowerCannotBeRemoved()
	{
		config.setRoster(EntourageFigure.GHOMMAL).clearWrites();

		RosterEdit.remove(config, view(), 0);

		assertRoster(1, EntourageFigure.GHOMMAL);
		assertTrue("nothing was written at all", config.writes().isEmpty());
	}

	@Test
	public void removingASlotThatIsNotInTheRosterDoesNothing()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS).clearWrites();

		RosterEdit.remove(config, view(), 3);
		RosterEdit.remove(config, view(), -1);

		assertRoster(2, EntourageFigure.ROGUE, EntourageFigure.HANS);
		assertTrue(config.writes().isEmpty());
	}

	// --- adding --------------------------------------------------------------

	/**
	 * Adding raises the count and picks nobody. "How many" and "who" are separate questions,
	 * and the card that comes into play already names somebody — choosing for them would
	 * overwrite a figure the user may have set earlier and then turned off.
	 */
	@Test
	public void addingRaisesTheCountAndLeavesTheNewSlotAsItWas()
	{
		config.setRoster(EntourageFigure.ROGUE).setFigureAt(1, EntourageFigure.GHOMMAL)
			.clearWrites();

		RosterEdit.add(config, view());

		assertRoster(2, EntourageFigure.ROGUE, EntourageFigure.GHOMMAL);
		assertEquals("only the count", java.util.Collections.singletonList("followers=2"),
			config.writes());
	}

	@Test
	public void thereIsNoSixthFollowerToAdd()
	{
		config.setFollowers(5).clearWrites();

		RosterEdit.add(config, view());

		assertEquals(5, view().getFollowers());
		assertTrue(config.writes().isEmpty());
	}

	// --- the count, set outright ---------------------------------------------

	@Test
	public void theCountIsClampedTheWayTheSceneClampsIt()
	{
		RosterEdit.setFollowers(config, 4);
		assertEquals(4, view().getFollowers());

		RosterEdit.setFollowers(config, 99);
		assertEquals("a nine-follower roster is five followers", 5, view().getFollowers());

		RosterEdit.setFollowers(config, 0);
		assertEquals("and zero is one", 1, view().getFollowers());
	}

	// --- the typed id --------------------------------------------------------

	@Test
	public void aTypedIdIsWrittenAndReadBackByTheScene()
	{
		RosterEdit.setCustomNpcId(config, 4931);

		assertTrue(view().isCustom());
		assertEquals(4931, view().getCustomNpcId());
		assertTrue("and the follower the scene builds wears it",
			config.settings().getBodies().get(0).isCustom());
		assertEquals(4931, config.settings().getBodies().get(0).getNpcId());
	}

	/**
	 * <b>Clearing removes the key rather than writing a zero.</b> Those are not the same
	 * profile: a stored zero is a user override that happens to equal the default and shows
	 * up as one in the config screen forever, while an absent key is the honest "there is no
	 * typed id here".
	 */
	@Test
	public void clearingATypedIdRemovesTheKeyRatherThanStoringZero()
	{
		config.setCustomNpcId(4931).clearWrites();

		RosterEdit.setCustomNpcId(config, 0);

		assertFalse(view().isCustom());
		assertEquals(java.util.Collections.singletonList("customNpcId="), config.writes());
	}

	@Test
	public void aNegativeIdIsClearedRatherThanStored()
	{
		config.setCustomNpcId(4931).clearWrites();

		RosterEdit.setCustomNpcId(config, -12);

		assertFalse(view().isCustom());
		assertEquals(java.util.Collections.singletonList("customNpcId="), config.writes());
	}

	// --- the two quick dials -------------------------------------------------

	@Test
	public void everyFormationSurvivesTheTripThroughTheProfile()
	{
		for (EntourageFormation formation : EntourageFormation.values())
		{
			RosterEdit.setFormation(config, formation);
			assertSame(formation + " did not survive the write", formation, view().getFormation());
			assertSame("and the scene reads the same one", formation,
				config.settings().getFormation());
		}
	}

	/**
	 * <b>The constant name, not the label.</b> {@code ConfigManager} stores an enum by
	 * {@code name()}; writing "Hangout ring" would store a name nothing resolves, which the
	 * proxy answers by handing back the interface default — a formation setting that
	 * silently reverts to "Behind me".
	 */
	@Test
	public void aFormationIsStoredUnderItsConstantNameAndNotItsLabel()
	{
		config.clearWrites();

		RosterEdit.setFormation(config, EntourageFormation.HANGOUT);

		assertEquals(java.util.Collections.singletonList("formationSlot=HANGOUT"), config.writes());
		assertEquals("and the label really is different from the name",
			"Hangout ring", EntourageFormation.HANGOUT.toString());
	}

	@Test
	public void theFollowDistanceIsClampedTheWayTheSceneClampsIt()
	{
		RosterEdit.setFollowDistance(config, 2);
		assertEquals(2, view().getFollowDistance());
		assertEquals(2, config.settings().getFollowDistance());

		RosterEdit.setFollowDistance(config, 40);
		assertEquals(2, view().getFollowDistance());

		RosterEdit.setFollowDistance(config, 0);
		assertEquals("zero would be a follower standing inside the player",
			1, view().getFollowDistance());
	}
}
