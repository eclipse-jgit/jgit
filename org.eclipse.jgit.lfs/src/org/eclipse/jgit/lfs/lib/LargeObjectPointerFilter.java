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

/**
 * Detects Large File pointers, as described in [1] in Git repository.
 *
 * [1] https://github.com/github/git-lfs/blob/master/docs/spec.md
 */
public class LargeObjectPointerFilter extends TreeFilter {
	private static int BUFFER_SIZE = 100;

	private static final byte[] VERSION_LINE = "version https://hawser.github.com/spec/v1\n" //$NON-NLS-1$
			.getBytes(UTF_8);

	private static final byte[][] REQUIRED_KEYS = new byte[][] {
			"oid ".getBytes(UTF_8), //$NON-NLS-1$
			"size ".getBytes(UTF_8) //$NON-NLS-1$
	};

	@Override
	public boolean include(TreeWalk walker) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (walker.isSubtree()) {
			return walker.isRecursive();
		}
		ObjectId objectId = walker.getObjectId(0);
		ObjectLoader object = walker.getObjectReader().open(objectId);
		if (object.isLarge()) {
			return false;
		}

		// ok, so it can be a LFS pointer; read first line
		byte[] buffer = new byte[BUFFER_SIZE];
		ObjectStream stream = object.openStream();
		int read = stream.read(buffer, 0, VERSION_LINE.length);

		// validate first line
		if (read < VERSION_LINE.length) {
			return false;
		}
		for (int i = 0; i < VERSION_LINE.length; i++) {
			if (buffer[i] != VERSION_LINE[i]) {
				return false;
			}
		}

		// it looks like LFS pointer, lets see if it has required keys
		int i = 0;
		int foundKeys = 0;
		read = stream.read(buffer);
		for (int r = 0; r < REQUIRED_KEYS.length; r++) {
			int found = 0;
			int keyIndex = 0;
			byte[] key = REQUIRED_KEYS[r];
			for (; read > 0 && read <= BUFFER_SIZE; i++, keyIndex++) {
				if (i == read) { // fill up the buffer
					i = 0;
					read = stream.read(buffer);
				}
				if (found == key.length) {
					foundKeys++;
					break;
				} else if (keyIndex < key.length
						&& buffer[i] == key[keyIndex]) {
					found++;
				} else {
					// skip all characters until new line
					for (; buffer[i] != '\n'; i++) {
						if (i == read) { // fill up the buffer
							i = 0;
							read = stream.read(buffer);
						}
					}
					keyIndex = -1;
				}
			}
		}

		return foundKeys == REQUIRED_KEYS.length;
	}

	@Override
	public boolean shouldBeRecursive() {
		return false;
	}

	@Override
	public TreeFilter clone() {
		return this;
	}
}
