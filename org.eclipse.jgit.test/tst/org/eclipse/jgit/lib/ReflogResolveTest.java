/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Unit tests for resolving reflog-based revisions
 */
public class ReflogResolveTest extends RepositoryTestCase {

	@Test
	public void resolveMasterCommits() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("edit file").call();

			assertEquals(c2, db.resolve("master@{0}"));
			assertEquals(c1, db.resolve("master@{1}"));
		}
	}

	@Test
	public void resolveUnnamedCurrentBranchCommits() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("edit file").call();

			assertEquals(c2, db.resolve("master@{0}"));
			assertEquals(c1, db.resolve("master@{1}"));

			git.checkout().setCreateBranch(true).setName("newbranch")
					.setStartPoint(c1).call();

			// same as current branch, e.g. master
			assertEquals(c1, db.resolve("@{0}"));
			try {
				assertEquals(c1, db.resolve("@{1}"));
				fail(); // Looking at wrong ref, e.g HEAD
			} catch (RevisionSyntaxException e) {
				assertNotNull(e);
			}

			// detached head, read HEAD reflog
			git.checkout().setName(c2.getName()).call();
			assertEquals(c2, db.resolve("@{0}"));
			assertEquals(c1, db.resolve("@{1}"));
			assertEquals(c2, db.resolve("@{2}"));
		}
	}

	@Test
	public void resolveReflogParent() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("edit file").call();

			assertEquals(c1, db.resolve("master@{0}~1"));
		}
	}

	@Test
	public void resolveNonExistingBranch() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();
			assertNull(db.resolve("notabranch@{7}"));
		}
	}

	@Test
	public void resolvePreviousBranch() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("edit file").call();

			git.checkout().setCreateBranch(true).setName("newbranch")
					.setStartPoint(c1).call();

			git.checkout().setName(c1.getName()).call();

			git.checkout().setName("master").call();

			assertEquals(c1.getName(), db.simplify("@{-1}"));
			assertEquals("newbranch", db.simplify("@{-2}"));
			assertEquals("master", db.simplify("@{-3}"));

			// chained expression
			try {
				// Cannot refer to reflog of detached head
				db.resolve("@{-1}@{0}");
				fail();
			} catch (RevisionSyntaxException e) {
				// good
			}
			assertEquals(c1.getName(), db.resolve("@{-2}@{0}").getName());

			assertEquals(c2.getName(), db.resolve("@{-3}@{0}").getName());
		}
	}

	@Test
	public void resolveDate() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();
			try {
				db.resolve("master@{yesterday}");
				fail("Exception not thrown");
			} catch (RevisionSyntaxException e) {
				assertNotNull(e);
			}
		}
	}
}
