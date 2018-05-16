/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.patch;

import static org.eclipse.jgit.util.RawParseUtils.match;
import static org.eclipse.jgit.util.RawParseUtils.nextLF;
import static org.eclipse.jgit.util.RawParseUtils.parseBase10;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.util.MutableInteger;

/**
 * Hunk header describing the layout of a single block of lines
 */
public class HunkHeader {
	/** Details about an old image of the file. */
	public abstract static class OldImage {
		/** First line number the hunk starts on in this file. */
		int startLine;

		/** Total number of lines this hunk covers in this file. */
		int lineCount;

		/** Number of lines deleted by the post-image from this file. */
		int nDeleted;

		/** Number of lines added by the post-image not in this file. */
		int nAdded;

		/** @return first line number the hunk starts on in this file. */
		public int getStartLine() {
			return startLine;
		}

		/** @return total number of lines this hunk covers in this file. */
		public int getLineCount() {
			return lineCount;
		}

		/** @return number of lines deleted by the post-image from this file. */
		public int getLinesDeleted() {
			return nDeleted;
		}

		/** @return number of lines added by the post-image not in this file. */
		public int getLinesAdded() {
			return nAdded;
		}

		/** @return object id of the pre-image file. */
		public abstract AbbreviatedObjectId getId();
	}

	final FileHeader file;

	/** Offset within {@link #file}.buf to the "@@ -" line. */
	final int startOffset;

	/** Position 1 past the end of this hunk within {@link #file}'s buf. */
	int endOffset;

	private final OldImage old;

	/** First line number in the post-image file where the hunk starts */
	int newStartLine;

	/** Total number of post-image lines this hunk covers (context + inserted) */
	int newLineCount;

	/** Total number of lines of context appearing in this hunk */
	int nContext;

	private EditList editList;

	HunkHeader(FileHeader fh, int offset) {
		this(fh, offset, new OldImage() {
			@Override
			public AbbreviatedObjectId getId() {
				return fh.getOldId();
			}
		});
	}

	HunkHeader(FileHeader fh, int offset, OldImage oi) {
		file = fh;
		startOffset = offset;
		old = oi;
	}

	HunkHeader(FileHeader fh, EditList editList) {
		this(fh, fh.buf.length);
		this.editList = editList;
		endOffset = startOffset;
		nContext = 0;
		if (editList.isEmpty()) {
			newStartLine = 0;
			newLineCount = 0;
		} else {
			newStartLine = editList.get(0).getBeginB();
			Edit last = editList.get(editList.size() - 1);
			newLineCount = last.getEndB() - newStartLine;
		}
	}

	/**
	 * Get header for the file this hunk applies to.
	 *
	 * @return header for the file this hunk applies to.
	 */
	public FileHeader getFileHeader() {
		return file;
	}

	/**
	 * Get the byte array holding this hunk's patch script.
	 *
	 * @return the byte array holding this hunk's patch script.
	 */
	public byte[] getBuffer() {
		return file.buf;
	}

	/**
	 * Get offset of the start of this hunk in {@link #getBuffer()}.
	 *
	 * @return offset of the start of this hunk in {@link #getBuffer()}.
	 */
	public int getStartOffset() {
		return startOffset;
	}

	/**
	 * Get offset one past the end of the hunk in {@link #getBuffer()}.
	 *
	 * @return offset one past the end of the hunk in {@link #getBuffer()}.
	 */
	public int getEndOffset() {
		return endOffset;
	}

	/**
	 * Get information about the old image mentioned in this hunk.
	 *
	 * @return information about the old image mentioned in this hunk.
	 */
	public OldImage getOldImage() {
		return old;
	}

	/**
	 * Get first line number in the post-image file where the hunk starts.
	 *
	 * @return first line number in the post-image file where the hunk starts.
	 */
	public int getNewStartLine() {
		return newStartLine;
	}

	/**
	 * Get total number of post-image lines this hunk covers.
	 *
	 * @return total number of post-image lines this hunk covers.
	 */
	public int getNewLineCount() {
		return newLineCount;
	}

	/**
	 * Get total number of lines of context appearing in this hunk.
	 *
	 * @return total number of lines of context appearing in this hunk.
	 */
	public int getLinesContext() {
		return nContext;
	}

	/**
	 * Convert to a list describing the content edits performed within the hunk.
	 *
	 * @return a list describing the content edits performed within the hunk.
	 */
	public EditList toEditList() {
		if (editList == null) {
			editList = new EditList();
			final byte[] buf = file.buf;
			int c = nextLF(buf, startOffset);
			int oLine = old.startLine;
			int nLine = newStartLine;
			Edit in = null;

			SCAN: for (; c < endOffset; c = nextLF(buf, c)) {
				switch (buf[c]) {
				case ' ':
				case '\n':
					in = null;
					oLine++;
					nLine++;
					continue;

				case '-':
					if (in == null) {
						in = new Edit(oLine - 1, nLine - 1);
						editList.add(in);
					}
					oLine++;
					in.extendA();
					continue;

				case '+':
					if (in == null) {
						in = new Edit(oLine - 1, nLine - 1);
						editList.add(in);
					}
					nLine++;
					in.extendB();
					continue;

				case '\\': // Matches "\ No newline at end of file"
					continue;

				default:
					break SCAN;
				}
			}
		}
		return editList;
	}

