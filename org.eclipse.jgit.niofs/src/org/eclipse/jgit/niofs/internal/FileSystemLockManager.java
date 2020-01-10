/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FileSystemLockManager {

	final Map<String, FileSystemLock> fileSystemsLocks = new ConcurrentHashMap<>();

	private static class LazyHolder {

		static final FileSystemLockManager INSTANCE = new FileSystemLockManager();
	}

	public static FileSystemLockManager getInstance() {
		return LazyHolder.INSTANCE;
	}

	public FileSystemLock getFileSystemLock(File directory, String lockName, TimeUnit lastAccessTimeUnit,
			long lastAccessThreshold) {

		return fileSystemsLocks.computeIfAbsent(directory.getAbsolutePath(),
				key -> new FileSystemLock(directory, lockName, lastAccessTimeUnit, lastAccessThreshold));
	}
}
