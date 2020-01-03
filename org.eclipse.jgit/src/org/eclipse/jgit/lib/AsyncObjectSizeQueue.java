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
 * Queue to examine object sizes asynchronously.
 *
 * A queue may perform background lookup of object sizes and supply them
 * (possibly out-of-order) to the application.
 *
 * @param <T>
 *            type of identifier supplied to the call that made the queue.
 */
public interface AsyncObjectSizeQueue<T extends ObjectId> extends
		AsyncOperation {

	/**
	 * Position this queue onto the next available result.
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
	 * <p>getCurrent.</p>
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
	 * Get the size of the current object.
	 *
	 * @return the size of the current object.
	 */
	long getSize();
}
