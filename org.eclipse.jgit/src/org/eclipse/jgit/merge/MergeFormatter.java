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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.RawText;

/**
 * A class to convert merge results into a Git conformant textual presentation
 */
public class MergeFormatter {
	/**
	 * Formats the results of a merge of {@link org.eclipse.jgit.diff.RawText}
	 * objects in a Git conformant way. This method also assumes that the
	 * {@link org.eclipse.jgit.diff.RawText} objects being merged are line
	 * oriented files which use LF as delimiter. This method will also use LF to
	 * separate chunks and conflict metadata, therefore it fits only to texts
	 * that are LF-separated lines.
	 *
	 * @param out
	 *            the outputstream where to write the textual presentation
	 * @param res
	 *            the merge result which should be presented
	 * @param seqName
	 *            When a conflict is reported each conflicting range will get a
	 *            name. This name is following the "&lt;&lt;&lt;&lt;&lt;&lt;&lt;
	 *            " or "&gt;&gt;&gt;&gt;&gt;&gt;&gt; " conflict markers. The
	 *            names for the sequences are given in this list
	 * @param charsetName
	 *            the name of the characterSet used when writing conflict
	 *            metadata
	 * @throws java.io.IOException
	 */
	public void formatMerge(OutputStream out, MergeResult<RawText> res,
			List<String> seqName, String charsetName) throws IOException {
		new MergeFormatterPass(out, res, seqName, charsetName).formatMerge();
	}

	/**
	 * Formats the results of a merge of exactly two
	 * {@link org.eclipse.jgit.diff.RawText} objects in a Git conformant way.
	 * This convenience method accepts the names for the three sequences (base
	 * and the two merged sequences) as explicit parameters and doesn't require
	 * the caller to specify a List
	 *
	 * @param out
	 *            the {@link java.io.OutputStream} where to write the textual
	 *            presentation
	 * @param res
	 *            the merge result which should be presented
	 * @param baseName
	 *            the name ranges from the base should get
	 * @param oursName
	 *            the name ranges from ours should get
	 * @param theirsName
	 *            the name ranges from theirs should get
	 * @param charsetName
	 *            the name of the characterSet used when writing conflict
	 *            metadata
	 * @throws java.io.IOException
	 */
	@SuppressWarnings("unchecked")
	public void formatMerge(OutputStream out, MergeResult res, String baseName,
			String oursName, String theirsName, String charsetName) throws IOException {
		List<String> names = new ArrayList<>(3);
		names.add(baseName);
		names.add(oursName);
		names.add(theirsName);
		formatMerge(out, res, names, charsetName);
	}
}
