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

package org.eclipse.jgit.storage.file;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;

import javaewah.EWAHCompressedBitmap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.storage.file.PackBitmapIndexBuilder.StoredEntry;

/**
 * Creates the version E003 pack table of contents files.
 *
 * @see PackIndexWriter
 * @see PackIndexVE003
 */
class PackIndexWriterVE003 extends PackIndexWriterV2 {
	private final DataOutput dataOutput;

	PackIndexWriterVE003(final OutputStream dst) {
		super(dst);
		dataOutput = new SimpleDataOutput(out);
	}

	@Override
	protected void writeImpl() throws IOException {
		if (bitmaps == null)
			throw new IllegalStateException();

		writeTOC(0xE003);
		writeV2Body();
		writeVE003Body();
		writeChecksumFooter();
	}

	private void writeVE003Body() throws IOException {
		writeOptions();
		writeBitmap(bitmaps.getCommits());
		writeBitmap(bitmaps.getTrees());
		writeBitmap(bitmaps.getBlobs());
		writeBitmap(bitmaps.getTags());
		writeBitmaps();
	}

	private void writeOptions() throws IOException {
		out.write(bitmaps.getOptions());
	}

	private void writeBitmap(EWAHCompressedBitmap bitmap) throws IOException {
		bitmap.serialize(dataOutput);
	}

	private void writeBitmaps() throws IOException {
		// Write number of entries
		int expectedBitmapCount = bitmaps.getBitmapCount();
		dataOutput.writeInt(expectedBitmapCount);

		int bitmapCount = 0;
		for (StoredEntry entry : bitmaps.getCompressedBitmaps()) {
			writeBitmapEntry(entry);
			bitmapCount++;
		}

		if (expectedBitmapCount != bitmapCount)
			throw new IOException(MessageFormat.format(
					JGitText.get().expectedGot,
					String.valueOf(expectedBitmapCount),
					String.valueOf(bitmapCount)));
	}

	private void writeBitmapEntry(StoredEntry entry) throws IOException {
		// Write object, xor offset, and bitmap
		dataOutput.writeInt((int) entry.getObjectId());
		out.write(entry.getXorOffset());
		writeBitmap(entry.getBitmap());
	}
}
