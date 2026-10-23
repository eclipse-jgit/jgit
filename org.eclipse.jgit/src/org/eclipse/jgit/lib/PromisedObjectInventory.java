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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Explicit inventory of objects promised by an external store.
 * <p>
 * Within one repository namespace it lists the complete object ids and
 * expected content lengths a {@link PromisedObjectResolver} may be asked for.
 * Unlisted objects keep normal missing-object behavior, so a resolver is
 * never asked to search for arbitrary missing objects.
 * <p>
 * This prototype lists blobs only ({@link Constants#OBJ_BLOB}).
 * <p>
 * Experimental API: not yet frozen and may change without notice.
 *
 * @since 8.0
 */
public final class PromisedObjectInventory {

	private final String namespace;

	private final Map<ObjectId, Long> expectedLengths;

	private PromisedObjectInventory(String namespace,
			Map<ObjectId, Long> expectedLengths) {
		this.namespace = namespace;
		this.expectedLengths = expectedLengths;
	}

	/**
	 * Creates a builder for an inventory.
	 *
	 * @param namespace
	 *            repository namespace, for resolvers serving more than one
	 *            repository; must not be {@code null} or empty.
	 * @return a new builder.
	 */
	public static Builder builder(String namespace) {
		return new Builder(namespace);
	}

	/**
	 * Get the repository namespace described by this inventory.
	 *
	 * @return the namespace, never {@code null}.
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * Whether the inventory promises the given object.
	 *
	 * @param objectId
	 *            object id to test.
	 * @return {@code true} when the inventory lists the object.
	 */
	public boolean isPromised(AnyObjectId objectId) {
		return expectedLengths.containsKey(objectId);
	}

	/**
	 * Get the expected content length of a promised object.
	 *
	 * @param objectId
	 *            object id to look up.
	 * @return expected content length in bytes, or {@code -1} when the object
	 *         is not promised.
	 */
	public long getExpectedLength(AnyObjectId objectId) {
		Long len = expectedLengths.get(objectId);
		return len == null ? -1 : len.longValue();
	}

	/**
	 * Get the number of objects promised by this inventory.
	 *
	 * @return number of promised objects.
	 */
	public int size() {
		return expectedLengths.size();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "PromisedObjectInventory[" + namespace + ", " //$NON-NLS-1$ //$NON-NLS-2$
				+ Integer.toString(expectedLengths.size()) + " promises]"; //$NON-NLS-1$
	}

	/**
	 * Builder for {@link PromisedObjectInventory}.
	 */
	public static final class Builder {

		private final String namespace;

		private final Map<ObjectId, Long> expectedLengths = new HashMap<>();

		private Builder(String namespace) {
			if (namespace == null || namespace.isEmpty()) {
				throw new IllegalArgumentException(
						"namespace must not be null or empty"); //$NON-NLS-1$
			}
			this.namespace = namespace;
		}

		/**
		 * Records one promised object.
		 *
		 * @param objectId
		 *            complete object id of the promised blob.
		 * @param expectedLength
		 *            expected content length in bytes; must not be negative.
		 * @return this builder.
		 */
		public Builder add(AnyObjectId objectId,
				long expectedLength) {
			if (objectId == null) {
				throw new IllegalArgumentException(
						"objectId must not be null"); //$NON-NLS-1$
			}
			if (expectedLength < 0) {
				throw new IllegalArgumentException(
						"expectedLength must not be negative: " //$NON-NLS-1$
								+ Long.toString(expectedLength));
			}
			expectedLengths.put(objectId.copy(), Long.valueOf(expectedLength));
			return this;
		}

		/**
		 * Builds the inventory.
		 *
		 * @return immutable inventory with the recorded promises.
		 */
		public PromisedObjectInventory build() {
			return new PromisedObjectInventory(namespace,
					Collections.unmodifiableMap(
							new HashMap<>(expectedLengths)));
		}
	}
}
