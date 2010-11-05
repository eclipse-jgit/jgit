/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.notes;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.TreeFormatter;

/** A tree entry found in a note branch that isn't a valid note. */
class NonNoteEntry extends ObjectId {
	/** Name of the entry in the tree, in raw format. */
	private final byte[] name;

	/** Mode of the entry as parsed from the tree. */
	private final FileMode mode;

	/** The next non-note entry in the same tree, as defined by tree order. */
	NonNoteEntry next;

	NonNoteEntry(byte[] name, FileMode mode, AnyObjectId id) {
		super(id);
		this.name = name;
		this.mode = mode;
	}

	void format(TreeFormatter fmt) {
		fmt.append(name, mode, this);
	}

	int treeEntrySize() {
		return TreeFormatter.entrySize(mode, name.length);
	}

	int pathCompare(byte[] bBuf, int bPos, int bLen, FileMode bMode) {
		return pathCompare(name, 0, name.length, mode, //
				bBuf, bPos, bLen, bMode);
	}

	private static int pathCompare(final byte[] aBuf, int aPos, final int aEnd,
			final FileMode aMode, final byte[] bBuf, int bPos, final int bEnd,
			final FileMode bMode) {
		while (aPos < aEnd && bPos < bEnd) {
			int cmp = (aBuf[aPos++] & 0xff) - (bBuf[bPos++] & 0xff);
			if (cmp != 0)
				return cmp;
		}

		if (aPos < aEnd)
			return (aBuf[aPos] & 0xff) - lastPathChar(bMode);
		if (bPos < bEnd)
			return lastPathChar(aMode) - (bBuf[bPos] & 0xff);
		return 0;
	}

	private static int lastPathChar(final FileMode mode) {
		return FileMode.TREE.equals(mode.getBits()) ? '/' : '\0';
	}
}
