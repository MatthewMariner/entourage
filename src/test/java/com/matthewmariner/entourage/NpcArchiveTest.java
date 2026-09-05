package com.matthewmariner.entourage;

import static com.matthewmariner.entourage.NpcRecordBytes.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Reading a cache record through the client, and the three outcomes.
 *
 * <p><b>The distinction under test is the one that costs something to get wrong.</b>
 * "This id does not exist" is permanent and has to be refused out loud; "this id is not
 * in memory yet" is a cold cache and has to be retried. Both look like a null coming back
 * from the client, and telling them apart is the only reason this class asks the archive
 * for its file list at all.
 */
public class NpcArchiveTest
{
	private static final String LABEL = "custom (npc 4931)";

	/** An id nothing else in these fixtures uses. */
	private static final int NPC_ID = 4931;

	@Test
	public void readsAndDecodesAResidentRecord()
	{
		FakeClient client = new FakeClient().withIndexConfig(new FakeIndexDataBase()
			.withNpc(NPC_ID, record().standing(808).walking(819).end()));

		NpcArchive read = NpcArchive.read(client, NPC_ID, LABEL);

		assertEquals(NpcArchive.Outcome.DECODED, read.getOutcome());
		assertNotNull(read.getRecord());
		assertEquals(808, read.getRecord().getStandingAnimation());
		assertEquals(819, read.getRecord().getWalkingAnimation());
	}

	@Test
	public void asksTheNpcArchiveForTheFile()
	{
		FakeIndexDataBase index = new FakeIndexDataBase()
			.withNpc(NPC_ID, record().standing(808).walking(819).end());

		NpcArchive.read(new FakeClient().withIndexConfig(index), NPC_ID, LABEL);

		// Pinned to the literals rather than to the constant: written as
		// NPC_ARCHIVE_ID + ":" + NPC_ID this would pass whatever the archive id was
		// changed to, and reading somebody else's archive is a figure wearing the wrong
		// animations rather than an error.
		assertEquals(java.util.Collections.singletonList("9:4931"), index.loads());
		assertEquals(9, NpcArchive.NPC_ARCHIVE_ID);
	}

	@Test
	public void anIdTheArchiveDoesNotListIsAbsentRatherThanUnavailable()
	{
		FakeClient client = new FakeClient().withIndexConfig(new FakeIndexDataBase()
			.withNpc(NPC_ID, record().end()));

		NpcArchive read = NpcArchive.read(client, NPC_ID + 1, LABEL);

		assertEquals("a permanent answer, so the caller may stop retrying",
			NpcArchive.Outcome.ABSENT, read.getOutcome());
		assertNull(read.getRecord());
	}

	@Test
	public void anIdThatIsNotResidentIsRetryableRatherThanAbsent()
	{
		FakeClient client = new FakeClient()
			.withIndexConfig(new FakeIndexDataBase().withNpcNotResident(NPC_ID));

		NpcArchive read = NpcArchive.read(client, NPC_ID, LABEL);

		assertEquals("a cold cache produces this constantly and it must never latch",
			NpcArchive.Outcome.UNAVAILABLE, read.getOutcome());
		assertNull(read.getRecord());
	}

	/**
	 * Asked with no index at all, so the answer can only come from the guard at the top:
	 * every other path out of {@code read} would call this retryable, and a negative id
	 * retried is a follower that spends its whole budget on a number that is not a file
	 * id in any archive.
	 */
	@Test
	public void aNegativeIdIsAbsentEvenWithNoIndexToAsk()
	{
		assertEquals(NpcArchive.Outcome.ABSENT,
			NpcArchive.read(new FakeClient(), -1, LABEL).getOutcome());
	}

	@Test
	public void noIndexYetIsUnavailable()
	{
		// The FakeClient's default: no index at all, which is what the real client answers
		// before one is up.
		NpcArchive read = NpcArchive.read(new FakeClient(), NPC_ID, LABEL);

		assertEquals(NpcArchive.Outcome.UNAVAILABLE, read.getOutcome());
	}

	@Test
	public void anIndexWithNoFileListIsUnavailable()
	{
		// getFileIds answers null rather than an empty array, so the null check is the
		// only thing between here and an NPE inside a game-tick handler.
		FakeClient client = new FakeClient()
			.withIndexConfig(new FakeIndexDataBase().withNoFileList());

		assertEquals(NpcArchive.Outcome.UNAVAILABLE,
			NpcArchive.read(client, NPC_ID, LABEL).getOutcome());
	}

	@Test
	public void anIndexThatThrowsIsUnavailableRatherThanAThrow()
	{
		FakeClient client = new FakeClient().withThrowingIndexConfig();

		assertEquals(NpcArchive.Outcome.UNAVAILABLE,
			NpcArchive.read(client, NPC_ID, LABEL).getOutcome());
	}

	@Test
	public void aFileListThatThrowsIsUnavailableRatherThanAThrow()
	{
		FakeClient client = new FakeClient()
			.withIndexConfig(new FakeIndexDataBase().withThrowingFileIds());

		assertEquals(NpcArchive.Outcome.UNAVAILABLE,
			NpcArchive.read(client, NPC_ID, LABEL).getOutcome());
	}

	@Test
	public void aLoadThatThrowsIsUnavailableRatherThanAThrow()
	{
		FakeClient client = new FakeClient()
			.withIndexConfig(new FakeIndexDataBase().withThrowingLoad(NPC_ID));

		assertEquals(NpcArchive.Outcome.UNAVAILABLE,
			NpcArchive.read(client, NPC_ID, LABEL).getOutcome());
	}

	@Test
	public void aRecordThatDecodesToNothingUsableStillDecodes()
	{
		// Whether the pair is usable is the record's question, not the archive's — the
		// archive's job is only to say where the bytes came from.
		FakeClient client = new FakeClient().withIndexConfig(new FakeIndexDataBase()
			.withNpc(NPC_ID, record().end()));

		NpcArchive read = NpcArchive.read(client, NPC_ID, LABEL);

		assertEquals(NpcArchive.Outcome.DECODED, read.getOutcome());
		assertNotNull(read.getRecord());
		assertTrue("an empty record is well-formed", read.getRecord().isComplete());
		assertEquals(NpcRecord.NO_ANIMATION, read.getRecord().getWalkingAnimation());
	}

	@Test
	public void doesNotHoldTheClientsFileIdArray()
	{
		// The array getFileIds answers with is the client's own. Nothing here may keep it,
		// and nothing here may write to it: a scan that sorted in place, or a cached
		// reference, would corrupt or stale the client's own table of contents.
		FakeIndexDataBase index = new FakeIndexDataBase()
			.withNpc(1, record().end())
			.withNpc(NPC_ID, record().standing(808).walking(819).end());

		int[] before = index.getFileIds(NpcArchive.NPC_ARCHIVE_ID);
		int[] snapshot = before.clone();

		NpcArchive.read(new FakeClient().withIndexConfig(index), NPC_ID, LABEL);

		assertTrue("the file list was mutated", java.util.Arrays.equals(snapshot, before));
		assertTrue("the same array instance must still be the one the index hands out",
			index.getFileIds(NpcArchive.NPC_ARCHIVE_ID) == before);
	}
}
