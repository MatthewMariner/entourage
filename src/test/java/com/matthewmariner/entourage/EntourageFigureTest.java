package com.matthewmariner.entourage;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The roster: every NPC id and every animation on it, pinned twice over.
 *
 * <p><b>Twice, because a rename and a renumbering are different failures.</b> Naming
 * the {@code gameval} constant catches an entry being pointed at a different NPC by a
 * careless edit — the constant would still compile, but it would be the wrong one.
 * Naming the literal catches the id being <i>renumbered</i> underneath the constant,
 * which compiles, does not warn, and puts a different body on the follower. Neither
 * assertion is redundant with the other, and the second is the kind of failure that
 * killed {@code ../lively-cities}' predecessor plugin.
 *
 * <p>The two pinning tests also count what they pinned against
 * {@code EntourageFigure.values().length}, so a preset added without a line here is a
 * red test rather than an unpinned entry.
 */
public class EntourageFigureTest
{
	private final EnumSet<EntourageFigure> pinned = EnumSet.noneOf(EntourageFigure.class);

	// --- The bodies -----------------------------------------------------------

	@Test
	public void everyFigureWearsTheNpcItIsDocumentedAsWearing()
	{
		npc(EntourageFigure.ROGUE, NpcID.ROGUE, 526);
		npc(EntourageFigure.VANNAKA, NpcID.SLAYER_MASTER_3, 403);
		npc(EntourageFigure.NIEVE, NpcID.SLAYER_MASTER_NIEVE, 6797);
		npc(EntourageFigure.STEVE, NpcID.SLAYER_MASTER_STEVE, 6798);
		npc(EntourageFigure.TURAEL, NpcID.SLAYER_MASTER_1_TUREAL, 13618);
		npc(EntourageFigure.DURADEL, NpcID.SLAYER_MASTER_5_DURADEL, 13622);
		npc(EntourageFigure.MAZCHNA, NpcID.SLAYER_MASTER_2_MAZCHNA, 13620);
		npc(EntourageFigure.WISE_OLD_MAN, NpcID.WISE_OLD_MAN, 2108);
		npc(EntourageFigure.WHITE_KNIGHT, NpcID.WHITE_KNIGHT, 1798);
		npc(EntourageFigure.ELITE_BLACK_KNIGHT, NpcID.ELITE_BLACK_KNIGHT_1, 13463);
		npc(EntourageFigure.SIR_AMIK_VARZE, NpcID.SIR_AMIK_VARZE, 4771);
		npc(EntourageFigure.SIR_VYVIN, NpcID.SIR_VYVIN, 4736);
		npc(EntourageFigure.PALADIN, NpcID.ARDOUGNE_PALADIN2, 1144);
		npc(EntourageFigure.GRILL_KNIGHT, NpcID.GRILLKNIGHT, 4777);
		npc(EntourageFigure.GHOMMAL, NpcID.WARGUILD_GHOMMAL_NPC, 13613);
		npc(EntourageFigure.HERO, NpcID.HERO, 3295);
		npc(EntourageFigure.ZAMORAK_MAGE, NpcID.RCU_ZAMMY_MAGE1A, 2580);
		npc(EntourageFigure.NECROMANCER, NpcID.NECROMANCER, 1025);
		npc(EntourageFigure.SORCERESS, NpcID.ARABIAN_SORCERESS, 1807);
		npc(EntourageFigure.THIEF, NpcID.THIEF2, 3253);
		npc(EntourageFigure.PIRATE, NpcID.PIRATE1, 521);
		npc(EntourageFigure.HANS, NpcID.HANS, 3105);
		npc(EntourageFigure.FARMER, NpcID.FARMER1, 3114);

		assertEquals("a preset with no line above is a preset nobody has checked the id of",
			EntourageFigure.values().length, pinned.size());
	}

	private void npc(EntourageFigure figure, int constant, int literal)
	{
		assertEquals(figure.name() + " does not wear the NpcID constant it is documented as",
			constant, figure.getNpcId());
		assertEquals(figure.name() + ": that constant has been renumbered under the enum",
			literal, figure.getNpcId());
		assertTrue(figure.name() + " is pinned twice", pinned.add(figure));
	}

