/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.manager;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.eclipse.jgit.niofs.internal.JGitFileSystem;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProxy;

public class JGitFileSystemsCache {

	// supplier for creation of new fs
	final Map<String, Supplier<JGitFileSystem>> fileSystemsSuppliers = new ConcurrentHashMap<>();

	// limited amount of real instances of FS
	final Map<String, Supplier<JGitFileSystem>> memoizedSuppliers;

	public JGitFileSystemsCache(JGitFileSystemProviderConfiguration config) {

		memoizedSuppliers = JGitFileSystemsCacheDataStructure.create(config);
	}

	public void addSupplier(String fsKey, Supplier<JGitFileSystem> createFSSupplier) {
		checkNotNull("fsKey", fsKey);
		checkNotNull("fsSupplier", createFSSupplier);

		fileSystemsSuppliers.putIfAbsent(fsKey, createFSSupplier);

		createMemoizedSupplier(fsKey, createFSSupplier);
	}

	public void remove(String fsName) {
		fileSystemsSuppliers.remove(fsName);
		memoizedSuppliers.remove(fsName);
	}

	public JGitFileSystem get(String fsName) {

		Supplier<JGitFileSystem> memoizedSupplier = memoizedSuppliers.get(fsName);
		if (memoizedSupplier != null) {
			return new JGitFileSystemProxy(fsName, memoizedSupplier);
		} else if (fileSystemsSuppliers.get(fsName) != null) {
			Supplier<JGitFileSystem> newMemoizedSupplier = createMemoizedSupplier(fsName,
					fileSystemsSuppliers.get(fsName));
			return new JGitFileSystemProxy(fsName, newMemoizedSupplier);
		}
		return null;
	}

	private Supplier<JGitFileSystem> createMemoizedSupplier(String fsKey, Supplier<JGitFileSystem> createFSSupplier) {
		Supplier<JGitFileSystem> memoizedFSSupplier = MemoizedFileSystemsSupplier.of(createFSSupplier);
		memoizedSuppliers.putIfAbsent(fsKey, memoizedFSSupplier);
		return memoizedFSSupplier;
	}

	public void clear() {
		memoizedSuppliers.clear();
		fileSystemsSuppliers.clear();
	}

	public boolean containsKey(String fsName) {
		return fileSystemsSuppliers.containsKey(fsName);
	}

	public Collection<String> getFileSystems() {
		return fileSystemsSuppliers.keySet();
	}

	public JGitFileSystemsCacheInfo getCacheInfo() {
		return new JGitFileSystemsCacheInfo();
	}

	public class JGitFileSystemsCacheInfo {

		public int fileSystemsCacheSize() {
			return memoizedSuppliers.size();
		}

		public Set<String> memoizedFileSystemsCacheKeys() {
			return memoizedSuppliers.keySet();
		}

		@Override
		public String toString() {
			return "JGitFileSystemsCacheInfo{fileSystemsCacheSize[" + fileSystemsCacheSize()
					+ "], memoizedFileSystemsCacheKeys[" + memoizedFileSystemsCacheKeys() + "]}";
		}
	}
}
