package com.matthewmariner.entourage;

import javax.annotation.Nullable;

/**
 * The two-and-a-bit fields this plugin needs out of one NPC's cache record: what it
 * stands with, what it walks with, and what it runs with.
 *
 * <p><b>Why this class exists at all.</b> {@link FollowerAppearance} can dress a figure
 * from any NPC id, because {@code NPCComposition} hands over {@code getModels()} and the
 * recolour pairs. It cannot animate one: that interface has no sequence accessor —
 * {@link EntourageAnimation}'s javadoc lists every method it does have — so
 * {@link EntourageFigure}'s twenty-three presets each carry a hand-verified pair instead.
 * An id typed into the settings box has no such pair, and a figure with no walk animation
 * is a body sliding across the ground, which is the single most visible way a follower
 * plugin can look broken. The cache record does carry the numbers. This reads them.
 *
 * <p><b>A pure function over {@code byte[]}, and that is the design rather than a
 * convenience.</b> Nothing here touches the client, allocates a client object or needs a
 * game running, so every branch below — including the malformed ones a live cache will
 * never produce — is reachable from a unit test. {@link NpcArchive} is the half that
 * talks to the client, and it is deliberately tiny for the same reason.
 *
 * <p><b>Transcribed from {@code net.runelite.cache.definitions.loaders.NpcLoader} at
 * 1.12.38</b>, read out of {@code net.runelite:cache:1.12.38}'s own sources jar rather
 * than from memory, with {@code EntityOpsLoader} and {@code net.runelite.cache.io.InputStream}
 * for the payload widths. The whole opcode table is here even though only four numbers
 * are kept, and it has to be: the record is a flat stream of opcode-then-payload with no
 * lengths and no index, so <b>the only way to reach opcode 13 is to have consumed every
 * byte in front of it correctly</b>. "Decode just the animations" is not an option the
 * format offers. That is also why every opcode below either reads its payload or is
 * explicitly a zero-byte flag — a missing {@code case} is not a field we ignore, it is
 * every later field misread.
 *
 * <p><b>The loader's revision switches are pinned to their defaults, which are the live
 * ones.</b> {@code NpcLoader} carries {@code rev210HeadIcons = true} and
 * {@code rev233 = true} unless {@code configureForRevision} says otherwise, so opcode 102
 * is the bitfield form and opcode 111 is the zero-byte {@code renderPriority} flag rather
 * than the pre-220 follower flag. Both of those are byte-width decisions, not semantic
 * ones: 111 reads nothing either way, and 102 has only the one live encoding.
 *
 * <p><b>An unrecognised opcode stops the parse where it stands.</b> The upstream loader
 * logs and carries on, which reads the next byte as an opcode and turns one unknown field
 * into a stream of nonsense. A future game update that adds an opcode would then be a
 * <i>wrong</i> animation pair rather than a missing one, and a wrong pair is a follower
 * that looks broken while the plugin believes it succeeded. Stopping keeps whatever was
 * read before the unknown byte — the animation opcodes are 13, 14, 17, 114 and 115, low
 * in the table — and anything incomplete fails {@link #hasWalkCycle()} and is refused out
 * loud. The same applies to a truncated record: reads past the end return zero and set
 * {@link #isComplete()} false rather than throwing, because this is called from a
 * game-tick handler.
 *
 * <p><b>Nothing here ever throws.</b> Not on {@code null}, not on an empty array, not on
 * a record that ends mid-field.
 */
final class NpcRecord
{
	/**
	 * What an animation field holds when the record does not declare one: {@code -1},
	 * which is {@code NpcDefinition}'s own default for all four of them.
	 */
	static final int NO_ANIMATION = -1;

	/**
	 * The largest animation id this plugin will hand to {@code Client.loadAnimation}:
	 * 65534.
	 *
	 * <p>Every animation opcode reads an <i>unsigned</i> short, so 65535 is representable
	 * and is the wire spelling of "none" everywhere else in this format — the config
	 * opcodes at 106 and 118 read it and write {@code -1}, in the loader, three lines
	 * apart from these. The animation opcodes do not do that conversion, so a record that
	 * declared 65535 would arrive here as a positive number that no sequence archive has
	 * an entry for. Left unbounded that is a follower whose walk silently fails to load
	 * and which therefore slides, which is exactly the outcome this class exists to
	 * prevent. Bounding it turns the same case into a refusal with a warning.
	 */
	static final int MAX_ANIMATION_ID = 65_534;

