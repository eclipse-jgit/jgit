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

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.lib.FileMode.REGULAR_FILE;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectInserter.Formatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.TreeFormatter;

/**
 * A note tree holding only notes, with no subtrees.
 *
 * The leaf bucket contains on average less than 256 notes, all of whom share
 * the same leading prefix. If a notes branch has less than 256 notes, the top
 * level tree of the branch should be a LeafBucket. Once a notes branch has more
 * than 256 notes, the root should be a {@link FanoutBucket} and the LeafBucket
 * will appear only as a cell of a FanoutBucket.
 *
 * Entries within the LeafBucket are stored sorted by ObjectId, and lookup is
 * performed using binary search. As the entry list should contain fewer than
 * 256 elements, the average number of compares to find an element should be
 * less than 8 due to the O(log N) lookup behavior.
 *
 * A LeafBucket must be parsed from a tree object by {@link NoteParser}.
 */
class LeafBucket extends InMemoryNoteBucket {
	static final int MAX_SIZE = 256;

	/** All note blobs in this bucket, sorted sequentially. */
	private Note[] notes;

	/** Number of items in {@link #notes}. */
	private int cnt;

	LeafBucket(int prefixLen) {
		super(prefixLen);
		notes = new Note[4];
	}

	private int search(AnyObjectId objId) {
		int low = 0;
		int high = cnt;
		while (low < high) {
			int mid = (low + high) >>> 1;
			int cmp = objId.compareTo(notes[mid]);
			if (cmp < 0)
				high = mid;
			else if (cmp == 0)
				return mid;
			else
				low = mid + 1;
		}
		return -(low + 1);
	}

	@Override
	Note getNote(AnyObjectId objId, ObjectReader or) {
		int idx = search(objId);
		return 0 <= idx ? notes[idx] : null;
	}

	Note get(int index) {
		return notes[index];
	}

	int size() {
		return cnt;
	}

	@Override
	Iterator<Note> iterator(AnyObjectId objId, ObjectReader reader) {
		return new Iterator<Note>() {
			private int idx;

			@Override
			public boolean hasNext() {
				return idx < cnt;
			}

			@Override
			public Note next() {
				if (hasNext())
					return notes[idx++];
				else
					throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	int estimateSize(AnyObjectId noteOn, ObjectReader or) throws IOException {
		return cnt;
	}

	@Override
	InMemoryNoteBucket set(AnyObjectId noteOn, AnyObjectId noteData,
			ObjectReader or) throws IOException {
		int p = search(noteOn);
		if (0 <= p) {
			if (noteData != null) {
				notes[p].setData(noteData.copy());
				return this;

			} else {
				System.arraycopy(notes, p + 1, notes, p, cnt - p - 1);
				cnt--;
				return 0 < cnt ? this : null;
			}

		} else if (noteData != null) {
			if (shouldSplit()) {
				return split().set(noteOn, noteData, or);

			} else {
				growIfFull();
				p = -(p + 1);
				if (p < cnt)
					System.arraycopy(notes, p, notes, p + 1, cnt - p);
				notes[p] = new Note(noteOn, noteData.copy());
				cnt++;
				return this;
			}

		} else {
			return this;
		}
	}

	@Override
	ObjectId writeTree(ObjectInserter inserter) throws IOException {
		return inserter.insert(build());
	}

	@Override
	ObjectId getTreeId() {
		try (Formatter f = new ObjectInserter.Formatter()) {
			return f.idFor(build());
		}
	}

	private TreeFormatter build() {
		byte[] nameBuf = new byte[OBJECT_ID_STRING_LENGTH];
		int nameLen = OBJECT_ID_STRING_LENGTH - prefixLen;
		TreeFormatter fmt = new TreeFormatter(treeSize(nameLen));
		NonNoteEntry e = nonNotes;

		for (int i = 0; i < cnt; i++) {
			Note n = notes[i];

			n.copyTo(nameBuf, 0);

			while (e != null
					&& e.pathCompare(nameBuf, prefixLen, nameLen, REGULAR_FILE) < 0) {
				e.format(fmt);
				e = e.next;
			}

			fmt.append(nameBuf, prefixLen, nameLen, REGULAR_FILE, n.getData());
		}

		for (; e != null; e = e.next)
			e.format(fmt);
		return fmt;
	}

	private int treeSize(int nameLen) {
		int sz = cnt * TreeFormatter.entrySize(REGULAR_FILE, nameLen);
		for (NonNoteEntry e = nonNotes; e != null; e = e.next)
			sz += e.treeEntrySize();
		return sz;
	}

	void parseOneEntry(AnyObjectId noteOn, AnyObjectId noteData) {
		growIfFull();
		notes[cnt++] = new Note(noteOn, noteData.copy());
	}

	@Override
	InMemoryNoteBucket append(Note note) {
		if (shouldSplit()) {
			return split().append(note);

		} else {
			growIfFull();
			notes[cnt++] = note;
			return this;
		}
	}

	private void growIfFull() {
		if (notes.length == cnt) {
			Note[] n = new Note[notes.length * 2];
			System.arraycopy(notes, 0, n, 0, cnt);
			notes = n;
		}
	}

	private boolean shouldSplit() {
		return MAX_SIZE <= cnt && prefixLen + 2 < OBJECT_ID_STRING_LENGTH;
	}

	FanoutBucket split() {
		FanoutBucket n = new FanoutBucket(prefixLen);
		for (int i = 0; i < cnt; i++)
			n.append(notes[i]);
		n.nonNotes = nonNotes;
		return n;
	}
}
