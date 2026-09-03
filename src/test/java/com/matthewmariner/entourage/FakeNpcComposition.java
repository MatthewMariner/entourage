package com.matthewmariner.entourage;

import javax.annotation.Nullable;

/**
 * An NPC composition with a name, some models and some recolour pairs.
 *
 * <p><b>Nulls are first-class here.</b> Every one of the four accessors this plugin
 * reads compiles, in the injected client, to a bare {@code getfield} on a field the
 * constructor only assigns for a composition whose cache entry carried that opcode — so
 * a real composition can hand back {@code null} from any of them, and
 * {@link FollowerAppearance} has to survive it. A fake that always returned arrays would
 * make those null checks untestable.
 */
final class FakeNpcComposition extends StubNpcComposition
{
	@Nullable
	private final String name;

	@Nullable
	private final int[] models;

	@Nullable
	private final short[] find;

	@Nullable
	private final short[] replace;

	private FakeNpcComposition(
		@Nullable String name,
		@Nullable int[] models,
		@Nullable short[] find,
		@Nullable short[] replace)
	{
		this.name = name;
		this.models = models;
		this.find = find;
		this.replace = replace;
	}

	/** A plain composition: a name, some models, no recolours. */
	static FakeNpcComposition of(String name, int... models)
	{
		return new FakeNpcComposition(name, models, new short[0], new short[0]);
	}

	/** The same, plus a matched set of recolour pairs. */
	static FakeNpcComposition recoloured(String name, int[] models, short[] find, short[] replace)
	{
		return new FakeNpcComposition(name, models, find, replace);
	}

	/** A composition the cache produced with one or more of its arrays absent. */
	static FakeNpcComposition withNulls(
		@Nullable String name,
		@Nullable int[] models,
		@Nullable short[] find,
		@Nullable short[] replace)
	{
		return new FakeNpcComposition(name, models, find, replace);
	}

	@Override
	@Nullable
	public String getName()
	{
		return name;
	}

	@Override
	@Nullable
	public int[] getModels()
	{
		return models;
	}

	@Override
	@Nullable
	public short[] getColorToReplace()
	{
		return find;
	}

	@Override
	@Nullable
	public short[] getColorToReplaceWith()
	{
		return replace;
	}
}
