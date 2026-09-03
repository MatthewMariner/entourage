package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

/**
 * A {@link ModelData} that records what was done to it on the way to becoming a model.
 *
 * <p>Three of those records matter, and each is a real bug this plugin could have:
 * whether {@code cloneColors()} was called before the first {@code recolor()} (without
 * it, repainting a merged model can reach through the client's own cache and repaint
 * every instance of that model in the world), which recolour pairs were applied, and
 * whether lighting happened at all.
 */
final class FakeModelData extends StubModelData
{
	private final List<int[]> recolours = new ArrayList<>();

	private boolean colorsCloned;
	private boolean recolouredBeforeClone;
	private boolean lit;

	@Override
	public ModelData cloneColors()
	{
		colorsCloned = true;
		return this;
	}

	@Override
	public ModelData recolor(short find, short replace)
	{
		if (!colorsCloned)
		{
			recolouredBeforeClone = true;
		}
		recolours.add(new int[]{find, replace});
		return this;
	}

	@Override
	public Model light()
	{
		lit = true;
		return new StubModel();
	}

	boolean wasLit()
	{
		return lit;
	}

	boolean colorsCloned()
	{
		return colorsCloned;
	}

	boolean recolouredBeforeClone()
	{
		return recolouredBeforeClone;
	}

	List<int[]> recolours()
	{
		return recolours;
	}
}
