/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

public class BranchConfigTest {

	@Test
	public void getRemoteTrackingBranchShouldHandleNormalCase() {
		Config c = parse("" //
				+ "[remote \"origin\"]\n"
				+ "  fetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "[branch \"master\"]\n"
				+ "  remote = origin\n"
				+ "  merge = refs/heads/master\n");

		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertEquals("refs/remotes/origin/master",
				branchConfig.getRemoteTrackingBranch());
	}

	@Test
	public void getRemoteTrackingBranchShouldHandleOtherMapping() {
		Config c = parse("" //
				+ "[remote \"test\"]\n"
				+ "  fetch = +refs/foo/*:refs/remotes/origin/foo/*\n"
				+ "  fetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "  fetch = +refs/other/*:refs/remotes/origin/other/*\n"
				+ "[branch \"master\"]\n"
				+ "  remote = test\n"
				+ "  merge = refs/foo/master\n" + "\n");

		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertEquals("refs/remotes/origin/foo/master",
				branchConfig.getRemoteTrackingBranch());
	}

	@Test
	public void getRemoteTrackingBranchShouldReturnNullWithoutFetchSpec() {
		Config c = parse("" //
				+ "[remote \"origin\"]\n"
				+ "  fetch = +refs/heads/onlyone:refs/remotes/origin/onlyone\n"
				+ "[branch \"master\"]\n"
				+ "  remote = origin\n"
				+ "  merge = refs/heads/master\n");
		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertNull(branchConfig.getRemoteTrackingBranch());
	}

	@Test
	public void getRemoteTrackingBranchShouldReturnNullWithoutMergeBranch() {
		Config c = parse("" //
				+ "[remote \"origin\"]\n"
				+ "  fetch = +refs/heads/onlyone:refs/remotes/origin/onlyone\n"
				+ "[branch \"master\"]\n"
				+ "  remote = origin\n");
		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertNull(branchConfig.getRemoteTrackingBranch());
	}

	@Test
	public void getTrackingBranchShouldReturnMergeBranchForLocalBranch() {
		Config c = parse("" //
				+ "[remote \"origin\"]\n"
				+ "  fetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "[branch \"master\"]\n"
				+ "  remote = .\n"
				+ "  merge = refs/heads/master\n");

		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertEquals("refs/heads/master",
				branchConfig.getTrackingBranch());
	}

	@Test
	public void getTrackingBranchShouldReturnNullWithoutMergeBranchForLocalBranch() {
		Config c = parse("" //
				+ "[remote \"origin\"]\n"
				+ "  fetch = +refs/heads/onlyone:refs/remotes/origin/onlyone\n"
				+ "[branch \"master\"]\n" //
				+ "  remote = .\n");
		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertNull(branchConfig.getTrackingBranch());
	}

	@Test
	public void getTrackingBranchShouldHandleNormalCaseForRemoteTrackingBranch() {
		Config c = parse("" //
				+ "[remote \"origin\"]\n"
				+ "  fetch = +refs/heads/*:refs/remotes/origin/*\n"
				+ "[branch \"master\"]\n"
				+ "  remote = origin\n"
				+ "  merge = refs/heads/master\n");

		BranchConfig branchConfig = new BranchConfig(c, "master");
		assertEquals("refs/remotes/origin/master",
				branchConfig.getTrackingBranch());
	}

	@Test
	public void isRebase() {
		Config c = parse("" //
				+ "[branch \"undefined\"]\n"
				+ "[branch \"false\"]\n"
				+ "  rebase = false\n"
				+ "[branch \"true\"]\n"
				+ "  rebase = true\n");

		assertFalse(new BranchConfig(c, "undefined").isRebase());
		assertFalse(new BranchConfig(c, "false").isRebase());
		assertTrue(new BranchConfig(c, "true").isRebase());
	}

	private static Config parse(String content) {
		final Config c = new Config(null);
		try {
			c.fromText(content);
		} catch (ConfigInvalidException e) {
			throw new RuntimeException(e);
		}
		return c;
	}
}
