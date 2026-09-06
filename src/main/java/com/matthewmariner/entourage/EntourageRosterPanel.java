package com.matthewmariner.entourage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The sidebar: five cards saying who walks with you, and a search to change any of them.
 *
 * <p>The surface the plugin was missing. Choosing an entourage meant five dropdowns of
 * twenty-three items each — a hundred and fifteen rows to read to answer "is the Wise Old
 * Man in there?" — plus a raw number field for a typed NPC id, all of it drawn by
 * RuneLite's own config screen, which a plugin cannot style and cannot add a verb to. The
 * complaint was that it looked plain. Underneath that, the roster was the part that was
 * hard to <em>use</em>.
 *
 * <h2>It decides nothing</h2>
 *
 * <p>Every card comes out of {@link RosterView}, every change goes through
 * {@link RosterEdit}, and every name in the search list comes out of {@link FigureSearch} —
 * all three static, offline and under test. What is left here is layout, colour and mouse
 * handling, which is the part no test can reach without a windowing system, and keeping it
 * to that is deliberate. It is the same split {@code ../gunnars-tools} draws between
 * {@code MonsterLookupPanel} and {@code MonsterIndex}.
 *
 * <h2>The config is the store, and there is no other one</h2>
 *
 * <p>This panel holds no roster of its own. Every control writes through
 * {@link ConfigWriter} into the ordinary keys and then reads the profile straight back to
 * redraw, so a change made here shows up in RuneLite's settings screen, a change made there
 * shows up here — {@link #refresh()} is how — and everything persists through RuneLite's
 * normal profile mechanism. The only state this class keeps is which slot is being picked
 * for and whether the quick settings are folded open, neither of which is a setting.
 *
 * <h2>It never reads the client, and that is a rule rather than an omission</h2>
 *
 * <p>{@code PluginManager} calls {@code startUp()} and every listener below from the event
 * dispatch thread, and a client read off the client thread throws — an
 * {@code IllegalStateException} in a shipped client. So nothing here asks the client
 * anything: the figures are an enum, the settings are a config proxy, and both are safe to
 * read from any thread. The one thing a live client could tell us and this cannot — what
 * NPC 4931 is actually called — is why a typed id is drawn as a number. Marshalling a
 * lookup onto the client thread and pushing the answer back would work, the way
 * {@code ../gunnars-tools}' {@code SidePanel#refresh} does it; it is not done because a
 * card that says "NPC 4931" is honest and a card that says nothing until the client answers
 * is a card that flickers.
 *
 * <h2>Colour</h2>
 *
 * <p>Every colour is {@link ColorScheme}'s, so the panel is the same panel when RuneLite's
 * theme changes and looks like the rest of the client rather than like a plugin. The two
 * fonts are {@link FontManager}'s, except on the two glyph actions, which use the client's
 * own default face — the game font is a bitmap face and {@code ×} is not in it.
 */
class EntourageRosterPanel extends PluginPanel
{
	/** Not picking for any slot: the roster is what is on screen. */
	private static final int ROSTER = -1;

	/**
	 * How wide a wrapped line inside a slot card is allowed to be.
	 *
	 * <p>Narrower than {@link #PANEL_WIDTH}: the card's own border eats 16 pixels
	 * ({@code EmptyBorder(6, 8, 6, 8)}) before a subtitle ever sees them, and a
	 * removable slot's × column eats a further ~24. Sized for that worst case rather
	 * than measured per-card, so a subtitle never has to ask whether its card happens
	 * to have a × on it today — see {@link #slotCard}.
	 */
	private static final int CARD_TEXT_WIDTH = 150;

	/**
	 * What the panel is for, in the register the rest of the plugin's documentation uses.
	 *
	 * <p>It says the two things somebody has to know that are not visible from the cards:
	 * that a slot past the count keeps its figure rather than losing it, and that the first
	 * slot will take any NPC id. Everything else on this panel explains itself by being
	 * pressed.
	 */
	private static final String HELPER_TEXT =
		"Five slots, as many of them walking with you as the count allows. Press a card to "
			+ "change who is in it; press the &times; to take one out and move the rest up. A "
			+ "greyed slot keeps its figure — the count decides who walks, not who is set. "
			+ "Slot 1 will also wear any NPC id you type.";

	/** The muted line under the search when a query matches nothing. */
	private static final String NO_MATCH = "Nothing here answers to that.";

	/** What the id box says when what was typed is not a number. */
	private static final String NOT_A_NUMBER = "That is not an id. Type a number, like 4931.";

	private final EntourageConfig config;
	private final ConfigWriter writer;

	private final IconTextField search = new IconTextField();
	private final JPanel north = new JPanel();
	private final JPanel body = new JPanel();

	/** Which slot the picker is choosing for, or {@link #ROSTER}. */
	private int picking = ROSTER;

	/** Whether the quick settings are folded open. Open to begin with: they are the point. */
	private boolean quickOpen = true;

	/** Set when a typed id would not parse, cleared on the next gesture. */
	private String notice;

	@Inject
	EntourageRosterPanel(EntourageConfig config, ConfigWriter writer)
	{
		super(true);
		this.config = config;
		this.writer = writer;

		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout(0, 8));

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.addClearListener(this::redrawBody);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				redrawBody();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				redrawBody();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				redrawBody();
			}
		});

		north.setLayout(new BorderLayout());
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(north, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		redraw();
	}

	/** Opened from the toolbar: whatever was changed elsewhere is on screen by the time it is. */
	@Override
	public void onActivate()
	{
		redraw();
	}

	/**
	 * Something changed somewhere else — see {@link SidePanel#refresh()}. Called from the
	 * client thread, so the hop to Swing's happens here rather than at every call site.
	 */
	void refresh()
	{
		SwingUtilities.invokeLater(this::redraw);
	}

	// --- drawing -------------------------------------------------------------

	/**
	 * Rebuilds both halves from scratch.
	 *
	 * <p>Wholesale rather than incrementally, because this is a dozen rows and reconciling
	 * two lists of them is where a stale card comes from. The search box is a field rather
	 * than a fresh component for the one case where that matters: it is added and removed as
	 * the mode changes, and rebuilding it per keystroke would take the caret with it.
	 */
	private void redraw()
	{
		north.removeAll();
		if (picking != ROSTER)
		{
			north.add(search, BorderLayout.CENTER);
		}

		redrawBody();
	}

	private void redrawBody()
	{
		body.removeAll();

		if (picking == ROSTER)
		{
			drawRoster(RosterView.of(config));
		}
		else
		{
			drawPicker(RosterView.of(config));
		}

		revalidate();
		repaint();
	}

	/** Five cards, an add affordance, and the three dials people actually change. */
	private void drawRoster(RosterView view)
	{
		body.add(paragraph(HELPER_TEXT));
		body.add(gap(8));

		for (RosterView.Slot slot : view.getSlots())
		{
			body.add(slotCard(view, slot));
			body.add(gap(4));
		}

		if (view.canAdd())
		{
			body.add(addCard(view));
			body.add(gap(4));
		}

		body.add(gap(4));
		body.add(quickSettings(view));
	}

	/**
	 * One slot: who is in it, whether it is walking with you, and the two things you can do
	 * to it.
	 *
	 * <p>The whole card is the "change who is in this" affordance, because that is the
	 * question a card about a person is asking and a separate button for it would be a
	 * smaller target saying the same thing. The {@code ×} is the other verb, and it is only
	 * drawn where it means something.
	 */
	private JPanel slotCard(RosterView view, RosterView.Slot slot)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.BORDER_COLOR),
			new EmptyBorder(6, 8, 6, 8)));
		card.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel heading = new JLabel(slot.getHeading());
		heading.setFont(FontManager.getRunescapeSmallFont());
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(left(heading));

		JLabel title = new JLabel(slot.getTitle());
		title.setFont(FontManager.getRunescapeBoldFont());
		// Inactive is a real state, not an excuse to go unreadable: LIGHT_GRAY_COLOR still
		// reads as dimmer than an active slot's BRAND_ORANGE, and still clears body-text
		// contrast against the card, which MEDIUM_GRAY_COLOR never did.
		title.setForeground(slot.isActive()
			? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		text.add(left(title));

		// CENTER rather than WEST: BorderLayout gives a WEST child its full preferred width,
		// so a long subtitle — "Typed id, or Elite Black Knight if refused" — would push the
		// × off the end of a 225-pixel card. In CENTER it gets what is left; wrapped() below
		// is what keeps that from ellipsising away the half of the sentence that says what a
		// refused id falls back to.
		JLabel subtitle = wrapped(slot.getSubtitle(), CARD_TEXT_WIDTH);
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(left(subtitle));

		card.add(text, BorderLayout.CENTER);

		if (slot.isActive() && view.canRemove())
		{
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			actions.add(action("×", "Take this one out of the entourage",
				() -> apply(() -> RosterEdit.remove(writer, view, slot.getIndex()))));
			card.add(actions, BorderLayout.EAST);
		}

		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				pickFor(slot.getIndex());
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				paint(card, ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				paint(card, ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return sized(card);
	}

	/** The dashed one at the bottom of the list: one more figure, keeping the one it names. */
	private JPanel addCard(RosterView view)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARK_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createDashedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1f, 4f, 3f, false),
			new EmptyBorder(6, 8, 6, 8)));
		card.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel label = new JLabel("+  Add a follower");
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		card.add(label, BorderLayout.WEST);

		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				apply(() -> RosterEdit.add(writer, view));
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				paint(card, ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				paint(card, ColorScheme.DARK_GRAY_COLOR);
			}
		});

		return sized(card);
	}

	/**
	 * The three dials that get changed often, behind one orange heading that folds.
	 *
	 * <p>Three rather than fifteen. Everything else this plugin can do — the pose, the
	 * facing, the whole dialogue section — is set once and left, and a panel that duplicated
	 * the settings screen would be a second settings screen with a worse layout. These are
	 * the ones you reach for while looking at the entourage.
	 */
	private JPanel quickSettings(RosterView view)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		header.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel heading = new JLabel("Quick settings");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(heading, BorderLayout.WEST);

		JLabel fold = new JLabel(quickOpen ? "-" : "+");
		fold.setFont(FontManager.getDefaultBoldFont());
		fold.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(fold, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				quickOpen = !quickOpen;
				redrawBody();
			}
		});

		section.add(sized(header));

		if (!quickOpen)
		{
			return section;
		}

		section.add(pips("Followers", EntourageSettings.MIN_FOLLOWERS,
			EntourageSettings.MAX_FOLLOWERS, view.getFollowers(),
			value -> apply(() -> RosterEdit.setFollowers(writer, value))));

		section.add(formationRow(view));

		section.add(pips("Follow distance",
			EntourageSettings.MIN_FOLLOW_DISTANCE, EntourageSettings.MAX_FOLLOW_DISTANCE,
			view.getFollowDistance(),
			value -> apply(() -> RosterEdit.setFollowDistance(writer, value))));

		section.add(paragraph("Everything else — the pose, which way they look, what they say "
			+ "— is in the plugin's own settings."));

		return section;
	}

	/** A labelled row of little numbered buttons, the current one lit. */
	private JPanel pips(String label, int min, int max, int selected, IntAction onPick)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));

		JLabel name = new JLabel(label);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.CENTER);

		JPanel choices = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		choices.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (int value = min; value <= max; value++)
		{
			final int choice = value;
			final boolean lit = value == selected;

			JLabel pip = new JLabel(Integer.toString(value));
			pip.setFont(FontManager.getRunescapeSmallFont());
			// Unlit is deliberately dimmer than the lit choice, but still a real text
			// colour — MEDIUM_GRAY_COLOR here read as almost the same grey as the pip's own
			// unlit fill, which is a border colour standing in for body text.
			pip.setForeground(lit ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			pip.setOpaque(true);
			pip.setBackground(lit ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			pip.setBorder(new EmptyBorder(2, 6, 2, 6));
			pip.setCursor(new Cursor(Cursor.HAND_CURSOR));
			pip.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					onPick.run(choice);
				}
			});

			choices.add(pip);
		}

		row.add(choices, BorderLayout.EAST);
		return sized(row);
	}

	/** The formation, as a dropdown — seven shapes is too many to lay out as buttons. */
	private JPanel formationRow(RosterView view)
	{
		JPanel row = new JPanel(new BorderLayout(0, 2));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));

		JLabel name = new JLabel("Formation");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.NORTH);

		JComboBox<EntourageFormation> box = new JComboBox<>(EntourageFormation.values());
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(ColorScheme.TEXT_COLOR);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setFocusable(false);

		// Selected before the listener is attached, and it has to be: setSelectedItem fires
		// an ActionEvent, so a listener added first would write the config on every redraw —
		// including the redraw a write itself causes.
		box.setSelectedItem(view.getFormation());
		box.addActionListener(event ->
		{
			Object chosen = box.getSelectedItem();
			if (chosen instanceof EntourageFormation)
			{
				apply(() -> RosterEdit.setFormation(writer, (EntourageFormation) chosen));
			}
		});

		row.add(box, BorderLayout.CENTER);
		return sized(row);
	}

	// --- the picker ----------------------------------------------------------

	/** Search results for one slot, plus the typed-id card when the slot is the first. */
	private void drawPicker(RosterView view)
	{
		RosterView.Slot slot = view.getSlot(picking);

		body.add(backRow());
		body.add(gap(4));

		JLabel heading = new JLabel("Who walks in "
			+ slot.getHeading().toLowerCase(Locale.ROOT) + "?");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		body.add(sized(heading));
		body.add(gap(4));

		if (slot.getIndex() == 0)
		{
			body.add(customIdCard(view));
			body.add(gap(6));
		}

		List<EntourageFigure> found = FigureSearch.matching(search.getText());
		if (found.isEmpty())
		{
			body.add(paragraph(NO_MATCH));
			return;
		}

		for (EntourageFigure figure : found)
		{
			body.add(figureRow(view, figure, !slot.isCustom() && figure == slot.getFigure()));
			body.add(gap(2));
		}
	}

	private JPanel backRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel back = new JLabel("<  Back to the roster");
		back.setFont(FontManager.getRunescapeSmallFont());
		back.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(back, BorderLayout.WEST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				pickFor(ROSTER);
			}
		});

		return sized(row);
	}

	/** One figure, clickable. The one already in the slot says so rather than looking the same. */
	private JPanel figureRow(RosterView view, EntourageFigure figure, boolean current)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel name = new JLabel(figure.getDisplayName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(current ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		row.add(name, BorderLayout.CENTER);

		if (current)
		{
			JLabel mark = new JLabel("in this slot");
			mark.setFont(FontManager.getRunescapeSmallFont());
			mark.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			row.add(mark, BorderLayout.EAST);
		}

		final int slot = picking;
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				RosterEdit.assign(writer, view, slot, figure);
				pickFor(ROSTER);
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				paint(row, ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				paint(row, ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return sized(row);
	}

	/**
	 * The typed NPC id, as a card rather than as a bare number box.
	 *
	 * <p>It sits in the first slot's picker and nowhere else, which is exactly where the
	 * setting applies — see {@link EntourageConfig} on why there is one of these rather than
	 * five. A card because the number needs three sentences around it that a spinner in a
	 * settings list has nowhere to put: that an id which cannot walk is refused, that the
	 * figure above is what you get when it is, and that clearing the box gives the dropdown
	 * back.
	 */
	private JPanel customIdCard(RosterView view)
	{
		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.BORDER_COLOR),
			new EmptyBorder(6, 8, 6, 8)));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel title = new JLabel(view.isCustom()
			? "Wearing NPC " + view.getCustomNpcId() : "Any NPC, by id");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		top.add(title, BorderLayout.CENTER);

		if (view.isCustom())
		{
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			actions.add(action("×", "Give the first slot its figure back",
				() -> apply(() -> RosterEdit.clearCustomNpcId(writer))));
			top.add(actions, BorderLayout.EAST);
		}

		card.add(top, BorderLayout.NORTH);

		JTextField field = new JTextField(
			view.isCustom() ? Integer.toString(view.getCustomNpcId()) : "");
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setForeground(ColorScheme.TEXT_COLOR);
		field.setBackground(ColorScheme.DARK_GRAY_COLOR);
		field.setCaretColor(ColorScheme.TEXT_COLOR);
		field.setBorder(new EmptyBorder(4, 4, 4, 4));
		field.addActionListener(event -> typeId(field.getText()));

		JPanel entry = new JPanel(new BorderLayout(4, 0));
		entry.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		entry.add(field, BorderLayout.CENTER);
		entry.add(action("Use", "Put this NPC in the first slot", () -> typeId(field.getText())),
			BorderLayout.EAST);
		card.add(entry, BorderLayout.CENTER);

		card.add(muted(notice != null ? notice
			: "Any id that can stand and walk. One that cannot is refused, and the figure "
				+ "below comes back. Empty the box to use the figure instead."),
			BorderLayout.SOUTH);

		return sized(card);
	}

	/**
	 * @param typed whatever is in the id box. Blank clears the setting, because an empty box
	 *              is somebody saying they do not want a typed id — which is the same thing
	 *              zero means and is easier to do than remembering that.
	 */
	private void typeId(String typed)
	{
		String trimmed = typed == null ? "" : typed.trim();
		if (trimmed.isEmpty())
		{
			apply(() -> RosterEdit.clearCustomNpcId(writer));
			return;
		}

		int npcId;
		try
		{
			npcId = Integer.parseInt(trimmed);
		}
		catch (NumberFormatException notANumber)
		{
			// Said on the card rather than swallowed: a box that ignores what was typed is
			// the same box that ignores a correct id, from the outside.
			notice = NOT_A_NUMBER;
			redrawBody();
			return;
		}

		apply(() -> RosterEdit.setCustomNpcId(writer, npcId));
	}

	// --- plumbing ------------------------------------------------------------

	/** Runs a change and draws the profile back, so the panel can never show a stale card. */
	private void apply(Runnable change)
	{
		notice = null;
		change.run();
		redrawBody();
	}

	private void pickFor(int slot)
	{
		picking = slot;
		notice = null;

		if (slot == ROSTER)
		{
			redraw();
			return;
		}

		redraw();
		search.requestFocusInWindow();
	}

	/** A small orange-on-hover glyph or word, which is what a card's actions are made of. */
	private static JLabel action(String glyph, String tooltip, Runnable onPress)
	{
		JLabel label = new JLabel(glyph);
		// The client's own face rather than the game's: the RuneScape font is a bitmap face
		// and does not carry a multiplication sign.
		label.setFont(FontManager.getDefaultBoldFont().deriveFont(Font.BOLD, 12f));
		// Resting state is still a text colour someone can read without hovering — a "×"
		// that only exists at MEDIUM_GRAY_COLOR is a remove action nobody can see is there.
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(0, 6, 0, 2));
		label.setToolTipText(tooltip);
		label.setCursor(new Cursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				onPress.run();
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				label.setForeground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});

		return label;
	}

	/**
	 * Pins a label to the left edge of a {@code BoxLayout} column.
	 *
	 * <p>Not a nicety. {@code BoxLayout} on the Y axis positions each child by its
	 * {@code alignmentX}, and a {@code JLabel}'s default is <b>centre</b> — so a card whose
	 * widest line is its subtitle would draw "Slot 1" and the figure's name centred over it,
	 * which reads as a layout that was never looked at.
	 */
	private static JLabel left(JLabel label)
	{
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	/** Muted grey prose that wraps, which a plain label does not. */
	private JPanel paragraph(String text)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(muted(text), BorderLayout.CENTER);
		return sized(wrapper);
	}

	private static JLabel muted(String text)
	{
		JLabel label = wrapped(text, PANEL_WIDTH - 32);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	/**
	 * A label that wraps at {@code width} pixels instead of running off the sidebar.
	 *
	 * <p>A one-cell table rather than a styled div, and the difference is not cosmetic: a
	 * CSS width on a div or a body is honoured when the view is <i>painted</i> and ignored
	 * when its preferred size is <i>measured</i>, so the label asks for 266 pixels in a
	 * 225-pixel sidebar and every line runs off the right edge. A table cell's width
	 * participates in the measurement, which is the whole job. Checked by rendering all
	 * three offline.
	 *
	 * <p>Callers still choose their own font and colour — this only owns the wrap.
	 */
	private static JLabel wrapped(String text, int width)
	{
		return new JLabel("<html><table><tr><td width='" + width + "'>"
			+ text + "</td></tr></table></html>");
	}

	/** Vertical space, as a component, because BoxLayout has no gap of its own. */
	private static Component gap(int height)
	{
		return Box.createVerticalStrut(height);
	}

	/**
	 * Pins a row to its own preferred height.
	 *
	 * <p>{@code BoxLayout} hands out every pixel of slack to whatever will take it, and a
	 * {@code JPanel}'s maximum size is unbounded — so without this the last card in the
	 * column stretches to the bottom of the sidebar and the ones above it drift apart.
	 */
	private static <T extends JComponent> T sized(T component)
	{
		component.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		component.setAlignmentX(LEFT_ALIGNMENT);
		return component;
	}

	/** The row and its labels, so a hover covers the whole strip rather than its margins. */
	private static void paint(JPanel row, Color colour)
	{
		row.setBackground(colour);
		for (Component child : row.getComponents())
		{
			child.setBackground(colour);
			if (child instanceof JPanel)
			{
				paint((JPanel) child, colour);
			}
		}
	}

	/** What a numbered button does when it is pressed. */
	@FunctionalInterface
	private interface IntAction
	{
		void run(int value);
	}
}
