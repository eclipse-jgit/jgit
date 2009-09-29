/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.patch;

import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.eclipse.jgit.util.RawParseUtils.match;
import static org.eclipse.jgit.util.RawParseUtils.nextLF;
import static org.eclipse.jgit.util.RawParseUtils.parseBase10;

/** Part of a "GIT binary patch" to describe the pre-image or post-image */
public class BinaryHunk {
	private static final byte[] LITERAL = encodeASCII("literal ");

	private static final byte[] DELTA = encodeASCII("delta ");

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

	BinaryHunk(final FileHeader fh, final int offset) {
		file = fh;
		startOffset = offset;
	}

	/** @return header for the file this hunk applies to */
	public FileHeader getFileHeader() {
		return file;
	}

	/** @return the byte array holding this hunk's patch script. */
	public byte[] getBuffer() {
		return file.buf;
	}

	/** @return offset the start of this hunk in {@link #getBuffer()}. */
	public int getStartOffset() {
		return startOffset;
	}

	/** @return offset one past the end of the hunk in {@link #getBuffer()}. */
	public int getEndOffset() {
		return endOffset;
	}

	/** @return type of this binary hunk */
	public Type getType() {
		return type;
	}

	/** @return inflated size of this hunk's data */
	public int getSize() {
		return length;
	}

	int parseHunk(int ptr, final int end) {
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
