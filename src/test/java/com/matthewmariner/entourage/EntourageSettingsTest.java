package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
			.setFacing(FollowerFacing.SOUTH_WEST)
			.setCanRun(false)
			.setRecallDistance(7)
			.setIdlePose(EntouragePose.DANCE)
			.setHideInInstances(true)
			.setDialogue(false)
			.setDialogueIntervalTicks(37)
			.setDialogueDwellTicks(11)
			.settings();

		assertSame(EntourageFigure.VANNAKA, settings.getFigure());
		assertEquals(2, settings.getFollowDistance());
		assertSame(FormationSlot.LEFT, settings.getFormationSlot());
		assertSame(FollowerFacing.SOUTH_WEST, settings.getFacing());
		assertFalse(settings.canRun());
		assertEquals(7, settings.getRecallDistance());
		assertSame(EntouragePose.DANCE, settings.getIdlePose());
		assertTrue(settings.hideInInstances());
		assertFalse(settings.isDialogue());
		assertEquals(37, settings.getDialogueIntervalTicks());
		assertEquals(11, settings.getDialogueDwellTicks());
	}

	/**
	 * The two booleans are not the same field. Both default one way and are set the other
	 * above, so a getter wired to its neighbour would pass that test and fail this one.
	 */
	@Test
	public void theTwoSwitchesAreNotTheSameField()
	{
		EntourageSettings hidden = new FakeConfig().setHideInInstances(true).settings();
		EntourageSettings quiet = new FakeConfig().setDialogue(false).settings();

		assertTrue(hidden.hideInInstances());
		assertTrue("hiding in instances has nothing to do with talking", hidden.isDialogue());

		assertFalse(quiet.isDialogue());
		assertFalse("and neither has being quiet", quiet.hideInInstances());
	}

	// --- The dialogue cadence -------------------------------------------------

	@Test
	public void anIntervalOutsideItsRangeIsBroughtBackInside()
	{
		assertEquals(EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS,
			new FakeConfig().setDialogueIntervalTicks(0).settings().getDialogueIntervalTicks());
		assertEquals("a zero interval is a modulo by zero in the cadence",
			EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS,
			new FakeConfig().setDialogueIntervalTicks(-9).settings().getDialogueIntervalTicks());
		assertEquals(EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS,
			new FakeConfig().setDialogueIntervalTicks(99_999).settings().getDialogueIntervalTicks());
	}

	@Test
	public void aDwellOutsideItsRangeIsBroughtBackInside()
	{
		assertEquals(EntourageSettings.MIN_DIALOGUE_DWELL_TICKS,
			new FakeConfig().setDialogueDwellTicks(0).settings().getDialogueDwellTicks());
		assertEquals(EntourageSettings.MAX_DIALOGUE_DWELL_TICKS,
			new FakeConfig()
				.setDialogueDwellTicks(99_999)
				.setDialogueIntervalTicks(EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS)
				.settings()
				.getDialogueDwellTicks());
	}

	/**
	 * <b>A dwell at least as long as the interval saturates the screen, so it is not a bad
	 * setting but an unreachable one.</b> A line is expired before the follower is asked
	 * whether it is due, so at {@code dwell == interval} it is never silent for a single
	 * tick, and above it the text never clears at all. {@code ../lively-cities} shipped
	 * exactly that pair as a default once. Neither {@code @Range} can see the other's
	 * value, so the guard lives here.
	 */
	@Test
	public void aDwellIsAlwaysShorterThanTheIntervalItSitsIn()
	{
		for (int interval = EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS;
			interval <= EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS; interval++)
		{
			for (int dwell : new int[]{
				0, EntourageSettings.MIN_DIALOGUE_DWELL_TICKS, interval, interval + 1, 99_999})
			{
				int effective = new FakeConfig()
					.setDialogueIntervalTicks(interval)
					.setDialogueDwellTicks(dwell)
					.settings()
					.getDialogueDwellTicks();

				assertTrue("dwell " + dwell + " in interval " + interval + " came out " + effective,
					effective < interval);
				assertTrue("and it must not be clamped below the shortest readable dwell",
					effective >= EntourageSettings.MIN_DIALOGUE_DWELL_TICKS);
			}
		}
	}

	/**
	 * The relationship that makes the clamp above safe: subtracting one tick from the
	 * tightest interval still leaves a dwell at or above the shortest one offered. If the
	 * minimum interval were ever lowered to five, that would stop being true and the clamp
	 * would silently produce a dwell shorter than the setting allows.
	 */
	@Test
	public void theTightestIntervalStillLeavesRoomForTheShortestDwell()
	{
		assertTrue(EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS - 1
			>= EntourageSettings.MIN_DIALOGUE_DWELL_TICKS);
	}

	// --- The custom lines box -------------------------------------------------

	@Test
	public void anEmptyBoxIsNoLinesAtAll()
	{
		assertTrue(EntourageSettings.parseLines(null).isEmpty());
		assertTrue(EntourageSettings.parseLines("").isEmpty());
		assertTrue(EntourageSettings.parseLines("   ").isEmpty());
		assertTrue("a box holding only separators is an empty box",
			EntourageSettings.parseLines(",,, ,").isEmpty());
	}

	@Test
	public void commasSeparateLinesAndTheSpaceAroundThemIsTrimmed()
	{
		assertEquals(Arrays.asList("Hello there", "Nice weather", "Onward"),
			EntourageSettings.parseLines("Hello there,  Nice weather ,Onward"));
	}

	/**
	 * <b>Every comma starts a new line, with no escape.</b> The setting was asked for as
	 * comma-separated values, and a {@code \,} escape would mean also defining what a bare
	 * backslash does in a box with no syntax highlighting and no error reporting. The
	 * README says so in as many words; this is where the rule is pinned.
	 */
	@Test
	public void aCommaInsideALineSplitsItRatherThanBeingEscaped()
	{
		assertEquals(Arrays.asList("Well done", "here you go"),
			EntourageSettings.parseLines("Well done, here you go"));
		assertEquals("a backslash is a character like any other",
			Arrays.asList("Well done\\", "here you go"),
			EntourageSettings.parseLines("Well done\\, here you go"));
	}

	@Test
	public void emptyEntriesAreDroppedRatherThanDrawnAsBlankText()
	{
		assertEquals(Arrays.asList("one", "two"),
			EntourageSettings.parseLines(",one,,  ,two,"));
	}

	@Test
	public void aLineLongerThanTheOverlayWillDrawIsCutRatherThanRefused()
	{
		StringBuilder essay = new StringBuilder();
		while (essay.length() < EntourageSettings.MAX_CUSTOM_LINE_LENGTH * 2)
		{
			essay.append("long ");
		}

		List<String> lines = EntourageSettings.parseLines(essay.toString());

		assertEquals(1, lines.size());
		assertEquals("cut, not dropped — a user who typed too much should see most of it",
			EntourageSettings.MAX_CUSTOM_LINE_LENGTH, lines.get(0).length());
	}

	@Test
	public void aBoxWithTooManyLinesIsCappedRatherThanParsedForever()
	{
		StringBuilder many = new StringBuilder();
		for (int i = 0; i < EntourageSettings.MAX_CUSTOM_LINES * 3; i++)
		{
			many.append("line ").append(i).append(',');
		}

		List<String> lines = EntourageSettings.parseLines(many.toString());

		assertEquals(EntourageSettings.MAX_CUSTOM_LINES, lines.size());
		assertEquals("and it is the first ones that are kept", "line 0", lines.get(0));
	}

	/**
	 * <b>Custom lines replace the presets; they do not add to them.</b> Emptying the box
	 * brings the figure's own lines back, which is what makes the choice safe to try.
	 */
	@Test
	public void customLinesReplaceTheFiguresOwnAndAnEmptyBoxRestoresThem()
	{
		EntourageSettings mine = new FakeConfig().setDialogueLines("Mind the step,Onward").settings();

		assertEquals(Arrays.asList("Mind the step", "Onward"),
			mine.linesFor(EntourageFigure.VANNAKA));
		assertEquals("the same lines whichever figure is wearing them",
			mine.linesFor(EntourageFigure.VANNAKA), mine.linesFor(EntourageFigure.HANS));

		EntourageSettings shipped = FakeConfig.defaults();
		assertEquals(FigureLines.of(EntourageFigure.VANNAKA),
			shipped.linesFor(EntourageFigure.VANNAKA));
		assertNotEquals("and the presets really do differ per figure",
			shipped.linesFor(EntourageFigure.VANNAKA), shipped.linesFor(EntourageFigure.HANS));
	}

	@Test
	public void theParsedLinesCannotBeEditedByTheirCallers()
	{
		try
		{
			EntourageSettings.parseLines("one,two").add("three");
			fail("a snapshot a caller can edit is not a snapshot");
		}
		catch (UnsupportedOperationException expected)
		{
			// The point of the test.
		}
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
			.setFacing(null)
			.setIdlePose(null)
			.setDialogueLines(null)
			.settings();

		assertSame(EntourageFigure.DEFAULT, settings.getFigure());
		assertSame(FormationSlot.BEHIND, settings.getFormationSlot());
		assertSame(FollowerFacing.AT_ME, settings.getFacing());
		assertSame(EntouragePose.FIGURE_DEFAULT, settings.getIdlePose());
		assertTrue("a null settings box is no custom lines, not a crash in the tick handler",
			settings.getCustomLines().isEmpty());
		assertEquals("and the figure's own lines are still there",
			FigureLines.of(EntourageFigure.DEFAULT), settings.linesFor(EntourageFigure.DEFAULT));
	}

	@Test
	public void theShippedDefaultsAreWhatAFreshInstallGets()
	{
		EntourageSettings settings = FakeConfig.defaults();

		assertSame(EntourageFigure.ROGUE, settings.getFigure());
		assertEquals(EntourageSettings.DEFAULT_FOLLOW_DISTANCE, settings.getFollowDistance());
		assertEquals(EntourageSettings.DEFAULT_RECALL_DISTANCE, settings.getRecallDistance());
		assertSame(FormationSlot.BEHIND, settings.getFormationSlot());
		assertSame(FollowerFacing.AT_ME, settings.getFacing());
		assertSame(EntouragePose.FIGURE_DEFAULT, settings.getIdlePose());
		assertTrue(settings.canRun());
		assertFalse("a Player Owned House is an instance, and that is where a follower is "
				+ "most wanted", settings.hideInInstances());
		assertTrue(settings.isDialogue());
		assertEquals(EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS,
			settings.getDialogueIntervalTicks());
		assertEquals(EntourageSettings.DEFAULT_DIALOGUE_DWELL_TICKS,
			settings.getDialogueDwellTicks());
		assertTrue(settings.getCustomLines().isEmpty());
	}
}
