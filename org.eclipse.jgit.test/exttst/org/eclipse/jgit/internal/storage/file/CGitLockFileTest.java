/*
 * Copyright (C) 2021 SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Test;

/**
 * Unit tests of {@link LockFile} testing interoperability with C git
 */
public class CGitLockFileTest extends RepositoryTestCase {

	@Test
	public void testLockedTwiceFails() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit1 = git.commit().setMessage("create file").call();

			assertNotNull(commit1);
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			assertNotNull(git.commit().setMessage("edit file").call());

			LockFile lf = new LockFile(db.getIndexFile());
			assertTrue(lf.lock());
			try {
				String[] command = new String[] { "git", "checkout",
						commit1.name() };
				ProcessBuilder pb = new ProcessBuilder(command);
				pb.directory(db.getWorkTree());
				ExecutionResult result = FS.DETECTED.execute(pb, null);
				assertNotEquals(0, result.getRc());
				String err = result.getStderr().toString().split("\\R")[0];
				assertTrue(err.matches(
						"fatal: Unable to create .*/\\.git/index\\.lock': File exists\\."));
			} finally {
				lf.unlock();
			}
		}
	}
}
