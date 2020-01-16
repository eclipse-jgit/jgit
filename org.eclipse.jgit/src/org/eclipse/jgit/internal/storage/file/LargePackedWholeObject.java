/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;

class LargePackedWholeObject extends ObjectLoader {
	private final int type;

	private final long size;

	private final long objectOffset;

	private final int headerLength;

	private final PackFile pack;

	private final FileObjectDatabase db;

	LargePackedWholeObject(int type, long size, long objectOffset,
			int headerLength, PackFile pack, FileObjectDatabase db) {
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
		try {
			throw new LargeObjectException(getObjectId());
		} catch (IOException cannotObtainId) {
			throw new LargeObjectException(cannotObtainId);
		}
	}

	/** {@inheritDoc} */
	@Override
	public ObjectStream openStream() throws MissingObjectException, IOException {
		WindowCursor wc = new WindowCursor(db);
		InputStream in;
		try {
			in = new PackInputStream(pack, objectOffset + headerLength, wc);
		} catch (IOException packGone) {
			// If the pack file cannot be pinned into the cursor, it
			// probably was repacked recently. Go find the object
			// again and open the stream from that location instead.
			//
			return wc.open(getObjectId(), type).openStream();
		}

		in = new BufferedInputStream( //
				new InflaterInputStream( //
						in, //
						wc.inflater(), //
						8192), //
				8192);
		return new ObjectStream.Filter(type, size, in);
	}

	private ObjectId getObjectId() throws IOException {
		return pack.findObjectForOffset(objectOffset);
	}
}
