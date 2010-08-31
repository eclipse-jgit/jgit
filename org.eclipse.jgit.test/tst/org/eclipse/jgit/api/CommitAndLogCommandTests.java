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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

public class CommitAndLogCommandTests extends RepositoryTestCase {
	public void testSomeCommits() throws NoHeadException, NoMessageException,
			UnmergedPathException, ConcurrentRefUpdateException,
			JGitInternalException, WrongRepositoryStateException {

		// do 4 commits
		Git git = new Git(db);
		git.commit().setMessage("initial commit").call();
		git.commit().setMessage("second commit").setCommitter(committer).call();
		git.commit().setMessage("third commit").setAuthor(author).call();
		git.commit().setMessage("fourth commit").setAuthor(author)
				.setCommitter(committer).call();
		Iterable<RevCommit> commits = git.log().call();

		// check that all commits came in correctly
		PersonIdent defaultCommitter = new PersonIdent(db);
		PersonIdent expectedAuthors[] = new PersonIdent[] { defaultCommitter,
				committer, author, author };
		PersonIdent expectedCommitters[] = new PersonIdent[] {
				defaultCommitter, committer, defaultCommitter, committer };
		String expectedMessages[] = new String[] { "initial commit",
				"second commit", "third commit", "fourth commit" };
		int l = expectedAuthors.length - 1;
		for (RevCommit c : commits) {
			assertEquals(expectedAuthors[l].getName(), c.getAuthorIdent()
					.getName());
			assertEquals(expectedCommitters[l].getName(), c.getCommitterIdent()
					.getName());
			assertEquals(c.getFullMessage(), expectedMessages[l]);
			l--;
		}
		assertEquals(l, -1);
	}

	// try to do a commit without specifying a message. Should fail!
	public void testWrongParams() throws UnmergedPathException,
			NoHeadException, ConcurrentRefUpdateException,
			JGitInternalException, WrongRepositoryStateException {
		Git git = new Git(db);
		try {
			git.commit().setAuthor(author).call();
			fail("Didn't get the expected exception");
		} catch (NoMessageException e) {
		}
	}

	// try to work with Commands after command has been invoked. Should throw
	// exceptions
	public void testMultipleInvocations() throws NoHeadException,
			ConcurrentRefUpdateException, NoMessageException,
			UnmergedPathException, JGitInternalException,
			WrongRepositoryStateException {
		Git git = new Git(db);
		CommitCommand commitCmd = git.commit();
		commitCmd.setMessage("initial commit").call();
		try {
			// check that setters can't be called after invocation
			commitCmd.setAuthor(author);
			fail("didn't catch the expected exception");
		} catch (IllegalStateException e) {
		}
		LogCommand logCmd = git.log();
		logCmd.call();
		try {
			// check that call can't be called twice
			logCmd.call();
			fail("didn't catch the expected exception");
		} catch (IllegalStateException e) {
		}
	}

	public void testMergeEmptyBranches() throws IOException, NoHeadException,
			NoMessageException, ConcurrentRefUpdateException,
			JGitInternalException, WrongRepositoryStateException {
		Git git = new Git(db);
		git.commit().setMessage("initial commit").call();
		RefUpdate r = db.updateRef("refs/heads/side");
		r.setNewObjectId(db.resolve(Constants.HEAD));
		assertEquals(r.forceUpdate(), RefUpdate.Result.NEW);
		RevCommit second = git.commit().setMessage("second commit").setCommitter(committer).call();
		db.updateRef(Constants.HEAD).link("refs/heads/side");
		RevCommit firstSide = git.commit().setMessage("first side commit").setAuthor(author).call();

		FileWriter wr = new FileWriter(new File(db.getDirectory(),
				Constants.MERGE_HEAD));
		wr.write(ObjectId.toString(db.resolve("refs/heads/master")));
		wr.close();
		wr = new FileWriter(new File(db.getDirectory(), Constants.MERGE_MSG));
		wr.write("merging");
		wr.close();

		RevCommit commit = git.commit().call();
		RevCommit[] parents = commit.getParents();
		assertEquals(parents[0], firstSide);
		assertEquals(parents[1], second);
		assertTrue(parents.length==2);
	}

	public void testAddUnstagedChanges() throws IOException, NoHeadException,
			NoMessageException, ConcurrentRefUpdateException,
			JGitInternalException, WrongRepositoryStateException,
			NoFilepatternException {
		File file = new File(db.getWorkTree(), "a.txt");
		file.createNewFile();
		PrintWriter writer = new PrintWriter(file);
		writer.print("content");
		writer.close();

		Git git = new Git(db);
		git.add().addFilepattern("a.txt").call();
		RevCommit commit = git.commit().setMessage("initial commit").call();
		TreeWalk tw = TreeWalk.forPath(db, "a.txt", commit.getTree());
		assertEquals("6b584e8ece562ebffc15d38808cd6b98fc3d97ea",
				tw.getObjectId(0).getName());

		writer = new PrintWriter(file);
		writer.print("content2");
		writer.close();
		commit = git.commit().setMessage("second commit").call();
		tw = TreeWalk.forPath(db, "a.txt", commit.getTree());
		assertEquals("6b584e8ece562ebffc15d38808cd6b98fc3d97ea",
				tw.getObjectId(0).getName());

		commit = git.commit().setAll(true).setMessage("third commit")
				.setAll(true).call();
		tw = TreeWalk.forPath(db, "a.txt", commit.getTree());
		assertEquals("db00fd65b218578127ea51f3dffac701f12f486a",
				tw.getObjectId(0).getName());
	}
}
