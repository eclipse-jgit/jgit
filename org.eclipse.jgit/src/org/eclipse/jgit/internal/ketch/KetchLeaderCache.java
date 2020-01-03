/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Repository;

/**
 * A cache of live leader instances, keyed by repository.
 * <p>
 * Ketch only assigns a leader to a repository when needed. If
 * {@link #get(Repository)} is called for a repository that does not have a
 * leader, the leader is created and added to the cache.
 */
public class KetchLeaderCache {
	private final KetchSystem system;
	private final ConcurrentMap<String, KetchLeader> leaders;
	private final Lock startLock;

	/**
	 * Initialize a new leader cache.
	 *
	 * @param system
	 *            system configuration for the leaders
	 */
	public KetchLeaderCache(KetchSystem system) {
		this.system = system;
		leaders = new ConcurrentHashMap<>();
		startLock = new ReentrantLock(true /* fair */);
	}

	/**
	 * Lookup the leader instance for a given repository.
	 *
	 * @param repo
	 *            repository to get the leader for.
	 * @return the leader instance for the repository.
	 * @throws java.net.URISyntaxException
	 *             remote configuration contains an invalid URL.
	 */
	public KetchLeader get(Repository repo)
			throws URISyntaxException {
		String key = computeKey(repo);
		KetchLeader leader = leaders.get(key);
		if (leader != null) {
			return leader;
		}
		return startLeader(key, repo);
	}

	private KetchLeader startLeader(String key, Repository repo)
			throws URISyntaxException {
		startLock.lock();
		try {
			KetchLeader leader = leaders.get(key);
			if (leader != null) {
				return leader;
			}
			leader = system.createLeader(repo);
			leaders.put(key, leader);
			return leader;
		} finally {
			startLock.unlock();
		}
	}

	private static String computeKey(Repository repo) {
		if (repo instanceof DfsRepository) {
			DfsRepository dfs = (DfsRepository) repo;
			return dfs.getDescription().getRepositoryName();
		}

		if (repo.getDirectory() != null) {
			return repo.getDirectory().toURI().toString();
		}

		throw new IllegalArgumentException();
	}
}
