/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.junit.Test;

/**
 * Some minimal cloning and fetching tests. Concrete subclasses can implement
 * the abstract operations from {@link SshTestHarness} to run with different SSH
 * implementations.
 *
 * @since 5.11
 */
public abstract class SshBasicTestBase extends SshTestHarness {

	protected File defaultCloneDir;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		defaultCloneDir = new File(getTemporaryDirectory(), "cloned");
	}

	@Test
	public void testSshCloneWithConfig() throws Exception {
		cloneWith("ssh://localhost/doesntmatter", defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
	}

	@Test
	public void testSshFetchWithConfig() throws Exception {
		File localClone = cloneWith("ssh://localhost/doesntmatter",
				defaultCloneDir, null, //
				"Host localhost", //
				"HostName localhost", //
				"Port " + testPort, //
				"User " + TEST_USER, //
				"IdentityFile " + privateKey1.getAbsolutePath());
		// Do a commit in the upstream repo
		try (Git git = new Git(db)) {
			writeTrashFile("SomeOtherFile.txt", "Other commit");
			git.add().addFilepattern("SomeOtherFile.txt").call();
			git.commit().setMessage("New commit").call();
		}
		// Pull in the clone
		try (Git git = Git.open(localClone)) {
			File f = new File(git.getRepository().getWorkTree(),
					"SomeOtherFile.txt");
			assertFalse(f.exists());
			git.pull().setRemote("origin").call();
			assertTrue(f.exists());
			assertEquals("Other commit", read(f));
		}
	}
}