	private final int standingAnimation;
	private final int walkingAnimation;
	private final int runAnimation;
	private final boolean complete;

	private NpcRecord(int standingAnimation, int walkingAnimation, int runAnimation,
		boolean complete)
	{
		this.standingAnimation = standingAnimation;
		this.walkingAnimation = walkingAnimation;
		this.runAnimation = runAnimation;
		this.complete = complete;
	}

	/**
	 * Reads one NPC's cache record.
	 *
	 * @param data the record's bytes, already decompressed and decrypted by the client —
	 *             see {@link NpcArchive}. May be {@code null} or empty; both give a record
	 *             that declares nothing and is not {@link #isComplete()}.
	 * @return what the record says, never {@code null} and never a throw
	 */
	static NpcRecord decode(@Nullable byte[] data)
	{
		if (data == null)
		{
			return new NpcRecord(NO_ANIMATION, NO_ANIMATION, NO_ANIMATION, false);
		}

		Cursor in = new Cursor(data);

		int standing = NO_ANIMATION;
		int walking = NO_ANIMATION;
		int running = NO_ANIMATION;
		boolean complete = false;

		while (true)
		{
			int opcode = in.u8();
			if (!in.ok())
			{
				// Ran off the end looking for the next opcode. Checked before the
				// terminator test on purpose: an exhausted cursor answers zero, and zero
				// is also the terminator, so testing the value first would read a truncated
				// record as a well-formed one.
				break;
			}

			if (opcode == 0)
			{
				complete = true;
				break;
			}

			// Assigned rather than returned so that a later opcode overwrites an earlier
			// one, which is what the upstream loader does — opcodes 14 and 17 both write
			// the walk, and 114 and 115 both write the run.
			switch (opcode)
			{
				case 1: // models, one unsigned short each
					in.skipShorts(in.u8());
					break;

				case 2: // name
					in.skipString();
					break;

				case 12: // size, in tiles
					in.u8();
					break;

				case 13:
					standing = in.u16();
					break;

				case 14:
					walking = in.u16();
					break;

				case 15: // idle rotate left
				case 16: // idle rotate right
					in.u16();
					break;

				case 17: // walk, plus the three turning variants of it
					walking = in.u16();
					in.skipShorts(3);
					break;

				case 18: // category
					in.u16();
					break;

				case 30: // op 1..5. Each is a string; "Hidden" is a value, not a marker,
				case 31: // so it costs the same bytes as any other.
				case 32:
				case 33:
				case 34:
					in.skipString();
					break;

				case 40: // recolour: n (find, replace) pairs of shorts
				case 41: // retexture: the same shape
					in.skipShorts(in.u8() * 2);
					break;

				case 60: // chathead models, unsigned shorts
					in.skipShorts(in.u8());
					break;

				case 61: // models, ints — the wide form, for ids past 16 bits
				case 62: // chathead models, ints
					in.skipInts(in.u8());
					break;

				case 74: // the six combat stats
				case 75:
				case 76:
				case 77:
				case 78:
				case 79:
					in.u16();
					break;

				case 93: // not drawn on the minimap
					break;

				case 95: // combat level
				case 97: // width scale
				case 98: // height scale
					in.u16();
					break;

				case 99: // render priority 1
					break;

				case 100: // ambient, a signed byte
				case 101: // contrast, a signed byte
					in.u8();
					break;

				case 102:
					in.skipHeadIcons();
					break;

				case 103: // rotation speed
					in.u16();
					break;

				case 106: // varbit, varp, and the transform table
					in.skipShorts(2);
					in.skipShorts(in.u8() + 1);
					break;

				case 107: // not interactable
				case 109: // does not rotate
				case 111: // render priority 2 at rev233, which is the live default
					break;

				case 114:
					running = in.u16();
					break;

				case 115: // run, plus the three turning variants of it
					running = in.u16();
					in.skipShorts(3);
					break;

				case 116: // crawl
					in.u16();
					break;

				case 117: // crawl, plus its three turning variants
					in.skipShorts(4);
					break;

				case 118: // varbit, varp, a default, and the transform table
					in.skipShorts(3);
					in.skipShorts(in.u8() + 1);
					break;

				case 122: // is a follower
				case 123: // low priority follower ops
					break;

				case 124: // height
				case 126: // footprint size
					in.u16();
					break;

				case 129: // unknown flag
				case 130: // idle animation restarts
				case 145: // may hide when overlapped
					break;

				case 146: // overlap tint
					in.u16();
					break;

				case 147: // no z-buffer
					break;

				case 249:
					in.skipParams();
					break;

				case 251: // sub-op: index, sub id, text — two single bytes, then a string
					in.u8();
					in.u8();
					in.skipString();
					break;

				case 252: // conditional op: index, varp, varb, min, max, text
					in.u8();
					in.skipShorts(2);
					in.skipInts(2);
					in.skipString();
					break;

				case 253: // conditional sub-op: index, sub id, varp, varb, min, max, text
					in.u8();
					in.skipShorts(3);
					in.skipInts(2);
					in.skipString();
					break;

				default:
					// Unrecognised. Everything after this byte is unreadable — see the
					// class javadoc — so stop rather than guess. `complete` stays false.
					return new NpcRecord(standing, walking, running, false);
			}
		}

		// `complete` and not `complete && in.ok()`: the exhausted-cursor case is already the
		// break above, and saying it twice makes both copies unfalsifiable — mutate either
		// one and the other still answers. The loop is the single place that decides.
		return new NpcRecord(standing, walking, running, complete);
	}

