package com.matthewmariner.entourage;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

/**
 * A {@link ModelData} that records what was done to it on the way to becoming a model.
 *
 * <p>Four of those records matter, and each is a real bug this plugin could have:
 * whether {@code cloneColors()} was called before the first {@code recolor()} (without
 * it, repainting a merged model can reach through the client's own cache and repaint
 * every instance of that model in the world), which recolour pairs were applied,
 * whether lighting happened at all, and <b>which of the two lighting overloads was
 * used</b>.
 *
 * <p><b>The last one is why {@link #light()} records itself rather than delegating.</b>
 * The no-argument overload is the interface rig — disassembled from the 1.12.38 injected
 * client, it is a single call to {@code light(64, 768, -50, -10, -50)} — and a figure
 * standing in the world wants the world rig instead. If this fake forwarded one to the
 * other, "it was lit" would be all a test could see and the difference between an
 * interface model and a world model would be invisible to every assertion in the suite.
 */
final class FakeModelData extends StubModelData
{
	private final List<int[]> recolours = new ArrayList<>();

	private boolean colorsCloned;
	private boolean recolouredBeforeClone;
	private boolean lit;

	/** The five arguments the five-argument overload was given, or {@code null}. */
	private int[] lighting;

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
		// Deliberately not recorded as lighting[]: this overload's arguments are the
		// client's, not the plugin's, and a test asking "what did the plugin ask for?"
		// must be able to tell that it asked for nothing.
		return new StubModel();
	}

	@Override
	public Model light(int ambient, int contrast, int x, int y, int z)
	{
		lit = true;
		lighting = new int[]{ambient, contrast, x, y, z};
		return new StubModel();
	}

	boolean wasLit()
	{
		return lit;
	}

	/**
	 * @return the five lighting arguments, or {@code null} if the model was lit through
	 * the no-argument overload — which is the interface rig, and is the bug.
	 */
	@Nullable
	int[] lighting()
	{
		return lighting;
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
