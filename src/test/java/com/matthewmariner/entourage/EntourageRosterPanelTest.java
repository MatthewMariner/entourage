package com.matthewmariner.entourage;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

	/**
	 * Knows no names unless a test teaches it one, which is what a cold cache and the login
	 * screen both look like — so every assertion below that reads "NPC 4931" is asserting the
	 * fallback the panel is supposed to draw rather than merely the absence of a feature.
	 */
	private final FakeNpcNames npcNames = new FakeNpcNames();

	/** Built on the event dispatch thread, because that is where the real one is built. */
	private EntourageRosterPanel panel()
	{
		final EntourageRosterPanel[] built = new EntourageRosterPanel[1];
		onSwing(() -> built[0] = new EntourageRosterPanel(config, config, npcNames));
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
			shows(panel, "Any slot will wear an NPC id you type"));
		assertTrue("and that the ids can be kept, which is the only mention of favourites "
			+ "anywhere on the roster screen", shows(panel, "you can star the ones you like"));
	}

	// --- sanitizing a cache name -------------------------------------------------

	/**
	 * {@code NPCComposition.getName()} is the one string on this panel whose content the
	 * plugin does not choose, and {@link EntourageRosterPanel#nameFor} is its only door in —
	 * see {@link EntourageRosterPanel#sanitizeCacheName}. Every payload here is one a
	 * reviewer actually probed the panel with, through {@link FakeNpcNames#knows}, which is
	 * exactly why the width guard in {@code TruncationGuardTest} never caught any of them
	 * before: nothing before this test ever pinned a hostile name there, only ordinary ones.
	 */
	@Test
	public void anOrdinaryNamePassesThroughUnchanged()
	{
		assertEquals("Gummy", EntourageRosterPanel.sanitizeCacheName("Gummy"));
	}

	/**
	 * A real in-game colour tag renders as plain text — the client draws
	 * {@code <col=00ffff>Gummy</col>} as "Gummy", never as literal angle brackets — so
	 * stripping it here is drawing the name the way the game already shows it, not censoring
	 * something the user would otherwise have seen.
	 */
	@Test
	public void aColourTagIsRemovedAndTheNameReadsTheWayTheGameShowsIt()
	{
		assertEquals("Gummy", EntourageRosterPanel.sanitizeCacheName("<col=00ffff>Gummy</col>"));
	}

	/**
	 * The payload this fix exists for. {@link EntourageRosterPanel#wrapped} builds its HTML
	 * by concatenating this name into the middle of a {@code <table>}, so a name ending its
	 * own {@code </td></tr></table>} would close that table early — unless no tag survives
	 * sanitizing to do it.
	 */
	@Test
	public void aNameThatTriesToCloseThePanelsTableHasNoTagsLeftToDoItWith()
	{
		String sanitized = EntourageRosterPanel.sanitizeCacheName("Gummy</td></tr></table>");

		assertFalse("no angle bracket survived", sanitized.contains("<"));
		assertFalse("no angle bracket survived", sanitized.contains(">"));
		assertTrue("the real name is still the start of it", sanitized.startsWith("Gummy"));
	}

	/**
	 * Unlike a tag, an {@code &} is not markup an NPC's name would never really contain, so
	 * it is escaped rather than removed — an NPC actually named with one should still show
	 * one.
	 */
	@Test
	public void anAmpersandIsEscapedRatherThanRemoved()
	{
		assertEquals("A &amp; B", EntourageRosterPanel.sanitizeCacheName("A & B"));
	}

	/**
	 * <b>The shape a fixed-width column cannot wrap around.</b> An ordinary multi-word name
	 * wraps at its spaces however long it is; a single 200-character token has none, so it is
	 * the one shape {@link EntourageRosterPanel#sanitizeCacheName} has to cut down on its own
	 * rather than leave to {@link EntourageRosterPanel#wrapped}.
	 */
	@Test
	public void aLongUnbrokenTokenIsCutWithATrailingEllipsis()
	{
		StringBuilder token = new StringBuilder();
		for (int i = 0; i < 200; i++)
		{
			token.append('A');
		}

		String sanitized = EntourageRosterPanel.sanitizeCacheName(token.toString());

		assertTrue("cut well inside any column this panel ever hands it to",
			sanitized.length() < 30);
		assertTrue("says out loud that it was cut", sanitized.endsWith("…"));
	}

	/**
	 * <b>The regression a flat cap causes, pinned by name.</b> The first version of the cut
	 * above measured the whole string rather than each run, at a limit sized for the tightest
	 * column — which is shorter than several of the names a user of this plugin is most likely
	 * to type an id for. It rendered {@code General Graardor} as {@code General Graardo…} in a
	 * panel whose whole purpose is standing an entourage next to a boss.
	 *
	 * <p>Not every name here has a run anywhere near the cap — {@code Corporeal Beast}'s
	 * longest is nine characters and {@code Wise Old Man}'s is four — and that is deliberate
	 * rather than an oversight this javadoc used to claim otherwise about: they are here so
	 * that whichever cap {@code MAX_CACHE_NAME_RUN} is set to, an ordinary multi-word boss or
	 * NPC name is never touched by it. The ones that do run close to the cap —
	 * {@code General Graardor}, {@code Commander Zilyana}, {@code K'ril Tsutsaroth},
	 * {@code Dagannoth Supreme} and {@code Elite Black Knight Captain} — are the ones
	 * {@code MAX_CACHE_NAME_RUN}'s own javadoc measures its cost against. None of them is the
	 * shape that overflows, because every one has a space to wrap at, and every name here must
	 * come back exactly as it went in.
	 */
	@Test
	public void aRealBossNameLongerThanTheRunLimitIsNotCutAtAll()
	{
		for (String name : new String[]{
			"General Graardor",
			"Commander Zilyana",
			"K'ril Tsutsaroth",
			"Dagannoth Supreme",
			"Elite Black Knight Captain",
			"Corporeal Beast",
			"Wise Old Man",
		})
		{
			assertEquals(name, EntourageRosterPanel.sanitizeCacheName(name));
		}
	}

	/**
	 * The other half of the same rule: a run really does have to be a run. Two long tokens
	 * with a space between them are each cut on their own rather than the second one being
	 * spared because the first already spent the budget.
	 */
	@Test
	public void everyLongRunIsCutSeparatelyRatherThanOnlyTheFirst()
	{
		String sanitized = EntourageRosterPanel.sanitizeCacheName(
			"AAAAAAAAAAAAAAAAAAAAAAAA BBBBBBBBBBBBBBBBBBBBBBBB");

		assertEquals("AAAAAAAAAA… BBBBBBBBBB…", sanitized);
	}

	/**
	 * A name with no long run in it at all can still be unreasonable, by being made of a great
	 * many short words. Wrapping turns that into height rather than width, so the ceiling on it
	 * is far looser than the run limit — but there is one.
	 */
	@Test
	public void aNameOfManyShortWordsIsStillBoundedOverall()
	{
		StringBuilder many = new StringBuilder();
		for (int i = 0; i < 100; i++)
		{
			many.append("ab ");
		}

		String sanitized = EntourageRosterPanel.sanitizeCacheName(many.toString());

		assertTrue("bounded", sanitized.length() <= 61);
		assertTrue("says out loud that it was cut", sanitized.endsWith("…"));
	}

	/**
	 * <b>{@link EntourageRosterPanel#nameFor}'s contract is "never blank, never
	 * {@code \"null\"}", and a resolved name can still be either.</b> An all-markup name
	 * sanitises to the empty string — {@link EntourageRosterPanel#sanitizeCacheName} strips
	 * every {@code <...>} span, and a name that is nothing but tags has nothing left — and
	 * the game's own placeholder for an NPC composition with no name is not a Java
	 * {@code null} but the literal four characters {@code "null"}. Both used to draw
	 * straight through: the empty string as "Wearing " with nothing after it, and
	 * {@code "null"} as the word sitting right there beside "Wearing". Reverting
	 * {@link NpcNames#isRealName}'s check in {@code nameFor} back to a bare
	 * {@code sanitizeCacheName(name)} reproduces both.
	 */
	@Test
	public void anUnusableCachedNameFallsBackToTheId()
	{
		config.setFigure(EntourageFigure.PIRATE).setCustomNpcId(4931);
		npcNames.knows(4931, "<col=00ffff></col>");
		EntourageRosterPanel allMarkup = panel();
		press(pressableFor(allMarkup, "Slot 1"));

		assertTrue("an all-markup name has nothing left to show, so it falls back to the id",
			shows(allMarkup, "Wearing NPC 4931"));

		config.setCustomNpcId(3598);
		npcNames.knows(3598, "null");
		EntourageRosterPanel cachesPlaceholder = panel();
		press(pressableFor(cachesPlaceholder, "Slot 1"));

		assertTrue("the cache's own placeholder for an unnamed NPC must not be drawn as text",
			shows(cachesPlaceholder, "Wearing NPC 3598"));
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

	/**
	 * <b>The park switch is on the panel, because that is where it gets pressed.</b> It is
	 * the one control here somebody uses in the middle of something — park the group, fight,
	 * unpark them — and the settings screen is three clicks and a scroll away from a boss.
	 */
	@Test
	public void theEntourageCanBeParkedAndUnparkedFromTheQuickSettings()
	{
		EntourageRosterPanel panel = panel();
		assertTrue(shows(panel, "Movement"));
		assertFalse("a fresh install follows", config.view().isStayPut());

		assertEquals(1, click(panel, "Stay put"));

		assertTrue(config.view().isStayPut());
		assertTrue("and the scene reads the same one", config.settings().isStayPut());

		assertEquals(1, click(panel, "Follow me"));

		assertFalse(config.view().isStayPut());
		assertFalse(config.settings().isStayPut());
	}

	/**
	 * <b>Both states are drawn at once, and the colour — not just the text — has to say
	 * which one is in force.</b> A checkbox says what would happen if you clicked it and
	 * leaves you to infer the state; two words, one lit, say which one you are in, which is
	 * what a control pressed without looking at it needs to do.
	 *
	 * <p>The version of this test that shape replaced asserted only that both labels
	 * existed, in both states — the identical pair of {@code shows()} calls before and after
	 * {@code config.setStayPut(true)}, which the flip never touched. Swapping the two
	 * {@code lit} arguments at {@code EntourageRosterPanel.java:615} and {@code :618} — so
	 * "Stay put" glows while you are following — left every one of those checks green,
	 * because "the text is on screen" does not change when only the colour is wrong.
	 * {@link #chipColour} reads {@link javax.swing.JLabel#getForeground()} directly instead:
	 * exactly one chip may carry {@link ColorScheme#BRAND_ORANGE}, and it has to be the one
	 * naming the state actually in force, in both states.
	 */
	@Test
	public void theLitChipIsWhicheverMovementStateIsActuallyInForce()
	{
		assertEquals("following: \"Follow me\" is lit and \"Stay put\" is not",
			ColorScheme.BRAND_ORANGE, chipColour(panel(), "Follow me"));
		assertNotEquals(ColorScheme.BRAND_ORANGE, chipColour(panel(), "Stay put"));

		config.setStayPut(true);

		assertEquals("parked: \"Stay put\" is lit and \"Follow me\" is not",
			ColorScheme.BRAND_ORANGE, chipColour(panel(), "Stay put"));
		assertNotEquals(ColorScheme.BRAND_ORANGE, chipColour(panel(), "Follow me"));
	}

	/** @return the foreground colour of the one chip whose text is exactly {@code text} */
	private static java.awt.Color chipColour(Container root, String text)
	{
		for (Component child : all(root))
		{
			if (child instanceof JLabel && text.equals(((JLabel) child).getText()))
			{
				return ((JLabel) child).getForeground();
			}
		}

		fail("no chip reads \"" + text + "\"");
		return null;
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

	/**
	 * <b>Offered wherever the setting applies, which is now every slot.</b>
	 *
	 * <p>This test used to be named for the opposite claim and assert it: opening slot 2's
	 * picker was expected <i>not</i> to show the id card, because there was one key and it
	 * belonged to the first slot. There are five keys now. The premise changed, so the test
	 * did — it is the same assertion pointed at the rule that is true.
	 */
	@Test
	public void theTypedIdIsOfferedForEverySlot()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE,
			EntourageFigure.VANNAKA, EntourageFigure.TURAEL);
		EntourageRosterPanel panel = panel();

		for (int slot = 1; slot <= RosterView.SLOTS; slot++)
		{
			clickCard(panel, "Slot " + slot);
			assertTrue("slot " + slot + " is not offered a typed id",
				shows(panel, "Any NPC, by id"));
			click(panel, "<  Back to the roster");
		}
	}

	/**
	 * <b>Five boxes, five keys, and each one writes its own.</b> The failure this exists for
	 * is silent and total: five cards all writing {@code customNpcId} would look correct on
	 * the card that was just typed into and would move every other slot with it.
	 */
	@Test
	public void eachSlotsTypedIdIsItsOwn()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE,
			EntourageFigure.VANNAKA, EntourageFigure.TURAEL);
		EntourageRosterPanel panel = panel();

		for (int slot = 0; slot < RosterView.SLOTS; slot++)
		{
			clickCard(panel, "Slot " + (slot + 1));
			typeIntoIdBox(panel, Integer.toString(3000 + slot));
			click(panel, "<  Back to the roster");
		}

		for (int slot = 0; slot < RosterView.SLOTS; slot++)
		{
			assertTrue("slot " + slot + " lost its typed id to a neighbour",
				config.view().getSlot(slot).isCustom());
			assertEquals(3000 + slot, config.view().getSlot(slot).getCustomNpcId());
		}

		assertEquals("and the scene builds five different bodies", 5,
			new java.util.HashSet<>(config.settings().getBodies()).size());
	}

	/** Clearing one slot's id leaves the other four exactly as they were. */
	@Test
	public void clearingOneSlotsTypedIdLeavesTheOthersAlone()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE)
			.setCustomNpcIdAt(0, 4931).setCustomNpcIdAt(1, 4932).setCustomNpcIdAt(2, 4933);
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Slot 2");
		typeIntoIdBox(panel, "   ");

		assertFalse("the slot that was cleared", config.view().getSlot(1).isCustom());
		assertEquals(4931, config.view().getSlot(0).getCustomNpcId());
		assertEquals(4933, config.view().getSlot(2).getCustomNpcId());
	}

	@Test
	public void typingAnIdPutsThatNpcInTheFirstSlot()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		typeIntoIdBox(panel, "4931");

		assertTrue(config.view().getSlot(0).isCustom());
		assertEquals(4931, config.view().getSlot(0).getCustomNpcId());
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

		assertFalse(config.view().getSlot(0).isCustom());
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
		assertFalse("and nothing was written", config.view().getSlot(0).isCustom());
	}

	// --- the favourites ------------------------------------------------------------

	/** Nothing starred yet says so, rather than leaving a heading over empty space. */
	@Test
	public void thePickerSaysWhereFavouritesComeFromBeforeThereAreAny()
	{
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Rogue");

		assertTrue(shows(panel, "Favourites"));
		assertTrue(shows(panel, "Nothing starred yet"));
	}

	/**
	 * The gesture the owner asked for, end to end: type an id, use it, decide you like it,
	 * press the star — and find it still there afterwards without having to remember 3598.
	 */
	@Test
	public void starringATypedIdKeepsIt()
	{
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		typeIntoIdBox(panel, "3598");
		assertEquals("the hollow star is the offer", 1, click(panel, "☆"));

		assertEquals(Collections.singletonList(3598), config.view().getFavourites());
		assertTrue("and it is on the list on screen", shows(panel, "NPC 3598"));
		assertFalse("the offer is gone", shows(panel, "☆"));
		assertTrue("and the filled star is what is there instead", shows(panel, "★"));
	}

	/** Pressing the filled star is how you stop keeping one. */
	@Test
	public void pressingTheFilledStarStopsKeepingTheId()
	{
		config.setCustomNpcId(3598).setFavouriteNpcIds("3598");
		EntourageRosterPanel panel = panel();
		clickCard(panel, "NPC 3598");

		assertEquals(1, click(panel, "★"));

		assertTrue(config.view().getFavourites().isEmpty());
		assertTrue("and the offer to keep it is back", shows(panel, "☆"));
	}

	/**
	 * <b>The point of the list: apply one to the slot you are picking for.</b> Not to slot 1
	 * — to whichever slot's picker is open, which is the whole reason the favourites live
	 * inside a picker rather than on the roster screen.
	 */
	@Test
	public void pressingAFavouriteAppliesItToTheSlotBeingPickedFor()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE)
			.setFavouriteNpcIds("3598");
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Pirate");
		assertEquals(1, click(panel, "NPC 3598"));

		assertEquals("the third slot, and only the third",
			3598, config.view().getSlot(2).getCustomNpcId());
		assertFalse(config.view().getSlot(0).isCustom());
		assertFalse(config.view().getSlot(1).isCustom());
		assertTrue("and it is still kept afterwards", config.view().isFavourite(3598));
	}

	@Test
	public void theXOnAFavouriteRowTakesItOffTheListWithoutTouchingTheSlot()
	{
		config.setFavouriteNpcIds("3598,4931");
		EntourageRosterPanel panel = panel();
		clickCard(panel, "Rogue");

		removeCard(panel, "NPC 4931");

		assertEquals(Collections.singletonList(3598), config.view().getFavourites());
		assertFalse("removing a favourite is not a change of roster",
			config.view().getSlot(0).isCustom());
	}

	/**
	 * <b>A name arrives from the client and the row starts using it.</b> The id is what a row
	 * says until then, which is the whole reason a favourite is stored as an integer.
	 */
	@Test
	public void aFavouriteReadsAsItsNameOnceTheClientHasAnswered()
	{
		npcNames.knows(3598, "Gummy");
		config.setFavouriteNpcIds("3598");
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Rogue");

		assertTrue("the name the client gave", shows(panel, "Gummy"));
		assertTrue("and the panel asked for it rather than reading it inline",
			npcNames.asked().contains(3598));
	}

	/**
	 * <b>A name that never comes back is the bare id, not "null" and not a blank row.</b>
	 * This is the ordinary state at the login screen and on a cold cache, where nothing
	 * resolves at all.
	 */
	@Test
	public void aFavouriteWithNoNameYetReadsAsItsId()
	{
		npcNames.knowsNothing();
		config.setFavouriteNpcIds("3598");
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Rogue");

		assertTrue(shows(panel, "NPC 3598"));
		assertFalse("never the word null", shows(panel, "null"));
	}

	/**
	 * <b>Once per id, not once per redraw.</b> Every answer that carries a new name causes a
	 * redraw and every redraw would otherwise ask again — a loop, and at the login screen a
	 * loop that never converges. Asserted as a count rather than as a shape, because the
	 * shape is exactly what a future refactor would change without noticing.
	 */
	@Test
	public void aNameIsAskedForOncePerIdRatherThanOncePerRedraw()
	{
		npcNames.knows(3598, "Gummy");
		config.setFavouriteNpcIds("3598");
		EntourageRosterPanel panel = panel();

		clickCard(panel, "Rogue");
		panel.refresh();
		settle();
		panel.refresh();
		settle();

		assertEquals("asked " + npcNames.asked(), 1, Collections.frequency(npcNames.asked(), 3598));
	}

	/**
	 * <b>A full list refuses out loud rather than quietly dropping the oldest.</b>
	 * {@link Favourites#with} would drop it — something has to give — but a control that
	 * silently discards something you saved is worse than one that says it is full.
	 */
	@Test
	public void starringAtTheCapSaysSoRatherThanDroppingTheOldest()
	{
		StringBuilder ids = new StringBuilder();
		for (int index = 0; index < Favourites.MAX; index++)
		{
			ids.append(1_000 + index).append(',');
		}
		config.setFavouriteNpcIds(ids.toString()).setCustomNpcId(3598);

		EntourageRosterPanel panel = panel();
		clickCard(panel, "NPC 3598");

		assertEquals(1, click(panel, "☆"));

		assertFalse("nothing was starred", config.view().isFavourite(3598));
		assertTrue("the oldest is still there", config.view().isFavourite(1_000));
		assertTrue(shows(panel, "The favourites list is full"));
	}

	/** Every slot's picker offers the same list, because the list is not a slot's property. */
	@Test
	public void theSameFavouritesAreOfferedInEverySlotsPicker()
	{
		config.setRoster(EntourageFigure.ROGUE, EntourageFigure.HANS, EntourageFigure.PIRATE,
			EntourageFigure.VANNAKA, EntourageFigure.TURAEL).setFavouriteNpcIds("3598");
		EntourageRosterPanel panel = panel();

		for (int slot = 1; slot <= RosterView.SLOTS; slot++)
		{
			clickCard(panel, "Slot " + slot);
			assertTrue("slot " + slot + " is not offered the favourites",
				shows(panel, "NPC 3598"));
			click(panel, "<  Back to the roster");
		}
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
			config.view().getSlot(0).isCustom());
		assertTrue(shows(panel, "Ghommal"));
	}
}
