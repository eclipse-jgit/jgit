/*
 * Copyright (C) 2024, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Represents a function that accepts a collection of objects to write into a
 * primary pack index storage format.
 */
public interface PackIndexWriter {
	/**
	 * Write all object entries to the index stream.
	 *
	 * @param toStore
	 *            sorted list of objects to store in the index. The caller must
	 *            have previously sorted the list using
	 *            {@link org.eclipse.jgit.transport.PackedObjectInfo}'s native
	 *            {@link java.lang.Comparable} implementation.
	 * @param packDataChecksum
	 *            checksum signature of the entire pack data content. This is
	 *            traditionally the last 20 bytes of the pack file's own stream.
	 * @throws java.io.IOException
	 *             an error occurred while writing to the output stream, or the
	 *             underlying format cannot store the object data supplied.
	 */
	void write(List<? extends PackedObjectInfo> toStore,
			byte[] packDataChecksum) throws IOException;
}
