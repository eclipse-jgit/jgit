/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.stream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * An {@link InputStream} that applies a binary delta to a base on the fly.
 * <p>
 * Delta application to a base needs random access to the base data. The delta
 * is expressed as a sequence of copy and insert instructions. A copy
 * instruction has the form "COPY fromOffset length" and says "copy length bytes
 * from the base, starting at offset fromOffset, to the result". An insert
 * instruction has the form "INSERT length" followed by length bytes and says
 * "copy the next length bytes from the delta to the result".
 * </p>
 * <p>
 * These instructions are generated using a content-defined chunking algorithm
 * (currently C git uses the standard Rabin variant; but there are others that
 * could be used) that identifies equal chunks. It is entirely possible that a
 * later copy instruction has a fromOffset that is before the fromOffset of an
 * earlier copy instruction.
 * </p>
 * <p>
 * This makes it impossible to stream the base.
 * </p>
 * <p>
 * JGit is limited to 2GB maximum size for the base since array indices are
 * signed 32bit values.
 *
 * @since 5.12
 */
public class BinaryDeltaInputStream extends InputStream {

	private final byte[] base;

	private final InputStream delta;

	private long resultLength;

	private long toDeliver = -1;

	private int fromBase;

	private int fromDelta;

	private int baseOffset = -1;

	/**
	 * Creates a new {@link BinaryDeltaInputStream} that applies {@code delta}
	 * to {@code base}.
	 *
	 * @param base
	 *            data to apply the delta to
	 * @param delta
	 *            {@link InputStream} delivering the delta to apply
	 */
	public BinaryDeltaInputStream(byte[] base, InputStream delta) {
		this.base = base;
		this.delta = delta;
	}

	@Override
	public int read() throws IOException {
		int b = readNext();
		if (b >= 0) {
			toDeliver--;
		}
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return super.read(b, off, len);
	}

	private void initialize() throws IOException {
		long baseSize = readVarInt(delta);
		if (baseSize > Integer.MAX_VALUE || baseSize < 0
				|| (int) baseSize != base.length) {
			throw new IOException(MessageFormat.format(
					JGitText.get().binaryDeltaBaseLengthMismatch,
					Integer.valueOf(base.length), Long.valueOf(baseSize)));
		}
		resultLength = readVarInt(delta);
		if (resultLength < 0) {
			throw new StreamCorruptedException(
					JGitText.get().binaryDeltaInvalidResultLength);
		}
		toDeliver = resultLength;
		baseOffset = 0;
	}

	private int readNext() throws IOException {
		if (baseOffset < 0) {
			initialize();
		}
		if (fromBase > 0) {
			fromBase--;
			return base[baseOffset++] & 0xFF;
		} else if (fromDelta > 0) {
			fromDelta--;
			return delta.read();
		}
		int command = delta.read();
		if (command < 0) {
			return -1;
		}
		if ((command & 0x80) != 0) {
			// Decode offset and length to read from base
			long copyOffset = 0;
			for (int i = 1, shift = 0; i < 0x10; i *= 2, shift += 8) {
				if ((command & i) != 0) {
					copyOffset |= ((long) next(delta)) << shift;
				}
			}
			int copySize = 0;
			for (int i = 0x10, shift = 0; i < 0x80; i *= 2, shift += 8) {
				if ((command & i) != 0) {
					copySize |= next(delta) << shift;
				}
			}
			if (copySize == 0) {
				copySize = 0x10000;
			}
			if (copyOffset > base.length - copySize) {
				throw new StreamCorruptedException(MessageFormat.format(
						JGitText.get().binaryDeltaInvalidOffset,
						Long.valueOf(copyOffset), Integer.valueOf(copySize)));
			}
			baseOffset = (int) copyOffset;
			fromBase = copySize;
			return readNext();
		} else if (command != 0) {
			// The next 'command' bytes come from the delta
			fromDelta = command - 1;
			return delta.read();
		} else {
			// Zero is reserved
			throw new StreamCorruptedException(
					JGitText.get().unsupportedCommand0);
		}
	}

	private int next(InputStream in) throws IOException {
		int b = in.read();
		if (b < 0) {
			throw new EOFException();
		}
		return b;
	}

	private long readVarInt(InputStream in) throws IOException {
		long val = 0;
		int shift = 0;
		int b;
		do {
			b = next(in);
			val |= ((long) (b & 0x7f)) << shift;
			shift += 7;
		} while ((b & 0x80) != 0);
		return val;
	}

	/**
	 * Tells the expected size of the final result.
	 *
	 * @return the size
	 * @throws IOException
	 *             if the size cannot be determined from {@code delta}
	 */
	public long getExpectedResultSize() throws IOException {
		if (baseOffset < 0) {
			initialize();
		}
		return resultLength;
	}

	/**
	 * Tells whether the delta has been fully consumed, and the expected number
	 * of bytes for the combined result have been read from this
	 * {@link BinaryDeltaInputStream}.
	 *
	 * @return whether delta application was successful
	 */
	public boolean isFullyConsumed() {
		try {
			return toDeliver == 0 && delta.read() < 0;
		} catch (IOException e) {
			return toDeliver == 0;
		}
	}

	@Override
	public void close() throws IOException {
		delta.close();
	}
}
