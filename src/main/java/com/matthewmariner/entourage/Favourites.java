package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The starred NPC ids: what the profile holds, what it means, and what a corrupted one
 * means.
 *
 * <p><b>Ids and not names, and the reason is in this repo already.</b> A favourite has to
 * be written into one config value, so it needs a delimiter, and a delimiter is only safe
 * if the values cannot contain it. {@link EntourageSettings#CUSTOM_LINE_SEPARATOR}
 * documents the other half of this plugin where that went the other way: the custom-lines
 * box splits on a comma with no escape, so a line of your own cannot contain one, and the
 * README says so out loud because there was no better answer. NPC names contain commas —
 * "Guard, Falador" is the shape of thing the cache is full of — so a list of names would
 * have inherited that defect on day one. A list of integers cannot: no id contains a comma,
 * so the collision is impossible by construction rather than merely unlikely. The name a
 * row shows is resolved from the cache when it is drawn, off the same
 * {@code NPCComposition} {@link FollowerAppearance} dresses a follower from.
 *
 * <p><b>Newest first.</b> {@link #with} puts a new id at the <i>front</i>, and re-starring
 * one already on the list moves it there. Two reasons, and the first is structural: the
 * list is capped, so something has to fall off, and the only defensible thing to drop is
 * the one starred longest ago — a cap that discarded the id you just pressed the star on
 * would be a control that visibly did nothing. The second is that the panel draws the list
 * top-down in this order, so the thing you were just looking at is the thing at the top.
 *
 * <p><b>Nothing here trusts the stored string.</b> It is a config value: it arrives from a
 * hand-edited profile, from a profile synced off another install, or from a build of this
 * plugin that wrote a different shape. {@link #parse} answers a well-formed list for every
 * possible input and throws for none of them — this repo's rule is that a config blob is
 * coerced field by field rather than trusted wholesale, and a favourites list that threw
 * inside a Swing redraw would be a side panel that stops drawing at all.
 */
final class Favourites
{
	/**
	 * How many ids may be starred at once: 20.
	 *
	 * <p>A bound on the panel rather than on memory — twenty integers cost nothing. The
	 * favourites are drawn as one row each inside a 225-pixel sidebar under everything else
	 * the picker shows, so a list nobody capped becomes a scroll with no bottom, and every
	 * row of it is a name this plugin has to ask the client to resolve. Twenty is comfortably
	 * more than the handful of bodies anybody actually keeps and few enough to read.
	 */
	static final int MAX = 20;

	/**
	 * What separates one id from the next.
	 *
	 * <p>The same character the custom-lines box splits on, and here it needs no escape and
	 * no caveat: the values on either side of it are integers.
	 */
	static final char SEPARATOR = ',';

	private Favourites()
	{
	}

	/**
	 * Turns whatever the profile holds into the list this plugin will act on.
	 *
	 * <p>Every way a stored value can be wrong is answered by dropping the piece that is
	 * wrong and keeping the rest, because the alternative — refusing the whole value — loses
	 * nineteen good favourites to one bad character. In order:
	 *
	 * <ul>
	 *   <li>{@code null} or blank is an empty list. The proxy is not this plugin's code and a
	 *       key that has never been written comes back as the {@code @ConfigItem} default,
	 *       which is the empty string.</li>
	 *   <li>An empty piece — a leading, doubled or trailing comma — is skipped, so
	 *       {@code "3598,,"} is one favourite rather than a parse failure. This is the shape
	 *       {@link #format} itself can never produce and a hand edit produces constantly.</li>
	 *   <li>Surrounding whitespace is trimmed, so {@code " 3598 , 1234"} is two ids. A
	 *       person editing the profile by hand puts spaces after commas.</li>
	 *   <li>Anything that is not an integer is skipped rather than thrown on — a name
	 *       somebody pasted, a decimal, a value past {@code Integer.MAX_VALUE}.
	 *       {@code Integer.parseInt} throws for all three and this is the only place that
	 *       has to know it.</li>
	 *   <li>Anything at or below {@link FollowerBody#NO_CUSTOM_NPC} is skipped. Zero is this
	 *       plugin's own word for "no typed id", so a zero on the favourites list is a star
	 *       on nothing; a negative is not a file id at all.</li>
	 *   <li>A duplicate keeps its <i>first</i> position and the later copy is dropped, so
	 *       the newest-first order the list is written in survives a round trip.</li>
	 *   <li>Anything past {@link #MAX} is dropped. A stored list longer than the cap is what
	 *       a profile written before the cap moved would hold, and the honest reading of it
	 *       is the first {@code MAX} — the same ones {@link #with} would have kept.</li>
	 * </ul>
	 *
	 * @param raw whatever {@link EntourageConfig#favouriteNpcIds()} answered
	 * @return the ids, newest first, unmodifiable, never {@code null}, never longer than
	 * {@link #MAX}, with no duplicates and nothing at or below
	 * {@link FollowerBody#NO_CUSTOM_NPC}
	 */
	static List<Integer> parse(@Nullable String raw)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return Collections.emptyList();
		}

		List<Integer> ids = new ArrayList<>();
		int start = 0;
		while (start <= raw.length() && ids.size() < MAX)
		{
			int end = raw.indexOf(SEPARATOR, start);
			if (end < 0)
			{
				end = raw.length();
			}

			add(ids, raw.substring(start, end));
			start = end + 1;
		}

		return Collections.unmodifiableList(ids);
	}

	/**
	 * One piece of the stored string, if it turns out to be an id worth keeping.
	 *
	 * <p>Split out so the loop above reads as the walk it is and every refusal has one
	 * place to live.
	 */
	private static void add(List<Integer> ids, String piece)
	{
		String trimmed = piece.trim();
		if (trimmed.isEmpty() || !looksLikeAnInt(trimmed))
		{
			return;
		}

		int npcId;
		try
		{
			npcId = Integer.parseInt(trimmed);
		}
		catch (NumberFormatException notANumber)
		{
			// Genuine overflow — "2147483648", ten digits and within looksLikeAnInt's own
			// length allowance — reaches here because that check is conservative rather
			// than exact: it cannot tell a magnitude problem from a well-formed number
			// without doing the parse it exists to avoid. This is the one shape of junk
			// that still costs a throw; everything else below is refused before this line.
			return;
		}

		if (npcId <= FollowerBody.NO_CUSTOM_NPC || ids.contains(npcId))
		{
			return;
		}

		ids.add(npcId);
	}

	/**
	 * A cheap, conservative stand-in for "would {@link Integer#parseInt} throw", so a
	 * favourites list full of junk is refused without paying for the exception each refusal
	 * used to cost.
	 *
	 * <p><b>Measured, not assumed.</b> A stored value of 200,000 junk pieces took 1685ms to
	 * parse, all of it on the event dispatch thread — {@link RosterView#of} rebuilds on
	 * every {@code redrawBody()} — and none of that time was the parsing itself: it was
	 * {@code NumberFormatException} filling in a stack trace, once per piece, for pieces
	 * that were never going to parse. This answers the same question — "is this piece worth
	 * handing to {@code parseInt}" — for a small fraction of the cost, because it never
	 * throws.
	 *
	 * <p><b>Conservative in one direction, with a single accepted exception.</b> A piece this
	 * method passes still goes through the real parse below unconditionally, so a false
	 * "maybe" costs nothing but one honest parseInt call — exactly what every well-formed id
	 * already paid. A false "never" would ordinarily be a behaviour change: a value this
	 * plugin used to accept would start silently vanishing from the list — which is exactly
	 * why the eleven-character cap is sized off a case that is actually kept, not discarded.
	 * {@code Integer.MIN_VALUE}'s {@code "-2147483648"} needs eleven characters too, but a
	 * negative is floored out by {@link FollowerBody#NO_CUSTOM_NPC} the instant it parses, so
	 * getting that one wrong here would cost nothing observable. The load-bearing
	 * eleven-character values are the positive ones this plugin actually keeps —
	 * {@code "+2147483647"}, and a value zero-padded to eleven digits,
	 * {@code "02147483647"} — both {@code Integer.MAX_VALUE}, both kept today, and one
	 * character shorter would refuse either. The one shape this generosity does not chase is
	 * the mirror image: a value zero-padded <i>past</i> eleven characters, which the length
	 * check below refuses even though the real parse would still accept it — the second
	 * residual, further down.
	 *
	 * <p><b>{@link Character#isDigit}, deliberately, and not {@code c >= '0' && c <= '9'}.</b>
	 * {@code Integer.parseInt} accepts any decimal digit in the Basic Multilingual Plane —
	 * Arabic-Indic {@code ٣٥٩٨} and full-width {@code ３５９８} both parse to 3598 today,
	 * through {@code Character.digit} — and an ASCII-only pre-check would silently refuse
	 * input this plugin has always accepted. Narrowed to the BMP deliberately rather than
	 * claimed of every Unicode digit: a supplementary-plane digit such as {@code 𝟎}
	 * (U+1D7CE) is a surrogate pair in Java's UTF-16 {@code char}s, neither half of which
	 * {@link Character#isDigit} recognises as one — and {@code Integer.parseInt("𝟎")} itself
	 * throws, so refusing it here changes no case this plugin ever accepted.
	 * {@code FavouritesTest} pins both BMP digit alphabets through this method as well as
	 * through the parse it guards.
	 *
	 * <p><b>Two residuals, both accepted on purpose.</b> A magnitude past {@code int} range
	 * at eleven characters or fewer — {@code "2147483648"} — still reaches {@code parseInt}
	 * and still costs one throw, because telling overflow apart from a good value needs the
	 * parse this method exists to skip. And a value padded with enough leading zeros to pass
	 * eleven characters — {@code parseInt} tolerates any number of them — is refused here
	 * even though the real parse would have accepted it; nobody's profile holds a favourite
	 * written with a dozen leading zeros, and refusing the vanishingly unrealistic case is
	 * what keeps the length check a flat, branch-free cap rather than a second walk over the
	 * string to skip zeros first.
	 *
	 * @param piece one comma-separated piece, already trimmed and confirmed non-empty
	 * @return false when {@link Integer#parseInt} is certain to throw for this piece, and — as
	 * the second residual above sets out, and only there — for a value padded past eleven
	 * characters with leading zeros, which it would have accepted
	 */
	private static boolean looksLikeAnInt(String piece)
	{
		int length = piece.length();
		if (length > 11)
		{
			return false;
		}

		int start = 0;
		char first = piece.charAt(0);
		if (first == '+' || first == '-')
		{
			start = 1;
		}

		if (start == length)
		{
			// Nothing but a sign.
			return false;
		}

		for (int index = start; index < length; index++)
		{
			if (!Character.isDigit(piece.charAt(index)))
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Writes a list back out in the shape {@link #parse} reads.
	 *
	 * @param ids the favourites, newest first
	 * @return the profile value, or the empty string for an empty list. No trailing
	 * separator, no spaces: {@code parse} tolerates both and there is no reason to write
	 * something that needs tolerating.
	 */
	static String format(List<Integer> ids)
	{
		StringBuilder out = new StringBuilder();
		for (int npcId : ids)
		{
			if (out.length() > 0)
			{
				out.append(SEPARATOR);
			}
			out.append(npcId);
		}
		return out.toString();
	}

	/**
	 * The list with {@code npcId} starred.
	 *
	 * <p>At the front, dropping the oldest if that puts it over {@link #MAX} — see the class
	 * javadoc. An id already on the list is <b>moved</b> to the front rather than duplicated,
	 * which is also what makes "star something you already starred" a harmless gesture rather
	 * than a way to fill the list with one id.
	 *
	 * @param npcId the id to star. Anything at or below {@link FollowerBody#NO_CUSTOM_NPC}
	 *              changes nothing, so a caller does not have to test the number first — it
	 *              is the same floor {@link FollowerBody#custom} applies, and a star on "no
	 *              typed id" is not a thing that can be meant.
	 * @return a new list, newest first, never longer than {@link #MAX} — a fresh copy even
	 * when nothing changed, so every path through this method keeps the same promise rather
	 * than the "no typed id" floor being the one caller that hands the caller's own list back
	 * under a different name
	 */
	static List<Integer> with(List<Integer> ids, int npcId)
	{
		if (npcId <= FollowerBody.NO_CUSTOM_NPC)
		{
			return Collections.unmodifiableList(new ArrayList<>(ids));
		}

		List<Integer> next = new ArrayList<>(ids.size() + 1);
		next.add(npcId);
		for (int existing : ids)
		{
			if (existing != npcId && next.size() < MAX)
			{
				next.add(existing);
			}
		}

		return Collections.unmodifiableList(next);
	}

	/**
	 * The list with {@code npcId} un-starred.
	 *
	 * @return a new list in the same order, minus that id. An id that was not on the list
	 * gives the list back unchanged rather than throwing: the panel draws from a snapshot,
	 * and a second click on a row that has already gone is a stale gesture rather than a bug.
	 */
	static List<Integer> without(List<Integer> ids, int npcId)
	{
		List<Integer> next = new ArrayList<>(ids.size());
		for (int existing : ids)
		{
			if (existing != npcId)
			{
				next.add(existing);
			}
		}

		return Collections.unmodifiableList(next);
	}
}
