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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.transport.PackParser;

/**
 * Inserter for an {@link ExternalObjectDatabase}.
 * <p>
 * Blobs are written to the store immediately and are visible as soon as
 * {@link #insert(AnyObjectId, int, long, InputStream)} returns; the store
 * checks that their content matches their id, so {@link #flush()} has
 * nothing to do.
 * <p>
 * Only blobs whose id is known in advance can be inserted, because the store
 * is keyed by id and verifies the content against it while writing.
 */
public class ExternalObjectInserter extends ObjectInserter {

	private final ExternalObjectDatabase db;

	ExternalObjectInserter(ExternalObjectDatabase db) {
		this.db = db;
	}

	/**
	 * Store a blob whose id is known in advance.
	 * <p>
	 * If the store already holds the blob, its content is not read. Otherwise
	 * the store verifies that the content hashes to {@code expectedId} before
	 * the blob becomes visible.
	 *
	 * @param expectedId
	 *            id of the blob
	 * @param type
	 *            type of the object; must be {@code Constants.OBJ_BLOB}
	 * @param size
	 *            size of the blob's content in bytes
	 * @param in
	 *            the blob's content, without the Git object header
	 * @return {@code expectedId}
	 * @throws IllegalArgumentException
	 *             {@code type} is not {@code Constants.OBJ_BLOB}
	 * @throws IOException
	 *             the blob could not be stored, or its content does not
	 *             match {@code expectedId} or {@code size}
	 */
	public ObjectId insert(AnyObjectId expectedId, int type, long size,
			InputStream in) throws IOException {
		if (type != OBJ_BLOB) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().externalObjectStoreBlobsOnly,
					expectedId.name(), Constants.typeString(type)));
		}
		ExternalObjectStore store = db.getStore();
		if (store.find(expectedId) < 0) {
			store.put(expectedId, size, in);
		}
		return expectedId.copy();
	}

	/**
	 * Not supported: the store needs the id before the content is written.
	 * Use {@link #insert(AnyObjectId, int, long, InputStream)}.
	 *
	 * @throws UnsupportedOperationException
	 *             always
	 */
	@Override
	public ObjectId insert(int objectType, long length, InputStream in) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Not supported: packs are never written to an external store.
	 *
	 * @throws UnsupportedOperationException
	 *             always
	 */
	@Override
	public PackParser newPackParser(InputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ObjectReader newReader() {
		return db.newReader();
	}

	@Override
	public void flush() {
		// Blobs are visible as soon as they are inserted.
	}

	@Override
	public void close() {
		// Nothing to release.
	}
}
