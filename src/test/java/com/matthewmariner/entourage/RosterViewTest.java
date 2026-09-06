package com.matthewmariner.entourage;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What the side panel draws, without the panel.
 *
 * <p>Everything on a card comes from here, so everything on a card is testable on a build
 * machine with no display — which is the whole reason the read model is a separate class.
 */
public class RosterViewTest
{
	private final FakeConfig config = new FakeConfig();

	@Test
	public void aFreshInstallDrawsFiveCardsOfWhichOneIsWalkingWithYou()
	{
		RosterView view = config.view();

		assertEquals("five cards, always", 5, view.getSlots().size());
		assertEquals(1, view.getFollowers());

		assertTrue(view.getSlot(0).isActive());
		for (int index = 1; index < RosterView.SLOTS; index++)
		{
			assertFalse("slot " + index + " is past the count and must read as inactive",
				view.getSlot(index).isActive());
		}
	}

	/**
	 * <b>An inactive slot still names somebody.</b> The count and the roster are separate
	 * questions — RuneLite cannot blank a dropdown — so a card past the count shows the
	 * figure it is set to rather than vanishing, which is also the only way that figure
	 * stays visible and changeable.
	 */
	@Test
	public void aSlotPastTheCountStillShowsTheFigureItIsSetTo()
	{
		config.setFigureAt(4, EntourageFigure.GHOMMAL);

		RosterView.Slot slot = config.view().getSlot(4);

		assertFalse(slot.isActive());
		assertSame(EntourageFigure.GHOMMAL, slot.getFigure());
		assertEquals("Ghommal", slot.getTitle());
		assertEquals("Not walking — raise the count", slot.getSubtitle());
	}

