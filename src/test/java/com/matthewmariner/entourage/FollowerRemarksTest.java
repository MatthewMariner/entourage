package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One follower's line: which one, and for how long.
 *
 * <p>Nothing here touches the client, so a thousand ticks of cadence costs nothing and
 * every claim about the randomness is a claim about a seeded stream rather than about
 * luck.
 */
public class FollowerRemarksTest
{
	private static final List<String> FOUR =
		Collections.unmodifiableList(Arrays.asList("one", "two", "three", "four"));

	private static FollowerRemarks remarks()
	{
		return new FollowerRemarks(EntourageFigure.ROGUE, 0);
	}

	@Test
	public void aFollowerStartsWithNothingToSay()
	{
		FollowerRemarks remarks = remarks();

		assertFalse(remarks.isTalking());
		assertNull(remarks.text());
	}

	@Test
	public void aLineStaysUpForItsDwellAndNotATickLonger()
	{
		FollowerRemarks remarks = remarks();
		remarks.say(10, 8, FOUR);

		assertTrue(remarks.isTalking());
		assertFalse("the tick before it is due to end", remarks.expire(17));
		assertTrue("still up", remarks.isTalking());

		assertTrue("and gone on the tick it expires", remarks.expire(18));
		assertFalse(remarks.isTalking());
		assertNull(remarks.text());
	}

	@Test
	public void expiringSomethingThatIsNotThereIsHarmless()
	{
		assertFalse(remarks().expire(1000));
	}

	@Test
	public void clearingStopsItTalkingAtOnce()
	{
		FollowerRemarks remarks = remarks();
		remarks.say(0, 1000, FOUR);
		assertTrue(remarks.isTalking());

		remarks.clear();

		assertFalse(remarks.isTalking());
		assertNull(remarks.text());
	}

	@Test
	public void everythingItSaysIsOneOfTheLinesItWasGiven()
	{
		FollowerRemarks remarks = remarks();

		for (int i = 0; i < 500; i++)
		{
			remarks.say(i, 1, FOUR);
			assertTrue("said something that was not on the list: " + remarks.text(),
				FOUR.contains(remarks.text()));
		}
	}

	/**
	 * <b>Never the same line twice running.</b> Not "rarely": the pick draws from the
	 * other lines and skips the last one, so a repeat is impossible rather than unlikely.
	 * A follower with four lines that said one of them twice in a row reads as the feature
	 * being broken rather than as a coincidence — and with a re-roll instead of a skip
	 * this test would pass most of the time and fail on somebody else's machine.
	 */
	@Test
	public void itNeverSaysTheSameLineTwiceRunning()
	{
		FollowerRemarks remarks = remarks();
		remarks.say(0, 1, FOUR);
		String previous = remarks.text();

		for (int i = 1; i < 2000; i++)
		{
			remarks.say(i, 1, FOUR);
			assertNotEquals("said \"" + previous + "\" twice in a row at " + i,
				previous, remarks.text());
			previous = remarks.text();
		}
	}

	@Test
	public void everyLineComesUpSoonerOrLater()
	{
		FollowerRemarks remarks = remarks();
		Set<String> heard = new HashSet<>();

		for (int i = 0; i < 200; i++)
		{
			remarks.say(i, 1, FOUR);
			heard.add(remarks.text());
		}

		assertEquals("a line nothing ever picks is a line nobody wrote", FOUR.size(), heard.size());
	}

	/** A single line is all it has, so it says that — the no-repeat rule cannot apply. */
	@Test
	public void oneLineIsSaidOverAndOver()
	{
		FollowerRemarks remarks = remarks();
		List<String> only = Collections.singletonList("hello");

		remarks.say(0, 1, only);
		remarks.say(1, 1, only);

		assertEquals("hello", remarks.text());
	}

