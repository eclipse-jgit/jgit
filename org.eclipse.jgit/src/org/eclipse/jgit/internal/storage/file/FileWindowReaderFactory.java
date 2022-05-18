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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating a FileWindowReader.
 */
class FileWindowReaderFactory {
	private static final Logger LOG = LoggerFactory
			.getLogger(FileWindowReaderFactory.class);

	private volatile static Constructor<?> panamaImplCtor = getPanamaImplCtor();

	private static Constructor<?> getPanamaImplCtor() {
		if (panamaImplCtor == null) {
			if (Runtime.version().feature() >= 18) {
				try {
					Class<?> clazz = Class.forName(
							"org.eclipse.jgit.panama.internal.PanamaFileWindowReader"); //$NON-NLS-1$
					if (clazz != null) {
						panamaImplCtor = clazz.getConstructor(Pack.class);
					}
				} catch (ClassNotFoundException | NoSuchMethodException
						| SecurityException e) {
					LOG.debug(e.getMessage(), e);
				}
			}
		}
		return panamaImplCtor;
	}

	private static FileWindowReader createPanamaFileWindowReader(Pack pack) {
		if (panamaImplCtor != null) {
			try {
				return (FileWindowReader) panamaImplCtor.newInstance(pack);
			} catch (InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException e) {
				LOG.error(e.getMessage(), e);
			}
		}
		return null;
	}

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
		FileWindowReader r = null;
		if (mmap) {
			r = createPanamaFileWindowReader(pack);
			if (r == null) {
				return new MmapFileWindowReader(pack);
			}
		} else {
			r = new HeapFileWindowReader(pack);
		}
		return r;
	}
}
