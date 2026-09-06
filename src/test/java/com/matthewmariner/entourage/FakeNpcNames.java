package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An {@link NpcNames} that answers from a map instead of from a game client.
 *
 * <p><b>Synchronous, and that is the one liberty it takes.</b> The real implementation hops
 * to the client thread and back to Swing; this one calls the consumer inline. Everything the
 * panel does with the answer — merging it, deciding whether anything changed, redrawing only
 * if it did — is identical either way, and the alternative is a test that has to pump two
 * event queues to assert that a label says "Gummy". The hops themselves are one lambda in
 * {@link EntouragePlugin} and are not reachable from a test with no client at all; they are
 * on the "wanted from a real client" list rather than pretended at here.
 *
 * <p><b>An unknown id is absent from the answer rather than mapped to null</b>, which is what
 * {@link NpcNames} promises and what the login screen produces for every id at once. The
 * default fixture knows nothing, so every existing panel test goes on seeing the bare ids it
 * always asserted against.
 */
final class FakeNpcNames implements NpcNames
{
	private final Map<Integer, String> known = new HashMap<>();

	/** Every id this has been asked about, in order, including repeats. */
	private final List<Integer> asked = new ArrayList<>();

	/** Whether to answer at all. Off is a cache that never warms up. */
	private boolean answering = true;

	FakeNpcNames knows(int npcId, String name)
	{
		known.put(npcId, name);
		return this;
	}

	/**
	 * Stops answering — the callback is still made, with nothing in it.
	 *
	 * <p>The distinction matters: {@link NpcNames} promises the consumer is called exactly
	 * once per request whether or not anything resolved, because a caller that only heard
	 * back on success could never tell "still loading" from "there is no such NPC".
	 */
	FakeNpcNames knowsNothing()
	{
		answering = false;
		return this;
	}

	/** @return every id asked about since this fixture was built, in order, with repeats */
	List<Integer> asked()
	{
		return asked;
	}

	@Override
	public void resolve(List<Integer> npcIds, Consumer<Map<Integer, String>> names)
	{
		asked.addAll(npcIds);

		Map<Integer, String> resolved = new HashMap<>();
		if (answering)
		{
			for (int npcId : npcIds)
			{
				String name = known.get(npcId);
				if (name != null)
				{
					resolved.put(npcId, name);
				}
			}
		}

		names.accept(resolved);
	}
}
