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

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.lib.AnyObjectId;

/**
 * Storage backend for objects kept outside the repository's object directory.
 * <p>
 * A store keeps blobs, keyed by object id, and is the only record of what it
 * holds. Callers ask {@link #find(AnyObjectId)} whether a blob is stored;
 * because an implementation may answer by contacting a remote service,
 * callers should consult a store only after cheaper local sources.
 * <p>
 * A store never makes content visible under an id it does not hash to, so
 * everything it returns can be trusted to match its id.
 */
public interface ExternalObjectStore extends AutoCloseable {

	/**
	 * Look up an object.
	 * <p>
	 * Only a definite answer is returned: if the store cannot tell whether it
	 * holds the object, for example because a remote service is unreachable,
	 * this method throws instead of returning {@code -1}.
	 *
	 * @param id
	 *            id of the object
	 * @return size of the object's content in bytes, without the Git object
	 *         header, or {@code -1} if the store does not hold the object
	 * @throws IOException
	 *             the store could not be queried
	 */
	long find(AnyObjectId id) throws IOException;

	/**
	 * Open the content of an object.
	 *
	 * @param id
	 *            id of the object
	 * @return stream over the object's content, without the Git object
	 *         header. The caller must close it.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the store does not hold the object
	 * @throws IOException
	 *             the store could not be read
	 */
	InputStream open(AnyObjectId id) throws IOException;

	/**
	 * Store the content of a blob.
	 * <p>
	 * The store verifies that {@code content} is the blob whose id is
	 * {@code id} before the object becomes visible to
	 * {@link #find(AnyObjectId)} and {@link #open(AnyObjectId)}. If it is
	 * not, or if its length does not match {@code size}, nothing is stored.
	 * Storing an object that is already present is allowed.
	 *
	 * @param id
	 *            id of the blob
	 * @param size
	 *            number of bytes in {@code content}
	 * @param content
	 *            the blob's content, without the Git object header. The
	 *            caller keeps ownership of the stream.
	 * @throws IOException
	 *             the content could not be stored, its length does not
	 *             match {@code size}, or it does not hash to {@code id}
	 */
	void put(AnyObjectId id, long size, InputStream content)
			throws IOException;

	/**
	 * Delete an object. Deleting an object that is not stored is not an
	 * error.
	 *
	 * @param id
	 *            id of the object
	 * @throws IOException
	 *             the object could not be deleted
	 */
	void delete(AnyObjectId id) throws IOException;

	/**
	 * Release any resources held by this store.
	 */
	@Override
	void close();
}
