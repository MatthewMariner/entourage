package com.matthewmariner.entourage;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The one part of the roster panel that is Swing, exercised without a windowing system.
 *
 * <p>Every <em>decision</em> the panel draws is somewhere else and already under test —
 * {@link RosterView} for the cards, {@link RosterEdit} for what a change writes,
 * {@link FigureSearch} for the list. What is left is wiring, and wiring is exactly the part
 * that is invisible to all three and fatal to the feature: a mouse listener attached to the
 * wrong component, an action drawn on a card that must not have one, a control that writes
 * a setting nothing reads.
 *
 * <p><b>Headless throughout.</b> {@code JPanel} and its children construct fine without a
 * display — it is {@code Frame} and {@code Window} that do not — so this runs on a build
 * machine. Nothing here paints, and nothing here touches the client, which is the panel's
 * own rule rather than this test's convenience: {@code PluginManager} drives the real thing
 * from the event dispatch thread, where a client read throws.
 */
public class EntourageRosterPanelTest
{
	private final FakeConfig config = new FakeConfig();

	/** Built on the event dispatch thread, because that is where the real one is built. */
	private EntourageRosterPanel panel()
	{
		final EntourageRosterPanel[] built = new EntourageRosterPanel[1];
		onSwing(() -> built[0] = new EntourageRosterPanel(config, config));
		return built[0];
	}

	// --- walking the panel, rather than adding seams to it ---------------------

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

	/** Every piece of text on screen, including the one inside a card's own children. */
	private static List<String> textOf(Container root)
	{
		List<String> text = new ArrayList<>();
		for (Component child : all(root))
		{
			if (child instanceof JLabel)
			{
				text.add(strip(((JLabel) child).getText()));
			}
		}
		return text;
	}

	/**
	 * The muted paragraphs are HTML, because a plain label does not wrap. Comparing against
	 * the markup would make every assertion below a restatement of the layout, so it comes
	 * off here.
	 */
	private static String strip(String label)
	{
		if (label == null)
		{
			return "";
		}

		return label.replaceAll("<[^>]*>", "");
	}

