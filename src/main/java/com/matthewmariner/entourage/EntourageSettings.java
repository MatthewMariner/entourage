package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Constants;

/**
 * One game tick's worth of {@link EntourageConfig}, read once and handed down.
 *
 * <p><b>Why a snapshot rather than passing the config itself.</b> Three reasons, and
 * the third is the one that matters:
 * <ul>
 *   <li>A {@code Config} is a dynamic proxy over {@code ConfigManager}, so every
 *       getter is a map lookup and a string parse. There are a dozen items now, and
 *       {@link FollowerWalk#tick} and {@link EntourageChatter} between them would
 *       otherwise read most of them per follower per tick.</li>
 *   <li>Reading each value once per tick means a setting changed mid-tick cannot be
 *       half-applied — the follower cannot pick a slot at one distance and then measure
 *       the recall at another.</li>
 *   <li><b>The numbers have to be clamped somewhere, and this is the only place that
 *       can.</b> {@code @Range} is a hint to the settings <i>panel</i>: it bounds the
 *       spinner, and it is not enforced on the way out of the proxy. A profile edited
 *       by hand — or carried forward from a build where the bounds were different — can
 *       hand this plugin {@code followDistance=0}, which is a follower standing inside
 *       the player, or {@code recallDistance=900}, which is a follower that walks to
 *       the edge of the loaded scene and is never brought back. Both are silent. So
 *       every number is clamped here, against the same constants the annotations
 *       use.</li>
 * </ul>
 *
 * <p>Immutable, and built per tick. Nothing holds one across ticks, because a held one
 * is a setting that stops taking effect.
 *
 * <p><b>What is deliberately not in here:</b> the colour, the font and the name-label
 * switch. Those are only ever read by {@link EntourageOverlay}, which draws between game
 * ticks and so cannot use a per-tick snapshot without lagging a click by up to 600ms.
 * Adding them here would be a second copy of an answer that has to be fresher than this
 * one.
 */
final class EntourageSettings
{
	/**
	 * The smallest roster: one follower.
	 *
	 * <p>Zero is not offered, and the reason is the same one that keeps a master on/off
	 * switch out of this config: the plugin's own toggle in the plugin list already means
	 * "none of this", and a second control that means the same thing is only a way for the
	 * two to disagree.
	 */
	static final int MIN_FOLLOWERS = 1;

	/**
	 * The largest: five, which is what was asked for — and the number the budget was
	 * checked against rather than assumed.
	 *
	 * <p><b>The figures are inherited, not measured.</b> {@code ../lively-cities}'
	 * {@code RenderPolicy} caps active cosmetic objects at <b>80</b> and model builds at
	 * <b>9 per frame</b>, the latter derived from a 16.667ms frame less 3ms of region
	 * loading and 151µs of overhead, over 1400µs per build. Five followers are five
	 * registered objects and — because a model is built once and kept, see
	 * {@link Follower} — at most five builds, all of them on the tick the roster first
	 * spawns. Both are comfortably inside those caps, so this plugin adds no
	 * {@code RenderPolicy} of its own: a budget guard that can never fire is a class
	 * nobody maintains.
	 *
	 * <p><b>What those numbers do not describe is the per-tick cost</b>, because Lively
	 * Cities' figures are mostly idle and these are mostly moving. The number to hold in
	 * mind is <b>ten collision checks per game tick</b>: five followers, each allowed
	 * {@link FollowerWalk#RUN_STEPS_PER_TICK} steps, each step one
	 * {@link WalkableStep#verdict} — which is a handful of null checks, two bounds
	 * comparisons and one {@code WorldArea.canTravelInDirection}. That is per <i>tick</i>,
	 * i.e. sixteen times a second at most, not per frame. <b>No frame cost has been
	 * measured for this plugin</b>, here or anywhere else in it, and this constant does
	 * not pretend otherwise; five is the number the owner asked for and the number the
	 * inherited budget clears, not a number a profiler produced.
	 */
	static final int MAX_FOLLOWERS = 5;

