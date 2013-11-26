/*******************************************************************************
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010-2012, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2012, Research In Motion Limited
 * Copyright (C) 2013, Obeo
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
 *******************************************************************************/
package org.eclipse.jgit.merge;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * This merge driver can be used on any file. It will merge the files using
 * their textual content through the use of a {@link MergeAlgorithm}.
 */
public class TextMergeDriver implements MergeDriver {
	// FIXME delete when API makes it possible.
	/** Low level result of the merge. */
	private MergeResult<RawText> lowLevelResults;

	/**
	 * {@inheritDoc}
	 *
	 * @see org.eclipse.jgit.merge.MergeDriver#merge(org.eclipse.jgit.lib.Repository,
	 *      java.io.File, java.io.File, java.io.File, java.lang.String[])
	 */
	public boolean merge(Repository repository, File ours, File theirs,
			File base, String[] commitNames) throws IOException {
		final SupportedAlgorithm diffAlg = repository.getConfig().getEnum(
				ConfigConstants.CONFIG_DIFF_SECTION, null,
				ConfigConstants.CONFIG_KEY_ALGORITHM,
				SupportedAlgorithm.HISTOGRAM);
		final MergeAlgorithm mergeAlgorithm = new MergeAlgorithm(
				DiffAlgorithm.getAlgorithm(diffAlg));

		lowLevelResults = contentMerge(mergeAlgorithm, base, ours, theirs);
		writeMergedContent(ours, lowLevelResults, commitNames);

		return !lowLevelResults.containsConflicts();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.eclipse.jgit.merge.MergeDriver#getName()
	 */
	public String getName() {
		return "Text"; //$NON-NLS-1$
	}

	/*
	 * FIXME this should not be exposed, see ResolveMerger#getMergeResults()
	 */
	/**
	 * @return The low level results.
	 */
	public MergeResult<RawText> getLowLevelResults() {
		return lowLevelResults;
	}

	/**
	 * Write merged file content in the given file.
	 *
	 * @param file
	 *            The file we are to overwrite.
	 * @param result
	 *            Result of the content merge.
	 * @param commitNames
	 *            Name of the commits we were merging. Will be used to format
	 *            the conflict markers.
	 * @throws IOException
	 */
	private static void writeMergedContent(File file,
			MergeResult<RawText> result, String[] commitNames)
			throws IOException {
		MergeFormatter fmt = new MergeFormatter();
		OutputStream stream = null;
		try {
			stream = new BufferedOutputStream(new FileOutputStream(file));
			fmt.formatMerge(stream, result, Arrays.asList(commitNames),
					Constants.CHARACTER_ENCODING);
		} finally {
			if (stream != null)
				stream.close();
		}
	}

	/**
	 * Does the content merge. If any of the files is <code>null</code>, an
	 * empty text will be used instead.
	 *
	 * @param mergeAlgorithm
	 *            The algorithm to use for this merge.
	 * @param base
	 *            Content of the common ancestor of ours and theirs.
	 * @param ours
	 *            Content of our file.
	 * @param theirs
	 *            Content of their file.
	 * @return The result of the content merge.
	 * @throws IOException
	 */
	private static MergeResult<RawText> contentMerge(
			MergeAlgorithm mergeAlgorithm, File base, File ours, File theirs)
			throws IOException {
		RawText baseText = getRawText(base);
		RawText ourText = getRawText(ours);
		RawText theirsText = getRawText(theirs);
		return mergeAlgorithm.merge(RawTextComparator.DEFAULT, baseText,
				ourText, theirsText);
	}

	private static RawText getRawText(File file)
			throws IOException {
		if (file == null)
			return RawText.EMPTY_TEXT;
		return new RawText(file);
	}
}
