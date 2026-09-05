package com.matthewmariner.entourage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The shipped lines: that every figure has some, that none of them is too long to draw,
 * and that the set is a set rather than a list with the same line in it twice.
 *
 * <p><b>What this file cannot check is the one thing that matters most</b> — whether a
 * line is really what that NPC says. No test can: the wiki is not on this classpath and
 * this plugin does not make network calls. The provenance is stated per figure in
 * {@link FigureLines}, with the transcript URL beside each sourced set, and stated again
 * in the README so that a user can tell the game's wording from ours. What is held here
 * is everything downstream of that.
 */
public class FigureLinesTest
{
	@Test
	public void everyFigureInTheRosterHasBetweenThreeAndSevenLines()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			List<String> lines = FigureLines.of(figure);

			assertTrue(figure + " has nothing to say — a figure with no lines is a dialogue "
					+ "setting that silently does nothing for whoever picked it",
				lines.size() >= FigureLines.MIN_LINES);
			assertTrue(figure + " has " + lines.size() + " lines, more than the ceiling",
				lines.size() <= FigureLines.MAX_LINES);
		}
	}

	/**
	 * A line longer than a custom one is allowed to be would be a banner across the
	 * viewport — the overlay draws it centred over the head without wrapping — and would
	 * also be a preset a user could not retype into the box.
	 */
	@Test
	public void noShippedLineIsLongerThanTheOverlayWillDraw()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			for (String line : FigureLines.of(figure))
			{
				assertTrue(figure + ": \"" + line + "\" is " + line.length() + " characters",
					line.length() <= EntourageSettings.MAX_CUSTOM_LINE_LENGTH);
			}
		}
	}

	/**
	 * <b>Within a figure the lines are distinct.</b> {@link FollowerRemarks} never repeats
	 * the line it just said, so a duplicate would defeat that guard by being a different
	 * index carrying the same words — the follower would look like it had repeated itself
	 * and nothing would be able to tell.
	 *
	 * <p>Across figures they are deliberately <i>not</i> distinct: the six slayer masters
	 * really do share a script in game, which is the next test.
	 */
	@Test
	public void aFigureNeverShipsTheSameLineTwice()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			Set<String> seen = new HashSet<>();
			for (String line : FigureLines.of(figure))
			{
				assertTrue(figure + " ships \"" + line + "\" twice", seen.add(line));
			}
		}
	}

	/**
	 * <b>The shared slayer-master template is shared on purpose, and this pins that it is
	 * a decision.</b> Nieve, Duradel and Mazchna have essentially nothing else short
	 * enough on their transcripts. Anybody who reads this as a copy-paste mistake and
	 * "fixes" it by writing them something distinctive would be inventing dialogue and
	 * shipping it as the game's — which is the one thing {@link FigureLines} exists not to
	 * do. If they are ever given their own lines, those lines have to move to the "ours"
	 * block with the rest.
	 */
	@Test
	public void theSlayerMastersShareTheScriptTheGameGivesThem()
	{
		String template = "'Ello, and what are you after then?";

		for (EntourageFigure master : new EntourageFigure[]{
			EntourageFigure.VANNAKA, EntourageFigure.NIEVE, EntourageFigure.STEVE,
			EntourageFigure.TURAEL, EntourageFigure.DURADEL, EntourageFigure.MAZCHNA})
		{
			// Turael is the exception: his own transcript is well enough stocked that he
			// does not need the template at all.
			if (master == EntourageFigure.TURAEL)
			{
				assertFalse("Turael has his own lines and should not be carrying the template",
					FigureLines.of(master).contains(template));
				continue;
			}

			assertTrue(master + " should carry the greeting every slayer master shares",
				FigureLines.of(master).contains(template));
		}
	}

	/**
	 * Nothing blank, nothing padded, and nothing with a line break in it. A line is drawn
	 * with {@code drawString}, which renders a newline as a box glyph rather than as a
	 * second line.
	 */
	@Test
	public void everyLineIsOneTrimmedNonEmptyLine()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			for (String line : FigureLines.of(figure))
			{
				assertFalse(figure + " ships an empty line", line.trim().isEmpty());
				assertEquals(figure + ": \"" + line + "\" has whitespace around it",
					line, line.trim());

				for (int i = 0; i < line.length(); i++)
				{
					if (Character.isISOControl(line.charAt(i)))
					{
						fail(figure + ": \"" + line + "\" contains a control character");
					}
				}
			}
		}
	}

	/**
	 * <b>The lists are shared, so they had better be unmodifiable.</b> One list per figure
	 * is handed to every follower that ever wears it and to
	 * {@link EntourageSettings#linesFor}; a caller that could edit it would be editing the
	 * roster for the rest of the session.
	 */
	@Test
	public void theShippedListsCannotBeEditedByTheirCallers()
	{
		try
		{
			FigureLines.of(EntourageFigure.ROGUE).add("...and I would have got away with it");
			fail("a shared list that can be added to is a roster any caller can rewrite");
		}
		catch (UnsupportedOperationException expected)
		{
			// The point of the test.
		}
	}

	/**
	 * A figure with no entry gets an empty list rather than a null. The roster test above
	 * makes this unreachable today, and it is the branch that keeps the unreachable case
	 * from being a null dereference inside a game-tick handler if it ever is.
	 */
	@Test
	public void aFigureWithNoEntryAnswersEmptyRatherThanNull()
	{
		assertTrue(FigureLines.of(null).isEmpty());
	}
}