	/** @return the sequence it holds while standing, or {@link #NO_ANIMATION} */
	int getStandingAnimation()
	{
		return standingAnimation;
	}

	/** @return the sequence it plays while walking, or {@link #NO_ANIMATION} */
	int getWalkingAnimation()
	{
		return walkingAnimation;
	}

	/**
	 * @return the sequence it plays while running, or {@link #NO_ANIMATION}. Plenty of
	 * NPCs declare none — they never run — and {@link Follower} falls back to the walk for
	 * those, which is what the client itself does.
	 */
	int getRunAnimation()
	{
		return runAnimation;
	}

	/**
	 * @return whether the parse reached the record's own terminator without running out
	 * of bytes or meeting an opcode it could not size. False means the numbers above are
	 * whatever was read before the parse stopped — they are never <i>wrong</i>, because
	 * everything in front of them was consumed correctly, but there may be more the record
	 * declared that this could not reach.
	 */
	boolean isComplete()
	{
		return complete;
	}

	/**
	 * Whether an NPC dressed from this record can be made to walk convincingly.
	 *
	 * <p><b>The three ways of failing are all real NPCs, not hypotheticals.</b> Spria
	 * declares neither a stand nor a walk — both are {@code -1} — and Krystilia declares
	 * the same id for both, which is a figure holding its standing pose while it covers
	 * ground. {@link EntourageFigure} rejects presets like those by name; an id typed into
	 * a settings box can be any of them, and the plugin has to notice on its own.
	 *
	 * @return true when both a stand and a walk are declared, they are different
	 * sequences, and both are ids {@code Client.loadAnimation} could resolve — see
	 * {@link #MAX_ANIMATION_ID}
	 */
	boolean hasWalkCycle()
	{
		return isLoadable(standingAnimation)
			&& isLoadable(walkingAnimation)
			&& standingAnimation != walkingAnimation;
	}

	/**
	 * @param animationId a sequence id out of this record
	 * @return whether it is one worth handing to {@code Client.loadAnimation}. Asking for
	 * a sequence outside this range costs a retry attempt in {@link Follower} and can
	 * never succeed.
	 */
	static boolean isLoadable(int animationId)
	{
		return animationId >= 0 && animationId <= MAX_ANIMATION_ID;
	}

	@Override
	public String toString()
	{
		return "NpcRecord{stand=" + standingAnimation
			+ ", walk=" + walkingAnimation
			+ ", run=" + runAnimation
			+ (complete ? "}" : ", incomplete}");
	}

	/**
	 * A read head over the record's bytes that answers zero instead of throwing.
	 *
	 * <p>Big-endian, matching {@code net.runelite.cache.io.InputStream}, which is a
	 * {@code ByteBuffer} in its default order. The difference from that class is the
	 * failure mode: a {@code ByteBuffer} throws {@code BufferUnderflowException} at the
	 * end of a truncated record, and this is reached from a game-tick handler where a
	 * throw costs the whole pass. {@link #ok()} is how the caller finds out instead.
	 *
	 * <p>Once exhausted it stays exhausted: every later read is refused rather than
	 * wrapping back into the buffer.
	 */
	private static final class Cursor
	{
		private final byte[] data;
		private int pos;
		private boolean underrun;

		Cursor(byte[] data)
		{
			this.data = data;
		}

		/** @return whether every read so far had the bytes it asked for */
		boolean ok()
		{
			return !underrun;
		}

