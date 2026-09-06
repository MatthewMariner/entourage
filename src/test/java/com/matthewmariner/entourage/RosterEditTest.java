package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.Collections;
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

	/**
	 * <b>And each slot's typed-id key.</b> Same failure, same silence: five id boxes all
	 * writing {@code customNpcId} would look right on whichever one was typed into and would
	 * move every other slot with it.
	 */
	@Test
	public void eachSlotWritesTheTypedIdKeyThatSlotIsStoredUnder()
	{
		assertEquals("the first slot's id predates the other four",
			"customNpcId", RosterEdit.customNpcIdKey(0));
		assertEquals("customNpcId2", RosterEdit.customNpcIdKey(1));
		assertEquals("customNpcId3", RosterEdit.customNpcIdKey(2));
		assertEquals("customNpcId4", RosterEdit.customNpcIdKey(3));
		assertEquals("customNpcId5", RosterEdit.customNpcIdKey(4));
	}

	/** The two key families never collide: no slot's figure key is any slot's id key. */
	@Test
	public void noFigureKeyIsAlsoATypedIdKey()
	{
		for (int figure = 0; figure < RosterView.SLOTS; figure++)
		{
			for (int id = 0; id < RosterView.SLOTS; id++)
			{
				assertFalse(RosterEdit.figureKey(figure) + " is also a typed-id key",
					RosterEdit.figureKey(figure).equals(RosterEdit.customNpcIdKey(id)));
			}
		}
	}

	@Test
	public void aSlotThatDoesNotExistIsARefusalRatherThanAWrongWrite()
	{
		for (int index : new int[]{-1, RosterView.SLOTS, 99})
		{
			try
			{
				RosterEdit.figureKey(index);
				fail("slot " + index + " must not resolve to a figure key");
			}
			catch (IllegalArgumentException expected)
			{
				// writing the wrong slot is worse than not writing
			}

			try
			{
				RosterEdit.customNpcIdKey(index);
				fail("slot " + index + " must not resolve to a typed-id key");
			}
			catch (IllegalArgumentException expected)
			{
				// same rule, same reason
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

		assertFalse("the id is gone", view().getSlot(0).isCustom());
		assertSame(EntourageFigure.GHOMMAL, view().getSlot(0).getFigure());
		assertEquals("Ghommal", view().getSlot(0).getTitle());
	}

	/** The other four slots have no id to clear, and must not clear the first slot's. */
	@Test
	public void pickingAFigureForAnyOtherSlotLeavesTheTypedIdAlone()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS).setCustomNpcId(4931);

		RosterEdit.assign(config, view(), 1, EntourageFigure.GHOMMAL);

		assertTrue("slot 2 has nothing to do with slot 1's typed id", view().getSlot(0).isCustom());
		assertEquals(4931, view().getSlot(0).getCustomNpcId());
	}

	/** Nothing is written that does not need to be. */
	@Test
	public void assigningToTheFirstSlotWithNoTypedIdWritesOnlyTheFigure()
	{
		config.clearWrites();

		RosterEdit.assign(config, view(), 0, EntourageFigure.GHOMMAL);

		assertEquals(Collections.singletonList("figure=GHOMMAL"), config.writes());
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
		assertFalse("the id must not survive onto Hans", view().getSlot(0).isCustom());
	}

	/** Removing anybody else leaves the first slot, and its id, exactly as they were. */
	@Test
	public void removingASlotBehindTheFirstLeavesTheTypedIdInPlace()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE)
			.setCustomNpcId(4931);

		RosterEdit.remove(config, view(), 1);

		assertRoster(2, EntourageFigure.ROGUE, EntourageFigure.PIRATE);
		assertTrue(view().getSlot(0).isCustom());
		assertEquals(4931, view().getSlot(0).getCustomNpcId());
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
		assertEquals("only the count", Collections.singletonList("followers=2"),
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
		RosterEdit.setCustomNpcId(config, 0, 4931);

		assertTrue(view().getSlot(0).isCustom());
		assertEquals(4931, view().getSlot(0).getCustomNpcId());
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

		RosterEdit.setCustomNpcId(config, 0, 0);

		assertFalse(view().getSlot(0).isCustom());
		assertEquals(Collections.singletonList("customNpcId="), config.writes());
	}

	@Test
	public void aNegativeIdIsClearedRatherThanStored()
	{
		config.setCustomNpcId(4931).clearWrites();

		RosterEdit.setCustomNpcId(config, 0, -12);

		assertFalse(view().getSlot(0).isCustom());
		assertEquals(Collections.singletonList("customNpcId="), config.writes());
	}

	/** Five boxes, five keys, and writing one does not move the other four. */
	@Test
	public void eachSlotsTypedIdIsWrittenUnderItsOwnKey()
	{
		for (int index = 0; index < RosterView.SLOTS; index++)
		{
			RosterEdit.setCustomNpcId(config, index, 3000 + index);
		}

		for (int index = 0; index < RosterView.SLOTS; index++)
		{
			assertEquals("slot " + index, 3000 + index, view().getSlot(index).getCustomNpcId());
		}
	}

	@Test
	public void clearingOneSlotsTypedIdLeavesTheOthers()
	{
		config.setCustomNpcIdAt(0, 4931).setCustomNpcIdAt(2, 4933).clearWrites();

		RosterEdit.clearCustomNpcId(config, 2);

		assertEquals(4931, view().getSlot(0).getCustomNpcId());
		assertFalse(view().getSlot(2).isCustom());
		assertEquals(Collections.singletonList("customNpcId3="), config.writes());
	}

	/**
	 * <b>A slot's typed id moves up with its figure.</b> This is the case a shift of only half
	 * the body gets wrong, and gets wrong invisibly: take the second of three out, and without
	 * this the third follower's figure walks up into slot 2 wearing the id that belonged to the
	 * one you just removed.
	 */
	@Test
	public void removingASlotShiftsTheTypedIdsUpWithTheFigures()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE)
			.setCustomNpcIdAt(1, 4932)
			.setCustomNpcIdAt(2, 4933);

		RosterEdit.remove(config, view(), 1);

		assertRoster(2, EntourageFigure.ROGUE, EntourageFigure.PIRATE);
		assertFalse("slot 1 never had an id and must not inherit one",
			view().getSlot(0).isCustom());
		assertEquals("the Pirate brought its own id up with it",
			4933, view().getSlot(1).getCustomNpcId());
		assertFalse("and the slot that fell off the end holds nothing",
			view().getSlot(2).isCustom());
	}

	/**
	 * <b>The slot that falls off the end is unset in both halves.</b> A stale id left on the
	 * tail slot is a number that comes back the next time the count is raised — a follower
	 * wearing an NPC nobody chose.
	 */
	@Test
	public void theSlotThatFallsOffTheEndKeepsNeitherFigureNorTypedId()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS)
			.setCustomNpcIdAt(1, 4932).clearWrites();

		RosterEdit.remove(config, view(), 0);

		assertTrue("the tail figure is unset", config.writes().contains("figure2="));
		assertTrue("and so is the tail id", config.writes().contains("customNpcId2="));
		assertFalse(view().getSlot(1).isCustom());
	}

	// --- the favourites ------------------------------------------------------

	@Test
	public void starringAnIdWritesItIntoTheProfileAndReadsBack()
	{
		config.clearWrites();

		RosterEdit.favourite(config, view(), 3598);

		assertEquals(Collections.singletonList("favouriteNpcIds=3598"), config.writes());
		assertTrue(view().isFavourite(3598));
		assertEquals(Collections.singletonList(3598), view().getFavourites());
	}

	@Test
	public void starringASecondIdPutsItInFrontOfTheFirst()
	{
		RosterEdit.favourite(config, view(), 3598);
		RosterEdit.favourite(config, view(), 4931);

		assertEquals(Arrays.asList(4931, 3598), view().getFavourites());
	}

	@Test
	public void unstarringTakesItBackOut()
	{
		RosterEdit.favourite(config, view(), 3598);
		RosterEdit.favourite(config, view(), 4931);

		RosterEdit.unfavourite(config, view(), 3598);

		assertEquals(Collections.singletonList(4931), view().getFavourites());
		assertFalse(view().isFavourite(3598));
	}

	/**
	 * <b>The last favourite going leaves no key behind.</b> Same rule as a cleared typed id:
	 * "the user has no favourites" and "the user has explicitly chosen to have none" are the
	 * same state, and only one of them is honest about never having been set.
	 */
	@Test
	public void unstarringTheLastOneRemovesTheKeyRatherThanStoringAnEmptyString()
	{
		RosterEdit.favourite(config, view(), 3598);
		config.clearWrites();

		RosterEdit.unfavourite(config, view(), 3598);

		assertEquals(Collections.singletonList("favouriteNpcIds="), config.writes());
		assertTrue(view().getFavourites().isEmpty());
	}

	/**
	 * <b>A gesture that changes nothing writes nothing.</b> Every write posts a
	 * {@code ConfigChanged}, which this plugin answers with a panel redraw — so a write that
	 * changed nothing is a rebuild of the whole body for no reason, fired from inside a mouse
	 * listener that is already about to redraw.
	 */
	@Test
	public void aStarThatChangesNothingWritesNothing()
	{
		RosterEdit.favourite(config, view(), 3598);
		config.clearWrites();

		RosterEdit.favourite(config, view(), 3598);
		RosterEdit.unfavourite(config, view(), 4931);
		RosterEdit.favourite(config, view(), 0);
		RosterEdit.favourite(config, view(), -7);

		assertTrue("wrote " + config.writes(), config.writes().isEmpty());
	}

	@Test
	public void starringAtTheCapDropsTheOldestAndKeepsTheListAtTheCap()
	{
		for (int index = 0; index < Favourites.MAX; index++)
		{
			RosterEdit.favourite(config, view(), 1_000 + index);
		}
		assertEquals(Favourites.MAX, view().getFavourites().size());
		assertFalse(view().canFavourite());

		RosterEdit.favourite(config, view(), 9_999);

		assertEquals(Favourites.MAX, view().getFavourites().size());
		assertTrue(view().isFavourite(9_999));
		assertFalse("the one starred longest ago is the one that went",
			view().isFavourite(1_000));
	}

	/**
	 * <b>The favourites and the roster are separate settings.</b> Starring an id must not put
	 * it in a slot, and putting an id in a slot must not star it — the whole point of the star
	 * is that it is a second, deliberate gesture.
	 */
	@Test
	public void starringAnIdDoesNotPutItInASlotAndViceVersa()
	{
		RosterEdit.favourite(config, view(), 3598);
		assertFalse("starring is not wearing", view().getSlot(0).isCustom());

		RosterEdit.setCustomNpcId(config, 0, 4931);
		assertFalse("and wearing is not starring", view().isFavourite(4931));
		assertEquals(Collections.singletonList(3598), view().getFavourites());
	}

	/** A favourite outlives the slot it was applied to, which is the point of keeping one. */
	@Test
	public void aFavouriteSurvivesTheSlotBeingChanged()
	{
		RosterEdit.favourite(config, view(), 3598);
		RosterEdit.setCustomNpcId(config, 0, 3598);

		RosterEdit.assign(config, view(), 0, EntourageFigure.GHOMMAL);

		assertFalse("the slot went back to a preset", view().getSlot(0).isCustom());
		assertTrue("but the id is still kept", view().isFavourite(3598));
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

		assertEquals(Collections.singletonList("formationSlot=HANGOUT"), config.writes());
		assertEquals("and the label really is different from the name",
			"Hangout ring", EntourageFormation.HANGOUT.toString());
	}

	/**
	 * <b>Both directions write, and both round-trip to the scene.</b> The panel and the
	 * settings screen are the same setting, and this is the switch somebody reaches for
	 * mid-fight — a write that only worked one way would leave the entourage parked with no
	 * way to unpark it from the panel.
	 */
	@Test
	public void parkingAndUnparkingBothWriteAndBothReachTheScene()
	{
		config.clearWrites();

		RosterEdit.setStayPut(config, true);
		assertTrue(view().isStayPut());
		assertTrue("and the scene reads the same one", config.settings().isStayPut());
		assertEquals(Collections.singletonList("stayPut=true"), config.writes());

		config.clearWrites();
		RosterEdit.setStayPut(config, false);
		assertFalse(view().isStayPut());
		assertFalse(config.settings().isStayPut());
		assertEquals(Collections.singletonList("stayPut=false"), config.writes());
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
