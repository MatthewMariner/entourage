package com.matthewmariner.entourage;

import static com.matthewmariner.entourage.NpcRecordBytes.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The cache decoder.
 *
 * <p><b>The bulk of this file is one assertion repeated: that every opcode consumes
 * exactly its own bytes.</b> Only four numbers come out of {@link NpcRecord}, so a skip
 * that is a byte out is invisible from outside — right up until the cursor lands inside
 * the wrong field and a follower gets somebody else's walk animation. {@link
 * NpcRecordBytes} explains the shape: opcode under test, then opcode 13 carrying a
 * sentinel, then the terminator. Read the sentinel back and the width was right; read
 * anything else and it was not.
 */
public class NpcRecordTest
{
	/**
	 * The value opcode 13 carries in every width test. Not round, and not a number any
	 * other field in these fixtures holds, so a misread cannot land on it by luck.
	 */
	private static final int SENTINEL = 4931;

	// --- The shape of the format ----------------------------------------------

	@Test
	public void nullDataDeclaresNothingAndIsNotComplete()
	{
		NpcRecord decoded = NpcRecord.decode(null);

		assertEquals(NpcRecord.NO_ANIMATION, decoded.getStandingAnimation());
		assertEquals(NpcRecord.NO_ANIMATION, decoded.getWalkingAnimation());
		assertEquals(NpcRecord.NO_ANIMATION, decoded.getRunAnimation());
		assertFalse(decoded.isComplete());
		assertFalse(decoded.hasWalkCycle());
	}

	@Test
	public void emptyDataIsNotComplete()
	{
		NpcRecord decoded = NpcRecord.decode(new byte[0]);

		assertFalse("nothing to read is not a well-formed record", decoded.isComplete());
		assertEquals(NpcRecord.NO_ANIMATION, decoded.getStandingAnimation());
	}

	@Test
	public void aBareTerminatorIsCompleteAndDeclaresNothing()
	{
		NpcRecord decoded = NpcRecord.decode(record().end());

		assertTrue(decoded.isComplete());
		assertEquals(NpcRecord.NO_ANIMATION, decoded.getStandingAnimation());
		assertEquals(NpcRecord.NO_ANIMATION, decoded.getWalkingAnimation());
		assertFalse(decoded.hasWalkCycle());
	}

