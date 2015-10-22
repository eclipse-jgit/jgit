/*
 * Copyright (C) 2015, Dariusz Luksza <dariusz@luksza.org>
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

package org.eclipse.jgit.lfs.lib;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Detects Large File pointers, as described in [1] in Git repository.
 *
 * [1] https://github.com/github/git-lfs/blob/master/docs/spec.md
 */
public class LargeObjectPointerFilter extends TreeFilter {

	private static final byte[] VERSION_LINE = "version https://git-lfs.github.com/spec/v1\n" //$NON-NLS-1$
			.getBytes(UTF_8);

	private static final byte[] VERSION_LINE_LEGACY = "version https://hawser.github.com/spec/v1\n" //$NON-NLS-1$
			.getBytes(UTF_8);

	private static final byte[][] VERSION_LINES = { VERSION_LINE,
			VERSION_LINE_LEGACY };

	private static final byte[][] REQUIRED_KEYS = new byte[][] {
			"oid sha256:".getBytes(UTF_8), //$NON-NLS-1$
			"size ".getBytes(UTF_8) //$NON-NLS-1$
	};

	private static final int OID_INDEX = 0;

	private static final int SIZE_INDEX = 1;

	private LargeObjectPointer pointer;

	/**
	 * @return {@link LargeObjectPointer} or {@code null}
	 */
	public LargeObjectPointer getPointer() {
		return pointer;
	}

	private boolean validVersionLine(int read, byte[] line) {
		for (int i = 0; i < VERSION_LINES.length; i++) {
			byte[] version = VERSION_LINES[i];
			if (read == line.length
					&& RawParseUtils.match(line, 0, version) < 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean include(TreeWalk walker) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		pointer = null;
		if (walker.isSubtree()) {
			return walker.isRecursive();
		}
		ObjectId objectId = walker.getObjectId(0);
		ObjectLoader object = walker.getObjectReader().open(objectId);
		if (object.isLarge()) {
			return false;
		}

		// ok, so it can be a LFS pointer; read first line
		ObjectStream stream = object.openStream();
		byte[] buffer = new byte[VERSION_LINE_LEGACY.length];
		int read = stream.read(buffer);

		// validate first line
		if (!validVersionLine(read, buffer)) {
			return false;
		}

		// it looks like LFS pointer, lets see if it has all required keys
		int line = 2; // skip version line
		int foundKeys = 0;
		byte[][] values = new byte[REQUIRED_KEYS.length][];
		buffer = object.getCachedBytes();
		IntList lineMap = RawParseUtils.lineMap(buffer, 0, buffer.length);
		int lineMapSize = lineMap.size() - 1;
		for (int r = 0; r < REQUIRED_KEYS.length; r++) {
			byte[] key = REQUIRED_KEYS[r];
			for (; line < lineMapSize; line++) {
				int lineStart = lineMap.get(line);
				int match = RawParseUtils.match(buffer, lineStart, key);
				if (match > 0) {
					foundKeys++;
					int len = lineMap.get(line + 1) - match - 1;
					values[r] = new byte[len];
					System.arraycopy(buffer, match, values[r], 0, len);
					break;
				}
			}
		}

		if (foundKeys == REQUIRED_KEYS.length) {
			LongObjectId oid = LongObjectId.fromString(values[OID_INDEX], 0);
			int size = RawParseUtils.parseBase10(values[SIZE_INDEX], 0,
					new MutableInteger());
			pointer = new LargeObjectPointer(oid, size);
			return true;
		}
		return false;
	}

	@Override
	public boolean shouldBeRecursive() {
		return false;
	}

	@Override
	public TreeFilter clone() {
		return new LargeObjectPointerFilter();
	}
}
