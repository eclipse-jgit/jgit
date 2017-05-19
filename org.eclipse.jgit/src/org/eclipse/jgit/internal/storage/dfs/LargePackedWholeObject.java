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

package org.eclipse.jgit.internal.storage.dfs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;

final class LargePackedWholeObject extends ObjectLoader {
	private final int type;

	private final long size;

	private final long objectOffset;

	private final int headerLength;

	private final DfsPackFile pack;

	private final DfsObjDatabase db;

	LargePackedWholeObject(int type, long size, long objectOffset,
			int headerLength, DfsPackFile pack, DfsObjDatabase db) {
		this.type = type;
		this.size = size;
		this.objectOffset = objectOffset;
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
		throw new LargeObjectException();
	}

	@Override
	public ObjectStream openStream() throws MissingObjectException, IOException {
		DfsReader ctx = db.newReader();
		InputStream in;
		try {
			in = new PackInputStream(pack, objectOffset + headerLength, ctx);
		} catch (IOException packGone) {
			// If the pack file cannot be pinned into the cursor, it
			// probably was repacked recently. Go find the object
			// again and open the stream from that location instead.
			//
			try {
				ObjectId obj = pack.getReverseIdx(ctx).findObject(objectOffset);
				return ctx.open(obj, type).openStream();
			} finally {
				ctx.close();
			}
		} finally {
			ctx.close();
		}

		// Align buffer to inflater size, at a larger than default block.
		// This reduces the number of context switches from the
		// caller down into the pack stream inflation.
		int bufsz = 8192;
		in = new BufferedInputStream(
				new InflaterInputStream(in, ctx.inflater(), bufsz),
				bufsz);
		return new ObjectStream.Filter(type, size, in);
	}
}
