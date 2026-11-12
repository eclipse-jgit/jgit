/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.BufferedInputStream;
import java.io.IOException;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;

/**
 * Loader for a blob held by an {@link ExternalObjectStore}.
 * <p>
 * The size is known when the loader is created; content is only read when
 * {@link #openStream()} is called, once per call. Externally stored blobs are
 * always treated as large: they are streamed, never held in memory as a
 * whole.
 */
class ExternalObjectLoader extends ObjectLoader {

	private final ExternalObjectStore store;

	private final ObjectId id;

	private final long size;

	ExternalObjectLoader(ExternalObjectStore store, ObjectId id, long size) {
		this.store = store;
		this.id = id;
		this.size = size;
	}

	@Override
	public int getType() {
		return OBJ_BLOB;
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
		throw new LargeObjectException(id);
	}

	@Override
	public ObjectStream openStream() throws IOException {
		return new ObjectStream.Filter(OBJ_BLOB, size,
				new BufferedInputStream(store.open(id)));
	}
}
