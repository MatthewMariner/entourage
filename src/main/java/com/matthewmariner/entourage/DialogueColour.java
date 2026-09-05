package com.matthewmariner.entourage;

import java.awt.Color;

/**
 * The colours overhead text may be drawn in.
 *
 * <p><b>A short list rather than a colour picker, and that is the whole point.</b>
 * RuneLite supports a {@code Color} config item, so a picker would be cheaper to write
 * than this enum is. It is deliberately not offered, for the reason
 * {@code CitizenLabel} in {@code ../lively-cities} sets out at length: the colour's job
 * is to be one the game never uses for something real, and a free colour can be set to
 * {@code ffff00} — which is exactly what the client draws a player's own overhead chat
 * in. Making the one legibility guarantee user-defeatable to save an enum is a bad
 * trade. The predecessor of that plugin was disabled after a player mistook a fake NPC
 * for a real one and went to the maintainers; text over a head is the same failure mode
 * with the volume up.
 *
 * <p>The colours the client itself uses, which nothing here may be mistaken for:
 *
 * <ul>
 *   <li>{@code ffff00} yellow — NPC menu targets, and overhead public chat</li>
 *   <li>{@code ffffff} white — player menu targets</li>
 *   <li>{@code 00ffff} cyan — scene objects</li>
 *   <li>{@code ff9040} orange — items, on the ground and in the inventory</li>
 * </ul>
 *
 * <p>Every entry below is far from all four in at least one channel, and
 * {@code DialogueColourTest} asserts that rather than leaving it as a paragraph — so a
 * seventh colour added carelessly is a red test rather than a figure whose speech reads
 * as somebody's chat.
 *
 * <p>{@link #MAGENTA} is the default and is {@code ../lively-cities}' own
 * {@code FAKE_RGB}, which is the colour the person who asked for this feature already
 * associates with "this is the plugin talking".
 */
public enum DialogueColour
{
	/** {@code ff66ff} — the same magenta {@code ../lively-cities} draws its citizens in. */
	MAGENTA("Magenta", 0xFF66FF),

	/** {@code b08cff}. */
	VIOLET("Violet", 0xB08CFF),

	/** {@code ff8ac4}. */
	PINK("Pink", 0xFF8AC4),

	/** {@code 7fa8ff} — a periwinkle blue, kept well away from the client's cyan. */
	BLUE("Blue", 0x7FA8FF),

	/** {@code 66ff8a}. */
	GREEN("Green", 0x66FF8A),

	/** {@code ff4d4d} — a red, kept well away from the client's item orange. */
	RED("Red", 0xFF4D4D);

	private final String displayName;
	private final int rgb;

	/**
	 * Built once. The overlay draws every frame, and {@code new Color(rgb)} per follower
	 * per frame would be an allocation for a value that cannot change.
	 */
	private final Color colour;

	DialogueColour(String displayName, int rgb)
	{
		this.displayName = displayName;
		this.rgb = rgb;
		this.colour = new Color(rgb);
	}

	/** @return the packed 24-bit value, for the tests that check it against the client's */
	int getRgb()
	{
		return rgb;
	}

	/** @return the colour to draw with */
	Color getColour()
	{
		return colour;
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}
}
