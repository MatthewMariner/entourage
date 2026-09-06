package com.matthewmariner.entourage;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Turning NPC ids into the names a person recognises, across the one thread boundary this
 * plugin cannot avoid.
 *
 * <p><b>Why this interface exists at all.</b> The favourites list is a list of integers —
 * see {@link Favourites} on why it has to be — and "3598" is not what the owner calls that
 * body. He calls it Gummy. The only place the name lives is the game's own cache, reachable
 * through {@code Client.getNpcDefinition}, and that call <b>throws off the client
 * thread</b>: an {@code IllegalStateException} in a shipped client, an assertion in a
 * development one. {@link EntourageRosterPanel} is a {@code PluginPanel}, so every listener
 * on it, every redraw, and — this is the part that catches people —
 * {@code PluginManager.startPlugin} and {@code stopPlugin} themselves all run on Swing's
 * event dispatch thread. There is no thread on which a panel may simply read a name.
 *
 * <p><b>So the answer is asked for on one thread and delivered on the other</b>, and both
 * halves are this interface's contract rather than a rule each call site has to remember:
 * {@link #resolve} is <i>called</i> from the event dispatch thread, does its reading on the
 * client thread, and runs its callback back on the event dispatch thread. A panel that
 * hopped threads itself would be a panel that has to be right about it at every call site,
 * and this repo has already put {@code ConfigWriter}, {@code OverlayRegistry} and
 * {@code SidePanel} behind one-method interfaces for the same shape of reason — the thing on
 * the other side cannot be constructed in a test, and the glue that builds it should be one
 * lambda in {@link EntouragePlugin}.
 *
 * <p><b>Not a thread, and it must never become one.</b> Plugin Hub review forbids plugins
 * spawning their own threads; nothing here does. {@code ClientThread#invoke} queues onto a
 * loop the client already runs, and {@code SwingUtilities#invokeLater} queues onto a loop
 * the JVM already runs. There is nothing to shut down, which is also why {@code shutDown()}
 * has nothing to say about this and why a request still in flight when the plugin stops is
 * harmless: the callback lands on a panel that is no longer in the toolbar and updates a map
 * nobody will read.
 *
 * <p><b>A name that does not come back is not an error.</b> The cache is not resident at the
 * login screen, an id may not exist, and the client throws rather than returning null for a
 * missing archive entry. All three come back as "no entry in the map", and the panel draws
 * the bare id — which is true — instead of blocking, retrying in a loop, or printing
 * "null".
 */
@FunctionalInterface
interface NpcNames
{
	/**
	 * Asks for some names.
	 *
	 * @param npcIds the ids whose names are wanted. Read once, on the client thread; an
	 *               implementation may not hold it.
	 * @param names  handed whatever resolved, <b>on the event dispatch thread</b>. Ids that
	 *               did not resolve are absent from the map rather than mapped to
	 *               {@code null}, so a caller's {@code containsKey} means "we asked and the
	 *               client answered" and not "we have not asked yet". Called exactly once
	 *               per request, including when nothing resolved — a caller that only heard
	 *               back on success could never tell "still loading" from "there is no such
	 *               NPC".
	 */
	void resolve(List<Integer> npcIds, Consumer<Map<Integer, String>> names);

	/**
	 * @param name a name straight off {@code NPCComposition.getName()}, or a value derived
	 *             from one — never a name this plugin made up itself
	 * @return whether {@code name} is a real name rather than the game's own placeholder for
	 * an NPC composition with none. That placeholder is not a Java {@code null} — it is the
	 * literal four characters {@code "null"}, spelled out as an ordinary string, because
	 * {@code NPCComposition.getName()} really does return that for an undefined NPC. An
	 * empty string is refused for the same reason a caller would refuse it: nothing to draw
	 * is not a name either.
	 *
	 * <p>Two callers need this exact answer — {@code Follower#getDisplayName()}, reading the
	 * client's own composition on the client thread, and
	 * {@code EntourageRosterPanel#nameFor}, reading a name this interface already carried
	 * across to the event dispatch thread — and neither may depend on the other to get it.
	 * It lives here, the one place both already depend on, rather than as a private
	 * {@code "null".equals(...)} copied into each.
	 */
	static boolean isRealName(@Nullable String name)
	{
		return name != null && !name.isEmpty() && !"null".equals(name);
	}
}
