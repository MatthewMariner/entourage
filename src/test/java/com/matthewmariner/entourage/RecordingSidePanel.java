package com.matthewmariner.entourage;

/**
 * A {@link SidePanel} that remembers whether it is in the sidebar and how often it has been
 * told to redraw.
 *
 * <p>The same shape as the recording overlay registry in
 * {@code EntouragePluginLifecycleTest} and for the same reason: a lifecycle promise is only
 * a promise if a test can ask whether it was kept, and the real implementation on the other
 * side of this interface is a Swing panel and a {@code ClientToolbar}, neither of which
 * exists on a build machine with no display.
 *
 * <p>{@link #isShown()} counts adds and removes rather than latching a boolean, because
 * "shutDown removes it" and "shutDown removes it twice" are different facts and only one of
 * them is what {@code ClientToolbar} is being asked for.
 */
final class RecordingSidePanel implements SidePanel
{
	private int shows;
	private int hides;
	private int refreshes;

	@Override
	public void show()
	{
		shows++;
	}

	@Override
	public void hide()
	{
		hides++;
	}

	@Override
	public void refresh()
	{
		refreshes++;
	}

	/** True when it has been added more often than it has been taken away. */
	boolean isShown()
	{
		return shows > hides;
	}

	int showCount()
	{
		return shows;
	}

	int hideCount()
	{
		return hides;
	}

	int refreshCount()
	{
		return refreshes;
	}
}
