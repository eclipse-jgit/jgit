/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
