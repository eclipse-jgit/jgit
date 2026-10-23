/*
 * Copyright (C) 2026 Shawn Hartsock <shartsock@nvidia.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.io.Serializable;

/**
 * Request to fetch the content of one promised object from a
 * {@link PromisedObjectResolver}.
 * <p>
 * A request names exactly one object: the repository namespace, the complete
 * object id, the expected type and, when known, the expected length.
 * Resolvers are never asked to search by any other criteria.
 * <p>
 * Experimental API: not yet frozen and may change without notice.
 *
 * @since 8.0
 */
public final class PromisedObjectRequest implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String namespace;

	private final ObjectId objectId;

	private final int objectType;

	private final long expectedLength;

	/**
	 * Creates a request.
	 *
	 * @param namespace
	 *            repository namespace, for resolvers serving more than one
	 *            repository; must not be {@code null} or empty.
	 * @param objectId
	 *            complete object id of the promised object.
	 * @param objectType
	 *            expected object type, one of the
	 *            {@link org.eclipse.jgit.lib.Constants} {@code OBJ_*} constants.
	 * @param expectedLength
	 *            expected content length in bytes, or {@code -1} when unknown.
	 */
	public PromisedObjectRequest(String namespace,
			ObjectId objectId, int objectType, long expectedLength) {
		if (namespace == null || namespace.isEmpty()) {
			throw new IllegalArgumentException(
					"namespace must not be null or empty"); //$NON-NLS-1$
		}
		if (objectId == null) {
			throw new IllegalArgumentException("objectId must not be null"); //$NON-NLS-1$
		}
		this.namespace = namespace;
		this.objectId = objectId;
		this.objectType = objectType;
		this.expectedLength = expectedLength;
	}

	/**
	 * Get the repository namespace of the promise.
	 *
	 * @return the namespace, never {@code null}.
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * Get the complete object id of the promised object.
	 *
	 * @return the object id, never {@code null}.
	 */
	public ObjectId getObjectId() {
		return objectId;
	}

	/**
	 * Get the expected object type.
	 *
	 * @return one of the {@link org.eclipse.jgit.lib.Constants} {@code OBJ_*}
	 *         constants.
	 */
	public int getObjectType() {
		return objectType;
	}

	/**
	 * Get the expected content length.
	 *
	 * @return expected length in bytes, or {@code -1} when unknown.
	 */
	public long getExpectedLength() {
		return expectedLength;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "PromisedObjectRequest[" + namespace + ", " + objectId.name() //$NON-NLS-1$ //$NON-NLS-2$
				+ ", type=" + Integer.toString(objectType) //$NON-NLS-1$
				+ ", expectedLength=" + Long.toString(expectedLength) + ']'; //$NON-NLS-1$
	}
}