	/**
	 * One follower, which is what the plugin shipped with.
	 *
	 * <p>A fresh profile and a profile written before the roster existed have to mean the
	 * same thing, and this is what makes them: the first figure slot keeps the {@code
	 * figure} key it always had, and nobody who never touches this dial notices that four
	 * more slots appeared.
	 */
	static final int DEFAULT_FOLLOWERS = 1;

	/**
	 * The closest a follower may be told to stand: one tile.
	 *
	 * <p>Zero would put it on the player's own tile, which is not a formation but a
	 * figure inside you.
	 */
	static final int MIN_FOLLOW_DISTANCE = 1;

	/**
	 * The furthest: two tiles.
	 *
	 * <p>Not an arbitrary ceiling. The step search in {@link FollowerWalk} is greedy —
	 * it tries the diagonal and then each axis component, and never walks away from its
	 * slot to get around something — so the further out the slot sits, the more of the
	 * time it is on the far side of something the follower cannot reason its way past.
	 * At one and two tiles the slot is inside the player's own clearance nearly always.
	 */
	static final int MAX_FOLLOW_DISTANCE = 2;

	/** One tile behind, which is what a follower normally does. */
	static final int DEFAULT_FOLLOW_DISTANCE = 1;

	/**
	 * The furthest out any formation will ever place a follower: six tiles.
	 *
	 * <p>{@link MAX_FOLLOW_DISTANCE} for the first rank plus one tile per rank after it,
	 * and the deepest shape is a column of {@link #MAX_FOLLOWERS} — so
	 * {@code 2 + (5 - 1)}. Written out rather than derived from
	 * {@link EntourageFormation}'s tables because it is a bound on those tables:
	 * {@code EntourageFormationTest} walks every formation at every roster size and every
	 * follow distance and asserts nothing exceeds it, which is a guard on the shapes
	 * rather than a restatement of them. A literal for the same reason: written as
	 * {@code MAX_FOLLOW_DISTANCE + MAX_FOLLOWERS - 1} it would move whenever either of
	 * those did, and the number below into {@link #MIN_RECALL_DISTANCE} with it, so a
	 * roster of nine would silently look correct.
	 */
	static final int MAX_STATION_DISTANCE = 6;

	/**
	 * The shortest recall distance offered: eight tiles.
	 *
	 * <p><b>Six until the roster grew, and it had to move.</b> A recall fires when the
	 * follower is further from the player than this, and with five followers a column
	 * puts the last of them {@link #MAX_STATION_DISTANCE} tiles out — so a floor of six
	 * would recall a follower for standing exactly where it was told to, every tick,
	 * forever. Eight is that furthest station plus two tiles of slack, which is the
	 * smallest honest number rather than a round one.
	 *
	 * <p>The original reasoning still applies on top of it: below this a recall stops
	 * being a rescue and becomes the normal way the follower travels, and a follower that
	 * pops back to you every time you round a corner reads as broken rather than as
	 * attentive.
	 */
	static final int MIN_RECALL_DISTANCE = 8;

	/**
	 * The longest: twenty tiles.
	 *
	 * <p>The bound is the loaded scene, not taste. A recall places the follower on the
	 * player's own tile and it steps off from there, so the follower has to have been
	 * somewhere this plugin could still address — and at twenty tiles it is comfortably
	 * inside the 104-tile scene however close to the edge the player is standing.
	 */
	static final int MAX_RECALL_DISTANCE = 20;

	/** Twelve tiles: far enough that a recall is a rescue, close enough to be one. */
	static final int DEFAULT_RECALL_DISTANCE = 12;

	// --- Dialogue timing ------------------------------------------------------
	//
	// Both numbers are wall-clock durations expressed in game ticks, and the units are
	// what got this wrong once already in ../lively-cities: the client's other clock is
	// the *client* tick, Constants.CLIENT_TICK_LENGTH = 20ms, thirty to a game tick, and
	// reading one figure as the other is a thirtyfold error in a number nobody looks at
	// twice. So every constant below is written as milliseconds over the tick length
	// rather than as a bare figure.