	/** Each card reads a different key, and reading the wrong one is a card that lies. */
	@Test
	public void everySlotShowsItsOwnFigureRatherThanItsNeighboursIs()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.VANNAKA,
			EntourageFigure.PIRATE, EntourageFigure.TURAEL);

		RosterView view = config.view();

		assertSame(EntourageFigure.ROGUE, view.getSlot(0).getFigure());
		assertSame(EntourageFigure.HANS, view.getSlot(1).getFigure());
		assertSame(EntourageFigure.VANNAKA, view.getSlot(2).getFigure());
		assertSame(EntourageFigure.PIRATE, view.getSlot(3).getFigure());
		assertSame(EntourageFigure.TURAEL, view.getSlot(4).getFigure());
	}

	/** The heading counts the way a person does, not the way an array does. */
	@Test
	public void theCardsAreNumberedOneToFive()
	{
		RosterView view = config.view();

		assertEquals("Slot 1", view.getSlot(0).getHeading());
		assertEquals("Slot 5", view.getSlot(4).getHeading());
	}

	/**
	 * <b>A typed id shows as an id, because the name cannot be known here.</b> Resolving
	 * NPC 4931 to "Cave goblin guard" needs the client, and the client throws off the client
	 * thread — which is the thread every one of these cards is built on. A number is the
	 * true thing this can say.
	 */
	@Test
	public void aTypedIdIsDrawnAsAnIdWithTheFallbackNamedUnderIt()
	{
		config.setFigure(EntourageFigure.VANNAKA).setCustomNpcId(4931);

		RosterView view = config.view();
		RosterView.Slot first = view.getSlot(0);

		assertTrue(view.getSlot(0).isCustom());
		assertEquals(4931, view.getSlot(0).getCustomNpcId());
		assertTrue(first.isCustom());
		assertEquals("NPC 4931", first.getTitle());
		assertEquals("the fallback is what you see when the id is refused",
			"Typed id, or Vannaka if refused", first.getSubtitle());
		assertSame("and the fallback is still the dropdown's figure",
			EntourageFigure.VANNAKA, first.getFigure());
	}

	/**
	 * <b>A typed id in one slot stays in that slot.</b> This test used to be called
	 * {@code noSlotButTheFirstCanWearATypedId} and asserted the opposite half of the same
	 * concern — that the id belonged to slot 1 alone. There are five keys now, so the thing
	 * worth pinning is that they do not leak into one another: five cards all reading the same
	 * key would look right on whichever one was typed into last and wrong on the other four.
	 */
	@Test
	public void aTypedIdInOneSlotDoesNotAppearInAnyOther()
	{
		for (int wearing = 0; wearing < RosterView.SLOTS; wearing++)
		{
			FakeConfig one = new FakeConfig()
				.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE,
					EntourageFigure.VANNAKA, EntourageFigure.TURAEL)
				.setCustomNpcIdAt(wearing, 4931);

			RosterView view = one.view();
			for (int index = 0; index < RosterView.SLOTS; index++)
			{
				if (index == wearing)
				{
					assertTrue("slot " + index + " was given the id and does not have it",
						view.getSlot(index).isCustom());
					assertEquals(4931, view.getSlot(index).getCustomNpcId());
				}
				else
				{
					assertFalse("slot " + index + " picked up slot " + wearing + "'s id",
						view.getSlot(index).isCustom());
					assertEquals(FollowerBody.NO_CUSTOM_NPC,
						view.getSlot(index).getCustomNpcId());
				}
			}
		}
	}

	/** Five different ids in five slots, each drawn on its own card. */
	@Test
	public void everySlotDrawsItsOwnTypedId()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE,
			EntourageFigure.VANNAKA, EntourageFigure.TURAEL);
		for (int index = 0; index < RosterView.SLOTS; index++)
		{
			config.setCustomNpcIdAt(index, 3000 + index);
		}

		RosterView view = config.view();

		for (int index = 0; index < RosterView.SLOTS; index++)
		{
			assertEquals(3000 + index, view.getSlot(index).getCustomNpcId());
			assertEquals("NPC " + (3000 + index), view.getSlot(index).getTitle());
		}
	}

	/** A slot past the count can hold an id too — it is a setting, not a follower. */
	@Test
	public void aSlotPastTheCountKeepsItsTypedIdTheWayItKeepsItsFigure()
	{
		config.setRoster(EntourageFigure.ROGUE).setCustomNpcIdAt(4, 4931);

		RosterView.Slot slot = config.view().getSlot(4);

		assertFalse(slot.isActive());
		assertTrue(slot.isCustom());
		assertEquals("Not walking — raise the count", slot.getSubtitle());
	}

	/** Every slot floors a hand-edited negative the same way, because there is one floor. */
	@Test
	public void aNegativeIsNotATypedIdInAnySlot()
	{
		for (int index = 0; index < RosterView.SLOTS; index++)
		{
			assertFalse("slot " + index + " read a negative as a typed id",
				new FakeConfig().setCustomNpcIdAt(index, -7).view().getSlot(index).isCustom());
		}
	}

	// --- the favourites ----------------------------------------------------------

	@Test
	public void aFreshInstallHasNoFavourites()
	{
		assertTrue(config.view().getFavourites().isEmpty());
		assertFalse(config.view().isFavourite(3598));
		assertTrue("and there is room for one", config.view().canFavourite());
	}

	@Test
	public void theFavouritesAreTheOnesTheProfileHolds()
	{
		config.setFavouriteNpcIds("3598,4931");

		RosterView view = config.view();

		assertEquals(java.util.Arrays.asList(3598, 4931), view.getFavourites());
		assertTrue(view.isFavourite(3598));
		assertTrue(view.isFavourite(4931));
		assertFalse(view.isFavourite(7));
	}

	/**
	 * <b>The panel draws the list the profile really means, not the string it really holds.</b>
	 * A hand-edited value with a name in it, a blank entry and a trailing comma has to come
	 * through as the ids that survived — the coercion is {@link Favourites}' and this is the
	 * assertion that the view actually goes through it rather than splitting the string
	 * itself.
	 */
	@Test
	public void aMalformedFavouritesValueStillDrawsTheIdsThatSurvivedIt()
	{
		config.setFavouriteNpcIds(" 3598 ,,Gummy,-7,0,4931,");

		assertEquals(java.util.Arrays.asList(3598, 4931), config.view().getFavourites());
	}

	@Test
	public void theFavouritesListIsFullAtTheCap()
	{
		StringBuilder ids = new StringBuilder();
		for (int index = 0; index < Favourites.MAX; index++)
		{
			ids.append(1_000 + index).append(',');
		}
		config.setFavouriteNpcIds(ids.toString());

		assertEquals(Favourites.MAX, config.view().getFavourites().size());
		assertFalse("and there is no room for another", config.view().canFavourite());
	}

	@Test
	public void theFavouritesCannotBeEditedUnderTheNextRedraw()
	{
		config.setFavouriteNpcIds("3598");

		try
		{
			config.view().getFavourites().add(1);
			fail("the favourites list must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// what an unmodifiable list does
		}
	}

	/** Zero means "use the dropdown", and so does anything a hand-edited profile floors past. */
	@Test
	public void zeroOrLessIsNotATypedIdAtAll()
	{
		assertFalse(config.setCustomNpcId(0).view().getSlot(0).isCustom());
		assertFalse("a hand-edited profile really can hold a negative",
			config.setCustomNpcId(-7).view().getSlot(0).isCustom());
		assertEquals("Rogue", config.setCustomNpcId(-7).view().getSlot(0).getTitle());
	}

	/**
	 * <b>The panel draws the roster the scene will actually spawn.</b> {@code @Range} bounds
	 * the settings spinner and nothing else, so a profile edited by hand can say
	 * {@code followers=9} — and a panel that believed it would draw nine active cards over a
	 * five-figure entourage.
	 */
	@Test
	public void aCountOutsideItsRangeIsClampedTheWayTheSceneClampsIt()
	{
		assertEquals(5, config.setFollowers(9).view().getFollowers());
		assertEquals(1, config.setFollowers(0).view().getFollowers());
		assertEquals(1, config.setFollowers(-4).view().getFollowers());

		assertEquals("and it is the scene's own answer rather than a second copy of it",
			config.setFollowers(9).settings().getRosterSize(),
			config.setFollowers(9).view().getFollowers());
	}

	@Test
	public void aFollowDistanceOutsideItsRangeIsClampedTheSameWay()
	{
		assertEquals(2, config.setFollowDistance(40).view().getFollowDistance());
		assertEquals("zero would be a follower standing inside the player",
			1, config.setFollowDistance(0).view().getFollowDistance());

		assertEquals(config.setFollowDistance(40).settings().getFollowDistance(),
			config.setFollowDistance(40).view().getFollowDistance());
	}

	@Test
	public void theFormationIsTheOneTheSettingNames()
	{
		assertSame(EntourageFormation.BEHIND, config.view().getFormation());
		assertSame(EntourageFormation.HANGOUT,
			config.setFormation(EntourageFormation.HANGOUT).view().getFormation());
	}

	/** The two ends of the roster, which decide whether the panel draws an add or a remove. */
	@Test
	public void addingIsOfferedBelowFiveAndRemovingAboveOne()
	{
		assertTrue("a fresh install has room for four more", config.setFollowers(1).view().canAdd());
		assertFalse("and one follower is the floor, because a roster of nobody is the "
			+ "plugin's own off switch", config.setFollowers(1).view().canRemove());

		assertFalse(config.setFollowers(5).view().canAdd());
		assertTrue(config.setFollowers(5).view().canRemove());

		assertTrue(config.setFollowers(3).view().canAdd());
		assertTrue(config.setFollowers(3).view().canRemove());
	}

	/**
	 * A mouse listener holding a stale index must not take the event dispatch thread down
	 * with it — the panel would stop responding rather than misdraw one card.
	 */
	@Test
	public void anIndexOffTheEndAnswersACardRatherThanThrowing()
	{
		RosterView view = config.view();

		assertEquals(0, view.getSlot(-3).getIndex());
		assertEquals(RosterView.SLOTS - 1, view.getSlot(99).getIndex());
	}

	@Test
	public void theSlotsCannotBeEditedUnderTheNextRedraw()
	{
		try
		{
			config.view().getSlots().clear();
			fail("the slot list must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// what an unmodifiable list does
		}
	}
}