	/**
	 * <b>The exclusions, which are a finding rather than an omission.</b> Each of these
	 * was looked at for the roster and rejected for a reason that a later reader, seeing
	 * a plausible slayer master or a well-known face missing, would otherwise be likely
	 * to "fix". Krystilia is the sharpest: her stand and her walk are the same id, so she
	 * has no walk animation at all and would slide.
	 */
	@Test
	public void theRejectedNpcsStayRejected()
	{
		Set<Integer> ids = new HashSet<>();
		for (EntourageFigure figure : EntourageFigure.values())
		{
			ids.add(figure.getNpcId());
		}

		assertFalse("Chaeldar is on the fairy rig and has one model",
			ids.contains(NpcID.SLAYER_MASTER_4));
		assertFalse("Krystilia's stand and walk are the same id — she has no walk animation",
			ids.contains(NpcID.SLAYER_MASTER_7));
		assertFalse("Spria's animations are both -1", ids.contains(NpcID.SLAYER_MASTER_9_ACTIVE));
		assertFalse("the Gnome Child is on the gnome rig", ids.contains(NpcID.GNOMECHILDGREEN));
		assertFalse("Kamfreena was rejected on inspection", ids.contains(NpcID.WARGUILD_KAMFREENA));
		assertFalse("Sir Mordred was rejected on inspection", ids.contains(NpcID.SIR_MORDRED));
		assertFalse("6799 is the other Steve, not the slayer master",
			ids.contains(NpcID.WYVERN_CAVE_STEVE));
	}

