/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.junit.Before;
import org.junit.Test;

public class SyncRemoteTest {

	private SyncRemote syncRemote;

	@Before
	public void setup() {
		syncRemote = new SyncRemote(mock(GitImpl.class), new AbstractMap.SimpleEntry<>("upstream", "b"));
	}

	@Test
	public void fillBranchesTest() {
		final List<Ref> branches = Arrays.asList(createBranch("refs/heads/local/branch1"),
				createBranch("refs/heads/localBranch2"), createBranch("refs/remotes/upstream/remote/branch1"),
				createBranch("refs/remotes/upstream/remoteBranch2"));

		final List<String> remoteBranches = new ArrayList<>();
		final List<String> localBranches = new ArrayList<>();

		syncRemote.fillBranches(branches, remoteBranches, localBranches);

		assertEquals(2, remoteBranches.size());
		assertEquals("remote/branch1", remoteBranches.get(0));
		assertEquals("remoteBranch2", remoteBranches.get(1));

		assertEquals(2, localBranches.size());
		assertEquals("local/branch1", localBranches.get(0));
		assertEquals("localBranch2", localBranches.get(1));
	}

	private Ref createBranch(String branchName) {
		final Ref branch = mock(Ref.class);
		doReturn(branchName).when(branch).getName();

		return branch;
	}
}