	private static boolean shows(Container root, String text)
	{
		for (String found : textOf(root))
		{
			if (found.contains(text))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Presses everything that has a mouse listener and reads as {@code label} — either
	 * because it is that label or because it contains one.
	 *
	 * @return how many things were pressed, so a test can insist there was exactly one
	 */
	private static int click(Container root, String label)
	{
		final int[] pressed = {0};
		onSwing(() ->
		{
			for (Component child : all(root))
			{
				if (child.getMouseListeners().length == 0 || !reads(child, label))
				{
					continue;
				}
				for (MouseListener listener : child.getMouseListeners())
				{
					listener.mousePressed(new MouseEvent(child, MouseEvent.MOUSE_PRESSED,
						0L, 0, 1, 1, 1, false));
				}
				pressed[0]++;
			}
		});
		return pressed[0];
	}

	private static boolean reads(Component child, String label)
	{
		if (child instanceof JLabel && strip(((JLabel) child).getText()).equals(label))
		{
			return true;
		}

		return child instanceof Container && textOf((Container) child).contains(label);
	}

	/**
	 * The smallest thing on screen that carries {@code label} and can be pressed.
	 *
	 * <p>Found by climbing from the label rather than by searching downwards, because
	 * searching downwards finds the whole body first: every container from the panel outward
	 * "contains" every label, so a match on subtree text alone would press the top of the
	 * tree and hit whichever card happened to be first. Climbing stops at the nearest
	 * ancestor that has a mouse listener, which is the card the label is on.
	 */
	private static Container pressableFor(Container root, String label)
	{
		for (Component child : all(root))
		{
			if (!(child instanceof JLabel) || !strip(((JLabel) child).getText()).equals(label))
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

		fail("nothing pressable reads \"" + label + "\"");
		return null;
	}

	/** The row of a labelled control — the one holding the label and its buttons. */
	private static Container rowFor(Container root, String label)
	{
		for (Component child : all(root))
		{
			if (child instanceof JLabel && strip(((JLabel) child).getText()).equals(label))
			{
				return child.getParent();
			}
		}

		fail("no row reads \"" + label + "\"");
		return null;
	}

	private static void press(Component target)
	{
		onSwing(() ->
		{
			for (MouseListener listener : target.getMouseListeners())
			{
				listener.mousePressed(new MouseEvent(target, MouseEvent.MOUSE_PRESSED,
					0L, 0, 1, 1, 1, false));
			}
		});
	}

	/** Presses the card carrying a given title — the whole card is the "change this" target. */
	private static void clickCard(Container root, String title)
	{
		assertEquals("exactly one card reading \"" + title + "\"", 1, click(root, title));
	}

	/** Presses the × on the card carrying a given title. */
	private static void removeCard(Container root, String title)
	{
		Container card = pressableFor(root, title);
		for (Component child : all(card))
		{
			if (child instanceof JLabel && "×".equals(((JLabel) child).getText()))
			{
				press(child);
				return;
			}
		}

		fail("the card reading \"" + title + "\" has no remove action");
	}

	/** How many of the cards on screen offer a remove action. */
	private static int removableCards(Container root)
	{
		int removable = 0;
		for (Component child : all(root))
		{
			if (child instanceof JLabel && "×".equals(((JLabel) child).getText()))
			{
				removable++;
			}
		}
		return removable;
	}

	private static void type(Container root, String query)
	{
		onSwing(() ->
		{
			for (Component child : all(root))
			{
				if (child instanceof IconTextField)
				{
					// RuneLite's own IconTextField.setText asserts it is on this thread, and
					// the test JVM runs with assertions on.
					((IconTextField) child).setText(query);
					return;
				}
			}
			fail("the panel has no search box");
		});
	}

	private static void typeIntoIdBox(Container root, String text)
	{
		onSwing(() ->
		{
			for (Component child : all(root))
			{
				// Not merely "the parent is not an IconTextField": that component nests a
				// FlatTextField around its own JTextField, so the search box's inner field
				// passes that check and swallows the whole test.
				if (!(child instanceof JTextField) || insideTheSearchBox(child))
				{
					continue;
				}

				JTextField field = (JTextField) child;
				field.setText(text);
				for (java.awt.event.ActionListener listener : field.getActionListeners())
				{
					listener.actionPerformed(new java.awt.event.ActionEvent(
						field, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
				}
				return;
			}
			fail("the picker has no id box");
		});
	}

	private static boolean insideTheSearchBox(Component child)
	{
		for (Component up = child; up != null; up = up.getParent())
		{
			if (up instanceof IconTextField)
			{
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private static void chooseFormation(Container root, EntourageFormation formation)
	{
		onSwing(() ->
		{
			for (Component child : all(root))
			{
				if (child instanceof JComboBox)
				{
					((JComboBox<EntourageFormation>) child).setSelectedItem(formation);
					return;
				}
			}
			fail("the quick settings have no formation control");
		});
	}

	/** Runs on the event dispatch thread and waits, so a failure inside it is this test's. */
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

	private static void settle()
	{
		onSwing(() ->
		{
		});
	}

	// --- what it draws ---------------------------------------------------------

	@Test
	public void itBuildsWithNoGameAndNoDisplay()
	{
		assertTrue(panel().getComponentCount() > 0);
	}

	/** Five cards, always — the two past the count included, greyed rather than gone. */
	@Test
	public void everySlotIsOnScreenWhateverTheCountSays()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS)
			.setFigureAt(4, EntourageFigure.GHOMMAL);

		EntourageRosterPanel panel = panel();

		for (int slot = 1; slot <= RosterView.SLOTS; slot++)
		{
			assertTrue("slot " + slot + " is not drawn", shows(panel, "Slot " + slot));
		}
		assertTrue("an inactive slot still names its figure", shows(panel, "Ghommal"));
		assertTrue(shows(panel, "Not walking — raise the count"));
		assertTrue(shows(panel, "Walking with you"));
	}

	/** The helper text is on screen rather than in a tooltip nobody hovers. */
	@Test
	public void itSaysWhatItIsForInPlainWords()
	{
		EntourageRosterPanel panel = panel();

		assertTrue("the thing the cards cannot show on their own",
			shows(panel, "the count decides who walks, not who is set"));
		assertTrue("and where the typed id lives, which is otherwise one click deep",
			shows(panel, "Slot 1 will also wear any NPC id you type."));
	}

	// --- changing who is in a slot ---------------------------------------------

	/**
	 * The click that is the whole point. Nothing above this can catch a listener attached to
	 * the wrong component, and a card you cannot press is the feature not existing.
	 */
	@Test
	public void pressingACardOpensTheSearchAndPickingFromItWritesTheSlot()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS);
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Hans");
		assertTrue("the picker names the slot it is picking for",
			shows(panel, "Who walks in slot 2?"));

		type(panel, "ghommal");
		assertEquals("exactly one row to press", 1, click(panel, "Ghommal"));

		assertSame("the second slot, and only the second", EntourageFigure.GHOMMAL,
			config.view().getSlot(1).getFigure());
		assertSame(EntourageFigure.ROGUE, config.view().getSlot(0).getFigure());
		assertTrue("and it goes back to the roster once you have chosen",
			shows(panel, "Slot 1"));
	}

	@Test
	public void aNameNothingAnswersToSaysSoRatherThanGoingBlank()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		type(panel, "zulrah");

		assertTrue(shows(panel, "Nothing here answers to that."));
	}

	/** A typo finds the figure, which is the point of the near tier being in there at all. */
	@Test
	public void aMisspelledNameStillFindsTheFigure()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		type(panel, "vanaka");

		assertEquals(1, click(panel, "Vannaka"));
		assertSame(EntourageFigure.VANNAKA, config.view().getSlot(0).getFigure());
	}

	@Test
	public void thePickerMarksTheFigureAlreadyInTheSlot()
	{
		config.setFigure(EntourageFigure.PIRATE);
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Pirate");

		assertTrue(shows(panel, "in this slot"));
	}

	/**
	 * <b>A typed id is what is in the slot, not its fallback figure.</b> The card above the
	 * list already says "Wearing NPC 4931"; if the fallback in the list underneath also
	 * claimed "in this slot" the two would contradict each other on the same screen. The
	 * fixture above this one has no custom id set, so {@code !slot.isCustom()} never runs
	 * and a mutation that deleted it would have passed unnoticed.
	 */
	@Test
	public void thePickerDoesNotMarkTheFallbackFigureWhileATypedIdIsInForce()
	{
		config.setFigure(EntourageFigure.PIRATE).setCustomNpcId(4931);
		EntourageRosterPanel panel = panel();

		clickCard(panel, "NPC 4931");

		assertTrue("the card still says which id is actually in the slot",
			shows(panel, "Wearing NPC 4931"));
		assertTrue("the fallback figure is still offered in the list",
			shows(panel, "Pirate"));
		assertFalse("but it is not what is in the slot — the typed id is",
			shows(panel, "in this slot"));
	}

	@Test
	public void thereIsAWayBackWithoutChoosingAnybody()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");
		assertTrue(shows(panel, "Who walks in slot 1?"));

		assertEquals(1, click(panel, "<  Back to the roster"));

		assertTrue(shows(panel, "Slot 5"));
		assertSame("and nothing was changed on the way out",
			EntourageFigure.ROGUE, config.view().getSlot(0).getFigure());
	}

	// --- the roster, as a list ---------------------------------------------------

	/**
	 * <b>The remove action is drawn on the cards that are in the roster and on no others.</b>
	 * A greyed slot has nothing to take out, and the last follower cannot be taken out at
	 * all.
	 */
	@Test
	public void onlyTheCardsInTheRosterOfferARemove()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE);

