package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.IndexDataBase;

/**
 * The config index, as far as this plugin uses it.
 *
 * <p>{@code IndexDataBase} has three methods — {@code isOverlayOutdated()},
 * {@code getFileIds(int)} and {@code loadData(int, int)} — so this is the whole
 * interface rather than a subset of a generated stub, and it needs no {@code StubClient}
 * equivalent.
 *
 * <p><b>Modelled on the real one's failure shapes.</b> {@code getFileIds} answers
 * {@code null} for an archive it does not have, not an empty array; {@code loadData}
 * answers {@code null} for a group that is not resident, which is a fetch being enqueued
 * rather than an error. Both of those are the cases {@link NpcArchive} routes differently,
 * so a permissive fake would make the routing untestable.
 *
 * <p>{@link #getFileIds(int)} hands back the same array instance every time it is asked,
 * on purpose: the real one returns the client's own, and a fake that copied would hide a
 * caller that mutated it.
 */
final class FakeIndexDataBase implements IndexDataBase
{
	/** Archive id to its file ids, in the order the fake was told about them. */
	private final Map<Integer, int[]> fileIds = new HashMap<>();

	/** Archive and file to its bytes. Keyed as {@code archive:file}. */
	private final Map<String, byte[]> groups = new HashMap<>();

	/** Files that exist but are not in memory — {@code loadData} answers null for these. */
	private final Set<String> notResident = new HashSet<>();

	/** Archives whose {@code getFileIds} blows up. */
	private final Set<Integer> throwingArchives = new HashSet<>();

	/** Files whose {@code loadData} blows up. */
	private final Set<String> throwingGroups = new HashSet<>();

	/** Every {@code loadData} call, as {@code archive:file}, in order. */
	private final List<String> loads = new ArrayList<>();

	/** Registers one NPC record, and its id in the archive's file list. */
	FakeIndexDataBase withNpc(int npcId, byte[] data)
	{
		addFileId(NpcArchive.NPC_ARCHIVE_ID, npcId);
		groups.put(key(NpcArchive.NPC_ARCHIVE_ID, npcId), data);
		return this;
	}

	/** The id is in the archive's file list but its bytes are not in memory yet. */
	FakeIndexDataBase withNpcNotResident(int npcId)
	{
		addFileId(NpcArchive.NPC_ARCHIVE_ID, npcId);
		notResident.add(key(NpcArchive.NPC_ARCHIVE_ID, npcId));
		return this;
	}

	/** The archive exists but has no file list yet, which is what a bad archive id also gets. */
	FakeIndexDataBase withNoFileList()
	{
		fileIds.clear();
		return this;
	}

	FakeIndexDataBase withThrowingFileIds()
	{
		throwingArchives.add(NpcArchive.NPC_ARCHIVE_ID);
		return this;
	}

	FakeIndexDataBase withThrowingLoad(int npcId)
	{
		addFileId(NpcArchive.NPC_ARCHIVE_ID, npcId);
		throwingGroups.add(key(NpcArchive.NPC_ARCHIVE_ID, npcId));
		return this;
	}

	/** @return every {@code loadData} call made, as {@code archive:file}, in order */
	List<String> loads()
	{
		return loads;
	}

	@Override
	public boolean isOverlayOutdated()
	{
		throw new UnsupportedOperationException(
			"FakeIndexDataBase does not implement isOverlayOutdated() — this plugin has never needed it");
	}

	@Override
	@Nullable
	public int[] getFileIds(int archiveId)
	{
		if (throwingArchives.contains(archiveId))
		{
			throw new IllegalStateException("archive " + archiveId + " blew up on the way out");
		}

		// The real one hands back the client's own array — the same instance, not a copy.
		return fileIds.get(archiveId);
	}

	@Override
	@Nullable
	public byte[] loadData(int archiveId, int fileId)
	{
		String key = key(archiveId, fileId);
		loads.add(key);

		if (throwingGroups.contains(key))
		{
			throw new IllegalStateException("group " + key + " blew up on the way out");
		}

		if (notResident.contains(key))
		{
			// The client enqueues a fetch and answers null in the meantime.
			return null;
		}

		return groups.get(key);
	}

	private void addFileId(int archiveId, int fileId)
	{
		int[] existing = fileIds.get(archiveId);
		if (existing == null)
		{
			fileIds.put(archiveId, new int[]{fileId});
			return;
		}

		int[] grown = new int[existing.length + 1];
		System.arraycopy(existing, 0, grown, 0, existing.length);
		grown[existing.length] = fileId;
		fileIds.put(archiveId, grown);
	}

	private static String key(int archiveId, int fileId)
	{
		return archiveId + ":" + fileId;
	}
}
