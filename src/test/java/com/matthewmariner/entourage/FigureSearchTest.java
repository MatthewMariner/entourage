package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Typing part of a name and getting the figure.
 *
 * <p>The queries below are the ones a person actually types — a half word, a word from the
 * middle of a name, and a name misremembered by a letter — rather than strings chosen to
 * suit the implementation.
 */
public class FigureSearchTest
{
	private static List<EntourageFigure> matching(String query)
	{
		return FigureSearch.matching(query);
	}

	private static EntourageFigure first(String query)
	{
		List<EntourageFigure> found = matching(query);
		assertFalse("\"" + query + "\" found nothing at all", found.isEmpty());
		return found.get(0);
	}

	/**
	 * An empty box is a browse, not a failed search. A panel that showed nothing until you
	 * typed would hide the list somebody opened it to read.
	 */
	@Test
	public void anEmptyQueryIsEveryFigureInTheOrderTheDropdownListsThem()
	{
		assertEquals(EntourageFigure.values().length, matching("").size());
		assertEquals(EntourageFigure.values().length, matching(null).size());
		assertEquals(EntourageFigure.values().length, matching("   ").size());

		assertSame("the enum's own order, which is the settings dropdown's order",
			EntourageFigure.values()[0], matching("").get(0));
		assertSame(EntourageFigure.values()[1], matching("").get(1));
	}

