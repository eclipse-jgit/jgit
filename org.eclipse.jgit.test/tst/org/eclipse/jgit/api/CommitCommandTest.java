/*
 * Copyright (C) 2011-2012, GitHub Inc.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.time.TimeUtil;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.GpgSigner;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests of {@link CommitCommand}.
 */
public class CommitCommandTest extends RepositoryTestCase {

	@Test
	public void testExecutableRetention() throws Exception {
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, true);
		config.save();

		FS executableFs = new FS() {

			@Override
			public boolean supportsExecute() {
				return true;
			}

			@Override
			public boolean setExecute(File f, boolean canExec) {
				return true;
			}

			@Override
			public ProcessBuilder runInShell(String cmd, String[] args) {
				return null;
			}

			@Override
			public boolean retryFailedLockFileCommit() {
				return false;
			}

			@Override
			public FS newInstance() {
				return this;
			}

			@Override
			protected File discoverGitExe() {
				return null;
			}

			@Override
			public boolean canExecute(File f) {
				return true;
			}

			@Override
			public boolean isCaseSensitive() {
				return true;
			}
		};

		Git git = Git.open(db.getDirectory(), executableFs);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		RevCommit commit1 = git.commit().setMessage("commit").call();
		TreeWalk walk = TreeWalk.forPath(db, path, commit1.getTree());
		assertNotNull(walk);
		assertEquals(FileMode.EXECUTABLE_FILE, walk.getFileMode(0));

		FS nonExecutableFs = new FS() {

			@Override
			public boolean supportsExecute() {
				return false;
			}

			@Override
			public boolean setExecute(File f, boolean canExec) {
				return false;
			}

			@Override
			public ProcessBuilder runInShell(String cmd, String[] args) {
				return null;
			}

			@Override
			public boolean retryFailedLockFileCommit() {
				return false;
			}

			@Override
			public FS newInstance() {
				return this;
			}

			@Override
			protected File discoverGitExe() {
				return null;
			}

			@Override
			public boolean canExecute(File f) {
				return false;
			}

			@Override
			public boolean isCaseSensitive() {
				return true;
			}
		};

