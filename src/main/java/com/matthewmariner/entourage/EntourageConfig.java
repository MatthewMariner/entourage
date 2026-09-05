package com.matthewmariner.entourage;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

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
 *   <li><b>A roster size.</b> The plugin spawns one figure. Growing that is a
 *       formation problem — where the second one stands, how they avoid each other,
 *       what happens when only one of them can reach its slot — and a number here would
 *       promise it before any of that exists.</li>
 *   <li><b>A master on/off.</b> That is the plugin's own toggle in the plugin list. A
 *       second one would only be a way for the two to disagree.</li>
 *   <li><b>A walking emote.</b> See {@link EntouragePose} — the client's second
 *       animation slot is never advanced for a {@code RuneLiteObject}, so a walk parked
 *       in it would freeze on its first frame.</li>
 *   <li><b>Anything about the player's own gear.</b> Dressing the follower in what you
 *       are wearing needs a cache decoder that does not exist yet; a setting for it now
 *       would be a switch wired to nothing.</li>
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
		keyName = "figure",
		name = "Figure",
		description = "Whose body the follower wears. Every one of these is built from the game's "
			+ "own cache, and each carries the stand and walk animations that NPC actually uses — "
			+ "so a figure holding a polearm walks like one. Changing this rebuilds the follower "
			+ "on the next game tick.",
		position = 1
	)
	default EntourageFigure figure()
	{
		return EntourageFigure.DEFAULT;
	}

	@ConfigItem(
		keyName = "hideInInstances",
		name = "Hide in instances",
		description = "Takes the follower off the screen anywhere the game hands out its own private "
			+ "copy of an area — a raid, a quest cutscene, the Inferno. Off by default, because a "
			+ "Player Owned House is an instance too and that is where a cosmetic follower is most "
			+ "wanted.",
		position = 2
	)
	default boolean hideInInstances()
	{
		return false;
	}

	// --- Movement ------------------------------------------------------------

	@ConfigItem(
		keyName = "followDistance",
		name = "Follow distance",
		description = "How many tiles away the follower stands, in tiles. One is at your shoulder; "
			+ "two gives it room and makes it more likely to get caught on a doorway, because it "
			+ "walks greedily towards its spot rather than pathfinding around obstacles.",
		position = 1,
		section = movementSection
	)
	@Range(min = EntourageSettings.MIN_FOLLOW_DISTANCE, max = EntourageSettings.MAX_FOLLOW_DISTANCE)
	default int followDistance()
	{
		return EntourageSettings.DEFAULT_FOLLOW_DISTANCE;
	}

	@ConfigItem(
		keyName = "formationSlot",
		name = "Stands",
		description = "Where the follower keeps station: behind you, ahead of you, or on either "
			+ "side. Measured against the way you last walked rather than the way you are facing, so "
			+ "turning on the spot does not send it walking a circle around you — except on \"Ahead "
			+ "of me\", where walking round to the front again is the whole point of the slot.",
		position = 2,
		section = movementSection
	)
	default FormationSlot formationSlot()
	{
		return FormationSlot.BEHIND;
	}

	@ConfigItem(
		keyName = "facing",
		name = "Faces",
		description = "Which way the follower points once it has stopped. At you, the same way you "
			+ "are facing, or a fixed compass direction. While it is walking it faces the way it is "
			+ "walking, whatever this says.",
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
		description = "How far behind you the follower may get, in tiles, before it is put back on "
			+ "your tile instead of walking. This is what stops it being stranded behind a wall it "
			+ "would have to walk away from to get around. Higher means fewer of those pops and "
			+ "longer absences when one is needed.",
		position = 5,
		section = movementSection
	)
	@Range(min = EntourageSettings.MIN_RECALL_DISTANCE, max = EntourageSettings.MAX_RECALL_DISTANCE)
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
	default int dialogueDwellTicks()
	{
		return EntourageSettings.DEFAULT_DIALOGUE_DWELL_TICKS;
	}
}
