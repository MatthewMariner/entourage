package com.matthewmariner.entourage;

import javax.annotation.Nullable;

/**
 * The one way this plugin writes its own settings.
 *
 * <p><b>Why an interface and not {@code ConfigManager} itself.</b> Its only constructor is
 * private — it takes eight collaborators including a {@code ScheduledExecutorService}, an
 * {@code EventBus} and a {@code SessionManager} — so it can be neither constructed nor
 * subclassed, and this repo has no mocking framework on the classpath and does not use
 * reflection. Anything that took one directly would be a class no test could ever build.
 * One method behind an interface makes the whole write path testable against a map and
 * leaves exactly one line of untestable glue, the {@code @Provides} in
 * {@link EntouragePlugin}. It is the same seam {@link OverlayRegistry} and
 * {@link SidePanel} are, for the same reason, and the same one {@code ../lively-cities}
 * uses.
 *
 * <p><b>The config stays the source of truth, and this is what keeps it that way.</b>
 * {@link EntourageRosterPanel} owns no state of its own: every control on it writes
 * through here into the user's profile and then reads the profile back to redraw. So the
 * settings screen and the panel cannot disagree, a change made in either shows up in the
 * other, and everything persists through RuneLite's normal profile mechanism without this
 * plugin inventing a second store.
 *
 * <p><b>The group is not a parameter.</b> Every key this plugin writes lives in
 * {@link EntourageConfig#GROUP}; making the group an argument would only create the
 * possibility of writing into somebody else's.
 */
@FunctionalInterface
interface ConfigWriter
{
	/**
	 * @param key   a key constant from {@link EntourageConfig} — {@code KEY_FIGURE} and
	 *              friends. Never a string spelled out at the call site: a key nothing
	 *              reads is a control that appears to work and persists nothing.
	 * @param value the new value, or {@code null} to remove the key entirely so the
	 *              {@code @ConfigItem} default applies again. Removing is not the same as
	 *              writing the default: a key left in the profile shows up as a user
	 *              override forever, so for "there is no typed id here" the honest end
	 *              state is "the user has no setting" rather than "the user has explicitly
	 *              chosen zero".
	 */
	void write(String key, @Nullable String value);
}
