package com.matthewmariner.entourage;

import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;

/**
 * One existing NPC's appearance — its model parts and its recolour pairs — read off
 * the client so a follower can wear it.
 *
 * <p><b>Why not the player's own outfit.</b> The obvious idea for this plugin is an
 * entourage of yous, and it is <b>unconfirmed</b> rather than merely unimplemented.
 * {@code PlayerComposition} exposes {@code getEquipmentIds()} and
 * {@code getColors()} — the second of those was written here as
 * {@code getBodyPartColours()}, which is not a method on that interface and never
 * was; {@code javap} gives {@code int[] getColors()}, in the same list that has the
 * correct {@code getEquipmentIds()} next to it. But there is no counterpart to
 * {@code NPCComposition.getModels()} — no one call that hands back a buildable
 * {@code int[]} of model ids — and turning kit and equipment ids into model ids means
 * reproducing the client's own item-to-model resolution, which this plugin cannot
 * verify without a cache. So: NPC-composition dressing, and "an entourage of yous" is
 * a spike somebody has to do the reading for, not a feature anybody has been
 * promised.
 *
 * <p><b>The accessors, verified against 1.12.38 rather than guessed.</b>
 * {@code javap net.runelite.api.Client} declares
 * {@code public abstract net.runelite.api.NPCComposition getNpcDefinition(int);} and
 * {@code javap net.runelite.api.NPCComposition} declares the three this class needs —
 * {@code int[] getModels()}, {@code short[] getColorToReplace()} and
 * {@code short[] getColorToReplaceWith()} — plus {@code getName()}, used only in a
 * log line. In the injected client every one of those compiles to a bare
 * {@code getfield} on a field the constructor only assigns for a composition whose
 * cache entry carried that opcode, so <b>every one of them can be {@code null}</b>.
 * Nothing here treats a null as an empty array.
 *
 * <p><b>Two ways of not resolving, and both are handled.</b> An id with no archive
 * entry makes the client <i>throw</i> rather than return null — the composition
 * constructor is handed a null buffer inside a {@code catch (RuntimeException)} that
 * rethrows wrapped — and a composition that does resolve may still have no models.
 * Both come back from {@link #resolve} as {@code null}, and {@link Follower} treats
 * that exactly like a cold model cache: the follower does not spawn, and it is tried
 * again. Never a throw out of here, and never a partial appearance.
 *
 * <p><b>Deliberately not read from the composition:</b> {@code getSize()}, and the
 * {@code getWidthScale()}/{@code getHeightScale()} pair. Size would be the right
 * footprint to hand {@link WalkableStep} for a figure bigger than one tile, and the
 * scale pair is a 128-based multiplier the model would have to be scaled by; both are
 * arithmetic this cannot verify without a live client, and getting either wrong
 * silently is a figure at the wrong size walking through walls it should not fit
 * past. Every figure in {@link EntourageFigure} is an ordinary human, so identity
 * scale and a 1x1 footprint are the right answers for all of them today. A non-human
 * figure would render at default size, which is a known limitation rather than a
 * surprise.
 *
 * <p><b>Client thread only</b> — {@code getNpcDefinition} asserts it.
 */
@Slf4j
final class FollowerAppearance
{
	private final String npcName;
	private final int[] modelIds;
	private final short[] recolorFind;
	private final short[] recolorReplace;

	private FollowerAppearance(
		@Nullable String npcName,
		int[] modelIds,
		short[] recolorFind,
		short[] recolorReplace)
	{
		this.npcName = npcName;
		this.modelIds = modelIds;
		this.recolorFind = recolorFind;
		this.recolorReplace = recolorReplace;
	}

	/**
	 * Reads one NPC's appearance.
	 *
	 * @param client the live client. Client thread only.
	 * @param npcId  the NPC to dress from
	 * @param label  the figure this is for, for the log line
	 * @return the appearance, or {@code null} if the id does not resolve, the
	 * composition has no models, or every model id it lists is unusable. Never throws.
	 * Writes nothing above debug — the caller owns the warning, because only it knows
	 * whether this is a first attempt or a retry.
	 */
	@Nullable
	static FollowerAppearance resolve(Client client, int npcId, String label)
	{
		if (npcId <= 0)
		{
			return null;
		}

		NPCComposition composition;
		try
		{
			composition = client.getNpcDefinition(npcId);
		}
		catch (RuntimeException e)
		{
			// The ordinary "this id no longer exists" case — the client throws rather
			// than returning null for a missing archive entry.
			log.debug("{}: npc {} threw on lookup", label, npcId, e);
			return null;
		}

		if (composition == null)
		{
			log.debug("{}: npc {} resolved to no composition", label, npcId);
			return null;
		}

		int[] models = usableModelIds(composition.getModels(), label, npcId);
		if (models.length == 0)
		{
			log.debug("{}: npc {} ('{}') has no usable models", label, npcId, composition.getName());
			return null;
		}

		short[] find = composition.getColorToReplace();
		short[] replace = composition.getColorToReplaceWith();
		int pairs = Math.min(find == null ? 0 : find.length, replace == null ? 0 : replace.length);

		short[] keptFind = new short[pairs];
		short[] keptReplace = new short[pairs];
		for (int i = 0; i < pairs; i++)
		{
			keptFind[i] = find[i];
			keptReplace[i] = replace[i];
		}

		return new FollowerAppearance(composition.getName(), models, keptFind, keptReplace);
	}

	/**
	 * Drops a non-positive model id. A {@code -1} in a composition's model array is
	 * the client's own "this slot is empty" rather than a missing part, so a
	 * composition with some usable models still resolves — asking the client about the
	 * sentinel would get a null back that the caller would read as a cold cache and
	 * retry for nothing.
	 */
	private static int[] usableModelIds(@Nullable int[] models, String label, int npcId)
	{
		if (models == null || models.length == 0)
		{
			return new int[0];
		}

		int[] kept = new int[models.length];
		int n = 0;
		for (int id : models)
		{
			if (id <= 0)
			{
				log.debug("{}: npc {} lists an empty model slot ({}), skipping it", label, npcId, id);
				continue;
			}
			kept[n++] = id;
		}

		if (n == models.length)
		{
			return models.clone();
		}

		int[] trimmed = new int[n];
		System.arraycopy(kept, 0, trimmed, 0, n);
		return trimmed;
	}

	/** @return the NPC's own name, for a log line. May be {@code null}. */
	@Nullable
	String getNpcName()
	{
		return npcName;
	}

	/** @return the model parts to build, never empty */
	int[] getModelIds()
	{
		return modelIds;
	}

	/** @return the NPC's {@code getColorToReplace()}, truncated to matched pairs */
	short[] getRecolorFind()
	{
		return recolorFind;
	}

	/** @return the NPC's {@code getColorToReplaceWith()}, truncated to matched pairs */
	short[] getRecolorReplace()
	{
		return recolorReplace;
	}
}