	/** Milliseconds in one game tick: 600. */
	static final int TICK_MILLIS = Constants.GAME_TICK_LENGTH;

	/**
	 * How often the follower says something: every 100 ticks, i.e. once a minute.
	 *
	 * <p>{@code ../lively-cities} rolls at the same cadence and then takes a 25% chance
	 * on it, so one of its citizens speaks about every four minutes. There are up to
	 * forty of them in view; there is one follower here, and a companion that says
	 * something every four minutes is a companion nobody notices. Dropping the chance and
	 * keeping the interval is what makes one figure feel present without making it
	 * chatty — and the dial goes down to six seconds for anybody who disagrees.
	 */
	static final int DEFAULT_DIALOGUE_INTERVAL_TICKS = 60_000 / TICK_MILLIS;

	/** The tightest cadence offered: once every 10 ticks, i.e. 6 seconds. */
	static final int MIN_DIALOGUE_INTERVAL_TICKS = 10;

	/**
	 * The loosest: 600 ticks, six minutes. Past this the feature is indistinguishable
	 * from the off switch, which already exists — a dial whose far end duplicates another
	 * control is a dial that misleads.
	 */
	static final int MAX_DIALOGUE_INTERVAL_TICKS = 600;

	/**
	 * How long a line stays on screen: 8 ticks, i.e. 4.8 seconds.
	 *
	 * <p>The longest line this plugin ships is under fifty characters, which is about
	 * three seconds at an unhurried reading pace, and a line that appears while the
	 * player is looking somewhere else needs a second or so on top before anybody starts
	 * reading it.
	 */
	static final int DEFAULT_DIALOGUE_DWELL_TICKS = 4_800 / TICK_MILLIS;

	/** The shortest dwell offered: 5 ticks, 3 seconds — about one read of one line. */
	static final int MIN_DIALOGUE_DWELL_TICKS = 5;

	/**
	 * The longest: 30 ticks, 18 seconds. Past that a line stops reading as somebody
	 * saying something and starts reading as a label stuck to their head — which is what
	 * the name label is for, and it has its own switch.
	 */
	static final int MAX_DIALOGUE_DWELL_TICKS = 30;

	/**
	 * The most custom lines that will be read out of the settings box: 12.
	 *
	 * <p>Not a performance bound — a hundred short strings cost nothing. It is a bound on
	 * the parse, which runs once per game tick, and a statement that this is a text field
	 * rather than a script: somebody who pastes an essay into it gets the first twelve
	 * lines rather than an unbounded list built 100 times a minute.
	 */
	static final int MAX_CUSTOM_LINES = 12;

	/**
	 * The longest a custom line may be: 60 characters, after which it is cut.
	 *
	 * <p>The text is drawn centred over the figure's head with no wrapping, so a long
	 * line is a banner across the viewport rather than a remark. Sixty is comfortably
	 * longer than anything this plugin ships and short enough to stay over the follower
	 * at a normal zoom. Cut rather than refused: a user who typed too much should see
	 * most of it and shorten it, not see nothing and wonder which line was rejected.
	 */
	static final int MAX_CUSTOM_LINE_LENGTH = 60;

	/**
	 * What separates one custom line from the next, and the reason it has no escape.
	 *
	 * <p>The setting was asked for as "comma separated values", so a comma always starts
	 * a new line — there is no way to put one <i>inside</i> a line, and adding a
	 * {@code \,} escape would mean also defining what a bare backslash does, in a text
	 * field with no syntax highlighting and no error reporting. A rule with no exceptions
	 * is one a user can hold in their head; the README says so in as many words, and a
	 * line that wants a pause can use a dash or a semicolon.
	 */
	static final char CUSTOM_LINE_SEPARATOR = ',';