	@Test
	public void readsTheStandAndTheWalk()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.standing(808)
			.walking(819)
			.end());

		assertEquals(808, decoded.getStandingAnimation());
		assertEquals(819, decoded.getWalkingAnimation());
		assertEquals(NpcRecord.NO_ANIMATION, decoded.getRunAnimation());
		assertTrue(decoded.isComplete());
		assertTrue(decoded.hasWalkCycle());
	}

	/**
	 * Opcode 17 is the other way a record declares a walk — the walk plus its three
	 * turning variants — and a decoder that only knew 14 would read the walk of an NPC
	 * that uses it as absent, which is a refusal for a figure that walks perfectly well.
	 */
	@Test
	public void opcodeSeventeenAlsoDeclaresTheWalk()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(17).u16(1205).u16(1206).u16(1207).u16(1208)
			.standing(SENTINEL)
			.end());

		assertEquals(1205, decoded.getWalkingAnimation());
		assertEquals("the three turning variants must be consumed, not read as opcodes",
			SENTINEL, decoded.getStandingAnimation());
	}

	@Test
	public void opcodeOneHundredAndFourteenDeclaresTheRun()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(114).u16(824)
			.standing(SENTINEL)
			.end());

		assertEquals(824, decoded.getRunAnimation());
		assertEquals(SENTINEL, decoded.getStandingAnimation());
	}

	@Test
	public void opcodeOneHundredAndFifteenDeclaresTheRunAndItsVariants()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(115).u16(824).u16(825).u16(826).u16(827)
			.standing(SENTINEL)
			.end());

		assertEquals(824, decoded.getRunAnimation());
		assertEquals(SENTINEL, decoded.getStandingAnimation());
	}

	/** A later opcode wins, exactly as the upstream loader's straight assignment does. */
	@Test
	public void aSecondDeclarationOverwritesTheFirst()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.walking(819)
			.u8(17).u16(1205).u16(0).u16(0).u16(0)
			.end());

		assertEquals(1205, decoded.getWalkingAnimation());
	}

	// --- Every payload width --------------------------------------------------

	@Test
	public void modelsWithShortIdsAreSkipped()
	{
		assertWidth(record().u8(1).u8(3).u16(100).u16(200).u16(300));
	}

	/**
	 * Opcode 61 is the wide form of the model list, and it exists because model ids
	 * outgrew sixteen bits. Sizing it as shorts would leave the cursor eight bytes short
	 * on a three-model NPC.
	 */
	@Test
	public void modelsWithIntIdsAreSkipped()
	{
		assertWidth(record().u8(61).u8(3).i32(70000).i32(80000).i32(90000));
	}

	@Test
	public void theNameIsSkipped()
	{
		assertWidth(record().u8(2).str("Cave goblin guard"));
	}

	@Test
	public void anEmptyNameIsSkipped()
	{
		assertWidth(record().u8(2).str(""));
	}

	@Test
	public void theSizeIsSkipped()
	{
		assertWidth(record().u8(12).u8(4));
	}

	@Test
	public void theIdleTurnAnimationsAreSkipped()
	{
		assertWidth(record().u8(15).u16(1000).u8(16).u16(1001));
	}

	@Test
	public void theCategoryIsSkipped()
	{
		assertWidth(record().u8(18).u16(42));
	}

	@Test
	public void everyOpIsSkipped()
	{
		NpcRecordBytes bytes = record();
		for (int opcode = 30; opcode < 35; opcode++)
		{
			bytes.u8(opcode).str("Talk-to");
		}
		assertWidth(bytes);
	}

	@Test
	public void recoloursAndRetexturesAreSkipped()
	{
		assertWidth(record()
			.u8(40).u8(2).u16(1).u16(2).u16(3).u16(4)
			.u8(41).u8(1).u16(5).u16(6));
	}

	@Test
	public void anEmptyRecolourListIsSkipped()
	{
		assertWidth(record().u8(40).u8(0));
	}

	@Test
	public void chatheadModelsAreSkippedInBothWidths()
	{
		assertWidth(record()
			.u8(60).u8(2).u16(11).u16(12)
			.u8(62).u8(1).i32(99000));
	}

	@Test
	public void theCombatStatsAreSkipped()
	{
		NpcRecordBytes bytes = record();
		for (int opcode = 74; opcode < 80; opcode++)
		{
			bytes.u8(opcode).u16(70);
		}
		assertWidth(bytes);
	}

	@Test
	public void theZeroByteFlagsConsumeNothing()
	{
		assertWidth(record()
			.u8(93).u8(99).u8(107).u8(109).u8(111)
			.u8(122).u8(123).u8(129).u8(130).u8(145).u8(147));
	}

	@Test
	public void theShortValuedFieldsAreSkipped()
	{
		assertWidth(record()
			.u8(95).u16(126)
			.u8(97).u16(128)
			.u8(98).u16(128)
			.u8(103).u16(32)
			.u8(124).u16(200)
			.u8(126).u16(51)
			.u8(146).u16(39188));
	}

	@Test
	public void theSingleByteLightingFieldsAreSkipped()
	{
		assertWidth(record().u8(100).u8(0xF0).u8(101).u8(0x10));
	}

	@Test
	public void theCrawlAnimationsAreSkipped()
	{
		assertWidth(record()
			.u8(116).u16(1300)
			.u8(117).u16(1300).u16(1301).u16(1302).u16(1303));
	}

	@Test
	public void bothTransformTablesAreSkipped()
	{
		// 106: varbit, varp, then length+1 shorts. 118: varbit, varp, a default, then the
		// same table. A decoder that read 118 as 106 would be two bytes adrift.
		assertWidth(record()
			.u8(106).u16(65535).u16(1234).u8(2).u16(10).u16(11).u16(12)
			.u8(118).u16(65535).u16(1234).u16(65535).u8(1).u16(20).u16(21));
	}

	@Test
	public void aTransformTableOfOneEntryIsSkipped()
	{
		// length 0 still carries one entry: the upstream loop runs `index <= length`.
		assertWidth(record().u8(106).u16(1).u16(2).u8(0).u16(30));
	}

	@Test
	public void headIconsWithOneEntryAreSkipped()
	{
		// Bitfield 0b1: one position, set. A two-byte archive id below 0x8000 and a
		// one-byte sprite index below 128.
		assertWidth(record().u8(102).u8(0b1).u16(0x0123).u8(0x05));
	}

	@Test
	public void headIconsCountBitLengthRatherThanSetBits()
	{
		// Bitfield 0b101: three positions, two of them set. Counting set bits instead of
		// the bit length would read two entries and leave a byte behind.
		assertWidth(record()
			.u8(102).u8(0b101)
			.u16(0x0123).u8(0x05)
			.u16(0x0456).u8(0x06));
	}

	@Test
	public void headIconsHandleTheWideArchiveIdAndTheWideSpriteIndex()
	{
		// A first byte with its high bit set makes the archive id four bytes; a sprite
		// index byte at or above 128 makes it two.
		assertWidth(record().u8(102).u8(0b1).i32(0x80000001).u16(0x8005));
	}

	@Test
	public void anEmptyHeadIconBitfieldIsSkipped()
	{
		assertWidth(record().u8(102).u8(0));
	}

	@Test
	public void everyParameterTypeIsSkipped()
	{
		assertWidth(record()
			.u8(249).u8(3)
			.u8(1).fill(3).str("a string parameter")
			.u8(2).fill(3).fill(8)
			.u8(0).fill(3).fill(4));
	}

	@Test
	public void anEmptyParameterMapIsSkipped()
	{
		assertWidth(record().u8(249).u8(0));
	}

	@Test
	public void theSubOpAndConditionalOpsAreSkipped()
	{
		assertWidth(record()
			.u8(251).u8(1).u8(2).str("Sub")
			.u8(252).u8(1).u16(2).u16(3).i32(4).i32(5).str("Conditional")
			.u8(253).u8(1).u16(2).u16(3).u16(4).i32(5).i32(6).str("Conditional sub"));
	}

	/**
	 * The whole table in one record, in ascending order, the way the cache writes one.
	 * Individually every width above is proven; this is the one that would catch a
	 * {@code case} that falls through into its neighbour.
	 */
	@Test
	public void aFullRecordStillYieldsItsAnimations()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(1).u8(2).u16(100).u16(200)
			.u8(2).str("Somebody")
			.u8(12).u8(1)
			.standing(808)
			.walking(819)
			.u8(15).u16(1)
			.u8(16).u16(2)
			.u8(18).u16(3)
			.u8(30).str("Talk-to")
			.u8(40).u8(1).u16(4).u16(5)
			.u8(60).u8(1).u16(6)
			.u8(74).u16(7)
			.u8(93)
			.u8(95).u16(8)
			.u8(97).u16(128)
			.u8(99)
			.u8(100).u8(9)
			.u8(102).u8(0b11).u16(10).u8(11).u16(12).u8(13)
			.u8(103).u16(32)
			.u8(106).u16(14).u16(15).u8(1).u16(16).u16(17)
			.u8(107)
			.u8(114).u16(824)
			.u8(116).u16(18)
			.u8(122)
			.u8(124).u16(19)
			.u8(126).u16(20)
			.u8(145)
			.u8(146).u16(21)
			.u8(249).u8(1).u8(1).fill(3).str("p")
			.end());

		assertEquals(808, decoded.getStandingAnimation());
		assertEquals(819, decoded.getWalkingAnimation());
		assertEquals(824, decoded.getRunAnimation());
		assertTrue(decoded.isComplete());
		assertTrue(decoded.hasWalkCycle());
	}

	// --- Malformed records ----------------------------------------------------

	@Test
	public void anUnknownOpcodeStopsTheParseAndKeepsWhatCameBefore()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.standing(808)
			.walking(819)
			.u8(200).u16(1).u16(2)
			.u8(114).u16(824)
			.end());

		assertEquals(808, decoded.getStandingAnimation());
		assertEquals(819, decoded.getWalkingAnimation());
		assertEquals("nothing past an opcode of unknown width may be trusted",
			NpcRecord.NO_ANIMATION, decoded.getRunAnimation());
		assertFalse(decoded.isComplete());
		assertTrue("what was read before it is still usable", decoded.hasWalkCycle());
	}

	@Test
	public void anUnknownOpcodeBeforeTheAnimationsLeavesThemUndeclared()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(201)
			.standing(808)
			.walking(819)
			.end());

		assertEquals(NpcRecord.NO_ANIMATION, decoded.getStandingAnimation());
		assertFalse(decoded.isComplete());
		assertFalse("an unreadable record must not produce a figure", decoded.hasWalkCycle());
	}

	@Test
	public void aRecordThatEndsWithoutItsTerminatorIsNotComplete()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.standing(808)
			.walking(819)
			.unterminated());

		assertEquals(808, decoded.getStandingAnimation());
		assertFalse(decoded.isComplete());
	}

	@Test
	public void aRecordThatEndsMidFieldIsNotComplete()
	{
		// Opcode 13 with one byte of its two-byte value.
		NpcRecord decoded = NpcRecord.decode(record().u8(13).u8(3).unterminated());

		assertFalse(decoded.isComplete());
		assertFalse(decoded.hasWalkCycle());
	}

	@Test
	public void aStringWithNoTerminatorExhaustsTheCursorRatherThanLooping()
	{
		NpcRecordBytes bytes = record().u8(2);
		for (int i = 0; i < 8; i++)
		{
			bytes.u8('x');
		}

		NpcRecord decoded = NpcRecord.decode(bytes.unterminated());

		assertFalse(decoded.isComplete());
	}

	@Test
	public void aLengthThatOverrunsTheBufferIsNotComplete()
	{
		// Opcode 1 promising 255 models and supplying four bytes of them.
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(1).u8(255).u16(1).u16(2)
			.standing(808)
			.end());

		assertFalse(decoded.isComplete());
		assertEquals("nothing may be read from past the end of the buffer",
			NpcRecord.NO_ANIMATION, decoded.getStandingAnimation());
	}

	/**
	 * A cursor that could not serve one read must not serve the next one either.
	 *
	 * <p>An overrunning skip leaves the read head where it was, with real bytes still in
	 * front of it. Without the latch the parse resumes there — in the middle of a field it
	 * meant to step over — and reads whatever it lands on as an opcode. Here that is a
	 * perfectly well-formed opcode 13, so the follower would come away with a standing
	 * animation assembled out of a model list. The record is unreadable; the answer has to
	 * be nothing, not a plausible number.
	 */
	@Test
	public void aReadPastTheEndDoesNotResumeInsideTheBuffer()
	{
		NpcRecord decoded = NpcRecord.decode(record()
			.u8(1).u8(255)
			.standing(4660)
			.end());

		assertEquals("the bytes the overrun skipped past are not fields",
			NpcRecord.NO_ANIMATION, decoded.getStandingAnimation());
		assertFalse(decoded.isComplete());
	}

	@Test
	public void aTruncatedHeadIconEntryIsNotComplete()
	{
		NpcRecord decoded = NpcRecord.decode(record().u8(102).u8(0b1).u8(0x01).unterminated());

		assertFalse(decoded.isComplete());
	}

	@Test
	public void aParameterCountThatOverrunsTheBufferIsNotComplete()
	{
		NpcRecord decoded = NpcRecord.decode(record().u8(249).u8(40).u8(1).fill(3).unterminated());

		assertFalse(decoded.isComplete());
	}

	// --- What counts as a usable pair -----------------------------------------

	@Test
	public void aRecordWithNoWalkHasNoWalkCycle()
	{
		// Spria's shape: neither animation declared at all.
		assertFalse(NpcRecord.decode(record().end()).hasWalkCycle());

		// And the half-declared case, which is a figure that stands and then slides.
		assertFalse(NpcRecord.decode(record().standing(808).end()).hasWalkCycle());
	}

	@Test
	public void aRecordWithNoStandHasNoWalkCycle()
	{
		assertFalse(NpcRecord.decode(record().walking(819).end()).hasWalkCycle());
	}

	@Test
	public void aWalkThatIsAlsoTheStandHasNoWalkCycle()
	{
		// Krystilia's shape: one id in both fields, which is a standing pose played while
		// the figure covers ground.
		assertFalse(NpcRecord.decode(record().standing(808).walking(808).end()).hasWalkCycle());
	}

	@Test
	public void anAnimationIdOfSixtyFiveThousandFiveHundredAndThirtyFiveIsRefused()
	{
		// The unsigned spelling of "none". No sequence archive has an entry for it, so a
		// follower given it as a walk would load nothing and slide.
		assertFalse(NpcRecord.decode(record().standing(808).walking(65535).end()).hasWalkCycle());
		assertFalse(NpcRecord.decode(record().standing(65535).walking(819).end()).hasWalkCycle());
	}

	@Test
	public void theAnimationIdBoundsAreWhereTheyAreWritten()
	{
		assertFalse("negative is the absent marker", NpcRecord.isLoadable(-1));
		assertTrue("zero is a real sequence id", NpcRecord.isLoadable(0));
		assertTrue(NpcRecord.isLoadable(65_534));
		assertFalse(NpcRecord.isLoadable(65_535));

		// Pinned to the literal rather than to itself: a test written as
		// isLoadable(MAX_ANIMATION_ID) passes whatever the constant is changed to.
		assertEquals(65_534, NpcRecord.MAX_ANIMATION_ID);
		assertEquals(-1, NpcRecord.NO_ANIMATION);
	}

	/**
	 * Writes the fixture, then opcode 13 carrying {@link #SENTINEL}, then the terminator,
	 * and insists the sentinel comes back. See {@link NpcRecordBytes}.
	 */
	private static void assertWidth(NpcRecordBytes bytes)
	{
		NpcRecord decoded = NpcRecord.decode(bytes.standing(SENTINEL).end());

		assertEquals("the payload was not consumed exactly", SENTINEL, decoded.getStandingAnimation());
		assertTrue("the parse did not reach the terminator", decoded.isComplete());
	}
}
