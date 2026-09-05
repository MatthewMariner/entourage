package com.matthewmariner.entourage;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;

/**
 * One follower's line: which one it is saying, and until when.
 *
 * <p>Built and owned by {@link Follower}, one per follower, exactly like
 * {@link FollowerWalk}. {@link EntourageChatter} drives it once per game tick and
 * {@link EntourageOverlay} reads it once per frame. This is the same split
 * {@code CitizenRemarks} in {@code ../lively-cities} arrived at after its predecessor
 * shipped the two loudest overhead-text bugs there are:
 *
 * <ul>
 *   <li><b>What is being said</b> has a lifetime in game ticks, so it is state, and it
 *       belongs on the thing that is saying it. {@link Follower#despawn()} clears it,
 *       which is what makes an orphaned line — text floating with nobody under it —
 *       impossible rather than unlikely: there is no separate list for a despawned
 *       follower to stay in.</li>
 *   <li><b>Where it is drawn</b> is not state at all. The overlay recomputes it from the
 *       object's live position every frame and caches nothing, so text cannot drift away
 *       from the figure it belongs to.</li>
 * </ul>
 *
 * <p><b>The randomness is seeded from the figure, and not from the clock.</b> Two
 * reasons, and the second is about a roster this plugin does not have yet. A fixed seed
 * makes a follower's order of lines the same every session, so a test can assert
 * something about a thousand ticks. And seeding <i>per figure</i> means that when there
 * is more than one follower, two of them ticking on the same tick draw from two different
 * streams — the failure it avoids is two figures saying their lines in lockstep forever,
 * which is what one shared {@link Random} would produce and which nobody could debug from
 * the outside. {@link #spread} is derived the same way, for the same reason: it staggers
 * whose turn it is to speak rather than letting a group all become due together.
 *
 * <p><b>Client-thread-free.</b> Nothing here touches the client.
 */
final class FollowerRemarks
{
	/**
	 * Mixed into the seed so that the line stream is not some other stream that happens
	 * to share the figure's hash. An arbitrary constant; its only requirement is being
	 * non-zero and fixed forever, because changing it changes every figure's order.
	 */
	private static final long SEED_SALT = 0x5AF31C0FFEEL;

	/**
	 * The stream this follower draws its lines from. Seeded from the figure's own name —
	 * {@code String.hashCode} is specified by the language, so it is the same number on
	 * every machine and in every session.
	 */
	private final Random random;

	/**
	 * The stagger, so that two followers are not due on the same tick — see
	 * {@link #dueAt}. Derived from the figure rather than drawn from {@link #random}, so
	 * it does not depend on how many times this follower has already spoken.
	 */
	private final long spread;

	@Nullable
	private String current;

	/** Only meaningful while {@link #current} is non-null. */
	private int expiresAtTick;

	/**
	 * Which line was said last, so the next one is a different one — see
	 * {@link #nextIndex}. {@code -1} until it has said anything.
	 */
	private int lastIndex = -1;

	FollowerRemarks(EntourageFigure figure)
	{
		long identity = figure.name().hashCode();
		this.random = new Random(identity ^ SEED_SALT);
		this.spread = identity;
	}

	/** @return true while a line is on screen */
	boolean isTalking()
	{
		return current != null;
	}

	/** @return the line being said, or {@code null} */
	@Nullable
	String text()
	{
		return current;
	}

	/**
	 * Stops talking now. Called by {@link Follower#despawn()} and by the hard off switch,
	 * and safe to call when there is nothing to stop.
	 */
	void clear()
	{
		current = null;
	}

	/**
	 * Ends a line that has been up for its dwell.
	 *
	 * @return true if this call ended one
	 */
	boolean expire(int tick)
	{
		if (current != null && tick >= expiresAtTick)
		{
			current = null;
			return true;
		}
		return false;
	}

	/**
	 * Whether this follower's turn to speak falls on this tick.
	 *
	 * <p>Staggered per figure rather than synchronised: with one follower that only
	 * decides which second of the minute it speaks on, and with several it is the
	 * difference between a conversation and a chorus.
	 *
	 * <p>{@code Math.floorMod} rather than {@code %} because the stagger is subtracted
	 * from the tick and the difference is routinely negative. Note that it is <i>not</i>
	 * load-bearing as written: the result is only compared against zero, and
	 * {@code a % n == 0} agrees with {@code floorMod(a, n) == 0} for every positive
	 * {@code n}, so a mutation pass swapping them leaves the suite green. It stays for the
	 * reader, and because it is the version that keeps working if the comparison ever
	 * becomes something other than zero.
	 *
	 * @param interval the interval in game ticks, already through
	 *                 {@link EntourageSettings#effectiveIntervalTicks(int)}, so never
	 *                 zero — this takes a modulo of it
	 */
	boolean dueAt(int tick, int interval)
	{
		return Math.floorMod(tick - spread, interval) == 0;
	}

	/**
	 * Picks a line and starts its dwell.
	 *
	 * @param tick       the current game tick
	 * @param dwellTicks how long it stays up
	 * @param lines      what this follower may say — the figure's own, or the user's, as
	 *                   {@link EntourageSettings#linesFor} decides. An empty list is a
	 *                   no-op rather than a throw: this runs inside a game-tick handler,
	 *                   and the caller checking is not a reason for this to be unable to.
	 */
	void say(int tick, int dwellTicks, List<String> lines)
	{
		if (lines.isEmpty())
		{
			return;
		}

		int index = nextIndex(lines.size());
		current = lines.get(index);
		lastIndex = index;
		expiresAtTick = tick + dwellTicks;
	}

	/**
	 * Draws a line index that is not the one drawn last time.
	 *
	 * <p><b>Not a re-roll, which would only make a repeat less likely.</b> It draws
	 * uniformly from the other {@code size - 1} lines and then skips over the last one,
	 * so an immediate repeat is impossible rather than rare — the failure being avoided
	 * is a follower with four lines saying the same one twice in a row, which reads as
	 * the feature being broken rather than as a coincidence.
	 *
	 * <p>The {@code lastIndex >= size} case is a user emptying the settings box, or
	 * editing it down to fewer lines, while a follower is standing there: the remembered
	 * index then points past the end of the new list and is treated as "nothing said
	 * yet".
	 */
	private int nextIndex(int size)
	{
		if (size == 1)
		{
			return 0;
		}

		if (lastIndex < 0 || lastIndex >= size)
		{
			return random.nextInt(size);
		}

		int index = random.nextInt(size - 1);
		return index >= lastIndex ? index + 1 : index;
	}
}
