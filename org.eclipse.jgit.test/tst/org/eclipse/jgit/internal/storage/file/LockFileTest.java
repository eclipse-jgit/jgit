/*
 * Copyright (C) 2012, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Unit tests of {@link LockFile}
 */
public class LockFileTest extends RepositoryTestCase {

	@Test
	public void lockFailedExceptionRecovery() throws Exception {
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
				git.checkout().setName(commit1.name()).call();
				fail("JGitInternalException not thrown");
			} catch (JGitInternalException e) {
				assertTrue(e.getCause() instanceof LockFailedException);
				lf.unlock();
				git.checkout().setName(commit1.name()).call();
			}
		}
	}
}
