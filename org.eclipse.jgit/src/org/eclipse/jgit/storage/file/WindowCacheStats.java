/*
 * Copyright (C) 2018, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.storage.file;

import org.eclipse.jgit.internal.storage.file.WindowCache;

/**
 * Accessor for stats about {@link WindowCache}.
 *
 * @since 4.11
 *
 */
public class WindowCacheStats {
	/**
	 * @return the number of open files.
	 */
	public static int getOpenFiles() {
		return WindowCache.getInstance().getOpenFiles();
	}

	/**
	 * @return the number of open bytes.
	 */
	public static long getOpenBytes() {
		return WindowCache.getInstance().getOpenBytes();
	}
}
