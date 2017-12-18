/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Three-way note tree merge.
 * <p>
 * Direct implementation of NoteMap merger without using
 * {@link org.eclipse.jgit.treewalk.TreeWalk} and
 * {@link org.eclipse.jgit.treewalk.AbstractTreeIterator}
 */
public class NoteMapMerger {
	private static final FanoutBucket EMPTY_FANOUT = new FanoutBucket(0);

	private static final LeafBucket EMPTY_LEAF = new LeafBucket(0);

	private final Repository db;

	private final NoteMerger noteMerger;

	private final MergeStrategy nonNotesMergeStrategy;

	private final ObjectReader reader;

	private final ObjectInserter inserter;

	private final MutableObjectId objectIdPrefix;

	/**
	 * Constructs a NoteMapMerger with custom
	 * {@link org.eclipse.jgit.notes.NoteMerger} and custom
	 * {@link org.eclipse.jgit.merge.MergeStrategy}.
	 *
	 * @param db
	 *            Git repository
	 * @param noteMerger
	 *            note merger for merging conflicting changes on a note
	 * @param nonNotesMergeStrategy
	 *            merge strategy for merging non-note entries
	 */
	public NoteMapMerger(Repository db, NoteMerger noteMerger,
			MergeStrategy nonNotesMergeStrategy) {
		this.db = db;
		this.reader = db.newObjectReader();
		this.inserter = db.newObjectInserter();
		this.noteMerger = noteMerger;
		this.nonNotesMergeStrategy = nonNotesMergeStrategy;
		this.objectIdPrefix = new MutableObjectId();
	}

	/**
	 * Constructs a NoteMapMerger with
	 * {@link org.eclipse.jgit.notes.DefaultNoteMerger} as the merger for notes
	 * and the {@link org.eclipse.jgit.merge.MergeStrategy#RESOLVE} as the
	 * strategy for resolving conflicts on non-notes
	 *
	 * @param db
	 *            Git repository
	 */
	public NoteMapMerger(Repository db) {
		this(db, new DefaultNoteMerger(), MergeStrategy.RESOLVE);
	}

	/**
	 * Performs the merge.
	 *
	 * @param base
	 *            base version of the note tree
	 * @param ours
	 *            ours version of the note tree
	 * @param theirs
	 *            theirs version of the note tree
	 * @return merge result as a new NoteMap
	 * @throws java.io.IOException
	 */
	public NoteMap merge(NoteMap base, NoteMap ours, NoteMap theirs)
			throws IOException {
		try {
			InMemoryNoteBucket mergedBucket = merge(0, base.getRoot(),
					ours.getRoot(), theirs.getRoot());
			inserter.flush();
			return NoteMap.newMap(mergedBucket, reader);
		} finally {
			reader.close();
			inserter.close();
		}
	}

	/**
	 * This method is called only when it is known that there is some difference
	 * between base, ours and theirs.
	 *
	 * @param treeDepth
	 * @param base
	 * @param ours
	 * @param theirs
	 * @return merge result as an InMemoryBucket
	 * @throws IOException
	 */
	private InMemoryNoteBucket merge(int treeDepth, InMemoryNoteBucket base,
			InMemoryNoteBucket ours, InMemoryNoteBucket theirs)
			throws IOException {
		InMemoryNoteBucket result;

		if (base instanceof FanoutBucket || ours instanceof FanoutBucket
				|| theirs instanceof FanoutBucket) {
			result = mergeFanoutBucket(treeDepth, asFanout(base),
					asFanout(ours), asFanout(theirs));

		} else {
			result = mergeLeafBucket(treeDepth, (LeafBucket) base,
					(LeafBucket) ours, (LeafBucket) theirs);
		}

		result.nonNotes = mergeNonNotes(nonNotes(base), nonNotes(ours),
				nonNotes(theirs));
		return result;
	}

	private FanoutBucket asFanout(InMemoryNoteBucket bucket) {
		if (bucket == null)
			return EMPTY_FANOUT;
		if (bucket instanceof FanoutBucket)
			return (FanoutBucket) bucket;
		return ((LeafBucket) bucket).split();
	}

	private static NonNoteEntry nonNotes(InMemoryNoteBucket b) {
		return b == null ? null : b.nonNotes;
	}

