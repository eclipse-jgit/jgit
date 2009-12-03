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

import java.util.ListIterator;

import org.eclipse.jgit.diff.ExpandableContent;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;

/**
 * Provides (currently as static methods) the merge algorithm which does a
 * three-way merge on content provided as RawText. Makes use of {@link MyersDiff} to
 * compute the diffs.
 *
 * @todo move code into a better class
 */
public class MergeAlgorithm {
	// a sentinel: this edit marks the end of a list of edits.
	private static final Edit stopEdit=new Edit(Integer.MAX_VALUE, Integer.MAX_VALUE);

	/**
	 * @param base
	 * @param ours
	 * @param theirs
	 * @return the resulting content
	 */
	public static ExpandableContent merge(RawText base, RawText ours, RawText theirs) {
		ExpandableContent result=new ExpandableContent();
		ListIterator<Edit> baseToOurs=new MyersDiff(base, ours).getEdits().listIterator();
		ListIterator<Edit> baseToTheirs=new MyersDiff(base, theirs).getEdits().listIterator();
		Edit oursEdit = baseToOurs.hasNext() ? baseToOurs.next() : stopEdit;
		Edit theirsEdit = baseToTheirs.hasNext() ? baseToTheirs.next() : stopEdit;
		int actBase=0; // points to the next line (first line is 0) of base which was not handled yet
		// iterate over all edits from base to ours and from base to theirs

		// as long as we have at least one unprocessed edit from ours or theirs stay in the loop
		while(theirsEdit!=stopEdit || oursEdit!=stopEdit) {

			// find out where the next edit starts (this works because of the stopEdit at the end of each list of edits)
			int nextDiff=Math.min(oursEdit.getBeginA(), theirsEdit.getBeginA());

			// Handle the common part which is untouched neither by ours or theirs
			// Just copy this part to the result
			if (nextDiff>actBase) {
				result.add(base, actBase, nextDiff);
				actBase=nextDiff;
			}

			switch (oursEdit.compareTo(theirsEdit)) {
			case -1:
				// something was changed in Ours not overlapping with any change from theirs -> take ours
				result.add(ours, oursEdit.getBeginB(), oursEdit.getEndB());
				actBase=oursEdit.getEndA();
				oursEdit = baseToOurs.hasNext() ? baseToOurs.next() : stopEdit;
				break;

			case 1:
				// something was changed in Theirs not overlapping with any change from ours -> take theirs
				result.add(theirs, theirsEdit.getBeginB(), theirsEdit.getEndB());
				actBase=theirsEdit.getEndA();
				theirsEdit = baseToTheirs.hasNext() ? baseToTheirs.next() : stopEdit;
				break;

			case 0:
				// here we found a real overlapping modification

				// Combine edits:
				// Maybe an Edit on one side corresponds to multiple Edits on the other side. Then
				// we have to merge Edits of the other side - so in the end we can merge together two single
				// edits
				// This merging together of edits is an iterative process: after we have merged some edits we have to do the check again. The merged
				// edits could now correspond to multiple edits on the other side.
				Edit nextOursEdit = baseToOurs.hasNext() ? baseToOurs.next() : stopEdit;
				Edit nextTheirsEdit = baseToTheirs.hasNext() ? baseToTheirs.next() : stopEdit;
				for(;;) {
					if (oursEdit.getEndA()>nextTheirsEdit.getBeginA()) {
						theirsEdit=melt(theirsEdit, nextTheirsEdit);
						nextTheirsEdit = baseToTheirs.hasNext() ? baseToTheirs.next() : stopEdit;
					} else if (theirsEdit.getEndA()>nextOursEdit.getBeginA()) {
						oursEdit=melt(oursEdit, nextOursEdit);
						nextOursEdit = baseToOurs.hasNext() ? baseToOurs.next() : stopEdit;
					} else {
						break;
					}
				}

				// After combining the edits we can now concentrate on handling
				// exactly one edit on one side with exactly one edit on the
				// other side

				// conform region:
				// the two edits must start and the same position in A and they
				// must end at the same position in A. To achieve that the edits
				// may have to be widened.
				if (oursEdit.getBeginA()<theirsEdit.getBeginA()) {
					theirsEdit=widen(theirsEdit, true, oursEdit.getBeginA());
				} else {
					oursEdit=widen(oursEdit, true, theirsEdit.getBeginA());
				}
				if (oursEdit.getEndA()>theirsEdit.getEndA()) {
					theirsEdit=widen(theirsEdit, false, oursEdit.getEndA());
				} else {
					oursEdit=widen(oursEdit, false, theirsEdit.getEndA());
				}

				// Add the hunk for on conflict
				result.add(new RawText("<<<<<<<\n".getBytes()));
				result.add(ours, oursEdit.getBeginB(), oursEdit.getEndB());
				result.add(new RawText("=======\n".getBytes()));
				result.add(theirs, theirsEdit.getBeginB(), theirsEdit.getEndB());
				result.add(new RawText(">>>>>>>\n".getBytes()));

				oursEdit=nextOursEdit;
				theirsEdit=nextTheirsEdit;
				actBase=theirsEdit.getEndA();
			}
		}
		return result;
	}

	/**
	 * enlarges an edit
	 *
	 * @param edit
	 *            the edit to be enlarged
	 * @param modifyBegin
	 *            whether we want to move the begin of the region
	 *            (modifyBegin=true) or the end of the region
	 *            (modifyBegin=false)
	 * @param newValue
	 *            the new start (if modifyBegin=true) or end (if
	 *            modifyBegin=false) of the edit
	 * @return the new Edit
	 * @todo avoid that this method creates a new instance of Edit
	 */
	private static Edit widen(Edit edit, boolean modifyBegin, int newValue) {
		if (modifyBegin) {
			return new Edit(newValue, edit.getEndA(), edit.getBeginB()-(edit.getBeginA()-newValue), edit.getEndB());
		} else {
			return new Edit(edit.getBeginA(), newValue, edit.getBeginB(), edit.getEndB()+(newValue - edit.getEndA()));
		}
	}

	/**
	 * combines an edit with another one.
	 *
	 * @param edit
	 * @param otherEdit
	 * @return the combined edit
	 * @todo avoid that this method creates a new instance of Edit
	 */
	private static Edit melt(Edit edit, Edit otherEdit) {
		return new Edit(edit.getBeginA(), otherEdit.getEndA(), edit
				.getBeginB(), otherEdit.getEndB());
	}
}
