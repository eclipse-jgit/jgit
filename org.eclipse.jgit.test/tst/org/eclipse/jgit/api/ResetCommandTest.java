/*
 * Copyright (C) 2011-2018, Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.time.Instant.EPOCH;
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class ResetCommandTest extends RepositoryTestCase {

	private Git git;

	private RevCommit initialCommit;

	private RevCommit secondCommit;

	private File indexFile;

	private File indexNestedFile;

	private File untrackedFile;

	private DirCacheEntry prestage;

	public void setupRepository() throws IOException, JGitInternalException,
			GitAPIException {

		// create initial commit
		git = new Git(db);
		initialCommit = git.commit().setMessage("initial commit").call();

		// create file
		indexFile = writeTrashFile("a.txt", "content");

		// create nested file
		indexNestedFile = writeTrashFile("dir/b.txt", "content");

		// add files and commit them
		git.add().addFilepattern("a.txt").addFilepattern("dir/b.txt").call();
		secondCommit = git.commit().setMessage("adding a.txt and dir/b.txt").call();

		prestage = DirCache.read(db.getIndexFile(), db.getFS()).getEntry(indexFile.getName());

		// modify files and add to index
		writeTrashFile("a.txt", "new content");
		writeTrashFile("dir/b.txt", "new content");
		git.add().addFilepattern("a.txt").addFilepattern("dir/b.txt").call();

		// create a file not added to the index
		untrackedFile = writeTrashFile("notAddedToIndex.txt", "content");
	}

	@Test
	public void testHardReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		ResetCommand reset = git.reset();
		assertSameAsHead(reset.setMode(ResetType.HARD)
				.setRef(initialCommit.getName()).call());
		assertFalse("reflog should be enabled", reset.isReflogDisabled());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files were removed
		assertFalse(indexFile.exists());
		assertFalse(indexNestedFile.exists());
		assertTrue(untrackedFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));
		assertReflog(prevHead, head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testHardResetReflogDisabled() throws Exception {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		ResetCommand reset = git.reset();
		assertSameAsHead(reset.setMode(ResetType.HARD)
				.setRef(initialCommit.getName()).disableRefLog(true).call());
		assertTrue("reflog should be disabled", reset.isReflogDisabled());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files were removed
		assertFalse(indexFile.exists());
		assertFalse(indexNestedFile.exists());
		assertTrue(untrackedFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));
		assertReflogDisabled(head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testHardResetWithConflicts_OverwriteUntrackedFile() throws Exception {
		setupRepository();

		git.rm().setCached(true).addFilepattern("a.txt").call();
		assertTrue(new File(db.getWorkTree(), "a.txt").exists());

		git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
		assertTrue(new File(db.getWorkTree(), "a.txt").exists());
		assertEquals("content", read(new File(db.getWorkTree(), "a.txt")));
	}

	@Test
	public void testHardResetWithConflicts_DeleteFileFolderConflict() throws Exception {
		setupRepository();

		writeTrashFile("dir-or-file/c.txt", "content");
		git.add().addFilepattern("dir-or-file/c.txt").call();

		FileUtils.delete(new File(db.getWorkTree(), "dir-or-file"), FileUtils.RECURSIVE);
		writeTrashFile("dir-or-file", "content");

		git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
		assertFalse(new File(db.getWorkTree(), "dir-or-file").exists());
	}

	@Test
	public void testHardResetWithConflicts_CreateFolder_UnstagedChanges() throws Exception {
		setupRepository();

		writeTrashFile("dir-or-file/c.txt", "content");
		git.add().addFilepattern("dir-or-file/c.txt").call();
		git.commit().setMessage("adding dir-or-file/c.txt").call();

		FileUtils.delete(new File(db.getWorkTree(), "dir-or-file"), FileUtils.RECURSIVE);
		writeTrashFile("dir-or-file", "content");

		// bug 479266: cannot create folder "dir-or-file"
		git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
		assertTrue(new File(db.getWorkTree(), "dir-or-file/c.txt").exists());
	}

	@Test
	public void testHardResetWithConflicts_DeleteFolder_UnstagedChanges() throws Exception {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);

		writeTrashFile("dir-or-file/c.txt", "content");
		git.add().addFilepattern("dir-or-file/c.txt").call();
		git.commit().setMessage("adding dir-or-file/c.txt").call();

		writeTrashFile("dir-or-file-2/d.txt", "content");
		git.add().addFilepattern("dir-or-file-2/d.txt").call();
		FileUtils.delete(new File(db.getWorkTree(), "dir-or-file-2"), FileUtils.RECURSIVE);
		writeTrashFile("dir-or-file-2", "content");

		// bug 479266: cannot delete folder "dir-or-file"
		git.reset().setMode(ResetType.HARD).setRef(prevHead.getName()).call();
		assertFalse(new File(db.getWorkTree(), "dir-or-file").exists());
		assertFalse(new File(db.getWorkTree(), "dir-or-file-2").exists());
	}

	@Test
	public void testResetToNonexistingHEAD() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {

		// create a file in the working tree of a fresh repo
		git = new Git(db);
		writeTrashFile("f", "content");

		try {
			git.reset().setRef(Constants.HEAD).call();
			fail("Expected JGitInternalException didn't occur");
		} catch (JGitInternalException e) {
			// got the expected exception
		}
	}

	@Test
	public void testSoftReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		assertSameAsHead(git.reset().setMode(ResetType.SOFT)
				.setRef(initialCommit.getName()).call());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		// fileInIndex must no longer be in HEAD but has to be in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertTrue(inIndex(indexFile.getName()));
		assertReflog(prevHead, head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testMixedReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		assertSameAsHead(git.reset().setMode(ResetType.MIXED)
				.setRef(initialCommit.getName()).call());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));

		assertReflog(prevHead, head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testMixedResetRetainsSizeAndModifiedTime() throws Exception {
		git = new Git(db);

		Files.setLastModifiedTime(writeTrashFile("a.txt", "a").toPath(),
				FileTime.from(Instant.now().minusSeconds(60)));
		assertNotNull(git.add().addFilepattern("a.txt").call());
		assertNotNull(git.commit().setMessage("a commit").call());

		Files.setLastModifiedTime(writeTrashFile("b.txt", "b").toPath(),
				FileTime.from(Instant.now().minusSeconds(60)));
		assertNotNull(git.add().addFilepattern("b.txt").call());
		RevCommit commit2 = git.commit().setMessage("b commit").call();
		assertNotNull(commit2);

		DirCache cache = db.readDirCache();

		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNotNull(aEntry);
		assertTrue(aEntry.getLength() > 0);
		assertTrue(aEntry.getLastModifiedInstant().compareTo(EPOCH) > 0);

		DirCacheEntry bEntry = cache.getEntry("b.txt");
		assertNotNull(bEntry);
		assertTrue(bEntry.getLength() > 0);
		assertTrue(bEntry.getLastModifiedInstant().compareTo(EPOCH) > 0);

		assertSameAsHead(git.reset().setMode(ResetType.MIXED)
				.setRef(commit2.getName()).call());

		cache = db.readDirCache();

		DirCacheEntry mixedAEntry = cache.getEntry("a.txt");
		assertNotNull(mixedAEntry);
		assertEquals(aEntry.getLastModifiedInstant(),
				mixedAEntry.getLastModifiedInstant());
		assertEquals(aEntry.getLastModifiedInstant(),
				mixedAEntry.getLastModifiedInstant());

		DirCacheEntry mixedBEntry = cache.getEntry("b.txt");
		assertNotNull(mixedBEntry);
		assertEquals(bEntry.getLastModifiedInstant(),
				mixedBEntry.getLastModifiedInstant());
		assertEquals(bEntry.getLastModifiedInstant(),
				mixedBEntry.getLastModifiedInstant());
	}

	@Test
	public void testMixedResetWithUnmerged() throws Exception {
		git = new Git(db);

		String file = "a.txt";
		writeTrashFile(file, "data");
		String file2 = "b.txt";
		writeTrashFile(file2, "data");

		git.add().addFilepattern(file).addFilepattern(file2).call();
		git.commit().setMessage("commit").call();

		DirCache index = db.lockDirCache();
		DirCacheBuilder builder = index.builder();
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 1, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 2, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 3, ""));
		assertTrue(builder.commit());

		assertEquals("[a.txt, mode:100644, stage:1]"
				+ "[a.txt, mode:100644, stage:2]"
				+ "[a.txt, mode:100644, stage:3]",
				indexState(0));

		assertSameAsHead(git.reset().setMode(ResetType.MIXED).call());

		assertEquals("[a.txt, mode:100644]" + "[b.txt, mode:100644]",
				indexState(0));
	}

	@Test
	public void testPathsReset() throws Exception {
		setupRepository();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		// 'a.txt' has already been modified in setupRepository
		// 'notAddedToIndex.txt' has been added to repository
		assertSameAsHead(git.reset().addPath(indexFile.getName())
				.addPath(untrackedFile.getName()).call());

		DirCacheEntry postReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(postReset);
		Assert.assertNotSame(preReset.getObjectId(), postReset.getObjectId());
		Assert.assertEquals(prestage.getObjectId(), postReset.getObjectId());

		// check that HEAD hasn't moved
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		assertTrue(inHead(indexFile.getName()));
		assertTrue(inIndex(indexFile.getName()));
		assertFalse(inIndex(untrackedFile.getName()));
	}

	@Test
	public void testPathsResetOnDirs() throws Exception {
		setupRepository();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry("dir/b.txt");
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		// 'dir/b.txt' has already been modified in setupRepository
		assertSameAsHead(git.reset().addPath("dir").call());

		DirCacheEntry postReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry("dir/b.txt");
		assertNotNull(postReset);
		Assert.assertNotSame(preReset.getObjectId(), postReset.getObjectId());

		// check that HEAD hasn't moved
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(inHead("dir/b.txt"));
		assertTrue(inIndex("dir/b.txt"));
	}

	@Test
	public void testPathsResetWithRef() throws Exception {
		setupRepository();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		// 'a.txt' has already been modified in setupRepository
		// 'notAddedToIndex.txt' has been added to repository
		// reset to the inital commit
		assertSameAsHead(git.reset().setRef(initialCommit.getName())
				.addPath(indexFile.getName()).addPath(untrackedFile.getName())
				.call());

		// check that HEAD hasn't moved
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		assertTrue(inHead(indexFile.getName()));
		assertFalse(inIndex(indexFile.getName()));
		assertFalse(inIndex(untrackedFile.getName()));
	}

	@Test
	public void testPathsResetWithUnmerged() throws Exception {
		setupRepository();

		String file = "a.txt";
		writeTrashFile(file, "data");

		git.add().addFilepattern(file).call();
		git.commit().setMessage("commit").call();

		DirCache index = db.lockDirCache();
		DirCacheBuilder builder = index.builder();
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 1, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 2, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 3, ""));
		builder.add(createEntry("b.txt", FileMode.REGULAR_FILE));
		assertTrue(builder.commit());

		assertEquals("[a.txt, mode:100644, stage:1]"
				+ "[a.txt, mode:100644, stage:2]"
				+ "[a.txt, mode:100644, stage:3]"
				+ "[b.txt, mode:100644]",
				indexState(0));

		assertSameAsHead(git.reset().addPath(file).call());

		assertEquals("[a.txt, mode:100644]" + "[b.txt, mode:100644]",
				indexState(0));
	}

	@Test
	public void testPathsResetOnUnbornBranch() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		// Should assume an empty tree, like in C Git 1.8.2
		assertSameAsHead(git.reset().addPath("a.txt").call());

		DirCache cache = db.readDirCache();
		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNull(aEntry);
	}

	@Test(expected = JGitInternalException.class)
	public void testPathsResetToNonexistingRef() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		assertSameAsHead(
				git.reset().setRef("doesnotexist").addPath("a.txt").call());
	}

	@Test
	public void testResetDefaultMode() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		writeTrashFile("a.txt", "modified");
		// should use default mode MIXED
		assertSameAsHead(git.reset().call());

		DirCache cache = db.readDirCache();
		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNull(aEntry);
		assertEquals("modified", read("a.txt"));
	}

	@Test
	public void testHardResetOnTag() throws Exception {
		setupRepository();
		String tagName = "initialtag";
		git.tag().setName(tagName).setObjectId(secondCommit)
				.setMessage("message").call();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		assertSameAsHead(git.reset().setRef(tagName).setMode(HARD).call());

		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
	}

	@Test
	public void testHardResetAfterSquashMerge() throws Exception {
		git = new Git(db);

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

		assertEquals(MergeResult.MergeStatus.FAST_FORWARD_SQUASHED,
				result.getMergeStatus());
		assertNotNull(db.readSquashCommitMsg());

		assertSameAsHead(git.reset().setMode(ResetType.HARD)
				.setRef(first.getName()).call());

		assertNull(db.readSquashCommitMsg());
	}

	@Test
	public void testHardResetOnUnbornBranch() throws Exception {
		git = new Git(db);
		File fileA = writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		// Should assume an empty tree, like in C Git 1.8.2
		assertSameAsHead(git.reset().setMode(ResetType.HARD).call());

		DirCache cache = db.readDirCache();
		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNull(aEntry);
		assertFalse(fileA.exists());
		assertNull(db.resolve(Constants.HEAD));
	}

	@Test
	public void testHardResetFileMode() throws Exception {
		Assume.assumeTrue("Test must be able to set executable bit",
				db.getFS().supportsExecute());
		git = new Git(db);
		File a = writeTrashFile("a.txt", "aaa");
		File b = writeTrashFile("b.txt", "bbb");
		db.getFS().setExecute(b, true);
		assertFalse(db.getFS().canExecute(a));
		assertTrue(db.getFS().canExecute(b));
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		RevCommit commit = git.commit().setMessage("files created").call();
		db.getFS().setExecute(a, true);
		db.getFS().setExecute(b, false);
		assertTrue(db.getFS().canExecute(a));
		assertFalse(db.getFS().canExecute(b));
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		git.commit().setMessage("change exe bits").call();
		Ref ref = git.reset().setRef(commit.getName()).setMode(HARD).call();
		assertSameAsHead(ref);
		assertEquals(commit.getId(), ref.getObjectId());
		assertFalse(db.getFS().canExecute(a));
		assertTrue(db.getFS().canExecute(b));
	}

	@Test
	public void testMergeResetFileMode() throws Exception {
		Assume.assumeTrue("Test must be able to set executable bit",
				db.getFS().supportsExecute());
		git = new Git(db);
		File a = writeTrashFile("a.txt", "aaa");
		File b = writeTrashFile("b.txt", "bbb");
		db.getFS().setExecute(b, true);
		assertFalse(db.getFS().canExecute(a));
		assertTrue(db.getFS().canExecute(b));
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		RevCommit commit = git.commit().setMessage("files created").call();
		db.getFS().setExecute(a, true);
		db.getFS().setExecute(b, false);
		assertTrue(db.getFS().canExecute(a));
		assertFalse(db.getFS().canExecute(b));
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		Ref ref = git.reset().setMode(ResetType.MERGE).call();
		assertEquals(commit.getId(), ref.getObjectId());
		assertFalse(db.getFS().canExecute(a));
		assertTrue(db.getFS().canExecute(b));
	}

	private RevCommit setupMergeReset() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "a1");
		writeTrashFile("b.txt", "b1");
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		RevCommit first = git.commit().setMessage("first").call();
		writeTrashFile("a.txt", "a2");
		git.add().addFilepattern("a.txt").call();
		git.commit().setMessage("second").call();
		return first;
	}

	private void assertMergeResetConflict(String ref, String... conflictingPaths)
			throws Exception {
		ObjectId head = db.resolve(Constants.HEAD);
		String index = indexState(CONTENT);
		try {
			git.reset().setMode(ResetType.MERGE).setRef(ref).call();
			fail("Expected CheckoutConflictException didn't occur");
		} catch (CheckoutConflictException e) {
			assertEquals(Arrays.asList(conflictingPaths), e.getConflictingPaths());
		}
		assertEquals(head, db.resolve(Constants.HEAD));
		assertEquals(index, indexState(CONTENT));
	}

	@Test
	public void testMergeReset() throws Exception {
		RevCommit first = setupMergeReset();
		ObjectId prevHead = db.resolve(Constants.HEAD);

		assertSameAsHead(git.reset().setMode(ResetType.MERGE)
				.setRef(first.getName()).call());

		assertEquals(first, db.resolve(Constants.HEAD));
		assertEquals(prevHead, db.readOrigHead());
		assertReflog(prevHead, first);
		assertEquals("[a.txt, mode:100644, content:a1]"
				+ "[b.txt, mode:100644, content:b1]", indexState(CONTENT));
		assertEquals("a1", read("a.txt"));
		assertEquals("b1", read("b.txt"));
	}

	@Test
	public void testMergeResetKeepsUnstagedChangesOfUnchangedFile()
			throws Exception {
		RevCommit first = setupMergeReset();
		writeTrashFile("b.txt", "b-local");

		git.reset().setMode(ResetType.MERGE).setRef(first.getName()).call();

		assertEquals(first, db.resolve(Constants.HEAD));
		assertEquals("[a.txt, mode:100644, content:a1]"
				+ "[b.txt, mode:100644, content:b1]", indexState(CONTENT));
		assertEquals("a1", read("a.txt"));
		assertEquals("b-local", read("b.txt"));
	}

	@Test
	public void testMergeResetDiscardsStagedChanges() throws Exception {
		RevCommit first = setupMergeReset();
		writeTrashFile("a.txt", "a3");
		writeTrashFile("b.txt", "b3");
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();

		git.reset().setMode(ResetType.MERGE).setRef(first.getName()).call();

		assertEquals("[a.txt, mode:100644, content:a1]"
				+ "[b.txt, mode:100644, content:b1]", indexState(CONTENT));
		assertEquals("a1", read("a.txt"));
		assertEquals("b1", read("b.txt"));
	}

	@Test
	public void testMergeResetConflictUnstagedChangesOfChangedFile()
			throws Exception {
		RevCommit first = setupMergeReset();
		writeTrashFile("a.txt", "a-local");
		writeTrashFile("b.txt", "b-local");

		assertMergeResetConflict(first.getName(), "a.txt");

		assertEquals("a-local", read("a.txt"));
		assertEquals("b-local", read("b.txt"));
	}

	@Test
	public void testMergeResetConflictUnstagedChangesOfStagedFile()
			throws Exception {
		setupMergeReset();
		writeTrashFile("a.txt", "a3");
		git.add().addFilepattern("a.txt").call();
		writeTrashFile("a.txt", "a4");

		assertMergeResetConflict(Constants.HEAD, "a.txt");

		assertEquals("a4", read("a.txt"));
	}

	@Test
	public void testMergeResetConflictUntrackedFile() throws Exception {
		setupMergeReset();
		writeTrashFile("c.txt", "c1");
		git.add().addFilepattern("c.txt").call();
		RevCommit withC = git.commit().setMessage("add c.txt").call();
		git.rm().addFilepattern("c.txt").call();
		git.commit().setMessage("remove c.txt").call();
		writeTrashFile("c.txt", "untracked");

		assertMergeResetConflict(withC.getName(), "c.txt");

		assertEquals("untracked", read("c.txt"));
	}

	@Test
	public void testMergeResetOverwritesIgnoredFile() throws Exception {
		git = new Git(db);
		writeTrashFile("c.txt", "c1");
		git.add().addFilepattern("c.txt").call();
		RevCommit withC = git.commit().setMessage("add c.txt").call();
		git.rm().addFilepattern("c.txt").call();
		writeTrashFile(".gitignore", "c.txt\n");
		git.add().addFilepattern(".gitignore").call();
		git.commit().setMessage("ignore c.txt").call();
		writeTrashFile("c.txt", "ignored");

		git.reset().setMode(ResetType.MERGE).setRef(withC.getName()).call();

		assertEquals("[c.txt, mode:100644, content:c1]", indexState(CONTENT));
		assertEquals("c1", read("c.txt"));
		assertFalse(new File(db.getWorkTree(), ".gitignore").exists());
	}

	@Test
	public void testMergeResetFileToDirectory() throws Exception {
		git = new Git(db);
		writeTrashFile("d/x.txt", "x");
		git.add().addFilepattern("d/x.txt").call();
		RevCommit withDir = git.commit().setMessage("add d/x.txt").call();
		git.rm().addFilepattern("d/x.txt").call();
		writeTrashFile("d", "file");
		git.add().addFilepattern("d").call();
		git.commit().setMessage("replace d/ by d").call();

		git.reset().setMode(ResetType.MERGE).setRef(withDir.getName()).call();

		assertEquals("[d/x.txt, mode:100644, content:x]", indexState(CONTENT));
		assertEquals("x", read("d/x.txt"));
	}

	@Test
	public void testMergeResetDirectoryToFile() throws Exception {
		git = new Git(db);
		writeTrashFile("d", "file");
		git.add().addFilepattern("d").call();
		RevCommit withFile = git.commit().setMessage("add d").call();
		git.rm().addFilepattern("d").call();
		writeTrashFile("d/x.txt", "x");
		git.add().addFilepattern("d/x.txt").call();
		git.commit().setMessage("replace d by d/").call();

		git.reset().setMode(ResetType.MERGE).setRef(withFile.getName())
				.call();

		assertEquals("[d, mode:100644, content:file]", indexState(CONTENT));
		assertTrue(new File(db.getWorkTree(), "d").isFile());
		assertEquals("file", read("d"));
	}

	@Test
	public void testMergeResetConflictUntrackedFileInReplacedDirectory()
			throws Exception {
		git = new Git(db);
		writeTrashFile("d", "file");
		git.add().addFilepattern("d").call();
		RevCommit withFile = git.commit().setMessage("add d").call();
		git.rm().addFilepattern("d").call();
		writeTrashFile("d/x.txt", "x");
		git.add().addFilepattern("d/x.txt").call();
		git.commit().setMessage("replace d by d/").call();
		writeTrashFile("d/y.txt", "untracked");

		assertMergeResetConflict(withFile.getName(), "d/y.txt");

		assertEquals("x", read("d/x.txt"));
		assertEquals("untracked", read("d/y.txt"));
	}

	@Test
	public void testMergeResetAbortsMerge() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "base");
		git.add().addFilepattern("a.txt").call();
		RevCommit base = git.commit().setMessage("base").call();
		createBranch(base, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		writeTrashFile("a.txt", "side");
		git.add().addFilepattern("a.txt").call();
		RevCommit side = git.commit().setMessage("side").call();
		checkoutBranch("refs/heads/master");
		writeTrashFile("a.txt", "master");
		git.add().addFilepattern("a.txt").call();
		RevCommit master = git.commit().setMessage("master").call();
		MergeResult result = git.merge().include(side).call();
		assertEquals(MergeResult.MergeStatus.CONFLICTING,
				result.getMergeStatus());
		assertEquals(RepositoryState.MERGING, db.getRepositoryState());

		git.reset().setMode(ResetType.MERGE).call();

		assertEquals(master, db.resolve(Constants.HEAD));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		assertNull(db.readMergeHeads());
		assertEquals("[a.txt, mode:100644, content:master]",
				indexState(CONTENT));
		assertEquals("master", read("a.txt"));
	}

	@Test
	public void testMergeResetFileToGitlink() throws Exception {
		git = new Git(db);
		File sub = new File(db.getWorkTree(), "sub");
		RevCommit subCommit;
		try (Git subGit = Git.init().setDirectory(sub).call()) {
			write(new File(sub, "s.txt"), "s1");
			subGit.add().addFilepattern("s.txt").call();
			subCommit = subGit.commit().setMessage("s1").call();
		}
		git.add().addFilepattern("sub").call();
		RevCommit gitlinkCommit = git.commit().setMessage("add gitlink").call();

		git.rm().setCached(true).addFilepattern("sub").call();
		FileUtils.delete(sub, FileUtils.RECURSIVE);
		writeTrashFile("sub", "content");
		git.add().addFilepattern("sub").call();
		git.commit().setMessage("replace gitlink with file").call();

		Ref ref = git.reset().setRef(gitlinkCommit.getName())
				.setMode(ResetType.MERGE).call();
		assertSameAsHead(ref);
		assertEquals(gitlinkCommit, ref.getObjectId());
		assertTrue(sub.isDirectory());
		DirCacheEntry entry = db.readDirCache().getEntry("sub");
		assertNotNull(entry);
		assertEquals(FileMode.GITLINK, entry.getFileMode());
		assertEquals(subCommit, entry.getObjectId());
	}

	@Test
	public void testMergeResetGitlinkToFile() throws Exception {
		git = new Git(db);
		File sub = new File(db.getWorkTree(), "sub");
		writeTrashFile("sub", "content");
		git.add().addFilepattern("sub").call();
		RevCommit fileCommit = git.commit().setMessage("add file").call();

		// Replace file with gitlink
		git.rm().setCached(true).addFilepattern("sub").call();
		FileUtils.delete(sub, FileUtils.RECURSIVE);
		try (Git subGit = Git.init().setDirectory(sub).call()) {
			write(new File(sub, "s.txt"), "s1");
			subGit.add().addFilepattern("s.txt").call();
			subGit.commit().setMessage("s1").call();
		}

		assertMergeResetConflict(fileCommit.getName(), "sub");

		assertEquals("s1", read("sub/s.txt"));
	}

	@Test
	public void testMergeResetIgnoresSubmoduleWorkingTree() throws Exception {
		git = new Git(db);
		File subDir = new File(db.getWorkTree(), "sub");
		try (Git sub = Git.init().setDirectory(subDir).call()) {
			write(new File(subDir, "s.txt"), "s1");
			sub.add().addFilepattern("s.txt").call();
			RevCommit s1 = sub.commit().setMessage("s1").call();
			git.add().addFilepattern("sub").call();
			RevCommit first = git.commit().setMessage("sub at s1").call();

			write(new File(subDir, "s.txt"), "s2");
			sub.add().addFilepattern("s.txt").call();
			RevCommit s2 = sub.commit().setMessage("s2").call();
			git.add().addFilepattern("sub").call();
			git.commit().setMessage("sub at s2").call();
			assertEquals(s2, db.readDirCache().getEntry("sub").getObjectId());

			// check out a submodule commit that differs from the index
			write(new File(subDir, "s.txt"), "s3");
			sub.add().addFilepattern("s.txt").call();
			RevCommit s3 = sub.commit().setMessage("s3").call();

			Ref ref = git.reset().setMode(ResetType.MERGE)
					.setRef(first.getName()).call();

			assertSameAsHead(ref);
			assertEquals(first, ref.getObjectId());
			DirCacheEntry entry = db.readDirCache().getEntry("sub");
			assertNotNull(entry);
			assertEquals(FileMode.GITLINK, entry.getFileMode());
			assertEquals(s1, entry.getObjectId());
			assertEquals(s3, sub.getRepository().resolve(Constants.HEAD));
			assertEquals("s3", read(new File(subDir, "s.txt")));
		}
	}

	private void setupConflictingCherryPick() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "base");
		writeTrashFile("b.txt", "b1");
		writeTrashFile("del.txt", "to be deleted");
		git.add().addFilepattern(".").call();
		RevCommit base = git.commit().setMessage("base").call();
		createBranch(base, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("a.txt", "topic");
		writeTrashFile("new.txt", "new in topic");
		writeTrashFile("del.txt", "modified in topic");
		git.add().addFilepattern(".").call();
		RevCommit topic = git.commit().setMessage("topic").call();
		checkoutBranch("refs/heads/master");
		writeTrashFile("a.txt", "master");
		git.add().addFilepattern("a.txt").call();
		git.rm().addFilepattern("del.txt").call();
		git.commit().setMessage("master").call();
		writeTrashFile("b.txt", "unstaged local change");

		CherryPickResult result = git.cherryPick().include(topic).call();
		assertEquals(CherryPickResult.CherryPickStatus.CONFLICTING,
				result.getStatus());
		assertEquals(RepositoryState.CHERRY_PICKING, db.getRepositoryState());
		assertEquals("[a.txt, mode:100644, stage:1, content:base]"
				+ "[a.txt, mode:100644, stage:2, content:master]"
				+ "[a.txt, mode:100644, stage:3, content:topic]"
				+ "[b.txt, mode:100644, content:b1]"
				+ "[del.txt, mode:100644, stage:1, content:to be deleted]"
				+ "[del.txt, mode:100644, stage:3, content:modified in topic]"
				+ "[new.txt, mode:100644, content:new in topic]",
				indexState(CONTENT));
		assertEquals("modified in topic", read("del.txt"));
		assertEquals("new in topic", read("new.txt"));
	}

	@Test
	public void testMergeResetAbortsCherryPick() throws Exception {
		setupConflictingCherryPick();
		ObjectId head = db.resolve(Constants.HEAD);

		git.reset().setMode(ResetType.MERGE).call();

		assertEquals(head, db.resolve(Constants.HEAD));
		assertEquals(head, db.readOrigHead());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		assertNull(db.readCherryPickHead());
		assertEquals(
				"[a.txt, mode:100644, content:master]"
						+ "[b.txt, mode:100644, content:b1]",
				indexState(CONTENT));
		assertEquals("master", read("a.txt"));
		assertEquals("unstaged local change", read("b.txt"));
		// added by the cherry-pick
		assertFalse(new File(db.getWorkTree(), "new.txt").exists());
		// modify/delete conflict, not in HEAD
		assertFalse(new File(db.getWorkTree(), "del.txt").exists());
	}

	@Test
	public void testMergeResetConflictUnstagedChangesOfAddedFile()
			throws Exception {
		setupConflictingCherryPick();
		writeTrashFile("new.txt", "local edit of new.txt");

		ObjectId head = db.resolve(Constants.HEAD);
		String index = indexState(CONTENT);
		try {
			git.reset().setMode(ResetType.MERGE).call();
			fail("Expected CheckoutConflictException didn't occur");
		} catch (CheckoutConflictException e) {
			assertEquals(Arrays.asList("new.txt"), e.getConflictingPaths());
		}
		assertEquals(head, db.resolve(Constants.HEAD));
		assertEquals(index, indexState(CONTENT));

		assertEquals(RepositoryState.CHERRY_PICKING, db.getRepositoryState());
		assertEquals("local edit of new.txt", read("new.txt"));
		assertEquals("modified in topic", read("del.txt"));
	}

	// Commits sub/s.txt, then replaces sub by an embedded repository
	// containing fileInSubmodule. Returns the commit with the directory.
	private RevCommit setupSubmoduleReplacingDirectory(String fileInSubmodule)
			throws Exception {
		git = new Git(db);
		writeTrashFile("sub/s.txt", "from directory");
		git.add().addFilepattern("sub").call();
		RevCommit withDir = git.commit().setMessage("sub as directory").call();
		git.rm().addFilepattern("sub").call();
		File sub = new File(db.getWorkTree(), "sub");
		FileUtils.delete(sub, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
		try (Git subGit = Git.init().setDirectory(sub).call()) {
			write(new File(sub, fileInSubmodule), "from submodule");
			subGit.add().addFilepattern(fileInSubmodule).call();
			subGit.commit().setMessage("submodule content").call();
		}
		git.add().addFilepattern("sub").call();
		git.commit().setMessage("sub as submodule").call();
		assertEquals(FileMode.GITLINK,
				db.readDirCache().getEntry("sub").getFileMode());
		return withDir;
	}

	@Test
	public void testMergeResetConflictSubmoduleReplacedByDirectory()
			throws Exception {
		RevCommit withDir = setupSubmoduleReplacingDirectory("s.txt");
		File sub = new File(db.getWorkTree(), "sub");
		ObjectId head = db.resolve(Constants.HEAD);
		ObjectId gitlink = db.readDirCache().getEntry("sub").getObjectId();

		try {
			git.reset().setMode(ResetType.MERGE).setRef(withDir.getName())
					.call();
			fail("Expected CheckoutConflictException didn't occur");
		} catch (CheckoutConflictException e) {
			assertEquals(Arrays.asList("sub/s.txt"), e.getConflictingPaths());
		}

		assertEquals(head, db.resolve(Constants.HEAD));
		DirCache dc = db.readDirCache();
		assertEquals(1, dc.getEntryCount());
		assertEquals(FileMode.GITLINK, dc.getEntry("sub").getFileMode());
		assertEquals(gitlink, dc.getEntry("sub").getObjectId());
		assertEquals("from submodule", read(new File(sub, "s.txt")));
	}

	@Test
	public void testMergeResetSubmoduleReplacedByDirectory() throws Exception {
		RevCommit withDir = setupSubmoduleReplacingDirectory("other.txt");
		File sub = new File(db.getWorkTree(), "sub");

		git.reset().setMode(ResetType.MERGE).setRef(withDir.getName()).call();

		assertEquals(withDir, db.resolve(Constants.HEAD));
		assertEquals("[sub/s.txt, mode:100644, content:from directory]",
				indexState(CONTENT));
		assertEquals("from directory", read(new File(sub, "s.txt")));
		// the embedded repository is kept, like native git does
		assertEquals("from submodule", read(new File(sub, "other.txt")));
		assertTrue(new File(sub, Constants.DOT_GIT).isDirectory());
	}

	private void assertReflog(ObjectId prevHead, ObjectId head)
			throws IOException {
		// Check the reflog for HEAD
		RefDatabase refDb = db.getRefDatabase();
		String actualHeadMessage = refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getComment();
		String expectedHeadMessage = head.getName() + ": updating HEAD";
		assertEquals(expectedHeadMessage, actualHeadMessage);
		assertEquals(head.getName(), refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getNewId().getName());
		assertEquals(prevHead.getName(), refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getOldId().getName());

		// The reflog for master contains the same as the one for HEAD
		String actualMasterMessage = refDb.getReflogReader("refs/heads/master")
				.getLastEntry().getComment();
		String expectedMasterMessage = head.getName() + ": updating HEAD"; // yes!
		assertEquals(expectedMasterMessage, actualMasterMessage);
		assertEquals(head.getName(), refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getNewId().getName());
		assertEquals(prevHead.getName(),
				refDb.getReflogReader("refs/heads/master").getLastEntry()
						.getOldId().getName());
	}

	private void assertReflogDisabled(ObjectId head)
			throws IOException {
		RefDatabase refDb = db.getRefDatabase();
		// Check the reflog for HEAD
		String actualHeadMessage = refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getComment();
		String expectedHeadMessage = "commit: adding a.txt and dir/b.txt";
		assertEquals(expectedHeadMessage, actualHeadMessage);
		assertEquals(head.getName(), refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getOldId().getName());

		// The reflog for master contains the same as the one for HEAD
		String actualMasterMessage = refDb.getReflogReader("refs/heads/master")
				.getLastEntry().getComment();
		String expectedMasterMessage = "commit: adding a.txt and dir/b.txt";
		assertEquals(expectedMasterMessage, actualMasterMessage);
		assertEquals(head.getName(), refDb.getReflogReader(Constants.HEAD)
				.getLastEntry().getOldId().getName());
	}
	/**
	 * Checks if a file with the given path exists in the HEAD tree
	 *
	 * @param path
	 * @return true if the file exists
	 * @throws IOException
	 */
	private boolean inHead(String path) throws IOException {
		ObjectId headId = db.resolve(Constants.HEAD);
		try (RevWalk rw = new RevWalk(db);
				TreeWalk tw = TreeWalk.forPath(db, path,
						rw.parseTree(headId))) {
			return tw != null;
		}
	}

	/**
	 * Checks if a file with the given path exists in the index
	 *
	 * @param path
	 * @return true if the file exists
	 * @throws IOException
	 */
	private boolean inIndex(String path) throws IOException {
		DirCache dc = DirCache.read(db.getIndexFile(), db.getFS());
		return dc.getEntry(path) != null;
	}

	/**
	 * Asserts that a certain ref is similar to repos HEAD.
	 * @param ref
	 * @throws IOException
	 */
	private void assertSameAsHead(Ref ref) throws IOException {
		Ref headRef = db.exactRef(Constants.HEAD);
		assertEquals(headRef.getName(), ref.getName());
		assertEquals(headRef.getObjectId(), ref.getObjectId());
	}
}
