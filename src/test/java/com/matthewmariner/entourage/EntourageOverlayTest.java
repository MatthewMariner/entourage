package com.matthewmariner.entourage;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The one overlay, and the two bug classes its shape is meant to make impossible: text
 * floating with nobody under it, and text drifting off the figure it belongs to.
 *
 * <p>Both come from a renderer with its own list of what to draw and its own idea of
 * where, so both are tested here as claims about the loop — it walks the live roster, it
 * asks the client whether each follower is still registered, and it re-projects the
 * object's own position every frame.
 *
 * <p><b>The projection is a seam, and it has to be.</b>
 * {@code Perspective.getCanvasTextLocation} reads the live camera, and this repo has no
 * mocking framework and does not use reflection. {@link RecordingOverlay} overrides it.
 * The one test that does <i>not</i> override it is
 * {@link #aProjectionThatFailsDrawsNothingRatherThanThrowing()}, which runs the real
 * {@code Perspective} call against {@link FakeClient} — whose {@code getWorldView(int)}
 * returns null, exactly as the real client's does for a view that has gone.
 *
 * <p><b>The font is a seam for a different reason:</b> {@link DialogueFont#getFont()}
 * reaches {@code FontManager}, whose static initialiser registers three TrueType faces
 * with the local graphics environment and lists a directory under the user's home. That
 * is not something a unit test should set off, so the mapping of three constants onto
 * three faces is the one piece of this feature only a live client can confirm.
 */
public class EntourageOverlayTest
{
	private static final WorldPoint STANDING = new WorldPoint(3221, 3218, 0);

	/** Where {@link RecordingOverlay} pretends the camera puts the text. */
	private static final Point PROJECTED = new Point(100, 200);

	private FakeClient client;
	private FakeWorldView view;
	private FakeConfig config;
	private EntourageScene scene;
	private Graphics2D graphics;

	@Before
	public void setUp()
	{
		client = new FakeClient().withRosterNpcs();
		view = FakeWorldView.around(STANDING);
		client.setTopLevelWorldView(view);
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING));
		config = new FakeConfig();
		scene = new EntourageScene(client, config);

		// A real Graphics2D off a 1x1 image: getFontMetrics() has to work, and a stub
		// Graphics2D would be four hundred throwing methods for no gain.
		graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
	}

	/** One follower, spawned and standing on screen. */
	private Follower spawn()
	{
		scene.onGameTick();
		assertEquals("the fixture has to actually spawn something", 1, client.registeredCount());
		return scene.getFollowers().get(0);
	}

	private static void say(Follower follower, String line)
	{
		follower.getRemarks().say(0, Integer.MAX_VALUE, Collections.singletonList(line));
		assertEquals(line, follower.getRemarks().text());
	}

	@Test
	public void aTalkingFollowerGetsOneLineAndASilentOneGetsNothing()
	{
		Follower follower = spawn();
		RecordingOverlay overlay = new RecordingOverlay();

		overlay.render(graphics);
		assertTrue("a follower with nothing to say draws nothing", overlay.drawn.isEmpty());

		say(follower, "Busy today.");
		overlay.render(graphics);

		assertEquals(1, overlay.drawn.size());
		assertEquals("Busy today.", overlay.drawn.get(0));
	}

	/**
	 * The hard off switch on the frame path. Checked here as well as in
	 * {@link EntourageChatter} so that unticking the box empties the screen on the same
	 * frame rather than on the next game tick, up to 600ms later.
	 */
	@Test
	public void theOffSwitchStopsTheOverlayDrawingAnything()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);
		assertEquals("the fixture has to draw something first", 1, overlay.drawn.size());

		config.setDialogue(false);
		overlay.drawn.clear();
		overlay.projections = 0;
		overlay.render(graphics);

		assertTrue("nothing may be drawn with overhead lines off", overlay.drawn.isEmpty());
		assertEquals("and nothing may even be projected — the check is the first line",
			0, overlay.projections);
	}

	@Test
	public void theNameLabelIsTheFiguresOwnName()
	{
		config.setFigure(EntourageFigure.VANNAKA).setDialogue(false).setNameLabel(true);
		spawn();

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(1, overlay.drawn.size());
		assertEquals(EntourageFigure.VANNAKA.getDisplayName(), overlay.drawn.get(0));
	}

	/**
	 * <b>The two switches are two switches.</b> The early return only fires when
	 * <i>both</i> are off, so with the name label on and dialogue off there is a live
	 * follower, a live overlay, and a line that must not be drawn — which is the one
	 * arrangement that tells the per-follower dialogue check apart from the early return.
	 * A mutation pass deleting that check went green until this existed: every other test
	 * here turns dialogue off with the name label off as well.
	 */
	@Test
	public void aNameLabelWithDialogueOffDrawsTheNameAndNotTheLine()
	{
		config.setDialogue(false).setNameLabel(true);
		Follower follower = spawn();
		say(follower, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(1, overlay.drawn.size());
		assertEquals("only the name, whatever the follower is holding",
			EntourageFigure.ROGUE.getDisplayName(), overlay.drawn.get(0));
	}

	/**
	 * With both on, the line sits above the name — the order a speech bubble and a name
	 * plate go in everywhere else. The lift is in pixels rather than world units, because
	 * the text does not scale with distance and a world-space gap would close up as the
	 * follower walked away.
	 */
	@Test
	public void aTalkingFollowerWithANameDrawsTheLineAboveIt()
	{
		config.setNameLabel(true);
		Follower follower = spawn();
		say(follower, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(2, overlay.drawn.size());
		assertEquals("the name is drawn at head height",
			EntourageFigure.ROGUE.getDisplayName(), overlay.drawn.get(0));
		assertEquals(PROJECTED.getY(), overlay.drawnAt.get(0).getY());

		assertEquals("Busy today.", overlay.drawn.get(1));
		assertTrue("a line drawn at or below the name overlaps it",
			overlay.drawnAt.get(1).getY() < overlay.drawnAt.get(0).getY());
	}

	/** With no name to sit above, the line takes head height itself. */
	@Test
	public void aTalkingFollowerWithNoNameDrawsTheLineAtHeadHeight()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(1, overlay.drawn.size());
		assertEquals(PROJECTED.getY(), overlay.drawnAt.get(0).getY());
	}

	/**
	 * A follower the client no longer has registered contributes nothing, on the very next
	 * frame.
	 *
	 * <p>The line is deliberately put back on the follower here. In the running plugin
	 * {@code despawn()} clears it, and {@code FollowerTest} pins that; this puts it back
	 * behind the overlay's back so that the only thing standing between a stale line and
	 * text drawn over empty ground is the overlay's own {@code isActive()} check.
	 */
	@Test
	public void aDespawnedFollowersTextVanishesOnTheNextFrame()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);
		assertEquals(1, overlay.drawn.size());

		follower.despawn();
		say(follower, "Busy today.");
		overlay.drawn.clear();
		overlay.render(graphics);

		assertTrue("text must never be drawn for a figure the client is not rendering",
			overlay.drawn.isEmpty());
	}

	/**
	 * The position comes from the object, every frame, not from anywhere this overlay
	 * remembers. This is the drifting-text half: a walking follower's text has to walk
	 * with it.
	 */
	@Test
	public void theTextFollowsTheObjectsOwnLivePosition()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);
		LocalPoint first = overlay.projectedAt.get(0);
		assertEquals(follower.getRenderLocation().getX(), first.getX());
		assertEquals(follower.getRenderLocation().getY(), first.getY());

		// Walk it: the player moves east, the follower steps after him and the frame pass
		// places the object part way through the step.
		client.setLocalPlayer(FakePlayer.standingOn(view, STANDING.dx(6)));
		scene.onGameTick();
		scene.onFrame(view, 0.5f);
		say(follower, "Busy today.");

		overlay.projectedAt.clear();
		overlay.render(graphics);

		LocalPoint moved = overlay.projectedAt.get(0);
		assertTrue("the text stayed where the follower used to be", moved.getX() != first.getX());
		assertEquals(follower.getRenderLocation().getX(), moved.getX());
	}

	@Test
	public void theColourIsTheOneTheSettingNames()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");
		config.setDialogueColour(DialogueColour.GREEN);

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(DialogueColour.GREEN.getColour(), overlay.colours.get(0));
	}

	/**
	 * <b>The graphics context is handed back the way it was found.</b>
	 * {@code OverlayRenderer.safeRender} sets the font before every overlay it draws, so
	 * ours cannot leak into anybody else's — but that is a reading of the client's
	 * internals rather than a contract, and putting it back costs one field write per
	 * frame.
	 */
	@Test
	public void theFontIsPutBackAfterDrawing()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");

		Font before = new Font(Font.MONOSPACED, Font.PLAIN, 11);
		graphics.setFont(before);

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals("the fixture has to have actually drawn something", 1, overlay.drawn.size());
		assertSame("the overlay's own font was left on a Graphics2D it does not own",
			before, graphics.getFont());
	}

	/** The font the overlay draws with is the one the setting names. */
	@Test
	public void theFontAskedForIsTheOneTheSettingNames()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");
		config.setDialogueFont(DialogueFont.SMALL);

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertEquals(DialogueFont.SMALL, overlay.fontsAskedFor.get(0));
	}

	/**
	 * A projection that fails draws nothing and does not throw — using the real
	 * {@code Perspective} call rather than a stand-in for it. Behind the camera, off the
	 * edge of the viewport, or on a world view that has gone: all three happen constantly
	 * and none is an error. This runs once per frame per follower, so a throw here would
	 * be sixty stack traces a second.
	 */
	@Test
	public void aProjectionThatFailsDrawsNothingRatherThanThrowing()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");

		DrawRecordingOverlay overlay = new DrawRecordingOverlay();

		assertNull("the real projection has to fail for this fixture, or the test proves nothing",
			overlay.textLocation(graphics, follower.getRenderLocation(), "Busy today."));

		overlay.render(graphics);

		assertTrue("a failed projection draws nothing", overlay.drawn.isEmpty());
	}

	@Test
	public void anEmptyRosterDrawsNothing()
	{
		RecordingOverlay overlay = new RecordingOverlay();

		assertNull(overlay.render(graphics));

		assertTrue(scene.getFollowers().isEmpty());
		assertTrue(overlay.drawn.isEmpty());
		assertEquals(0, overlay.projections);
	}

	@Test
	public void bothSwitchesOffCostsNothingButTwoConfigReads()
	{
		Follower follower = spawn();
		say(follower, "Busy today.");
		config.setDialogue(false).setNameLabel(false);

		RecordingOverlay overlay = new RecordingOverlay();
		overlay.render(graphics);

		assertTrue(overlay.drawn.isEmpty());
		assertEquals(0, overlay.projections);
		assertTrue("the font is not even asked for when nothing will be drawn",
			overlay.fontsAskedFor.isEmpty());
	}

	/**
	 * The overlay with every seam recorded and the projection replaced by a fixed point,
	 * so the loop's decisions are observable without a camera.
	 */
	private class RecordingOverlay extends EntourageOverlay
	{
		private final List<String> drawn = new ArrayList<>();
		private final List<Point> drawnAt = new ArrayList<>();
		private final List<Color> colours = new ArrayList<>();
		private final List<LocalPoint> projectedAt = new ArrayList<>();
		private final List<DialogueFont> fontsAskedFor = new ArrayList<>();

		private int projections;

		private RecordingOverlay()
		{
			super(null, client, scene, config);
		}

		@Override
		@Nullable
		Point textLocation(Graphics2D graphics, LocalPoint at, String text)
		{
			projections++;
			projectedAt.add(at);
			return PROJECTED;
		}

		@Override
		void drawText(Graphics2D graphics, Point at, String text, Color colour)
		{
			drawn.add(text);
			drawnAt.add(at);
			colours.add(colour);
		}

		@Override
		Font fontFor(DialogueFont font)
		{
			// Never DialogueFont.getFont(): that reaches FontManager. See the class javadoc.
			fontsAskedFor.add(font);
			return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		}
	}

	/**
	 * Only the draw and the font are replaced; the projection is the real one. Used by the
	 * single test that wants {@code Perspective} to actually run.
	 */
	private class DrawRecordingOverlay extends EntourageOverlay
	{
		private final List<String> drawn = new ArrayList<>();

		private DrawRecordingOverlay()
		{
			super(null, client, scene, config);
		}

		@Override
		void drawText(Graphics2D graphics, Point at, String text, Color colour)
		{
			drawn.add(text);
		}

		@Override
		Font fontFor(DialogueFont font)
		{
			return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		}
	}
}
