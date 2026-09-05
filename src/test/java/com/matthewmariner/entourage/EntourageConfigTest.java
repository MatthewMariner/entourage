package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The settings surface as a fresh install sees it.
 *
 * <p><b>What is deliberately not tested here, and why.</b> The {@code keyName}s and the
 * {@code @Range} bounds live in annotations, and reading an annotation means reflection,
 * which this repo forbids. So the bounds are pinned through the constants the
 * annotations are written in terms of — {@link EntourageSettings#MIN_FOLLOW_DISTANCE}
 * and friends — which is the whole of the arithmetic either way, and the key names are
 * held by the rule in {@link EntourageConfig}'s javadoc rather than by a test. Writing a
 * literal into a {@code @Range} instead of the constant would slip past this file; that
 * is the known gap, and it is cheaper than a reflective test in a plugin that is not
 * allowed to have one.
 */
public class EntourageConfigTest
{
	private final EntourageConfig config = new FakeConfig();

	/**
	 * The group is the prefix on every key in the user's profile. Renaming it resets
	 * every setting for everyone who has one, silently, so it is pinned as a literal
	 * rather than compared against itself.
	 */
	@Test
	public void theConfigGroupIsTheOneAlreadyWrittenIntoProfiles()
	{
		assertEquals("entourage", EntourageConfig.GROUP);
	}

	@Test
	public void aFreshInstallWalksARogueOneTileBehindAtARunHoldingItsOwnPose()
	{
		assertEquals("one follower, so a profile written before the roster existed still "
			+ "means what it meant", 1, config.followers());
		assertEquals(EntourageFigure.ROGUE, config.figure());
		assertEquals(1, config.followDistance());
		assertEquals(EntourageFormation.BEHIND, config.formation());
		assertEquals(12, config.recallDistance());
		assertEquals(EntouragePose.FIGURE_DEFAULT, config.idlePose());
		assertTrue("running is on by default — without it a running player is never kept up with",
			config.canRun());
	}

	/**
	 * <b>Five different bodies in the five slots.</b> A default that put the same figure in
	 * all of them would make turning the roster up look like the setting had not worked —
	 * five identical figures reads as one figure drawn five times, which is also what the
	 * bug would look like.
	 */
	@Test
	public void theFiveFigureSlotsShipFiveDifferentFigures()
	{
		Set<EntourageFigure> shipped = new HashSet<>(Arrays.asList(
			config.figure(), config.figure2(), config.figure3(), config.figure4(),
			config.figure5()));

		assertEquals("five slots, five bodies", EntourageSettings.MAX_FOLLOWERS, shipped.size());
		assertEquals("the first slot is the one every existing profile has",
			EntourageFigure.DEFAULT, config.figure());

		for (EntourageFigure figure : shipped)
		{
			assertEquals(figure.name() + " is not on the human rig, so a default group would "
					+ "not move as one group",
				EntourageAnimation.HUMAN_WALK, figure.getWalkAnimation());
		}
	}

	/** And the enum's own answer is the one the dropdowns use, rather than a second copy. */
	@Test
	public void theShippedRosterComesFromTheFigureEnumRatherThanFromTheConfig()
	{
		assertEquals(EntourageFigure.defaultAt(0), config.figure());
		assertEquals(EntourageFigure.defaultAt(1), config.figure2());
		assertEquals(EntourageFigure.defaultAt(2), config.figure3());
		assertEquals(EntourageFigure.defaultAt(3), config.figure4());
		assertEquals(EntourageFigure.defaultAt(4), config.figure5());

		assertEquals("anything off the end of the roster is the first slot's figure",
			EntourageFigure.DEFAULT, EntourageFigure.defaultAt(-1));
		assertEquals(EntourageFigure.DEFAULT, EntourageFigure.defaultAt(9));
	}

	@Test
	public void aFreshInstallSaysSomethingOnceAMinuteInMagenta()
	{
		assertTrue("the feature the dialogue settings exist for is on by default",
			config.dialogue());
		assertEquals("", config.dialogueLines());
		assertEquals(100, config.dialogueIntervalTicks());
		assertEquals(8, config.dialogueDwellTicks());
		assertEquals(DialogueColour.MAGENTA, config.dialogueColour());
		assertEquals(DialogueFont.REGULAR, config.dialogueFont());
		assertFalse("a name over the head is opt-in", config.nameLabel());
		assertFalse("and so is vanishing inside an instance", config.hideInInstances());
		assertEquals(FollowerFacing.AT_ME, config.facing());
	}

	/**
	 * <b>A default outside its own bounds is silently overridden and nothing says so.</b>
	 * {@link EntourageSettings} clamps, so a default of 12 with a minimum raised to 14
	 * would leave the settings panel showing 12 while the follower used 14 — a number in
	 * the UI that is not the number in effect. This is the assertion that ties the two
	 * halves together.
	 */
	@Test
	public void everyDefaultSurvivesTheClampUnchanged()
	{
		EntourageSettings settings = EntourageSettings.from(config);

		assertEquals("the roster count default is outside its own range",
			config.followers(), settings.getRosterSize());
		assertEquals("the follow distance default is outside its own range",
			config.followDistance(), settings.getFollowDistance());
		assertEquals("the recall distance default is outside its own range",
			config.recallDistance(), settings.getRecallDistance());
		assertEquals("the dialogue interval default is outside its own range",
			config.dialogueIntervalTicks(), settings.getDialogueIntervalTicks());
		assertEquals("the dwell default is outside its own range, or longer than the interval",
			config.dialogueDwellTicks(), settings.getDialogueDwellTicks());
	}

	/**
	 * The cadence bounds in wall-clock terms, which is how the settings panel describes
	 * them and how anybody reasons about them. The units are the thing that has gone wrong
	 * before in this family of plugins: the client's other clock is the client tick, thirty
	 * to a game tick, and reading one figure as the other is a thirtyfold error.
	 */
	@Test
	public void theDialogueBoundsAreTheOnesTheDocumentationClaims()
	{
		assertEquals("six seconds", 6_000 / EntourageSettings.TICK_MILLIS,
			EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS);
		assertEquals("six minutes, past which the dial duplicates the off switch",
			360_000 / EntourageSettings.TICK_MILLIS, EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS);
		assertEquals("one minute", 60_000 / EntourageSettings.TICK_MILLIS,
			EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS);

		assertEquals("three seconds, about one read of one line",
			3_000 / EntourageSettings.TICK_MILLIS, EntourageSettings.MIN_DIALOGUE_DWELL_TICKS);
		assertEquals("eighteen seconds", 18_000 / EntourageSettings.TICK_MILLIS,
			EntourageSettings.MAX_DIALOGUE_DWELL_TICKS);

		assertTrue("a range whose minimum exceeds its maximum clamps everything to one value",
			EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS
				< EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS);
		assertTrue(EntourageSettings.MIN_DIALOGUE_DWELL_TICKS
			< EntourageSettings.MAX_DIALOGUE_DWELL_TICKS);
		assertTrue("the shipped dwell has to be comfortably inside the shipped interval",
			EntourageSettings.DEFAULT_DIALOGUE_DWELL_TICKS
				< EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS);
	}

	/**
	 * <b>The two caps on the custom-lines box, as literals.</b>
	 *
	 * <p>Everything that exercises them — the cut, the count, and the check that no
	 * shipped line is longer than a user's may be — is written in terms of these
	 * constants, which is right for a behavioural test and useless as a guard on the
	 * values themselves: raising {@code MAX_CUSTOM_LINE_LENGTH} to five thousand moves
	 * every one of those expectations with it and nothing goes red. This is the one place
	 * the numbers are written out, and they are the numbers the README quotes to the user.
	 */
	@Test
	public void theCustomLineCapsAreTheOnesTheReadmeQuotes()
	{
		assertEquals("the README says anything past sixty characters is cut",
			60, EntourageSettings.MAX_CUSTOM_LINE_LENGTH);
		assertEquals("and that the first twelve lines are used",
			12, EntourageSettings.MAX_CUSTOM_LINES);
		assertEquals("the box is comma-separated, which is what was asked for",
			',', EntourageSettings.CUSTOM_LINE_SEPARATOR);
	}

	@Test
	public void theBoundsAreTheOnesTheDocumentationClaims()
	{
		assertEquals("a zero follow distance is a follower standing inside the player",
			1, EntourageSettings.MIN_FOLLOW_DISTANCE);
		assertEquals("greedy stepping does not survive a slot further out than this",
			2, EntourageSettings.MAX_FOLLOW_DISTANCE);
		assertEquals("a column of five at the widest follow distance reaches this far back",
			6, EntourageSettings.MAX_STATION_DISTANCE);
		assertEquals("the furthest station plus two tiles of slack", 8,
			EntourageSettings.MIN_RECALL_DISTANCE);
		assertEquals("the loaded scene is 104 tiles, and a recall has to land inside it",
			20, EntourageSettings.MAX_RECALL_DISTANCE);

		assertTrue("a range whose minimum exceeds its maximum clamps everything to one value",
			EntourageSettings.MIN_FOLLOW_DISTANCE < EntourageSettings.MAX_FOLLOW_DISTANCE);
		assertTrue(EntourageSettings.MIN_RECALL_DISTANCE < EntourageSettings.MAX_RECALL_DISTANCE);
		assertTrue("a follower standing where it was told to must never be recalled for it",
			EntourageSettings.MAX_STATION_DISTANCE < EntourageSettings.MIN_RECALL_DISTANCE);
		assertTrue("a follower must never be told to stand further out than it is recalled from",
			EntourageSettings.MAX_FOLLOW_DISTANCE < EntourageSettings.MIN_RECALL_DISTANCE);
	}

	/**
	 * <b>The roster bounds, as literals, and the budget claim behind the upper one.</b>
	 * Everything that exercises the cap is written in terms of {@code MAX_FOLLOWERS},
	 * which is right for a behavioural test and useless as a guard on the value: raising it
	 * to nine moves every one of those loops with it and nothing goes red. This is the one
	 * place the number is written out.
	 *
	 * <p>Five is what the owner asked for and what the inherited budget clears —
	 * {@code ../lively-cities}' {@code RenderPolicy} allows 80 active objects and 9 model
	 * builds per frame, and five followers are five objects built once each. <b>No frame
	 * cost has been measured for this plugin</b>, so the ceiling is a deliberate stop
	 * rather than a profiled one, and moving it should be a decision somebody takes rather
	 * than a character they change.
	 */
	@Test
	public void theRosterBoundsAreTheOnesTheReadmeQuotes()
	{
		assertEquals("one follower, because a roster of nobody is the plugin's own off switch",
			1, EntourageSettings.MIN_FOLLOWERS);
		assertEquals("five, which is what was asked for and what the inherited budget clears",
			5, EntourageSettings.MAX_FOLLOWERS);
		assertEquals("and a fresh install is still the one figure it always was",
			1, EntourageSettings.DEFAULT_FOLLOWERS);

		assertTrue(EntourageSettings.MIN_FOLLOWERS < EntourageSettings.MAX_FOLLOWERS);
		assertTrue("the shipped roster has to be inside the range it is clamped to",
			EntourageSettings.DEFAULT_FOLLOWERS >= EntourageSettings.MIN_FOLLOWERS
				&& EntourageSettings.DEFAULT_FOLLOWERS <= EntourageSettings.MAX_FOLLOWERS);
	}
}
