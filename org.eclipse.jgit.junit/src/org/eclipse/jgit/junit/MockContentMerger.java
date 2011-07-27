/*
 * Copyright (C) 2011, Benjamin Muskalla <benjamin.muskalla@tasktop.com>
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
package org.eclipse.jgit.junit;

import java.io.IOException;
import java.util.Collections;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ContentMerger;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

public final class MockContentMerger extends ContentMerger {

	public MockContentMerger(Repository db) {
		super(db);
	}

	@Override
	public MergeResult<RawText> merge(RawTextComparator cmp,
			CanonicalTreeParser base, CanonicalTreeParser ours,
			CanonicalTreeParser theirs) throws IOException {
		String mergedContent = "custom merge - ";
		mergedContent += getRawText(base);
		mergedContent += ":";
		mergedContent += getRawText(ours);
		mergedContent += ":";
		mergedContent += getRawText(theirs);

		RawText rawMerge = new RawText(mergedContent.getBytes());
		MergeResult<RawText> mergeResult = new MergeResult<RawText>(
				Collections.singletonList(rawMerge));
		mergeResult.add(0, 0, 1, ConflictState.NO_CONFLICT);
		return mergeResult;
	}

	private String getRawText(CanonicalTreeParser tree) throws IOException {
		return new RawText(db.open(tree.getEntryObjectId(), Constants.OBJ_BLOB)
				.getCachedBytes()).getString(0);
	}
}