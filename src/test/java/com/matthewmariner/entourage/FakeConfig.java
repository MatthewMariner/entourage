package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import static org.junit.Assert.fail;

/**
 * A settable {@link EntourageConfig}, which is also the {@link ConfigWriter} that writes
 * into it.
 *
 * <p>No mocking framework needed and none available: a RuneLite config is an interface
 * of {@code default} methods, so the defaults come for free and only the parts a test
 * cares about have to be written.
 *
 * <p><b>Every field starts at the real default, taken from the interface rather than
 * retyped.</b> {@code EntourageConfig.super.followDistance()} is the shipped answer, so
 * a test that sets nothing is testing what a fresh install does — and a default changed
 * in the interface changes here with it, instead of leaving a fixture quietly asserting
 * against last month's numbers.
 *
 * <p><b>The two numbers are {@code int} fields with no bounds of their own</b>, on
 * purpose: {@link EntourageSettings} is the thing that clamps, and a fixture that
 * refused an out-of-range value would make the clamp untestable. A profile edited by
 * hand really can hold {@code followDistance=0}.
 *
 * <h2>Why the same object is the writer</h2>
 *
 * <p>Because "the setting still round-trips" is the promise the panel has to keep, and it
 * is only a promise a test can check if the write and the read go through the same object
 * they do in the client. {@link #write} parses a key exactly as {@code ConfigManager}
 * would — an enum by {@code name()}, an int by {@code parseInt}, {@code null} meaning
 * "remove the key so the default applies" — and the getter above it is what
 * {@link EntourageSettings} reads. So a panel that wrote {@code figure3} when it meant
 * {@code figure4} fails here rather than in a live client.
 *
 * <p><b>An unknown key fails the test rather than being ignored.</b> That is the whole
 * value of this fixture: {@code ConfigManager} accepts any string as a key and stores it
 * happily, so a misspelled key in the plugin is a setting that saves, reloads and does
 * nothing, with no error anywhere. Here it stops the test.
 */
final class FakeConfig implements EntourageConfig, ConfigWriter
{
	/** Every write, in order, as {@code key=value} — {@code key=} for a removal. */
	private final List<String> writes = new ArrayList<>();

	private int followers = EntourageConfig.super.followers();
	private EntourageFigure figure = EntourageConfig.super.figure();
	private EntourageFigure figure2 = EntourageConfig.super.figure2();
	private EntourageFigure figure3 = EntourageConfig.super.figure3();
	private EntourageFigure figure4 = EntourageConfig.super.figure4();
	private EntourageFigure figure5 = EntourageConfig.super.figure5();
	private int customNpcId = EntourageConfig.super.customNpcId();
	private int customNpcId2 = EntourageConfig.super.customNpcId2();
	private int customNpcId3 = EntourageConfig.super.customNpcId3();
	private int customNpcId4 = EntourageConfig.super.customNpcId4();
	private int customNpcId5 = EntourageConfig.super.customNpcId5();
	private String favouriteNpcIds = EntourageConfig.super.favouriteNpcIds();
	private int followDistance = EntourageConfig.super.followDistance();
	private EntourageFormation formation = EntourageConfig.super.formation();
	private FollowerFacing facing = EntourageConfig.super.facing();
	private boolean canRun = EntourageConfig.super.canRun();
	private boolean stayPut = EntourageConfig.super.stayPut();
	private int recallDistance = EntourageConfig.super.recallDistance();
	private EntouragePose idlePose = EntourageConfig.super.idlePose();
	private boolean hideInInstances = EntourageConfig.super.hideInInstances();
	private boolean dialogue = EntourageConfig.super.dialogue();
	private String dialogueLines = EntourageConfig.super.dialogueLines();
	private boolean nameLabel = EntourageConfig.super.nameLabel();
	private DialogueColour dialogueColour = EntourageConfig.super.dialogueColour();
	private DialogueFont dialogueFont = EntourageConfig.super.dialogueFont();
	private int dialogueIntervalTicks = EntourageConfig.super.dialogueIntervalTicks();
	private int dialogueDwellTicks = EntourageConfig.super.dialogueDwellTicks();

	/** @return this config as the snapshot the plugin actually reads, clamping and all */
	EntourageSettings settings()
	{
		return EntourageSettings.from(this);
	}

	/** @return this config as the panel reads it */
	RosterView view()
	{
		return RosterView.of(this);
	}

