/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2014, André de Oliveira <andre.oliveira@liferay.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.merge;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;

class MergeFormatterPass {

	private final EolAwareOutputStream out;

	private final MergeResult<RawText> res;

	private final List<String> seqName;

	private final Charset charset;

	private final boolean threeWayMerge;

	private final boolean writeBase; // diff3-style requested

	private String lastConflictingName; // is set to non-null whenever we are in
										// a conflict

	/**
	 * @param out
	 *            the {@link java.io.OutputStream} where to write the textual
	 *            presentation
	 * @param res
	 *            the merge result which should be presented
	 * @param seqName
	 *            When a conflict is reported each conflicting range will get a
	 *            name. This name is following the "&lt;&lt;&lt;&lt;&lt;&lt;&lt;
	 *            " or "&gt;&gt;&gt;&gt;&gt;&gt;&gt; " conflict markers. The
	 *            names for the sequences are given in this list
	 * @param charset
	 *            the character set used when writing conflict metadata
	 */
	MergeFormatterPass(OutputStream out, MergeResult<RawText> res,
			List<String> seqName, Charset charset) {
		this(out, res, seqName, charset, false);
	}

	/**
	 * @param out
	 *            the {@link java.io.OutputStream} where to write the textual
	 *            presentation
	 * @param res
	 *            the merge result which should be presented
	 * @param seqName
	 *            When a conflict is reported each conflicting range will get a
	 *            name. This name is following the "&lt;&lt;&lt;&lt;&lt;&lt;&lt;
	 *            ", "|||||||" or "&gt;&gt;&gt;&gt;&gt;&gt;&gt; " conflict
	 *            markers. The names for the sequences are given in this list
	 * @param charset
	 *            the character set used when writing conflict metadata
	 * @param writeBase
	 *            base's contribution should be written in conflicts
	 */
	MergeFormatterPass(OutputStream out, MergeResult<RawText> res,
			List<String> seqName, Charset charset, boolean writeBase) {
		this.out = new EolAwareOutputStream(out);
		this.res = res;
		this.seqName = seqName;
		this.charset = charset;
		this.threeWayMerge = (res.getSequences().size() == 3);
		this.writeBase = writeBase;
	}

	void formatMerge() throws IOException {
		boolean missingNewlineAtEnd = false;
		for (MergeChunk chunk : res) {
			if (!isBase(chunk) || writeBase) {
				RawText seq = res.getSequences().get(chunk.getSequenceIndex());
				writeConflictMetadata(chunk);
				// the lines with conflict-metadata are written. Now write the
				// chunk
				for (int i = chunk.getBegin(); i < chunk.getEnd(); i++)
					writeLine(seq, i);
				missingNewlineAtEnd = seq.isMissingNewlineAtEnd();
			}
		}
		// one possible leftover: if the merge result ended with a conflict we
		// have to close the last conflict here
		if (lastConflictingName != null)
			writeConflictEnd();
		if (!missingNewlineAtEnd)
			out.beginln();
	}

	private void writeConflictMetadata(MergeChunk chunk) throws IOException {
		if (lastConflictingName != null
				&& !isTheirs(chunk) && !isBase(chunk)) {
			// found the end of a conflict
			writeConflictEnd();
		}
		if (isOurs(chunk)) {
			// found the start of a conflict
			writeConflictStart(chunk);
		} else if (isTheirs(chunk)) {
			// found the theirs conflicting chunk
			writeConflictChange(chunk);
		} else if (isBase(chunk)) {
			// found the base conflicting chunk
			writeConflictBase(chunk);
		}
	}

	private void writeConflictEnd() throws IOException {
		writeln(">>>>>>> " + lastConflictingName); //$NON-NLS-1$
		lastConflictingName = null;
	}

	private void writeConflictStart(MergeChunk chunk) throws IOException {
		lastConflictingName = seqName.get(chunk.getSequenceIndex());
		writeln("<<<<<<< " + lastConflictingName); //$NON-NLS-1$
	}

	private void writeConflictChange(MergeChunk chunk) throws IOException {
		/*
		 * In case of a non-three-way merge I'll add the name of the conflicting
		 * chunk behind the equal signs. I also append the name of the last
		 * conflicting chunk after the ending greater-than signs. If somebody
		 * knows a better notation to present non-three-way merges - feel free
		 * to correct here.
		 */
		lastConflictingName = seqName.get(chunk.getSequenceIndex());
		writeln(threeWayMerge ? "=======" : "======= " //$NON-NLS-1$ //$NON-NLS-2$
				+ lastConflictingName);
	}

	private void writeConflictBase(MergeChunk chunk) throws IOException {
		lastConflictingName = seqName.get(chunk.getSequenceIndex());
		writeln("||||||| " + lastConflictingName); //$NON-NLS-1$
	}

	private void writeln(String s) throws IOException {
		out.beginln();
		out.write((s + "\n").getBytes(charset)); //$NON-NLS-1$
	}

	private void writeLine(RawText seq, int i) throws IOException {
		out.beginln();
		seq.writeLine(out, i);
		// still BOL? It was a blank line. But writeLine won't lf, so we do.
		if (out.isBeginln())
			out.write('\n');
	}

	private boolean isBase(MergeChunk chunk) {
		return chunk.getConflictState() == ConflictState.BASE_CONFLICTING_RANGE;
	}

	private boolean isOurs(MergeChunk chunk) {
		return chunk
				.getConflictState() == ConflictState.FIRST_CONFLICTING_RANGE;
	}

	private boolean isTheirs(MergeChunk chunk) {
		return chunk.getConflictState() == ConflictState.NEXT_CONFLICTING_RANGE;
	}
}