		/**
		 * @return whether {@code bytes} more may be read, latching the underrun when they
		 * cannot. {@code pos} never exceeds {@code data.length}, so the sum cannot
		 * overflow.
		 *
		 * <p><b>Deliberately no {@code underrun ||} short-circuit.</b> A cursor that
		 * refused every later read once it had failed one would be a second copy of the
		 * rule the parse loop already enforces — it stops at the top of the next iteration
		 * on {@link #ok()} — and two copies of one guard means neither can be broken by
		 * itself, so neither can be tested. The loop is where "once exhausted, stop" is
		 * said, once. {@link #underrun} itself still latches: nothing ever clears it.
		 */
		private boolean take(int bytes)
		{
			if (pos + bytes > data.length)
			{
				underrun = true;
				return false;
			}
			return true;
		}

		/**
		 * @return the next byte, unsigned. Also used for the signed one-byte fields
		 * (ambient, contrast), whose values this plugin discards — only the width matters.
		 */
		int u8()
		{
			if (!take(1))
			{
				return 0;
			}
			return data[pos++] & 0xFF;
		}

		/** @return the next two bytes, big-endian, unsigned */
		int u16()
		{
			if (!take(2))
			{
				return 0;
			}
			int value = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
			pos += 2;
			return value;
		}

		/**
		 * @return the next byte without consuming it, signed, as {@code peek()} is
		 * upstream. Zero when there is no next byte, and deliberately <b>without</b>
		 * latching the underrun: every caller follows a peek with the {@link #skip} whose
		 * width the peek decided, and that skip cannot succeed on a buffer with nothing
		 * left in it. Latching here as well would be the same rule written twice, and
		 * neither copy could then be broken on its own.
		 */
		private int peek()
		{
			if (pos >= data.length)
			{
				return 0;
			}
			return data[pos];
		}

		private void skip(int bytes)
		{
			if (take(bytes))
			{
				pos += bytes;
			}
		}

		/**
		 * @param count how many unsigned shorts to discard. A count out of a one-byte
		 *              length is at most 256, so the multiplication cannot overflow.
		 */
		void skipShorts(int count)
		{
			skip(count * 2);
		}

		/** @param count how many four-byte ints to discard */
		void skipInts(int count)
		{
			skip(count * 4);
		}

		/**
		 * Discards a null-terminated string.
		 *
		 * <p>The upstream reader maps bytes 128..159 onto a Windows-1252 table; that
		 * changes the characters and not the length, and nothing here keeps the
		 * characters. A string with no terminator exhausts the cursor, which is the
		 * truncated-record case.
		 */
		void skipString()
		{
			while (ok() && u8() != 0)
			{
				// The terminator is the only byte that means anything here.
			}
		}

		/**
		 * Discards opcode 102's head icons.
		 *
		 * <p>A one-byte bitfield, then one entry per bit position <i>up to and including
		 * the highest set bit</i> — the upstream loop counts shifts until the field is
		 * zero, which is the bit length rather than the population count, and the two
		 * differ for any bitfield with a gap in it. Only the set positions carry bytes.
		 *
		 * <p>Each of those is a {@code readBigSmart2} — two bytes, or four when the first
		 * has its high bit set — followed by a {@code readUnsignedShortSmartMinusOne},
		 * which is one byte below 128 and two at or above it. Both are variable width, so
		 * this is the one opcode whose length cannot be worked out without reading it.
		 */
		void skipHeadIcons()
		{
			int bitfield = u8();

			int length = 0;
			for (int remaining = bitfield; remaining != 0; remaining >>= 1)
			{
				length++;
			}

			for (int i = 0; i < length && ok(); i++)
			{
				if ((bitfield & (1 << i)) == 0)
				{
					continue;
				}

				skip(peek() < 0 ? 4 : 2);
				skip((peek() & 0xFF) < 128 ? 1 : 2);
			}
		}

		/**
		 * Discards opcode 249's parameter map.
		 *
		 * <p>A one-byte count, then per entry a one-byte type, a three-byte id and a value
		 * whose width the type decides: a string, eight bytes for type 2, four otherwise.
		 * Transcribed from {@code InputStream.readParams} rather than from the shorter
		 * two-branch form the client's own reader is often described with — if those ever
		 * disagree this is the one the rest of this class was read from.
		 */
		void skipParams()
		{
			int count = u8();
			for (int i = 0; i < count && ok(); i++)
			{
				int type = u8();
				skip(3);

				if (type == 1)
				{
					skipString();
				}
				else
				{
					skip(type == 2 ? 8 : 4);
				}
			}
		}
	}
}
