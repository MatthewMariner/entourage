package com.matthewmariner.entourage;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * The plugin's dials.
 *
 * <p><b>{@code keyName}s are permanent.</b> They are what RuneLite writes into the
 * user's profile, so a rename silently resets that setting for everyone who had it —
 * with no error and nothing in a log. Every key here is a machine name chosen
 * independently of the label above it, and none of them may be reused for a different
 * question. Renaming a <i>label</i> or a <i>description</i> is free; renaming a key
 * needs a migration.
 *
 * <p><b>Nothing here is enforced by RuneLite.</b> {@link Range} bounds the spinner in
 * the settings panel and nothing else: the value that comes back out of the proxy is
 * whatever the profile holds. {@link EntourageSettings} is where the numbers are
 * clamped, against the same constants the annotations below use, and it is what
 * everything on the game-tick path reads.
 *
 * <p><b>{@link EntourageOverlay} is the one exception, and it has to be.</b> It draws
 * between game ticks, so a snapshot taken at the top of the tick would make a change of
 * colour, or a flipped switch, take up to 600ms to show — which reads as a setting that
 * did not work. It therefore reads the four items it draws with straight off this proxy,
 * and checks the two switches first so that "off" costs two map lookups a frame.
 *
 * <p><b>What is deliberately absent.</b>
 * <ul>
 *   <li><b>A way to blank a figure slot.</b> The roster is a count plus five dropdowns,
 *       not five dropdowns one of which may say "none". A "none" entry would have to live
 *       in {@link EntourageFigure}, where every other constant is a body that resolves,
 *       carries three animations and has a display name — and every test that walks that
 *       enum would have to grow an exception for the one member that has none of those.
 *       A count answers "how many walk with me" in one control and leaves the figure enum
 *       meaning exactly one thing.</li>
 *   <li><b>A per-follower distance, slot or pose.</b> Five copies of every movement
 *       setting is thirty dropdowns to describe a shape that
 *       {@link EntourageFormation} names in one. The formation decides where each
 *       follower stands; these settings apply to all of them.</li>
 *   <li><b>A master on/off.</b> That is the plugin's own toggle in the plugin list. A
 *       second one would only be a way for the two to disagree — which is also why the
 *       roster count starts at one rather than at zero.</li>
 *   <li><b>A walking emote.</b> See {@link EntouragePose} — the client's second
 *       animation slot is never advanced for a {@code RuneLiteObject}, so a walk parked
 *       in it would freeze on its first frame.</li>
 *   <li><b>Anything about the player's own gear.</b> Dressing the follower in what you
 *       are wearing needs turning kit and equipment ids into model ids, which is the
 *       client's own item resolution reproduced — see {@link FollowerAppearance}. There
 *       <i>is</i> a cache decoder in the plugin now ({@link NpcRecord}), and it is
 *       deliberately no help here: it reads four animation ids out of one NPC record and
 *       knows nothing about items.</li>
 *   <li><b>Five custom NPC ids.</b> {@link #customNpcId()} replaces the first slot and
 *       only the first, because the first is the one that always exists — the roster
 *       starts at one — and because five more numbered boxes would double the roster
 *       section to describe something four of the dropdowns already do. Mixing one typed
 *       id with four presets is the case that was asked for; five typed ids is a second
 *       feature, and it can be five keys beside this one whenever anybody wants it.</li>
 * </ul>
 */
@ConfigGroup(EntourageConfig.GROUP)
public interface EntourageConfig extends Config
{
	/**
	 * The config group, i.e. the prefix on every key in the user's profile.
	 *
	 * <p>Specific rather than generic, per this repo's {@code AGENTS.md}: it is the
	 * plugin's own name and nothing else's.
	 */
	String GROUP = "entourage";

	// --- The keys, as constants ----------------------------------------------
	//
	// Every keyName below is written once, here, and referenced from its own annotation.
	// Two reasons, and the second is new:
	//
	//   1. EntourageConfigTest could never see a keyName, because reading an annotation
	//      means reflection and this repo forbids it. A constant is a literal a test can
	//      assert against, which turns "a rename silently resets everyone's setting" from
	//      a rule in a comment into a rule that goes red.
	//   2. EntourageRosterPanel writes these keys back through ConfigWriter. A panel that
	//      spelled its own copy of "figure3" would write a key nothing reads — a control
	//      that appears to work, persists nothing, and says nothing about it. One spelling
	//      shared by the annotation and the writer makes that impossible.
	//
	// Unannotated String fields on a Config interface are inert: ConfigManager's descriptor
	// scan takes only fields carrying @ConfigSection (verified in the 1.12.38 client's
	// getConfigDescriptor), and GROUP has sat here as one since the plugin's first commit.

	/** @see #followers() */
	String KEY_FOLLOWERS = "followers";

	/**
	 * @see #figure()
	 *
	 * <p>"figure" and not "figure1": it is the key this setting has always had, and
	 * renaming it to match its four neighbours would silently reset the one setting every
	 * existing profile has.
	 */
	String KEY_FIGURE = "figure";

	/** @see #figure2() */
	String KEY_FIGURE_2 = "figure2";

	/** @see #figure3() */
	String KEY_FIGURE_3 = "figure3";

	/** @see #figure4() */
	String KEY_FIGURE_4 = "figure4";

	/** @see #figure5() */
	String KEY_FIGURE_5 = "figure5";

	/** @see #customNpcId() */
	String KEY_CUSTOM_NPC_ID = "customNpcId";

	/** @see #followDistance() */
	String KEY_FOLLOW_DISTANCE = "followDistance";

	/**
	 * @see #formation()
	 *
	 * <p>"formationSlot" and not "formation": it is the key this setting has always had.
	 * What it holds is now a whole shape rather than one slot, but renaming the key to
	 * match would silently reset the setting for anybody who has one.
	 */
	String KEY_FORMATION = "formationSlot";

	/**
	 * The units on a distance in tiles.
	 *
	 * <p>{@link Units} names ticks, seconds, minutes, milliseconds, pixels and percent,
	 * and has no constant for a tile — so this is a literal, written once here rather than
	 * once per annotation, and it copies the client's own convention of a leading space
	 * ({@code Units.TICKS} is {@code " ticks"}). Plural regardless of the value, which is
	 * also the client's: its own spinners say "1 ticks".
	 */
	String TILES = " tiles";

	@ConfigSection(
		name = "Roster",
		description = "How many figures walk with you, and whose bodies they wear. "
			+ "The Entourage button in the sidebar does all of this with a search and five "
			+ "cards instead of five dropdowns.",
		position = 5
	)
	String rosterSection = "roster";

	@ConfigSection(
		name = "Movement",
		description = "How close the follower stays, which side it walks on, and whether it can run.",
		position = 10
	)
	String movementSection = "movement";

	@ConfigSection(
		name = "Pose",
		description = "What the follower does while it is standing still.",
		position = 20
	)
	String poseSection = "pose";

	@ConfigSection(
		name = "Dialogue",
		description = "The lines above the follower's head: what it says, how it looks and how often.",
		position = 30
	)
	String dialogueSection = "dialogue";

	@ConfigItem(
		keyName = "hideInInstances",
		name = "Hide in instances",
		description = "Takes the entourage off the screen anywhere the game hands out its own private "
			+ "copy of an area — a raid, a quest cutscene, the Inferno. Off by default, because a "
			+ "Player Owned House is an instance too and that is where a cosmetic follower is most "
			+ "wanted.",
		position = 1
	)
	default boolean hideInInstances()
	{
		return false;
	}

	// --- Roster --------------------------------------------------------------

	@ConfigItem(
		keyName = KEY_FOLLOWERS,
		name = "Followers",
		description = "How many figures walk with you, from one to five. The figure dropdowns below "
			+ "this number are the ones in play; the rest keep whatever they are set to and are "
			+ "ignored. Changing this rebuilds the entourage on the next game tick.",
		position = 1,
		section = rosterSection
	)
	@Range(min = EntourageSettings.MIN_FOLLOWERS, max = EntourageSettings.MAX_FOLLOWERS)
	default int followers()
	{
		return EntourageSettings.DEFAULT_FOLLOWERS;
	}

	@ConfigItem(
		keyName = KEY_FIGURE,
		name = "Figure 1",
		description = "Whose body the first follower wears. Every one of these is built from the "
			+ "game's own cache, and each carries the stand and walk animations that NPC actually "
			+ "uses — so a figure holding a polearm walks like one. Changing this rebuilds the "
			+ "entourage on the next game tick.",
		position = 2,
		section = rosterSection
	)
	default EntourageFigure figure()
	{
		return EntourageFigure.defaultAt(0);
	}

	@ConfigItem(
		keyName = KEY_FIGURE_2,
		name = "Figure 2",
		description = "Whose body the second follower wears. Used when \"Followers\" is at least two.",
		position = 3,
		section = rosterSection
	)
	default EntourageFigure figure2()
	{
		return EntourageFigure.defaultAt(1);
	}

	@ConfigItem(
		keyName = KEY_FIGURE_3,
		name = "Figure 3",
		description = "Whose body the third follower wears. Used when \"Followers\" is at least three.",
		position = 4,
		section = rosterSection
	)
	default EntourageFigure figure3()
	{
		return EntourageFigure.defaultAt(2);
	}

	@ConfigItem(
		keyName = KEY_FIGURE_4,
		name = "Figure 4",
		description = "Whose body the fourth follower wears. Used when \"Followers\" is at least four.",
		position = 5,
		section = rosterSection
	)
	default EntourageFigure figure4()
	{
		return EntourageFigure.defaultAt(3);
	}

	@ConfigItem(
		keyName = KEY_FIGURE_5,
		name = "Figure 5",
		description = "Whose body the fifth follower wears. Used when \"Followers\" is five.",
		position = 6,
		section = rosterSection
	)
	default EntourageFigure figure5()
	{
		return EntourageFigure.defaultAt(4);
	}

	@ConfigItem(
		keyName = KEY_CUSTOM_NPC_ID,
		name = "Custom NPC id",
		description = "Puts any NPC in the first slot instead of whatever \"Figure 1\" says — type its "
			+ "id and leave the rest alone. Zero means \"use the dropdown\", which is where it starts. "
			+ "Unlike the presets, an arbitrary NPC has to be checked before it can be used: the "
			+ "plugin reads that NPC's own stand and walk animations out of the game's cache, and if "
			+ "it has none, or walks with the same animation it stands with, the id is refused and the "
			+ "dropdown figure comes back. Non-human bodies are allowed and may look odd. Changing "
			+ "this rebuilds the entourage on the next game tick.",
		position = 7,
		section = rosterSection
	)
	@Range(min = FollowerBody.NO_CUSTOM_NPC)
	default int customNpcId()
	{
		return FollowerBody.NO_CUSTOM_NPC;
	}

	// --- Movement ------------------------------------------------------------

	@ConfigItem(
		keyName = KEY_FOLLOW_DISTANCE,
		name = "Follow distance",
		description = "How far out the nearest rank of the formation stands, in tiles. One is at "
			+ "your shoulder; two gives them room and makes them more likely to get caught on a "
			+ "doorway, because a follower walks greedily towards its spot rather than pathfinding "
			+ "around obstacles. Ranks behind the first sit one tile further out each.",
		position = 1,
		section = movementSection
	)
	@Range(min = EntourageSettings.MIN_FOLLOW_DISTANCE, max = EntourageSettings.MAX_FOLLOW_DISTANCE)
	@Units(TILES)
	default int followDistance()
	{
		return EntourageSettings.DEFAULT_FOLLOW_DISTANCE;
	}

	@ConfigItem(
		// The enum constant names are fixed for the same reason the key is — see
		// KEY_FORMATION above. ConfigManager stores an enum by name(), so renaming a
		// constant in EntourageFormation resets this setting for anybody who picked it.
		keyName = KEY_FORMATION,
		name = "Formation",
		description = "The shape the entourage stands in. The first four put everybody in a single "
			+ "file or rank in one direction; \"Hangout ring\" spreads them around you facing "
			+ "inward, \"Wedge behind\" trails them in a V, and \"Line abreast\" puts them in a row "
			+ "with you in the middle of it. Every shape is measured against the way you last "
			+ "walked rather than the way you are facing, so turning on the spot does not send them "
			+ "walking a circle around you — except on \"Ahead of me\", where walking round to the "
			+ "front again is the whole point.",
		position = 2,
		section = movementSection
	)
	default EntourageFormation formation()
	{
		return EntourageFormation.DEFAULT;
	}

	@ConfigItem(
		keyName = "facing",
		name = "Faces",
		description = "Which way a follower points once it has stopped. At you, the same way you "
			+ "are facing, or a fixed compass direction. \"At me\" is what makes the hangout ring "
			+ "face inward. While a follower is walking it faces the way it is walking, whatever "
			+ "this says.",
		position = 3,
		section = movementSection
	)
	default FollowerFacing facing()
	{
		return FollowerFacing.AT_ME;
	}

	@ConfigItem(
		keyName = "canRun",
		name = "Can run",
		description = "Lets the follower cover two tiles in a game tick when it has fallen behind, "
			+ "which is what running is. Without it a running player outruns the follower by a tile "
			+ "every tick and it has to be recalled every few seconds. Turn it off if you would "
			+ "rather it never moved faster than a walk.",
		position = 4,
		section = movementSection
	)
	default boolean canRun()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recallDistance",
		name = "Recall at",
		description = "How far behind you a follower may get, in tiles, before it is put back on "
			+ "your tile instead of walking. This is what stops it being stranded behind a wall it "
			+ "would have to walk away from to get around. Higher means fewer of those pops and "
			+ "longer absences when one is needed. It cannot go below eight, because a column of "
			+ "five at the widest follow distance legitimately reaches six tiles back.",
		position = 5,
		section = movementSection
	)
	@Range(min = EntourageSettings.MIN_RECALL_DISTANCE, max = EntourageSettings.MAX_RECALL_DISTANCE)
	@Units(TILES)
	default int recallDistance()
	{
		return EntourageSettings.DEFAULT_RECALL_DISTANCE;
	}

	// --- Pose ----------------------------------------------------------------

	@ConfigItem(
		keyName = "idlePose",
		name = "Idle pose",
		description = "What the follower holds while it is standing still. \"The figure's own\" is "
			+ "whatever that NPC stands with, which is the only choice that stays right when you "
			+ "change the figure. Everything else here loops on purpose — a one-shot emote would "
			+ "freeze on its last frame. The pose is dropped the moment the follower starts walking.",
		position = 1,
		section = poseSection
	)
	default EntouragePose idlePose()
	{
		return EntouragePose.FIGURE_DEFAULT;
	}

	// --- Dialogue ------------------------------------------------------------

	@ConfigItem(
		keyName = "dialogue",
		name = "Overhead lines",
		description = "Lets the follower say something now and then, in a line of text above its "
			+ "head. Turning this off is silence immediately rather than when the line on screen "
			+ "runs out.",
		position = 1,
		section = dialogueSection
	)
	default boolean dialogue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dialogueLines",
		name = "Custom lines",
		description = "Your own lines, separated by commas — \"Hello there, Nice weather, Onward\" is "
			+ "three of them. Anything here REPLACES the figure's own lines rather than adding to "
			+ "them; empty the box to get them back. Every comma starts a new line, so a line cannot "
			+ "contain one.",
		position = 2,
		section = dialogueSection
	)
	default String dialogueLines()
	{
		return "";
	}

	@ConfigItem(
		keyName = "nameLabel",
		name = "Name above head",
		description = "Draws the figure's name above it, in the same colour and font as its lines. "
			+ "When it is saying something, the line sits above the name.",
		position = 3,
		section = dialogueSection
	)
	default boolean nameLabel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "dialogueColour",
		name = "Colour",
		description = "What colour the text is drawn in. Deliberately a short list: every one of "
			+ "these is a colour the game itself never uses for something real, which is what stops "
			+ "a follower's line being mistaken for a player's chat.",
		position = 4,
		section = dialogueSection
	)
	default DialogueColour dialogueColour()
	{
		return DialogueColour.MAGENTA;
	}

	@ConfigItem(
		keyName = "dialogueFont",
		name = "Font",
		description = "Which of the game's own three faces the text is drawn in. \"Large\" is the "
			+ "bold face, which is both heavier and taller.",
		position = 5,
		section = dialogueSection
	)
	default DialogueFont dialogueFont()
	{
		return DialogueFont.REGULAR;
	}

	@ConfigItem(
		keyName = "dialogueIntervalTicks",
		name = "Speaks every",
		description = "How often the follower says something, in game ticks — 100 is a minute, 10 is "
			+ "six seconds. It only ever speaks when it is on screen and has nothing already up.",
		position = 6,
		section = dialogueSection
	)
	@Range(
		min = EntourageSettings.MIN_DIALOGUE_INTERVAL_TICKS,
		max = EntourageSettings.MAX_DIALOGUE_INTERVAL_TICKS
	)
	@Units(Units.TICKS)
	default int dialogueIntervalTicks()
	{
		return EntourageSettings.DEFAULT_DIALOGUE_INTERVAL_TICKS;
	}

	@ConfigItem(
		keyName = "dialogueDwellTicks",
		name = "Stays up for",
		description = "How long a line stays on screen, in game ticks — 8 is just under five "
			+ "seconds. Always shortened to less than \"Speaks every\", because a line that outlasts "
			+ "the gap between lines never clears.",
		position = 7,
		section = dialogueSection
	)
	@Range(
		min = EntourageSettings.MIN_DIALOGUE_DWELL_TICKS,
		max = EntourageSettings.MAX_DIALOGUE_DWELL_TICKS
	)
	@Units(Units.TICKS)
	default int dialogueDwellTicks()
	{
		return EntourageSettings.DEFAULT_DIALOGUE_DWELL_TICKS;
	}
}
