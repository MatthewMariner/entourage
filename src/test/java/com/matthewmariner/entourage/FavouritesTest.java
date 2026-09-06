package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The starred ids: what a stored value means, and what a broken one means.
 *
 * <p>Most of this file is about input nobody typed on purpose. That is deliberate — the
 * value comes out of a config proxy, which means it can equally have come from a
 * hand-edited profile, a profile synced off another install, or a build of this plugin that
 * wrote a different shape. The rule this repo holds itself to is that a config blob is
 * coerced field by field rather than trusted wholesale, and the only way to know that
 * happened is to hand it every wrong thing on purpose.
 */
public class FavouritesTest
{
	// --- what a well-formed value means ----------------------------------------

	@Test
	public void anEmptyProfileHasNoFavourites()
	{
		assertTrue(Favourites.parse(null).isEmpty());
		assertTrue(Favourites.parse("").isEmpty());
		assertTrue("whitespace is not a favourite", Favourites.parse("   ").isEmpty());
	}

	@Test
	public void aListOfIdsIsReadInTheOrderItIsWritten()
	{
		assertEquals(Arrays.asList(3598, 4931, 7), Favourites.parse("3598,4931,7"));
	}

	@Test
	public void oneIdIsAList()
	{
		assertEquals(Collections.singletonList(3598), Favourites.parse("3598"));
	}

	/** Written in the shape it is read, so a round trip is the identity. */
	@Test
	public void whatIsWrittenIsWhatIsRead()
	{
		List<Integer> ids = Arrays.asList(3598, 4931, 7);

		assertEquals("3598,4931,7", Favourites.format(ids));
		assertEquals(ids, Favourites.parse(Favourites.format(ids)));
	}

	@Test
	public void anEmptyListFormatsToNothingAtAll()
	{
		assertEquals("", Favourites.format(Collections.emptyList()));
	}

	// --- what a broken value means -----------------------------------------------

