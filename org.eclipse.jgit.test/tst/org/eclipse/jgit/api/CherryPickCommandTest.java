/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Test cherry-pick command
 */
public class CherryPickCommandTest extends RepositoryTestCase {
	@Test
	public void testCherryPick() throws IOException, JGitInternalException,
			GitAPIException {
		Git git = new Git(db);

		writeTrashFile("a", "first line\nsec. line\nthird line\n");
		git.add().addFilepattern("a").call();
		RevCommit firstCommit = git.commit().setMessage("create a").call();

		writeTrashFile("b", "content\n");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("create b").call();

		writeTrashFile("a", "first line\nsec. line\nthird line\nfourth line\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("enlarged a").call();

		writeTrashFile("a",
				"first line\nsecond line\nthird line\nfourth line\n");
		git.add().addFilepattern("a").call();
		RevCommit fixingA = git.commit().setMessage("fixed a").call();

		git.branchCreate().setName("side").setStartPoint(firstCommit).call();
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "first line\nsec. line\nthird line\nfeature++\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("enhanced a").call();

		git.cherryPick().include(fixingA).call();

		assertFalse(new File(db.getWorkTree(), "b").exists());
		checkFile(new File(db.getWorkTree(), "a"),
				"first line\nsecond line\nthird line\nfeature++\n");
		Iterator<RevCommit> history = git.log().call().iterator();
		assertEquals("fixed a", history.next().getFullMessage());
		assertEquals("enhanced a", history.next().getFullMessage());
		assertEquals("create a", history.next().getFullMessage());
		assertFalse(history.hasNext());
	}
}
