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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * {@link RosterEdit}, every name in the search list comes out of {@link FigureSearch} and
 * every favourites list comes out of {@link Favourites} — all four static, offline and
 * under test. What is left here is layout, colour and mouse handling, which is the part no
 * test can reach without a windowing system, and keeping it to that is deliberate. It is
 * the same split {@code ../gunnars-tools} draws between {@code MonsterLookupPanel} and
 * {@code MonsterIndex}.
 *
 * <h2>The config is the store, and there is no other one</h2>
 *
 * <p>This panel holds no roster of its own. Every control writes through
 * {@link ConfigWriter} into the ordinary keys and then reads the profile straight back to
 * redraw, so a change made here shows up in RuneLite's settings screen, a change made there
 * shows up here — {@link #refresh()} is how — and everything persists through RuneLite's
 * normal profile mechanism. The favourites are the same: a hidden config item, not a file
 * and not a second store. The only state this class keeps is which slot is being picked
 * for, whether the quick settings are folded open, and the NPC names it has been told —
 * none of which is a setting.
 *
 * <h2>The one thing it asks the client, and the two hops that make it legal</h2>
 *
 * <p>{@code PluginManager} calls {@code startUp()}, {@code shutDown()} and every listener
 * below from the event dispatch thread, and a client read off the client thread throws — an
 * {@code IllegalStateException} in a shipped client. So nothing here reads the client
 * <i>inline</i>: the figures are an enum, the settings are a config proxy, and both are safe
 * from any thread.
 *
 * <p>The favourites list changed what that costs. A favourite is an integer — see
 * {@link Favourites} — and a list of integers is not a thing anybody recognises, so a name
 * has to come from somewhere. It comes through {@link NpcNames}, which reads on the client
 * thread and calls back on this one; the names land in {@link #names}, which is touched only
 * from the event dispatch thread, and a redraw happens only if something new arrived. Until
 * one does, every row draws the bare id, which is true. Nothing here blocks, nothing here
 * spins, and nothing here starts a thread.
 *
 * <h2>Colour and size</h2>
 *
 * <p>Every colour is {@link ColorScheme}'s, so the panel is the same panel when RuneLite's
 * theme changes and looks like the rest of the client rather than like a plugin.
 *
 * <p><b>Body text is {@link FontManager#getRunescapeFont()}, not the small face.</b> The
 * panel was built on {@code getRunescapeSmallFont()} throughout and read as fine print in a
 * sidebar full of ordinary-sized client text. Headings, prose, row labels and the favourites
 * are the normal face now; what stayed small is the handful of labels that are subordinate
 * to something beside them — the "in this slot" mark next to a figure's name — because the
 * point was to raise the floor, not to flatten three sizes into one. The glyph actions keep
 * the client's own face rather than the game's, because the RuneScape font is a bitmap face
 * and carries neither {@code ×} nor {@code ★}.
 *
 * <p><b>Bigger glyphs in the same box is how a wrapped line starts truncating again</b>, so
 * the wrap widths below are derived from the space that actually exists rather than tuned by
 * eye, and {@code TruncationGuardTest} lays the whole panel out at
 * {@link PluginPanel#PANEL_WIDTH} and fails if any label asks for more room than it is
 * given.
 */
class EntourageRosterPanel extends PluginPanel
{
	/** Not picking for any slot: the roster is what is on screen. */
	private static final int ROSTER = -1;

	/**
	 * This panel's own border, per side.
	 *
	 * <p>Written as a constant because three widths below are measured from it, and a border
	 * changed in the constructor without them is a subtitle that quietly starts ellipsising.
	 */
	private static final int PANEL_BORDER = 8;

	/**
	 * What a slot card's own frame eats, per side: a 1-pixel matte plus 8 pixels of padding.
	 */
	private static final int CARD_BORDER = 9;

	/**
	 * How wide a column a glyph action takes: 22 pixels.
	 *
	 * <p>Measured rather than guessed — a {@code ×} or a {@code ★} in the client's default
	 * bold face at {@link #ACTION_FONT_SIZE}, inside {@code EmptyBorder(0, 6, 0, 2)}, asks
	 * for 20 or 21. Two pixels of headroom on top so that a font substitution on somebody
	 * else's machine has somewhere to go.
	 */
	private static final int ACTION_WIDTH = 22;

	/**
	 * Slack held back from every wrap width: 4 pixels.
	 *
	 * <p>A wrapped label asks for exactly the width its cell was given — see {@link #wrapped}
	 * — so without this the arithmetic below would have to be exactly right rather than
	 * merely right, and a one-pixel disagreement with a layout manager would be an
	 * ellipsis.
	 */
	private static final int SLACK = 4;

	/** The widest a wrapped line sitting directly on the panel may be. */
	private static final int PANEL_TEXT_WIDTH = PANEL_WIDTH - (2 * PANEL_BORDER) - SLACK;

	/** The widest a wrapped line spanning the full width of a card may be. */
	private static final int CARD_PROSE_WIDTH = PANEL_TEXT_WIDTH - (2 * CARD_BORDER);

	/**
	 * The widest a label on a plain list row may be — a figure, or a favourite.
	 *
	 * <p>Those rows have a 6-pixel padding either side instead of a card's border, and one
	 * action column.
	 */
	private static final int ROW_TEXT_WIDTH = PANEL_TEXT_WIDTH - 12 - ACTION_WIDTH;

	/**
	 * How wide a wrapped line inside a slot card is allowed to be.
	 *
	 * <p>{@link #CARD_PROSE_WIDTH} less the column a {@code ×} takes. Sized for that worst
	 * case rather than measured per-card, so a subtitle never has to ask whether its card
	 * happens to have an action on it today — see {@link #slotCard}.
	 *
	 * <p>It was a bare {@code 150} when everything on the card was the small font, which was
	 * both a guess and 15 pixels of the card thrown away. It is now the space that is
	 * actually there, which is what makes the bigger face fit.
	 */
	private static final int CARD_TEXT_WIDTH = CARD_PROSE_WIDTH - ACTION_WIDTH;

	/**
	 * The point size of a glyph action.
	 *
	 * <p>Up from 12 with the rest of the panel. A {@code ×} is a click target as well as a
	 * mark, and it was the smallest one here.
	 */
	private static final float ACTION_FONT_SIZE = 14f;

	/**
	 * What the panel is for, in the register the rest of the plugin's documentation uses.
	 *
	 * <p>It says the two things somebody has to know that are not visible from the cards:
	 * that a slot past the count keeps its figure rather than losing it, and that any slot
	 * will take an NPC id. The second half used to say "Slot 1", because it was true; it is
	 * the sentence a feature makes obsolete rather than a sentence that was wrong.
	 */
	private static final String HELPER_TEXT =
		"Five slots, as many of them walking with you as the count allows. Press a card to "
			+ "change who is in it; press the &times; to take one out and move the rest up. A "
			+ "greyed slot keeps its figure — the count decides who walks, not who is set. "
			+ "Any slot will wear an NPC id you type, and you can star the ones you like.";

	/** The muted line under the search when a query matches nothing. */
	private static final String NO_MATCH = "Nothing here answers to that.";

	/** What the id box says when what was typed is not a number. */
	private static final String NOT_A_NUMBER = "That is not an id. Type a number, like 4931.";

	/** What the favourites list says before there is anything on it. */
	private static final String NO_FAVOURITES =
		"Nothing starred yet. Type an id, press Use, and press the &#9734; if you like what "
			+ "turns up.";

	/** What the star says when the list is full and this id is not on it. */
	private static final String FAVOURITES_FULL =
		"The favourites list is full at " + Favourites.MAX + ". Remove one to star another.";

	private final EntourageConfig config;
	private final ConfigWriter writer;
	private final NpcNames npcNames;

	private final IconTextField search = new IconTextField();
	private final JPanel north = new JPanel();
	private final JPanel body = new JPanel();

	/**
	 * NPC ids that have been resolved to a name.
	 *
	 * <p><b>Event dispatch thread only.</b> Written from {@link #acceptNames}, which
	 * {@link NpcNames} promises to call on this thread, and read from the draw methods, which
	 * Swing calls on it. Nothing else touches it, which is what makes a plain
	 * {@link HashMap} the right container rather than a concurrent one — a lock here would be
	 * a lock protecting against a caller that does not exist.
	 */
	private final Map<Integer, String> names = new HashMap<>();

	/**
	 * NPC ids already asked about, whether or not an answer came back.
	 *
	 * <p>Without this the panel would ask again on every redraw for every id the cache cannot
	 * answer for — which at the login screen is all of them — and each answer would prompt
	 * another redraw. {@link #retryUnresolvedNames()} is where an id that failed gets a second
	 * chance, and it is deliberately tied to a user gesture rather than to a timer.
	 */
	private final Set<Integer> asked = new HashSet<>();

	/** Which slot the picker is choosing for, or {@link #ROSTER}. */
	private int picking = ROSTER;

	/** Whether the quick settings are folded open. Open to begin with: they are the point. */
	private boolean quickOpen = true;

	/** Set when a typed id would not parse, or a star would not fit; cleared on the next gesture. */
	private String notice;

	@Inject
	EntourageRosterPanel(EntourageConfig config, ConfigWriter writer, NpcNames npcNames)
	{
		super(true);
		this.config = config;
		this.writer = writer;
		this.npcNames = npcNames;

		setBorder(new EmptyBorder(PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, PANEL_BORDER));
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
		// A name that could not be resolved last time — because nobody was logged in, or the
		// cache was cold — gets another go now. Opening the panel is a gesture, so this cannot
		// become a poll.
		retryUnresolvedNames();
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

	/**
	 * <p><b>The name lookup is fired last, after the tree is built and revalidated, and that
	 * ordering is load-bearing.</b> A resolved name causes another redraw — that is the whole
	 * point of it — so anything this method did <i>after</i> asking would be running against a
	 * body the nested redraw had already emptied and refilled. Firing from here rather than
	 * from inside the draw methods means the hazard cannot be reintroduced by adding a line to
	 * one of them, which is why they hand the ids back instead of asking for themselves.
	 */
	private void redrawBody()
	{
		body.removeAll();

		RosterView view = RosterView.of(config);
		List<Integer> wanted = picking == ROSTER ? drawRoster(view) : drawPicker(view);

		revalidate();
		repaint();

		requestNames(wanted);
	}

	/**
	 * Five cards, an add affordance, and the dials people actually change.
	 *
	 * @return the NPC ids on screen that would read better as names
	 */
	private List<Integer> drawRoster(RosterView view)
	{
		body.add(paragraph(HELPER_TEXT));
		body.add(gap(8));

		List<Integer> wanted = new ArrayList<>();
		for (RosterView.Slot slot : view.getSlots())
		{
			body.add(slotCard(view, slot));
			body.add(gap(4));

			if (slot.isCustom())
			{
				wanted.add(slot.getCustomNpcId());
			}
		}

		if (view.canAdd())
		{
			body.add(addCard(view));
			body.add(gap(4));
		}

		body.add(gap(4));
		body.add(quickSettings(view));

		return wanted;
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
		heading.setFont(FontManager.getRunescapeFont());
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(left(heading));

		// The name when the client has told us one, and the id until then — see nameFor. The
		// offline answer in RosterView.Slot#getTitle is still what a card falls back to, and
		// is still the only thing any of this is *tested* against without a client.
		//
		// Wrapped, unlike every other heading on this panel, and for a reason none of them
		// have: this is the one line here whose length the plugin does not control. A figure's
		// display name is bounded by an enum somebody wrote; an NPC name comes out of the game
		// cache and can be anything.
		JLabel title = wrapped(titleFor(slot), CARD_TEXT_WIDTH);
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
		subtitle.setFont(FontManager.getRunescapeFont());
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
	 * The dials that get changed often, behind one orange heading that folds.
	 *
	 * <p>A short list rather than fifteen. Most of what this plugin can do — the pose, the
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
		fold.setFont(FontManager.getDefaultBoldFont().deriveFont(Font.BOLD, ACTION_FONT_SIZE));
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

		// First, and above the roster dials, because it is the one control here that gets
		// pressed in the middle of something — you park the group, you fight, you unpark
		// them. The others are set while you are looking at the entourage rather than at a
		// boss.
		section.add(stayPutRow(view));

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

	/**
	 * Follow me, or stay put — the two states, both drawn, the one in force lit.
	 *
	 * <p><b>Two chips rather than a checkbox, and the reason is what this control is for.</b>
	 * It gets pressed mid-fight, at a boss, by somebody who is not reading it carefully; a
	 * checkbox says what would happen if you clicked it and leaves you to infer the state,
	 * while two lit-or-unlit words say which one you are in. Both labels are on screen at
	 * once, so there is nothing to remember and nothing to toggle by accident and then have
	 * to work out.
	 *
	 * <p>The label sits above the chips rather than beside them, the way
	 * {@link #formationRow} does: "Movement" and two words do not fit on one 209-pixel line
	 * at the normal font, and the alternative to stacking them is abbreviating one of the
	 * two states nobody should have to guess at.
	 */
	private JPanel stayPutRow(RosterView view)
	{
		JPanel row = new JPanel(new BorderLayout(0, 2));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));

		JLabel name = new JLabel("Movement");
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.NORTH);

		JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		choices.setBackground(ColorScheme.DARK_GRAY_COLOR);
		choices.add(chip("Follow me", !view.isStayPut(),
			"They walk to their formation and are recalled if they fall too far behind",
			() -> apply(() -> RosterEdit.setStayPut(writer, false))));
		choices.add(chip("Stay put", view.isStayPut(),
			"They hold the tiles they are on, and are never recalled to you however far you go",
			() -> apply(() -> RosterEdit.setStayPut(writer, true))));
		row.add(choices, BorderLayout.CENTER);

		return sized(row);
	}

	/**
	 * One word-sized button that is either lit or not.
	 *
	 * <p>The same two colours and the same two fills a quick-settings pip uses — see
	 * {@link #pips} — so the two controls read as the same kind of thing, and so a new chip
	 * gets {@code ContrastGuardTest}'s UI-component bar rather than the paragraph one for the
	 * same structural reason: its state is carried in the fill as well as the glyph.
	 */
	private static JLabel chip(String text, boolean lit, String tooltip, Runnable onPress)
	{
		JLabel chip = new JLabel(text);
		chip.setFont(FontManager.getRunescapeFont());
		chip.setForeground(lit ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		chip.setOpaque(true);
		chip.setBackground(lit ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		chip.setBorder(new EmptyBorder(2, 6, 2, 6));
		chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
		chip.setToolTipText(tooltip);
		chip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				onPress.run();
			}
		});

		return chip;
	}

	/** A labelled row of little numbered buttons, the current one lit. */
	private JPanel pips(String label, int min, int max, int selected, IntAction onPick)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));

		JLabel name = new JLabel(label);
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.CENTER);

		JPanel choices = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		choices.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (int value = min; value <= max; value++)
		{
			final int choice = value;
			final boolean lit = value == selected;

			JLabel pip = new JLabel(Integer.toString(value));
			pip.setFont(FontManager.getRunescapeFont());
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
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name, BorderLayout.NORTH);

		JComboBox<EntourageFormation> box = new JComboBox<>(EntourageFormation.values());
		box.setFont(FontManager.getRunescapeFont());
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

	/**
	 * Search results for one slot, plus the typed-id card and the favourites.
	 *
	 * @return the NPC ids on screen that would read better as names
	 */
	private List<Integer> drawPicker(RosterView view)
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

		// Offered for every slot now, where once it was the first slot only. The card is the
		// setting's whole interface, so it belongs wherever the setting applies.
		body.add(customIdCard(view, slot));
		body.add(gap(6));

		body.add(favourites(view, slot));
		body.add(gap(6));

		List<EntourageFigure> found = FigureSearch.matching(search.getText());
		if (found.isEmpty())
		{
			body.add(paragraph(NO_MATCH));
		}
		else
		{
			for (EntourageFigure figure : found)
			{
				body.add(figureRow(view, figure, !slot.isCustom() && figure == slot.getFigure()));
				body.add(gap(2));
			}
		}

		List<Integer> wanted = new ArrayList<>(view.getFavourites());
		if (slot.isCustom())
		{
			wanted.add(slot.getCustomNpcId());
		}
		return wanted;
	}

	private JPanel backRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setCursor(new Cursor(Cursor.HAND_CURSOR));

		JLabel back = new JLabel("<  Back to the roster");
		back.setFont(FontManager.getRunescapeFont());
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
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(current ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		row.add(name, BorderLayout.CENTER);

		if (current)
		{
			JLabel mark = new JLabel("in this slot");
			// Left at the small face on purpose. It is an annotation on the name beside it
			// rather than a line of its own, and it is the one label on this row that has to
			// share a 209-pixel strip with "Elite Black Knight".
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
	 * <p>A card because the number needs three sentences around it that a spinner in a
	 * settings list has nowhere to put: that an id which cannot walk is refused, that the
	 * figure below is what you get when it is, and that clearing the box gives the dropdown
	 * back.
	 *
	 * <p>It used to sit in the first slot's picker and nowhere else, because that was the
	 * only slot the setting applied to. There are five keys now, so there are five cards —
	 * one per picker, each writing its own slot.
	 */
	private JPanel customIdCard(RosterView view, RosterView.Slot slot)
	{
		final int index = slot.getIndex();

		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.BORDER_COLOR),
			new EmptyBorder(6, 8, 6, 8)));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Two action columns when an id is in force — the star and the × — and none when
		// there is not one, which is also the only case where the heading is a string this
		// file wrote and therefore the only case where its width was ever knowable.
		JLabel title = wrapped(slot.isCustom()
				? "Wearing " + nameFor(slot.getCustomNpcId()) : "Any NPC, by id",
			slot.isCustom() ? CARD_PROSE_WIDTH - (2 * ACTION_WIDTH) : CARD_PROSE_WIDTH);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		top.add(title, BorderLayout.CENTER);

		if (slot.isCustom())
		{
			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			actions.add(starAction(view, slot.getCustomNpcId()));
			actions.add(action("×", "Give this slot its figure back",
				() -> apply(() -> RosterEdit.clearCustomNpcId(writer, index))));
			top.add(actions, BorderLayout.EAST);
		}

		card.add(top, BorderLayout.NORTH);

		JTextField field = new JTextField(
			slot.isCustom() ? Integer.toString(slot.getCustomNpcId()) : "");
		field.setFont(FontManager.getRunescapeFont());
		field.setForeground(ColorScheme.TEXT_COLOR);
		field.setBackground(ColorScheme.DARK_GRAY_COLOR);
		field.setCaretColor(ColorScheme.TEXT_COLOR);
		field.setBorder(new EmptyBorder(4, 4, 4, 4));
		field.addActionListener(event -> typeId(index, field.getText()));

		JPanel entry = new JPanel(new BorderLayout(4, 0));
		entry.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		entry.add(field, BorderLayout.CENTER);
		entry.add(action("Use", "Put this NPC in this slot", () -> typeId(index, field.getText())),
			BorderLayout.EAST);
		card.add(entry, BorderLayout.CENTER);

		card.add(muted(notice != null ? notice
			: "Any id that can stand and walk. One that cannot is refused, and the figure "
				+ "below comes back. Empty the box to use the figure instead.", CARD_PROSE_WIDTH),
			BorderLayout.SOUTH);

		return sized(card);
	}

	/**
	 * The star on the typed-id card: keep this id, or stop keeping it.
	 *
	 * <p>A filled star when the id is on the list and a hollow one when it is not, which is
	 * the same two-state convention every other star in every other piece of software uses
	 * and needs no legend. Both are drawn in the client's own default face for the reason the
	 * {@code ×} is: the RuneScape font is a bitmap face and carries neither glyph.
	 *
	 * <p>Starring at the cap is refused out loud rather than silently dropping the oldest
	 * favourite. {@link Favourites#with} would drop it — it has to, something must give — but
	 * a control that quietly discards something you saved is worse than one that says it is
	 * full.
	 */
	private JLabel starAction(RosterView view, int npcId)
	{
		if (view.isFavourite(npcId))
		{
			return action("★", "Stop keeping this id in your favourites",
				() -> apply(() -> RosterEdit.unfavourite(writer, view, npcId)),
				ColorScheme.BRAND_ORANGE);
		}

		if (!view.canFavourite())
		{
			return action("☆", FAVOURITES_FULL, () ->
			{
				notice = FAVOURITES_FULL;
				redrawBody();
			});
		}

		return action("☆", "Keep this id in your favourites", () ->
			apply(() -> RosterEdit.favourite(writer, view, npcId)));
	}

	/**
	 * The starred ids, as rows you can apply to the slot you are picking for.
	 *
	 * <p><b>The list is drawn from ids and the names are painted in as they arrive.</b>
	 * {@link RosterView} cannot know what 3598 is called and this panel cannot ask inline —
	 * see the class javadoc — so a row that has not been told yet says "NPC 3598", which is
	 * both true and the number you would type. It is never blank and never "null".
	 */
	private JPanel favourites(RosterView view, RosterView.Slot slot)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel heading = new JLabel("Favourites");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		section.add(sized(left(heading)));
		section.add(gap(4));

		if (view.getFavourites().isEmpty())
		{
			section.add(paragraph(NO_FAVOURITES));
			return sized(section);
		}

		for (int npcId : view.getFavourites())
		{
			section.add(favouriteRow(view, slot, npcId));
			section.add(gap(2));
		}

		// Sized like every other row: this one sits in the middle of the picker's column
		// rather than at the end of it, so a BoxLayout with slack to hand out would stretch
		// it and push the figure list off the bottom.
		return sized(section);
	}

	/** One starred id: press it to wear it here, press the × to stop keeping it. */
	private JPanel favouriteRow(RosterView view, RosterView.Slot slot, int npcId)
	{
		final int index = slot.getIndex();
		final boolean worn = slot.isCustom() && slot.getCustomNpcId() == npcId;

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setCursor(new Cursor(Cursor.HAND_CURSOR));
		// The id lives in a tooltip rather than on the row, because the row is 209 pixels wide
		// and a name plus an id plus a × does not fit at any font this panel is willing to use.
		//
		// On the row and not on the label inside it, and that is not arbitrary:
		// setToolTipText registers the component with ToolTipManager, which installs itself as
		// a MouseListener. A label carrying a tooltip therefore *looks* pressable to anything
		// that finds a click target by asking which components have listeners — which is how
		// this panel's own tests find one, and how a future accessibility or focus helper would
		// too. The pressable thing here is the row; the tooltip goes on the same component so
		// the two answers agree.
		row.setToolTipText("NPC " + npcId);

		// Wrapped for the reason the slot card's title is: a name out of the game cache is not
		// a string this plugin chose the length of.
		JLabel name = wrapped(nameFor(npcId), ROW_TEXT_WIDTH);
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(worn ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		row.add(name, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		actions.add(action("×", "Stop keeping this id in your favourites",
			() -> apply(() -> RosterEdit.unfavourite(writer, view, npcId))));
		row.add(actions, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				apply(() -> RosterEdit.setCustomNpcId(writer, index, npcId));
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
	 * @param index which slot the box belongs to, 0-based
	 * @param typed whatever is in the id box. Blank clears the setting, because an empty box
	 *              is somebody saying they do not want a typed id — which is the same thing
	 *              zero means and is easier to do than remembering that.
	 */
	private void typeId(int index, String typed)
	{
		String trimmed = typed == null ? "" : typed.trim();
		if (trimmed.isEmpty())
		{
			apply(() -> RosterEdit.clearCustomNpcId(writer, index));
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

		apply(() -> RosterEdit.setCustomNpcId(writer, index, npcId));
	}

	// --- names ---------------------------------------------------------------

	/**
	 * @param npcId the id to describe
	 * @return the NPC's own name if the client has told us one, and {@code "NPC 3598"} until
	 * it does. Never blank, never {@code "null"} — an id whose name never arrives reads as
	 * the number, which is what the user typed and what they would type again.
	 *
	 * <p><b>"Told us one" still has to be checked, not just asked for.</b> A resolved name
	 * can be unusable two ways: {@link #sanitizeCacheName} strips every {@code <...>} span,
	 * so a name that is nothing but markup sanitises to the empty string, and the cache
	 * names an NPC composition with none as the literal four characters {@code "null"}
	 * rather than a Java {@code null}. {@link NpcNames#isRealName} is the one place both
	 * checks live — see {@link Follower#getDisplayName()} for the other caller that needs
	 * the same answer — so falling back to the id here is one branch rather than a second
	 * copy of either check.
	 */
	private String nameFor(int npcId)
	{
		String name = names.get(npcId);
		if (name == null)
		{
			return "NPC " + npcId;
		}

		String sanitized = sanitizeCacheName(name);
		return NpcNames.isRealName(sanitized) ? sanitized : "NPC " + npcId;
	}

	/**
	 * The longest unbroken run of non-space characters this panel will draw out of an
	 * {@code NPCComposition} name before cutting the rest of that run off with a {@code …}.
	 *
	 * <p><b>Measured against the tightest column this panel has, not guessed — and, this
	 * time, measured per glyph rather than against {@code 'A'} alone.</b> The typed id card
	 * draws its title as {@code "Wearing " + nameFor(...)} beside both the star and the
	 * {@code ×}, which leaves less room than any other caller of {@link #nameFor} — see
	 * {@link #customIdCard}. A first pass measured only {@code 'A'}, which is also the most
	 * forgiving glyph this panel can be handed: swept through
	 * {@code TruncationGuardTest}'s real Swing harness at that column, the widest run that
	 * still fits is {@code 'A'} 20, {@code 'm'}/{@code '_'} 15, {@code '&'}/{@code 'M'} 13,
	 * {@code 'W'}/{@code '%'} 12, and {@code '@'}/{@code '#'} 10 — so a limit tuned against
	 * {@code 'A'} alone left every narrower glyph free to overflow at any width between its
	 * own floor and twenty. Ten is the floor across every glyph probed, including
	 * {@code '&'}: a run of ten still fits once escaped to five-characters-wide
	 * {@code &amp;} apiece, where fifteen did not — see
	 * {@code TruncationGuardTest.everyProbedGlyphFitsARunOfTheCapInTheTightestColumn}.
	 *
	 * <p><b>A run, not the whole name, and the difference is the entire point.</b> The shape
	 * that overflows is a single token with no space in it: a fixed-width {@code <td>} draws
	 * one of those on one line however long that line has to be. A multi-word name is not
	 * that shape — {@link #wrapped} breaks it at its own spaces and it fits — so measuring the
	 * whole string would cut names that were never a problem. It is not a hypothetical
	 * difference: {@code General Graardor} is sixteen characters, {@code Dagannoth Supreme}
	 * seventeen and {@code Elite Black Knight Captain} twenty-six, and a flat cap of this size
	 * renders the first as {@code General Graardo…} — in a panel whose reason for existing is
	 * standing an entourage next to a boss.
	 *
	 * <p><b>What ten costs, honestly.</b> Every boss-name token this plugin is likely to be
	 * typed in against is still under it — {@code Tsutsaroth} is ten characters,
	 * {@code Commander}, {@code Dagannoth} and {@code Corporeal} are nine, {@code Graardor}
	 * is eight — but {@code Thermonuclear smoke devil} is not: its first word is thirteen
	 * characters, and it now renders as {@code Thermonucl… smoke devil}. Twenty would have
	 * let that one through whole, and would also have let a run of eleven to nineteen of
	 * {@code @}, {@code #}, {@code W}, {@code %}, {@code &} or {@code M} overflow the
	 * column — the trade this repo is taking on purpose, since an occasional truncated
	 * first word is a smaller failure than a card that ellipsises the fallback sentence it
	 * exists to show.
	 */
	private static final int MAX_CACHE_NAME_RUN = 10;

	/**
	 * A ceiling on the whole name after the runs are cut, so that a name made of hundreds of
	 * short words cannot grow the card downwards without limit. Wrapping means such a name
	 * costs height rather than width, which is why this is far looser than
	 * {@link #MAX_CACHE_NAME_RUN} and why no real name comes close to it.
	 */
	private static final int MAX_CACHE_NAME_LENGTH = 60;

	/**
	 * Makes an {@code NPCComposition} name safe to hand to {@link #wrapped} — the one string
	 * on this panel whose content the plugin does not choose, since {@link #nameFor} is its
	 * only source and that reads straight off the game cache.
	 *
	 * <p><b>Tag markup is removed, not escaped.</b> A real in-game name can carry one — the
	 * client marks up some NPCs' names with a colour tag, {@code <col=00ffff>Gummy</col>} —
	 * and the game renders that as plain "Gummy", never as literal angle brackets. Escaping
	 * it here would show the user markup nobody typed and the game never shows; stripping is
	 * what the name already looks like on screen. And a stripped {@code <} cannot go on to
	 * close {@link #wrapped}'s own {@code <table>} early, which a name ending
	 * {@code </td></tr></table>} would otherwise do the moment it reached a label that
	 * assembles its HTML by concatenation. Every bracketed span is removed the same way,
	 * recognised or not — {@link #nameFor}'s caller has no way to tell a colour tag from one
	 * this method has never seen, so refusing to guess and cutting the whole span is the only
	 * answer that is safe for both.
	 *
	 * <p><b>{@code &} is escaped, not stripped</b> — unlike a tag, it is not markup an NPC's
	 * name would never really contain. An NPC actually named with an ampersand should still
	 * show one, and {@code &amp;} is how a {@code JLabel}'s HTML renderer is told to draw a
	 * literal ampersand rather than starting an entity reference it does not recognise.
	 *
	 * <p><b>Each unbroken run is capped, not the name as a whole</b> — see
	 * {@link #MAX_CACHE_NAME_RUN} for why the difference is the whole of it, and
	 * {@link #MAX_CACHE_NAME_LENGTH} for the far looser ceiling that stops a name made of
	 * hundreds of short words from growing the card downwards forever.
	 *
	 * <p><b>The cutting happens before the escaping, deliberately.</b> {@code &} becomes five
	 * characters when it is escaped and stays one character wide when it is drawn, so a cap
	 * applied afterwards would be measuring the markup instead of the name and would cut a
	 * name with an ampersand in it four characters early per ampersand.
	 *
	 * @param name the name straight off {@code NPCComposition.getName()}
	 * @return the same name with every {@code <...>} span removed, every run longer than
	 * {@link #MAX_CACHE_NAME_RUN} cut with a trailing {@code …}, the whole capped at
	 * {@link #MAX_CACHE_NAME_LENGTH}, and {@code &} escaped last
	 */
	// Package-private rather than private: this is offline, deterministic string logic with
	// nothing Swing about it, and RosterEdit's own javadoc gives the reason to pull that kind
	// of thing out where a test can call it directly rather than through a rendered label.
	static String sanitizeCacheName(String name)
	{
		StringBuilder cleaned = new StringBuilder(name.length());
		boolean insideTag = false;
		int run = 0;
		for (int index = 0; index < name.length(); index++)
		{
			char c = name.charAt(index);
			if (c == '<')
			{
				insideTag = true;
				continue;
			}
			if (c == '>')
			{
				insideTag = false;
				continue;
			}
			if (insideTag)
			{
				continue;
			}

			if (Character.isWhitespace(c))
			{
				// A space both ends the run and is where wrapped() is able to break the
				// line, which is the same fact stated twice.
				run = 0;
				cleaned.append(c);
				continue;
			}

			run++;
			if (run <= MAX_CACHE_NAME_RUN)
			{
				cleaned.append(c);
			}
			else if (run == MAX_CACHE_NAME_RUN + 1)
			{
				// One ellipsis for the run, then the rest of it is dropped until a space
				// starts a new one — rather than an ellipsis per character.
				cleaned.append('…');
			}
		}

		String cut = cleaned.length() <= MAX_CACHE_NAME_LENGTH
			? cleaned.toString()
			: cleaned.substring(0, MAX_CACHE_NAME_LENGTH) + "…";

		// Last, so that every cap above counted drawn characters rather than markup.
		return cut.replace("&", "&amp;");
	}

	/**
	 * @return what a slot card's big line says: the resolved name when the slot wears a typed
	 * id we have a name for, and {@link RosterView.Slot#getTitle()} otherwise. The offline
	 * answer stays the fallback rather than being replaced, because it is the one every test
	 * without a client asserts against and the one that is right when nothing resolves.
	 */
	private String titleFor(RosterView.Slot slot)
	{
		return slot.isCustom() ? nameFor(slot.getCustomNpcId()) : slot.getTitle();
	}

	/**
	 * Asks {@link NpcNames} about any of these ids we have not asked about before.
	 *
	 * <p><b>Once per id, not once per redraw.</b> Every answer that carries a new name causes
	 * a redraw, and a redraw asks again — so without {@link #asked} this would be a loop, and
	 * at the login screen, where nothing resolves, it would be a loop that never even
	 * converges. An id that failed is re-asked only on a gesture; see
	 * {@link #retryUnresolvedNames()}.
	 */
	private void requestNames(List<Integer> npcIds)
	{
		List<Integer> wanted = new ArrayList<>();
		for (int npcId : npcIds)
		{
			if (asked.add(npcId))
			{
				wanted.add(npcId);
			}
		}

		if (!wanted.isEmpty())
		{
			npcNames.resolve(wanted, this::acceptNames);
		}
	}

	/**
	 * Names came back — see {@link NpcNames}, which promises this runs on the event dispatch
	 * thread.
	 *
	 * <p><b>Redraws only when something actually changed.</b> An answer that told us nothing
	 * new must not cost a rebuild of the whole body, both because it is wasted work and
	 * because a redraw is what asks the next question.
	 */
	private void acceptNames(Map<Integer, String> resolved)
	{
		boolean changed = false;
		for (Map.Entry<Integer, String> entry : resolved.entrySet())
		{
			changed |= !entry.getValue().equals(names.put(entry.getKey(), entry.getValue()));
		}

		if (changed)
		{
			redrawBody();
		}
	}

	/**
	 * Lets every id we could not name be asked about again.
	 *
	 * <p>The ones already answered stay answered — a name does not change — so this only ever
	 * costs a second attempt at the ones that failed, which is what the login screen and a
	 * cold cache produce.
	 */
	private void retryUnresolvedNames()
	{
		asked.retainAll(names.keySet());
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

		// Opening a picker is the gesture that draws the favourites, so it is also where an
		// id whose name would not resolve last time gets another attempt.
		retryUnresolvedNames();
		redraw();
		search.requestFocusInWindow();
	}

	/** A small orange-on-hover glyph or word, which is what a card's actions are made of. */
	private static JLabel action(String glyph, String tooltip, Runnable onPress)
	{
		return action(glyph, tooltip, onPress, ColorScheme.LIGHT_GRAY_COLOR);
	}

	/**
	 * @param resting the colour it sits at when nothing is hovering it. Orange for an action
	 *                that is already <i>on</i> — a filled star — because that is state rather
	 *                than emphasis, and grey for everything else. Still a real text colour
	 *                either way: a "×" that only exists at {@code MEDIUM_GRAY_COLOR} is a
	 *                remove action nobody can see is there.
	 */
	private static JLabel action(String glyph, String tooltip, Runnable onPress, Color resting)
	{
		JLabel label = new JLabel(glyph);
		// The client's own face rather than the game's: the RuneScape font is a bitmap face
		// and carries neither a multiplication sign nor a star.
		label.setFont(FontManager.getDefaultBoldFont().deriveFont(Font.BOLD, ACTION_FONT_SIZE));
		label.setForeground(resting);
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
				label.setForeground(resting);
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
		wrapper.add(muted(text, PANEL_TEXT_WIDTH), BorderLayout.CENTER);
		return sized(wrapper);
	}

	/**
	 * @param width how much room the caller actually has. Passed in rather than assumed,
	 *              because the same prose is drawn both directly on the panel and inside a
	 *              card whose border has already taken 18 pixels — one constant for both was
	 *              wrong for one of them.
	 */
	private static JLabel muted(String text, int width)
	{
		JLabel label = wrapped(text, width);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	/**
	 * A label that wraps at {@code width} pixels instead of running off the sidebar.
	 *
	 * <p>A one-cell table rather than a styled div, and the difference is not cosmetic: a
	 * CSS width on a div or a body is honoured when the view is <i>painted</i> and ignored
	 * when its preferred size is <i>measured</i>, so the label asks for 195 pixels whatever
	 * you set and every line runs off the right edge. A table cell's width participates in
	 * the measurement, which is the whole job. Checked by rendering all three offline.
	 *
	 * <p><b>The table is explicitly zeroed out</b>, and that is worth the four attributes:
	 * Swing's default table carries cellspacing, so {@code <table>} on its own asks for
	 * {@code width + 4} and every caller had to know to subtract a number nothing wrote down.
	 * Zeroed, a wrapped label asks for exactly the width it was given, which is what lets
	 * {@code TruncationGuardTest} compare the two numbers and mean it.
	 *
	 * <p>Callers still choose their own font and colour — this only owns the wrap.
	 */
	private static JLabel wrapped(String text, int width)
	{
		return new JLabel("<html><table cellpadding='0' cellspacing='0' border='0'>"
			+ "<tr><td width='" + width + "'>" + text + "</td></tr></table></html>");
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
