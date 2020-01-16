/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.patch;

import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.eclipse.jgit.util.RawParseUtils.match;
import static org.eclipse.jgit.util.RawParseUtils.nextLF;
import static org.eclipse.jgit.util.RawParseUtils.parseBase10;

/**
 * Part of a "GIT binary patch" to describe the pre-image or post-image
 */
public class BinaryHunk {
	private static final byte[] LITERAL = encodeASCII("literal "); //$NON-NLS-1$

	private static final byte[] DELTA = encodeASCII("delta "); //$NON-NLS-1$

	/** Type of information stored in a binary hunk. */
	public static enum Type {
		/** The full content is stored, deflated. */
		LITERAL_DEFLATED,

		/** A Git pack-style delta is stored, deflated. */
		DELTA_DEFLATED;
	}

	private final FileHeader file;

	/** Offset within {@link #file}.buf to the "literal" or "delta " line. */
	final int startOffset;

	/** Position 1 past the end of this hunk within {@link #file}'s buf. */
	int endOffset;

	/** Type of the data meaning. */
	private Type type;

	/** Inflated length of the data. */
	private int length;

	BinaryHunk(FileHeader fh, int offset) {
		file = fh;
		startOffset = offset;
	}

	/**
	 * Get header for the file this hunk applies to.
	 *
	 * @return header for the file this hunk applies to.
	 */
	public FileHeader getFileHeader() {
		return file;
	}

	/**
	 * Get the byte array holding this hunk's patch script.
	 *
	 * @return the byte array holding this hunk's patch script.
	 */
	public byte[] getBuffer() {
		return file.buf;
	}

	/**
	 * Get offset the start of this hunk in {@link #getBuffer()}.
	 *
	 * @return offset the start of this hunk in {@link #getBuffer()}.
	 */
	public int getStartOffset() {
		return startOffset;
	}

	/**
	 * Get offset one past the end of the hunk in {@link #getBuffer()}.
	 *
	 * @return offset one past the end of the hunk in {@link #getBuffer()}.
	 */
	public int getEndOffset() {
		return endOffset;
	}

	/**
	 * Get type of this binary hunk.
	 *
	 * @return type of this binary hunk.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Get inflated size of this hunk's data.
	 *
	 * @return inflated size of this hunk's data.
	 */
	public int getSize() {
		return length;
	}

	int parseHunk(int ptr, int end) {
		final byte[] buf = file.buf;

		if (match(buf, ptr, LITERAL) >= 0) {
			type = Type.LITERAL_DEFLATED;
			length = parseBase10(buf, ptr + LITERAL.length, null);

		} else if (match(buf, ptr, DELTA) >= 0) {
			type = Type.DELTA_DEFLATED;
			length = parseBase10(buf, ptr + DELTA.length, null);

		} else {
			// Not a valid binary hunk. Signal to the caller that
			// we cannot parse any further and that this line should
			// be treated otherwise.
			//
			return -1;
		}
		ptr = nextLF(buf, ptr);

		// Skip until the first blank line; that is the end of the binary
		// encoded information in this hunk. To save time we don't do a
		// validation of the binary data at this point.
		//
		while (ptr < end) {
			final boolean empty = buf[ptr] == '\n';
			ptr = nextLF(buf, ptr);
			if (empty)
				break;
		}

		return ptr;
	}
}
