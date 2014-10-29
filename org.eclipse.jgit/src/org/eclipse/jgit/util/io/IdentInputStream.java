/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr>
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
import java.io.InputStream;

import org.eclipse.jgit.diff.RawText;

/**
 * Input stream that replaces on the fly "$Id: {BLOB_NAME} $" to "$Id$"
 * <p>
 * BLOB_NAME is the 40 character length id of the blob.
 * </p>
 */
public class IdentInputStream extends InputStream {

	/**
	 * Size of the auxiliary buffer.
	 */
	static final int BUFFER_SIZE = 8096;

	/**
	 * First part of the "Ident" pattern.
	 */
	private static final byte[] START_IDENT_PATTERN = new byte[] { '$', 'I',
			'd', ':' };

	/**
	 * Closing character for the ident pattern.
	 */
	private static final byte END_IDENT_PATTERN = '$';

	/**
	 * Used for {@link #read()} method.
	 */
	private final byte[] single = new byte[1];

	/**
	 * Buffer between this stream and the wrapped input stream.
	 */
	private final byte[] auxBuffer = new byte[BUFFER_SIZE];

	/**
	 * Number of valid byte in {@link #auxBuffer} to read.
	 */
	private int auxBufferLengh;

	/**
	 * Pointer to the current byte to read from {@link #auxBuffer}
	 */
	private int auxBufferIndex;

	/**
	 * Input stream used as input for for this stream.
	 */
	private final InputStream wrappedInputStream;

	/**
	 * This buffer is filled with the next 43 bytes after matching the "ident"
	 * start pattern.
	 * <p>
	 * 1 space + 40 (Blob object name length) + 1 space + 1 (final '$') = 43
	 *
	 * <pre>
	 *      |               [memory]                  |
	 *      v                                         v
	 * "$Id: 0000000000000000000000000000000000000000 $"
	 * </pre>
	 *
	 * </p>
	 *
	 * @see #START_IDENT_PATTERN
	 * @see #END_IDENT_PATTERN
	 */
	private final byte[] memory = new byte[43];

	/**
	 * Holds the number of bytes matching the pattern in a row.
	 */
	private int matchingByteFound = 0;

	/**
	 * Pointer to the current byte to read from {@link #memory}
	 */
	private int memoryIndex = 0;

	/**
	 * Number of valid byte in {@link #memory} to read.
	 */
	private int memoryBufferLengh = 0;

	private boolean isBinary;

	private boolean detectBinary;

	private boolean abortIfBinary;

	/**
	 * A special exception thrown when {@link IdentInputStream} is told to throw
	 * an exception when attempting to read a binary file. The exception may be
	 * thrown at any stage during reading.
	 */
	public static class IsBinaryException extends IOException {
		private static final long serialVersionUID = 1L;