	/** Twenty-three is what the README promises, and the search walks all of them. */
	@Test
	public void everyFigureIsReachableByItsOwnName()
	{
		assertEquals("the README says twenty-three figures", 23, EntourageFigure.values().length);

		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertSame(figure.getDisplayName() + " cannot be found by typing its own name",
				figure, first(figure.getDisplayName()));
		}
	}

	/**
	 * "Knight" is GRILL_KNIGHT's label and is also inside "White Knight" and "Elite Black
	 * Knight", both of which come earlier in the enum — so without the tiers the dropdown
	 * order would put one of those first.
	 *
	 * <p>The whole list rather than the first row, because the two behind it are what pins
	 * the <i>tie</i>: the sort is stable so that equally good answers keep the order the
	 * settings dropdown lists them in, and nothing else in this file would notice if they
	 * came back in some other order.
	 */
	@Test
	public void anExactNameOutranksTheLongerNamesThatContainIt()
	{
		assertEquals(Arrays.asList(
				EntourageFigure.GRILL_KNIGHT,
				EntourageFigure.WHITE_KNIGHT,
				EntourageFigure.ELITE_BLACK_KNIGHT),
			matching("Knight"));
	}

	/**
	 * <b>Chosen so that the tier is the only thing that can produce the answer.</b> "kn"
	 * starts "Knight", which is the fourteenth figure in the enum, and sits inside "White
	 * Knight" and "Elite Black Knight", which are the ninth and tenth. Sorted by tier the
	 * answer is Knight; sorted by nothing at all it is White Knight — so a version of this
	 * test built on a query where the best match happened to come first in the enum anyway
	 * could not fail, which is how the first draft of it read.
	 */
	@Test
	public void aPrefixOutranksAMatchInTheMiddleOfAName()
	{
		assertSame(EntourageFigure.GRILL_KNIGHT, first("kn"));
		assertTrue("the longer names are still offered, underneath",
			matching("kn").contains(EntourageFigure.WHITE_KNIGHT));

		// "man" starts nothing and sits inside "Wise Old Man" and "Necromancer".
		List<EntourageFigure> inTheMiddle = matching("man");
		assertTrue(inTheMiddle.contains(EntourageFigure.WISE_OLD_MAN));
		assertTrue(inTheMiddle.contains(EntourageFigure.NECROMANCER));
	}

	@Test
	public void aWordFromTheMiddleOfANameFindsIt()
	{
		assertSame(EntourageFigure.WISE_OLD_MAN, first("old man"));
		assertSame(EntourageFigure.ELITE_BLACK_KNIGHT, first("black"));
		assertSame(EntourageFigure.ZAMORAK_MAGE, first("mage"));
	}

	@Test
	public void searchIsNotCaseSensitiveEitherWay()
	{
		assertSame(EntourageFigure.VANNAKA, first("VANNAKA"));
		assertSame(EntourageFigure.VANNAKA, first("vAnNaKa"));
		assertSame(EntourageFigure.WISE_OLD_MAN, first("  Wise Old Man  "));
	}

	/**
	 * <b>The point of the near tier.</b> Each of these is a name off by one letter, which is
	 * the case a list this short makes most annoying: the figure is plainly in the dropdown
	 * and the box says nothing.
	 */
	@Test
	public void aNameMisrememberedByOneLetterStillFindsTheFigure()
	{
		assertSame("a dropped letter", EntourageFigure.VANNAKA, first("vanaka"));
		assertSame("a wrong vowel", EntourageFigure.DURADEL, first("duradal"));
		assertSame("a dropped letter mid-word", EntourageFigure.SORCERESS, first("sorcress"));
		assertSame("a doubled letter", EntourageFigure.NECROMANCER, first("neccromancer"));
	}

	/**
	 * And half a word with a typo in it, which is what typing fast actually produces.
	 *
	 * <p>"necrp" is the case the <i>opening</i> comparison exists for: it is one edit from
	 * the first five characters of "Necromancer" and seven from the whole word, so a
	 * near-match that only measured whole names would answer nothing. "vanaka" in the test
	 * above is the mirror image — one edit from the whole word and two from its opening —
	 * so between them the two comparisons cannot be removed without something going red.
	 */
	@Test
	public void aHalfTypedNameWithATypoInItStillFindsTheFigure()
	{
		assertSame(EntourageFigure.SORCERESS, first("sorcres"));
		assertSame("a slip in half a word, which no whole-word measure can reach",
			EntourageFigure.NECROMANCER, first("necrp"));
		assertSame(EntourageFigure.NECROMANCER, first("necro"));
		assertSame(EntourageFigure.PALADIN, first("palladin"));
	}

	/**
	 * <b>The floor, exercised at both sides of it.</b> Three characters have too many
	 * neighbours to be a spelling mistake, so "haz" must not become "Hans" — while the same
	 * kind of slip one character longer must.
	 */
	@Test
	public void aQueryTooShortToBeATypoIsNotTreatedAsOne()
	{
		assertEquals("four characters before a typo is forgiven at all — three has too many "
			+ "neighbours to be a spelling mistake", 4, FigureSearch.NEAR_MATCH_MINIMUM);

		assertTrue("\"haz\" is one edit from \"han\" and must not reach Hans",
			matching("haz").isEmpty());
		assertFalse("but four characters is over the floor", matching("hanz").isEmpty());
		assertSame(EntourageFigure.HANS, first("hanz"));
	}

	/**
	 * <b>Two edits are only forgiven once the query is long enough to carry them.</b>
	 * "Turael" mistyped twice in six characters must not match, because at that length two
	 * edits reaches names nobody meant; the same slack over the threshold must.
	 */
	@Test
	public void twoEditsAreForgivenOnlyFromSevenCharacters()
	{
		assertEquals("the threshold the near tier widens at", 7, FigureSearch.TWO_EDITS_FROM);

		assertTrue("six characters, two edits — \"turial\" is not offered Turael",
			matching("turial").isEmpty());
		assertSame("nine characters, two edits — \"necromencor\" is still Necromancer",
			EntourageFigure.NECROMANCER, first("necromencor"));
	}

	/**
	 * <b>A near match never outranks a real one, however close it is</b> — and these two
	 * queries are the ones where that is load-bearing rather than incidental.
	 *
	 * <p>"dura" starts "Duradel", the sixth figure, and is one substitution from "Turael",
	 * the fifth. "manc" sits inside "Necromancer", the eighteenth, and is one substitution
	 * from the opening of "Mazchna", the seventh. In both cases the figure that comes first
	 * in the enum is the wrong answer, so a search that ranked by nothing would hand back a
	 * near miss over a real hit — which is what somebody typing four letters of Duradel's
	 * name would see.
	 */
	@Test
	public void aNearMatchSortsBelowEveryRealMatch()
	{
		assertSame("a prefix beats a one-letter miss", EntourageFigure.DURADEL, first("dura"));
		assertTrue("and the near miss is still offered underneath",
			matching("dura").contains(EntourageFigure.TURAEL));

		assertSame("and so does a match in the middle of a name",
			EntourageFigure.NECROMANCER, first("manc"));
		assertTrue(matching("manc").contains(EntourageFigure.MAZCHNA));

		assertSame("an exact name most of all", EntourageFigure.DURADEL, first("duradel"));
	}

	@Test
	public void aNameNothingAnswersToFindsNothingRatherThanEverything()
	{
		assertTrue(matching("zulrah").isEmpty());
		assertTrue(matching("qqqqqqqq").isEmpty());
	}

	/** The list handed back is the caller's to read and nobody's to edit. */
	@Test
	public void theResultsCannotBeEditedUnderTheNextSearch()
	{
		try
		{
			matching("").add(EntourageFigure.ROGUE);
			fail("the result list must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// what an unmodifiable list does
		}
	}
}