	void parseHeader() {
		// Parse "@@ -236,9 +236,9 @@ protected boolean"
		//
		final byte[] buf = file.buf;
		final MutableInteger ptr = new MutableInteger();
		ptr.value = nextLF(buf, startOffset, ' ');
		old.startLine = -parseBase10(buf, ptr.value, ptr);
		if (buf[ptr.value] == ',')
			old.lineCount = parseBase10(buf, ptr.value + 1, ptr);
		else
			old.lineCount = 1;

		newStartLine = parseBase10(buf, ptr.value + 1, ptr);
		if (buf[ptr.value] == ',')
			newLineCount = parseBase10(buf, ptr.value + 1, ptr);
		else
			newLineCount = 1;
	}

	int parseBody(Patch script, int end) {
		final byte[] buf = file.buf;
		int c = nextLF(buf, startOffset), last = c;

		old.nDeleted = 0;
		old.nAdded = 0;

		SCAN: for (; c < end; last = c, c = nextLF(buf, c)) {
			switch (buf[c]) {
			case ' ':
			case '\n':
				nContext++;
				continue;

			case '-':
				old.nDeleted++;
				continue;

			case '+':
				old.nAdded++;
				continue;

			case '\\': // Matches "\ No newline at end of file"
				continue;

			default:
				break SCAN;
			}
		}

		if (last < end && nContext + old.nDeleted - 1 == old.lineCount
				&& nContext + old.nAdded == newLineCount
				&& match(buf, last, Patch.SIG_FOOTER) >= 0) {
			// This is an extremely common occurrence of "corruption".
			// Users add footers with their signatures after this mark,
			// and git diff adds the git executable version number.
			// Let it slide; the hunk otherwise looked sound.
			//
			old.nDeleted--;
			return last;
		}

		if (nContext + old.nDeleted < old.lineCount) {
			final int missingCount = old.lineCount - (nContext + old.nDeleted);
			script.error(buf, startOffset, MessageFormat.format(
					JGitText.get().truncatedHunkOldLinesMissing,
					Integer.valueOf(missingCount)));

		} else if (nContext + old.nAdded < newLineCount) {
			final int missingCount = newLineCount - (nContext + old.nAdded);
			script.error(buf, startOffset, MessageFormat.format(
					JGitText.get().truncatedHunkNewLinesMissing,
					Integer.valueOf(missingCount)));

		} else if (nContext + old.nDeleted > old.lineCount
				|| nContext + old.nAdded > newLineCount) {
			final String oldcnt = old.lineCount + ":" + newLineCount; //$NON-NLS-1$
			final String newcnt = (nContext + old.nDeleted) + ":" //$NON-NLS-1$
					+ (nContext + old.nAdded);
			script.warn(buf, startOffset, MessageFormat.format(
					JGitText.get().hunkHeaderDoesNotMatchBodyLineCountOf, oldcnt, newcnt));
		}

		return c;
	}

	void extractFileLines(OutputStream[] out) throws IOException {
		final byte[] buf = file.buf;
		int ptr = startOffset;
		int eol = nextLF(buf, ptr);
		if (endOffset <= eol)
			return;

		// Treat the hunk header as though it were from the ancestor,
		// as it may have a function header appearing after it which
		// was copied out of the ancestor file.
		//
		out[0].write(buf, ptr, eol - ptr);

		SCAN: for (ptr = eol; ptr < endOffset; ptr = eol) {
			eol = nextLF(buf, ptr);
			switch (buf[ptr]) {
			case ' ':
			case '\n':
			case '\\':
				out[0].write(buf, ptr, eol - ptr);
				out[1].write(buf, ptr, eol - ptr);
				break;
			case '-':
				out[0].write(buf, ptr, eol - ptr);
				break;
			case '+':
				out[1].write(buf, ptr, eol - ptr);
				break;
			default:
				break SCAN;
			}
		}
	}

	void extractFileLines(final StringBuilder sb, final String[] text,
			final int[] offsets) {
		final byte[] buf = file.buf;
		int ptr = startOffset;
		int eol = nextLF(buf, ptr);
		if (endOffset <= eol)
			return;
		copyLine(sb, text, offsets, 0);
		SCAN: for (ptr = eol; ptr < endOffset; ptr = eol) {
			eol = nextLF(buf, ptr);
			switch (buf[ptr]) {
			case ' ':
			case '\n':
			case '\\':
				copyLine(sb, text, offsets, 0);
				skipLine(text, offsets, 1);
				break;
			case '-':
				copyLine(sb, text, offsets, 0);
				break;
			case '+':
				copyLine(sb, text, offsets, 1);
				break;
			default:
				break SCAN;
			}
		}
	}

	void copyLine(final StringBuilder sb, final String[] text,
			final int[] offsets, final int fileIdx) {
		final String s = text[fileIdx];
		final int start = offsets[fileIdx];
		int end = s.indexOf('\n', start);
		if (end < 0)
			end = s.length();
		else
			end++;
		sb.append(s, start, end);
		offsets[fileIdx] = end;
	}

	void skipLine(final String[] text, final int[] offsets,
			final int fileIdx) {
		final String s = text[fileIdx];
		final int end = s.indexOf('\n', offsets[fileIdx]);
		offsets[fileIdx] = end < 0 ? s.length() : end + 1;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("HunkHeader[");
		buf.append(getOldImage().getStartLine());
		buf.append(',');
		buf.append(getOldImage().getLineCount());
		buf.append("->");
		buf.append(getNewStartLine()).append(',').append(getNewLineCount());
		buf.append(']');
		return buf.toString();
	}
}
