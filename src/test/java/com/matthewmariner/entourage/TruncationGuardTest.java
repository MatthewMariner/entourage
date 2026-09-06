package com.matthewmariner.entourage;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * No label on the roster panel asks for more room than the sidebar gives it.
 *
 * <p><b>Why this exists.</b> A slot subtitle — "Typed id, or Elite Black Knight if refused"
 * — was truncating mid-phrase, which is the half of the sentence that says what a refused id
 * falls back to. The fix wraps it through a one-cell table sized by a constant, and that
 * constant was tuned by eye against the <i>small</i> font in a 225-pixel panel. Raising the
 * body text to the normal face makes every glyph about 11% wider and the bold face 31%, so
 * the same box holds less; nothing in this repo would have noticed it going back. Every test
 * that asserts a label <i>exists</i> — and this repo has plenty — passes at any width,
 * because a {@code JLabel} that is drawing "Typed id, or Elite Black…" still returns the
 * whole string from {@code getText()}. The property that breaks is not the one anything
 * asserts on, which is the same shape of gap {@link ContrastGuardTest} was written for.
 *
 * <p><b>Measured, not hardcoded.</b> The panel is built, sized to
 * {@link PluginPanel#PANEL_WIDTH} and laid out for real; every number below comes out of the
 * component tree at that moment. Nothing here records an expected pixel count to compare
 * against, so a font swap, a border change or a new action glyph that reintroduces the bug
 * fails on the arithmetic rather than on a stale literal.
 *
 * <p><b>Two failures, because there are two ways to lose text.</b>
 * <ul>
 *   <li><b>Squeezed.</b> A label in {@code BorderLayout.CENTER} gets whatever the {@code EAST}
 *       sibling leaves. Ask for more than that and Swing ellipsises — the label's width is
 *       smaller than its preferred width, and the text on screen is not the text in the
 *       string.</li>
 *   <li><b>Overflowing.</b> A label in {@code BorderLayout.WEST} is <i>always</i> given its
 *       full preferred width, so the first check can never catch one; what happens instead is
 *       that it runs off the right edge of the panel and is clipped by it. So the second
 *       check is the absolute one: measured from the panel's own left edge, no label's right
 *       edge may pass the panel's content edge.</li>
 * </ul>
 * Either alone is a guard with a hole in it exactly the shape of one layout manager.
 *
 * <p><b>Laid out by hand rather than by {@code validate()}.</b> {@code Container.validate()}
 * returns without doing anything when the container has no peer, and a build machine with no
 * display never gives one. {@link #layOut} walks the tree calling {@code doLayout()}, which
 * is the part {@code validateTree} would have done and the only part this needs.
 */
public class TruncationGuardTest
{
	/**
	 * How tall to make the panel while measuring: tall enough that nothing wraps for want of
	 * vertical room. Width is the only dimension under test; the sidebar scrolls vertically
	 * and always has.
	 */
	private static final int TALL_ENOUGH = 4_000;

	/**
	 * The fewest labels a screen must have produced for a pass to mean anything.
	 *
	 * <p><b>Catches exactly one thing: a body that renders nothing at all.</b> Not "the
	 * assertion that makes the other two falsifiable" — a layout that silently failed to run
	 * leaves every label at its construction-time zero width, so {@code wanted > given} fires
	 * on the very first one {@link #assertFits} looks at and the width checks fail on their
	 * own, with this count never read. What this alone stops is a walk that finds nothing to
	 * check in the first place — a wiring bug, or a body that throws before adding anything —
	 * which would otherwise leave {@code failures} empty and the test green having verified
	 * zero labels. It is not a floor against a partial collapse: the fixtures below measure
	 * between 15 and 70 labels apiece, so a change that lost three-quarters of the tree and
	 * left a double-digit handful standing would clear this floor and still have to be caught
	 * by the width checks, not by this one.
	 */
	private static final int FEWEST_LABELS = 8;

	private final FakeConfig config = new FakeConfig();

	/**
	 * Knows one name and not the other, so both a resolved favourite row and an unresolved one
	 * are on screen. They are different lengths and both are new — "Gummy" is short and
	 * "NPC 3598" is what an unresolved row falls back to, and a guard that only ever saw one
	 * of them would be half a guard on the newest labels in the panel.
	 */
	private final FakeNpcNames npcNames = new FakeNpcNames()
		.knows(3598, "Gummy")
		.knows(4931, "Elite Black Knight Captain");

	private EntourageRosterPanel panel()
	{
		final EntourageRosterPanel[] built = new EntourageRosterPanel[1];
		onSwing(() -> built[0] = new EntourageRosterPanel(config, config, npcNames));
		return built[0];
	}

	/**
	 * The roster: five cards, a mix of active and greyed, the removable ones carrying a
	 * {@code ×} beside the subtitle that has to share the card with it, the helper paragraph,
	 * the add card and the quick settings.
	 *
	 * <p>The figures are chosen for length rather than for variety. "Elite Black Knight" is
	 * the longest display name in the enum, and it is the one that makes a slot's subtitle the
	 * longest sentence the panel can draw.
	 */
	@Test
	public void nothingOnTheRosterAsksForMoreRoomThanItHas()
	{
		config.setRoster(EntourageFigure.ELITE_BLACK_KNIGHT, EntourageFigure.WISE_OLD_MAN,
			EntourageFigure.SIR_AMIK_VARZE);

		assertFits(panel());
	}

	/**
	 * The worst case for a slot card: every slot wearing a typed id, so every subtitle is
	 * the long "Typed id, or … if refused" sentence <b>and</b> every card still has a
	 * {@code ×} taking a column out of the width that sentence has to fit in.
	 */
	@Test
	public void aTypedIdOnEveryCardStillFits()
	{
		config.setRoster(EntourageFigure.ELITE_BLACK_KNIGHT, EntourageFigure.ELITE_BLACK_KNIGHT,
			EntourageFigure.ELITE_BLACK_KNIGHT, EntourageFigure.ELITE_BLACK_KNIGHT,
			EntourageFigure.ELITE_BLACK_KNIGHT);
		for (int slot = 0; slot < RosterView.SLOTS; slot++)
		{
			config.setCustomNpcIdAt(slot, 4931);
		}

		assertFits(panel());
	}

	/**
	 * The picker: the typed-id card in both its states, the star, the favourites list with
	 * both a resolved and an unresolved row, the "nothing matches" line, the rejected-input
	 * notice, and the figure list including the longest name in the enum with "in this slot"
	 * beside it.
	 *
	 * <p>The favourites rows are the labels with no history at all — they did not exist at any
	 * font size — so they are the ones this test is most for.
	 */
	@Test
	public void nothingInThePickerAsksForMoreRoomThanItHas()
	{
		config.setFigure(EntourageFigure.ELITE_BLACK_KNIGHT)
			.setFavouriteNpcIds("3598,4931,123456789");
		EntourageRosterPanel panel = panel();

		press(pressableFor(panel, "Slot 1"));
		assertFits(panel);

		typeIntoIdBox(panel, "4931");
		assertFits(panel);

		typeIntoIdBox(panel, "not a number");
		assertFits(panel);

		type(panel, "there is no figure named this");
		assertFits(panel);

		type(panel, "knight");
		assertFits(panel);
	}

	/** An empty favourites list draws a paragraph instead of rows, and it wraps too. */
	@Test
	public void thePickerFitsWithNothingStarredYet()
	{
		EntourageRosterPanel panel = panel();

		press(pressableFor(panel, "Slot 1"));

		assertFits(panel);
	}

	/**
	 * A full favourites list, every row unresolved, so every row draws {@code "NPC "} plus the
	 * widest id an {@code int} can hold beside a {@code ×}.
	 *
	 * <p>Not a realistic profile and not meant to be. {@link Favourites#MAX} rows is the most
	 * the panel will ever draw, and {@code Integer.MAX_VALUE} is the longest an unresolved row
	 * can be — if the widest thing that can appear fits, nothing narrower has to be checked
	 * one profile at a time.
	 */
	@Test
	public void theWidestFavouritesListThatCanExistStillFits()
	{
		StringBuilder ids = new StringBuilder();
		for (int index = 0; index < Favourites.MAX; index++)
		{
			ids.append(Integer.MAX_VALUE - index).append(',');
		}
		config.setFavouriteNpcIds(ids.toString());

		EntourageRosterPanel panel = panel();
		press(pressableFor(panel, "Slot 1"));

		assertFits(panel);
	}

	/**
	 * <b>The one guard that would have caught the defect this file is named for.</b> Every
	 * other test above chooses names off {@link EntourageFigure} or off {@link #npcNames}'
	 * own two entries — content this plugin picked, never content a hostile cache could
	 * hand back. {@link FakeNpcNames#knows} is what lets a test choose the name a slot
	 * resolves to at all, which is exactly why nothing above ever exercised this: an ordinary
	 * name was the only kind on offer.
	 *
	 * <p>Two shapes: a single long unbroken token, which {@link EntourageRosterPanel#wrapped}'s
	 * one-cell table cannot wrap at a space that is not there — reviewer-measured against the
	 * panel before sanitizing existed at all, {@code pref=420 given=191} — and a name ending
	 * its own {@code </td></tr></table>}. The token is built from {@code '@'} rather than
	 * {@code 'A'}: {@code 'A'} is the most forgiving glyph this panel ever measures — see
	 * {@link EntourageRosterPanel#MAX_CACHE_NAME_RUN} — so a token built from it fits at any
	 * cap this constant is ever likely to be mistakenly set to, and a fixture that always
	 * fits regardless of the mistake is not a guard.
	 *
	 * <p><b>The table-closing shape is no longer a width defect, and an earlier version of
	 * this javadoc claimed it still was.</b> It used to say this fixture "closes that table
	 * before the width the {@code <td>} was given ever applies (measured
	 * {@code pref=10003 given=191})" — a number measured before
	 * {@link EntourageRosterPanel#sanitizeCacheName} existed, back when the raw string reached
	 * {@link EntourageRosterPanel#wrapped} untouched. It does not any more: sanitizing strips
	 * every {@code <...>} span in the same pass that counts a run, so {@code "Gummy"} is all
	 * that is left of {@code "Gummy</td></tr></table>"} by the time either check could matter,
	 * and mutating away only the run cap — raising {@link EntourageRosterPanel#MAX_CACHE_NAME_RUN}
	 * arbitrarily high — leaves this fixture exactly as harmless as it already was. That
	 * property is pinned at the string level instead, directly and without a rendered panel:
	 * {@code EntourageRosterPanelTest.aColourTagIsRemovedAndTheNameReadsTheWayTheGameShowsIt}
	 * and {@code aNameThatTriesToCloseThePanelsTableHasNoTagsLeftToDoItWith}. What is left worth
	 * checking here is only that the sanitized five characters still render where a slot can
	 * show them, which they always were going to.
	 */
	@Test
	public void aHostileCachedNameStillFitsWhereverASlotCanShowIt()
	{
		StringBuilder unbrokenToken = new StringBuilder();
		for (int index = 0; index < 200; index++)
		{
			unbrokenToken.append('@');
		}

		npcNames.knows(90_001, unbrokenToken.toString());
		npcNames.knows(90_002, "Gummy</td></tr></table>");

		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS)
			.setCustomNpcIdAt(0, 90_001)
			.setFavouriteNpcIds("90002");

		EntourageRosterPanel panel = panel();
		assertFits(panel);

		press(pressableFor(panel, "Slot 1"));
		assertFits(panel);
	}

	/**
	 * <b>The real pin on {@link EntourageRosterPanel#MAX_CACHE_NAME_RUN}.</b> Swept by hand
	 * through this file's own harness at the tightest column — the typed-id card's title,
	 * with both the star and the {@code ×} beside it — the widest run that still fits is
	 * {@code 'A'} 20, {@code 'm'}/{@code '_'} 15, {@code '&'}/{@code 'M'} 13,
	 * {@code 'W'}/{@code '%'} 12, and {@code '@'}/{@code '#'} 10. A sweep built only from
	 * {@code 'A'} — every other test in this file, and the constant's own first measurement
	 * — cannot tell a correct cap from one with 2x slack in it, because twenty is nowhere
	 * near either. This one probes all nine, at whatever
	 * {@link EntourageRosterPanel#MAX_CACHE_NAME_RUN} is actually compiled to — read back via
	 * {@link #measuredRunCap()} rather than hardcoded, since the field is private to that
	 * class — so raising the constant even one character past its true floor for the
	 * tightest glyph fails this on the arithmetic. A run of ten {@code '&'} is in this set
	 * too: escaped to five-characters-wide {@code &amp;} apiece, it is what confirms finding
	 * 9's width regression — a 15-run of {@code '&'} drawing at 165px in a 150px column — is
	 * closed at the cap this constant now ships with.
	 */
	@Test
	public void everyProbedGlyphFitsARunOfTheCapInTheTightestColumn()
	{
		int cap = measuredRunCap();
		char[] glyphs = {'W', 'M', '@', '#', '%', '&', 'm', '_', 'A'};

		for (int glyphIndex = 0; glyphIndex < glyphs.length; glyphIndex++)
		{
			StringBuilder run = new StringBuilder();
			for (int i = 0; i < cap; i++)
			{
				run.append(glyphs[glyphIndex]);
			}

			int npcId = 80_000 + glyphIndex;
			npcNames.knows(npcId, run.toString());
			config.setFigure(EntourageFigure.ROGUE).setCustomNpcIdAt(0, npcId);

			EntourageRosterPanel panel = panel();
			press(pressableFor(panel, "Slot 1"));

			assertFits(panel);
		}
	}

	/**
	 * @return the value {@link EntourageRosterPanel#MAX_CACHE_NAME_RUN} is currently compiled
	 * to, read off its own behaviour rather than hardcoded — the field is private to that
	 * class, so nothing outside it can name the constant directly. A probe well past any
	 * value this is ever set to is fed through
	 * {@link EntourageRosterPanel#sanitizeCacheName}, and the ellipsis it inserts lands
	 * exactly one character past the cap.
	 */
	private static int measuredRunCap()
	{
		StringBuilder probe = new StringBuilder();
		for (int i = 0; i < 55; i++)
		{
			probe.append('x');
		}

		String sanitized = EntourageRosterPanel.sanitizeCacheName(probe.toString());
		int ellipsis = sanitized.indexOf('…');
		assertTrue("the probe was not long enough to find the cap", ellipsis > 0);
		return ellipsis;
	}

	// --- the guard itself ------------------------------------------------------

	/**
	 * Lays the panel out at its real width and checks every label on it.
	 *
	 * @param panel the panel as the user would be looking at it
	 */
	private static void assertFits(EntourageRosterPanel panel)
	{
		final List<String> failures = new ArrayList<>();
		final int[] checked = {0};

		onSwing(() ->
		{
			panel.setSize(new Dimension(PluginPanel.PANEL_WIDTH, TALL_ENOUGH));
			layOut(panel);

			Insets insets = panel.getInsets();
			int contentRight = PluginPanel.PANEL_WIDTH - insets.right;

			for (Component component : all(panel))
			{
				if (!(component instanceof JLabel) || !component.isVisible()
					|| insideACombo(component))
				{
					continue;
				}

				JLabel label = (JLabel) component;
				String text = strip(label.getText());
				if (text.isEmpty())
				{
					continue;
				}

				checked[0]++;

				int wanted = label.getPreferredSize().width;
				int given = label.getWidth();
				if (wanted > given)
				{
					failures.add(String.format(
						"\"%s\" wants %dpx and was given %dpx — it will ellipsise",
						text, wanted, given));
					continue;
				}

				int right = leftEdgeWithin(panel, label) + wanted;
				if (right > contentRight)
				{
					failures.add(String.format(
						"\"%s\" ends at %dpx, past the panel's content edge at %dpx",
						text, right, contentRight));
				}
			}
		});

		assertTrue("the layout produced almost no labels, so this checked nothing: "
			+ checked[0], checked[0] >= FEWEST_LABELS);
		assertTrue("label(s) wider than the room they have:\n" + String.join("\n", failures),
			failures.isEmpty());
	}

	/**
	 * Lays out a tree that has no peer.
	 *
	 * <p>{@code Container.validate()} is the method for this and it does nothing without a
	 * peer — it is guarded on one, and a headless build machine never supplies one. Calling
	 * {@code doLayout()} top-down does the part that matters: each container's layout manager
	 * assigns its children's bounds, and the children then place theirs inside the bounds they
	 * were just given. The root has to be sized first, which {@link #assertFits} does.
	 */
	private static void layOut(Container root)
	{
		root.doLayout();
		for (Component child : root.getComponents())
		{
			if (child instanceof Container)
			{
				layOut((Container) child);
			}
		}
	}

	/**
	 * @return how far {@code component}'s left edge is from {@code root}'s, in pixels,
	 * including {@code root}'s own border — so the number is comparable against
	 * {@link PluginPanel#PANEL_WIDTH} directly rather than against some inner content box
	 * whose extent would itself have to be worked out.
	 */
	private static int leftEdgeWithin(Container root, Component component)
	{
		int x = 0;
		for (Component at = component; at != null && at != root; at = at.getParent())
		{
			x += at.getX();
		}
		return x;
	}

	/**
	 * @return whether this label belongs to a {@code JComboBox}'s own machinery rather than to
	 * this panel. The formation control renders its selected value through a label the Swing
	 * look-and-feel owns and sizes; it is not one of ours, its width is the combo's business,
	 * and holding somebody else's component to this panel's arithmetic would be a failure
	 * nobody here could fix.
	 */
	private static boolean insideACombo(Component component)
	{
		for (Component at = component; at != null; at = at.getParent())
		{
			if (at instanceof JComboBox)
			{
				return true;
			}
		}
		return false;
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
		return label == null ? "" : label.replaceAll("<[^>]*>", "").trim();
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