	@Test
	public void noTwoFiguresWearTheSameBody()
	{
		Set<Integer> ids = new HashSet<>();
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertTrue(figure.name() + " duplicates an NPC id already in the enum",
				ids.add(figure.getNpcId()));
		}
	}

	// --- The animations -------------------------------------------------------

	/**
	 * The (idle, walk) pair each preset declares, from the cache. Tier A is the human
	 * rig's own 808/819; every Tier B entry below is here because it declares something
	 * else, and getting one of them wrong is a figure holding a polearm with its arms at
	 * its sides.
	 */
	@Test
	public void everyFigurePlaysThePairItsNpcDeclares()
	{
		human(EntourageFigure.ROGUE);
		human(EntourageFigure.DURADEL);
		human(EntourageFigure.MAZCHNA);
		human(EntourageFigure.SIR_AMIK_VARZE);
		human(EntourageFigure.SIR_VYVIN);
		human(EntourageFigure.PALADIN);
		human(EntourageFigure.GRILL_KNIGHT);
		human(EntourageFigure.GHOMMAL);
		human(EntourageFigure.HERO);
		human(EntourageFigure.ZAMORAK_MAGE);
		human(EntourageFigure.NECROMANCER);
		human(EntourageFigure.SORCERESS);
		human(EntourageFigure.THIEF);
		human(EntourageFigure.PIRATE);
		human(EntourageFigure.HANS);
		human(EntourageFigure.FARMER);

		pair(EntourageFigure.VANNAKA,
			EntourageAnimation.WEAPON_STAND, AnimationID.HUMAN_DH_WEAPON_READY, 2561,
			EntourageAnimation.HUMAN_WALK, AnimationID.HUMAN_WALK_F, 819);
		pair(EntourageFigure.NIEVE,
			EntourageAnimation.STAFF_STAND, AnimationID.HUMAN_STAFFREADY, 813,
			EntourageAnimation.HALBERD_WALK, AnimationID.HUMAN_HALBERDWALK_F, 1205);
		pair(EntourageFigure.STEVE,
			EntourageAnimation.STAFF_STAND, AnimationID.HUMAN_STAFFREADY, 813,
			EntourageAnimation.HALBERD_WALK, AnimationID.HUMAN_HALBERDWALK_F, 1205);
		pair(EntourageFigure.TURAEL,
			EntourageAnimation.STAFF_STAND, AnimationID.HUMAN_STAFFREADY, 813,
			EntourageAnimation.HALBERD_WALK, AnimationID.HUMAN_HALBERDWALK_F, 1205);
		pair(EntourageFigure.WISE_OLD_MAN,
			EntourageAnimation.STAFF_STAND, AnimationID.HUMAN_STAFFREADY, 813,
			EntourageAnimation.WALKING_STICK_WALK, AnimationID.WALK_WALKINGSTICK, 1146);
		pair(EntourageFigure.WHITE_KNIGHT,
			EntourageAnimation.WEAPON_STAND, AnimationID.HUMAN_DH_WEAPON_READY, 2561,
			EntourageAnimation.WEAPON_WALK, AnimationID.HUMAN_DH_WEAPON_WALK, 2562);
		pair(EntourageFigure.ELITE_BLACK_KNIGHT,
			EntourageAnimation.SWORD_STAND, AnimationID.DH_SWORD_UPDATE_READY, 7053,
			EntourageAnimation.SWORD_WALK, AnimationID.DH_SWORD_UPDATE_WALK, 7052);

		assertEquals("a preset with no line above is a preset nobody has checked the animations of",
			EntourageFigure.values().length, pinned.size());
	}

	private void human(EntourageFigure figure)
	{
		pair(figure,
			EntourageAnimation.HUMAN_STAND, AnimationID.HUMAN_READY, 808,
			EntourageAnimation.HUMAN_WALK, AnimationID.HUMAN_WALK_F, 819);
	}

	private void pair(EntourageFigure figure,
		EntourageAnimation idle, int idleConstant, int idleLiteral,
		EntourageAnimation walk, int walkConstant, int walkLiteral)
	{
		assertEquals(figure.name() + " does not idle with the animation it is documented as",
			idle, figure.getIdleAnimation());
		assertEquals(figure.name() + ": the idle names a different gameval constant",
			idleConstant, figure.getIdleAnimation().getId());
		assertEquals(figure.name() + ": the idle's constant has been renumbered",
			idleLiteral, figure.getIdleAnimation().getId());

		assertEquals(figure.name() + " does not walk with the animation it is documented as",
			walk, figure.getWalkAnimation());
		assertEquals(figure.name() + ": the walk names a different gameval constant",
			walkConstant, figure.getWalkAnimation().getId());
		assertEquals(figure.name() + ": the walk's constant has been renumbered",
			walkLiteral, figure.getWalkAnimation().getId());

		assertTrue(figure.name() + " is pinned twice", pinned.add(figure));
	}

	/**
	 * The run is the one animation in the enum that was not read off the NPC that plays
	 * it — nothing in the cache gives Nieve a run, because Nieve never runs — so it is
	 * the same id for everybody and the enum says so out loud.
	 */
	@Test
	public void everyFigureRunsWithTheHumanRigsOwnRun()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertEquals(figure.name(), EntourageAnimation.HUMAN_RUN, figure.getRunAnimation());
			assertEquals(figure.name(), AnimationID.HUMAN_RUNNING, figure.getRunAnimation().getId());
			assertEquals(figure.name() + ": HUMAN_RUNNING has been renumbered",
				824, figure.getRunAnimation().getId());
		}
	}

	/**
	 * A figure whose two movement animations are the same id is a figure that is not
	 * animated by moving — the sliding-mesh failure. Krystilia is exactly this case,
	 * which is why she is not in the enum.
	 */
	@Test
	public void noFigureIdlesAndWalksWithTheSameAnimation()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertNotEquals(figure.name() + " would slide along the ground",
				figure.getIdleAnimation(), figure.getWalkAnimation());
			assertNotEquals(figure.name() + " would slide at a run",
				figure.getWalkAnimation(), figure.getRunAnimation());
		}
	}

	@Test
	public void everyFigureHasAllThreeAnimations()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertTrue(figure.name() + " has no idle", figure.getIdleAnimation().getId() > 0);
			assertTrue(figure.name() + " has no walk", figure.getWalkAnimation().getId() > 0);
			assertTrue(figure.name() + " has no run", figure.getRunAnimation().getId() > 0);
		}
	}

	// --- Names ----------------------------------------------------------------

	@Test
	public void theLabelNamesTheNpcSoALogLineCanBeTracedToACacheId()
	{
		for (EntourageFigure figure : EntourageFigure.values())
		{
			assertTrue(figure.label(), figure.label().contains(String.valueOf(figure.getNpcId())));
		}
	}

	/**
	 * The dropdown reads {@code toString()}, so a figure with no display name would be
	 * an entry reading {@code GRILL_KNIGHT}, and two figures sharing one would be two
	 * indistinguishable rows.
	 */
	@Test
	public void everyFigureHasItsOwnPlayerFacingName()
	{
		Set<String> names = new HashSet<>();
		for (EntourageFigure figure : EntourageFigure.values())
		{
			String name = figure.getDisplayName();
			assertFalse(figure.name() + " has no display name", name.isEmpty());
			assertEquals(figure.name() + " is not rendered by its display name",
				name, figure.toString());
			assertNotEquals(figure.name() + " is showing its enum constant to the user",
				figure.name(), name);
			assertTrue(figure.name() + " shares a display name with another figure",
				names.add(name));
		}
	}

	@Test
	public void theDefaultIsTheRogue()
	{
		assertEquals("the config default and the roster have to agree on who ships",
			EntourageFigure.ROGUE, EntourageFigure.DEFAULT);
		assertEquals(EntourageFigure.DEFAULT, new FakeConfig().figure());
	}
}
