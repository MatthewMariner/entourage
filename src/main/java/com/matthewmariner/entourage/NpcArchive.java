package com.matthewmariner.entourage;

import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;

/**
 * Getting one NPC's cache record out of the client, and telling "that id does not exist"
 * apart from "that id is not loaded yet".
 *
 * <p>This is the only part of the custom-NPC feature that touches the client;
 * {@link NpcRecord} — the half that actually reads the bytes — is a pure function, so
 * everything interesting is testable without a game running. <b>Client thread only.</b>
 *
 * <p><b>The API, verified against 1.12.38 rather than remembered.</b>
 * {@code javap net.runelite.api.Client} declares
 * {@code public abstract net.runelite.api.IndexDataBase getIndexConfig();} and
 * {@code javap net.runelite.api.IndexDataBase} declares, in full,
 * {@code boolean isOverlayOutdated()}, {@code int[] getFileIds(int)} and
 * {@code byte[] loadData(int, int)}. {@code getIndexConfig()} is the CONFIGS index,
 * number 2; the NPC archive inside it is number 9, which is
 * {@code net.runelite.cache.ConfigType.NPC}'s own id. One file per NPC id. The bytes come
 * back decompressed, and decrypted for the unencrypted indices this is one of.
 *
 * <p><b>The three outcomes exist because {@link Follower} has to route them differently,
 * and getting that wrong is the whole cost of this class.</b>
 * <ul>
 *   <li>{@link Outcome#UNAVAILABLE} — {@code loadData} answered {@code null}. That does
 *       <b>not</b> mean the group is missing: the client enqueues a fetch and answers
 *       null in the meantime, so a cold cache produces it constantly and it is
 *       <b>retryable</b>. Latching a permanent failure on it would lose the follower for
 *       the session on a slow login. Also what an absent index or an absent file list
 *       gets, because neither can be told from "not warmed up yet" and the retry budget
 *       bounds the cost of guessing wrong.</li>
 *   <li>{@link Outcome#ABSENT} — the archive's own file list does not contain this id.
 *       That is a definite answer that will not change, so the caller may refuse the id
 *       out loud instead of retrying it three times per scene load forever. This is the
 *       whole reason the file list is consulted at all: without it a typo and a cold
 *       cache are the same event.</li>
 *   <li>{@link Outcome#DECODED} — bytes, parsed. Whether they are <i>usable</i> is
 *       {@link NpcRecord#hasWalkCycle()}'s question, not this class's.</li>
 * </ul>
 *
 * <p><b>{@code getFileIds} hands back the client's own array.</b> It is read inside this
 * one call, on the client thread, and never stored — which is the guarantee a defensive
 * copy would buy, without copying sixteen thousand ints on every retry. Anything that
 * ever needs to hold it across a tick has to clone it there, because the client is free
 * to replace the array it is handing out.
 *
 * <p><b>Nothing here throws.</b> Every client call is wrapped: these are cache reads
 * reached from a game-tick handler, and a throw out of one would cost the whole pass.
 */
@Slf4j
final class NpcArchive
{
	/**
	 * The NPC archive's id inside the config index: 9.
	 *
	 * <p>{@code net.runelite.cache.ConfigType.NPC}. Not a {@code gameval} constant because
	 * there is none — {@code net.runelite.api.gameval} names game entities, and this is a
	 * number in the cache's own table of contents.
	 */
	static final int NPC_ARCHIVE_ID = 9;

	/** What came of asking for an id. See the class javadoc. */
	enum Outcome
	{
		/** The record was read and parsed. {@link NpcArchive#getRecord()} is non-null. */
		DECODED,

		/** The archive does not have a file with this id. Permanent; refuse it. */
		ABSENT,

		/** Not resident yet, or the index is not up. Retry. */
		UNAVAILABLE
	}

	private final Outcome outcome;

	@Nullable
	private final NpcRecord record;

	private NpcArchive(Outcome outcome, @Nullable NpcRecord record)
	{
		this.outcome = outcome;
		this.record = record;
	}

	/**
	 * Asks the client for one NPC's cache record.
	 *
	 * @param client the live client. Client thread only.
	 * @param npcId  the id to read
	 * @param label  who is asking, for the log line
	 * @return the outcome, never {@code null}. Writes nothing above debug — the caller
	 * owns the warning, because only it knows whether this is a first attempt or a retry.
	 */
	static NpcArchive read(Client client, int npcId, String label)
	{
		if (npcId < 0)
		{
			// Not a file id at all. Answered here rather than by the scan below so that the
			// caller gets the same permanent refusal it would for a real out-of-range id.
			return new NpcArchive(Outcome.ABSENT, null);
		}

		IndexDataBase index = null;
		int[] fileIds;
		try
		{
			index = client.getIndexConfig();

			// A null index is the ordinary state before a cache is up rather than an error,
			// so it is a branch here and not an NPE for the catch to convert. It is the one
			// check in this method whose removal the outcome cannot show — the catch below
			// answers the same thing — and it is kept anyway, because an exception is not
			// how a plugin should notice that it is at the login screen.
			fileIds = index == null ? null : index.getFileIds(NPC_ARCHIVE_ID);
		}
		catch (RuntimeException e)
		{
			log.debug("{}: the config index would not answer for archive {}",
				label, NPC_ARCHIVE_ID, e);
			fileIds = null;
		}

		if (fileIds == null)
		{
			// Null rather than an empty array is what a bad archive id gets, and it is also
			// what an index that has not loaded its table of contents gets. Treated as the
			// retryable one: the archive id above is a constant, so "not loaded yet" is the
			// only reading that can be true here twice.
			log.debug("{}: archive {} has no file list yet", label, NPC_ARCHIVE_ID);
			return new NpcArchive(Outcome.UNAVAILABLE, null);
		}

		if (!contains(fileIds, npcId))
		{
			log.debug("{}: archive {} lists {} file(s) and none of them is {}",
				label, NPC_ARCHIVE_ID, fileIds.length, npcId);
			return new NpcArchive(Outcome.ABSENT, null);
		}

		byte[] data;
		try
		{
			// Non-null by construction: a file list only came back because there was an
			// index to ask for it.
			data = index.loadData(NPC_ARCHIVE_ID, npcId);
		}
		catch (RuntimeException e)
		{
			log.debug("{}: loading npc {} threw", label, npcId, e);
			return new NpcArchive(Outcome.UNAVAILABLE, null);
		}

		if (data == null)
		{
			// The group is not in memory and the client has queued a fetch for it. Routine
			// on a cold cache, and retryable — never a failure.
			log.debug("{}: npc {} is in the archive but not resident yet", label, npcId);
			return new NpcArchive(Outcome.UNAVAILABLE, null);
		}

		NpcRecord decoded = NpcRecord.decode(data);
		log.debug("{}: npc {} decoded from {} byte(s) to {}", label, npcId, data.length, decoded);
		return new NpcArchive(Outcome.DECODED, decoded);
	}

	/**
	 * A linear scan rather than a binary search: the file list's order is the client's
	 * business, and a sorted-input assumption that is wrong once answers "absent" for an
	 * id that exists. Sixteen thousand comparisons, at most three times per scene load.
	 */
	private static boolean contains(int[] fileIds, int npcId)
	{
		for (int fileId : fileIds)
		{
			if (fileId == npcId)
			{
				return true;
			}
		}
		return false;
	}

	Outcome getOutcome()
	{
		return outcome;
	}

	/** @return the record, or {@code null} unless the outcome is {@link Outcome#DECODED} */
	@Nullable
	NpcRecord getRecord()
	{
		return record;
	}
}
