package com.matthewmariner.entourage;

import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

/**
 * A real {@link RuneLiteObject} that counts the three calls this slice has to get
 * right.
 *
 * <p>A subclass rather than a stub, because the whole lifecycle contract rests on
 * {@code RuneLiteObject}'s real code: {@code setActive} really does call
 * {@code client.registerRuneLiteObject}, {@code isActive} really does ask the client,
 * and {@code setLocation} really does run {@code Perspective.getTileHeight}. What is
 * added here is bookkeeping only:
 *
 * <ul>
 *   <li>{@link #tickCalls()} — how many times the plugin advanced the animation itself.
 *       The answer has to stay zero: the client calls {@code tick} once per frame for
 *       every registered object, so a second caller runs every animation at double
 *       speed.</li>
 *   <li>{@link #animationControllerInstalls()} — how many times a controller was handed
 *       over. Installing one resets its frame counter, so this has to be the number of
 *       idle-to-walk switches and not the number of ticks. A controller reinstalled
 *       every game tick is an animation that restarts 1.6 times a second, which is what
 *       "it needs animation smoothing" looks like from the outside.</li>
 *   <li>{@link #setLocationCalls()} — how many times the object was moved. The
 *       interesting case is a follower standing still: it must not be re-placed every
 *       frame, because {@code setLocation} runs {@code Perspective.getTileHeight}
 *       against the live scene.</li>
 * </ul>
 */
final class FakeRuneLiteObject extends RuneLiteObject
{
	private int tickCalls;
	private int animationControllerInstalls;
	private int setLocationCalls;

	FakeRuneLiteObject(Client client)
	{
		super(client);
	}

	@Override
	public void setLocation(LocalPoint location, int plane)
	{
		setLocationCalls++;
		super.setLocation(location, plane);
	}

	@Override
	public void tick(int ticksSinceLastFrame)
	{
		tickCalls++;
		super.tick(ticksSinceLastFrame);
	}

	@Override
	public void setAnimationController(AnimationController controller)
	{
		animationControllerInstalls++;
		super.setAnimationController(controller);
	}

	int tickCalls()
	{
		return tickCalls;
	}

	int animationControllerInstalls()
	{
		return animationControllerInstalls;
	}

	int setLocationCalls()
	{
		return setLocationCalls;
	}
}