	/**
	 * <b>A trailing comma is what a hand edit produces and what an older writer might have
	 * left.</b> It has to cost nothing, because the alternative — a parse that gives up — loses
	 * every favourite to one character.
	 */
	@Test
	public void aTrailingOrDoubledCommaCostsNothing()
	{
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse("3598,4931,"));
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse("3598,,4931"));
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse(",3598,4931"));
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse(",,3598,,,4931,,"));
	}

	/** Somebody editing a profile by hand puts a space after a comma. */
	@Test
	public void whitespaceAroundAnIdIsTrimmedRatherThanFatal()
	{
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse(" 3598 , 4931 "));
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse("\t3598,\n4931"));
	}

	/**
	 * <b>The entry that is not a number costs itself and nothing else.</b> This is the case
	 * the whole "ids, not names" decision exists for: if favourites were stored as names, a
	 * name containing a comma would split into pieces exactly like this, and there would be no
	 * way to tell the wreckage from a deliberate list.
	 */
	@Test
	public void somethingThatIsNotANumberIsSkippedAndTheRestSurvives()
	{
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse("3598,Gummy,4931"));
		assertEquals(Arrays.asList(3598, 4931),
			Favourites.parse("3598,Guard, Falador,4931"));
		assertEquals("a decimal is not an id either",
			Arrays.asList(3598, 4931), Favourites.parse("3598,45.5,4931"));
	}

	/**
	 * <b>A number too big for an {@code int} throws inside {@code Integer.parseInt}</b>, which
	 * is the one refusal here that is a thrown exception rather than a comparison — so it is
	 * the one most likely to escape if the catch were ever narrowed.
	 *
	 * <p><b>The payload has to actually reach the parse.</b> This used to read
	 * {@code "99999999999999999999"} — twenty characters — which {@code looksLikeAnInt}'s own
	 * {@code length > 11} now refuses lexically, before {@code Integer.parseInt} is ever
	 * called; deleting the {@code try}/{@code catch} this test exists to guard left it PASSED
	 * regardless, because nothing reached the code the catch protects. {@code "2147483648"}
	 * is ten characters — inside the length allowance, genuinely an overflow, and the one
	 * shape {@code looksLikeAnInt}'s own javadoc names as a residual it cannot filter out
	 * without doing the parse itself.
	 */
	@Test
	public void aNumberTooBigForAnIntIsSkippedRatherThanThrown()
	{
		assertEquals(Arrays.asList(3598, 4931),
			Favourites.parse("3598,2147483648,4931"));
	}

	/**
	 * Zero is this plugin's own word for "no typed id" — see {@link FollowerBody#NO_CUSTOM_NPC}
	 * — so a zero on the favourites list would be a star on nothing, and a negative is not a
	 * file id at all.
	 */
	@Test
	public void zeroAndNegativesAreNotFavourites()
	{
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse("3598,0,-7,4931"));
		assertTrue(Favourites.parse("0").isEmpty());
		assertTrue(Favourites.parse("-1").isEmpty());
	}

	/**
	 * <b>A duplicate keeps its first position.</b> The list is written newest-first, so the
	 * first occurrence is the newer one; keeping the later copy instead would silently
	 * reorder the list on every read.
	 */
	@Test
	public void aDuplicateIsDroppedAndTheFirstPositionWins()
	{
		assertEquals(Arrays.asList(3598, 4931), Favourites.parse("3598,4931,3598"));
		assertEquals(Arrays.asList(1, 2, 3), Favourites.parse("1,2,1,3,2,1"));
	}

	/** A profile written before the cap moved, or edited by somebody with a long list. */
	@Test
	public void aStoredListPastTheCapIsCutToTheCap()
	{
		StringBuilder raw = new StringBuilder();
		for (int index = 0; index < Favourites.MAX * 3; index++)
		{
			raw.append(1_000 + index).append(',');
		}

		List<Integer> parsed = Favourites.parse(raw.toString());

		assertEquals(Favourites.MAX, parsed.size());
		assertEquals("and it keeps the front of the list, which is the newest end",
			Integer.valueOf(1_000), parsed.get(0));
		assertEquals(Integer.valueOf(1_000 + Favourites.MAX - 1), parsed.get(Favourites.MAX - 1));
	}

	/**
	 * <b>The cap counts favourites, not commas.</b> A stored value padded with junk must still
	 * yield a full list of the ids that are actually in it — a cap that counted skipped pieces
	 * would quietly shorten everybody's list the first time a profile picked up a stray comma.
	 */
	@Test
	public void junkDoesNotCountAgainstTheCap()
	{
		StringBuilder raw = new StringBuilder(",,,");
		for (int index = 0; index < Favourites.MAX; index++)
		{
			raw.append(1_000 + index).append(",not-an-id,,");
		}

		assertEquals(Favourites.MAX, Favourites.parse(raw.toString()).size());
	}

	@Test
	public void theParsedListCannotBeEditedUnderTheNextReader()
	{
		try
		{
			Favourites.parse("3598").add(1);
			fail("the parsed list must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// what an unmodifiable list does
		}
	}

	// --- starring and un-starring --------------------------------------------------

	/**
	 * <b>New entries go to the front.</b> The list is capped, so something has to fall off
	 * when it is full — and the only defensible thing to drop is the one starred longest ago.
	 * A cap that discarded the id you just pressed the star on would be a control that
	 * visibly did nothing.
	 */
	@Test
	public void aNewFavouriteGoesToTheFront()
	{
		assertEquals(Arrays.asList(7, 3598, 4931),
			Favourites.with(Arrays.asList(3598, 4931), 7));
	}

	@Test
	public void starringSomethingAlreadyStarredMovesItToTheFrontRatherThanDuplicatingIt()
	{
		assertEquals(Arrays.asList(4931, 3598, 7),
			Favourites.with(Arrays.asList(3598, 4931, 7), 4931));
		assertEquals("and the list is no longer than it was",
			3, Favourites.with(Arrays.asList(3598, 4931, 7), 4931).size());
	}

	/** Starring the one already at the front is a no-op that does not reorder anything. */
	@Test
	public void starringTheFrontOfTheListChangesNothing()
	{
		assertEquals(Arrays.asList(3598, 4931),
			Favourites.with(Arrays.asList(3598, 4931), 3598));
	}

	/**
	 * <b>At the cap, the oldest falls off — and it is the oldest and not the newest.</b>
	 * Written against a full list built in a known order, so a mutation that dropped from the
	 * wrong end goes red rather than merely changing which id is missing.
	 */
	@Test
	public void starringAtTheCapDropsTheOldest()
	{
		List<Integer> full = full();

		List<Integer> next = Favourites.with(full, 9_999);

		assertEquals(Favourites.MAX, next.size());
		assertEquals("the new one is at the front", Integer.valueOf(9_999), next.get(0));
		assertEquals("the one starred longest ago is gone",
			full.subList(0, Favourites.MAX - 1),
			next.subList(1, Favourites.MAX));
		assertFalse(next.contains(full.get(Favourites.MAX - 1)));
	}

	/** Zero and below are what "no typed id" looks like, so a star on one is not a thing. */
	@Test
	public void starringNothingChangesNothing()
	{
		List<Integer> ids = Arrays.asList(3598, 4931);

		assertEquals(ids, Favourites.with(ids, 0));
		assertEquals(ids, Favourites.with(ids, -7));
	}

	@Test
	public void unstarringTakesOneOutAndLeavesTheOrder()
	{
		assertEquals(Arrays.asList(3598, 7),
			Favourites.without(Arrays.asList(3598, 4931, 7), 4931));
	}

	/** A second click on a row that has already gone is a stale gesture, not a fault. */
	@Test
	public void unstarringSomethingThatIsNotThereChangesNothing()
	{
		assertEquals(Arrays.asList(3598, 4931),
			Favourites.without(Arrays.asList(3598, 4931), 7));
		assertTrue(Favourites.without(Collections.emptyList(), 7).isEmpty());
	}

	@Test
	public void unstarringTheLastOneEmptiesTheList()
	{
		assertTrue(Favourites.without(Collections.singletonList(3598), 3598).isEmpty());
	}

	/**
	 * <b>The cap, as a literal.</b> Everything above is written in terms of
	 * {@code Favourites.MAX}, which is right for a behavioural test and useless as a guard on
	 * the value: raising it to a thousand moves every one of those loops with it and nothing
	 * goes red. This is the one place the number is written out, and it is the number the
	 * README quotes to the user.
	 */
	@Test
	public void theCapIsTheOneTheReadmeQuotes()
	{
		assertEquals("the README says twenty", 20, Favourites.MAX);
		assertEquals("and the separator is the same comma the lines box uses",
			',', Favourites.SEPARATOR);
		assertEquals("which is the whole reason favourites are ids and not names",
			Favourites.SEPARATOR, EntourageSettings.CUSTOM_LINE_SEPARATOR);
	}

	// --- The lexical pre-check must not change a single answer --------------

	/**
	 * What {@link Favourites#add} did before the lexical pre-check existed: trim, parse,
	 * floor at {@link FollowerBody#NO_CUSTOM_NPC} — no pre-check at all. Every payload below
	 * is run through this as well as through the real {@link Favourites#parse}, so "the
	 * pre-check refuses only what parseInt would also refuse" is something a test can fail
	 * rather than something the javadoc merely asserts.
	 *
	 * @return the id the pre-fix code would have kept, or {@code null} for one it would have
	 * skipped
	 */
	private static Integer oldRule(String piece)
	{
		String trimmed = piece.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}

		int npcId;
		try
		{
			npcId = Integer.parseInt(trimmed);
		}
		catch (NumberFormatException notANumber)
		{
			return null;
		}

		return npcId <= FollowerBody.NO_CUSTOM_NPC ? null : npcId;
	}

	/** @return the one id {@code Favourites.parse(piece)} kept, or {@code null} if it kept none */
	private static Integer onlyId(String piece)
	{
		List<Integer> parsed = Favourites.parse(piece);
		return parsed.isEmpty() ? null : parsed.get(0);
	}

	/**
	 * <b>Every payload a reviewer probed the pre-check with, old rule against new, one piece
	 * at a time.</b> A pre-check that refused something {@code parseInt} would have accepted
	 * would silently drop a favourite nobody meant to lose; the only thing it may add is
	 * refusing junk without the throw, and that does not show up here because both rules
	 * already agree the junk is refused — only the cost of saying so differs.
	 *
	 * <p><b>{@code "+2147483647"} and {@code "02147483647"} are the two that actually pin the
	 * eleven.</b> Both are eleven characters and both parse to a value this plugin keeps —
	 * unlike {@code "-2147483648"} above, which the {@code NO_CUSTOM_NPC} floor would discard
	 * either way. {@code length > 11} tightened to {@code length > 10} leaves every other
	 * probe in this array agreeing with the old rule and only these two disagreeing — the old
	 * rule still accepts them, a length-10 pre-check silently refuses them — which is what
	 * makes that one-character change visible here rather than merely dangerous in theory.
	 */
	@Test
	public void thePreCheckAgreesWithTheOldRuleOnEveryProbedPiece()
	{
		String[] pieces = {
			"", "   ", "--5", "0x10", "2147483648", "+5", "2147483647", "0003598", "-0",
			"35 98", "<b>3598</b>", " 3598 ", "-2147483648",
			"+2147483647", "02147483647",
		};

		for (String piece : pieces)
		{
			assertEquals("\"" + piece + "\" must come out the same under both rules",
				oldRule(piece), onlyId(piece));
		}
	}

	/**
	 * A whole stored value made of nothing but separators splits into empty pieces, and an
	 * empty piece was already refused before the trimmed pre-check ever runs — this is the
	 * one probed payload the pre-check cannot take credit or blame for either way.
	 */
	@Test
	public void aValueThatIsNothingButSeparatorsIsStillEmpty()
	{
		assertTrue(Favourites.parse(",,,,,,").isEmpty());
	}

	/**
	 * <b>The case a narrower pre-check would get wrong.</b> {@code Integer.parseInt} accepts
	 * any Unicode decimal digit through {@code Character.digit}, so Arabic-Indic and
	 * full-width digits both parse to 3598 today, and they still must after this fix —
	 * {@code Character.isDigit} is true for both, which is why the pre-check uses it rather
	 * than the {@code c >= '0' && c <= '9'} an ASCII-only version would reach for; that
	 * narrower check is false for both and would start refusing ids this plugin has always
	 * accepted.
	 */
	@Test
	public void thePreCheckStillAcceptsEveryDigitAlphabetParseIntDoes()
	{
		String arabicIndic3598 = "٣٥٩٨";
		String fullWidth3598 = "３５９８";

		assertEquals(Integer.valueOf(3598), onlyId(arabicIndic3598));
		assertEquals(Integer.valueOf(3598), onlyId(fullWidth3598));

		// And the old rule agrees on both — parseInt always accepted these, so a pre-check
		// that also accepts them has changed nothing.
		assertEquals(oldRule(arabicIndic3598), onlyId(arabicIndic3598));
		assertEquals(oldRule(fullWidth3598), onlyId(fullWidth3598));
	}

	/** @return a full list, in a known order, oldest at the end */
	private static List<Integer> full()
	{
		List<Integer> ids = new java.util.ArrayList<>();
		for (int index = 0; index < Favourites.MAX; index++)
		{
			ids.add(1_000 + index);
		}
		return ids;
	}
}
