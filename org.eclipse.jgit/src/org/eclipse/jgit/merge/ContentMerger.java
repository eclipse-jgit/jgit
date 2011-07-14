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
package org.eclipse.jgit.merge;

import java.io.IOException;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * The merger is responsible to merge the file contents during a pass of
 * {@link ResolveMerger}.
 *
 */
abstract public class ContentMerger {

	/**	 */
	protected final Repository db;

	/**
	 * Default content merger based on {@link MergeAlgorithm}
	 *
	 * @param repository
	 * @return content merger for <code>repository</code>
	 */
	public static ContentMerger getDefaultContentMerger(
			final Repository repository) {
		return new ContentMerger(repository) {

			@Override
			public MergeResult<RawText> merge(RawTextComparator cmp,
					CanonicalTreeParser base, CanonicalTreeParser ours,
					CanonicalTreeParser theirs) throws IOException {
				RawText baseText = base == null ? RawText.EMPTY_TEXT
						: ResolveMerger.getRawText(base.getEntryObjectId(),
								repository);
				RawText theirsText = ResolveMerger.getRawText(
						theirs.getEntryObjectId(), repository);
				RawText oursText = ResolveMerger.getRawText(
						ours.getEntryObjectId(), repository);
				MergeAlgorithm mergeAlgorithm = getMergeAlgorithm(repository);
				MergeResult<RawText> result = mergeAlgorithm.merge(
						RawTextComparator.DEFAULT, baseText, oursText,
						theirsText);
				return result;
			}

			protected MergeAlgorithm getMergeAlgorithm(final Repository db) {
				SupportedAlgorithm diffAlg = db.getConfig().getEnum(
						ConfigConstants.CONFIG_DIFF_SECTION, null,
						ConfigConstants.CONFIG_KEY_ALGORITHM,
						SupportedAlgorithm.HISTOGRAM);
				MergeAlgorithm mergeAlgorithm = new MergeAlgorithm(
						DiffAlgorithm.getAlgorithm(diffAlg));
				return mergeAlgorithm;
			}

		};
	}

	/**
	 * @param db
	 */
	public ContentMerger(Repository db) {
		this.db = db;
	}

	/**
	 * Merges a the given <code>ours</code> with <code>theirs</code> and
	 * <code>base</code> as ancestor. This method should return
	 * <code>null</code> in case the merge is not possible.
	 *
	 * @param cmp
	 * @param base
	 * @param ours
	 * @param theirs
	 * @return the merge result
	 * @throws IOException
	 */
	public abstract MergeResult<RawText> merge(RawTextComparator cmp,
			CanonicalTreeParser base, CanonicalTreeParser ours,
			CanonicalTreeParser theirs) throws IOException;

}
