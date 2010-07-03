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

import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.Constants.encodeASCII;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.QuotedString;

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
			throw new IllegalArgumentException(JGitText.get().contextMustBeNonNegative);
		context = lineCount;
	}

	/**
	 * Format a patch script from a list of difference entries.
	 *
	 * @param out
	 *            stream to write the patch script out to.
	 * @param src
	 *            repository the file contents can be read from.
	 * @param entries
	 *            entries describing the affected files.
	 * @throws IOException
	 *             a file's content cannot be read, or the output stream cannot
	 *             be written to.
	 */
	public void format(final OutputStream out, Repository src,
			List<? extends DiffEntry> entries) throws IOException {
		for(DiffEntry ent : entries) {
			if (ent instanceof FileHeader) {
				format(
						out,
						(FileHeader) ent, //
						newRawText(open(src, ent.getOldMode(), ent.getOldId())),
						newRawText(open(src, ent.getNewMode(), ent.getNewId())));
			} else {
				format(out, src, ent);
			}
		}
	}

	private void format(OutputStream out, Repository src, DiffEntry ent)
			throws IOException {
		String oldName = quotePath("a/" + ent.getOldName());
		String newName = quotePath("b/" + ent.getNewName());
		out.write(encode("diff --git " + oldName + " " + newName + "\n"));

		switch(ent.getChangeType()) {
		case ADD:
			out.write(encodeASCII("new file mode "));
			ent.getNewMode().copyTo(out);
			out.write('\n');
			break;

		case DELETE:
			out.write(encodeASCII("deleted file mode "));
			ent.getOldMode().copyTo(out);
			out.write('\n');
			break;

		case RENAME:
			out.write(encode("similarity index " + ent.getScore() + "%"));
			out.write('\n');

			out.write(encode("rename from " + quotePath(ent.getOldName())));
			out.write('\n');

			out.write(encode("rename to " + quotePath(ent.getNewName())));
			out.write('\n');
			break;

		case COPY:
			out.write(encode("similarity index " + ent.getScore() + "%"));
			out.write('\n');

			out.write(encode("copy from " + quotePath(ent.getOldName())));
			out.write('\n');

			out.write(encode("copy to " + quotePath(ent.getNewName())));
			out.write('\n');

			if (!ent.getOldMode().equals(ent.getNewMode())) {
				out.write(encodeASCII("new file mode "));
				ent.getNewMode().copyTo(out);
				out.write('\n');
			}
			break;
		}

		switch (ent.getChangeType()) {
		case RENAME:
		case MODIFY:
			if (!ent.getOldMode().equals(ent.getNewMode())) {
				out.write(encodeASCII("old mode "));
				ent.getOldMode().copyTo(out);
				out.write('\n');

				out.write(encodeASCII("new mode "));
				ent.getNewMode().copyTo(out);
				out.write('\n');
			}
		}

		out.write(encodeASCII("index " //
				+ format(src, ent.getOldId()) //
				+ ".." //
				+ format(src, ent.getNewId())));
		if (ent.getOldMode().equals(ent.getNewMode())) {
			out.write(' ');
			ent.getNewMode().copyTo(out);
		}
		out.write('\n');
		out.write(encode("--- " + oldName + '\n'));
		out.write(encode("+++ " + newName + '\n'));

		byte[] aRaw = open(src, ent.getOldMode(), ent.getOldId());
		byte[] bRaw = open(src, ent.getNewMode(), ent.getNewId());

		if (RawText.isBinary(aRaw) || RawText.isBinary(bRaw)) {
			out.write(encodeASCII("Binary files differ\n"));

		} else {
			RawText a = newRawText(aRaw);
			RawText b = newRawText(bRaw);
			formatEdits(out, a, b, new MyersDiff(a, b).getEdits());
		}
	}

	/**
	 * Construct a RawText sequence for use with {@link MyersDiff}.
	 *
	 * @param content
	 *            text to be compared.
	 * @return the raw text instance to handle the content.
	 */
	protected RawText newRawText(byte[] content) {
		return new RawText(content);
	}

	private String format(Repository db, AbbreviatedObjectId oldId) {
		if (oldId.isComplete())
			oldId = oldId.toObjectId().abbreviate(db, 8);
		return oldId.name();
	}

	private static String quotePath(String name) {
		String q = QuotedString.GIT_PATH.quote(name);
		return ('"' + name + '"').equals(q) ? name : q;
	}

	private byte[] open(Repository src, FileMode mode, AbbreviatedObjectId id)
			throws IOException {
		if (mode == FileMode.MISSING)
			return new byte[] {};

		if (mode.getObjectType() != Constants.OBJ_BLOB)
			return new byte[] {};

		if (id.isComplete()) {
			ObjectLoader ldr = src.openObject(id.toObjectId());
			return ldr.getCachedBytes();
		}

		return new byte[] {};
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
					writeContextLine(out, a, aCur, isEndOfLineMissing(a, aCur));
					aCur++;
					bCur++;
				} else if (aCur < curEdit.getEndA()) {
					writeRemovedLine(out, a, aCur, isEndOfLineMissing(a, aCur));
					aCur++;
				} else if (bCur < curEdit.getEndB()) {
					writeAddedLine(out, b, bCur, isEndOfLineMissing(b, bCur));
					bCur++;
				}

				if (end(curEdit, aCur, bCur) && ++curIdx < edits.size())
					curEdit = edits.get(curIdx);
			}
		}
	}

	/**
	 * Output a line of diff context
	 *
	 * @param out
	 *            OutputStream
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @param endOfLineMissing
	 *            true if we should add the GNU end of line missing warning
	 * @throws IOException
	 */
	protected void writeContextLine(final OutputStream out, final RawText text,
			final int line, boolean endOfLineMissing) throws IOException {
		writeLine(out, ' ', text, line, endOfLineMissing);
	}

	private boolean isEndOfLineMissing(final RawText text, final int line) {
		return line + 1 == text.size() && text.isMissingNewlineAtEnd();
	}

	/**
	 * Output an added line
	 *
	 * @param out
	 *            OutputStream
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @param endOfLineMissing
	 *            true if we should add the gnu end of line missing warning
	 * @throws IOException
	 */
	protected void writeAddedLine(final OutputStream out, final RawText text, final int line, boolean endOfLineMissing)
			throws IOException {
		writeLine(out, '+', text, line, endOfLineMissing);
	}

	/**
	 * Output a removed line
	 *
	 * @param out
	 *            OutputStream
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @param endOfLineMissing
	 *            true if we should add the gnu end of line missing warning
	 * @throws IOException
	 */
	protected void writeRemovedLine(final OutputStream out, final RawText text,
			final int line, boolean endOfLineMissing) throws IOException {
		writeLine(out, '-', text, line, endOfLineMissing);
	}

	/**
	 * Output a hunk header
	 *
	 * @param out
	 *            OutputStream
	 * @param aStartLine
	 *            within first source
	 * @param aEndLine
	 *            within first source
	 * @param bStartLine
	 *            within second source
	 * @param bEndLine
	 *            within second source
	 * @throws IOException
	 */
	protected void writeHunkHeader(final OutputStream out, int aStartLine, int aEndLine,
			int bStartLine, int bEndLine) throws IOException {
		out.write('@');
		out.write('@');
		writeRange(out, '-', aStartLine + 1, aEndLine - aStartLine);
		writeRange(out, '+', bStartLine + 1, bEndLine - bStartLine);
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
			final RawText text, final int cur, boolean noNewLineIndicator) throws IOException {
		out.write(prefix);
		text.writeLine(out, cur);
		out.write('\n');
		if (noNewLineIndicator)
			writeNoNewLine(out);
	}

	private static void writeNoNewLine(final OutputStream out)
			throws IOException {
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
