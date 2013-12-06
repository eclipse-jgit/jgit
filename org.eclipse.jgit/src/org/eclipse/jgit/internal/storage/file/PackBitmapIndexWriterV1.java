/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.text.MessageFormat;

import com.googlecode.javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder.StoredEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;

/**
 * Creates the version 1 pack bitmap index files.
 *
 * @see PackBitmapIndexV1
 */
public class PackBitmapIndexWriterV1 {
	private final DigestOutputStream out;
	private final DataOutput dataOutput;

	/**
	 * Creates the version 1 pack bitmap index files.
	 *
	 * @param dst
	 *            the output stream to which the index will be written.
	 */
	public PackBitmapIndexWriterV1(final OutputStream dst) {
		out = new DigestOutputStream(dst instanceof BufferedOutputStream ? dst
				: new SafeBufferedOutputStream(dst),
				Constants.newMessageDigest());
		dataOutput = new SimpleDataOutput(out);
	}

	/**
	 * Write all object entries to the index stream.
	 * <p>
	 * After writing the stream passed to the factory is flushed but remains
	 * open. Callers are always responsible for closing the output stream.
	 *
	 * @param bitmaps
	 *            the index data for the bitmaps
	 * @param packDataChecksum
	 *            checksum signature of the entire pack data content. This is
	 *            traditionally the last 20 bytes of the pack file's own stream.
	 * @throws IOException
	 *             an error occurred while writing to the output stream, or this
	 *             index format cannot store the object data supplied.
	 */
	public void write(PackBitmapIndexBuilder bitmaps, byte[] packDataChecksum)
			throws IOException {
		if (bitmaps == null || packDataChecksum.length != 20)
			throw new IllegalStateException();

		writeHeader(bitmaps.getOptions(), bitmaps.getBitmapCount(),
				packDataChecksum);
		writeBody(bitmaps);
		writeFooter();

		out.flush();
	}

	private void writeHeader(
			int options, int bitmapCount, byte[] packDataChecksum)
			throws IOException {
		out.write(PackBitmapIndexV1.MAGIC);
		dataOutput.writeShort(1);
		dataOutput.writeShort(options);
		dataOutput.writeInt(bitmapCount);
		out.write(packDataChecksum);
	}

	private void writeBody(PackBitmapIndexBuilder bitmaps) throws IOException {
		writeBitmap(bitmaps.getCommits());
		writeBitmap(bitmaps.getTrees());
		writeBitmap(bitmaps.getBlobs());
		writeBitmap(bitmaps.getTags());
		writeBitmaps(bitmaps);
	}

	private void writeBitmap(EWAHCompressedBitmap bitmap) throws IOException {
		bitmap.serialize(dataOutput);
	}

	private void writeBitmaps(PackBitmapIndexBuilder bitmaps)
			throws IOException {
		int bitmapCount = 0;
		for (StoredEntry entry : bitmaps.getCompressedBitmaps()) {
			writeBitmapEntry(entry);
			bitmapCount++;
		}

		int expectedBitmapCount = bitmaps.getBitmapCount();
		if (expectedBitmapCount != bitmapCount)
			throw new IOException(MessageFormat.format(
					JGitText.get().expectedGot,
					String.valueOf(expectedBitmapCount),
					String.valueOf(bitmapCount)));
	}

	private void writeBitmapEntry(StoredEntry entry) throws IOException {
		// Write object, XOR offset, and bitmap
		dataOutput.writeInt((int) entry.getObjectId());
		out.write(entry.getXorOffset());
		out.write(entry.getFlags());
		writeBitmap(entry.getBitmap());
	}

	private void writeFooter() throws IOException {
		out.on(false);
		out.write(out.getMessageDigest().digest());
	}
}
