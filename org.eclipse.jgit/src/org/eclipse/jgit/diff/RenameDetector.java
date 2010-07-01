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

package org.eclipse.jgit.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Detect and resolve object renames. */
public class RenameDetector {

	private static final Comparator<DiffEntry> DIFF_COMPARATOR = new Comparator<DiffEntry>() {
		public int compare(DiffEntry o1, DiffEntry o2) {
			return o1.newName.compareTo(o2.newName);
		}
	};

	private final Repository repo;

	private final List<DiffEntry> entries = new ArrayList<DiffEntry>();

	private List<DiffEntry> deleted = new ArrayList<DiffEntry>();

	private List<DiffEntry> added = new ArrayList<DiffEntry>();

	private boolean done = false;

	/**
	 * Create a new rename detector for the given repository
	 *
	 * @param repo
	 *            the repository to use for rename detection
	 */
	public RenameDetector(Repository repo) {
		this.repo = repo;
	}

	/**
	 * Walk through a given tree walk and add all differing files to the list of
	 * object to run rename detection on. The tree walk must have two trees
	 * attached to it, as well as a filter. Calling this method after calling
	 * {@link #getEntries()} will result in an {@link IllegalStateException}.
	 *
	 * @param walk
	 *            the TreeWalk to walk through
	 * @throws IllegalStateException
	 *             the {@link #getEntries()} method has already been called for
	 *             this instance
	 * @throws MissingObjectException
	 *             {@link TreeWalk#isRecursive()} was enabled on the tree, a
	 *             subtree was found, but the subtree object does not exist in
	 *             this repository. The repository may be missing objects.
	 * @throws IncorrectObjectTypeException
	 *             {@link TreeWalk#isRecursive()} was enabled on the tree, a
	 *             subtree was found, and the subtree id does not denote a tree,
	 *             but instead names some other non-tree type of object. The
	 *             repository may have data corruption.
	 * @throws CorruptObjectException
	 *             the contents of a tree did not appear to be a tree. The
	 *             repository may have data corruption.
	 * @throws IOException
	 *             a loose object or pack file could not be read.
	 */
	public void addTreeWalk(TreeWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException, IOException {
		if (done)
			throw new IllegalStateException("Renames have already been found");
		while (walk.next()) {
			DiffEntry entry = new DiffEntry();
			entry.oldId = walk.getObjectId(0).abbreviate(repo);
			entry.newId = walk.getObjectId(1).abbreviate(repo);
			entry.oldMode = walk.getFileMode(0);
			entry.newMode = walk.getFileMode(1);
			entry.newName = entry.oldName = walk.getPathString();
			if (entry.oldId.equals(ObjectId.zeroId())) {
				entry.changeType = ChangeType.ADD;
				added.add(entry);
			} else if (entry.newId.equals(ObjectId.zeroId())) {
				entry.changeType = ChangeType.DELETE;
				deleted.add(entry);
			} else {
				entry.changeType = ChangeType.MODIFY;
				entries.add(entry);
			}
		}
	}

	/**
	 * Add a DiffEntry to the list of items to run rename detection on. Calling
	 * this method after calling {@link #getEntries()} will result in an
	 * {@link IllegalStateException}.
	 *
	 * @param entry
	 *            the {@link DiffEntry} to add
	 *
	 * @throws IllegalStateException
	 *             the {@link #getEntries()} method has already been called for
	 *             this instance
	 */
	public void addDiffEntry(DiffEntry entry) {
		if (done)
			throw new IllegalStateException("Renames have already been found");
		switch (entry.changeType) {
		case ADD:
			added.add(entry);
			break;
		case DELETE:
			deleted.add(entry);
			break;
		case COPY:
		case MODIFY:
		case RENAME:
		default:
			entries.add(entry);
		}
	}

	/**
	 * Determines which files, if any, are renames, and returns an unmodifiable
	 * list of {@link DiffEntry}s representing all files that have been changed
	 * in some way. The list will contain all modified files first
	 *
	 * @return an unmodifiable list of {@link DiffEntry}s representing all files
	 *         that have been changed
	 * @throws IOException
	 */
	public List<DiffEntry> getEntries() throws IOException {
		if (!done) {
			findRenames();
			done = true;
			Collections.sort(entries, DIFF_COMPARATOR);
		}
		return Collections.unmodifiableList(entries);
	}

	@SuppressWarnings("unchecked")
	private void findRenames() {
		HashMap<ObjectId, Object> map = new HashMap<ObjectId, Object>();

		for (DiffEntry del : deleted) {
			ObjectId id = del.oldId.toObjectId();
			Object old = map.put(id, del);
			if (old != null) {
				if (old instanceof DiffEntry) {
					map.put(id, Arrays.asList(del, (DiffEntry) old));
				} else {
					// Must be a list of DiffEntrys
					((List) old).add(del);
					map.put(id, old);
				}
			}
		}
		deleted = null;

		ArrayList<DiffEntry> tempAdded = new ArrayList<DiffEntry>(added.size());

		for (DiffEntry add : added) {
			ObjectId id = add.newId.toObjectId();
			Object del = map.remove(id);
			if (del != null) {
				if (del instanceof DiffEntry) {
					entries.add(resolveRename(add, (DiffEntry) del, 100));
				} else {
					// Must be a list of DiffEntrys
					entries.add(resolveRename(add, ((List<DiffEntry>) del)
							.get(0), 100));
					((List<DiffEntry>) del).remove(0);
					map.put(id, del);
				}
			} else {
				tempAdded.add(add);
			}
		}
		entries.addAll(tempAdded);
		added = null;

		for (Object o : map.values()) {
			if (o instanceof DiffEntry)
				entries.add((DiffEntry) o);
			else
				entries.addAll((List<DiffEntry>) o);
		}
	}

	private DiffEntry resolveRename(DiffEntry add, DiffEntry del, int score) {
		DiffEntry renamed = new DiffEntry();

		renamed.oldId = del.oldId;
		renamed.oldMode = del.oldMode;
		renamed.oldName = del.oldName;
		renamed.newId = add.newId;
		renamed.newMode = add.newMode;
		renamed.newName = add.newName;
		renamed.changeType = ChangeType.RENAME;
		renamed.score = score;

		return renamed;
	}
}
