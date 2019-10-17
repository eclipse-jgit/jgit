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
import static org.eclipse.jgit.lib.Constants.encodeASCII;
import static org.eclipse.jgit.lib.FileMode.TREE;
import static org.eclipse.jgit.util.RawParseUtils.parseHexInt4;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/** Custom tree parser to select note bucket type and load it. */
final class NoteParser extends CanonicalTreeParser {
	/**
	 * Parse a tree object into a {@link NoteBucket} instance.
	 *
	 * The type of note tree is automatically detected by examining the items
	 * within the tree, and allocating the proper storage type based on the
	 * first note-like entry encountered. Since the method parses by guessing
	 * the type on the first element, malformed note trees can be read as the
	 * wrong type of tree.
	 *
	 * This method is not recursive, it parses the one tree given to it and
	 * returns the bucket. If there are subtrees for note storage, they are
	 * setup as lazy pointers that will be resolved at a later time.
	 *
	 * @param prefix
	 *            common hex digits that all notes within this tree share. The
	 *            root tree has {@code prefix.length() == 0}, the first-level
	 *            subtrees should be {@code prefix.length()==2}, etc.
	 * @param treeId
	 *            the tree to read from the repository.
	 * @param reader
	 *            reader to access the tree object.
	 * @return bucket to holding the notes of the specified tree.
	 * @throws IOException
	 *             {@code treeId} cannot be accessed.
	 */
	static InMemoryNoteBucket parse(AbbreviatedObjectId prefix,
			final ObjectId treeId, final ObjectReader reader)
			throws IOException {
		return new NoteParser(prefix, reader, treeId).parse();
	}

	private final int prefixLen;

	private final int pathPadding;

	private NonNoteEntry firstNonNote;

	private NonNoteEntry lastNonNote;

	private NoteParser(AbbreviatedObjectId prefix, ObjectReader r, ObjectId t)
			throws IncorrectObjectTypeException, IOException {
		super(encodeASCII(prefix.name()), r, t);
		prefixLen = prefix.length();

		// Our path buffer has a '/' that we don't want after the prefix.
		// Drop it by shifting the path down one position.
		pathPadding = 0 < prefixLen ? 1 : 0;
		if (0 < pathPadding)
			System.arraycopy(path, 0, path, pathPadding, prefixLen);
	}

	private InMemoryNoteBucket parse() {
		InMemoryNoteBucket r = parseTree();
		r.nonNotes = firstNonNote;
		return r;
	}

	private InMemoryNoteBucket parseTree() {
		for (; !eof(); next(1)) {
			if (pathLen == pathPadding + OBJECT_ID_STRING_LENGTH && isHex())
				return parseLeafTree();

			else if (getNameLength() == 2 && isHex() && isTree())
				return parseFanoutTree();

			else
				storeNonNote();
		}

		// If we cannot determine the style used, assume its a leaf.
		return new LeafBucket(prefixLen);
	}

	private LeafBucket parseLeafTree() {
		final LeafBucket leaf = new LeafBucket(prefixLen);
		final MutableObjectId idBuf = new MutableObjectId();

		for (; !eof(); next(1)) {
			if (parseObjectId(idBuf))
				leaf.parseOneEntry(idBuf, getEntryObjectId());
			else
				storeNonNote();
		}

		return leaf;
	}

	private boolean parseObjectId(MutableObjectId id) {
		if (pathLen == pathPadding + OBJECT_ID_STRING_LENGTH) {
			try {
				id.fromString(path, pathPadding);
				return true;
			} catch (ArrayIndexOutOfBoundsException notHex) {
				return false;
			}
		}
		return false;
	}

	private FanoutBucket parseFanoutTree() {
		final FanoutBucket fanout = new FanoutBucket(prefixLen);

		for (; !eof(); next(1)) {
			final int cell = parseFanoutCell();
			if (0 <= cell)
				fanout.setBucket(cell, getEntryObjectId());
			else
				storeNonNote();
		}

		return fanout;
	}

	private int parseFanoutCell() {
		if (getNameLength() == 2 && isTree()) {
			try {
				return (parseHexInt4(path[pathOffset + 0]) << 4)
						| parseHexInt4(path[pathOffset + 1]);
			} catch (ArrayIndexOutOfBoundsException notHex) {
				return -1;
			}
		}
		return -1;
	}

	private void storeNonNote() {
		ObjectId id = getEntryObjectId();
		FileMode fileMode = getEntryFileMode();

		byte[] name = new byte[getNameLength()];
		getName(name, 0);

		NonNoteEntry ent = new NonNoteEntry(name, fileMode, id);
		if (firstNonNote == null)
			firstNonNote = ent;
		if (lastNonNote != null)
			lastNonNote.next = ent;
		lastNonNote = ent;
	}

	private boolean isTree() {
		return TREE.equals(mode);
	}

	private boolean isHex() {
		try {
			for (int i = pathOffset; i < pathLen; i++)
				parseHexInt4(path[i]);
			return true;
		} catch (ArrayIndexOutOfBoundsException fail) {
			return false;
		}
	}
}
