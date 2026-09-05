package com.matthewmariner.entourage;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The palette, and the one property that makes it a palette rather than a colour picker.
 *
 * <p><b>Text over a head must not be mistakable for something the game put there.</b>
 * {@code ../lively-cities}' predecessor was disabled from the Plugin Hub after a player
 * mistook a fake NPC for a real one and went to the maintainers about it, and that plugin
 * draws its overhead text in one hard-coded magenta for exactly this reason. This one
 * offers six colours, so the guarantee has to be a test rather than a constant.
 */
public class DialogueColourTest
{
	/**
	 * What the client itself uses, and what nothing here may be confused with.
	 *
	 * <p>Yellow is the one that matters most: it is both an NPC's menu target and the
	 * colour a player's own overhead chat is drawn in, which is the exact thing a line
	 * over a follower's head could be taken for.
	 */
	private static final int[] CLIENT_COLOURS = {
		0xFFFF00, // NPC targets, and overhead public chat
		0xFFFFFF, // player targets
		0x00FFFF, // scene objects
		0xFF9040, // items
	};

	/**
	 * How far apart is far enough, per channel: 0x30 of 0xFF.
	 *
	 * <p>A judgement rather than a perceptual model — this repo has no colour-science
	 * library and does not need one. What it buys is that no shipped colour is a shade of
	 * a colour the client uses: one channel differing by a fifth of its range is visible
	 * at a glance, at any brightness, to anybody.
	 */
	private static final int FAR_ENOUGH = 0x30;

	@Test
	public void noShippedColourCanBeMistakenForOneTheClientUses()
	{
		for (DialogueColour colour : DialogueColour.values())
		{
			for (int client : CLIENT_COLOURS)
			{
				assertTrue(colour + " (" + hex(colour.getRgb()) + ") is a shade of the client's "
						+ hex(client) + ", which is what a real target or a real player's chat "
						+ "is drawn in",
					farApart(colour.getRgb(), client));
			}
		}
	}

	/** The guard has to be able to fail, so the colours it forbids have to fail it. */
	@Test
	public void theSeparationRuleRejectsTheClientsOwnColours()
	{
		for (int client : CLIENT_COLOURS)
		{
			assertTrue("a rule that passes " + hex(client) + " against itself is no rule",
				!farApart(client, client));
		}

		assertTrue("and a near-shade of yellow is not far enough either",
			!farApart(0xFFFF20, 0xFFFF00));
	}

	@Test
	public void theDefaultIsTheMagentaTheSiblingPluginAlreadyUses()
	{
		assertSame(DialogueColour.MAGENTA, new FakeConfig().dialogueColour());
		assertEquals("../lively-cities' CitizenLabel.FAKE_RGB", 0xFF66FF,
			DialogueColour.MAGENTA.getRgb());
	}

	@Test
	public void everyEntryHasItsOwnColourAndItsOwnLabel()
	{
		Set<Integer> colours = new LinkedHashSet<>();
		Set<String> labels = new LinkedHashSet<>();

		for (DialogueColour colour : DialogueColour.values())
		{
			assertTrue(colour + " duplicates another entry's colour", colours.add(colour.getRgb()));
			assertTrue(colour + " duplicates another entry's label", labels.add(colour.toString()));
			assertTrue("an entry with no label is a blank row in the dropdown",
				!colour.toString().trim().isEmpty());
		}

		assertEquals(DialogueColour.values().length, colours.size());
	}

	/**
	 * The drawn colour is the documented one. Two fields that could disagree — the packed
	 * int the javadoc quotes and the {@code Color} the overlay draws with — are worth one
	 * assertion.
	 */
	@Test
	public void theDrawnColourIsThePackedValue()
	{
		for (DialogueColour colour : DialogueColour.values())
		{
			assertEquals(colour.toString(),
				colour.getRgb(), colour.getColour().getRGB() & 0xFFFFFF);
		}
	}

	/**
	 * Built once, not per frame. The overlay asks for this on every rendered frame, and
	 * {@code new Color(rgb)} per frame per follower is an allocation for a value that
	 * cannot change.
	 */
	@Test
	public void theColourObjectIsBuiltOnce()
	{
		assertSame(DialogueColour.MAGENTA.getColour(), DialogueColour.MAGENTA.getColour());
	}

	private static boolean farApart(int a, int b)
	{
		return channel(a, 16, b) >= FAR_ENOUGH
			|| channel(a, 8, b) >= FAR_ENOUGH
			|| channel(a, 0, b) >= FAR_ENOUGH;
	}

	private static int channel(int a, int shift, int b)
	{
		return Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF));
	}

	private static String hex(int rgb)
	{
		return String.format("%06x", rgb);
	}
}
