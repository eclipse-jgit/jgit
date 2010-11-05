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
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

/**
 * Index of notes from a note branch.
 *
 * This class is not thread-safe, and relies on an {@link ObjectReader} that it
 * borrows/shares with the caller. The reader can be used during any call, and
 * is not released by this class. The caller should arrange for releasing the
 * shared {@code ObjectReader} at the proper times.
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
	public static NoteMap read(ObjectReader reader, RevCommit commit)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		return read(reader, commit.getTree());
	}

	/**
	 * Load a collection of notes from a tree.
	 *
	 * @param reader
	 *            reader to scan the note branch with. This reader may be
	 *            retained by the NoteMap for the life of the map in order to
	 *            support lazy loading of entries.
	 * @param tree
	 *            the note tree to read.
	 * @return the note map read from the tree.
	 * @throws IOException
	 *             the repository cannot be accessed through the reader.
	 * @throws CorruptObjectException
	 *             a tree object is corrupt and cannot be read.
	 * @throws IncorrectObjectTypeException
	 *             a tree object wasn't actually a tree.
	 * @throws MissingObjectException
	 *             a reference tree object doesn't exist.
	 */
	public static NoteMap read(ObjectReader reader, RevTree tree)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		return readTree(reader, tree);
	}

	/**
	 * Load a collection of notes from a tree.
	 *
	 * @param reader
	 *            reader to scan the note branch with. This reader may be
	 *            retained by the NoteMap for the life of the map in order to
	 *            support lazy loading of entries.
	 * @param treeId
	 *            the note tree to read.
	 * @return the note map read from the tree.
	 * @throws IOException
	 *             the repository cannot be accessed through the reader.
	 * @throws CorruptObjectException
	 *             a tree object is corrupt and cannot be read.
	 * @throws IncorrectObjectTypeException
	 *             a tree object wasn't actually a tree.
	 * @throws MissingObjectException
	 *             a reference tree object doesn't exist.
	 */
	public static NoteMap readTree(ObjectReader reader, ObjectId treeId)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		NoteMap map = new NoteMap(reader);
		map.load(treeId);
		return map;
	}

	/** Borrowed reader to access the repository. */
	private final ObjectReader reader;

	/** All of the notes that have been loaded. */
	private InMemoryNoteBucket root;

	private NoteMap(ObjectReader reader) {
		this.reader = reader;
	}

	/**
	 * Lookup a note for a specific ObjectId.
	 *
	 * @param id
	 *            the object to look for.
	 * @return the note's blob ObjectId, or null if no note exists.
	 * @throws IOException
	 *             a portion of the note space is not accessible.
	 */
	public ObjectId get(AnyObjectId id) throws IOException {
		return root.get(id, reader);
	}

	/**
	 * Determine if a note exists for the specified ObjectId.
	 *
	 * @param id
	 *            the object to look for.
	 * @return true if a note exists; false if there is no note.
	 * @throws IOException
	 *             a portion of the note space is not accessible.
	 */
	public boolean contains(AnyObjectId id) throws IOException {
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

	private void load(ObjectId rootTree) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		AbbreviatedObjectId none = AbbreviatedObjectId.fromString("");
		root = NoteParser.parse(none, rootTree, reader);
	}
}
