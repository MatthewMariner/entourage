package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Who speaks, when, and how many at once.
 *
 * <p>The followers here are real ones bound to real {@code RuneLiteObject}s, because the
 * pass turns on {@link Follower#isActive()} — which asks the client — and a fake that
 * answered from its own bookkeeping would make "a follower nobody can see is silent"
 * untestable.
 */
public class EntourageChatterTest
{
	private static final WorldPoint STANDING = new WorldPoint(3221, 3218, 0);

	private FakeClient client;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs();
		view = FakeWorldView.around(STANDING);
		client.setTopLevelWorldView(view);
	}

	/** A follower that is spawned and standing on screen, first in the roster. */
	private Follower spawned(EntourageFigure figure)
	{
		return spawned(figure, 0);
	}

	/** The same, in a named roster position — which is what staggers whose turn it is. */
	private Follower spawned(EntourageFigure figure, int index)
	{
		Follower follower = new Follower(client, figure, index, STANDING);
		FollowerAnchor anchor = FollowerAnchor.of(FakePlayer.standingOn(view, STANDING), view);
		follower.onGameTick(anchor, view, FakeConfig.defaults());
		assertTrue("the fixture has to actually spawn", follower.isActive());
		return follower;
	}

	/** Five followers, numbered as the scene numbers them. */
	private List<Follower> fullRoster(EntourageFigure... figures)
	{
		List<Follower> roster = new ArrayList<>(figures.length);
		for (int index = 0; index < figures.length; index++)
		{
			roster.add(spawned(figures[index], index));
		}
		return roster;
	}

	private static EntourageSettings settings()
	{
		return FakeConfig.defaults();
	}

	@Test
	public void aFollowerOnScreenSaysSomethingWithinOneInterval()
	{
		Follower follower = spawned(EntourageFigure.VANNAKA);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();
		EntourageSettings settings = settings();

		String said = null;
		for (int tick = 0; tick < settings.getDialogueIntervalTicks() + 1 && said == null; tick++)
		{
			chatter.onGameTick(roster, settings);
			said = follower.getRemarks().text();
		}

		assertNotNull("a follower that never speaks is a feature that does nothing", said);
		assertTrue("and what it said is one of Vannaka's own lines: " + said,
			FigureLines.of(EntourageFigure.VANNAKA).contains(said));
	}

	@Test
	public void aLineStaysUpForTheDwellAndThenClears()
	{
		Follower follower = spawned(EntourageFigure.ROGUE);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();
		EntourageSettings settings = settings();

		int spokeAt = tickUntilTalking(chatter, roster, settings);
		String line = follower.getRemarks().text();

		for (int i = 1; i < settings.getDialogueDwellTicks(); i++)
		{
			chatter.onGameTick(roster, settings);
			assertEquals("the line must not change mid-dwell", line, follower.getRemarks().text());
		}

		chatter.onGameTick(roster, settings);

		assertFalse("a line that outlasts its dwell is a label, not a remark",
			follower.getRemarks().isTalking());
		assertTrue("this test needs the line to have gone up at all", spokeAt > 0);
	}

	/**
	 * <b>The hard off switch clears what is up rather than merely stopping the next
	 * one.</b> The difference is "off" against "off in a minute", which is the complaint
	 * this whole feature is shaped around avoiding.
	 */
	@Test
	public void theOffSwitchEmptiesWhateverIsAlreadyOnScreen()
	{
		Follower follower = spawned(EntourageFigure.ROGUE);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();

		tickUntilTalking(chatter, roster, settings());

		int talking = chatter.onGameTick(roster, new FakeConfig().setDialogue(false).settings());

		assertEquals(0, talking);
		assertFalse(follower.getRemarks().isTalking());
		assertNull(follower.getRemarks().text());
	}

	/**
	 * <b>Two claims, and the second one needs more than a tick.</b> A line already up on a
	 * follower that has left the screen is cleared, and — this is the half a single tick
	 * cannot show — one that has left the screen never starts a new line either. The first
	 * version of this test ticked once, which is a tick the follower was almost certainly
	 * not due on: deleting the {@code isActive()} check from the pass that starts lines
	 * left it green.
	 */
	@Test
	public void aFollowerThatIsNotOnScreenNeitherSpeaksNorKeepsALine()
	{
		Follower follower = spawned(EntourageFigure.ROGUE);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();
		EntourageSettings settings = settings();

		tickUntilTalking(chatter, roster, settings);

		// Taken off the screen, and then the line put back by hand — so that the only
		// thing between a stale line and text drawn over empty ground is the chatter's own
		// isActive() check. That despawn() clears the line itself is a separate promise,
		// pinned in FollowerTest; without this the two would be indistinguishable.
		follower.despawn();
		assertFalse("the fixture has to actually despawn it", follower.isActive());
		follower.getRemarks().say(0, 1000, FigureLines.of(EntourageFigure.ROGUE));
		assertTrue("and the line has to be back up", follower.getRemarks().isTalking());

		int talking = chatter.onGameTick(roster, settings);

		assertEquals(0, talking);
		assertFalse("a figure the client is not drawing is not saying anything",
			follower.getRemarks().isTalking());

		// Long enough to cover several of its turns to speak, so "it was not due" cannot
		// be why it stayed quiet.
		for (int tick = 0; tick < settings.getDialogueIntervalTicks() * 3; tick++)
		{
			assertEquals("a figure nobody can see must not start talking either",
				0, chatter.onGameTick(roster, settings));
			assertFalse(follower.getRemarks().isTalking());
		}
	}

	/**
	 * <b>The cap, which is the reason this class exists.</b> Written against the full
	 * roster of five, at the tightest cadence the settings allow, where each of them wants
	 * to be talking for nine ticks in every ten and an overlap is a certainty rather than a
	 * possibility. Without a cap this is five lines stacked over five heads standing within
	 * two tiles of each other — the wall of text {@code ../lively-cities} shipped a dial to
	 * prevent.
	 *
	 * <p><b>Five rather than the two this used to use.</b> Two followers is a fixture that
	 * cannot tell a cap of one from a cap of two, and a cap of two at five followers is a
	 * pair of lines drawn over adjacent heads — which is the failure the cap is for. The
	 * roster is also numbered 0..4, as the scene numbers it, because that is what staggers
	 * whose turn it is; a fixture that built five followers all at index zero would be
	 * measuring a much easier problem.
	 *
	 * <p><b>The number below is a literal, and that is the whole point of an earlier
	 * revision.</b> It used to read {@code talking <= EntourageChatter.MAX_CONCURRENT_LINES}
	 * — an assertion that takes its expected value from the constant it is checking, so it
	 * proves the implementation honours whatever the constant says and nothing about what
	 * the constant says. Setting {@code MAX_CONCURRENT_LINES} to 99 left it green: two
	 * followers talking over each other, the wall of text on screen, and a test named for
	 * preventing exactly that passing. A literal cannot be moved by the mutation it exists
	 * to catch. The same reasoning is why {@code EntourageSceneTest.ROSTER_SIZE} is written
	 * out rather than derived.
	 */
	@Test
	public void neverMoreThanOneFollowerIsTalkingAtOnce()
	{
		List<Follower> roster = fullRoster(EntourageFigure.ROGUE, EntourageFigure.HANS,
			EntourageFigure.VANNAKA, EntourageFigure.PIRATE, EntourageFigure.TURAEL);
		EntourageChatter chatter = new EntourageChatter();

		EntourageSettings tightest = new FakeConfig()
			.setDialogueIntervalTicks(EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS)
			.setDialogueDwellTicks(EntourageSettings.MAX_DIALOGUE_DWELL_TICKS)
			.settings();

		assertTrue("this fixture needs a dwell that fills most of the interval",
			tightest.getDialogueDwellTicks() >= tightest.getDialogueIntervalTicks() - 1);

		int spoke = 0;
		for (int tick = 0; tick < 500; tick++)
		{
			int talking = chatter.onGameTick(roster, tightest);

			// A literal, not the constant — see the javadoc.
			assertTrue("two followers were talking at once at tick " + tick, talking <= 1);

			int actuallyUp = 0;
			for (Follower follower : roster)
			{
				if (follower.getRemarks().isTalking())
				{
					actuallyUp++;
				}
			}

			// This one is data-driven on purpose: the claim is that the count handed back
			// is the count on screen, whatever either of them is.
			assertEquals("the count returned has to be the count on screen", talking, actuallyUp);
			assertTrue("and two lines are two lines however they were counted", actuallyUp <= 1);
			spoke += talking;
		}

		assertTrue("this fixture has to produce some talking to be measuring anything", spoke > 0);
	}

	/**
	 * <b>What the cap is set to, pinned separately and with a literal.</b>
	 *
	 * <p>The test above says "at most one follower talks" as a fact about behaviour; this
	 * says the shipped constant agrees with it. Raising the cap is a perfectly reasonable
	 * thing to do when the roster grows — it should just be a deliberate act that turns
	 * two tests red and makes somebody re-read what the behavioural one is asserting,
	 * rather than a one-character edit that quietly widens a guarantee.
	 *
	 * <p><b>This assertion alone would not be enough.</b> A {@code static final int} is a
	 * compile-time constant, so its value is inlined into this class file at compilation,
	 * and whether a mutation of it can reach here at all depends on the build recompiling
	 * the test source set — a property of Gradle rather than of the code. Measured rather
	 * than assumed: setting the constant to 99 and running {@code ./gradlew test} does
	 * turn this red, so the incremental compiler does recompile dependents when a constant
	 * changes. The behavioural literal above has no such dependency either way — it fails
	 * because two figures are talking, not because a number moved.
	 */
	@Test
	public void theShippedCapIsOneVoiceAtATime()
	{
		assertEquals(1, EntourageChatter.MAX_CONCURRENT_LINES);
	}

	/**
	 * Every follower gets turns rather than the first in the list holding the slot forever.
	 *
	 * <p>With a cap of one and five followers, the way this goes wrong is not subtle: if
	 * their turns to speak fell on the same tick, follower zero would win the pass every
	 * time and the other four would be figures with no dialogue for the whole session.
	 * That is what {@code FollowerRemarks}' per-position stagger is for, and this is where
	 * it is checked end to end.
	 */
	@Test
	public void theCapDoesNotBelongToWhicheverFollowerIsFirstInTheRoster()
	{
		List<Follower> roster = fullRoster(EntourageFigure.ROGUE, EntourageFigure.HANS,
			EntourageFigure.VANNAKA, EntourageFigure.PIRATE, EntourageFigure.TURAEL);
		EntourageChatter chatter = new EntourageChatter();
		EntourageSettings settings = settings();

		boolean[] spoke = new boolean[roster.size()];
		for (int tick = 0; tick < settings.getDialogueIntervalTicks() * 8; tick++)
		{
			chatter.onGameTick(roster, settings);
			for (int index = 0; index < roster.size(); index++)
			{
				spoke[index] |= roster.get(index).getRemarks().isTalking();
			}
		}

		for (int index = 0; index < spoke.length; index++)
		{
			assertTrue("follower " + index + " never gets a word in", spoke[index]);
		}
	}

	/**
	 * <b>And the same, for the roster a user can build by accident: five copies of one
	 * figure.</b> The line stream and the stagger are seeded from the figure's name, so
	 * five Rogues are five identical seeds unless the roster position is mixed in — and
	 * then the cap of one means follower zero speaks forever and the other four never do.
	 * The test above cannot see it, because its five figures are five different names.
	 */
	@Test
	public void fiveCopiesOfOneFigureStillTakeTurns()
	{
		List<Follower> roster = fullRoster(EntourageFigure.ROGUE, EntourageFigure.ROGUE,
			EntourageFigure.ROGUE, EntourageFigure.ROGUE, EntourageFigure.ROGUE);
		EntourageChatter chatter = new EntourageChatter();
		EntourageSettings settings = settings();

		boolean[] spoke = new boolean[roster.size()];
		for (int tick = 0; tick < settings.getDialogueIntervalTicks() * 8; tick++)
		{
			chatter.onGameTick(roster, settings);
			for (int index = 0; index < roster.size(); index++)
			{
				spoke[index] |= roster.get(index).getRemarks().isTalking();
			}
		}

		for (int index = 0; index < spoke.length; index++)
		{
			assertTrue("copy " + index + " of the same figure never gets a word in", spoke[index]);
		}
	}

	/**
	 * The custom-lines box replaces the presets — the choice is documented on
	 * {@link EntourageSettings#linesFor} and this is where it is enforced end to end.
	 */
	@Test
	public void aFollowerSaysTheUsersOwnLinesWhenThereAreSome()
	{
		Follower follower = spawned(EntourageFigure.VANNAKA);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();
		// One line, so the assertion is about replacement rather than about which of
		// several was drawn. What a comma does to that box is pinned in
		// EntourageSettingsTest.
		EntourageSettings mine = new FakeConfig()
			.setDialogueLines("Mind the step")
			.settings();

		tickUntilTalking(chatter, roster, mine);

		assertEquals("Mind the step", follower.getRemarks().text());
		assertFalse("and not one of the figure's own",
			FigureLines.of(EntourageFigure.VANNAKA).contains(follower.getRemarks().text()));
	}

	@Test
	public void theCadenceIsTheIntervalSetting()
	{
		Follower follower = spawned(EntourageFigure.ROGUE);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();

		EntourageSettings brisk = new FakeConfig()
			.setDialogueIntervalTicks(20)
			.setDialogueDwellTicks(EntourageSettings.MIN_DIALOGUE_DWELL_TICKS)
			.settings();

		int lines = 0;
		String previous = null;
		for (int tick = 0; tick < 400; tick++)
		{
			chatter.onGameTick(roster, brisk);
			String now = follower.getRemarks().text();
			if (now != null && !now.equals(previous))
			{
				lines++;
			}
			previous = now;
		}

		assertEquals("four hundred ticks at one line per twenty is twenty lines", 20, lines);
	}

	/**
	 * A scene load starts a fresh phase and leaves nothing on screen. Without the reset a
	 * follower would inherit a cadence from the world it was in before, and — worse — a
	 * line said in it.
	 */
	@Test
	public void resettingForgetsBothTheClockAndTheLine()
	{
		Follower follower = spawned(EntourageFigure.ROGUE);
		List<Follower> roster = Collections.singletonList(follower);
		EntourageChatter chatter = new EntourageChatter();

		tickUntilTalking(chatter, roster, settings());
		assertTrue(chatter.getTick() > 0);

		chatter.reset(roster);

		assertEquals(0, chatter.getTick());
		assertFalse(follower.getRemarks().isTalking());
	}

	private static int tickUntilTalking(EntourageChatter chatter, List<Follower> roster,
		EntourageSettings settings)
	{
		for (int tick = 1; tick <= settings.getDialogueIntervalTicks() * 4; tick++)
		{
			chatter.onGameTick(roster, settings);
			if (roster.get(0).getRemarks().isTalking())
			{
				return tick;
			}
		}

		throw new AssertionError("the fixture never produced a line to work with");
	}
}
