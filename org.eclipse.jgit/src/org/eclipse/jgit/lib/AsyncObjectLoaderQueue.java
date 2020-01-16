/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.errors.MissingObjectException;

/**
 * Queue to open objects asynchronously.
 *
 * A queue may perform background decompression of objects and supply them
 * (possibly out-of-order) to the application.
 *
 * @param <T>
 *            type of identifier supplied to the call that made the queue.
 */
public interface AsyncObjectLoaderQueue<T extends ObjectId> extends
		AsyncOperation {

	/**
	 * Position this queue onto the next available result.
	 *
	 * Even if this method returns true, {@link #open()} may still throw
	 * {@link org.eclipse.jgit.errors.MissingObjectException} if the underlying
	 * object database was concurrently modified and the current object is no
	 * longer available.
	 *
	 * @return true if there is a result available; false if the queue has
	 *         finished its input iteration.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist. If the implementation is retaining
	 *             the application's objects {@link #getCurrent()} will be the
	 *             current object that is missing. There may be more results
	 *             still available, so the caller should continue invoking next
	 *             to examine another result.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	boolean next() throws MissingObjectException, IOException;

	/**
	 * Get the current object, null if the implementation lost track.
	 *
	 * @return the current object, null if the implementation lost track.
	 *         Implementations may for performance reasons discard the caller's
	 *         ObjectId and provider their own through {@link #getObjectId()}.
	 */
	T getCurrent();

	/**
	 * Get the ObjectId of the current object. Never null.
	 *
	 * @return the ObjectId of the current object. Never null.
	 */
	ObjectId getObjectId();

	/**
	 * Obtain a loader to read the object.
	 *
	 * This method can only be invoked once per result
	 *
	 * Due to race conditions with a concurrent modification of the underlying
	 * object database, an object may be unavailable when this method is
	 * invoked, even though next returned successfully.
	 *
	 * @return the ObjectLoader to read this object. Never null.
	 * @throws MissingObjectException
	 *             the object does not exist. If the implementation is retaining
	 *             the application's objects {@link #getCurrent()} will be the
	 *             current object that is missing. There may be more results
	 *             still available, so the caller should continue invoking next
	 *             to examine another result.
	 * @throws java.io.IOException
	 *             the object store cannot be accessed.
	 */
	ObjectLoader open() throws IOException;
}
