/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BranchTrackingStatusTest extends RepositoryTestCase {
	private TestRepository<Repository> util;

	protected RevWalk rw;

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		util = new TestRepository<>(db);
		StoredConfig config = util.getRepository().getConfig();
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, "master",
				ConfigConstants.CONFIG_KEY_REMOTE, "origin");
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, "master",
				ConfigConstants.CONFIG_KEY_MERGE, "refs/heads/master");
		config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin",
				"fetch", "+refs/heads/*:refs/remotes/origin/*");
	}

	@Test
	void shouldWorkInNormalCase() throws Exception {
		RevCommit remoteTracking = util.branch("refs/remotes/origin/master")
				.commit().create();
		util.branch("master").commit().parent(remoteTracking).create();
		util.branch("master").commit().create();

		BranchTrackingStatus status = BranchTrackingStatus.of(
				util.getRepository(), "master");
		assertEquals(2, status.getAheadCount());
		assertEquals(0, status.getBehindCount());
		assertEquals("refs/remotes/origin/master",
				status.getRemoteTrackingBranch());
	}

	@Test
	void shouldWorkWithoutMergeBase() throws Exception {
		util.branch("refs/remotes/origin/master").commit().create();
		util.branch("master").commit().create();

		BranchTrackingStatus status = BranchTrackingStatus.of(util.getRepository(), "master");
		assertEquals(1, status.getAheadCount());
		assertEquals(1, status.getBehindCount());
	}

	@Test
	void shouldReturnNullWhenBranchDoesntExist() throws Exception {
		BranchTrackingStatus status = BranchTrackingStatus.of(
				util.getRepository(), "doesntexist");

		assertNull(status);
	}
}
