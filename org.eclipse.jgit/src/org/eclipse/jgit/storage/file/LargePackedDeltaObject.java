/*
 * Copyright (C) 2010, Google Inc.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.storage.pack.DeltaStream;

class LargePackedDeltaObject extends ObjectLoader {
	private final int type;

	private final long size;

	private final long objectOffset;

	private final long baseOffset;

	private final int headerLength;

	private final PackFile pack;

	private final FileObjectDatabase db;

	LargePackedDeltaObject(int type, long size, long objectOffset,
			long baseOffset, int headerLength, PackFile pack,
			FileObjectDatabase db) {
		this.type = type;
		this.size = size;
		this.objectOffset = objectOffset;
		this.baseOffset = baseOffset;
		this.headerLength = headerLength;
		this.pack = pack;
		this.db = db;
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public long getSize() {
		return size;
	}

	@Override
	public boolean isLarge() {
		return true;
	}

	@Override
	public byte[] getCachedBytes() throws LargeObjectException {
		try {
			throw new LargeObjectException(getObjectId());
		} catch (IOException cannotObtainId) {
			throw new LargeObjectException();
		}
	}

	@Override
	public ObjectStream openStream() throws MissingObjectException, IOException {
		final WindowCursor wc = new WindowCursor(db);
		InputStream in = open(wc);
		in = new BufferedInputStream(in, 8192);
		return new ObjectStream.Filter(type, size, in) {
			@Override
			public void close() throws IOException {
				wc.release();
				super.close();
			}
		};
	}

	private InputStream open(final WindowCursor wc)
			throws MissingObjectException, IOException,
			IncorrectObjectTypeException {
		InputStream delta;
		try {
			delta = new PackInputStream(pack, objectOffset + headerLength, wc);
		} catch (IOException packGone) {
			// If the pack file cannot be pinned into the cursor, it
			// probably was repacked recently. Go find the object
			// again and open the stream from that location instead.
			//
			return wc.open(getObjectId(), type).openStream();
		}
		delta = new InflaterInputStream(delta);

		final ObjectLoader base = pack.load(wc, baseOffset);
		return new DeltaStream(delta) {
			@Override
			protected InputStream openBase() throws IOException {
				if (base instanceof LargePackedDeltaObject)
					return ((LargePackedDeltaObject) base).open(wc);
				return base.openStream();
			}

			@Override
			protected long getBaseSize() throws IOException {
				return base.getSize();
			}
		};
	}

	private ObjectId getObjectId() throws IOException {
		return pack.findObjectForOffset(objectOffset);
	}
}