	/**
	 * Who walks with you, in roster order. Never empty, never longer than
	 * {@link #MAX_FOLLOWERS}, and unmodifiable — {@link EntourageScene} keeps the
	 * reference to compare next tick's roster against, so a list a caller could edit
	 * would be a roster change that never got noticed.
	 */
	private final List<FollowerBody> bodies;

	private final int followDistance;
	private final int recallDistance;
	private final EntourageFormation formation;
	private final FollowerFacing facing;
	private final boolean canRun;
	private final boolean stayPut;
	private final EntouragePose idlePose;
	private final boolean hideInInstances;
	private final boolean dialogue;
	private final int dialogueIntervalTicks;
	private final int dialogueDwellTicks;
	private final List<String> customLines;

	private EntourageSettings(List<FollowerBody> bodies, int followDistance, int recallDistance,
		EntourageFormation formation, FollowerFacing facing, boolean canRun, boolean stayPut,
		EntouragePose idlePose, boolean hideInInstances, boolean dialogue,
		int dialogueIntervalTicks, int dialogueDwellTicks, List<String> customLines)
	{
		this.bodies = bodies;
		this.followDistance = followDistance;
		this.recallDistance = recallDistance;
		this.formation = formation;
		this.facing = facing;
		this.canRun = canRun;
		this.stayPut = stayPut;
		this.idlePose = idlePose;
		this.hideInInstances = hideInInstances;
		this.dialogue = dialogue;
		this.dialogueIntervalTicks = dialogueIntervalTicks;
		this.dialogueDwellTicks = dialogueDwellTicks;
		this.customLines = customLines;
	}

	/**
	 * Reads every setting once and clamps the two numbers.
	 *
	 * <p>The three enums are not clamped and cannot be: {@code ConfigManager} resolves
	 * an enum key by name and falls back to the interface default when the stored name
	 * matches nothing, so a garbage value never reaches this method. They are
	 * null-checked all the same — the proxy is not this plugin's code, and a null figure
	 * would be an NPE inside a game-tick handler rather than a wrong-looking follower.
	 */
	static EntourageSettings from(EntourageConfig config)
	{
		EntourageFormation formation = config.formation();
		FollowerFacing facing = config.facing();
		EntouragePose pose = config.idlePose();

		return new EntourageSettings(
			readBodies(config),
			effectiveFollowDistance(config.followDistance()),
			clamp(config.recallDistance(), MIN_RECALL_DISTANCE, MAX_RECALL_DISTANCE),
			formation == null ? EntourageFormation.DEFAULT : formation,
			facing == null ? FollowerFacing.AT_ME : facing,
			config.canRun(),
			config.stayPut(),
			pose == null ? EntouragePose.FIGURE_DEFAULT : pose,
			config.hideInInstances(),
			config.dialogue(),
			effectiveIntervalTicks(config.dialogueIntervalTicks()),
			effectiveDwellTicks(config.dialogueDwellTicks(), config.dialogueIntervalTicks()),
			parseLines(config.dialogueLines()));
	}

	/**
	 * The roster, read out of the five figure slots and the custom-id box.
	 *
	 * <p><b>The count decides how many of the five are read, and the ones past it are not
	 * read at all.</b> A slot the user has turned off is a slot whose dropdown still holds
	 * whatever they last set it to — RuneLite has no way to blank one — so "how many" and
	 * "who" have to be separate questions, and the count is the one that answers whether a
	 * figure is in the entourage.
	 *
	 * <p>Each body is {@link #bodyAt}'s answer — the one place that decides whether a slot
	 * is wearing a typed id or its dropdown figure.
	 *
	 * @return the bodies, in roster order, unmodifiable and never empty
	 */
	private static List<FollowerBody> readBodies(EntourageConfig config)
	{
		int followers = effectiveFollowers(config.followers());

		List<FollowerBody> bodies = new ArrayList<>(followers);
		for (int index = 0; index < followers; index++)
		{
			bodies.add(bodyAt(config, index));
		}

		return Collections.unmodifiableList(bodies);
	}

