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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Detect and resolve object renames. */
public class RenameDetector {

	/**
	 * The size of the hash table to build in the heuristic rename detection
	 */
	static final int HASH_TABLE_SIZE = 65537; // 2^16+1, which is prime

	private static final int EXACT_RENAME_SCORE = 100;

	private static final Comparator<DiffEntry> DIFF_COMPARATOR = new Comparator<DiffEntry>() {
		public int compare(DiffEntry o1, DiffEntry o2) {
			return o1.newName.compareTo((o2.newName == null ? o2.oldName
					: o2.newName));
		}
	};

	private final List<DiffEntry> entries = new ArrayList<DiffEntry>();

	private List<DiffEntry> deleted = new ArrayList<DiffEntry>();

	private List<DiffEntry> added = new ArrayList<DiffEntry>();

	private boolean done = false;

	private final Repository repo;

	private static final class HashCount {
		int hash;

		int count;

		HashCount next;

		HashCount(int hash, int count, HashCount next) {
			this.hash = hash;
			this.count = count;
			this.next = next;
		}
	}

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
	 * Walk through a given tree walk with exactly two trees and add all
	 * differing files to the list of object to run rename detection on.
	 * <p>
	 * The tree walk must have two trees attached to it, as well as a filter.
	 * Calling this method after calling {@link #getEntries()} will result in an
	 * {@link IllegalStateException}.
	 *
	 * @param walk
	 *            the TreeWalk to walk through. Must have exactly two trees.
	 * @throws IllegalStateException
	 *             the {@link #getEntries()} method has already been called for
	 *             this instance.
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
			throw new IllegalStateException(JGitText.get().renamesAlreadyFound);
		MutableObjectId idBuf = new MutableObjectId();
		while (walk.next()) {
			DiffEntry entry = new DiffEntry();
			walk.getObjectId(idBuf, 0);
			entry.oldId = AbbreviatedObjectId.fromObjectId(idBuf);
			walk.getObjectId(idBuf, 1);
			entry.newId = AbbreviatedObjectId.fromObjectId(idBuf);
			entry.oldMode = walk.getFileMode(0);
			entry.newMode = walk.getFileMode(1);
			entry.newName = entry.oldName = walk.getPathString();
			if (entry.oldMode == FileMode.MISSING) {
				entry.changeType = ChangeType.ADD;
				added.add(entry);
			} else if (entry.newMode == FileMode.MISSING) {
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
			throw new IllegalStateException(JGitText.get().renamesAlreadyFound);
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
			done = true;
			findExactRenames();
			findContentRenames();
			entries.addAll(added);
			entries.addAll(deleted);
			added = null;
			deleted = null;
			Collections.sort(entries, DIFF_COMPARATOR);
		}
		return Collections.unmodifiableList(entries);
	}

	private void findContentRenames() throws IOException {

		if (added.isEmpty() || deleted.isEmpty())
			return;

		boolean[] paired = new boolean[deleted.size()];
		int[][] scores = calculateScores();

		class MaxLocation {
			int max = 0;
			int location;
		}

		MaxLocation[] rowMax = new MaxLocation[added.size()];
		MaxLocation[] colMax = new MaxLocation[deleted.size()];

		for (int r = 0; r < added.size(); r++) {
			MaxLocation rml = new MaxLocation();
			rowMax[r] = rml;
			for (int c = 0; c < deleted.size(); c++) {
				MaxLocation cml = colMax[c];
				if (cml == null) {
					cml = new MaxLocation();
					colMax[c] = cml;
				}
				if (scores[r][c] > rml.max) {
					rml.max = scores[r][c];
					rml.location = c;
				}
				if (scores[r][c] > colMax[c].max) {
					colMax[c].max = scores[r][c];
					colMax[c].location = r;
				}
			}
		}

		ArrayList<DiffEntry> tempAdded = new ArrayList<DiffEntry>(added.size());
		for (int r = 0; r < added.size(); r++) {
			int c = rowMax[r].location;
			// Check if we have a column max and a row max in the same cell
			if (colMax[c].location == r && scores[r][c] > 50) {
				entries.add(resolveRename(added.get(r), deleted.get(c),
						scores[r][c]));
				paired[c] = true;
			} else {
				tempAdded.add(added.get(r));
			}
		}
		added = tempAdded;

		ArrayList<DiffEntry> tempDeleted = new ArrayList<DiffEntry>(deleted
				.size());
		for (int i = 0; i < deleted.size(); i++)
			if (!paired[i])
				tempDeleted.add(deleted.get(i));

		deleted = tempDeleted;
	}

	private int[][] calculateScores() throws IOException {
		int[][] scores = new int[added.size()][deleted.size()];
		for (int r = 0; r < added.size(); r++) {
			for (int c = 0; c < deleted.size(); c++) {
				scores[r][c] = score(added.get(r).newId, deleted.get(c).oldId);
			}
		}
		return scores;
	}

	private int score(AbbreviatedObjectId a, AbbreviatedObjectId b)
			throws IOException {
		byte[] ac = repo.openBlob(a.toObjectId()).getBytes();
		byte[] bc = repo.openBlob(b.toObjectId()).getBytes();

		HashCount[] aCounts = calcHashCounts(ac);
		HashCount[] bCounts = calcHashCounts(bc);

		int aDiff = 0; // blocks that a has that b does not
		int bDiff = 0; // blocks that b has that a does not
		int same = 0; // block that are common

		for (int i = 0; i < HASH_TABLE_SIZE; i++) {
			HashCount aptr = aCounts[i];
			HashCount bptr = bCounts[i];

			while (aptr != null && bptr != null) {
				if (aptr.hash < bptr.hash) {
					// a has something b does not
					aDiff += aptr.count;
					aptr = aptr.next;
				} else if (aptr.hash > bptr.hash) {
					// b has something a does not
					bDiff += bptr.count;
					bptr = bptr.next;
				} else {
					// a and b both have the same thing, but possibly a
					// different number of them
					if (aptr.count > bptr.count) {
						aDiff += aptr.count - bptr.count;
					} else {
						bDiff += bptr.count - aptr.count;
					}

					// add the common blocks to same, which is the minimum of
					// both counts
					same += (aptr.count < bptr.count ? aptr.count : bptr.count);
					aptr = aptr.next;
					bptr = bptr.next;
				}
			}

			// at this point, either a or b might have more things left
			while (aptr != null) {
				aDiff += aptr.count;
				aptr = aptr.next;
			}

			while (bptr != null) {
				bDiff += bptr.count;
				bptr = bptr.next;
			}
		}

		return Math
				.round((same * 2) / ((float) same * 2 + aDiff + bDiff) * 100);
	}

	private HashCount[] calcHashCounts(byte[] bytes) {
		int s = 0;
		int e = 0;
		HashCount[] table = new HashCount[HASH_TABLE_SIZE];

		while (e < bytes.length) {
			if (bytes[e] == '\n' || e - s == 60 || e == bytes.length - 1) {
				int hash = hashLine(bytes, s, e + 1);
				int tableIndex = (hash >>> 1) % HASH_TABLE_SIZE;

				HashCount tmp = table[tableIndex];

				if (tmp == null) {
					HashCount hc = new HashCount(hash, 1, null);
					table[tableIndex] = hc;
				} else if (hash < tmp.hash) {
					HashCount hc = new HashCount(hash, 1, tmp);
					table[tableIndex] = hc;
				} else if (hash > tmp.hash) {
					while (tmp.hash != hash && tmp.next != null
							&& hash >= tmp.next.hash) {
						tmp = tmp.next;
					}
					if (tmp.hash == hash) {
						tmp.count++;
					} else if (tmp.next == null) {
						HashCount hc = new HashCount(hash, 1, null);
						tmp.next = hc;
					} else {
						// tmp.hash < hash < tmp.next.hash
						// Must insert between tmp and tmp.next
						HashCount hc = new HashCount(hash, 1, tmp.next);
						tmp.next = hc;
					}
				} else {
					tmp.count++;
				}

				s = e + 1;
			}

			e++;
		}

		return table;
	}

	int hashLine(final byte[] raw, int ptr, final int end) {
		int hash = 5381;
		for (; ptr < end; ptr++)
			hash = (hash << 5) ^ (raw[ptr] & 0xff);
		return hash;
	}

	@SuppressWarnings("unchecked")
	private void findExactRenames() {
		HashMap<AbbreviatedObjectId, Object> map = new HashMap<AbbreviatedObjectId, Object>();

		for (DiffEntry del : deleted) {
			Object old = map.put(del.oldId, del);
			if (old != null) {
				if (old instanceof DiffEntry) {
					ArrayList<DiffEntry> tmp = new ArrayList<DiffEntry>(2);
					tmp.add((DiffEntry) old);
					tmp.add(del);
					map.put(del.oldId, tmp);
				} else {
					// Must be a list of DiffEntrys
					((List) old).add(del);
					map.put(del.oldId, old);
				}
			}
		}

		ArrayList<DiffEntry> tempAdded = new ArrayList<DiffEntry>(added.size());

		for (DiffEntry add : added) {
			Object del = map.remove(add.newId);
			if (del != null) {
				if (del instanceof DiffEntry) {
					entries.add(resolveRename(add, (DiffEntry) del,
							EXACT_RENAME_SCORE));
				} else {
					// Must be a list of DiffEntrys
					List<DiffEntry> tmp = (List<DiffEntry>) del;
					entries.add(resolveRename(add, tmp.remove(0),
							EXACT_RENAME_SCORE));
					if (!tmp.isEmpty())
						map.put(add.newId, del);
				}
			} else {
				tempAdded.add(add);
			}
		}
		added = tempAdded;

		Collection<Object> values = map.values();
		ArrayList<DiffEntry> tempDeleted = new ArrayList<DiffEntry>(values
				.size());
		for (Object o : values) {
			if (o instanceof DiffEntry)
				tempDeleted.add((DiffEntry) o);
			else
				tempDeleted.addAll((List<DiffEntry>) o);
		}
		deleted = tempDeleted;
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
