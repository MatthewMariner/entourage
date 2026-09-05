package com.matthewmariner.entourage;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * The one overlay: whatever the followers are saying, and their names if they are asked
 * for.
 *
 * <p><b>It derives everything from the live roster, every frame, and keeps no list of its
 * own.</b> That is the structural fix for the two classic overhead-text bugs at once —
 * text floating with nobody under it, and text drifting off the figure it belonged to.
 * Both are symptoms of one thing: a renderer with its own idea of what to draw and its
 * own idea of where. There is no such idea here. Each frame this walks
 * {@link EntourageScene#getFollowers()}, skips anything the client does not have
 * registered, asks the follower what it is saying, and projects the position the client
 * is about to draw the model at. A follower that despawned one frame ago is simply not in
 * the loop's output.
 *
 * <p><b>{@code Actor.setOverheadText} was not an option.</b> A {@code RuneLiteObject} is
 * not an {@code Actor} — it has no overhead text, no health bar and no native menu — so
 * drawing in screen space is the only way, and it is what the hub-merged precedents do.
 *
 * <p><b>Projection is allowed to fail, and failing must draw nothing.</b>
 * {@code Perspective.getCanvasTextLocation} returns null for a figure behind the camera,
 * off the edge of the viewport, or on a world view the client no longer has — all three
 * happen constantly and none is an error. Every one of them is a {@code continue}. This
 * runs once per frame per talking follower, so a throw here would be a stack trace sixty
 * times a second.
 *
 * <p><b>The hard off switch is checked here as well as in {@link EntourageChatter}.</b>
 * The chatter clears its state on its next game tick, up to 600ms away, and a toggle that
 * visibly lags the click reads as a toggle that did not work. Checking it here is what
 * makes unticking the box silence the screen on the same frame.
 *
 * <p><b>What is read per frame is kept to what is drawn.</b> A {@code Config} is a
 * dynamic proxy over {@code ConfigManager}, so every getter is a map lookup and a string
 * parse — which is why the rest of the plugin reads {@link EntourageSettings} once a game
 * tick instead. This cannot: it draws between game ticks, so it has to see a change of
 * colour or a flipped switch on the frame it happens. It reads the two switches first and
 * returns before touching anything else when both are off, so the cost of "I do not want
 * this" is two lookups a frame.
 */
class EntourageOverlay extends Overlay
{
	/**
	 * How far above the figure's tile the text sits, in local units
	 * ({@link Perspective#LOCAL_TILE_SIZE} = 128 per tile).
	 *
	 * <p>A constant rather than the model's own height, and the same 220 that
	 * {@code ../lively-cities} settled on. {@code Model} exposes {@code getBottomY()} and
	 * an {@code AABB}, neither of which is a documented "how tall is this" and both of
	 * which need a live, lit model to mean anything; {@code Actor.getLogicalHeight()},
	 * which RuneLite's own actor overlays use, does not exist on a
	 * {@code RuneLiteObject}. Every figure in this plugin's roster is a human-scale
	 * single-tile model, so one number that clears a head is both honest and right.
	 */
	static final int TEXT_HEIGHT = 220;

	private final Client client;
	private final EntourageScene scene;
	private final EntourageConfig config;

	@Inject
	EntourageOverlay(EntouragePlugin plugin, Client client, EntourageScene scene,
		EntourageConfig config)
	{
		super(plugin);
		this.client = client;
		this.scene = scene;
		this.config = config;

		// ABOVE_SCENE so the text sits over the world but under the interface — the layer
		// RuneLite's own world-space text overlays use. DYNAMIC because there is no box to
		// drag: the position comes from the followers.
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);

		// Below everything that is actually telling the user something. This is ambience;
		// it must never end up drawn over a warning.
		setPriority(Overlay.PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final boolean dialogue = config.dialogue();
		final boolean names = config.nameLabel();
		if (!dialogue && !names)
		{
			return null;
		}

		final List<Follower> followers = scene.getFollowers();
		if (followers.isEmpty())
		{
			return null;
		}

		final Color colour = config.dialogueColour().getColour();

		// OverlayRenderer.safeRender sets the font on this Graphics2D before every
		// overlay it draws — disassembled from 1.12.38, it picks one of three by the
		// overlay's position — so ours cannot leak into anybody else's drawing. It is put
		// back anyway, in a finally: that is a reading of the client's internals rather
		// than a contract, and restoring costs one field write per frame.
		final Font original = graphics.getFont();
		graphics.setFont(fontFor(config.dialogueFont()));

		try
		{
			// An index loop rather than a for-each: this runs at the frame rate and
			// allocates no iterator.
			for (int i = 0; i < followers.size(); i++)
			{
				draw(graphics, followers.get(i), dialogue, names, colour);
			}
		}
		finally
		{
			graphics.setFont(original);
		}

		return null;
	}

	/**
	 * One follower's text: the name at head height, and the line it is saying above that.
	 *
	 * <p>The line goes on top rather than underneath because the name is a label on the
	 * figure and the line is something coming out of it, which is the order a speech
	 * bubble and a name plate sit in everywhere else. The lift is a font height in
	 * pixels rather than more local units, because the text does not scale with distance
	 * and a world-space gap would close up as the follower walked away.
	 */
	private void draw(Graphics2D graphics, Follower follower, boolean dialogue, boolean names,
		Color colour)
	{
		// Asked of the client, not of local bookkeeping: this is what makes a despawned
		// follower's text disappear on the next frame rather than on the next tick.
		if (!follower.isActive())
		{
			return;
		}

		LocalPoint at = follower.getRenderLocation();
		if (at == null)
		{
			return;
		}

		String name = names ? follower.getDisplayName() : null;
		String line = dialogue ? follower.getRemarks().text() : null;

		if (name != null)
		{
			Point on = textLocation(graphics, at, name);
			if (on != null)
			{
				drawText(graphics, on, name, colour);
			}
		}

		if (line != null)
		{
			Point on = textLocation(graphics, at, line);
			if (on != null)
			{
				int lift = name == null ? 0 : graphics.getFontMetrics().getHeight();
				drawText(graphics, new Point(on.getX(), on.getY() - lift), line, colour);
			}
		}
	}

	/**
	 * Projects a follower's position to the screen.
	 *
	 * <p>Package-private and non-final so a test can stand in for it — the real
	 * implementation reads the live camera through {@code Perspective.localToCanvas}, and
	 * this repo has no mocking framework and does not use reflection. Everything the
	 * tests care about — which followers are considered, and what happens when this
	 * returns null — is on this side of the seam.
	 *
	 * @return the canvas point, or {@code null} if the follower cannot be drawn
	 */
	@Nullable
	Point textLocation(Graphics2D graphics, LocalPoint at, String text)
	{
		return Perspective.getCanvasTextLocation(client, graphics, at, text, TEXT_HEIGHT);
	}

	/** Draws one string. Package-private and non-final for the same reason. */
	void drawText(Graphics2D graphics, Point at, String text, Color colour)
	{
		OverlayUtil.renderTextLocation(graphics, at, text, colour);
	}

	/**
	 * The face to draw in. The third seam, and the only one that exists for the tests
	 * rather than for the camera: {@link DialogueFont#getFont()} reaches
	 * {@code FontManager}, whose static initialiser registers three TrueType faces with
	 * the local {@code GraphicsEnvironment} and lists {@code ~/.runelite/fonts} — a
	 * graphics environment and a home directory, in a unit test, for a mapping of three
	 * constants onto three static fields.
	 */
	Font fontFor(DialogueFont font)
	{
		return font.getFont();
	}
}