	/**
	 * <b>Whose body one slot wears — the single answer, for every caller.</b>
	 *
	 * <p>This used to be two copies of one ternary: one here, inside {@link #readBodies},
	 * and one in {@link RosterView#of}. Two copies at one slot is survivable; the day the
	 * typed id became available in all five it would have been ten, and the day they
	 * diverged the panel would name one follower while the world drew another — with no
	 * error, in the one plugin whose entire output is which body is standing there. So
	 * "what does slot N wear?" is asked in exactly one place, and both the scene and the
	 * panel ask it.
	 *
	 * <p>Always through {@link FollowerBody#custom}, never a branch on
	 * {@code FollowerBody.preset}: {@code custom} floors its argument at
	 * {@link FollowerBody#NO_CUSTOM_NPC}, and a body built with a floored id is
	 * value-equal to the preset, so the branch would have been two spellings of one
	 * result. That is also where a hand-edited negative id is dealt with — the one place,
	 * so a negative reads as "no typed id" on the panel exactly as it does on the tick
	 * path.
	 *
	 * @param index which slot, 0-based
	 * @return that slot's body: the typed id if it has one, and either way the dropdown
	 * figure it falls back to. Never {@code null}.
	 */
	static FollowerBody bodyAt(EntourageConfig config, int index)
	{
		return FollowerBody.custom(customNpcIdAt(config, index), figureAt(config, index));
	}

	/**
	 * @param index which slot, 0-based
	 * @return the id typed into that slot's box, or {@link FollowerBody#NO_CUSTOM_NPC}.
	 * Raw: the floor lives in {@link FollowerBody#custom}, and a second one here would be
	 * the same rule written twice with neither copy falsifiable on its own.
	 *
	 * <p>Package-private for the reason {@link #figureAt} is, and it is the same failure:
	 * a caller that read slot 4's id out of {@code customNpcId5()} would draw one NPC on a
	 * card and spawn another in the world.
	 */
	static int customNpcIdAt(EntourageConfig config, int index)
	{
		switch (index)
		{
			case 1:
				return config.customNpcId2();
			case 2:
				return config.customNpcId3();
			case 3:
				return config.customNpcId4();
			case 4:
				return config.customNpcId5();
			default:
				return config.customNpcId();
		}
	}

	/**
	 * The roster count this plugin will actually use for a configured value.
	 *
	 * <p>Package-private and named rather than inlined, because {@link RosterView} needs
	 * the same answer to decide which of the five slot cards are in play — and a second
	 * clamp written over there would be the same rule twice, with neither copy falsifiable
	 * on its own. The panel drawing four active cards while the scene spawned five is
	 * exactly the disagreement one shared answer makes impossible.
	 *
	 * @return the value inside {@link #MIN_FOLLOWERS} .. {@link #MAX_FOLLOWERS}
	 */
	static int effectiveFollowers(int requested)
	{
		return clamp(requested, MIN_FOLLOWERS, MAX_FOLLOWERS);
	}

	/**
	 * The follow distance this plugin will actually use for a configured value.
	 *
	 * @return the value inside {@link #MIN_FOLLOW_DISTANCE} ..
	 * {@link #MAX_FOLLOW_DISTANCE}. Shared with {@link RosterView} and {@link RosterEdit}
	 * for the reason {@link #effectiveFollowers(int)} gives.
	 */
	static int effectiveFollowDistance(int requested)
	{
		return clamp(requested, MIN_FOLLOW_DISTANCE, MAX_FOLLOW_DISTANCE);
	}

