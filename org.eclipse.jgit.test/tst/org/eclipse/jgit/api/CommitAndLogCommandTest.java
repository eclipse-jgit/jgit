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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

/**
 * Testing the git commit and log commands
 */
public class CommitAndLogCommandTest extends RepositoryTestCase {
	@Test
	public void testSomeCommits() throws JGitInternalException, IOException,
			GitAPIException {

		// do 4 commits
		try (Git git = new Git(db)) {
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
			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment().startsWith("commit:"));
			reader = db.getReflogReader(db.getBranch());
			assertTrue(reader.getLastEntry().getComment().startsWith("commit:"));
		}
	}

	@Test
	public void testLogWithFilter() throws IOException, JGitInternalException,
			GitAPIException {

		try (Git git = new Git(db)) {
			// create first file
			File file = new File(db.getWorkTree(), "a.txt");
			FileUtils.createNewFile(file);
			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("content1");
			}

			// First commit - a.txt file
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("commit1").setCommitter(committer).call();

			// create second file
			file = new File(db.getWorkTree(), "b.txt");
			FileUtils.createNewFile(file);
			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("content2");
			}

			// Second commit - b.txt file
			git.add().addFilepattern("b.txt").call();
			git.commit().setMessage("commit2").setCommitter(committer).call();

			// First log - a.txt filter
			int count = 0;
			for (RevCommit c : git.log().addPath("a.txt").call()) {
				assertEquals("commit1", c.getFullMessage());
				count++;
			}
			assertEquals(1, count);

			// Second log - b.txt filter
			count = 0;
			for (RevCommit c : git.log().addPath("b.txt").call()) {
				assertEquals("commit2", c.getFullMessage());
				count++;
			}
			assertEquals(1, count);

			// Third log - without filter
			count = 0;
			for (RevCommit c : git.log().call()) {
				assertEquals(committer, c.getCommitterIdent());
				count++;
			}
			assertEquals(2, count);
		}
	}

	// try to do a commit without specifying a message. Should fail!
	@Test
	public void testWrongParams() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.commit().setAuthor(author).call();
			fail("Didn't get the expected exception");
		} catch (NoMessageException e) {
			// expected
		}
	}

	// try to work with Commands after command has been invoked. Should throw
	// exceptions
	@Test
	public void testMultipleInvocations() throws GitAPIException {
		try (Git git = new Git(db)) {
			CommitCommand commitCmd = git.commit();
			commitCmd.setMessage("initial commit").call();
			try {
				// check that setters can't be called after invocation
				commitCmd.setAuthor(author);
				fail("didn't catch the expected exception");
			} catch (IllegalStateException e) {
				// expected
			}
			LogCommand logCmd = git.log();
			logCmd.call();
			try {
				// check that call can't be called twice
				logCmd.call();
				fail("didn't catch the expected exception");
			} catch (IllegalStateException e) {
				// expected
			}
		}
	}

	@Test
	public void testMergeEmptyBranches() throws IOException,
			JGitInternalException, GitAPIException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			RefUpdate r = db.updateRef("refs/heads/side");
			r.setNewObjectId(db.resolve(Constants.HEAD));
			assertEquals(r.forceUpdate(), RefUpdate.Result.NEW);
			RevCommit second = git.commit().setMessage("second commit").setCommitter(committer).call();
			db.updateRef(Constants.HEAD).link("refs/heads/side");
			RevCommit firstSide = git.commit().setMessage("first side commit").setAuthor(author).call();

			write(new File(db.getDirectory(), Constants.MERGE_HEAD), ObjectId
					.toString(db.resolve("refs/heads/master")));
			write(new File(db.getDirectory(), Constants.MERGE_MSG), "merging");

			RevCommit commit = git.commit().call();
			RevCommit[] parents = commit.getParents();
			assertEquals(parents[0], firstSide);
			assertEquals(parents[1], second);
			assertEquals(2, parents.length);
		}
	}

	@Test
	public void testAddUnstagedChanges() throws IOException,
			JGitInternalException, GitAPIException {
		File file = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(file);
		try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
			writer.print("content");
		}

		try (Git git = new Git(db)) {
			git.add().addFilepattern("a.txt").call();
			RevCommit commit = git.commit().setMessage("initial commit").call();
			TreeWalk tw = TreeWalk.forPath(db, "a.txt", commit.getTree());
			assertEquals("6b584e8ece562ebffc15d38808cd6b98fc3d97ea",
					tw.getObjectId(0).getName());

			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("content2");
			}
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

	@Test
	public void testModeChange() throws IOException, GitAPIException {
		assumeFalse(System.getProperty("os.name").startsWith("Windows"));// SKIP
		try (Git git = new Git(db)) {
			// create file
			File file = new File(db.getWorkTree(), "a.txt");
			FileUtils.createNewFile(file);
			try (PrintWriter writer = new PrintWriter(file, UTF_8.name())) {
				writer.print("content1");
			}

			// First commit - a.txt file
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("commit1").setCommitter(committer).call();

			// pure mode change should be committable
			FS fs = db.getFS();
			fs.setExecute(file, true);
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("mode change").setCommitter(committer).call();

			// pure mode change should be committable with -o option
			fs.setExecute(file, false);
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("mode change").setCommitter(committer)
					.setOnly("a.txt").call();
		}
	}

	@Test
	public void testCommitRange() throws GitAPIException,
			JGitInternalException, MissingObjectException,
			IncorrectObjectTypeException {
		// do 4 commits and set the range to the second and fourth one
		try (Git git = new Git(db)) {
			git.commit().setMessage("first commit").call();
			RevCommit second = git.commit().setMessage("second commit")
					.setCommitter(committer).call();
			git.commit().setMessage("third commit").setAuthor(author).call();
			RevCommit last = git.commit().setMessage("fourth commit").setAuthor(
					author)
					.setCommitter(committer).call();
			Iterable<RevCommit> commits = git.log().addRange(second.getId(),
					last.getId()).call();

			// check that we have the third and fourth commit
			PersonIdent defaultCommitter = new PersonIdent(db);
			PersonIdent expectedAuthors[] = new PersonIdent[] { author, author };
			PersonIdent expectedCommitters[] = new PersonIdent[] {
					defaultCommitter, committer };
			String expectedMessages[] = new String[] { "third commit",
					"fourth commit" };
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
	}

	@Test
	public void testCommitAmend() throws JGitInternalException, IOException,
			GitAPIException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("first comit").call(); // typo
			git.commit().setAmend(true).setMessage("first commit").call();

			Iterable<RevCommit> commits = git.log().call();
			int c = 0;
			for (RevCommit commit : commits) {
				assertEquals("first commit", commit.getFullMessage());
				c++;
			}
			assertEquals(1, c);
			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("commit (amend):"));
			reader = db.getReflogReader(db.getBranch());
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("commit (amend):"));
		}
	}

	@Test
	public void testInsertChangeId() throws JGitInternalException,
			GitAPIException {
		try (Git git = new Git(db)) {
			String messageHeader = "Some header line\n\nSome detail explanation\n";
			String changeIdTemplate = "\nChange-Id: I"
					+ ObjectId.zeroId().getName() + "\n";
			String messageFooter = "Some foooter lines\nAnother footer line\n";
			RevCommit commit = git.commit().setMessage(
					messageHeader + messageFooter)
					.setInsertChangeId(true).call();
			// we should find a real change id (at the end of the file)
			byte[] chars = commit.getFullMessage().getBytes(UTF_8);
			int lastLineBegin = RawParseUtils.prevLF(chars, chars.length - 2);
			String lastLine = RawParseUtils.decode(chars, lastLineBegin + 1,
					chars.length);
			assertTrue(lastLine.contains("Change-Id:"));
			assertFalse(lastLine.contains(
					"Change-Id: I" + ObjectId.zeroId().getName()));

			commit = git.commit().setMessage(
					messageHeader + changeIdTemplate + messageFooter)
					.setInsertChangeId(true).call();
			// we should find a real change id (in the line as dictated by the
			// template)
			chars = commit.getFullMessage().getBytes(UTF_8);
			int lineStart = 0;
			int lineEnd = 0;
			for (int i = 0; i < 4; i++) {
				lineStart = RawParseUtils.nextLF(chars, lineStart);
			}
			lineEnd = RawParseUtils.nextLF(chars, lineStart);

			String line = RawParseUtils.decode(chars, lineStart, lineEnd);

			assertTrue(line.contains("Change-Id:"));
			assertFalse(line.contains(
					"Change-Id: I" + ObjectId.zeroId().getName()));

			commit = git.commit().setMessage(
					messageHeader + changeIdTemplate + messageFooter)
					.setInsertChangeId(false).call();
			// we should find the untouched template
			chars = commit.getFullMessage().getBytes(UTF_8);
			lineStart = 0;
			lineEnd = 0;
			for (int i = 0; i < 4; i++) {
				lineStart = RawParseUtils.nextLF(chars, lineStart);
			}
			lineEnd = RawParseUtils.nextLF(chars, lineStart);

			line = RawParseUtils.decode(chars, lineStart, lineEnd);

			assertTrue(commit.getFullMessage().contains(
					"Change-Id: I" + ObjectId.zeroId().getName()));
		}
	}
}
