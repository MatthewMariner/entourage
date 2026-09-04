package com.matthewmariner.entourage;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
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
		assertEquals(EntourageFigure.ROGUE, config.figure());
		assertEquals(1, config.followDistance());
		assertEquals(FormationSlot.BEHIND, config.formationSlot());
		assertEquals(12, config.recallDistance());
		assertEquals(EntouragePose.FIGURE_DEFAULT, config.idlePose());
		assertTrue("running is on by default — without it a running player is never kept up with",
			config.canRun());
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

		assertEquals("the follow distance default is outside its own range",
			config.followDistance(), settings.getFollowDistance());
		assertEquals("the recall distance default is outside its own range",
			config.recallDistance(), settings.getRecallDistance());
	}

	@Test
	public void theBoundsAreTheOnesTheDocumentationClaims()
	{
		assertEquals("a zero follow distance is a follower standing inside the player",
			1, EntourageSettings.MIN_FOLLOW_DISTANCE);
		assertEquals("greedy stepping does not survive a slot further out than this",
			2, EntourageSettings.MAX_FOLLOW_DISTANCE);
		assertEquals(6, EntourageSettings.MIN_RECALL_DISTANCE);
		assertEquals("the loaded scene is 104 tiles, and a recall has to land inside it",
			20, EntourageSettings.MAX_RECALL_DISTANCE);

		assertTrue("a range whose minimum exceeds its maximum clamps everything to one value",
			EntourageSettings.MIN_FOLLOW_DISTANCE < EntourageSettings.MAX_FOLLOW_DISTANCE);
		assertTrue(EntourageSettings.MIN_RECALL_DISTANCE < EntourageSettings.MAX_RECALL_DISTANCE);
		assertTrue("a follower must never be told to stand further out than it is recalled from",
			EntourageSettings.MAX_FOLLOW_DISTANCE < EntourageSettings.MIN_RECALL_DISTANCE);
	}
}
