package com.matthewmariner.entourage;

/**
 * Every abstract method of {@link net.runelite.api.NPCComposition}, throwing.
 *
 * <p>Mechanically generated from {@code javap net.runelite.api.NPCComposition} and its supertypes,
 * against the 1.12.38 API jar, and then checked in — no reflection, no mocking
 * framework, nothing generated at runtime. It exists so a Fake can implement the
 * handful of methods this plugin actually calls without hand-typing the other
 * 26, and so that a method this plugin has never used fails loudly the first
 * time a test reaches it instead of quietly returning {@code null}.
 *
 * <p>{@link FakeNpcComposition} is the subclass that answers the four {@link FollowerAppearance} reads.
 *
 * <p>One method per line on purpose. This is a lookup table, not code to read.
 */
class StubNpcComposition implements net.runelite.api.NPCComposition
{
	static UnsupportedOperationException unsupported(String method)
	{
		return new UnsupportedOperationException(
			"StubNpcComposition does not implement NPCComposition." + method + "(..) — this plugin has never needed it");
	}

	// --- net.runelite.api.NPCComposition ---
	@Override public java.lang.String[] getActions() { throw unsupported("getActions"); }
	@Override public int[] getChatheadModels() { throw unsupported("getChatheadModels"); }
	@Override public short[] getColorToReplace() { throw unsupported("getColorToReplace"); }
	@Override public short[] getColorToReplaceWith() { throw unsupported("getColorToReplaceWith"); }
	@Override public int getCombatLevel() { throw unsupported("getCombatLevel"); }
	@Override public int[] getConfigs() { throw unsupported("getConfigs"); }
	@Override public int getFootprintSize() { throw unsupported("getFootprintSize"); }
	@Override public int getHeightScale() { throw unsupported("getHeightScale"); }
	@Override public int getId() { throw unsupported("getId"); }
	@Override public int[] getModels() { throw unsupported("getModels"); }
	@Override public java.lang.String getName() { throw unsupported("getName"); }
	@Override public net.runelite.api.EntityOps getOps() { throw unsupported("getOps"); }
	@Override public int getSize() { throw unsupported("getSize"); }
	@Override public int[] getStats() { throw unsupported("getStats"); }
	@Override public int getWidthScale() { throw unsupported("getWidthScale"); }
	@Override public boolean isFollower() { throw unsupported("isFollower"); }
	@Override public boolean isInteractible() { throw unsupported("isInteractible"); }
	@Override public boolean isMinimapVisible() { throw unsupported("isMinimapVisible"); }
	@Override public net.runelite.api.NPCComposition transform() { throw unsupported("transform"); }

	// --- net.runelite.api.ParamHolder ---
	@Override public int getIntValue(int a0) { throw unsupported("getIntValue"); }
	@Override public long getLongValue(int a0) { throw unsupported("getLongValue"); }
	@Override public net.runelite.api.IterableHashTable<net.runelite.api.Node> getParams() { throw unsupported("getParams"); }
	@Override public java.lang.String getStringValue(int a0) { throw unsupported("getStringValue"); }
	@Override public void setValue(int a0, int a1) { throw unsupported("setValue"); }
	@Override public void setValue(int a0, java.lang.String a1) { throw unsupported("setValue"); }
	@Override public void setValue(int a0, long a1) { throw unsupported("setValue"); }
}
