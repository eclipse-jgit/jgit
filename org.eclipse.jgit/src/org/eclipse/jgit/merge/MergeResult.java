/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.merge;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;
import org.eclipse.jgit.util.IntList;

/**
 * The result of merging a number of {@link Sequence} objects. These sequences
 * have one common predecessor sequence. The result of a merge is a list of
 * MergeChunks. Each MergeChunk contains either a range (a subsequence) from
 * one of the merged sequences, a range from the common predecessor or a
 * conflicting range from one of the merged sequences. A conflict will be
 * reported as multiple chunks, one for each conflicting range. The first chunk
 * for a conflict is marked specially to distinguish the border between two
 * consecutive conflicts.
 * <p>
 * This class does not know anything about how to present the merge result to
 * the end-user. MergeFormatters have to be used to construct something human
 * readable.
 *
 * @param <S>
 *            type of sequence.
 */
public class MergeResult<S extends Sequence> implements Iterable<MergeChunk> {
	private final List<S> sequences;

	private final IntList chunks = new IntList();

	private boolean containsConflicts = false;

	/**
	 * Creates a new empty MergeResult
	 *
	 * @param sequences
	 *            contains the common predecessor sequence at position 0
	 *            followed by the merged sequences. This list should not be
	 *            modified anymore during the lifetime of this {@link MergeResult}.
	 */
	public MergeResult(List<S> sequences) {
		this.sequences = sequences;
	}

	/**
	 * Adds a new range from one of the merged sequences or from the common
	 * predecessor. This method can add conflicting and non-conflicting ranges
	 * controlled by the conflictState parameter
	 *
	 * @param srcIdx
	 *            determines from which sequence this range comes. An index of
	 *            x specifies the x+1 element in the list of sequences
	 *            specified to the constructor
	 * @param begin
	 *            the first element from the specified sequence which should be
	 *            included in the merge result. Indexes start with 0.
	 * @param end
	 *            specifies the end of the range to be added. The element this
	 *            index points to is the first element which not added to the
	 *            merge result. All elements between begin (including begin) and
	 *            this element are added.
	 * @param conflictState
	 *            when set to NO_CONLICT a non-conflicting range is added.
	 *            This will end implicitly all open conflicts added before.
	 */
	public void add(int srcIdx, int begin, int end, ConflictState conflictState) {
		chunks.add(conflictState.ordinal());
		chunks.add(srcIdx);
		chunks.add(begin);
		chunks.add(end);
		if (conflictState != ConflictState.NO_CONFLICT)
			containsConflicts = true;
	}

	/**
	 * Returns the common predecessor sequence and the merged sequence in one
	 * list. The common predecessor is is the first element in the list
	 *
	 * @return the common predecessor at position 0 followed by the merged
	 *         sequences.
	 */
	public List<S> getSequences() {
		return sequences;
	}

	private static final ConflictState[] states = ConflictState.values();

	/**
	 * @return an iterator over the MergeChunks. The iterator does not support
	 * the remove operation
	 */
	public Iterator<MergeChunk> iterator() {
		return new Iterator<MergeChunk>() {
			int idx;

			public boolean hasNext() {
				return (idx < chunks.size());
			}

			public MergeChunk next() {
				ConflictState state = states[chunks.get(idx++)];
				int srcIdx = chunks.get(idx++);
				int begin = chunks.get(idx++);
				int end = chunks.get(idx++);
				return new MergeChunk(srcIdx, begin, end, state);
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * @return true if this merge result contains conflicts
	 */
	public boolean containsConflicts() {
		return containsConflicts;
	}

	/**
	 * Sets explicitly whether this merge should be seen as containing a
	 * conflict or not. Needed because during RecursiveMerger we want to do
	 * content-merges and take the resulting content (even with conflict
	 * markers!) as new conflict-free content
	 *
	 * @param containsConflicts
	 * @since 3.5
	 */
	protected void setContainsConflicts(boolean containsConflicts) {
		this.containsConflicts = containsConflicts;
	}
}