	/**
	 * A user emptying the custom-lines box while the follower is standing there leaves the
	 * remembered index pointing past the end of the new list. That must be a fresh draw
	 * rather than an {@code IndexOutOfBoundsException} inside a game-tick handler.
	 */
	@Test
	public void aShorterListThanLastTimeIsHandledRatherThanIndexedPastTheEnd()
	{
		FollowerRemarks remarks = remarks();

		// Long enough that the remembered index is very likely past the end of the pair
		// below, and repeated so that it certainly is at least once.
		List<String> many = new ArrayList<>();
		for (int i = 0; i < 12; i++)
		{
			many.add("line " + i);
		}
		List<String> two = Arrays.asList("a", "b");

		for (int i = 0; i < 200; i++)
		{
			remarks.say(i * 2, 1, many);
			remarks.say(i * 2 + 1, 1, two);
			assertTrue(two.contains(remarks.text()));
		}
	}

	@Test
	public void anEmptyListLeavesItSayingNothingRatherThanThrowing()
	{
		FollowerRemarks remarks = remarks();

		remarks.say(0, 10, Collections.emptyList());

		assertFalse(remarks.isTalking());
	}

	/**
	 * <b>The same figure says the same things in the same order every session.</b> Seeded
	 * from the figure rather than from the clock, so a street reads as a place rather than
	 * as a random generator — and so every other test in this file can assert something
	 * about two thousand draws.
	 */
	@Test
	public void theOrderIsTheSameEverySession()
	{
		assertEquals(sequence(new FollowerRemarks(EntourageFigure.VANNAKA, 0), 40),
			sequence(new FollowerRemarks(EntourageFigure.VANNAKA, 0), 40));
	}

	/**
	 * <b>Two figures draw from two streams, and this is the test that is here for a roster
	 * that does not exist yet.</b> With one follower it only says that the seed depends on
	 * the figure; with five it is the difference between five companions and five copies
	 * of one, saying the same line at the same moment forever. A shared {@link
	 * java.util.Random} — or a seed that ignored the figure — passes every other test in
	 * this file.
	 */
	@Test
	public void twoFiguresDoNotSayTheirLinesInLockstep()
	{
		List<String> rogue = sequence(new FollowerRemarks(EntourageFigure.ROGUE, 0), 40);
		List<String> hans = sequence(new FollowerRemarks(EntourageFigure.HANS, 0), 40);

		assertNotEquals("two figures drawing the same sequence is one stream, not two",
			rogue, hans);
	}

	/**
	 * The same claim for whose turn it is to speak. Two followers becoming due on the same
	 * tick would, with a cap of one, mean the second one never spoke at all.
	 */
	@Test
	public void twoFiguresAreNotDueOnTheSameTicks()
	{
		FollowerRemarks rogue = remarks();
		FollowerRemarks hans = new FollowerRemarks(EntourageFigure.HANS, 0);
		int interval = EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS;

		boolean everDiffered = false;
		for (int tick = 0; tick < interval * 4; tick++)
		{
			if (rogue.dueAt(tick, interval) != hans.dueAt(tick, interval))
			{
				everDiffered = true;
			}
		}

		assertTrue("two figures due on exactly the same ticks are one figure twice", everDiffered);
	}

	/**
	 * <b>The same claim again, for the roster the user can actually build: five copies of
	 * one figure.</b> Nothing stops somebody putting the Rogue in all five slots, and the
	 * figure's name hash is the same number five times — so on the figure alone all five
	 * would share one stream and say the same line at the same moment forever. The two
	 * tests above cannot see it, because both of them use two <i>different</i> figures.
	 */
	@Test
	public void twoCopiesOfOneFigureDoNotSayTheirLinesInLockstep()
	{
		List<String> first = sequence(new FollowerRemarks(EntourageFigure.ROGUE, 0), 40);
		List<String> second = sequence(new FollowerRemarks(EntourageFigure.ROGUE, 1), 40);

		assertNotEquals("two Rogues drawing the same sequence is one stream, not two",
			first, second);
	}

