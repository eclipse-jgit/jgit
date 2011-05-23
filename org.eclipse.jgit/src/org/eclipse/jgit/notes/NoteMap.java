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
import java.util.Iterator;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
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
public class NoteMap implements Iterable<Note> {
	/**
	 * Construct a new empty note map.
	 *
	 * @return an empty note map.
	 */
	public static NoteMap newEmptyMap() {
		NoteMap r = new NoteMap(null /* no reader */);
		r.root = new LeafBucket(0);
		return r;
	}

	/**
	 * Shorten the note ref name by trimming off the {@link Constants#R_NOTES}
	 * prefix if it exists.
	 *
	 * @param noteRefName
	 * @return a more user friendly note name
	 */
	public static String shortenRefName(String noteRefName) {
		if (noteRefName.startsWith(Constants.R_NOTES))
			return noteRefName.substring(Constants.R_NOTES.length());
		return noteRefName;
	}

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

	/**
	 * Construct a new note map from an existing note bucket.
	 *
	 * @param root
	 *            the root bucket of this note map
	 * @param reader
	 *            reader to scan the note branch with. This reader may be
	 *            retained by the NoteMap for the life of the map in order to
	 *            support lazy loading of entries.
	 * @return the note map built from the note bucket
	 */
	static NoteMap newMap(InMemoryNoteBucket root, ObjectReader reader) {
		NoteMap map = new NoteMap(reader);
		map.root = root;
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
	 * @return an iterator that iterates over notes of this NoteMap. Non note
	 *         entries are ignored by this iterator.
	 */
	public Iterator<Note> iterator() {
		try {
			return root.iterator(new MutableObjectId(), reader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
		Note n = root.getNote(id, reader);
		return n == null ? null : n.getData();
	}

	/**
	 * Lookup a note for a specific ObjectId.
	 *
	 * @param id
	 *            the object to look for.
	 * @return the note for the given object id, or null if no note exists.
	 * @throws IOException
	 *             a portion of the note space is not accessible.
	 */
	public Note getNote(AnyObjectId id) throws IOException {
		return root.getNote(id, reader);
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

	/**
	 * Attach (or remove) a note on an object.
	 *
	 * If no note exists, a new note is stored. If a note already exists for the
	 * given object, it is replaced (or removed).
	 *
	 * This method only updates the map in memory.
	 *
	 * If the caller wants to attach a UTF-8 encoded string message to an
	 * object, {@link #set(AnyObjectId, String, ObjectInserter)} is a convenient
	 * way to encode and update a note in one step.
	 *
	 * @param noteOn
	 *            the object to attach the note to. This same ObjectId can later
	 *            be used as an argument to {@link #get(AnyObjectId)} or
	 *            {@link #getCachedBytes(AnyObjectId, int)} to read back the
	 *            {@code noteData}.
	 * @param noteData
	 *            data to associate with the note. This must be the ObjectId of
	 *            a blob that already exists in the repository. If null the note
	 *            will be deleted, if present.
	 * @throws IOException
	 *             a portion of the note space is not accessible.
	 */
	public void set(AnyObjectId noteOn, ObjectId noteData) throws IOException {
		InMemoryNoteBucket newRoot = root.set(noteOn, noteData, reader);
		if (newRoot == null) {
			newRoot = new LeafBucket(0);
			newRoot.nonNotes = root.nonNotes;
		}
		root = newRoot;
	}

	/**
	 * Attach a note to an object.
	 *
	 * If no note exists, a new note is stored. If a note already exists for the
	 * given object, it is replaced (or removed).
	 *
	 * @param noteOn
	 *            the object to attach the note to. This same ObjectId can later
	 *            be used as an argument to {@link #get(AnyObjectId)} or
	 *            {@link #getCachedBytes(AnyObjectId, int)} to read back the
	 *            {@code noteData}.
	 * @param noteData
	 *            text to store in the note. The text will be UTF-8 encoded when
	 *            stored in the repository. If null the note will be deleted, if
	 *            the empty string a note with the empty string will be stored.
	 * @param ins
	 *            inserter to write the encoded {@code noteData} out as a blob.
	 *            The caller must ensure the inserter is flushed before the
	 *            updated note map is made available for reading.
	 * @throws IOException
	 *             the note data could not be stored in the repository.
	 */
	public void set(AnyObjectId noteOn, String noteData, ObjectInserter ins)
			throws IOException {
		ObjectId dataId;
		if (noteData != null) {
			byte[] dataUTF8 = Constants.encode(noteData);
			dataId = ins.insert(Constants.OBJ_BLOB, dataUTF8);
		} else {
			dataId = null;
		}
		set(noteOn, dataId);
	}

	/**
	 * Remove a note from an object.
	 *
	 * If no note exists, no action is performed.
	 *
	 * This method only updates the map in memory.
	 *
	 * @param noteOn
	 *            the object to remove the note from.
	 * @throws IOException
	 *             a portion of the note space is not accessible.
	 */
	public void remove(AnyObjectId noteOn) throws IOException {
		set(noteOn, null);
	}

	/**
	 * Write this note map as a tree.
	 *
	 * @param inserter
	 *            inserter to use when writing trees to the object database.
	 *            Caller is responsible for flushing the inserter before trying
	 *            to read the objects, or exposing them through a reference.
	 * @return the top level tree.
	 * @throws IOException
	 *             a tree could not be written.
	 */
	public ObjectId writeTree(ObjectInserter inserter) throws IOException {
		return root.writeTree(inserter);
	}

	/** @return the root note bucket */
	InMemoryNoteBucket getRoot() {
		return root;
	}

	private void load(ObjectId rootTree) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		AbbreviatedObjectId none = AbbreviatedObjectId.fromString("");
		root = NoteParser.parse(none, rootTree, reader);
	}
}
