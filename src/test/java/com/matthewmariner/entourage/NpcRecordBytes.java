package com.matthewmariner.entourage;

import java.io.ByteArrayOutputStream;

/**
 * Builds the bytes of one NPC cache record, the way the cache stores it.
 *
 * <p><b>Every test in {@link NpcRecordTest} that proves a width works the same way:</b>
 * write the opcode under test with a payload, then write opcode 13 with a value nothing
 * else in the record uses, then the terminator — and assert the standing animation comes
 * back as that value. A skip that is one byte short or one byte long puts the cursor
 * inside the wrong field, so 13 is read as something else and the assertion fails. That
 * makes "the payload width is right" observable from outside, which a decoder that
 * discards the payload otherwise is not.
 *
 * <p>Big-endian, matching {@code net.runelite.cache.io.InputStream}.
 */
final class NpcRecordBytes
{
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	static NpcRecordBytes record()
	{
		return new NpcRecordBytes();
	}

	/** One raw byte — an opcode, a length, or a one-byte payload field. */
	NpcRecordBytes u8(int value)
	{
		out.write(value & 0xFF);
		return this;
	}

	/** Two bytes, big-endian. */
	NpcRecordBytes u16(int value)
	{
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
		return this;
	}

	/** Four bytes, big-endian. */
	NpcRecordBytes i32(int value)
	{
		out.write((value >> 24) & 0xFF);
		out.write((value >> 16) & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
		return this;
	}

	/** A string as the cache stores one: the characters, then a zero byte. */
	NpcRecordBytes str(String value)
	{
		for (int i = 0; i < value.length(); i++)
		{
			out.write(value.charAt(i) & 0xFF);
		}
		out.write(0);
		return this;
	}

	/** Filler, for the payloads whose contents are never read. */
	NpcRecordBytes fill(int count)
	{
		for (int i = 0; i < count; i++)
		{
			out.write(0xAB);
		}
		return this;
	}

	/** Opcode 13 with a value, which is what the width tests look for on the far side. */
	NpcRecordBytes standing(int animationId)
	{
		return u8(13).u16(animationId);
	}

	/** Opcode 14 with a value. */
	NpcRecordBytes walking(int animationId)
	{
		return u8(14).u16(animationId);
	}

	/** The record's own terminator. */
	byte[] end()
	{
		out.write(0);
		return out.toByteArray();
	}

	/** The bytes so far, with no terminator — a truncated record. */
	byte[] unterminated()
	{
		return out.toByteArray();
	}
}
