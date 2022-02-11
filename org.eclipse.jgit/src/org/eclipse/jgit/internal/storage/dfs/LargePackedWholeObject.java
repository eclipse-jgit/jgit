/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	/** {@inheritDoc} */
	@Override
	public int getType() {
		return type;
	}

	/** {@inheritDoc} */
	@Override
	public long getSize() {
		return size;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isLarge() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public byte[] getCachedBytes() throws LargeObjectException {
		throw new LargeObjectException();
	}

	/** {@inheritDoc} */
	@Override
	public ObjectStream openStream() throws MissingObjectException, IOException {
		PackInputStream packIn;
		// ctx is closed by PackInputStream, or explicitly in the finally block
		DfsReader ctx = db.newReader();
		try {
			try {
				packIn = new PackInputStream(
						pack, objectOffset + headerLength, ctx);
				ctx = null; // owned by packIn
			} catch (IOException packGone) {
				// If the pack file cannot be pinned into the cursor, it
				// probably was repacked recently. Go find the object
				// again and open the stream from that location instead.
				ObjectId obj = pack.getReverseIdx(ctx).findObject(objectOffset);
				return ctx.open(obj, type).openStream();
			}
		} finally {
			if (ctx != null) {
				ctx.close();
			}
		}

		// Align buffer to inflater size, at a larger than default block.
		// This reduces the number of context switches from the
		// caller down into the pack stream inflation.
		int bufsz = 8192;
		InputStream in = new BufferedInputStream(
				new InflaterInputStream(packIn, packIn.ctx.inflater(), bufsz),
				bufsz);
		return new ObjectStream.Filter(type, size, in);
	}
}