	/**
	 * @param index which slot, 0-based
	 * @return the figure that slot names. Null-checked for the same reason the enums above
	 * are: {@code ConfigManager} resolves an unknown enum name to the interface default
	 * rather than to null, so this guards an implementation this plugin does not own — and
	 * the cost of being wrong is an NPE inside a game-tick handler rather than a
	 * wrong-looking follower.
	 *
	 * <p>Package-private so {@link RosterView} reads a slot the same way the scene does.
	 * The mapping from a slot number to one of five differently-named getters is the sort
	 * of thing that is copied wrong once and then disagrees forever: a panel that read
	 * slot 4 out of {@code figure5()} would show the wrong figure on a card and write the
	 * right one to the profile.
	 */
	static EntourageFigure figureAt(EntourageConfig config, int index)
	{
		EntourageFigure figure;
		switch (index)
		{
			case 1:
				figure = config.figure2();
				break;
			case 2:
				figure = config.figure3();
				break;
			case 3:
				figure = config.figure4();
				break;
			case 4:
				figure = config.figure5();
				break;
			default:
				figure = config.figure();
				break;
		}

		return figure == null ? EntourageFigure.defaultAt(index) : figure;
	}

	/**
	 * The interval this plugin will actually use for a configured value.
	 *
	 * @return the value inside {@link #MIN_DIALOGUE_INTERVAL_TICKS} ..
	 * {@link #MAX_DIALOGUE_INTERVAL_TICKS}, so that {@link FollowerRemarks#dueAt} can
	 * never be handed a zero to take a modulo of
	 */
	static int effectiveIntervalTicks(int requested)
	{
		return clamp(requested, MIN_DIALOGUE_INTERVAL_TICKS, MAX_DIALOGUE_INTERVAL_TICKS);
	}

	/**
	 * The dwell this plugin will actually use, which depends on the interval.
	 *
	 * <p><b>A dwell at least as long as the interval is not a bad setting but an
	 * unreachable one</b>, and this is inherited whole from {@code ../lively-cities},
	 * where the same pair shipped saturating once: a line is expired before the follower
	 * is asked whether it is due, so at {@code dwell == interval} it is never silent for
	 * a single tick, and above it the text simply never clears. Both values also arrive
	 * from a hand-edited {@code settings.properties} and from a profile synced off
	 * another install, and neither {@code @Range} can see the other's value — so the
	 * guard lives here, where every read goes through it.
	 *
	 * <p>One tick of silence is the least it can subtract and still be honest, so that is
	 * what it subtracts. The shipped defaults are nowhere near it (8 of 100), and because
	 * {@link #MIN_DIALOGUE_INTERVAL_TICKS} is 10 the clamped value can never fall below
	 * {@link #MIN_DIALOGUE_DWELL_TICKS}, which {@code EntourageSettingsTest} asserts
	 * rather than leaving as a comment.
	 *
	 * @return a dwell inside {@link #MIN_DIALOGUE_DWELL_TICKS} ..
	 * {@link #MAX_DIALOGUE_DWELL_TICKS} that is also strictly less than
	 * {@link #effectiveIntervalTicks(int)} of {@code requestedInterval}
	 */
	static int effectiveDwellTicks(int requestedDwell, int requestedInterval)
	{
		int interval = effectiveIntervalTicks(requestedInterval);
		int dwell = clamp(requestedDwell, MIN_DIALOGUE_DWELL_TICKS, MAX_DIALOGUE_DWELL_TICKS);
		return Math.min(dwell, interval - 1);
	}

