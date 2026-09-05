package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.Collections;
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
			.setFormation(EntourageFormation.LEFT)
			.setFacing(FollowerFacing.SOUTH_WEST)
			.setCanRun(false)
			.setRecallDistance(9)
			.setIdlePose(EntouragePose.DANCE)
			.setHideInInstances(true)
			.setDialogue(false)
			.setDialogueIntervalTicks(37)
			.setDialogueDwellTicks(11)
			.settings();

		assertEquals(presets(EntourageFigure.VANNAKA), settings.getBodies());
		assertEquals(2, settings.getFollowDistance());
		assertSame(EntourageFormation.LEFT, settings.getFormation());
		assertSame(FollowerFacing.SOUTH_WEST, settings.getFacing());
		assertFalse(settings.canRun());
		assertEquals(9, settings.getRecallDistance());
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

	// --- The roster -----------------------------------------------------------

	/**
	 * The five slots are five different fields, read in order. A fixture that set them all
	 * to the same figure could not tell a getter wired to its neighbour from a correct one,
	 * and one that only set the first could not tell four slots from one.
	 */
	@Test
	public void theRosterIsTheFiveFigureSlotsInOrder()
	{
		EntourageSettings settings = new FakeConfig()
			.setRoster(EntourageFigure.VANNAKA, EntourageFigure.HANS, EntourageFigure.PIRATE,
				EntourageFigure.TURAEL, EntourageFigure.GHOMMAL)
			.settings();

		assertEquals(presets(EntourageFigure.VANNAKA, EntourageFigure.HANS,
			EntourageFigure.PIRATE, EntourageFigure.TURAEL, EntourageFigure.GHOMMAL),
			settings.getBodies());
		assertEquals(EntourageSettings.MAX_FOLLOWERS, settings.getRosterSize());
	}

	/**
	 * <b>The count decides how many slots are read, and the slots past it are not read at
	 * all.</b> RuneLite has no way to blank a dropdown, so a slot the user has turned off
	 * still holds whatever they last set it to — a roster that took the figures without
	 * consulting the count would put a follower on screen that the count says is not there.
	 */
	@Test
	public void theSlotsPastTheCountAreIgnoredRatherThanWalkedWith()
	{
		EntourageSettings settings = new FakeConfig()
			.setRoster(EntourageFigure.VANNAKA, EntourageFigure.HANS)
			.setFigureAt(2, EntourageFigure.PIRATE)
			.setFigureAt(4, EntourageFigure.GHOMMAL)
			.settings();

		assertEquals(presets(EntourageFigure.VANNAKA, EntourageFigure.HANS),
			settings.getBodies());
		assertFalse("a figure past the count is not in the entourage",
			settings.getBodies().contains(FollowerBody.preset(EntourageFigure.PIRATE)));
	}

	/** The same figure five times is a legal roster, and it is five followers. */
	@Test
	public void thesameFigureInEverySlotIsStillFiveFollowers()
	{
		EntourageSettings settings = new FakeConfig()
			.setRoster(EntourageFigure.ROGUE, EntourageFigure.ROGUE, EntourageFigure.ROGUE,
				EntourageFigure.ROGUE, EntourageFigure.ROGUE)
			.settings();

		assertEquals(5, settings.getRosterSize());
		assertEquals(Collections.nCopies(5, FollowerBody.preset(EntourageFigure.ROGUE)),
			settings.getBodies());
	}

	@Test
	public void aRosterCountOutsideItsRangeIsBroughtBackInside()
	{
		assertEquals("nobody at all is what the plugin's own on switch is for",
			EntourageSettings.MIN_FOLLOWERS,
			new FakeConfig().setFollowers(0).settings().getRosterSize());
		assertEquals(EntourageSettings.MIN_FOLLOWERS,
			new FakeConfig().setFollowers(Integer.MIN_VALUE).settings().getRosterSize());
		assertEquals("a hand-edited profile must not be able to ask for a crowd",
			EntourageSettings.MAX_FOLLOWERS,
			new FakeConfig().setFollowers(400).settings().getRosterSize());
	}

	@Test
	public void everyRosterCountInRangeIsAcceptedAsItself()
	{
		for (int count = EntourageSettings.MIN_FOLLOWERS;
			count <= EntourageSettings.MAX_FOLLOWERS; count++)
		{
			assertEquals(count, new FakeConfig().setFollowers(count).settings().getRosterSize());
		}
	}

	/**
	 * The roster a caller could edit is a roster {@link EntourageScene} would fail to
	 * notice changing — it keeps the reference to compare next tick's against.
	 */
	@Test
	public void theRosterCannotBeEditedByItsCallers()
	{
		try
		{
			FakeConfig.defaults().getBodies().add(FollowerBody.preset(EntourageFigure.HANS));
			fail("a snapshot a caller can edit is not a snapshot");
		}
		catch (UnsupportedOperationException expected)
		{
			// The point of the test.
		}
	}

	/**
	 * A null out of any of the five figure slots falls back to that slot's own shipped
	 * default rather than to the first slot's, so a proxy that answered null for slot three
	 * does not silently make a roster of duplicate Rogues.
	 */
	@Test
	public void aNullFigureSlotFallsBackToThatSlotsOwnDefault()
	{
		EntourageSettings settings = new FakeConfig()
			.setFollowers(EntourageSettings.MAX_FOLLOWERS)
			.setFigureAt(0, null)
			.setFigureAt(1, null)
			.setFigureAt(2, null)
			.setFigureAt(3, null)
			.setFigureAt(4, null)
			.settings();

		assertEquals(presets(
			EntourageFigure.defaultAt(0), EntourageFigure.defaultAt(1),
			EntourageFigure.defaultAt(2), EntourageFigure.defaultAt(3),
			EntourageFigure.defaultAt(4)), settings.getBodies());
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
			.setRecallDistance(9)
			.settings();

		assertEquals(2, settings.getFollowDistance());
		assertEquals(9, settings.getRecallDistance());
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
			.setFormation(null)
			.setFacing(null)
			.setIdlePose(null)
			.setDialogueLines(null)
			.settings();

		assertEquals(presets(EntourageFigure.DEFAULT), settings.getBodies());
		assertSame(EntourageFormation.DEFAULT, settings.getFormation());
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

		assertEquals("a fresh install still walks one Rogue, exactly as it always did",
			presets(EntourageFigure.ROGUE), settings.getBodies());
		assertEquals(EntourageSettings.DEFAULT_FOLLOWERS, settings.getRosterSize());
		assertEquals(EntourageSettings.DEFAULT_FOLLOW_DISTANCE, settings.getFollowDistance());
		assertEquals(EntourageSettings.DEFAULT_RECALL_DISTANCE, settings.getRecallDistance());
		assertSame(EntourageFormation.BEHIND, settings.getFormation());
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
	// --- The custom NPC id ----------------------------------------------------

	/**
	 * The typed id replaces the first slot's body and leaves the other four alone — see
	 * {@link EntourageConfig}'s javadoc on why there is one of these rather than five.
	 */
	@Test
	public void aCustomIdReplacesTheFirstSlotAndNothingElse()
	{
		EntourageSettings settings = new FakeConfig()
			.setRoster(EntourageFigure.VANNAKA, EntourageFigure.HANS, EntourageFigure.PIRATE)
			.setCustomNpcId(4931)
			.settings();

		assertEquals(Arrays.asList(
			FollowerBody.custom(4931, EntourageFigure.VANNAKA),
			FollowerBody.preset(EntourageFigure.HANS),
			FollowerBody.preset(EntourageFigure.PIRATE)), settings.getBodies());
	}

	/** The first slot's dropdown is still read: it is what the id falls back to. */
	@Test
	public void aCustomIdKeepsTheFirstSlotsDropdownAsItsFallback()
	{
		EntourageSettings settings = new FakeConfig()
			.setFigure(EntourageFigure.HANS)
			.setCustomNpcId(4931)
			.settings();

		assertSame(EntourageFigure.HANS, settings.getBodies().get(0).getFigure());
		assertEquals(4931, settings.getBodies().get(0).getNpcId());
	}

	@Test
	public void anEmptyCustomIdLeavesTheRosterExactlyAsItWas()
	{
		assertEquals(presets(EntourageFigure.ROGUE),
			new FakeConfig().setCustomNpcId(0).settings().getBodies());
	}

	/**
	 * {@code @Range} bounds the spinner and nothing else, so a hand-edited profile can
	 * hold a negative id. Floored rather than refused, because zero already means "use the
	 * dropdown" and that is the right answer for a number that is not a file id.
	 */
	@Test
	public void aNegativeCustomIdIsFlooredToNoCustomNpcAtAll()
	{
		EntourageSettings settings = new FakeConfig().setCustomNpcId(-7).settings();

		assertFalse(settings.getBodies().get(0).isCustom());
		assertEquals(presets(EntourageFigure.ROGUE), settings.getBodies());
	}

	/**
	 * <b>Deliberately no ceiling.</b> The NPC archive's highest file id moves with every
	 * game update, and {@code NpcArchive} asks the archive itself — a number written into
	 * this class would be a false refusal for every NPC added after it was written.
	 */
	@Test
	public void aLargeCustomIdIsNotCappedHere()
	{
		assertEquals(9_999_999,
			new FakeConfig().setCustomNpcId(9_999_999).settings().getBodies().get(0).getNpcId());
	}

	/**
	 * The roster is compared by value to decide whether the followers have to be rebuilt,
	 * so a changed id has to make a different roster. Without it the follower would go on
	 * wearing the old NPC until something else forced a rebuild.
	 */
	@Test
	public void changingTheCustomIdChangesTheRoster()
	{
		assertNotEquals(
			new FakeConfig().setCustomNpcId(4931).settings().getBodies(),
			new FakeConfig().setCustomNpcId(4932).settings().getBodies());
	}

	@Test
	public void theShippedDefaultIsNoCustomNpc()
	{
		// FakeConfig's field is initialised from the interface default, so this is that
		// default pinned to a literal rather than to itself.
		assertEquals(0, new FakeConfig().customNpcId());
		assertFalse(FakeConfig.defaults().getBodies().get(0).isCustom());
	}

	/**
	 * @param figures the roster, in order
	 * @return the same roster as {@link FollowerBody}s with no custom id on any of them,
	 * which is what every settings snapshot holds until somebody types one
	 */
	private static java.util.List<FollowerBody> presets(EntourageFigure... figures)
	{
		java.util.List<FollowerBody> bodies = new java.util.ArrayList<>(figures.length);
		for (EntourageFigure figure : figures)
		{
			bodies.add(FollowerBody.preset(figure));
		}
		return bodies;
	}
}