	/**
	 * <b>And whose turn it is to speak, which is the half that actually bites.</b> With
	 * {@link EntourageChatter#MAX_CONCURRENT_LINES} at one, five identical figures becoming
	 * due on exactly the same tick means the first of them takes every turn and the other
	 * four never say a word for the whole session — a bug that looks, from outside, like
	 * four followers with no dialogue.
	 *
	 * <p>Checked at both ends of the cadence: the stagger is taken modulo the interval, so
	 * a stride that separated five followers nicely at a hundred ticks could still fold
	 * them together at ten.
	 */
	@Test
	public void fiveCopiesOfOneFigureAreDueOnFiveDifferentTicks()
	{
		for (int interval : new int[]{
			EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS,
			EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS,
			EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS})
		{
			Set<Integer> dueTicks = new HashSet<>();

			for (int index = 0; index < EntourageSettings.MAX_FOLLOWERS; index++)
			{
				FollowerRemarks remarks = new FollowerRemarks(EntourageFigure.ROGUE, index);

				Integer due = null;
				for (int tick = 1; tick <= interval && due == null; tick++)
				{
					if (remarks.dueAt(tick, interval))
					{
						due = tick;
					}
				}

				assertNotNull("copy " + index + " is never due at interval " + interval, due);
				assertTrue("copies " + index + " and an earlier one are due on tick " + due
					+ " at interval " + interval, dueTicks.add(due));
			}

			assertEquals(EntourageSettings.MAX_FOLLOWERS, dueTicks.size());
		}
	}

	/**
	 * The first slot's stream is exactly what a lone follower's always was. The stagger is
	 * added to the figure's hash, so position zero adds nothing — which is what keeps a
	 * single follower's order of lines the same as it was before the roster existed.
	 */
	@Test
	public void theFirstSlotIsUnchangedByTheRosterExisting()
	{
		assertEquals(sequence(new FollowerRemarks(EntourageFigure.VANNAKA, 0), 40),
			sequence(new FollowerRemarks(EntourageFigure.VANNAKA, 0), 40));
		assertNotEquals("and the second slot is not the first one twice",
			sequence(new FollowerRemarks(EntourageFigure.VANNAKA, 0), 40),
			sequence(new FollowerRemarks(EntourageFigure.VANNAKA, 1), 40));
	}

	/**
	 * Due exactly once per interval — no more, which would be a follower talking over
	 * itself, and no less, which would be a cadence setting that does not mean what it
	 * says.
	 */
	@Test
	public void aFollowerIsDueExactlyOncePerInterval()
	{
		FollowerRemarks remarks = remarks();
		int interval = 25;
		int due = 0;

		for (int tick = 0; tick < interval * 40; tick++)
		{
			if (remarks.dueAt(tick, interval))
			{
				due++;
			}
		}

		assertEquals(40, due);
	}

	/**
	 * Every figure gets a turn, whichever way its hash fell.
	 *
	 * <p><b>And a correction, because a mutation pass earned it.</b> This javadoc used to
	 * claim that {@code Math.floorMod} was load-bearing here — that with a plain {@code %}
	 * a figure whose hash is positive would never become due. That is wrong, and replacing
	 * the floor-mod with a remainder leaves every test in this file green: the result is
	 * only ever compared against zero, and {@code a % n == 0} is exactly
	 * {@code floorMod(a, n) == 0} for any positive {@code n}. The floor-mod stays because
	 * it says what is meant and because it is the version that survives somebody changing
	 * the comparison, not because a test can tell the difference. What this test really
	 * holds is the property in its name.
	 */
	@Test
	public void everyFigureBecomesDueWithinOneIntervalOfStarting()
	{
		int interval = EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS;

		for (EntourageFigure figure : EntourageFigure.values())
		{
			FollowerRemarks remarks = new FollowerRemarks(figure, 0);
			boolean due = false;

			for (int tick = 1; tick <= interval; tick++)
			{
				due |= remarks.dueAt(tick, interval);
			}

			assertTrue(figure + " is never due — its stagger fell outside the interval", due);
		}
	}

	private static List<String> sequence(FollowerRemarks remarks, int draws)
	{
		List<String> said = new ArrayList<>(draws);
		for (int i = 0; i < draws; i++)
		{
			remarks.say(i, 1, FOUR);
			said.add(remarks.text());
		}
		return said;
	}
}
