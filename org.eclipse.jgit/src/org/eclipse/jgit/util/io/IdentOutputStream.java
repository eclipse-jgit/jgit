/*
 * Copyright (C) 2014, Obeo
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
package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.OutputStream;

import javax.swing.text.html.InlineView;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.util.IntList;

/**
 * This output stream replaces on the fly "$Id$" pattern with "$Id:{BlobName}$"
 *
 * @author adaussy
 *
 * @see #bloblPattern
 */
public class IdentOutputStream extends OutputStream {

	// Default visibility for test needs
	static final int BUFFER_SIZE = 8096;

	/**
	 * Prefix character to insert before blobl name.
	 */
	private static final byte[] BLOB_NAME_PREFIX = new byte[] { ':' };

	/**
	 * Ident pattern.
	 */
	private static final byte[] IDENT_PATTERN = new byte[] { '$', 'I', 'd', '$' };

	/**
	 * Wrapped {@link OutputStream}.
	 */
	private final OutputStream out;

	/**
	 * Buffer used to detected binary files.
	 */
	private final byte[] binbuf;

	/**
	 * Counter on {@link #binbuf}.
	 */
	private int binbufcnt = 0;

	/**
	 * Single byte buffer used for {@link #write(int)}.
	 */
	private final byte[] onebytebuf = new byte[1];

	/**
	 * Number of character from the pattern matched in a row.
	 */
	private int patternCounter = 0;

	/**
	 * Holds true if the current input is binary.
	 */
	private boolean isBinary;

	/**
	 * Blob name to insert in the ident pattern.
	 */
	private final byte[] bloblPattern;

	/**
	 * Constructor.
	 *
	 * @param out
	 *            Wrapped {@link OutputStream}
	 * @param blobName
	 *            Blob name to insert in the ident pattern.
	 */
	public IdentOutputStream(OutputStream out, byte[] blobName) {
		this.out = out;
		// Increases the size by 2 since the blobl name as to be surrounded by
		// two extra spaces
		byte[] pattern = new byte[blobName.length + 2];
		pattern[0] = (byte) 0x20; // Space character
		System.arraycopy(blobName, 0, pattern, 1, blobName.length);
		pattern[pattern.length - 1] = (byte) 0x20; // Space character
		this.bloblPattern = pattern;
		binbuf = new byte[BUFFER_SIZE];
	}

	@Override
	public void write(int b) throws IOException {
		onebytebuf[0] = (byte) b;
		write(onebytebuf, 0, 1);
	}

	@Override
	public void write(byte[] b) throws IOException {
		int overflow = buffer(b, 0, b.length);
		if (overflow > 0)
			write(b, b.length - overflow, overflow);
	}

	@Override
	public void write(byte[] b, final int startOff, final int startLen)
			throws IOException {
		final int overflow = buffer(b, startOff, startLen);
		if (overflow < 0)
			return;
		final int off = startOff + startLen - overflow;
		final int len = overflow;
		if (len == 0)
			return;
		if (isBinary) {
			out.write(b, off, len);
			return;
		}
		IntList matchedPatternIndexes = matchPattern(b, off, len);

		if (matchedPatternIndexes.size() == 0) {
			out.write(b, off, len);
		} else {
			int start = off;
			int remaining = len;
			for (int matchedPatterNumber = 0; matchedPatterNumber < matchedPatternIndexes
					.size(); matchedPatterNumber++) {
				int matchedPatternIndex = matchedPatternIndexes
						.get(matchedPatterNumber);
				// Writes data
				int firstPartLength = matchedPatternIndex - start;
				out.write(b, start, firstPartLength);
				// Inserts blob name
				out.write(BLOB_NAME_PREFIX, 0, 1);
				out.write(bloblPattern, 0, bloblPattern.length);
				start = matchedPatternIndex;
				remaining -= firstPartLength;
			}
			// Writes remaining data
			if (remaining > 0) {
				out.write(b, start, remaining);
			}

		}

	}

	/**
	 * Matches the ident pattern from input.
	 *
	 * @param b
	 * @param off
	 * @param len
	 * @return an {@link InlineView} holding the index of the last byte of the
	 *         ident pattern ( second '$')
	 */
	private IntList matchPattern(byte[] b, final int off, final int len) {
		IntList matchedPatternIndexes = new IntList();
		for (int i = off; i < off + len; ++i) {
			final byte currentByte = b[i];
			// Matches pattern
			if (currentByte == IDENT_PATTERN[patternCounter]) {
				if (patternCounter == IDENT_PATTERN.length - 1) {
					matchedPatternIndexes.add(i);
					patternCounter = 0;
				} else {
					patternCounter++;
				}
			} else {
				patternCounter = 0;
			}
		}
		return matchedPatternIndexes;
	}

	// Copied from org.eclipse.jgit.util.io.AutoCRLFInputStream.fillBuffer()
	private int buffer(byte[] b, int off, int len) throws IOException {
		if (binbufcnt > binbuf.length)
			return len;
		int copy = Math.min(binbuf.length - binbufcnt, len);
		System.arraycopy(b, off, binbuf, binbufcnt, copy);
		binbufcnt += copy;
		int remaining = len - copy;
		if (remaining > 0)
			decideMode();
		return remaining;
	}

	// Copied from org.eclipse.jgit.util.io.AutoCRLFInputStream.decideMode()
	private void decideMode() throws IOException {
		isBinary = RawText.isBinary(binbuf, binbufcnt);
		int cachedLen = binbufcnt;
		binbufcnt = binbuf.length + 1; // full!
		write(binbuf, 0, cachedLen);
	}

	@Override
	public void flush() throws IOException {
		if (binbufcnt <= binbuf.length)
			decideMode();
		out.flush();
	}

	@Override
	public void close() throws IOException {
		flush();
		out.close();
	}

}