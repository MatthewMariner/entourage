package com.matthewmariner.entourage;

/**
 * The roster panel's place in RuneLite's sidebar: put it there, take it away, tell it that
 * something it draws has moved.
 *
 * <p>Behind an interface for the same reason as {@link OverlayRegistry} one file over — a
 * {@code ClientToolbar} cannot be constructed from a test, its only constructor is private
 * and there is no mocking framework on this classpath — and for one more that matters as
 * much: <b>the thing on the other side of it is Swing</b>. The promise being kept here is
 * {@code shutDown()} leaves nothing registered, and that promise has to hold on a build
 * machine with no display. A lifecycle test that had to build a real {@code PluginPanel}
 * and a real toolbar would be a lifecycle test that cannot run there.
 *
 * <p>{@code EntouragePluginLifecycleTest} therefore holds the button to exactly the
 * standard the overlay and the {@code RuneLiteObject}s are held to: added once by
 * {@code startUp}, taken away by {@code shutDown}, counted off a recording implementation
 * rather than read off the source. A navigation button left in the toolbar after the plugin
 * is disabled is a button that opens a panel writing settings for a plugin that is not
 * running.
 */
interface SidePanel
{
	/** Adds the roster panel to the sidebar. */
	void show();

	/** Takes it away again. What {@code shutDown()} calls. */
	void hide();

	/**
	 * A setting the panel draws has changed somewhere else: draw it again.
	 *
	 * <p><b>Called from the client thread</b>, because the change that prompts it arrives on
	 * the event bus — somebody moving a dial in RuneLite's own settings screen, or a profile
	 * being switched. An implementation that touches Swing has to hop to the event dispatch
	 * thread itself rather than making every caller remember to, which is the whole reason
	 * this is a method on an interface and not a direct call into the panel.
	 */
	void refresh();
}