		IsBinaryException() {
			super();
		}
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 */
	public IdentInputStream(InputStream in, boolean detectBinary) {
		this(in, detectBinary, false);
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @param abortIfBinary
	 *            throw an {@link IsBinaryException} if the file is binary
	 */
	public IdentInputStream(InputStream in, boolean detectBinary,
			boolean abortIfBinary) {
		this.wrappedInputStream = in;
		this.detectBinary = detectBinary;
		this.abortIfBinary = abortIfBinary;
	}

	@Override
	public int read() throws IOException {
		final int read = read(single, 0, 1);
		return read == 1 ? single[0] & 0xff : -1;
	}

	@Override
	public int read(byte[] bs, final int off, final int len) throws IOException {
		if (len == 0)
			return 0;
		// If nothing left to read in auxStream or memory buffer stop reading
		if (auxBufferLengh == -1 && !isMemoryBufferNotEmpty())
			return -1;

		int currentReadBufferIndex = off;
		int lastByteToReadIndex = off + len;

		// Set to true if something was written if bs
		boolean somethingWritten = false;

		while (currentReadBufferIndex < lastByteToReadIndex) {
			if (!hasNextByte()) {
				break;
			}

			byte currentAuxBufferCurentByte = getNextByte();

			if (matchPattern(currentAuxBufferCurentByte)) {
				currentAuxBufferCurentByte = END_IDENT_PATTERN;
			}

			bs[currentReadBufferIndex++] = currentAuxBufferCurentByte;
			somethingWritten = true;
		}

		return somethingWritten ? currentReadBufferIndex - off : -1;
	}

	/**
	 * Tries to match the "ident" pattern.
	 *
	 * @param currentAuxBufferCurentByte
	 *            Current byte.
	 * @return True if the pattern has been matched.
	 * @throws IOException
	 * @see #START_IDENT_PATTERN
	 * @see #END_IDENT_PATTERN
	 */
	private boolean matchPattern(byte currentAuxBufferCurentByte)
			throws IOException {
		boolean patternMatched = false;
		// Matches pattern
		if (currentAuxBufferCurentByte == START_IDENT_PATTERN[matchingByteFound]) {
			if (matchingByteFound == START_IDENT_PATTERN.length - 1) {
				matchingByteFound = 0;
				fillMemoryBuffer();
				// If the pattern is matched clear memory and go on reading
				if (memoryBufferLengh == memory.length
						&& memory[memoryBufferLengh - 1] == END_IDENT_PATTERN) {

					patternMatched = true;
					// Empties memory
					memoryIndex = 0;
					memoryBufferLengh = 0;
				}

			} else {
				matchingByteFound++;
			}
		} else {
			matchingByteFound = 0;
		}
		return patternMatched;
	}

	/**
	 * @return {@code true} if there is something left to read, {@code false}
	 *         otherwise.
	 * @throws IOException
	 */
	private boolean hasNextByte() throws IOException {
		return isMemoryBufferNotEmpty() || auxBufferIndex < auxBufferLengh
				|| fillAuxBuffer();
	}

	/**
	 * Returns the next byte to use.
	 * <p>
	 * This byte comes either from the auxiliary input stream (
	 * {@link #wrappedInputStream}) or from the memory buffer ({@link #memory}
	 * </p>
	 *
	 * @return the next byte.
	 */
	private byte getNextByte() {
		final byte currentAuxBufferCurentByte;
		if (isMemoryBufferNotEmpty()) {
			currentAuxBufferCurentByte = memory[memoryIndex++];
		} else {
			currentAuxBufferCurentByte = auxBuffer[auxBufferIndex++];
		}
		return currentAuxBufferCurentByte;
	}

	/**
	 * @return True if there is something left in the memory buffer
	 */
	private boolean isMemoryBufferNotEmpty() {
		return memoryIndex < memoryBufferLengh;
	}

	/**
	 * Fills the memory buffer (maximum 41 bytes need for matching the pattern).
	 * <p>
	 * This method may update {@link #auxBufferIndex} and
	 * {@link #auxBufferLengh} since {@link #auxBuffer} may need to be refilled.
	 * </p>
	 *
	 * @throws IOException
	 */
	private void fillMemoryBuffer() throws IOException {
		int memoryBufferLenghAux = 0;
		for (int index = 0; index < memory.length; index++) {
			if (hasNextByte()) {
				memory[index] = getNextByte();
				memoryBufferLenghAux = index + 1;
			} else {
				break;
			}

		}
		memoryIndex = 0;
		memoryBufferLengh = memoryBufferLenghAux;

	}

	/**
	 * @return true if the stream has detected as a binary so far
	 * @since 3.4
	 */
	public boolean isBinary() {
		return isBinary;
	}

	@Override
	public void close() throws IOException {
		wrappedInputStream.close();
	}

	/**
	 * @return false if the end of {@link #wrappedInputStream} or if the
	 *         {@link #wrappedInputStream} is a binary
	 * @throws IOException
	 */
	private boolean fillAuxBuffer() throws IOException {
		auxBufferLengh = wrappedInputStream
				.read(auxBuffer, 0, auxBuffer.length);
		if (auxBufferLengh < 1)
			return false;
		if (detectBinary) {
			isBinary = RawText.isBinary(auxBuffer, auxBufferLengh);
			detectBinary = false;
			if (isBinary && abortIfBinary)
				throw new IsBinaryException();
		}
		auxBufferIndex = 0;
		return true;
	}

}
