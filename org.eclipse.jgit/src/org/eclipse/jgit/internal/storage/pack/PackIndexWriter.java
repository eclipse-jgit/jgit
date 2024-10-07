/*
 * Copyright (C) 2024, Google Inc.
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
 * pack index storage format.
 */
@FunctionalInterface
public interface PackIndexWriter {
	/**
	 * @param toStore
	 *            list of objects that will pack written to pack index
	 * @param packDataChecksum
	 *            checksum of the pack that the index refers to
	 * @throws IOException
	 *             thrown in case of IO errors while writing the index
	 */
	public void write(final List<? extends PackedObjectInfo> toStore,
			final byte[] packDataChecksum) throws IOException;
}
