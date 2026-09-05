package com.matthewmariner.entourage;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What each figure has to say, before the user has typed anything of their own.
 *
 * <p><b>Two kinds of line live here and they are not mixed within a figure.</b> Where the
 * NPC has recorded dialogue on the Old School RuneScape Wiki, the lines are that
 * dialogue, quoted exactly — the block below says which page each set came from. Where
 * the figure is a generic body out of the cache with no recorded speech at all (a rogue,
 * a farmer, a paladin), the lines are <b>written for this plugin</b> and are marked as
 * such. Nothing here is a paraphrase presented as a quote, and no figure carries a mix of
 * the two, so "is this the game's wording or ours?" is always answerable per figure
 * rather than per line. The README says the same thing to the user.
 *
 * <p><b>The six slayer masters really do share a script.</b> {@code 'Ello, and what are
 * you after then?}, {@code Good luck with that.} and {@code That's the spirit.} are
 * word-for-word identical on Vannaka's, Nieve's, Steve's, Turael's, Duradel's and
 * Mazchna's transcripts — it is one template the game reuses. Nieve, Duradel and Mazchna
 * have almost nothing else short enough to draw over a head, so their sets are mostly
 * that template. That is a fact about the game rather than a shortcut here, and the
 * alternative — inventing them a personality and calling it canon — is the thing this
 * class exists not to do.
 *
 * <p><b>Nothing here is longer than {@link EntourageSettings#MAX_CUSTOM_LINE_LENGTH}.</b>
 * The overlay draws a line centred over the figure's head without wrapping, so length is
 * a layout constraint rather than a style preference, and {@code FigureLinesTest} holds
 * every shipped line to the same bound a user's own line is cut at.
 *
 * <p><b>Lines may contain commas; a user's own may not.</b> These are Java string
 * literals and never go through {@link EntourageSettings#parseLines}, which splits on
 * every comma with no escape. So a preset can say {@code Well done, here you go.} and a
 * custom line cannot — that asymmetry is real, and the README states it rather than
 * leaving somebody to discover it by typing one.
 *
 * <p>Client-thread-free, allocation-free after class load: the lists are built once and
 * handed out unmodifiable.
 */
final class FigureLines
{
	/** The fewest lines a figure may ship with — the brief's floor, held by a test. */
	static final int MIN_LINES = 3;

	/**
	 * The most: seven. Past that a follower stops having a handful of things it says and
	 * starts having a script, and the odds of hearing any particular line get small
	 * enough that the writing stops mattering.
	 */
	static final int MAX_LINES = 7;

	/**
	 * The shared slayer-master template, named once rather than retyped six times — see
	 * the class javadoc. Sourced from every one of their transcripts.
	 */
	private static final String SLAYER_GREETING = "'Ello, and what are you after then?";
	private static final String SLAYER_GOOD_LUCK = "Good luck with that.";
	private static final String SLAYER_SPIRIT = "That's the spirit.";

	/** The shared Astral Contact greeting, likewise. */
	private static final String CONTACT_GREETING = "'Ello, can I help you?";

	private static final Map<EntourageFigure, List<String>> BY_FIGURE =
		new EnumMap<>(EntourageFigure.class);

	static
	{
		// --- Ours: figures with no recorded dialogue ---------------------------
		//
		// Every set in this block was written for this plugin. These are generic bodies
		// out of the cache — a rogue, a thief, a farmer — and the game gives them nothing
		// to say, so writing them something in keeping is the honest option and claiming
		// the wiki said it would not be.

		put(EntourageFigure.ROGUE,
			"Keep your voice down.",
			"Nobody saw us.",
			"Mind your purse.",
			"I know a shortcut.");

		put(EntourageFigure.WHITE_KNIGHT,
			"The White Knights stand ready.",
			"Falador is safe enough tonight.",
			"Keep to the path.",
			"I have my orders.");

		put(EntourageFigure.ELITE_BLACK_KNIGHT,
			"The Kinshra do not forget.",
			"Strength is its own argument.",
			"We are watching.",
			"Stay behind me.");

		put(EntourageFigure.PALADIN,
			"Ardougne is quieter than it looks.",
			"Stand fast.",
			"Trouble travels in threes.",
			"Keep moving.");

		put(EntourageFigure.GRILL_KNIGHT,
			"At your side.",
			"Lead on.",
			"A quiet patrol is a good patrol.",
			"My blade is sharp.");

		put(EntourageFigure.HERO,
			"They still tell that story wrong.",
			"I have seen worse.",
			"Another day. Onward.",
			"After you.");

		put(EntourageFigure.ZAMORAK_MAGE,
			"The Abyss is closer than you think.",
			"Mind the rifts.",
			"Power has a price.",
			"Do not touch that.");

		put(EntourageFigure.NECROMANCER,
			"The dead keep better secrets.",
			"Bones remember.",
			"I felt something move.",
			"Stand still a moment.");

		put(EntourageFigure.SORCERESS,
			"My garden is not for trespassers.",
			"Magic is not a parlour trick.",
			"Do stop fidgeting.",
			"The desert keeps its own counsel.");

		put(EntourageFigure.THIEF,
			"Nothing to see here.",
			"That pocket looked heavy.",
			"I was never here.",
			"Look busy.");

		put(EntourageFigure.PIRATE,
			"I have seen bigger waves.",
			"Land makes me queasy.",
			"Keep your rum close.",
			"There be worse company.");

		put(EntourageFigure.FARMER,
			"The crops need rain.",
			"Mind the drills.",
			"Best keep off the allotments.",
			"Nothing grows in a hurry.");

		// --- The game's own words ----------------------------------------------
		//
		// Each set below was read off the wiki transcript named above it and is quoted
		// exactly, punctuation and all — including the leading apostrophe in "'Ello" and
		// Sir Vyvin's ellipses, both of which are how the transcripts have them.

		// https://oldschool.runescape.wiki/wiki/Transcript:Vannaka
		put(EntourageFigure.VANNAKA,
			SLAYER_GREETING,
			SLAYER_GOOD_LUCK,
			SLAYER_SPIRIT,
			CONTACT_GREETING,
			"Here, have this.");

		// https://oldschool.runescape.wiki/wiki/Transcript:Nieve
		// and https://oldschool.runescape.wiki/wiki/Transcript:Monkey_Madness_II
		put(EntourageFigure.NIEVE,
			SLAYER_GREETING,
			SLAYER_GOOD_LUCK,
			"What in Gielinor is that thing?",
			"What's wrong?");

		// https://oldschool.runescape.wiki/wiki/Transcript:Steve — one page covers both
		// the Wyvern Cave gatekeeper and the slayer master he becomes.
		put(EntourageFigure.STEVE,
			"Welcome to my private little wyvern area.",
			"I love farming, but I need some help!",
			SLAYER_GREETING,
			SLAYER_SPIRIT);

		// https://oldschool.runescape.wiki/wiki/Transcript:Turael
		put(EntourageFigure.TURAEL,
			"I'm one of the elite Slayer Masters.",
			"Suit yourself.",
			"Hmmm well I'm not so sure...",
			"I suppose you want a new axe?",
			"Very well done, take this.");

		// https://oldschool.runescape.wiki/wiki/Transcript:Duradel — the template and
		// nothing else short enough. See the class javadoc.
		put(EntourageFigure.DURADEL,
			SLAYER_GREETING,
			SLAYER_GOOD_LUCK,
			SLAYER_SPIRIT);

		// https://oldschool.runescape.wiki/wiki/Transcript:Mazchna — likewise.
		put(EntourageFigure.MAZCHNA,
			SLAYER_GREETING,
			SLAYER_GOOD_LUCK,
			SLAYER_SPIRIT,
			CONTACT_GREETING);

		// https://oldschool.runescape.wiki/wiki/Transcript:Wise_Old_Man
		put(EntourageFigure.WISE_OLD_MAN,
			"Less of the 'old' man, if you please!",
			"Deary deary me...",
			"I know what I know.",
			"Spot on!",
			"Bah!");

		// https://oldschool.runescape.wiki/wiki/Transcript:Hans
		put(EntourageFigure.HANS,
			"Hello. What are you doing here?",
			"Oooh! Who are you?",
			"Well done, here you go.",
			"Help! Help!");

		// Sir Amik's own transcript is flagged incomplete and has almost nothing in it;
		// these are from the quests he speaks in —
		// https://oldschool.runescape.wiki/wiki/Transcript:Black_Knights%27_Fortress
		// and https://oldschool.runescape.wiki/wiki/Transcript:Wanted!
		put(EntourageFigure.SIR_AMIK_VARZE,
			"Subtlety isn't exactly our strong point.",
			"Ok. Please don't break anything.",
			"How's the mission going?",
			"Call me Sir Amik, or liege.");

		// https://oldschool.runescape.wiki/wiki/Transcript:Sir_Vyvin — the ellipses are
		// the transcript's, from the painting exchange.
		put(EntourageFigure.SIR_VYVIN,
			"Greetings traveller.",
			"... ...what?",
			"... ...you are very odd.",
			"Excellent work!");

		// https://oldschool.runescape.wiki/wiki/Transcript:Ghommal
		put(EntourageFigure.GHOMMAL,
			"Can I help you?",
			"Your gear is adequate, I'm impressed.",
			"Oh, how have you been getting on?");
	}

	private FigureLines()
	{
	}

	/**
	 * @param figure whose lines are wanted
	 * @return everything this figure can say, never {@code null} and never empty —
	 * {@code FigureLinesTest} holds every figure in the roster to at least
	 * {@link #MIN_LINES}. Unmodifiable: one list is shared by every follower that ever
	 * wears this figure, and a caller that could edit it would be editing the roster.
	 */
	static List<String> of(EntourageFigure figure)
	{
		List<String> lines = BY_FIGURE.get(figure);
		return lines == null ? Collections.emptyList() : lines;
	}

	private static void put(EntourageFigure figure, String... lines)
	{
		BY_FIGURE.put(figure, Collections.unmodifiableList(Arrays.asList(lines)));
	}
}
