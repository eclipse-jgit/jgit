/*
 * Copyright (C) 2016, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
	 * @throws URISyntaxException
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