		assertEquals("three walking with you, three ×s, and nothing on the two greyed cards",
			3, removableCards(panel()));
	}

	/** One at a time, which is what a person does. */
	@Test
	public void removingTheMiddleCardMovesTheOneBehindItUp()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE);
		EntourageRosterPanel panel = panel();

		removeCard(panel, "Hans");

		assertEquals(2, config.view().getFollowers());
		assertSame("the first card is untouched",
			EntourageFigure.ROGUE, config.view().getSlot(0).getFigure());
		assertSame("the Pirate moved up into the gap",
			EntourageFigure.PIRATE, config.view().getSlot(1).getFigure());
		assertFalse("and Hans is not still on screen somewhere", shows(panel, "Hans"));
	}

	/**
	 * <b>The last follower has no remove action drawn at all.</b> Not merely inert: a × that
	 * does nothing is a control that reads as broken, and the reason it cannot work — a
	 * roster of nobody is the plugin's own off switch — is not something a greyed glyph
	 * explains.
	 */
	@Test
	public void theLastFollowerHasNoRemoveActionOnItsCard()
	{
		config.setRoster(EntourageFigure.ROGUE);

		assertFalse("a roster of one draws no ×", shows(panel(), "×"));
	}

	@Test
	public void theAddCardRaisesTheCountAndDisappearsAtFive()
	{
		EntourageRosterPanel panel = panel();
		assertTrue(shows(panel, "+  Add a follower"));

		assertEquals(1, click(panel, "+  Add a follower"));
		assertEquals(2, config.view().getFollowers());

		for (int more = 0; more < 3; more++)
		{
			click(panel, "+  Add a follower");
		}

		assertEquals(5, config.view().getFollowers());
		assertFalse("there is no sixth follower to offer", shows(panel, "+  Add a follower"));
	}

	// --- the quick settings ------------------------------------------------------

	/**
	 * The pips are found inside their own row rather than anywhere on the panel, and they
	 * have to be: "Followers" runs one to five and "Follow distance" runs one to two, so a
	 * search for a button reading "2" finds one of each.
	 */
	@Test
	public void theCountCanBeSetOutrightFromTheQuickSettings()
	{
		EntourageRosterPanel panel = panel();

		assertTrue(shows(panel, "Quick settings"));
		press(pressableFor(rowFor(panel, "Followers"), "4"));

		assertEquals(4, config.view().getFollowers());
		assertEquals("and the scene spawns four", 4, config.settings().getRosterSize());
	}

	@Test
	public void theFormationCanBeChangedFromTheQuickSettings()
	{
		EntourageRosterPanel panel = panel();

		chooseFormation(panel, EntourageFormation.HANGOUT);

		assertSame(EntourageFormation.HANGOUT, config.view().getFormation());
		assertSame("and the scene reads the same one",
			EntourageFormation.HANGOUT, config.settings().getFormation());
	}

	/**
	 * <b>Redrawing must not write.</b> The formation control is a combo box, and
	 * {@code setSelectedItem} fires the same event a click does — so a listener attached
	 * before the current value was set would write the config on every redraw, including the
	 * redraw a write itself causes.
	 */
	@Test
	public void drawingThePanelWritesNothingAtAll()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS)
			.setFormation(EntourageFormation.WEDGE).clearWrites();

		EntourageRosterPanel panel = panel();
		settle();

		assertTrue("building the panel wrote " + config.writes(), config.writes().isEmpty());
		assertSame(EntourageFormation.WEDGE, config.view().getFormation());

		// And again after a redraw prompted from outside, which is the loop that would spin.
		panel.refresh();
		settle();
		assertTrue(config.writes().isEmpty());
	}

	@Test
	public void theFollowDistanceCanBeChangedFromTheQuickSettings()
	{
		EntourageRosterPanel panel = panel();

		press(pressableFor(rowFor(panel, "Follow distance"), "2"));

		assertEquals(2, config.view().getFollowDistance());
		assertEquals("and the scene walks them two tiles out",
			2, config.settings().getFollowDistance());
		assertEquals("without touching the roster", 1, config.view().getFollowers());
	}

	@Test
	public void theQuickSettingsFoldAway()
	{
		EntourageRosterPanel panel = panel();
		assertTrue(shows(panel, "Formation"));

		assertEquals(1, click(panel, "Quick settings"));

		assertFalse("folded away", shows(panel, "Formation"));
		assertTrue("and the heading is still there to fold back", shows(panel, "Quick settings"));

		click(panel, "Quick settings");
		assertTrue(shows(panel, "Formation"));
	}

	// --- the typed NPC id --------------------------------------------------------

	/** Offered where the setting applies, and nowhere else. */
	@Test
	public void theTypedIdIsOfferedForTheFirstSlotOnly()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS);
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Hans");
		assertFalse("no other slot can wear one", shows(panel, "Any NPC, by id"));

		click(panel, "<  Back to the roster");
		clickCard(panel, "Rogue");
		assertTrue(shows(panel, "Any NPC, by id"));
	}

	@Test
	public void typingAnIdPutsThatNpcInTheFirstSlot()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		typeIntoIdBox(panel, "4931");

		assertTrue(config.view().isCustom());
		assertEquals(4931, config.view().getCustomNpcId());
		assertTrue("and the follower the scene builds wears it",
			config.settings().getBodies().get(0).isCustom());
	}

	@Test
	public void theCardSaysWhichIdIsInForceAndWhatItFallsBackTo()
	{
		config.setFigure(EntourageFigure.VANNAKA).setCustomNpcId(4931);

		EntourageRosterPanel panel = panel();

		assertTrue(shows(panel, "NPC 4931"));
		assertTrue(shows(panel, "Typed id, or Vannaka if refused"));
	}

	@Test
	public void emptyingTheIdBoxGivesTheDropdownFigureBack()
	{
		config.setCustomNpcId(4931);
		EntourageRosterPanel panel = panel();
		clickCard(panel, "NPC 4931");

		typeIntoIdBox(panel, "   ");

		assertFalse(config.view().isCustom());
		assertTrue(shows(panel, "Rogue"));
	}

	/**
	 * <b>A box that ignores what was typed is, from the outside, the same box that ignores a
	 * correct id.</b> So it says so.
	 */
	@Test
	public void somethingThatIsNotANumberIsSaidOutLoudRatherThanSwallowed()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		typeIntoIdBox(panel, "vannaka");

		assertTrue(shows(panel, "That is not an id."));
		assertFalse("and nothing was written", config.view().isCustom());
	}

	@Test
	public void pickingAFigureForTheFirstSlotClearsTheTypedIdThroughThePanel()
	{
		config.setCustomNpcId(4931);
		EntourageRosterPanel panel = panel();

		clickCard(panel, "NPC 4931");
		type(panel, "ghommal");
		click(panel, "Ghommal");

		assertFalse("the id would otherwise have gone on overriding the choice",
			config.view().isCustom());
		assertTrue(shows(panel, "Ghommal"));
	}
}
