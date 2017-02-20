/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
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

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class BranchTrackingStatusTest extends RepositoryTestCase {
	private TestRepository<Repository> util;

	protected RevWalk rw;

	@Override
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
	public void shouldWorkInNormalCase() throws Exception {
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
	public void shouldWorkWithoutMergeBase() throws Exception {
		util.branch("refs/remotes/origin/master").commit().create();
		util.branch("master").commit().create();

		BranchTrackingStatus status = BranchTrackingStatus.of(util.getRepository(), "master");
		assertEquals(1, status.getAheadCount());
		assertEquals(1, status.getBehindCount());
	}

	@Test
	public void shouldReturnNullWhenBranchDoesntExist() throws Exception {
		BranchTrackingStatus status = BranchTrackingStatus.of(
				util.getRepository(), "doesntexist");

		assertNull(status);
	}
}
