/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class ReflogCommandTest extends RepositoryTestCase {

	private Git git;

	private RevCommit commit1, commit2;

	private static final String FILE = "test.txt";

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		git = new Git(db);
		// commit something
		writeTrashFile(FILE, "Hello world");
		git.add().addFilepattern(FILE).call();
		commit1 = git.commit().setMessage("Initial commit").call();
		git.checkout().setCreateBranch(true).setName("b1").call();
		git.rm().addFilepattern(FILE).call();
		commit2 = git.commit().setMessage("Removed file").call();
		git.notesAdd().setObjectId(commit1).setMessage("data").call();
	}

	/**
	 * Test getting the HEAD reflog
	 *
	 * @throws Exception
	 */
	@Test
	public void testHeadReflog() throws Exception {
		Collection<ReflogEntry> reflog = git.reflog().call();
		assertNotNull(reflog);
		assertEquals(3, reflog.size());
		ReflogEntry[] reflogs = reflog.toArray(new ReflogEntry[reflog.size()]);
		assertEquals(reflogs[2].getComment(),
				"commit (initial): Initial commit");
		assertEquals(reflogs[2].getNewId(), commit1.getId());
		assertEquals(reflogs[2].getOldId(), ObjectId.zeroId());
		assertEquals(reflogs[1].getComment(),
				"checkout: moving from master to b1");
		assertEquals(reflogs[1].getNewId(), commit1.getId());
		assertEquals(reflogs[1].getOldId(), commit1.getId());
		assertEquals(reflogs[0].getComment(), "commit: Removed file");
		assertEquals(reflogs[0].getNewId(), commit2.getId());
		assertEquals(reflogs[0].getOldId(), commit1.getId());
	}

	/**
	 * Test getting the reflog for an explicit branch
	 *
	 * @throws Exception
	 */
	@Test
	public void testBranchReflog() throws Exception {
		Collection<ReflogEntry> reflog = git.reflog()
				.setRef(Constants.R_HEADS + "b1").call();
		assertNotNull(reflog);
		assertEquals(2, reflog.size());
		ReflogEntry[] reflogs = reflog.toArray(new ReflogEntry[reflog.size()]);
		assertEquals(reflogs[0].getComment(), "commit: Removed file");
		assertEquals(reflogs[0].getNewId(), commit2.getId());
		assertEquals(reflogs[0].getOldId(), commit1.getId());
		assertEquals(reflogs[1].getComment(),
				"branch: Created from commit Initial commit");
		assertEquals(reflogs[1].getNewId(), commit1.getId());
		assertEquals(reflogs[1].getOldId(), ObjectId.zeroId());
	}

	/**
	 * Test getting the reflog for an amend commit
	 *
	 * @throws Exception
	 */
	@Test
	public void testAmendReflog() throws Exception {
		RevCommit commit2a = git.commit().setAmend(true)
				.setMessage("Deleted file").call();
		Collection<ReflogEntry> reflog = git.reflog().call();
		assertNotNull(reflog);
		assertEquals(4, reflog.size());
		ReflogEntry[] reflogs = reflog.toArray(new ReflogEntry[reflog.size()]);
		assertEquals(reflogs[3].getComment(),
				"commit (initial): Initial commit");
		assertEquals(reflogs[3].getNewId(), commit1.getId());
		assertEquals(reflogs[3].getOldId(), ObjectId.zeroId());
		assertEquals(reflogs[2].getComment(),
				"checkout: moving from master to b1");
		assertEquals(reflogs[2].getNewId(), commit1.getId());
		assertEquals(reflogs[2].getOldId(), commit1.getId());
		assertEquals(reflogs[1].getComment(), "commit: Removed file");
		assertEquals(reflogs[1].getNewId(), commit2.getId());
		assertEquals(reflogs[1].getOldId(), commit1.getId());
		assertEquals(reflogs[0].getComment(), "commit (amend): Deleted file");
		assertEquals(reflogs[0].getNewId(), commit2a.getId());
		assertEquals(reflogs[0].getOldId(), commit2.getId());
	}
}
