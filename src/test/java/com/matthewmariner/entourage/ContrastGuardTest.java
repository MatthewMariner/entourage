package com.matthewmariner.entourage;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Every label a user is meant to read clears a real contrast ratio against whatever is
 * actually painted behind it.
 *
 * <p><b>Why this exists.</b> {@code MEDIUM_GRAY_COLOR} — RGB(77,77,77) — sat on slot
 * headings, subtitles, the muted paragraphs and a handful of other labels for a panel
 * background of RGB(40,40,40) or RGB(30,30,30): a contrast ratio under 2:1 against WCAG's
 * 4.5:1 minimum for body text. Every test that asserted the text <i>existed</i> — and
 * this repo has plenty — passed the whole time, because a label whose foreground is
 * indistinguishable from its background still returns its string from
 * {@code getText()}. This repo has already shipped a panel elsewhere whose headings
 * rendered at 0px with every test green for the same shape of reason: the property that
 * broke was never the one anything asserted on.
 *
 * <p><b>Computed, not hardcoded.</b> The ratio comes out of the two {@code Color}s a
 * label is actually holding at the moment this walks the tree — its own foreground, and
 * the background of the nearest ancestor that actually paints one — via the WCAG 2
 * relative-luminance formula. A future colour swap that reintroduces the bug fails this
 * on the numbers, not because a string constant was recorded here to compare against.
 *
 * <p><b>Two bars, structurally chosen.</b> Ordinary body text — a heading, a subtitle, a
 * paragraph sitting directly on its card or panel — is held to WCAG SC 1.4.3's 4.5:1.
 * The handful of labels that are themselves opaque, self-painted chips (the quick-settings
 * pips here) are held to SC 1.4.11's 3:1 instead: a chip's state is carried twice, in the
 * fill as well as the glyph, which is exactly the redundancy 1.4.11 exists for. Which bar
 * applies is read off {@link JLabel#isOpaque()} rather than off which label this happens
 * to be, so a new chip gets the right bar automatically and a new paragraph cannot quietly
 * claim the looser one.
 */
public class ContrastGuardTest
{
	/** WCAG 2 SC 1.4.3 (text contrast, AA): the bar for a plain label on its panel. */
	private static final double TEXT_MIN_CONTRAST = 4.5;

	/**
	 * WCAG 2 SC 1.4.11 (non-text/UI-component contrast, AA): the bar for a label that
	 * paints its own background, which in this file means a quick-settings pip. Its
	 * selected/unselected state is already carried by the fill lightening, so the glyph
	 * itself is held to the UI-component bar rather than the paragraph one.
	 */
	private static final double CHIP_MIN_CONTRAST = 3.0;

	private final FakeConfig config = new FakeConfig();

	private EntourageRosterPanel panel()
	{
		final EntourageRosterPanel[] built = new EntourageRosterPanel[1];
		onSwing(() -> built[0] = new EntourageRosterPanel(config, config));
		return built[0];
	}

	/**
	 * The roster itself: a mix of active and greyed slots (so both branches of the
	 * heading/title/subtitle ternaries are on screen at once), a remove action on the
	 * active ones, the add card, the helper paragraph, and both lit and unlit
	 * quick-settings pips.
	 */
	@Test
	public void noLabelOnTheRosterIsTooCloseToItsBackground()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE);

		assertReadable(panel());
	}

	/**
	 * The picker, the typed-id card in both its states, the "nothing matches" paragraph,
	 * the rejected-input notice, and the "in this slot" mark — every label the roster
	 * screen above does not reach.
	 */
	@Test
	public void noLabelInThePickerIsTooCloseToItsBackground()
	{
		config.setFigure(EntourageFigure.PIRATE);
		EntourageRosterPanel panel = panel();

		press(pressableFor(panel, "Pirate"));
		assertReadable(panel);

		type(panel, "there is no figure named this");
		assertReadable(panel);

		type(panel, "");
		typeIntoIdBox(panel, "not a number");
		assertReadable(panel);

		typeIntoIdBox(panel, "4931");
		assertReadable(panel);
	}

	// --- the guard itself ------------------------------------------------------

	private static void assertReadable(Container root)
	{
		List<String> failures = new ArrayList<>();

		for (Component component : all(root))
		{
			if (!(component instanceof JLabel) || !component.isVisible())
			{
				continue;
			}

			JLabel label = (JLabel) component;
			String text = strip(label.getText());
			if (text.isEmpty())
			{
				continue;
			}

			Color background = effectiveBackground(label);
			double ratio = contrast(label.getForeground(), background);
			double minimum = label.isOpaque() ? CHIP_MIN_CONTRAST : TEXT_MIN_CONTRAST;

			if (ratio < minimum)
			{
				failures.add(String.format(
					"\"%s\" reads %.2f:1 (needs %.1f:1) — foreground %s on background %s",
					text, ratio, minimum, label.getForeground(), background));
			}
		}

		assertTrue("label(s) too close to their background:\n" + String.join("\n", failures),
			failures.isEmpty());
	}

	/**
	 * @return the colour actually painted behind {@code component} — its own, if it is
	 * opaque and paints one itself, otherwise the nearest ancestor's. A plain
	 * {@code JLabel} is not opaque by default and paints nothing, so what a user sees
	 * behind it is whichever {@code JPanel} it sits on, and every panel in this file sets
	 * its own background explicitly.
	 */
	private static Color effectiveBackground(Component component)
	{
		for (Component at = component; at != null; at = at.getParent())
		{
			if (at instanceof JComponent && ((JComponent) at).isOpaque())
			{
				return at.getBackground();
			}
		}

		throw new AssertionError("no opaque ancestor carries a background for " + component);
	}

	private static double contrast(Color foreground, Color background)
	{
		double lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
		double darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
		return (lighter + 0.05) / (darker + 0.05);
	}

	/** The WCAG 2 relative-luminance formula, straight off the spec. */
	private static double relativeLuminance(Color colour)
	{
		return 0.2126 * channel(colour.getRed())
			+ 0.7152 * channel(colour.getGreen())
			+ 0.0722 * channel(colour.getBlue());
	}

	private static double channel(int value)
	{
		double normalised = value / 255.0;
		return normalised <= 0.03928
			? normalised / 12.92
			: Math.pow((normalised + 0.055) / 1.055, 2.4);
	}

	// --- walking and driving the panel, trimmed from EntourageRosterPanelTest --------

	private static void collect(Container root, List<Component> into)
	{
		for (Component child : root.getComponents())
		{
			into.add(child);
			if (child instanceof Container)
			{
				collect((Container) child, into);
			}
		}
	}

	private static List<Component> all(Container root)
	{
		List<Component> found = new ArrayList<>();
		collect(root, found);
		return found;
	}

	private static String strip(String label)
	{
		return label == null ? "" : label.replaceAll("<[^>]*>", "");
	}

	/** The smallest pressable ancestor of the label reading {@code text}. */
	private static Container pressableFor(Container root, String text)
	{
		for (Component child : all(root))
		{
			if (!(child instanceof JLabel) || !strip(((JLabel) child).getText()).equals(text))
			{
				continue;
			}

			for (Component up = child; up != null; up = up.getParent())
			{
				if (up.getMouseListeners().length > 0 && up instanceof Container)
				{
					return (Container) up;
				}
			}
		}

		throw new AssertionError("nothing pressable reads \"" + text + "\"");
	}

	private static void press(Component target)
	{
		onSwing(() ->
		{
			for (java.awt.event.MouseListener listener : target.getMouseListeners())
			{
				listener.mousePressed(new java.awt.event.MouseEvent(target,
					java.awt.event.MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false));
			}
		});
	}

	private static void type(Container root, String query)
	{
		onSwing(() ->
		{
			for (Component child : all(root))
			{
				if (child instanceof net.runelite.client.ui.components.IconTextField)
				{
					((net.runelite.client.ui.components.IconTextField) child).setText(query);
					return;
				}
			}
			throw new AssertionError("the panel has no search box");
		});
	}

	private static void typeIntoIdBox(Container root, String text)
	{
		onSwing(() ->
		{
			for (Component child : all(root))
			{
				if (!(child instanceof javax.swing.JTextField) || insideTheSearchBox(child))
				{
					continue;
				}

				javax.swing.JTextField field = (javax.swing.JTextField) child;
				field.setText(text);
				for (java.awt.event.ActionListener listener : field.getActionListeners())
				{
					listener.actionPerformed(new java.awt.event.ActionEvent(
						field, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
				}
				return;
			}
			throw new AssertionError("the picker has no id box");
		});
	}

	private static boolean insideTheSearchBox(Component child)
	{
		for (Component up = child; up != null; up = up.getParent())
		{
			if (up instanceof net.runelite.client.ui.components.IconTextField)
			{
				return true;
			}
		}
		return false;
	}

	private static void onSwing(Runnable work)
	{
		try
		{
			SwingUtilities.invokeAndWait(work);
		}
		catch (InvocationTargetException thrown)
		{
			throw thrown.getCause() instanceof AssertionError
				? (AssertionError) thrown.getCause()
				: new AssertionError(thrown.getCause());
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError(interrupted);
		}
	}
}