		config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, false);
		config.save();

		Git git2 = Git.open(db.getDirectory(), nonExecutableFs);
		writeTrashFile(path, "content2");
		RevCommit commit2 = git2.commit().setOnly(path).setMessage("commit2")
				.call();
		walk = TreeWalk.forPath(db, path, commit2.getTree());
		assertNotNull(walk);
		assertEquals(FileMode.EXECUTABLE_FILE, walk.getFileMode(0));
	}

	@Test
	public void commitNewSubmodule() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit = git.commit().setMessage("create file").call();

			SubmoduleAddCommand command = new SubmoduleAddCommand(db);
			String path = "sub";
			command.setPath(path);
			String uri = db.getDirectory().toURI().toString();
			command.setURI(uri);
			Repository repo = command.call();
			assertNotNull(repo);
			addRepoToClose(repo);

			SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
			assertTrue(generator.next());
			assertEquals(path, generator.getPath());
			assertEquals(commit, generator.getObjectId());
			assertEquals(uri, generator.getModulesUrl());
			assertEquals(path, generator.getModulesPath());
			assertEquals(uri, generator.getConfigUrl());
			try (Repository subModRepo = generator.getRepository()) {
				assertNotNull(subModRepo);
			}
			assertEquals(commit, repo.resolve(Constants.HEAD));

			RevCommit submoduleCommit = git.commit().setMessage("submodule add")
					.setOnly(path).call();
			assertNotNull(submoduleCommit);
			try (TreeWalk walk = new TreeWalk(db)) {
				walk.addTree(commit.getTree());
				walk.addTree(submoduleCommit.getTree());
				walk.setFilter(TreeFilter.ANY_DIFF);
				List<DiffEntry> diffs = DiffEntry.scan(walk);
				assertEquals(1, diffs.size());
				DiffEntry subDiff = diffs.get(0);
				assertEquals(FileMode.MISSING, subDiff.getOldMode());
				assertEquals(FileMode.GITLINK, subDiff.getNewMode());
				assertEquals(ObjectId.zeroId(), subDiff.getOldId().toObjectId());
				assertEquals(commit, subDiff.getNewId().toObjectId());
				assertEquals(path, subDiff.getNewPath());
			}
		}
	}

	@Test
	public void commitSubmoduleUpdate() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit = git.commit().setMessage("create file").call();
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit2 = git.commit().setMessage("edit file").call();

			SubmoduleAddCommand command = new SubmoduleAddCommand(db);
			String path = "sub";
			command.setPath(path);
			String uri = db.getDirectory().toURI().toString();
			command.setURI(uri);
			Repository repo = command.call();
			assertNotNull(repo);
			addRepoToClose(repo);

			SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
			assertTrue(generator.next());
			assertEquals(path, generator.getPath());
			assertEquals(commit2, generator.getObjectId());
			assertEquals(uri, generator.getModulesUrl());
			assertEquals(path, generator.getModulesPath());
			assertEquals(uri, generator.getConfigUrl());
			try (Repository subModRepo = generator.getRepository()) {
				assertNotNull(subModRepo);
			}
			assertEquals(commit2, repo.resolve(Constants.HEAD));

			RevCommit submoduleAddCommit = git.commit().setMessage("submodule add")
					.setOnly(path).call();
			assertNotNull(submoduleAddCommit);

			RefUpdate update = repo.updateRef(Constants.HEAD);
			update.setNewObjectId(commit);
			assertEquals(Result.FORCED, update.forceUpdate());

			RevCommit submoduleEditCommit = git.commit()
					.setMessage("submodule add").setOnly(path).call();
			assertNotNull(submoduleEditCommit);
			try (TreeWalk walk = new TreeWalk(db)) {
				walk.addTree(submoduleAddCommit.getTree());
				walk.addTree(submoduleEditCommit.getTree());
				walk.setFilter(TreeFilter.ANY_DIFF);
				List<DiffEntry> diffs = DiffEntry.scan(walk);
				assertEquals(1, diffs.size());
				DiffEntry subDiff = diffs.get(0);
				assertEquals(FileMode.GITLINK, subDiff.getOldMode());
				assertEquals(FileMode.GITLINK, subDiff.getNewMode());
				assertEquals(commit2, subDiff.getOldId().toObjectId());
				assertEquals(commit, subDiff.getNewId().toObjectId());
				assertEquals(path, subDiff.getNewPath());
				assertEquals(path, subDiff.getOldPath());
			}
		}
	}

	@Ignore("very flaky when run with Hudson")
	@Test
	public void commitUpdatesSmudgedEntries() throws Exception {
		try (Git git = new Git(db)) {
			File file1 = writeTrashFile("file1.txt", "content1");
			TimeUtil.setLastModifiedWithOffset(file1.toPath(), -5000L);
			File file2 = writeTrashFile("file2.txt", "content2");
			TimeUtil.setLastModifiedWithOffset(file2.toPath(), -5000L);
			File file3 = writeTrashFile("file3.txt", "content3");
			TimeUtil.setLastModifiedWithOffset(file3.toPath(), -5000L);

			assertNotNull(git.add().addFilepattern("file1.txt")
					.addFilepattern("file2.txt").addFilepattern("file3.txt").call());
			RevCommit commit = git.commit().setMessage("add files").call();
			assertNotNull(commit);

			DirCache cache = DirCache.read(db.getIndexFile(), db.getFS());
			int file1Size = cache.getEntry("file1.txt").getLength();
			int file2Size = cache.getEntry("file2.txt").getLength();
			int file3Size = cache.getEntry("file3.txt").getLength();
			ObjectId file2Id = cache.getEntry("file2.txt").getObjectId();
			ObjectId file3Id = cache.getEntry("file3.txt").getObjectId();
			assertTrue(file1Size > 0);
			assertTrue(file2Size > 0);
			assertTrue(file3Size > 0);

			// Smudge entries
			cache = DirCache.lock(db.getIndexFile(), db.getFS());
			cache.getEntry("file1.txt").setLength(0);
			cache.getEntry("file2.txt").setLength(0);
			cache.getEntry("file3.txt").setLength(0);
			cache.write();
			assertTrue(cache.commit());

			// Verify entries smudged
			cache = DirCache.read(db.getIndexFile(), db.getFS());
			assertEquals(0, cache.getEntry("file1.txt").getLength());
			assertEquals(0, cache.getEntry("file2.txt").getLength());
			assertEquals(0, cache.getEntry("file3.txt").getLength());

			TimeUtil.setLastModifiedWithOffset(db.getIndexFile().toPath(),
					-5000L);

			write(file1, "content4");

			TimeUtil.setLastModifiedWithOffset(file1.toPath(), 2500L);
			assertNotNull(git.commit().setMessage("edit file").setOnly("file1.txt")
					.call());

			cache = db.readDirCache();
			assertEquals(file1Size, cache.getEntry("file1.txt").getLength());
			assertEquals(file2Size, cache.getEntry("file2.txt").getLength());
			assertEquals(file3Size, cache.getEntry("file3.txt").getLength());
			assertEquals(file2Id, cache.getEntry("file2.txt").getObjectId());
			assertEquals(file3Id, cache.getEntry("file3.txt").getObjectId());
		}
	}

	@Ignore("very flaky when run with Hudson")
	@Test
	public void commitIgnoresSmudgedEntryWithDifferentId() throws Exception {
		try (Git git = new Git(db)) {
			File file1 = writeTrashFile("file1.txt", "content1");
			TimeUtil.setLastModifiedWithOffset(file1.toPath(), -5000L);
			File file2 = writeTrashFile("file2.txt", "content2");
			TimeUtil.setLastModifiedWithOffset(file2.toPath(), -5000L);

			assertNotNull(git.add().addFilepattern("file1.txt")
					.addFilepattern("file2.txt").call());
			RevCommit commit = git.commit().setMessage("add files").call();
			assertNotNull(commit);

			DirCache cache = DirCache.read(db.getIndexFile(), db.getFS());
			int file1Size = cache.getEntry("file1.txt").getLength();
			int file2Size = cache.getEntry("file2.txt").getLength();
			assertTrue(file1Size > 0);
			assertTrue(file2Size > 0);

			writeTrashFile("file2.txt", "content3");
			assertNotNull(git.add().addFilepattern("file2.txt").call());
			writeTrashFile("file2.txt", "content4");

			// Smudge entries
			cache = DirCache.lock(db.getIndexFile(), db.getFS());
			cache.getEntry("file1.txt").setLength(0);
			cache.getEntry("file2.txt").setLength(0);
			cache.write();
			assertTrue(cache.commit());

			// Verify entries smudged
			cache = db.readDirCache();
			assertEquals(0, cache.getEntry("file1.txt").getLength());
			assertEquals(0, cache.getEntry("file2.txt").getLength());

			TimeUtil.setLastModifiedWithOffset(db.getIndexFile().toPath(),
					-5000L);

			write(file1, "content5");
			TimeUtil.setLastModifiedWithOffset(file1.toPath(), 1000L);

			assertNotNull(git.commit().setMessage("edit file").setOnly("file1.txt")
					.call());

			cache = db.readDirCache();
			assertEquals(file1Size, cache.getEntry("file1.txt").getLength());
			assertEquals(0, cache.getEntry("file2.txt").getLength());
		}
	}

	@Test
	public void commitAfterSquashMerge() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit first = git.commit().setMessage("initial commit").call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			createBranch(first, "refs/heads/branch1");
			checkoutBranch("refs/heads/branch1");

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();
			git.commit().setMessage("second commit").call();
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			checkoutBranch("refs/heads/master");

			MergeResult result = git.merge()
					.include(db.exactRef("refs/heads/branch1"))
					.setSquash(true)
					.call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertEquals(MergeResult.MergeStatus.FAST_FORWARD_SQUASHED,
					result.getMergeStatus());

			// comment not set, should be inferred from SQUASH_MSG
			RevCommit squashedCommit = git.commit().call();

			assertEquals(1, squashedCommit.getParentCount());
			assertNull(db.readSquashCommitMsg());
			assertEquals("commit: Squashed commit of the following:", db
					.getReflogReader(Constants.HEAD).getLastEntry().getComment());
			assertEquals("commit: Squashed commit of the following:", db
					.getReflogReader(db.getBranch()).getLastEntry().getComment());
		}
	}

	@Test
	public void testReflogs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("f", "1");
			git.add().addFilepattern("f").call();
			git.commit().setMessage("c1").call();
			writeTrashFile("f", "2");
			git.commit().setMessage("c2").setAll(true).setReflogComment(null)
					.call();
			writeTrashFile("f", "3");
			git.commit().setMessage("c3").setAll(true)
					.setReflogComment("testRl").call();

			db.getReflogReader(Constants.HEAD).getReverseEntries();

			assertEquals("testRl;commit (initial): c1;", reflogComments(
					db.getReflogReader(Constants.HEAD).getReverseEntries()));
			assertEquals("testRl;commit (initial): c1;", reflogComments(
					db.getReflogReader(db.getBranch()).getReverseEntries()));
		}
	}

	private static String reflogComments(List<ReflogEntry> entries) {
		StringBuilder b = new StringBuilder();
		for (ReflogEntry e : entries) {
			b.append(e.getComment()).append(";");
		}
		return b.toString();
	}

	@Test(expected = WrongRepositoryStateException.class)
	public void commitAmendOnInitialShouldFail() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setAmend(true).setMessage("initial commit").call();
		}
	}

	@Test
	public void commitAmendWithoutAuthorShouldSetOriginalAuthorAndAuthorTime()
			throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();

			final String authorName = "First Author";
			final String authorEmail = "author@example.org";
			final Date authorDate = new Date(1349621117000L);
			PersonIdent firstAuthor = new PersonIdent(authorName, authorEmail,
					authorDate, TimeZone.getTimeZone("UTC"));
			git.commit().setMessage("initial commit").setAuthor(firstAuthor).call();

			RevCommit amended = git.commit().setAmend(true)
					.setMessage("amend commit").call();

			PersonIdent amendedAuthor = amended.getAuthorIdent();
			assertEquals(authorName, amendedAuthor.getName());
			assertEquals(authorEmail, amendedAuthor.getEmailAddress());
			assertEquals(authorDate.getTime(), amendedAuthor.getWhen().getTime());
		}
	}

	@Test
	public void commitAmendWithAuthorShouldUseIt() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			git.commit().setMessage("initial commit").call();

			RevCommit amended = git.commit().setAmend(true)
					.setAuthor("New Author", "newauthor@example.org")
					.setMessage("amend commit").call();

			PersonIdent amendedAuthor = amended.getAuthorIdent();
			assertEquals("New Author", amendedAuthor.getName());
			assertEquals("newauthor@example.org", amendedAuthor.getEmailAddress());
		}
	}

	@Test
	public void commitEmptyCommits() throws Exception {
		try (Git git = new Git(db)) {

			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit initial = git.commit().setMessage("initial commit")
					.call();

			RevCommit emptyFollowUp = git.commit()
					.setAuthor("New Author", "newauthor@example.org")
					.setMessage("no change").call();

			assertNotEquals(initial.getId(), emptyFollowUp.getId());
			assertEquals(initial.getTree().getId(),
					emptyFollowUp.getTree().getId());

			try {
				git.commit().setAuthor("New Author", "newauthor@example.org")
						.setMessage("again no change").setAllowEmpty(false)
						.call();
				fail("Didn't get the expected EmptyCommitException");
			} catch (EmptyCommitException e) {
				// expect this exception
			}

			// Allow empty commits also when setOnly was set
			git.commit().setAuthor("New Author", "newauthor@example.org")
					.setMessage("again no change").setOnly("file1")
					.setAllowEmpty(true).call();
		}
	}

	@Test
	public void commitOnlyShouldCommitUnmergedPathAndNotAffectOthers()
			throws Exception {
		DirCache index = db.lockDirCache();
		DirCacheBuilder builder = index.builder();
		addUnmergedEntry("unmerged1", builder);
		addUnmergedEntry("unmerged2", builder);
		DirCacheEntry other = new DirCacheEntry("other");
		other.setFileMode(FileMode.REGULAR_FILE);
		builder.add(other);
		builder.commit();

		writeTrashFile("unmerged1", "unmerged1 data");
		writeTrashFile("unmerged2", "unmerged2 data");
		writeTrashFile("other", "other data");

		assertEquals("[other, mode:100644]"
				+ "[unmerged1, mode:100644, stage:1]"
				+ "[unmerged1, mode:100644, stage:2]"
				+ "[unmerged1, mode:100644, stage:3]"
				+ "[unmerged2, mode:100644, stage:1]"
				+ "[unmerged2, mode:100644, stage:2]"
				+ "[unmerged2, mode:100644, stage:3]",
				indexState(0));

		try (Git git = new Git(db)) {
			RevCommit commit = git.commit().setOnly("unmerged1")
					.setMessage("Only one file").call();

			assertEquals("[other, mode:100644]" + "[unmerged1, mode:100644]"
					+ "[unmerged2, mode:100644, stage:1]"
					+ "[unmerged2, mode:100644, stage:2]"
					+ "[unmerged2, mode:100644, stage:3]",
					indexState(0));

			try (TreeWalk walk = TreeWalk.forPath(db, "unmerged1", commit.getTree())) {
				assertEquals(FileMode.REGULAR_FILE, walk.getFileMode(0));
			}
		}
	}

	@Test
	public void commitOnlyShouldHandleIgnored() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("subdir/foo", "Hello World");
			writeTrashFile("subdir/bar", "Hello World");
			writeTrashFile(".gitignore", "bar");
			git.add().addFilepattern("subdir").call();
			git.commit().setOnly("subdir").setMessage("first commit").call();
			assertEquals("[subdir/foo, mode:100644, content:Hello World]",
					indexState(CONTENT));
		}
	}

	@Test
	public void commitWithAutoCrlfAndNonNormalizedIndex() throws Exception {
		try (Git git = new Git(db)) {
			// Commit a file with CR/LF into the index
			FileBasedConfig config = db.getConfig();
			config.setString("core", null, "autocrlf", "false");
			config.save();
			writeTrashFile("file.txt", "line 1\r\nline 2\r\n");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Initial").call();
			assertEquals(
					"[file.txt, mode:100644, content:line 1\r\nline 2\r\n]",
					indexState(CONTENT));
			config.setString("core", null, "autocrlf", "true");
			config.save();
			writeTrashFile("file.txt", "line 1\r\nline 1.5\r\nline 2\r\n");
			writeTrashFile("file2.txt", "new\r\nfile\r\n");
			git.add().addFilepattern("file.txt").addFilepattern("file2.txt")
					.call();
			git.commit().setMessage("Second").call();
			assertEquals(
					"[file.txt, mode:100644, content:line 1\r\nline 1.5\r\nline 2\r\n]"
							+ "[file2.txt, mode:100644, content:new\nfile\n]",
					indexState(CONTENT));
			writeTrashFile("file2.txt", "new\r\nfile\r\ncontent\r\n");
			git.add().addFilepattern("file2.txt").call();
			git.commit().setMessage("Third").call();
			assertEquals(
					"[file.txt, mode:100644, content:line 1\r\nline 1.5\r\nline 2\r\n]"
							+ "[file2.txt, mode:100644, content:new\nfile\ncontent\n]",
					indexState(CONTENT));
		}
	}

	@Test
	public void testDeletionConflictWithAutoCrlf() throws Exception {
		try (Git git = new Git(db)) {
			// Commit a file with CR/LF into the index
			FileBasedConfig config = db.getConfig();
			config.setString("core", null, "autocrlf", "false");
			config.save();
			File file = writeTrashFile("file.txt", "foo\r\n");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Initial").call();
			// Switch to side branch
			git.checkout().setCreateBranch(true).setName("side").call();
			assertTrue(file.delete());
			git.rm().addFilepattern("file.txt").call();
			git.commit().setMessage("Side").call();
			// Switch on autocrlf=true
			config.setString("core", null, "autocrlf", "true");
			config.save();
			// Switch back to master and commit a conflict
			git.checkout().setName("master").call();
			writeTrashFile("file.txt", "foob\r\n");
			git.add().addFilepattern("file.txt").call();
			assertEquals("[file.txt, mode:100644, content:foob\r\n]",
					indexState(CONTENT));
			writeTrashFile("g", "file2.txt", "anything");
			git.add().addFilepattern("g/file2.txt");
			RevCommit master = git.commit().setMessage("Second").call();
			// Switch to side branch again so that the deletion is "ours"
			git.checkout().setName("side").call();
			// Cherry pick master: produces a delete-modify conflict.
			CherryPickResult pick = git.cherryPick().include(master).call();
			assertEquals("Expected a cherry-pick conflict",
					CherryPickStatus.CONFLICTING, pick.getStatus());
			// XXX: g/file2.txt should actually be staged already, but isn't.
			git.add().addFilepattern("g/file2.txt").call();
			// Resolve the conflict by taking the master version
			writeTrashFile("file.txt", "foob\r\n");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Cherry").call();
			// We expect this to be committed with a single LF since there is no
			// "ours" stage.
			assertEquals(
					"[file.txt, mode:100644, content:foob\n]"
							+ "[g/file2.txt, mode:100644, content:anything]",
					indexState(CONTENT));
		}
	}

	private void testConflictWithAutoCrlf(String baseLf, String lf)
			throws Exception {
		try (Git git = new Git(db)) {
			// Commit a file with CR/LF into the index
			FileBasedConfig config = db.getConfig();
			config.setString("core", null, "autocrlf", "false");
			config.save();
			writeTrashFile("file.txt", "foo" + baseLf);
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Initial").call();
			// Switch to side branch
			git.checkout().setCreateBranch(true).setName("side").call();
			writeTrashFile("file.txt", "bar\r\n");
			git.add().addFilepattern("file.txt").call();
			RevCommit side = git.commit().setMessage("Side").call();
			// Switch back to master and commit a conflict with the given lf
			git.checkout().setName("master");
			writeTrashFile("file.txt", "foob" + lf);
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Second").call();
			// Switch on autocrlf=true
			config.setString("core", null, "autocrlf", "true");
			config.save();
			// Cherry pick side: conflict. Resolve with CR-LF and commit.
			CherryPickResult pick = git.cherryPick().include(side).call();
			assertEquals("Expected a cherry-pick conflict",
					CherryPickStatus.CONFLICTING, pick.getStatus());
			writeTrashFile("file.txt", "foobar\r\n");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("Second").call();
			assertEquals("[file.txt, mode:100644, content:foobar" + lf + "]",
					indexState(CONTENT));
		}
	}

	@Test
	public void commitConflictWithAutoCrlfBaseCrLfOursLf() throws Exception {
		testConflictWithAutoCrlf("\r\n", "\n");
	}

	@Test
	public void commitConflictWithAutoCrlfBaseLfOursLf() throws Exception {
		testConflictWithAutoCrlf("\n", "\n");
	}

	@Test
	public void commitConflictWithAutoCrlfBasCrLfOursCrLf() throws Exception {
		testConflictWithAutoCrlf("\r\n", "\r\n");
	}

	@Test
	public void commitConflictWithAutoCrlfBaseLfOursCrLf() throws Exception {
		testConflictWithAutoCrlf("\n", "\r\n");
	}

	private static void addUnmergedEntry(String file, DirCacheBuilder builder) {
		DirCacheEntry stage1 = new DirCacheEntry(file, DirCacheEntry.STAGE_1);
		DirCacheEntry stage2 = new DirCacheEntry(file, DirCacheEntry.STAGE_2);
		DirCacheEntry stage3 = new DirCacheEntry(file, DirCacheEntry.STAGE_3);
		stage1.setFileMode(FileMode.REGULAR_FILE);
		stage2.setFileMode(FileMode.REGULAR_FILE);
		stage3.setFileMode(FileMode.REGULAR_FILE);
		builder.add(stage1);
		builder.add(stage2);
		builder.add(stage3);
	}

	@Test
	public void callSignerWithProperSigningKey() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();

			String[] signingKey = new String[1];
			PersonIdent[] signingCommitters = new PersonIdent[1];
			AtomicInteger callCount = new AtomicInteger();
			GpgSigner.setDefault(new GpgSigner() {
				@Override
				public void sign(CommitBuilder commit, String gpgSigningKey,
						PersonIdent signingCommitter, CredentialsProvider credentialsProvider) {
					signingKey[0] = gpgSigningKey;
					signingCommitters[0] = signingCommitter;
					callCount.incrementAndGet();
				}

				@Override
				public boolean canLocateSigningKey(String gpgSigningKey,
						PersonIdent signingCommitter,
						CredentialsProvider credentialsProvider)
						throws CanceledException {
					return false;
				}
			});

			// first call should use config, which is expected to be null at
			// this time
			git.commit().setCommitter(committer).setSign(Boolean.TRUE)
					.setMessage("initial commit")
					.call();
			assertNull(signingKey[0]);
			assertEquals(1, callCount.get());
			assertSame(committer, signingCommitters[0]);

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();

			// second commit applies config value
			String expectedConfigSigningKey = "config-" + System.nanoTime();
			StoredConfig config = git.getRepository().getConfig();
			config.setString("user", null, "signingKey",
					expectedConfigSigningKey);
			config.save();

			git.commit().setCommitter(committer).setSign(Boolean.TRUE)
					.setMessage("initial commit")
					.call();
			assertEquals(expectedConfigSigningKey, signingKey[0]);
			assertEquals(2, callCount.get());
			assertSame(committer, signingCommitters[0]);

			writeTrashFile("file3", "file3");
			git.add().addFilepattern("file3").call();

			// now use specific on api
			String expectedSigningKey = "my-" + System.nanoTime();
			git.commit().setCommitter(committer).setSign(Boolean.TRUE)
					.setSigningKey(expectedSigningKey)
					.setMessage("initial commit").call();
			assertEquals(expectedSigningKey, signingKey[0]);
			assertEquals(3, callCount.get());
			assertSame(committer, signingCommitters[0]);
		}
	}

	@Test
	public void callSignerOnlyWhenSigning() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();

			AtomicInteger callCount = new AtomicInteger();
			GpgSigner.setDefault(new GpgSigner() {
				@Override
				public void sign(CommitBuilder commit, String gpgSigningKey,
						PersonIdent signingCommitter, CredentialsProvider credentialsProvider) {
					callCount.incrementAndGet();
				}

				@Override
				public boolean canLocateSigningKey(String gpgSigningKey,
						PersonIdent signingCommitter,
						CredentialsProvider credentialsProvider)
						throws CanceledException {
					return false;
				}
			});

			// first call should use config, which is expected to be null at
			// this time
			git.commit().setMessage("initial commit").call();
			assertEquals(0, callCount.get());

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();

			// now force signing
			git.commit().setSign(Boolean.TRUE).setMessage("commit").call();
			assertEquals(1, callCount.get());

			writeTrashFile("file3", "file3");
			git.add().addFilepattern("file3").call();

			// now rely on config
			StoredConfig config = git.getRepository().getConfig();
			config.setBoolean("commit", null, "gpgSign", true);
			config.save();

			git.commit().setMessage("commit").call();
			assertEquals(2, callCount.get());

			writeTrashFile("file4", "file4");
			git.add().addFilepattern("file4").call();

			// now force "no-sign" (even though config is true)
			git.commit().setSign(Boolean.FALSE).setMessage("commit").call();
			assertEquals(2, callCount.get());
		}
	}
}