	private InMemoryNoteBucket mergeFanoutBucket(int treeDepth,
			FanoutBucket base,
			FanoutBucket ours, FanoutBucket theirs) throws IOException {
		FanoutBucket result = new FanoutBucket(treeDepth * 2);
		// walking through entries of base, ours, theirs
		for (int i = 0; i < 256; i++) {
			NoteBucket b = base.getBucket(i);
			NoteBucket o = ours.getBucket(i);
			NoteBucket t = theirs.getBucket(i);

			if (equals(o, t))
				addIfNotNull(result, i, o);

			else if (equals(b, o))
				addIfNotNull(result, i, t);

			else if (equals(b, t))
				addIfNotNull(result, i, o);

			else {
				objectIdPrefix.setByte(treeDepth, i);
				InMemoryNoteBucket mergedBucket = merge(treeDepth + 1,
						FanoutBucket.loadIfLazy(b, objectIdPrefix, reader),
						FanoutBucket.loadIfLazy(o, objectIdPrefix, reader),
						FanoutBucket.loadIfLazy(t, objectIdPrefix, reader));
				result.setBucket(i, mergedBucket);
			}
		}
		return result.contractIfTooSmall(objectIdPrefix, reader);
	}

	private static boolean equals(NoteBucket a, NoteBucket b) {
		if (a == null && b == null)
			return true;
		return a != null && b != null && a.getTreeId().equals(b.getTreeId());
	}

	private void addIfNotNull(FanoutBucket b, int cell, NoteBucket child)
			throws IOException {
		if (child == null)
			return;
		if (child instanceof InMemoryNoteBucket)
			b.setBucket(cell, ((InMemoryNoteBucket) child).writeTree(inserter));
		else
			b.setBucket(cell, child.getTreeId());
	}

	private InMemoryNoteBucket mergeLeafBucket(int treeDepth, LeafBucket bb,
			LeafBucket ob, LeafBucket tb) throws MissingObjectException,
			IOException {
		bb = notNullOrEmpty(bb);
		ob = notNullOrEmpty(ob);
		tb = notNullOrEmpty(tb);

		InMemoryNoteBucket result = new LeafBucket(treeDepth * 2);
		int bi = 0, oi = 0, ti = 0;
		while (bi < bb.size() || oi < ob.size() || ti < tb.size()) {
			Note b = get(bb, bi), o = get(ob, oi), t = get(tb, ti);
			Note min = min(b, o, t);

			b = sameNoteOrNull(min, b);
			o = sameNoteOrNull(min, o);
			t = sameNoteOrNull(min, t);

			if (sameContent(o, t))
				result = addIfNotNull(result, o);

			else if (sameContent(b, o))
				result = addIfNotNull(result, t);

			else if (sameContent(b, t))
				result = addIfNotNull(result, o);

			else
				result = addIfNotNull(result,
						noteMerger.merge(b, o, t, reader, inserter));

			if (b != null)
				bi++;
			if (o != null)
				oi++;
			if (t != null)
				ti++;
		}
		return result;
	}

	private static LeafBucket notNullOrEmpty(LeafBucket b) {
		return b != null ? b : EMPTY_LEAF;
	}

	private static Note get(LeafBucket b, int i) {
		return i < b.size() ? b.get(i) : null;
	}

	private static Note min(Note b, Note o, Note t) {
		Note min = b;
		if (min == null || (o != null && o.compareTo(min) < 0))
			min = o;
		if (min == null || (t != null && t.compareTo(min) < 0))
			min = t;
		return min;
	}

	private static Note sameNoteOrNull(Note min, Note other) {
		return sameNote(min, other) ? other : null;
	}

	private static boolean sameNote(Note a, Note b) {
		if (a == null && b == null)
			return true;
		return a != null && b != null && AnyObjectId.equals(a, b);
	}

	private static boolean sameContent(Note a, Note b) {
		if (a == null && b == null)
			return true;
		return a != null && b != null
				&& AnyObjectId.equals(a.getData(), b.getData());
	}

	private static InMemoryNoteBucket addIfNotNull(InMemoryNoteBucket result,
			Note note) {
		if (note != null)
			return result.append(note);
		else
			return result;
	}

	private NonNoteEntry mergeNonNotes(NonNoteEntry baseList,
			NonNoteEntry oursList, NonNoteEntry theirsList) throws IOException {
		if (baseList == null && oursList == null && theirsList == null)
			return null;

		ObjectId baseId = write(baseList);
		ObjectId oursId = write(oursList);
		ObjectId theirsId = write(theirsList);
		inserter.flush();

		Merger m = nonNotesMergeStrategy.newMerger(db, true);
		if (m instanceof ThreeWayMerger)
			((ThreeWayMerger) m).setBase(baseId);
		if (!m.merge(oursId, theirsId))
			throw new NotesMergeConflictException(baseList, oursList,
					theirsList);
		ObjectId resultTreeId = m.getResultTreeId();
		AbbreviatedObjectId none = AbbreviatedObjectId.fromString(""); //$NON-NLS-1$
		return NoteParser.parse(none, resultTreeId, reader).nonNotes;
	}

	private ObjectId write(NonNoteEntry list)
			throws IOException {
		LeafBucket b = new LeafBucket(0);
		b.nonNotes = list;
		return b.writeTree(inserter);
	}
}
