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
 * A store keeps raw object content, without the Git object header, keyed by
 * object id. It is never asked whether an object exists: callers track the
 * stored objects in a catalog, so a store only receives requests for objects
 * it holds. This keeps implementations simple and avoids remote calls for
 * objects that are not stored externally.
 */
public interface ExternalObjectStore extends AutoCloseable {

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
	 * Store the content of an object.
	 * <p>
	 * The object becomes visible to {@link #open(AnyObjectId)} only after all
	 * of its content has been written. Storing an object that is already
	 * present is allowed.
	 *
	 * @param id
	 *            id of the object
	 * @param size
	 *            number of bytes in {@code content}
	 * @param content
	 *            the object's content, without the Git object header. The
	 *            caller keeps ownership of the stream.
	 * @throws IOException
	 *             the content could not be stored, or its length does not
	 *             match {@code size}
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
