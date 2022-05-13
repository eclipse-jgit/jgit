/*
 * Copyright (C) 2022 Matthias Sohn <matthias.sohn@sap.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

/**
 * Factory for creating a FileWindowReader.
 */
class FileWindowReaderFactory {
	private static boolean mmap = false;

	/**
	 * Whether to map file windows to memory
	 *
	 * @param useMmap
	 *            whether to map file windows to memory
	 */
	static void setMmap(boolean useMmap) {
		mmap = useMmap;
	}

	/**
	 * Create a new {@code FileWindowReader} for the given pack
	 *
	 * @param pack
	 *            the pack
	 * @return the new {@code FileWindowReader}
	 */
	static FileWindowReader create(Pack pack) {
		if (mmap) {
			return new MmapNioFileWindowReader(pack);
		}
		return new HeapFileWindowReader(pack);
	}
}
