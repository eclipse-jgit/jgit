/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import java.io.File;
import java.io.PrintWriter;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoMessageException;
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
	public void testSomeCommits() throws Exception {
		// do 4 commits
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			git.commit().setMessage("second commit").setCommitter(committer)
					.call();
			git.commit().setMessage("third commit").setAuthor(author).call();
			git.commit().setMessage("fourth commit").setAuthor(author)
					.setCommitter(committer).call();
			Iterable<RevCommit> commits = git.log().call();

			// check that all commits came in correctly
			PersonIdent defaultCommitter = new PersonIdent(db);
			PersonIdent expectedAuthors[] = new PersonIdent[] {
					defaultCommitter, committer, author, author };
			PersonIdent expectedCommitters[] = new PersonIdent[] {
					defaultCommitter, committer, defaultCommitter, committer };
			String expectedMessages[] = new String[] { "initial commit",
					"second commit", "third commit", "fourth commit" };
			int l = expectedAuthors.length - 1;
			for (RevCommit c : commits) {
				assertEquals(expectedAuthors[l].getName(),
						c.getAuthorIdent().getName());
				assertEquals(expectedCommitters[l].getName(),
						c.getCommitterIdent().getName());
				assertEquals(c.getFullMessage(), expectedMessages[l]);
				l--;
			}
			assertEquals(l, -1);
			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(
					reader.getLastEntry().getComment().startsWith("commit:"));
			reader = db.getReflogReader(db.getBranch());
			assertTrue(
					reader.getLastEntry().getComment().startsWith("commit:"));
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
	public void testMergeEmptyBranches() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			RefUpdate r = db.updateRef("refs/heads/side");
			r.setNewObjectId(db.resolve(Constants.HEAD));
			assertEquals(r.forceUpdate(), RefUpdate.Result.NEW);
			RevCommit second = git.commit().setMessage("second commit")
					.setCommitter(committer).call();
			db.updateRef(Constants.HEAD).link("refs/heads/side");
			RevCommit firstSide = git.commit().setMessage("first side commit")
					.setAuthor(author).call();

			write(new File(db.getDirectory(), Constants.MERGE_HEAD),
					ObjectId.toString(db.resolve("refs/heads/master")));
			write(new File(db.getDirectory(), Constants.MERGE_MSG), "merging");

			RevCommit commit = git.commit().call();
			RevCommit[] parents = commit.getParents();
			assertEquals(parents[0], firstSide);
			assertEquals(parents[1], second);
			assertEquals(2, parents.length);
		}
	}

	@Test
	public void testAddUnstagedChanges() throws Exception {
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
	public void testModeChange() throws Exception {
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
			git.commit().setMessage("mode change").setCommitter(committer)
					.call();

			// pure mode change should be committable with -o option
			fs.setExecute(file, false);
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("mode change").setCommitter(committer)
					.setOnly("a.txt").call();
		}
	}

	@Test
	public void testCommitRange() throws Exception {
		// do 4 commits and set the range to the second and fourth one
		try (Git git = new Git(db)) {
			git.commit().setMessage("first commit").call();
			RevCommit second = git.commit().setMessage("second commit")
					.setCommitter(committer).call();
			git.commit().setMessage("third commit").setAuthor(author).call();
			RevCommit last = git.commit().setMessage("fourth commit")
					.setAuthor(author).setCommitter(committer).call();
			Iterable<RevCommit> commits = git.log()
					.addRange(second.getId(), last.getId()).call();

			// check that we have the third and fourth commit
			PersonIdent defaultCommitter = new PersonIdent(db);
			PersonIdent expectedAuthors[] = new PersonIdent[] { author,
					author };
			PersonIdent expectedCommitters[] = new PersonIdent[] {
					defaultCommitter, committer };
			String expectedMessages[] = new String[] { "third commit",
					"fourth commit" };
			int l = expectedAuthors.length - 1;
			for (RevCommit c : commits) {
				assertEquals(expectedAuthors[l].getName(),
						c.getAuthorIdent().getName());
				assertEquals(expectedCommitters[l].getName(),
						c.getCommitterIdent().getName());
				assertEquals(c.getFullMessage(), expectedMessages[l]);
				l--;
			}
			assertEquals(l, -1);
		}
	}

	@Test
	public void testCommitAmend() throws Exception {
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
	public void testInsertChangeId() throws Exception {
		try (Git git = new Git(db)) {
			String messageHeader = "Some header line\n\nSome detail explanation\n";
			String changeIdTemplate = "\nChange-Id: I"
					+ ObjectId.zeroId().getName() + "\n";
			String messageFooter = "Some foooter lines\nAnother footer line\n";
			RevCommit commit = git.commit()
					.setMessage(messageHeader + messageFooter)
					.setInsertChangeId(true).call();
			// we should find a real change id (at the end of the file)
			byte[] chars = commit.getFullMessage().getBytes(UTF_8);
			int lastLineBegin = RawParseUtils.prevLF(chars, chars.length - 2);
			String lastLine = RawParseUtils.decode(chars, lastLineBegin + 1,
					chars.length);
			assertTrue(lastLine.contains("Change-Id:"));
			assertFalse(lastLine
					.contains("Change-Id: I" + ObjectId.zeroId().getName()));

			commit = git.commit()
					.setMessage(
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
			assertFalse(line
					.contains("Change-Id: I" + ObjectId.zeroId().getName()));

			commit = git.commit()
					.setMessage(
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

			assertTrue(commit.getFullMessage()
					.contains("Change-Id: I" + ObjectId.zeroId().getName()));
		}
	}
}
