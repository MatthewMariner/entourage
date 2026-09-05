package com.matthewmariner.entourage;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Who is saying something, and for how long.
 *
 * <p><b>The cap is the reason this is a class and not four lines inside
 * {@link EntourageScene}.</b> It was written before there was a roster to constrain,
 * because a cap retro-fitted to a crowd is a cap that arrives after the wall of text
 * does; there are up to five followers now, and it is doing the job it was written for.
 * {@code ../lively-cities} learned this the expensive way: overhead chatter was its
 * predecessor's single loudest complaint, and its {@code maxConcurrentRemarks} exists
 * precisely because forty citizens talking at once is not ambience. The shape below is
 * that plugin's pass order — expire, then count, then start up to the cap — with the
 * parts that only make sense for a crowd (a radius, a per-citizen chance, a nearest-first
 * sort) left out rather than imagined.
 *
 * <p><b>The pass order is deliberate.</b> Expiring before counting means a line that ends
 * on the same tick another wants to start does not hold the slot against it; counting
 * before starting means the cap is measured against what is actually on screen rather
 * than against what this pass has done so far.
 *
 * <p><b>The clock is the game tick and nothing else.</b> {@link #onGameTick} is called
 * from {@link EntourageScene#onGameTick()} and from nowhere else, so the cadence cannot
 * be advanced by anything a user does to the settings panel — which matters because
 * RuneLite posts one {@code ConfigChanged} per key, and a chatter clock that advanced on
 * those would run at about thirty times speed for a moment every time somebody switched
 * profiles. This plugin has no {@code ConfigChanged} handler at all, so that is a
 * property of the design rather than a check.
 *
 * <p><b>The hard off switch is applied here and in the overlay.</b> Not
 * belt-and-braces: this runs on the game tick, up to 600ms away, and a toggle that
 * visibly lags the click reads as a toggle that did not work. The overlay's own check is
 * what makes unticking the box empty the screen on the same frame; this one is what stops
 * the follower deciding to speak while it is off, and what clears the state so turning it
 * back on does not resume a line from a minute ago.
 *
 * <p><b>Client-thread-free.</b> Nothing here touches the client; it reads
 * {@link Follower#isActive()}, which does, so callers are on the client thread anyway.
 */
@Slf4j
final class EntourageChatter
{
	/**
	 * How many followers may have a line on screen at the same time: one.
	 *
	 * <p>A constant rather than a setting, and one rather than "all of them". With a
	 * single follower the two were the same number; there are five now, and "everyone
	 * speaks whenever they are due" would be five lines of text stacked over five heads.
	 *
	 * <p><b>Deliberately still one, and not three.</b> {@code ../lively-cities} allows
	 * three concurrent remarks across forty citizens, and the temptation is to read that
	 * as a per-figure ratio and raise this. It is not one: <b>its three are spread over a
	 * town and these five stand within two tiles of each other.</b>
	 * {@link EntourageOverlay} draws each line a fixed
	 * {@link EntourageOverlay#TEXT_HEIGHT} above its own figure, so two lines over two
	 * adjacent followers are two lines overlapping on the same few hundred pixels — which
	 * is not two remarks but one unreadable smear. Five followers at the shipped cadence
	 * still produce five lines a minute between them; the cap only decides whether they
	 * queue, and queueing is what makes them readable. Raising it should turn
	 * {@code theShippedCapIsOneVoiceAtATime} and
	 * {@code neverMoreThanOneFollowerIsTalkingAtOnce} red, both of which assert against
	 * literals on purpose so that it cannot be done by accident.
	 */
	static final int MAX_CONCURRENT_LINES = 1;

	/**
	 * Game ticks since this chatter started. Its own counter rather than
	 * {@code client.getTickCount()}, so the class stays client-free and so the cadence
	 * restarts cleanly on a scene invalidation instead of inheriting a phase from before
	 * the player logged in.
	 */
	private int tick;

	/**
	 * One game tick of dialogue.
	 *
	 * @param followers the live roster — filtered here on {@link Follower#isActive()}, so
	 *                  a follower that is not on screen cannot be talking
	 * @param settings  this tick's configuration: the off switch, the cadence, and which
	 *                  lines are in play
	 * @return how many followers have a line on screen when this returns
	 */
	int onGameTick(List<Follower> followers, EntourageSettings settings)
	{
		tick++;

		if (!settings.isDialogue())
		{
			// Clearing rather than merely not rolling is the difference between "off" and
			// "off in a minute".
			silence(followers);
			return 0;
		}

		int interval = settings.getDialogueIntervalTicks();
		int dwell = settings.getDialogueDwellTicks();

		int talking = 0;
		int started = 0;

		// Two passes over the same list rather than one, so that the cap is applied
		// against every follower already talking and not merely against the ones earlier
		// in the roster than the candidate.
		for (int i = 0; i < followers.size(); i++)
		{
			FollowerRemarks remarks = followers.get(i).getRemarks();

			if (!followers.get(i).isActive())
			{
				// Not on screen. despawn() already cleared it; this is the belt to that
				// braces, and it costs a field read.
				remarks.clear();
				continue;
			}

			remarks.expire(tick);
			if (remarks.isTalking())
			{
				talking++;
			}
		}

		for (int i = 0; i < followers.size() && talking < MAX_CONCURRENT_LINES; i++)
		{
			Follower follower = followers.get(i);
			FollowerRemarks remarks = follower.getRemarks();

			if (!follower.isActive() || remarks.isTalking() || !remarks.dueAt(tick, interval))
			{
				continue;
			}

			remarks.say(tick, dwell, settings.linesFor(follower.getFigure()));
			if (remarks.isTalking())
			{
				talking++;
				started++;
			}
		}

		if (started > 0 && log.isDebugEnabled())
		{
			log.debug("chatter: {} line(s) started, {} on screen of at most {}",
				started, talking, MAX_CONCURRENT_LINES);
		}

		return talking;
	}

	/**
	 * Forgets the cadence and empties every line. Called when the scene is invalidated or
	 * torn down, so that a fresh login starts a fresh phase rather than inheriting one
	 * from the last world — and so nothing is left holding a string a despawned follower
	 * was saying.
	 */
	void reset(List<Follower> followers)
	{
		tick = 0;
		silence(followers);
	}

	/** @return the chatter clock, for the tests and the log lines */
	int getTick()
	{
		return tick;
	}

	private static void silence(List<Follower> followers)
	{
		for (int i = 0; i < followers.size(); i++)
		{
			followers.get(i).getRemarks().clear();
		}
	}
}
