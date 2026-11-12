/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Selects received objects by their resolved identity, type and inflated size.
 * <p>
 * A filter must be deterministic. It is evaluated once per distinct received
 * object ID, on the first reconstructed representation. It is not evaluated
 * for an external base appended to repair a thin pack. The object contents and
 * paths are not available to this callback.
 *
 * @since 8.0
 */
@FunctionalInterface
public interface ReceivedObjectFilter {
	/**
	 * Select a received object.
	 *
	 * @param id
	 *            resolved object ID.
	 * @param type
	 *            resolved object type, not a delta type code.
	 * @param inflatedSize
	 *            full object size in bytes, without its object header.
	 * @return true to select this object.
	 */
	boolean select(ObjectId id, int type, long inflatedSize);
}