	/**
	 * Turns the settings box into lines.
	 *
	 * <p>Splits on {@link #CUSTOM_LINE_SEPARATOR} — always, with no escape, see that
	 * constant — trims each piece, drops the empty ones so that a trailing comma or a
	 * double one costs nothing, cuts anything past
	 * {@link #MAX_CUSTOM_LINE_LENGTH} characters and stops after
	 * {@link #MAX_CUSTOM_LINES}.
	 *
	 * <p>Nothing is stripped or escaped beyond that, and it does not need to be: the
	 * overlay hands the string to {@code Graphics2D.drawString}, which draws characters
	 * rather than parsing the client's {@code <col=..>} markup, so there is no tag for a
	 * user to smuggle in and nothing for one to break.
	 *
	 * @param raw whatever the config holds, which may be {@code null} — the proxy is not
	 *            this plugin's code
	 * @return the lines, in order, never {@code null} and never containing an empty
	 * string
	 */
	static List<String> parseLines(String raw)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return Collections.emptyList();
		}

		List<String> lines = new ArrayList<>();
		int start = 0;
		while (start <= raw.length() && lines.size() < MAX_CUSTOM_LINES)
		{
			int end = raw.indexOf(CUSTOM_LINE_SEPARATOR, start);
			if (end < 0)
			{
				end = raw.length();
			}

			String line = raw.substring(start, end).trim();
			if (!line.isEmpty())
			{
				lines.add(line.length() > MAX_CUSTOM_LINE_LENGTH
					? line.substring(0, MAX_CUSTOM_LINE_LENGTH)
					: line);
			}

			start = end + 1;
		}

		return Collections.unmodifiableList(lines);
	}

	private static int clamp(int value, int min, int max)
	{
		return value < min ? min : (value > max ? max : value);
	}

	/**
	 * @return whose body each follower wears, in roster order. Never empty, never longer
	 * than {@link #MAX_FOLLOWERS}, and unmodifiable.
	 */
	List<FollowerBody> getBodies()
	{
		return bodies;
	}

	/** @return how many followers there are, 1..{@link #MAX_FOLLOWERS} */
	int getRosterSize()
	{
		return bodies.size();
	}

	/** @return how many tiles out the formation slot sits, 1..2 */
	int getFollowDistance()
	{
		return followDistance;
	}

	/** @return how far behind the player a follower may get before it is put back, 8..20 */
	int getRecallDistance()
	{
		return recallDistance;
	}

	/** @return the shape the entourage wants to stand in */
	EntourageFormation getFormation()
	{
		return formation;
	}

	/** @return which way the follower points once it has stopped walking */
	FollowerFacing getFacing()
	{
		return facing;
	}

	/** @return whether the entourage is taken off the screen inside an instance */
	boolean hideInInstances()
	{
		return hideInInstances;
	}

	/** @return whether the follower says anything at all */
	boolean isDialogue()
	{
		return dialogue;
	}

	/** @return game ticks between lines, already clamped */
	int getDialogueIntervalTicks()
	{
		return dialogueIntervalTicks;
	}

	/**
	 * @return how many game ticks a line stays up, already clamped — and always at least
	 * one tick shorter than {@link #getDialogueIntervalTicks()}, so a follower is never
	 * permanently mid-sentence
	 */
	int getDialogueDwellTicks()
	{
		return dialogueDwellTicks;
	}

	/** @return the lines typed into the settings box, in order. Empty if there are none. */
	List<String> getCustomLines()
	{
		return customLines;
	}

	/**
	 * What this figure may say, this tick.
	 *
	 * <p><b>Custom lines replace the presets rather than adding to them</b>, and that is
	 * the choice rather than an accident of implementation. A box that appended would
	 * mean somebody who typed three lines of their own heard them one time in three
	 * against the figure's own — so the setting would be "add some lines", which is not
	 * what anyone types a list into a box for, and there would be no way at all to say
	 * "only these". Replacing gives exactly what is in the box and nothing else, which is
	 * both the more useful answer and the one you can check by reading the box. Emptying
	 * it brings the presets back, so nothing is lost by trying it.
	 *
	 * @param figure whose presets to fall back on
	 * @return the lines to draw from, never {@code null}
	 */
	List<String> linesFor(EntourageFigure figure)
	{
		return customLines.isEmpty() ? FigureLines.of(figure) : customLines;
	}

	/** @return whether the follower may cover two tiles in a game tick to keep up */
	boolean canRun()
	{
		return canRun;
	}

	/**
	 * @return whether the entourage is parked where it stands rather than following.
	 *
	 * <p>This is a suppression of the movement subsystem, not a second one: see
	 * {@link FollowerWalk#tick} for the one place it is honoured, and
	 * {@link EntourageConfig#stayPut()} for why it takes the <i>recall</i> with it.
	 */
	boolean isStayPut()
	{
		return stayPut;
	}

	/** @return the pose it holds while standing still */
	EntouragePose getIdlePose()
	{
		return idlePose;
	}
}
