/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

import java.io.IOException;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.RepositoryTestCase;

public class BranchCommandTest extends RepositoryTestCase {

	private static final String TEST_BRANCH_NAME = "test";

	public void testCreateBranch() throws JGitInternalException,
			ConcurrentRefUpdateException, NoHeadException, NoMessageException,
			UnmergedPathException, WrongRepositoryStateException {
		Git git = new Git(db);
		git.commit().setMessage("first commit!").call();
		git.branch().setName(TEST_BRANCH_NAME).call();

		try {
			assertNotNull(db.getRef(TEST_BRANCH_NAME));
		} catch (IOException e) {
			fail("failed during branch creation test");
		}
	}

	public void testInvalidBranchName() throws JGitInternalException,
			ConcurrentRefUpdateException, NoHeadException, NoMessageException,
			UnmergedPathException, WrongRepositoryStateException {
		Git git = new Git(db);
		git.commit().setMessage("first commit!").call();

		try {
			git.branch().setName(TEST_BRANCH_NAME + " spaces are bad").call();
		} catch (JGitInternalException e) {
			// assert that we fail here...
		}
	}

	public void testDeleteBranch() throws JGitInternalException,
			ConcurrentRefUpdateException, NoHeadException, NoMessageException,
			UnmergedPathException, WrongRepositoryStateException {
		Git git = new Git(db);
		git.commit().setMessage("first commit!").call();
		git.branch().setName("test").call();

		try {
			assertNotNull(db.getRef("test"));
			git.branch().setName("test").setDelete(true).call();
			assertNull(db.getRef("test"));
		} catch (IOException e) {
			fail("failed during branch creation test");
		}
	}

	public void testMoveBranch() throws JGitInternalException,
			ConcurrentRefUpdateException, NoHeadException, NoMessageException,
			UnmergedPathException, WrongRepositoryStateException {
		Git git = new Git(db);
		git.commit().setMessage("first commit!").call();
		git.branch().rename(null, "test2").call();

		try {
			assertNull(db.getRef("test"));
			assertNotNull(db.getRef("test2"));
		} catch (IOException e) {
			fail("failed during branch creation test");
		}
	}

}
