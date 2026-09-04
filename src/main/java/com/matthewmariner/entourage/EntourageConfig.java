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
 * clamped, against the same constants the annotations below use, and it is the only
 * thing the rest of the plugin reads.
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
		description = "Which side of you the follower keeps to. Measured against the way you last "
			+ "walked rather than the way you are facing, so turning on the spot does not send it "
			+ "walking a circle around you.",
		position = 2,
		section = movementSection
	)
	default FormationSlot formationSlot()
	{
		return FormationSlot.BEHIND;
	}

	@ConfigItem(
		keyName = "canRun",
		name = "Can run",
		description = "Lets the follower cover two tiles in a game tick when it has fallen behind, "
			+ "which is what running is. Without it a running player outruns the follower by a tile "
			+ "every tick and it has to be recalled every few seconds. Turn it off if you would "
			+ "rather it never moved faster than a walk.",
		position = 3,
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
		position = 4,
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
}
