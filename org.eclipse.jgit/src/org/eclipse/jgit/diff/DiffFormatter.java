/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008-2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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

import static org.eclipse.jgit.lib.Constants.encodeASCII;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.patch.FileHeader;

/**
 * Format an {@link EditList} as a Git style unified patch script.
 */
public class DiffFormatter {
	private static final byte[] noNewLine = encodeASCII("\\ No newline at end of file\n");

	private int context;

	/** Create a new formatter with a default level of context. */
	public DiffFormatter() {
		setContext(3);
	}

	/**
	 * Change the number of lines of context to display.
	 *
	 * @param lineCount
	 *            number of lines of context to see before the first
	 *            modification and after the last modification within a hunk of
	 *            the modified file.
	 */
	public void setContext(final int lineCount) {
		if (lineCount < 0)
			throw new IllegalArgumentException("context must be >= 0");
		context = lineCount;
	}

	/**
	 * Format a patch script, reusing a previously parsed FileHeader.
	 * <p>
	 * This formatter is primarily useful for editing an existing patch script
	 * to increase or reduce the number of lines of context within the script.
	 * All header lines are reused as-is from the supplied FileHeader.
	 *
	 * @param out
	 *            stream to write the patch script out to.
	 * @param head
	 *            existing file header containing the header lines to copy.
	 * @param a
	 *            text source for the pre-image version of the content. This
	 *            must match the content of {@link FileHeader#getOldId()}.
	 * @param b
	 *            text source for the post-image version of the content. This
	 *            must match the content of {@link FileHeader#getNewId()}.
	 * @throws IOException
	 *             writing to the supplied stream failed.
	 */
	public void format(final OutputStream out, final FileHeader head,
			final RawText a, final RawText b) throws IOException {
		// Reuse the existing FileHeader as-is by blindly copying its
		// header lines, but avoiding its hunks. Instead we recreate
		// the hunks from the text instances we have been supplied.
		//
		final int start = head.getStartOffset();
		int end = head.getEndOffset();
		if (!head.getHunks().isEmpty())
			end = head.getHunks().get(0).getStartOffset();
		out.write(head.getBuffer(), start, end - start);

		formatEdits(out, a, b, head.toEditList());
	}

	/**
	 * Formats a list of edits in unified diff format
	 * @param out where the unified diff is written to
	 * @param a the text A which was compared
	 * @param b the text B which was compared
	 * @param edits some differences which have been calculated between A and B
	 * @throws IOException
	 */
	public void formatEdits(final OutputStream out, final RawText a,
			final RawText b, final EditList edits) throws IOException {
		for (int curIdx = 0; curIdx < edits.size();) {
			Edit curEdit = edits.get(curIdx);
			final int endIdx = findCombinedEnd(edits, curIdx);
			final Edit endEdit = edits.get(endIdx);

			int aCur = Math.max(0, curEdit.getBeginA() - context);
			int bCur = Math.max(0, curEdit.getBeginB() - context);
			final int aEnd = Math.min(a.size(), endEdit.getEndA() + context);
			final int bEnd = Math.min(b.size(), endEdit.getEndB() + context);

			writeHunkHeader(out, aCur, aEnd, bCur, bEnd);

			while (aCur < aEnd || bCur < bEnd) {
				if (aCur < curEdit.getBeginA() || endIdx + 1 < curIdx) {
					writeLine(out, ' ', a, aCur);
					aCur++;
					bCur++;

				} else if (aCur < curEdit.getEndA()) {
					writeLine(out, '-', a, aCur++);

				} else if (bCur < curEdit.getEndB()) {
					writeLine(out, '+', b, bCur++);
				}

				if (end(curEdit, aCur, bCur) && ++curIdx < edits.size())
					curEdit = edits.get(curIdx);
			}
		}
	}

	private void writeHunkHeader(final OutputStream out, int aCur, int aEnd,
			int bCur, int bEnd) throws IOException {
		out.write('@');
		out.write('@');
		writeRange(out, '-', aCur + 1, aEnd - aCur);
		writeRange(out, '+', bCur + 1, bEnd - bCur);
		out.write(' ');
		out.write('@');
		out.write('@');
		out.write('\n');
	}

	private static void writeRange(final OutputStream out, final char prefix,
			final int begin, final int cnt) throws IOException {
		out.write(' ');
		out.write(prefix);
		switch (cnt) {
		case 0:
			// If the range is empty, its beginning number must be the
			// line just before the range, or 0 if the range is at the
			// start of the file stream. Here, begin is always 1 based,
			// so an empty file would produce "0,0".
			//
			out.write(encodeASCII(begin - 1));
			out.write(',');
			out.write('0');
			break;

		case 1:
			// If the range is exactly one line, produce only the number.
			//
			out.write(encodeASCII(begin));
			break;

		default:
			out.write(encodeASCII(begin));
			out.write(',');
			out.write(encodeASCII(cnt));
			break;
		}
	}

	private static void writeLine(final OutputStream out, final char prefix,
			final RawText text, final int cur) throws IOException {
		out.write(prefix);
		text.writeLine(out, cur);
		out.write('\n');
		if (cur + 1 == text.size() && text.isMissingNewlineAtEnd())
			out.write(noNewLine);
	}

	private int findCombinedEnd(final List<Edit> edits, final int i) {
		int end = i + 1;
		while (end < edits.size()
				&& (combineA(edits, end) || combineB(edits, end)))
			end++;
		return end - 1;
	}

	private boolean combineA(final List<Edit> e, final int i) {
		return e.get(i).getBeginA() - e.get(i - 1).getEndA() <= 2 * context;
	}

	private boolean combineB(final List<Edit> e, final int i) {
		return e.get(i).getBeginB() - e.get(i - 1).getEndB() <= 2 * context;
	}

	private static boolean end(final Edit edit, final int a, final int b) {
		return edit.getEndA() <= a && edit.getEndB() <= b;
	}
}
