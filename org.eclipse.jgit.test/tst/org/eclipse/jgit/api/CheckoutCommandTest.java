/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
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

import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

public class CheckoutCommandTest extends RepositoryTestCase {
	private Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		BuiltinLFS.register();
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		initialCommit = git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		secondCommit = git.commit().setMessage("Second commit").call();
	}

	@Test
	public void testSimpleCheckout() throws Exception {
		git.checkout().setName("test").call();
	}

	@Test
	public void testCheckout() throws Exception {
		git.checkout().setName("test").call();
		assertEquals("[Test.txt, mode:100644, content:Some change]",
				indexState(CONTENT));
		Ref result = git.checkout().setName("master").call();
		assertEquals("[Test.txt, mode:100644, content:Hello world]",
				indexState(CONTENT));
		assertEquals("refs/heads/master", result.getName());
		assertEquals("refs/heads/master", git.getRepository().getFullBranch());
	}

	@Test
	public void testCreateBranchOnCheckout() throws Exception {
		git.checkout().setCreateBranch(true).setName("test2").call();
		assertNotNull(db.exactRef("refs/heads/test2"));
	}

	@Test
	public void testCheckoutToNonExistingBranch() throws GitAPIException {
		try {
			git.checkout().setName("badbranch").call();
			fail("Should have failed");
		} catch (RefNotFoundException e) {
			// except to hit here
		}
	}

	@Test
	public void testCheckoutWithConflict() {
		CheckoutCommand co = git.checkout();
		try {
			writeTrashFile("Test.txt", "Another change");
			assertEquals(Status.NOT_TRIED, co.getResult().getStatus());
			co.setName("master").call();
			fail("Should have failed");
		} catch (Exception e) {
			assertEquals(Status.CONFLICTS, co.getResult().getStatus());
			assertTrue(co.getResult().getConflictList().contains("Test.txt"));
		}
	}

	@Test
	public void testCheckoutWithNonDeletedFiles() throws Exception {
		File testFile = writeTrashFile("temp", "");
		try (FileInputStream fis = new FileInputStream(testFile)) {
			FileUtils.delete(testFile);
			return;
		} catch (IOException e) {
			// the test makes only sense if deletion of
			// a file with open stream fails
		}
		FileUtils.delete(testFile);
		CheckoutCommand co = git.checkout();
		// delete Test.txt in branch test
		testFile = new File(db.getWorkTree(), "Test.txt");
		assertTrue(testFile.exists());
		FileUtils.delete(testFile);
		assertFalse(testFile.exists());
		git.add().addFilepattern("Test.txt");
		git.commit().setMessage("Delete Test.txt").setAll(true).call();
		git.checkout().setName("master").call();
		assertTrue(testFile.exists());
		// lock the file so it can't be deleted (in Windows, that is)
		try (FileInputStream fis = new FileInputStream(testFile)) {
			assertEquals(Status.NOT_TRIED, co.getResult().getStatus());
			co.setName("test").call();
			assertTrue(testFile.exists());
			assertEquals(Status.NONDELETED, co.getResult().getStatus());
			assertTrue(co.getResult().getUndeletedList().contains("Test.txt"));
		}
	}

	@Test
	public void testCheckoutCommit() throws Exception {
		Ref result = git.checkout().setName(initialCommit.name()).call();
		assertEquals("[Test.txt, mode:100644, content:Hello world]",
				indexState(CONTENT));
		assertNull(result);
		assertEquals(initialCommit.name(), git.getRepository().getFullBranch());
	}

	@Test
	public void testCheckoutLightweightTag() throws Exception {
		git.tag().setAnnotated(false).setName("test-tag")
				.setObjectId(initialCommit).call();
		Ref result = git.checkout().setName("test-tag").call();

		assertNull(result);
		assertEquals(initialCommit.getId(), db.resolve(Constants.HEAD));
		assertHeadDetached();
	}

	@Test
	public void testCheckoutAnnotatedTag() throws Exception {
		git.tag().setAnnotated(true).setName("test-tag")
				.setObjectId(initialCommit).call();
		Ref result = git.checkout().setName("test-tag").call();

		assertNull(result);
		assertEquals(initialCommit.getId(), db.resolve(Constants.HEAD));
		assertHeadDetached();
	}

	@Test
	public void testCheckoutRemoteTrackingWithUpstream() throws Exception {
		Repository db2 = createRepositoryWithRemote();

		Git.wrap(db2).checkout().setCreateBranch(true).setName("test")
				.setStartPoint("origin/test")
				.setUpstreamMode(SetupUpstreamMode.TRACK).call();

		assertEquals("refs/heads/test",
				db2.exactRef(Constants.HEAD).getTarget().getName());
		StoredConfig config = db2.getConfig();
		assertEquals("origin", config.getString(
				ConfigConstants.CONFIG_BRANCH_SECTION, "test",
				ConfigConstants.CONFIG_KEY_REMOTE));
		assertEquals("refs/heads/test", config.getString(
				ConfigConstants.CONFIG_BRANCH_SECTION, "test",
				ConfigConstants.CONFIG_KEY_MERGE));
	}

	@Test
	public void testCheckoutRemoteTrackingWithoutLocalBranch() throws Exception {
		Repository db2 = createRepositoryWithRemote();

		// checkout remote tracking branch in second repository
		// (no local branches exist yet in second repository)
		Git.wrap(db2).checkout().setName("remotes/origin/test").call();
		assertEquals("[Test.txt, mode:100644, content:Some change]",
				indexState(db2, CONTENT));
	}



	@Test
	public void testCheckoutOfFileWithInexistentParentDir() throws Exception {
		File a = writeTrashFile("dir/a.txt", "A");
		writeTrashFile("dir/b.txt", "A");
		git.add().addFilepattern("dir/a.txt").addFilepattern("dir/b.txt")
				.call();
		git.commit().setMessage("Added dir").call();

		File dir = new File(db.getWorkTree(), "dir");
		FileUtils.delete(dir, FileUtils.RECURSIVE);

		git.checkout().addPath("dir/a.txt").call();
		assertTrue(a.exists());
	}

	@Test
	public void testCheckoutOfDirectoryShouldBeRecursive() throws Exception {
		File a = writeTrashFile("dir/a.txt", "A");
		File b = writeTrashFile("dir/sub/b.txt", "B");
		git.add().addFilepattern("dir").call();
		git.commit().setMessage("Added dir").call();

		write(a, "modified");
		write(b, "modified");
		git.checkout().addPath("dir").call();

		assertThat(read(a), is("A"));
		assertThat(read(b), is("B"));
	}

	@Test
	public void testCheckoutAllPaths() throws Exception {
		File a = writeTrashFile("dir/a.txt", "A");
		File b = writeTrashFile("dir/sub/b.txt", "B");
		git.add().addFilepattern("dir").call();
		git.commit().setMessage("Added dir").call();

		write(a, "modified");
		write(b, "modified");
		git.checkout().setAllPaths(true).call();

		assertThat(read(a), is("A"));
		assertThat(read(b), is("B"));
	}

	@Test
	public void testCheckoutWithStartPoint() throws Exception {
		File a = writeTrashFile("a.txt", "A");
		git.add().addFilepattern("a.txt").call();
		RevCommit first = git.commit().setMessage("Added a").call();

		write(a, "other");
		git.commit().setAll(true).setMessage("Other").call();

		git.checkout().setCreateBranch(true).setName("a")
				.setStartPoint(first.getId().getName()).call();

		assertThat(read(a), is("A"));
	}

	@Test
	public void testCheckoutWithStartPointOnlyCertainFiles() throws Exception {
		File a = writeTrashFile("a.txt", "A");
		File b = writeTrashFile("b.txt", "B");
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		RevCommit first = git.commit().setMessage("First").call();

		write(a, "other");
		write(b, "other");
		git.commit().setAll(true).setMessage("Other").call();

		git.checkout().setCreateBranch(true).setName("a")
				.setStartPoint(first.getId().getName()).addPath("a.txt").call();

		assertThat(read(a), is("A"));
		assertThat(read(b), is("other"));
	}

	@Test
	public void testDetachedHeadOnCheckout() throws JGitInternalException,
			IOException, GitAPIException {
		CheckoutCommand co = git.checkout();
		co.setName("master").call();

		String commitId = db.exactRef(R_HEADS + MASTER).getObjectId().name();
		co = git.checkout();
		co.setName(commitId).call();

		assertHeadDetached();
	}

	@Test
	public void testUpdateSmudgedEntries() throws Exception {
		git.branchCreate().setName("test2").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test2");

		File file = new File(db.getWorkTree(), "Test.txt");
		long size = file.length();
		long mTime = file.lastModified() - 5000L;
		assertTrue(file.setLastModified(mTime));

		DirCache cache = DirCache.lock(db.getIndexFile(), db.getFS());
		DirCacheEntry entry = cache.getEntry("Test.txt");
		assertNotNull(entry);
		entry.setLength(0);
		entry.setLastModified(0);
		cache.write();
		assertTrue(cache.commit());

		cache = DirCache.read(db.getIndexFile(), db.getFS());
		entry = cache.getEntry("Test.txt");
		assertNotNull(entry);
		assertEquals(0, entry.getLength());
		assertEquals(0, entry.getLastModified());

		db.getIndexFile().setLastModified(
				db.getIndexFile().lastModified() - 5000);

		assertNotNull(git.checkout().setName("test").call());

		cache = DirCache.read(db.getIndexFile(), db.getFS());
		entry = cache.getEntry("Test.txt");
		assertNotNull(entry);
		assertEquals(size, entry.getLength());
		assertEquals(mTime, entry.getLastModified());
	}

	@Test
	public void testCheckoutOrphanBranch() throws Exception {
		CheckoutCommand co = newOrphanBranchCommand();
		assertCheckoutRef(co.call());

		File HEAD = new File(trash, ".git/HEAD");
		String headRef = read(HEAD);
		assertEquals("ref: refs/heads/orphanbranch\n", headRef);
		assertEquals(2, trash.list().length);

		File heads = new File(trash, ".git/refs/heads");
		assertEquals(2, heads.listFiles().length);

		this.assertNoHead();
		this.assertRepositoryCondition(1);
		assertEquals(CheckoutResult.NOT_TRIED_RESULT, co.getResult());
	}

	private Repository createRepositoryWithRemote() throws IOException,
			URISyntaxException, MalformedURLException, GitAPIException,
			InvalidRemoteException, TransportException {
		// create second repository
		Repository db2 = createWorkRepository();
		try (Git git2 = new Git(db2)) {
			// setup the second repository to fetch from the first repository
			final StoredConfig config = db2.getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
			URIish uri = new URIish(db.getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.update(config);
			config.save();

			// fetch from first repository
			git2.fetch().setRemote("origin")
					.setRefSpecs("+refs/heads/*:refs/remotes/origin/*").call();
			return db2;
		}
	}

	private CheckoutCommand newOrphanBranchCommand() {
		return git.checkout().setOrphan(true)
				.setName("orphanbranch");
	}

	private static void assertCheckoutRef(Ref ref) {
		assertNotNull(ref);
		assertEquals("refs/heads/orphanbranch", ref.getTarget().getName());
	}

	private void assertNoHead() throws IOException {
		assertNull(db.resolve("HEAD"));
	}

	private void assertHeadDetached() throws IOException {
		Ref head = db.exactRef(Constants.HEAD);
		assertFalse(head.isSymbolic());
		assertSame(head, head.getTarget());
	}

	private void assertRepositoryCondition(int files) throws GitAPIException {
		org.eclipse.jgit.api.Status status = this.git.status().call();
		assertFalse(status.isClean());
		assertEquals(files, status.getAdded().size());
	}

	@Test
	public void testCreateOrphanBranchWithStartCommit() throws Exception {
		CheckoutCommand co = newOrphanBranchCommand();
		Ref ref = co.setStartPoint(initialCommit).call();
		assertCheckoutRef(ref);
		assertEquals(2, trash.list().length);
		this.assertNoHead();
		this.assertRepositoryCondition(1);
	}

	@Test
	public void testCreateOrphanBranchWithStartPoint() throws Exception {
		CheckoutCommand co = newOrphanBranchCommand();
		Ref ref = co.setStartPoint("HEAD^").call();
		assertCheckoutRef(ref);

		assertEquals(2, trash.list().length);
		this.assertNoHead();
		this.assertRepositoryCondition(1);
	}

	@Test
	public void testInvalidRefName() throws Exception {
		try {
			git.checkout().setOrphan(true).setName("../invalidname").call();
			fail("Should have failed");
		} catch (InvalidRefNameException e) {
			// except to hit here
		}
	}

	@Test
	public void testNullRefName() throws Exception {
		try {
			git.checkout().setOrphan(true).setName(null).call();
			fail("Should have failed");
		} catch (InvalidRefNameException e) {
			// except to hit here
		}
	}

	@Test
	public void testAlreadyExists() throws Exception {
		this.git.checkout().setCreateBranch(true).setName("orphanbranch")
				.call();
		this.git.checkout().setName("master").call();

		try {
			newOrphanBranchCommand().call();
			fail("Should have failed");
		} catch (RefAlreadyExistsException e) {
			// except to hit here
		}
	}

	// TODO: write a faster test which depends less on characteristics of
	// underlying filesystem/OS.
	@Test
	public void testCheckoutAutoCrlfTrue() throws Exception {
		int nrOfAutoCrlfTestFiles = 200;

		FileBasedConfig c = db.getConfig();
		c.setString("core", null, "autocrlf", "true");
		c.save();

		AddCommand add = git.add();
		for (int i = 100; i < 100 + nrOfAutoCrlfTestFiles; i++) {
			writeTrashFile("Test_" + i + ".txt", "Hello " + i
					+ " world\nX\nYU\nJK\n");
			add.addFilepattern("Test_" + i + ".txt");
		}
		fsTick(null);
		add.call();
		RevCommit c1 = git.commit().setMessage("add some lines").call();

		add = git.add();
		for (int i = 100; i < 100 + nrOfAutoCrlfTestFiles; i++) {
			writeTrashFile("Test_" + i + ".txt", "Hello " + i
					+ " world\nX\nY\n");
			add.addFilepattern("Test_" + i + ".txt");
		}
		fsTick(null);
		add.call();
		git.commit().setMessage("add more").call();

		git.checkout().setName(c1.getName()).call();

		boolean foundUnsmudged = false;
		DirCache dc = db.readDirCache();
		for (int i = 100; i < 100 + nrOfAutoCrlfTestFiles; i++) {
			DirCacheEntry entry = dc.getEntry(
					"Test_" + i + ".txt");
			if (!entry.isSmudged()) {
				foundUnsmudged = true;
				assertEquals("unexpected file length in git index", 28,
						entry.getLength());
			}
		}
		org.junit.Assume.assumeTrue(foundUnsmudged);
	}

	@Test
	public void testSmudgeFilter_modifyExisting() throws IOException, GitAPIException {
		File script = writeTempFile("sed s/o/e/g");
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "smudge",
				"sh " + slashify(script.getPath()));
		config.save();

		writeTrashFile(".gitattributes", "*.txt filter=lfs");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("src/a.tmp", "x");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.txt", "x\n");
		git.add().addFilepattern("src/a.tmp").addFilepattern("src/a.txt")
				.call();
		RevCommit content1 = git.commit().setMessage("add content").call();

		writeTrashFile("src/a.tmp", "foo");
		writeTrashFile("src/a.txt", "foo\n");
		git.add().addFilepattern("src/a.tmp").addFilepattern("src/a.txt")
				.call();
		RevCommit content2 = git.commit().setMessage("changed content").call();

		git.checkout().setName(content1.getName()).call();
		git.checkout().setName(content2.getName()).call();

		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=lfs][Test.txt, mode:100644, content:Some change][src/a.tmp, mode:100644, content:foo][src/a.txt, mode:100644, content:foo\n]",
				indexState(CONTENT));
		assertEquals(Sets.of("src/a.txt"), git.status().call().getModified());
		assertEquals("foo", read("src/a.tmp"));
		assertEquals("fee\n", read("src/a.txt"));
	}

	@Test
	public void testSmudgeFilter_createNew()
			throws IOException, GitAPIException {
		File script = writeTempFile("sed s/o/e/g");
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "smudge",
				"sh " + slashify(script.getPath()));
		config.save();

		writeTrashFile("foo", "foo");
		git.add().addFilepattern("foo").call();
		RevCommit initial = git.commit().setMessage("initial").call();

		writeTrashFile(".gitattributes", "*.txt filter=lfs");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("src/a.tmp", "foo");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.txt", "foo\n");
		git.add().addFilepattern("src/a.tmp").addFilepattern("src/a.txt")
				.call();
		RevCommit content = git.commit().setMessage("added content").call();

		git.checkout().setName(initial.getName()).call();
		git.checkout().setName(content.getName()).call();

		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=lfs][Test.txt, mode:100644, content:Some change][foo, mode:100644, content:foo][src/a.tmp, mode:100644, content:foo][src/a.txt, mode:100644, content:foo\n]",
				indexState(CONTENT));
		assertEquals("foo", read("src/a.tmp"));
		assertEquals("fee\n", read("src/a.txt"));
	}

	@Test
	public void testSmudgeFilter_deleteFileAndRestoreFromCommit()
			throws IOException, GitAPIException {
		File script = writeTempFile("sed s/o/e/g");
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "smudge",
				"sh " + slashify(script.getPath()));
		config.save();

		writeTrashFile("foo", "foo");
		git.add().addFilepattern("foo").call();
		git.commit().setMessage("initial").call();

		writeTrashFile(".gitattributes", "*.txt filter=lfs");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("src/a.tmp", "foo");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.txt", "foo\n");
		git.add().addFilepattern("src/a.tmp").addFilepattern("src/a.txt")
				.call();
		RevCommit content = git.commit().setMessage("added content").call();

		deleteTrashFile("src/a.txt");
		git.checkout().setStartPoint(content.getName()).addPath("src/a.txt")
				.call();

		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=lfs][Test.txt, mode:100644, content:Some change][foo, mode:100644, content:foo][src/a.tmp, mode:100644, content:foo][src/a.txt, mode:100644, content:foo\n]",
				indexState(CONTENT));
		assertEquals("foo", read("src/a.tmp"));
		assertEquals("fee\n", read("src/a.txt"));
	}

	@Test
	public void testSmudgeFilter_deleteFileAndRestoreFromIndex()
			throws IOException, GitAPIException {
		File script = writeTempFile("sed s/o/e/g");
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "smudge",
				"sh " + slashify(script.getPath()));
		config.save();

		writeTrashFile("foo", "foo");
		git.add().addFilepattern("foo").call();
		git.commit().setMessage("initial").call();

		writeTrashFile(".gitattributes", "*.txt filter=lfs");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("src/a.tmp", "foo");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.txt", "foo\n");
		git.add().addFilepattern("src/a.tmp").addFilepattern("src/a.txt")
				.call();
		git.commit().setMessage("added content").call();

		deleteTrashFile("src/a.txt");
		git.checkout().addPath("src/a.txt").call();

		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=lfs][Test.txt, mode:100644, content:Some change][foo, mode:100644, content:foo][src/a.tmp, mode:100644, content:foo][src/a.txt, mode:100644, content:foo\n]",
				indexState(CONTENT));
		assertEquals("foo", read("src/a.tmp"));
		assertEquals("fee\n", read("src/a.txt"));
	}

	@Test
	public void testSmudgeFilter_deleteFileAndCreateBranchAndRestoreFromCommit()
			throws IOException, GitAPIException {
		File script = writeTempFile("sed s/o/e/g");
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "lfs", "smudge",
				"sh " + slashify(script.getPath()));
		config.save();

		writeTrashFile("foo", "foo");
		git.add().addFilepattern("foo").call();
		git.commit().setMessage("initial").call();

		writeTrashFile(".gitattributes", "*.txt filter=lfs");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("src/a.tmp", "foo");
		// Caution: we need a trailing '\n' since sed on mac always appends
		// linefeeds if missing
		writeTrashFile("src/a.txt", "foo\n");
		git.add().addFilepattern("src/a.tmp").addFilepattern("src/a.txt")
				.call();
		RevCommit content = git.commit().setMessage("added content").call();

		deleteTrashFile("src/a.txt");
		git.checkout().setName("newBranch").setCreateBranch(true)
				.setStartPoint(content).addPath("src/a.txt").call();

		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=lfs][Test.txt, mode:100644, content:Some change][foo, mode:100644, content:foo][src/a.tmp, mode:100644, content:foo][src/a.txt, mode:100644, content:foo\n]",
				indexState(CONTENT));
		assertEquals("foo", read("src/a.tmp"));
		assertEquals("fee\n", read("src/a.txt"));
	}

	@Test
	public void testSmudgeAndClean() throws Exception {
		File clean_filter = writeTempFile("sed s/V1/@version/g");
		File smudge_filter = writeTempFile("sed s/@version/V1/g");

		try (Git git2 = new Git(db)) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("filter", "lfs", "smudge",
					"sh " + slashify(smudge_filter.getPath()));
			config.setString("filter", "lfs", "clean",
					"sh " + slashify(clean_filter.getPath()));
			config.setBoolean("filter", "lfs", "useJGitBuiltin", true);
			config.save();
			writeTrashFile(".gitattributes", "filterTest.txt filter=lfs");
			git2.add().addFilepattern(".gitattributes").call();
			git2.commit().setMessage("add attributes").call();

			fsTick(writeTrashFile("filterTest.txt", "hello world, V1\n"));
			git2.add().addFilepattern("filterTest.txt").call();
			RevCommit one = git2.commit().setMessage("add filterText.txt").call();
			assertEquals(
					"[.gitattributes, mode:100644, content:filterTest.txt filter=lfs][Test.txt, mode:100644, content:Some change][filterTest.txt, mode:100644, content:version https://git-lfs.github.com/spec/v1\noid sha256:7bd5d32e5c494354aa4c2473a1306d0ce7b52cc3bffeb342c03cd517ef8cf8da\nsize 16\n]",
					indexState(CONTENT));

			fsTick(writeTrashFile("filterTest.txt", "bon giorno world, V1\n"));
			git2.add().addFilepattern("filterTest.txt").call();
			RevCommit two = git2.commit().setMessage("modified filterTest.txt").call();

			assertTrue(git2.status().call().isClean());
			assertEquals(
					"[.gitattributes, mode:100644, content:filterTest.txt filter=lfs][Test.txt, mode:100644, content:Some change][filterTest.txt, mode:100644, content:version https://git-lfs.github.com/spec/v1\noid sha256:087148cccf53b0049c56475c1595113c9da4b638997c3489af8ac7108d51ef13\nsize 21\n]",
					indexState(CONTENT));

			git2.checkout().setName(one.getName()).call();
			assertTrue(git2.status().call().isClean());
			assertEquals(
					"[.gitattributes, mode:100644, content:filterTest.txt filter=lfs][Test.txt, mode:100644, content:Some change][filterTest.txt, mode:100644, content:version https://git-lfs.github.com/spec/v1\noid sha256:7bd5d32e5c494354aa4c2473a1306d0ce7b52cc3bffeb342c03cd517ef8cf8da\nsize 16\n]",
					indexState(CONTENT));
			assertEquals("hello world, V1\n", read("filterTest.txt"));

			git2.checkout().setName(two.getName()).call();
			assertTrue(git2.status().call().isClean());
			assertEquals(
					"[.gitattributes, mode:100644, content:filterTest.txt filter=lfs][Test.txt, mode:100644, content:Some change][filterTest.txt, mode:100644, content:version https://git-lfs.github.com/spec/v1\noid sha256:087148cccf53b0049c56475c1595113c9da4b638997c3489af8ac7108d51ef13\nsize 21\n]",
					indexState(CONTENT));
			assertEquals("bon giorno world, V1\n", read("filterTest.txt"));
		}
	}

	@Test
	public void testNonDeletableFilesOnWindows()
			throws GitAPIException, IOException {
		// Only on windows a FileInputStream blocks us from deleting a file
		org.junit.Assume.assumeTrue(SystemReader.getInstance().isWindows());
		writeTrashFile("toBeModified.txt", "a");
		writeTrashFile("toBeDeleted.txt", "a");
		git.add().addFilepattern(".").call();
		RevCommit addFiles = git.commit().setMessage("add more files").call();

		git.rm().setCached(false).addFilepattern("Test.txt")
				.addFilepattern("toBeDeleted.txt").call();
		writeTrashFile("toBeModified.txt", "b");
		writeTrashFile("toBeCreated.txt", "a");
		git.add().addFilepattern(".").call();
		RevCommit crudCommit = git.commit().setMessage("delete, modify, add")
				.call();
		git.checkout().setName(addFiles.getName()).call();
		try ( FileInputStream fis=new FileInputStream(new File(db.getWorkTree(), "Test.txt")) ) {
			CheckoutCommand coCommand = git.checkout();
			coCommand.setName(crudCommit.getName()).call();
			CheckoutResult result = coCommand.getResult();
			assertEquals(Status.NONDELETED, result.getStatus());
			assertEquals("[Test.txt, toBeDeleted.txt]",
					result.getRemovedList().toString());
			assertEquals("[toBeCreated.txt, toBeModified.txt]",
					result.getModifiedList().toString());
			assertEquals("[Test.txt]", result.getUndeletedList().toString());
			assertTrue(result.getConflictList().isEmpty());
		}
	}

	private File writeTempFile(String body) throws IOException {
		File f = File.createTempFile("CheckoutCommandTest_", "");
		JGitTestUtil.write(f, body);
		return f;
	}
}
