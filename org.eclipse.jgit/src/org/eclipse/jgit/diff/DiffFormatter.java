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
import static org.eclipse.jgit.lib.FileMode.GITLINK;

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

	private final OutputStream out;

	private Repository db;

	private int context;

	private int abbreviationLength;

	private RawText.Factory rawTextFactory = RawText.FACTORY;

	/**
	 * Create a new formatter with a default level of context.
	 *
	 * @param out
	 *            the stream the formatter will write line data to. This stream
	 *            should have buffering arranged by the caller, as many small
	 *            writes are performed to it.
	 */
	public DiffFormatter(OutputStream out) {
		this.out = out;
		setContext(3);
		setAbbreviationLength(8);
	}

	/** @return the stream we are outputting data to. */
	protected OutputStream getOutputStream() {
		return out;
	}

	/**
	 * Set the repository the formatter can load object contents from.
	 *
	 * @param repository
	 *            source repository holding referenced objects.
	 */
	public void setRepository(Repository repository) {
		db = repository;
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
			throw new IllegalArgumentException(
					JGitText.get().contextMustBeNonNegative);
		context = lineCount;
	}

	/**
	 * Change the number of digits to show in an ObjectId.
	 *
	 * @param count
	 *            number of digits to show in an ObjectId.
	 */
	public void setAbbreviationLength(final int count) {
		if (count < 0)
			throw new IllegalArgumentException(
					JGitText.get().abbreviationLengthMustBeNonNegative);
		abbreviationLength = count;
	}

	/**
	 * Set the helper that constructs difference output.
	 *
	 * @param type
	 *            the factory to create different output. Different types of
	 *            factories can produce different whitespace behavior, for
	 *            example.
	 * @see RawText#FACTORY
	 * @see RawTextIgnoreAllWhitespace#FACTORY
	 * @see RawTextIgnoreLeadingWhitespace#FACTORY
	 * @see RawTextIgnoreTrailingWhitespace#FACTORY
	 * @see RawTextIgnoreWhitespaceChange#FACTORY
	 */
	public void setRawTextFactory(RawText.Factory type) {
		rawTextFactory = type;
	}

	/**
	 * Flush the underlying output stream of this formatter.
	 *
	 * @throws IOException
	 *             the stream's own flush method threw an exception.
	 */
	public void flush() throws IOException {
		out.flush();
	}

	/**
	 * Format a patch script from a list of difference entries.
	 *
	 * @param entries
	 *            entries describing the affected files.
	 * @throws IOException
	 *             a file's content cannot be read, or the output stream cannot
	 *             be written to.
	 */
	public void format(List<? extends DiffEntry> entries) throws IOException {
		for (DiffEntry ent : entries)
			format(ent);
	}

	/**
	 * Format a patch script for one file entry.
	 *
	 * @param ent
	 *            the entry to be formatted.
	 * @throws IOException
	 *             a file's content cannot be read, or the output stream cannot
	 *             be written to.
	 */
	public void format(DiffEntry ent) throws IOException {
		String oldName = quotePath("a/" + ent.getOldName());
		String newName = quotePath("b/" + ent.getNewName());
		out.write(encode("diff --git " + oldName + " " + newName + "\n"));

		switch (ent.getChangeType()) {
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
			out.write(encodeASCII("similarity index " + ent.getScore() + "%"));
			out.write('\n');

			out.write(encode("rename from " + quotePath(ent.getOldName())));
			out.write('\n');

			out.write(encode("rename to " + quotePath(ent.getNewName())));
			out.write('\n');
			break;

		case COPY:
			out.write(encodeASCII("similarity index " + ent.getScore() + "%"));
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
				+ format(ent.getOldId()) //
				+ ".." //
				+ format(ent.getNewId())));
		if (ent.getOldMode().equals(ent.getNewMode())) {
			out.write(' ');
			ent.getNewMode().copyTo(out);
		}
		out.write('\n');
		out.write(encode("--- " + oldName + '\n'));
		out.write(encode("+++ " + newName + '\n'));

		if (ent.getOldMode() == GITLINK || ent.getNewMode() == GITLINK) {
			if (ent.getOldMode() == GITLINK) {
				out.write(encodeASCII("-Subproject commit "
						+ ent.getOldId().name() + "\n"));
			}
			if (ent.getNewMode() == GITLINK) {
				out.write(encodeASCII("+Subproject commit "
						+ ent.getNewId().name() + "\n"));
			}
		} else {
			byte[] aRaw = open(ent.getOldMode(), ent.getOldId());
			byte[] bRaw = open(ent.getNewMode(), ent.getNewId());

			if (RawText.isBinary(aRaw) || RawText.isBinary(bRaw)) {
				out.write(encodeASCII("Binary files differ\n"));

			} else {
				RawText a = rawTextFactory.create(aRaw);
				RawText b = rawTextFactory.create(bRaw);
				formatEdits(a, b, new MyersDiff(a, b).getEdits());
			}
		}
	}

	private String format(AbbreviatedObjectId oldId) {
		if (oldId.isComplete() && db != null)
			oldId = oldId.toObjectId().abbreviate(db, abbreviationLength);
		return oldId.name();
	}

	private static String quotePath(String name) {
		String q = QuotedString.GIT_PATH.quote(name);
		return ('"' + name + '"').equals(q) ? name : q;
	}

	private byte[] open(FileMode mode, AbbreviatedObjectId id)
			throws IOException {
		if (mode == FileMode.MISSING)
			return new byte[] {};

		if (mode.getObjectType() != Constants.OBJ_BLOB)
			return new byte[] {};

		if (db == null)
			throw new IllegalStateException(JGitText.get().repositoryIsRequired);
		if (id.isComplete()) {
			ObjectLoader ldr = db.openObject(id.toObjectId());
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
	public void format(final FileHeader head, final RawText a, final RawText b)
			throws IOException {
		// Reuse the existing FileHeader as-is by blindly copying its
		// header lines, but avoiding its hunks. Instead we recreate
		// the hunks from the text instances we have been supplied.
		//
		final int start = head.getStartOffset();
		int end = head.getEndOffset();
		if (!head.getHunks().isEmpty())
			end = head.getHunks().get(0).getStartOffset();
		out.write(head.getBuffer(), start, end - start);

		formatEdits(a, b, head.toEditList());
	}

	/**
	 * Formats a list of edits in unified diff format
	 *
	 * @param a
	 *            the text A which was compared
	 * @param b
	 *            the text B which was compared
	 * @param edits
	 *            some differences which have been calculated between A and B
	 * @throws IOException
	 */
	public void formatEdits(final RawText a, final RawText b,
			final EditList edits) throws IOException {
		for (int curIdx = 0; curIdx < edits.size();) {
			Edit curEdit = edits.get(curIdx);
			final int endIdx = findCombinedEnd(edits, curIdx);
			final Edit endEdit = edits.get(endIdx);

			int aCur = Math.max(0, curEdit.getBeginA() - context);
			int bCur = Math.max(0, curEdit.getBeginB() - context);
			final int aEnd = Math.min(a.size(), endEdit.getEndA() + context);
			final int bEnd = Math.min(b.size(), endEdit.getEndB() + context);

			writeHunkHeader(aCur, aEnd, bCur, bEnd);

			while (aCur < aEnd || bCur < bEnd) {
				if (aCur < curEdit.getBeginA() || endIdx + 1 < curIdx) {
					writeContextLine(a, aCur);
					if (isEndOfLineMissing(a, aCur))
						out.write(noNewLine);
					aCur++;
					bCur++;
				} else if (aCur < curEdit.getEndA()) {
					writeRemovedLine(a, aCur);
					if (isEndOfLineMissing(a, aCur))
						out.write(noNewLine);
					aCur++;
				} else if (bCur < curEdit.getEndB()) {
					writeAddedLine(b, bCur);
					if (isEndOfLineMissing(b, bCur))
						out.write(noNewLine);
					bCur++;
				}

				if (end(curEdit, aCur, bCur) && ++curIdx < edits.size())
					curEdit = edits.get(curIdx);
			}
		}
	}

	/**
	 * Output a line of context (unmodified line).
	 *
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @throws IOException
	 */
	protected void writeContextLine(final RawText text, final int line)
			throws IOException {
		writeLine(' ', text, line);
	}

	private boolean isEndOfLineMissing(final RawText text, final int line) {
		return line + 1 == text.size() && text.isMissingNewlineAtEnd();
	}

	/**
	 * Output an added line.
	 *
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @throws IOException
	 */
	protected void writeAddedLine(final RawText text, final int line)
			throws IOException {
		writeLine('+', text, line);
	}

	/**
	 * Output a removed line
	 *
	 * @param text
	 *            RawText for accessing raw data
	 * @param line
	 *            the line number within text
	 * @throws IOException
	 */
	protected void writeRemovedLine(final RawText text, final int line)
			throws IOException {
		writeLine('-', text, line);
	}

	/**
	 * Output a hunk header
	 *
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
	protected void writeHunkHeader(int aStartLine, int aEndLine,
			int bStartLine, int bEndLine) throws IOException {
		out.write('@');
		out.write('@');
		writeRange('-', aStartLine + 1, aEndLine - aStartLine);
		writeRange('+', bStartLine + 1, bEndLine - bStartLine);
		out.write(' ');
		out.write('@');
		out.write('@');
		out.write('\n');
	}

	private void writeRange(final char prefix, final int begin, final int cnt)
			throws IOException {
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

	/**
	 * Write a standard patch script line.
	 *
	 * @param prefix
	 *            prefix before the line, typically '-', '+', ' '.
	 * @param text
	 *            the text object to obtain the line from.
	 * @param cur
	 *            line number to output.
	 * @throws IOException
	 *             the stream threw an exception while writing to it.
	 */
	protected void writeLine(final char prefix, final RawText text,
			final int cur) throws IOException {
		out.write(prefix);
		text.writeLine(out, cur);
		out.write('\n');
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
