package com.matthewmariner.entourage;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * The dev client's entry point — {@code ./gradlew run} launches this {@code main} on
 * {@code sourceSets.test.runtimeClasspath}, per {@code build.gradle}'s
 * {@code pluginMainClass}.
 *
 * <p>Despite the name, this is not a JUnit test: it has no {@code @Test} method and
 * Gradle's test-class detection does not pick it up as one (it looks for JUnit markers,
 * not for the filename pattern). The name mirrors {@code LivelyCitiesPluginTest} and
 * {@code GunnarsToolsPluginTest}, which mirror the official {@code runelite/example-plugin}
 * template this convention comes from.
 *
 * <p>It lives in the test source set because it is developer tooling: the shipped jar is
 * what the Plugin Hub's automated review scans, and a {@code RuneLite.main} entry point
 * has no business in it.
 */
public class EntouragePluginTest
{
	// loadBuiltin's varargs parameter is Class<? extends Plugin>..., so passing a single
	// Class literal still triggers "generic array created for a varargs parameter" at the
	// call site. Suppressed here rather than left as build noise for a warning nothing
	// here can fix.
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EntouragePlugin.class);
		RuneLite.main(args);
	}
}
