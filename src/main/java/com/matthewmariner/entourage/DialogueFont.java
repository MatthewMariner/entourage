package com.matthewmariner.entourage;

import java.awt.Font;
import net.runelite.client.ui.FontManager;

/**
 * Which of the game's own three faces overhead text is drawn in.
 *
 * <p><b>Three faces rather than a size in points.</b> The obvious implementation is
 * {@code graphics.getFont().deriveFont(size)}, and it is the wrong one: the RuneScape
 * faces are authored at one size, so a derived 12 or 20 point version is a scaled bitmap
 * — blurry, and immediately not the game's typography. {@link FontManager} ships the
 * three the client itself uses, they are the three RuneLite's own overlay font setting
 * offers, and picking between them is what "a font size" means in this game.
 *
 * <p><b>The bold face is the large one.</b> It is a heavier <i>and</i> taller face rather
 * than the regular one emboldened, which is why the label says "Large" and the constant
 * says {@code BOLD}: the enum names what it asks the client for, the dropdown names what
 * the user sees happen.
 *
 * <p><b>{@link #getFont()} is the one method in this plugin that only a live client can
 * exercise.</b> {@code FontManager}'s static initialiser registers three TrueType faces
 * with the local {@code GraphicsEnvironment} and reads {@code ~/.runelite/fonts}; that is
 * a graphics environment and a home directory in a unit test, for a mapping of three
 * constants onto three static fields. The tests therefore replace this seam
 * ({@code EntourageOverlay.fontFor}) rather than triggering it, exactly as they replace
 * the projection — and what a real client still has to confirm is only that these three
 * look right above a head, which is a judgement anyway. See the README's "Wanted from a
 * real client".
 */
public enum DialogueFont
{
	/** {@code FontManager.getRunescapeSmallFont()}. */
	SMALL("Small"),

	/** {@code FontManager.getRunescapeFont()} — the client's own default overlay face. */
	REGULAR("Regular"),

	/** {@code FontManager.getRunescapeBoldFont()}, which is the biggest of the three. */
	BOLD("Large");

	private final String displayName;

	DialogueFont(String displayName)
	{
		this.displayName = displayName;
	}

	/**
	 * @return the face to draw with. Never {@code null}: {@code FontManager} throws out
	 * of its own initialiser rather than handing back a null font, so there is nothing
	 * here to fall back from.
	 */
	Font getFont()
	{
		switch (this)
		{
			case SMALL:
				return FontManager.getRunescapeSmallFont();
			case BOLD:
				return FontManager.getRunescapeBoldFont();
			default:
				return FontManager.getRunescapeFont();
		}
	}

	/** RuneLite's settings panel renders an enum by its {@code toString()}. */
	@Override
	public String toString()
	{
		return displayName;
	}
}