	/**
	 * The {@link ConfigWriter} half: applies a write the way {@code ConfigManager} would.
	 *
	 * <p>{@code null} removes the key, which is what {@code unsetConfiguration} does, and
	 * here means putting the field back to the interface's own default rather than to zero
	 * or to whatever it happened to hold.
	 */
	@Override
	public void write(String key, @Nullable String value)
	{
		writes.add(key + "=" + (value == null ? "" : value));

		switch (key)
		{
			case EntourageConfig.KEY_FOLLOWERS:
				followers = value == null
					? EntourageConfig.super.followers() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_FIGURE:
				figure = value == null ? EntourageConfig.super.figure() : EntourageFigure.valueOf(value);
				break;
			case EntourageConfig.KEY_FIGURE_2:
				figure2 = value == null ? EntourageConfig.super.figure2() : EntourageFigure.valueOf(value);
				break;
			case EntourageConfig.KEY_FIGURE_3:
				figure3 = value == null ? EntourageConfig.super.figure3() : EntourageFigure.valueOf(value);
				break;
			case EntourageConfig.KEY_FIGURE_4:
				figure4 = value == null ? EntourageConfig.super.figure4() : EntourageFigure.valueOf(value);
				break;
			case EntourageConfig.KEY_FIGURE_5:
				figure5 = value == null ? EntourageConfig.super.figure5() : EntourageFigure.valueOf(value);
				break;
			case EntourageConfig.KEY_CUSTOM_NPC_ID:
				customNpcId = value == null
					? EntourageConfig.super.customNpcId() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_CUSTOM_NPC_ID_2:
				customNpcId2 = value == null
					? EntourageConfig.super.customNpcId2() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_CUSTOM_NPC_ID_3:
				customNpcId3 = value == null
					? EntourageConfig.super.customNpcId3() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_CUSTOM_NPC_ID_4:
				customNpcId4 = value == null
					? EntourageConfig.super.customNpcId4() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_CUSTOM_NPC_ID_5:
				customNpcId5 = value == null
					? EntourageConfig.super.customNpcId5() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_FAVOURITE_NPC_IDS:
				// Stored exactly as written, including a shape Favourites.format would never
				// produce: this fixture is the profile, and the profile is where a
				// hand-edited value comes from. Coercing here would hide the very thing
				// Favourites.parse exists to survive.
				favouriteNpcIds = value == null
					? EntourageConfig.super.favouriteNpcIds() : value;
				break;
			case EntourageConfig.KEY_STAY_PUT:
				stayPut = value == null
					? EntourageConfig.super.stayPut() : Boolean.parseBoolean(value);
				break;
			case EntourageConfig.KEY_FOLLOW_DISTANCE:
				followDistance = value == null
					? EntourageConfig.super.followDistance() : Integer.parseInt(value);
				break;
			case EntourageConfig.KEY_FORMATION:
				formation = value == null
					? EntourageConfig.super.formation() : EntourageFormation.valueOf(value);
				break;
			default:
				// ConfigManager would store this happily and nothing would ever read it,
				// which is a control that saves and does nothing. Here it is a failure.
				fail("nothing reads the config key \"" + key + "\"");
				break;
		}
	}

	/** @return every write this fixture has taken, oldest first, as {@code key=value} */
	List<String> writes()
	{
		return Collections.unmodifiableList(writes);
	}

	/** Forgets the log, so a test can assert on what one gesture wrote. */
	FakeConfig clearWrites()
	{
		writes.clear();
		return this;
	}

	/** The shipped defaults, as a snapshot. */
	static EntourageSettings defaults()
	{
		return new FakeConfig().settings();
	}

	FakeConfig setFigure(EntourageFigure figure)
	{
		this.figure = figure;
		return this;
	}

	/** @param customNpcId the id typed into the first slot's box, or zero to use the dropdown */
	FakeConfig setCustomNpcId(int customNpcId)
	{
		return setCustomNpcIdAt(0, customNpcId);
	}

	/**
	 * One slot's typed id, 0-based.
	 *
	 * @param npcId the id typed into that slot's box, or zero to use the dropdown. Not
	 *              clamped and not refused — {@link FollowerBody#custom} is what floors a
	 *              negative, and a fixture that refused one would make that floor untestable.
	 */
	FakeConfig setCustomNpcIdAt(int index, int npcId)
	{
		switch (index)
		{
			case 1:
				this.customNpcId2 = npcId;
				break;
			case 2:
				this.customNpcId3 = npcId;
				break;
			case 3:
				this.customNpcId4 = npcId;
				break;
			case 4:
				this.customNpcId5 = npcId;
				break;
			default:
				this.customNpcId = npcId;
				break;
		}
		return this;
	}

	/**
	 * The favourites, as the profile holds them.
	 *
	 * @param favouriteNpcIds the raw stored string, <b>including a malformed one</b>. That is
	 *                        the point of taking a string rather than a list: everything
	 *                        {@link Favourites#parse} promises about a blank entry, a
	 *                        negative, a name somebody pasted or a trailing comma is only
	 *                        checkable if a test can put one here.
	 */
	FakeConfig setFavouriteNpcIds(String favouriteNpcIds)
	{
		this.favouriteNpcIds = favouriteNpcIds;
		return this;
	}

	/**
	 * Fills the roster: the count and as many figure slots as there are arguments.
	 *
	 * <p>Both halves at once because they are one question — the four slots past the count
	 * are ignored, so setting a figure without the count is a fixture that quietly tests
	 * nothing, and it is the mistake this method exists to make impossible.
	 */
	FakeConfig setRoster(EntourageFigure... figures)
	{
		this.followers = figures.length;
		for (int index = 0; index < figures.length; index++)
		{
			setFigureAt(index, figures[index]);
		}
		return this;
	}

