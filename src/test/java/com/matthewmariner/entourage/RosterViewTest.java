package com.matthewmariner.entourage;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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

		assertTrue(view.isCustom());
		assertEquals(4931, view.getCustomNpcId());
		assertTrue(first.isCustom());
		assertEquals("NPC 4931", first.getTitle());
		assertEquals("the fallback is what you see when the id is refused",
			"Typed id, or Vannaka if refused", first.getSubtitle());
		assertSame("and the fallback is still the dropdown's figure",
			EntourageFigure.VANNAKA, first.getFigure());
	}

	/** And only the first slot is ever offered one — see EntourageConfig on why there is one. */
	@Test
	public void noSlotButTheFirstCanWearATypedId()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS).setCustomNpcId(4931);

		RosterView view = config.view();

		assertTrue(view.getSlot(0).isCustom());
		for (int index = 1; index < RosterView.SLOTS; index++)
		{
			assertFalse("slot " + index + " has no typed id to wear", view.getSlot(index).isCustom());
			assertEquals(FollowerBody.NO_CUSTOM_NPC, view.getSlot(index).getCustomNpcId());
		}
	}

	/** Zero means "use the dropdown", and so does anything a hand-edited profile floors past. */
	@Test
	public void zeroOrLessIsNotATypedIdAtAll()
	{
		assertFalse(config.setCustomNpcId(0).view().isCustom());
		assertFalse("a hand-edited profile really can hold a negative",
			config.setCustomNpcId(-7).view().isCustom());
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
			org.junit.Assert.fail("the slot list must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// what an unmodifiable list does
		}
	}
}
