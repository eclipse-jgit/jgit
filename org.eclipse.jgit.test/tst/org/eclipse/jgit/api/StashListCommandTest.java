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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Unit tests of {@link StashListCommand}
 */
public class StashListCommandTest extends RepositoryTestCase {

	@Test
	public void noStashRef() throws Exception {
		StashListCommand command = Git.wrap(db).stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertTrue(stashed.isEmpty());
	}

	@Test
	public void emptyStashReflog() throws Exception {
		Git git = Git.wrap(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		RefUpdate update = db.updateRef(Constants.R_STASH);
		update.setNewObjectId(commit);
		update.disableRefLog();
		assertEquals(Result.NEW, update.update());

		StashListCommand command = Git.wrap(db).stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertTrue(stashed.isEmpty());
	}

	@Test
	public void singleStashedCommit() throws Exception {
		Git git = Git.wrap(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		assertEquals(Result.NEW, newStashUpdate(commit).update());

		StashListCommand command = git.stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertEquals(1, stashed.size());
		assertEquals(commit, stashed.iterator().next());
	}

	@Test
	public void multipleStashedCommits() throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("edit file").call();

		assertEquals(Result.NEW, newStashUpdate(commit1).update());
		assertEquals(Result.FAST_FORWARD, newStashUpdate(commit2).update());

		StashListCommand command = git.stashList();
		Collection<RevCommit> stashed = command.call();
		assertNotNull(stashed);
		assertEquals(2, stashed.size());
		Iterator<RevCommit> iter = stashed.iterator();
		assertEquals(commit2, iter.next());
		assertEquals(commit1, iter.next());
	}

	private RefUpdate newStashUpdate(ObjectId newId) throws Exception {
		RefUpdate ru = db.updateRef(Constants.R_STASH);
		ru.setNewObjectId(newId);
		ru.setForceRefLog(true);
		return ru;
	}
}