	/** The count on its own, including values the {@code @Range} would refuse. */
	FakeConfig setFollowers(int followers)
	{
		this.followers = followers;
		return this;
	}

	/** One slot on its own, 0-based, including slots past the count. */
	FakeConfig setFigureAt(int index, EntourageFigure figure)
	{
		switch (index)
		{
			case 1:
				this.figure2 = figure;
				break;
			case 2:
				this.figure3 = figure;
				break;
			case 3:
				this.figure4 = figure;
				break;
			case 4:
				this.figure5 = figure;
				break;
			default:
				this.figure = figure;
				break;
		}
		return this;
	}

	FakeConfig setFollowDistance(int followDistance)
	{
		this.followDistance = followDistance;
		return this;
	}

	FakeConfig setFormation(EntourageFormation formation)
	{
		this.formation = formation;
		return this;
	}

	FakeConfig setCanRun(boolean canRun)
	{
		this.canRun = canRun;
		return this;
	}

	/** @param stayPut true to park the entourage where it stands instead of following */
	FakeConfig setStayPut(boolean stayPut)
	{
		this.stayPut = stayPut;
		return this;
	}

	FakeConfig setRecallDistance(int recallDistance)
	{
		this.recallDistance = recallDistance;
		return this;
	}

	FakeConfig setIdlePose(EntouragePose idlePose)
	{
		this.idlePose = idlePose;
		return this;
	}

	FakeConfig setFacing(FollowerFacing facing)
	{
		this.facing = facing;
		return this;
	}

	FakeConfig setHideInInstances(boolean hideInInstances)
	{
		this.hideInInstances = hideInInstances;
		return this;
	}

	FakeConfig setDialogue(boolean dialogue)
	{
		this.dialogue = dialogue;
		return this;
	}

	FakeConfig setDialogueLines(String dialogueLines)
	{
		this.dialogueLines = dialogueLines;
		return this;
	}

	FakeConfig setNameLabel(boolean nameLabel)
	{
		this.nameLabel = nameLabel;
		return this;
	}

	FakeConfig setDialogueColour(DialogueColour dialogueColour)
	{
		this.dialogueColour = dialogueColour;
		return this;
	}

	FakeConfig setDialogueFont(DialogueFont dialogueFont)
	{
		this.dialogueFont = dialogueFont;
		return this;
	}

	FakeConfig setDialogueIntervalTicks(int dialogueIntervalTicks)
	{
		this.dialogueIntervalTicks = dialogueIntervalTicks;
		return this;
	}

	FakeConfig setDialogueDwellTicks(int dialogueDwellTicks)
	{
		this.dialogueDwellTicks = dialogueDwellTicks;
		return this;
	}

	@Override
	public int followers()
	{
		return followers;
	}

	@Override
	public EntourageFigure figure()
	{
		return figure;
	}

	@Override
	public EntourageFigure figure2()
	{
		return figure2;
	}

	@Override
	public EntourageFigure figure3()
	{
		return figure3;
	}

	@Override
	public EntourageFigure figure4()
	{
		return figure4;
	}

	@Override
	public EntourageFigure figure5()
	{
		return figure5;
	}

	@Override
	public int customNpcId()
	{
		return customNpcId;
	}

	@Override
	public int customNpcId2()
	{
		return customNpcId2;
	}

	@Override
	public int customNpcId3()
	{
		return customNpcId3;
	}

	@Override
	public int customNpcId4()
	{
		return customNpcId4;
	}

	@Override
	public int customNpcId5()
	{
		return customNpcId5;
	}

	@Override
	public String favouriteNpcIds()
	{
		return favouriteNpcIds;
	}

	@Override
	public int followDistance()
	{
		return followDistance;
	}

	@Override
	public EntourageFormation formation()
	{
		return formation;
	}

	@Override
	public boolean canRun()
	{
		return canRun;
	}

	@Override
	public boolean stayPut()
	{
		return stayPut;
	}

	@Override
	public int recallDistance()
	{
		return recallDistance;
	}

	@Override
	public EntouragePose idlePose()
	{
		return idlePose;
	}

	@Override
	public FollowerFacing facing()
	{
		return facing;
	}

	@Override
	public boolean hideInInstances()
	{
		return hideInInstances;
	}

	@Override
	public boolean dialogue()
	{
		return dialogue;
	}

	@Override
	public String dialogueLines()
	{
		return dialogueLines;
	}

	@Override
	public boolean nameLabel()
	{
		return nameLabel;
	}

	@Override
	public DialogueColour dialogueColour()
	{
		return dialogueColour;
	}

	@Override
	public DialogueFont dialogueFont()
	{
		return dialogueFont;
	}

	@Override
	public int dialogueIntervalTicks()
	{
		return dialogueIntervalTicks;
	}

	@Override
	public int dialogueDwellTicks()
	{
		return dialogueDwellTicks;
	}
}
