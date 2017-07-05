/*
 * Copyright (C) 2011, GitHub Inc.
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
