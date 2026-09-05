package com.matthewmariner.entourage;

import net.runelite.client.ui.overlay.Overlay;

/**
 * Where an overlay is registered and unregistered.
 *
 * <p><b>An interface rather than {@code OverlayManager} itself, because the manager
 * cannot be stood in for.</b> Its only constructor is private — it takes seven
 * collaborators — so it can be neither constructed nor subclassed, and this repo has no
 * mocking framework on the classpath and does not use reflection.
 *
 * <p>That matters here for exactly the reason the {@code RuneLiteObject} lifecycle
 * matters. "An overlay left in the manager keeps drawing after shutdown" is the same
 * class of leak as "a {@code RuneLiteObject} left active renders forever", and this
 * plugin's teardown promise is already pinned against the client's own registered-object
 * list rather than against its own bookkeeping. Two methods behind an interface are what
 * let {@code EntouragePluginLifecycleTest} hold the overlay to the same standard —
 * {@code startUp} adds it, {@code shutDown} removes it, asserted rather than read off the
 * source. The same seam is what {@code ../lively-cities} uses, for the same reason.
 */
interface OverlayRegistry
{
	void add(Overlay overlay);

	void remove(Overlay overlay);
}
