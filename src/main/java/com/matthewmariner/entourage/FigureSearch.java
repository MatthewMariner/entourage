package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finding one of the twenty-three figures by typing part of its name.
 *
 * <p><b>Why this exists at all.</b> Choosing an entourage meant five dropdowns of
 * twenty-three items each: a hundred and fifteen rows to read to answer "is the Wise Old
 * Man in here?". A search box answers it in three keystrokes, which is the actual
 * complaint underneath the panel looking plain.
 *
 * <p><b>Much less machinery than {@code ../gunnars-tools}' {@code MonsterIndex}, on
 * purpose.</b> That one indexes sixteen thousand cache entries read at runtime and has to
 * fold variants, count them and report a truncation. This one ranks a fixed list of
 * twenty-three names that ship in an enum, so there is no index to build, nothing to seal
 * and no thread to hand it across — it is a loop over {@code values()} on every keystroke,
 * and twenty-three string comparisons is not a cost worth engineering around.
 *
 * <p><b>The tiers are borrowed, though, and so is the tolerance for a typo.</b> A search
 * that only did prefixes would answer nothing for "old man", and one that only did
 * substrings would rank "Elite Black Knight" above "Knight" for "knight". And a name
 * misremembered by one letter — "Duradal", "Vanaka", "Sorcress" — is the case a list this
 * short makes most annoying, because the figure is plainly there and the box says nothing.
 *
 * <p><b>The near tier here is deliberately an addition, not a fallback — the opposite of
 * {@code MonsterIndex}'s choice, for the opposite reason.</b> {@code MonsterIndex.matching}
 * only runs its near pass {@code if (found.isEmpty())}, precisely so that "spid" cannot put
 * Spindel in the middle of a search for spiders: over sixteen thousand names a query that
 * matches properly is common and a near match that always ran would dilute it. {@link
 * #tierOf} has no such guard and runs {@link #isNear} unconditionally whenever the cheap
 * tiers miss — over twenty-three names a query that matches nothing real is common enough
 * that {@code aNearMatchSortsBelowEveryRealMatch} pins the opposite behaviour on purpose:
 * "dura" is meant to still offer "Turael" underneath "Duradel". Sixteen thousand and
 * twenty-three are different problems, and this is not the same guard ported over.
 *
 * <p>The two distance functions differ as well. {@link #distance} is plain Levenshtein;
 * {@code MonsterIndex.prefixDistance} is banded and counts two neighbouring letters swapped
 * as one edit rather than two. A transposition inside a name — "turale" for "Turael" — can
 * therefore resolve against the game's sixteen thousand and not against this list's
 * twenty-three, or vice versa depending on which budget it falls inside. Worth knowing
 * before assuming the two searches would agree on a name typed the same way into both.
 */
final class FigureSearch
{
	/**
	 * Shorter than this and a near match is not attempted.
	 *
	 * <p>Three characters have too many neighbours to be a spelling mistake: at one edit
	 * "han" reaches "hans", "man", "ban" and most of a dictionary, and a list of everything
	 * three letters could have meant is not an answer. The cheap tiers already cover a short
	 * query — {@link Tier#PREFIX} and {@link Tier#CONTAINS} are what "sorc" wants.
	 */
	static final int NEAR_MATCH_MINIMUM = 4;

	/**
	 * From this many characters typed, two edits are forgiven rather than one.
	 *
	 * <p>Same threshold {@code MonsterIndex} settled on after a real report — "Dagganoth"
	 * for "Dagannoth" is two substitutions in nine characters. A long word carries enough
	 * signal that two edits still identify it out of twenty-three; a five-letter one does
	 * not, and at that length two edits reaches names nobody typing it meant.
	 */
	static final int TWO_EDITS_FROM = 7;

	/**
	 * How closely a figure's name matched. The ordinal is the sort key, so the tiers are
	 * declared best-first and nothing else has to know how many there are.
	 */
	enum Tier
	{
		/** The typed name, exactly. "Nieve" for "nieve". */
		EXACT,

		/** The name starts with what was typed. "Vannaka" for "vann". */
		PREFIX,

		/** The name contains it anywhere. "Wise Old Man" for "old". */
		CONTAINS,

		/** The name starts with something close enough. "Duradel" for "duradal". */
		NEAR
	}

	private FigureSearch()
	{
	}

	/**
	 * Every figure the query names, best match first.
	 *
	 * <p>Ties inside a tier keep {@link EntourageFigure}'s own order, which is the order the
	 * settings dropdown lists them in — so the panel and the config screen agree about which
	 * of two equally good answers comes first.
	 *
	 * @param query whatever is in the search box. {@code null}, empty or all spaces means
	 *              "everything", because an empty box is a browse rather than a failed
	 *              search — a panel that went blank until you typed would hide the list the
	 *              user opened it to see.
	 * @return the matches, in order. Never {@code null}; empty only when nothing matched.
	 */
	static List<EntourageFigure> matching(String query)
	{
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty())
		{
			return Collections.unmodifiableList(Arrays.asList(EntourageFigure.values()));
		}

		List<Ranked> ranked = new ArrayList<>();
		for (EntourageFigure figure : EntourageFigure.values())
		{
			Tier tier = tierOf(figure.getDisplayName().toLowerCase(Locale.ROOT), needle);
			if (tier != null)
			{
				ranked.add(new Ranked(figure, tier));
			}
		}

		// Stable, so the comparator only has to say what the tier decides and the enum's own
		// order survives underneath it.
		ranked.sort(Comparator.comparingInt(entry -> entry.tier.ordinal()));

		List<EntourageFigure> found = new ArrayList<>(ranked.size());
		for (Ranked entry : ranked)
		{
			found.add(entry.figure);
		}

		return Collections.unmodifiableList(found);
	}

	/**
	 * @param name   one figure's display name, already lowercased
	 * @param needle what was typed, already trimmed and lowercased and never empty
	 * @return how well they match, or {@code null} for not at all
	 */
	private static Tier tierOf(String name, String needle)
	{
		if (name.equals(needle))
		{
			return Tier.EXACT;
		}
		if (name.startsWith(needle))
		{
			return Tier.PREFIX;
		}
		if (name.contains(needle))
		{
			return Tier.CONTAINS;
		}

		return isNear(name, needle) ? Tier.NEAR : null;
	}

	/**
	 * Whether the name is close enough to what was typed.
	 *
	 * <p><b>Two comparisons, and both are load-bearing.</b> The query is measured against
	 * the whole name <i>and</i> against the name's opening of the same length, and either
	 * one landing is a match — because the two shapes of mistake do not survive the same
	 * measurement:
	 * <ul>
	 *   <li><b>A whole name misremembered</b> — "vanaka" for "Vannaka" — is one edit from
	 *       the whole word and <i>two</i> from its first six characters, because dropping a
	 *       letter shifts everything after it. Only the whole-word comparison catches it.</li>
	 *   <li><b>Half a name with a slip in it</b> — "necrp" for "Necromancer" — is one edit
	 *       from the first five characters and seven from the whole word. Only the opening
	 *       comparison catches it.</li>
	 * </ul>
	 * A name shorter than the query has no separate opening, so the two collapse to one.
	 */
	private static boolean isNear(String name, String needle)
	{
		if (needle.length() < NEAR_MATCH_MINIMUM)
		{
			return false;
		}

		int allowed = needle.length() >= TWO_EDITS_FROM ? 2 : 1;
		if (distance(name, needle, allowed) <= allowed)
		{
			return true;
		}

		if (name.length() <= needle.length())
		{
			return false;
		}

		return distance(name.substring(0, needle.length()), needle, allowed) <= allowed;
	}

	/**
	 * Levenshtein distance, given up on once it cannot come in under {@code ceiling}.
	 *
	 * <p>Two rows rather than a full matrix, and an early exit on the best value in a row —
	 * not for speed over twenty-three short names, but because the answer this asks for is
	 * "is it within two?" and a function that returns {@code ceiling + 1} for everything
	 * further away cannot be misread as a real distance by a caller that starts ranking on
	 * it.
	 *
	 * @return the distance, or {@code ceiling + 1} for anything at least that far apart
	 */
	private static int distance(String left, String right, int ceiling)
	{
		int[] previous = new int[right.length() + 1];
		int[] current = new int[right.length() + 1];

		for (int column = 0; column <= right.length(); column++)
		{
			previous[column] = column;
		}

		for (int row = 1; row <= left.length(); row++)
		{
			current[0] = row;
			int best = current[0];

			for (int column = 1; column <= right.length(); column++)
			{
				int substitution = previous[column - 1]
					+ (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
				current[column] = Math.min(substitution,
					Math.min(previous[column] + 1, current[column - 1] + 1));
				best = Math.min(best, current[column]);
			}

			if (best > ceiling)
			{
				return ceiling + 1;
			}

			int[] swap = previous;
			previous = current;
			current = swap;
		}

		return previous[right.length()];
	}

	/** One figure and how well it matched, for the sort. */
	private static final class Ranked
	{
		private final EntourageFigure figure;
		private final Tier tier;

		private Ranked(EntourageFigure figure, Tier tier)
		{
			this.figure = figure;
			this.tier = tier;
		}
	}
}
