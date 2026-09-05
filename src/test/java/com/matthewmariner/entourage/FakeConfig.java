package com.matthewmariner.entourage;

/**
 * A settable {@link EntourageConfig}.
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
 */
final class FakeConfig implements EntourageConfig
{
	private EntourageFigure figure = EntourageConfig.super.figure();
	private int followDistance = EntourageConfig.super.followDistance();
	private FormationSlot formationSlot = EntourageConfig.super.formationSlot();
	private FollowerFacing facing = EntourageConfig.super.facing();
	private boolean canRun = EntourageConfig.super.canRun();
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

	FakeConfig setFollowDistance(int followDistance)
	{
		this.followDistance = followDistance;
		return this;
	}

	FakeConfig setFormationSlot(FormationSlot formationSlot)
	{
		this.formationSlot = formationSlot;
		return this;
	}

	FakeConfig setCanRun(boolean canRun)
	{
		this.canRun = canRun;
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
	public EntourageFigure figure()
	{
		return figure;
	}

	@Override
	public int followDistance()
	{
		return followDistance;
	}

	@Override
	public FormationSlot formationSlot()
	{
		return formationSlot;
	}

	@Override
	public boolean canRun()
	{
		return canRun;
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
