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

import java.io.IOException;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * A note tree holding only note subtrees, each named using a 2 digit hex name.
 *
 * The fanout buckets/trees contain on average 256 subtrees, naming the subtrees
 * by a slice of the ObjectId contained within them, from "00" through "ff".
 *
 * Each fanout bucket has a {@link #prefixLen} that defines how many digits it
 * skips in an ObjectId before it gets to the digits matching {@link #table}.
 *
 * The root tree has {@code prefixLen == 0}, and thus does not skip any digits.
 * For ObjectId "c0ffee...", the note (if it exists) will be stored within the
 * bucket {@code table[0xc0]}.
 *
 * The first level tree has {@code prefixLen == 2}, and thus skips the first two
 * digits. For the same example "c0ffee..." object, its note would be found
 * within the {@code table[0xff]} bucket (as first 2 digits "c0" are skipped).
 *
 * Each subtree is loaded on-demand, reducing startup latency for reads that
 * only need to examine a few objects. However, due to the rather uniform
 * distribution of the SHA-1 hash that is used for ObjectIds, accessing 256
 * objects is very likely to load all of the subtrees into memory.
 *
 * A FanoutBucket must be parsed from a tree object by {@link NoteParser}.
 */
class FanoutBucket extends InMemoryNoteBucket {
	/**
	 * Fan-out table similar to the PackIndex structure.
	 *
	 * Notes for an object are stored within the sub-bucket that is held here as
	 * {@code table[ objectId.getByte( prefixLen / 2 ) ]}. If the slot is null
	 * there are no notes with that prefix.
	 */
	private final NoteBucket[] table;

	FanoutBucket(int prefixLen) {
		super(prefixLen);
		table = new NoteBucket[256];
	}

	void parseOneEntry(int cell, ObjectId id) {
		table[cell] = new LazyNoteBucket(id);
	}

	@Override
	ObjectId get(AnyObjectId objId, ObjectReader or) throws IOException {
		NoteBucket b = table[cell(objId)];
		return b != null ? b.get(objId, or) : null;
	}

	private int cell(AnyObjectId id) {
		return id.getByte(prefixLen >> 1);
	}

	private class LazyNoteBucket extends NoteBucket {
		private final ObjectId treeId;

		LazyNoteBucket(ObjectId treeId) {
			this.treeId = treeId;
		}

		@Override
		ObjectId get(AnyObjectId objId, ObjectReader or) throws IOException {
			return load(objId, or).get(objId, or);
		}

		private NoteBucket load(AnyObjectId objId, ObjectReader or)
				throws IOException {
			AbbreviatedObjectId p = objId.abbreviate(prefixLen + 2);
			NoteBucket self = NoteParser.parse(p, treeId, or);
			table[cell(objId)] = self;
			return self;
		}
	}
}
