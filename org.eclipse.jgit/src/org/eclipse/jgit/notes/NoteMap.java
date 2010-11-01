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

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Index of notes from a note branch.
 *
 * This class is not thread-safe, and relies on an {@link ObjectReader} that it
 * borrows/shares with the caller.
 *
 * The current implementation is not very efficient. During load it recursively
 * scans the entire note branch and indexes all annotated objects. If there are
 * more than 256 notes in the branch, and not all of them will be accessed by
 * the caller, this aggressive up-front loading probably takes too much time.
 */
public class NoteMap {
	/**
	 * Load a collection of notes from a branch.
	 *
	 * @param reader
	 *            reader to scan the note branch with. This reader may be
	 *            retained by the NoteMap for the life of the map in order to
	 *            support lazy loading of entries.
	 * @param commit
	 *            the revision of the note branch to read.
	 * @return the note map read from the commit.
	 * @throws IOException
	 *             the repository cannot be accessed through the reader.
	 * @throws CorruptObjectException
	 *             a tree object is corrupt and cannot be read.
	 * @throws IncorrectObjectTypeException
	 *             a tree object wasn't actually a tree.
	 * @throws MissingObjectException
	 *             a reference tree object doesn't exist.
	 */
	public static NoteMap load(ObjectReader reader, RevCommit commit)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		NoteMap map = new NoteMap(reader);
		map.load(commit);
		return map;
	}

	/** Borrowed reader to access the repository. */
	private final ObjectReader reader;

	/** Commit of the note branch that was loaded into this map. */
	private ObjectId commit;

	/** All of the notes that have been loaded. */
	private ObjectIdSubclassMap<Note> notes = new ObjectIdSubclassMap<Note>();

	private NoteMap(ObjectReader reader) {
		this.reader = reader;
	}

	/**
	 * Get the revision of the note branch that is loaded in this map.
	 *
	 * This is the id of the commit that was passed to the constructor. Callers
	 * may use this to determine when the note map is out-dated and should be
	 * discarded and reloaded with new content.
	 *
	 * @return revision of the note branch that was loaded in the map.
	 */
	public ObjectId getCommitId() {
		return commit;
	}

	/**
	 * Lookup a note for a specific ObjectId.
	 *
	 * @param id
	 *            the object to look for.
	 * @return the note's blob ObjectId, or null if no note exists.
	 */
	public ObjectId get(AnyObjectId id) {
		Note note = notes.get(id);
		return note != null ? note.getData() : null;
	}

	/**
	 * Determine if a note exists for the specified ObjectId.
	 *
	 * @param id
	 *            the object to look for.
	 * @return true if a note exists; false if there is no note.
	 */
	public boolean contains(AnyObjectId id) {
		return get(id) != null;
	}

	/**
	 * Open and return the content of an object's note.
	 *
	 * This method assumes the note is fairly small and can be accessed
	 * efficiently. Larger notes should be accessed by streaming:
	 *
	 * <pre>
	 * ObjectId dataId = thisMap.get(id);
	 * if (dataId != null)
	 * 	reader.open(dataId).openStream();
	 * </pre>
	 *
	 * @param id
	 *            object to lookup the note of.
	 * @param sizeLimit
	 *            maximum number of bytes to return. If the note data size is
	 *            larger than this limit, LargeObjectException will be thrown.
	 * @return if a note is defined for {@code id}, the note content. If no note
	 *         is defined, null.
	 * @throws LargeObjectException
	 *             the note data is larger than {@code sizeLimit}.
	 * @throws MissingObjectException
	 *             the note's blob does not exist in the repository.
	 * @throws IOException
	 *             the note's blob cannot be read from the repository
	 */
	public byte[] getCachedBytes(AnyObjectId id, int sizeLimit)
			throws LargeObjectException, MissingObjectException, IOException {
		ObjectId dataId = get(id);
		if (dataId != null)
			return reader.open(dataId).getCachedBytes(sizeLimit);
		else
			return null;
	}

	private void load(RevCommit src) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		try {
			byte[] idBuf = new byte[Constants.OBJECT_ID_LENGTH];
			TreeWalk tw = new TreeWalk(reader);
			tw.reset();
			tw.setRecursive(true);
			tw.addTree(src.getTree());

			notes.clear();
			commit = src.copy();

			while (tw.next()) {
				ObjectId pathId = pathToId(idBuf, tw.getRawPath());
				if (pathId != null)
					notes.add(new Note(pathId, tw.getObjectId(0)));
			}
		} finally {
			reader.release();
		}
	}

	private static ObjectId pathToId(byte[] rawIdBuf, byte[] path) {
		int r = 0;

		for (int i = 0; i < path.length; i++) {
			byte c = path[i];

			// Skip embedded '/' in paths, these aren't part of a name.
			if (c == '/')
				continue;

			// We need at least 2 digits to consume in this cycle.
			if (path.length < i + 2)
				return null;

			// We cannot exceed an ObjectId raw length.
			if (r == Constants.OBJECT_ID_STRING_LENGTH)
				return null;

			int high, low;
			try {
				high = RawParseUtils.parseHexInt4(c);
				low = RawParseUtils.parseHexInt4(path[++i]);
			} catch (ArrayIndexOutOfBoundsException notHex) {
				return null;
			}

			rawIdBuf[r++] = (byte) ((high << 4) | low);
		}

		// We need exactly the right number of input bytes.
		if (r == Constants.OBJECT_ID_LENGTH)
			return ObjectId.fromRaw(rawIdBuf);
		else
			return null;
	}
}
