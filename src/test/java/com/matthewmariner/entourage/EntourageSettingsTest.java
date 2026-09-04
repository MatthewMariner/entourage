package com.matthewmariner.entourage;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The per-tick snapshot of the settings, and the clamping that is the reason it exists.
 *
 * <p><b>{@code @Range} is a hint to the settings panel and nothing else.</b> It bounds
 * the spinner; it is not enforced on the way out of the config proxy. A profile edited
 * by hand, or carried across a build where the bounds were different, hands the plugin
 * whatever it holds — and both of the numbers here fail silently and badly out of range:
 * a follow distance of zero is a follower standing inside the player, and a recall
 * distance of nine hundred is a follower that walks off the loaded scene and is never
 * brought back.
 */
public class EntourageSettingsTest
{
	@Test
	public void everySettingIsCarriedThroughUnchangedWhenItIsInRange()
	{
		EntourageSettings settings = new FakeConfig()
			.setFigure(EntourageFigure.VANNAKA)
			.setFollowDistance(2)
			.setFormationSlot(FormationSlot.LEFT)
			.setCanRun(false)
			.setRecallDistance(7)
			.setIdlePose(EntouragePose.DANCE)
			.settings();

		assertSame(EntourageFigure.VANNAKA, settings.getFigure());
		assertEquals(2, settings.getFollowDistance());
		assertSame(FormationSlot.LEFT, settings.getFormationSlot());
		assertFalse(settings.canRun());
		assertEquals(7, settings.getRecallDistance());
		assertSame(EntouragePose.DANCE, settings.getIdlePose());
	}

	/**
	 * <b>Each setting is given a different non-default value above.</b> A fixture that
	 * moved one dial at a time would not notice a getter wired to the wrong field, and
	 * one that moved them all to the same value could not tell the fields apart at all —
	 * which is why the two integers differ from each other as well as from their
	 * defaults.
	 */
	@Test
	public void theTwoDistancesAreNotTheSameField()
	{
		EntourageSettings settings = new FakeConfig()
			.setFollowDistance(2)
			.setRecallDistance(6)
			.settings();

		assertEquals(2, settings.getFollowDistance());
		assertEquals(6, settings.getRecallDistance());
	}

	@Test
	public void aFollowDistanceBelowTheMinimumIsRaisedRatherThanPuttingTheFollowerInsideThePlayer()
	{
		assertEquals(EntourageSettings.MIN_FOLLOW_DISTANCE,
			new FakeConfig().setFollowDistance(0).settings().getFollowDistance());
		assertEquals(EntourageSettings.MIN_FOLLOW_DISTANCE,
			new FakeConfig().setFollowDistance(-40).settings().getFollowDistance());
	}

	@Test
	public void aFollowDistanceAboveTheMaximumIsLowered()
	{
		assertEquals(EntourageSettings.MAX_FOLLOW_DISTANCE,
			new FakeConfig().setFollowDistance(9).settings().getFollowDistance());
	}

	@Test
	public void aRecallDistanceOffTheLoadedSceneIsBroughtBackInsideIt()
	{
		assertEquals(EntourageSettings.MAX_RECALL_DISTANCE,
			new FakeConfig().setRecallDistance(900).settings().getRecallDistance());
	}

	@Test
	public void aRecallDistanceThatWouldMakeARecallTheNormalWayToTravelIsRaised()
	{
		assertEquals(EntourageSettings.MIN_RECALL_DISTANCE,
			new FakeConfig().setRecallDistance(1).settings().getRecallDistance());
		assertEquals(EntourageSettings.MIN_RECALL_DISTANCE,
			new FakeConfig().setRecallDistance(Integer.MIN_VALUE).settings().getRecallDistance());
	}

	/**
	 * The bounds themselves are legal values, not exclusive limits. Off-by-one here
	 * would quietly take one of the two follow distances the setting offers away.
	 */
	@Test
	public void bothEndsOfEveryRangeAreAcceptedAsThemselves()
	{
		for (int distance = EntourageSettings.MIN_FOLLOW_DISTANCE;
			distance <= EntourageSettings.MAX_FOLLOW_DISTANCE; distance++)
		{
			assertEquals(distance,
				new FakeConfig().setFollowDistance(distance).settings().getFollowDistance());
		}

		for (int distance = EntourageSettings.MIN_RECALL_DISTANCE;
			distance <= EntourageSettings.MAX_RECALL_DISTANCE; distance++)
		{
			assertEquals(distance,
				new FakeConfig().setRecallDistance(distance).settings().getRecallDistance());
		}
	}

	/**
	 * A null out of the config proxy would be an NPE inside a game-tick handler, which
	 * abandons the rest of the pass — including anything that was supposed to be
	 * deactivated. {@code ConfigManager} resolves an unknown enum name to the interface
	 * default rather than to null, so this is a guard against an implementation this
	 * plugin does not own rather than against a state it has seen.
	 */
	@Test
	public void aNullEnumOutOfTheProxyFallsBackToTheShippedDefault()
	{
		EntourageSettings settings = new FakeConfig()
			.setFigure(null)
			.setFormationSlot(null)
			.setIdlePose(null)
			.settings();

		assertSame(EntourageFigure.DEFAULT, settings.getFigure());
		assertSame(FormationSlot.BEHIND, settings.getFormationSlot());
		assertSame(EntouragePose.FIGURE_DEFAULT, settings.getIdlePose());
	}

	@Test
	public void theShippedDefaultsAreWhatAFreshInstallGets()
	{
		EntourageSettings settings = FakeConfig.defaults();

		assertSame(EntourageFigure.ROGUE, settings.getFigure());
		assertEquals(EntourageSettings.DEFAULT_FOLLOW_DISTANCE, settings.getFollowDistance());
		assertEquals(EntourageSettings.DEFAULT_RECALL_DISTANCE, settings.getRecallDistance());
		assertSame(FormationSlot.BEHIND, settings.getFormationSlot());
		assertSame(EntouragePose.FIGURE_DEFAULT, settings.getIdlePose());
		assertTrue(settings.canRun());
	}
}
