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

import org.eclipse.jgit.internal.storage.file.PackBitmapIndexBuilder;

/**
 * Represents a function that accepts a collection of bitmaps and write them
 * into storage.
 */
@FunctionalInterface
public interface PackBitmapIndexWriter {
	/**
	 * @param bitmaps
	 *            list of bitmaps to be written to a bitmap index
	 * @param packChecksum
	 *            checksum of the pack that the bitmap index refers to
	 * @throws IOException
	 *             thrown in case of IO errors while writing the bitmap index
	 */
	public void write(PackBitmapIndexBuilder bitmaps, byte[] packChecksum)
			throws IOException;
}