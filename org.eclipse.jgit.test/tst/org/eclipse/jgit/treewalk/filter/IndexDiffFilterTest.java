/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010, Philipp Thun <philipp.thun@sap.com>
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
package org.eclipse.jgit.treewalk.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class IndexDiffFilterTest extends RepositoryTestCase {
	private static final String FILE = "file";

	private static final String UNTRACKED_FILE = "untracked_file";

	private static final String IGNORED_FILE = "ignored_file";

	private static final String FILE_IN_FOLDER = "folder/file";

	private static final String UNTRACKED_FILE_IN_FOLDER = "folder/untracked_file";

	private static final String IGNORED_FILE_IN_FOLDER = "folder/ignored_file";

	private static final String FILE_IN_IGNORED_FOLDER = "ignored_folder/file";

	private static final String FOLDER = "folder";

	private static final String UNTRACKED_FOLDER = "untracked_folder";

	private static final String IGNORED_FOLDER = "ignored_folder";

	private static final String GITIGNORE = ".gitignore";

	private static final String FILE_CONTENT = "content";

	private static final String MODIFIED_FILE_CONTENT = "modified_content";

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void testRecursiveTreeWalk() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderName();
		TreeWalk treeWalk = createTreeWalk(commit);

		assertTrue(treeWalk.next());
		assertEquals("folder", treeWalk.getPathString());
		assertTrue(treeWalk.next());
		assertEquals("folder/file", treeWalk.getPathString());
		assertFalse(treeWalk.next());
	}

	@Test
	public void testNonRecursiveTreeWalk() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderName();
		TreeWalk treeWalk = createNonRecursiveTreeWalk(commit);

		assertTrue(treeWalk.next());
		assertEquals("folder", treeWalk.getPathString());
		assertTrue(treeWalk.next());
		assertEquals("folder", treeWalk.getPathString());
		assertTrue(treeWalk.isSubtree());
		treeWalk.enterSubtree();
		assertTrue(treeWalk.next());
		assertEquals("folder/file", treeWalk.getPathString());
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileCommitted() throws Exception {
		RevCommit commit = writeFileAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testConflicts() throws Exception {
		RevCommit initial = git.commit().setMessage("initial").call();
		writeTrashFile(FILE, "master");
		git.add().addFilepattern(FILE).call();
		RevCommit master = git.commit().setMessage("master").call();
		git.checkout().setName("refs/heads/side")
				.setCreateBranch(true).setStartPoint(initial).call();
		writeTrashFile(FILE, "side");
		git.add().addFilepattern(FILE).call();
		RevCommit side = git.commit().setMessage("side").call();
		assertFalse(git.merge().include("master", master).call()
				.getMergeStatus()
				.isSuccessful());
		assertEquals(read(FILE),
				"<<<<<<< HEAD\nside\n=======\nmaster\n>>>>>>> master\n");
		writeTrashFile(FILE, "master");

		TreeWalk treeWalk = createTreeWalk(side);
		int count = 0;
		while (treeWalk.next())
			count++;
		assertEquals(2, count);
	}

	@Test
	public void testFileInFolderCommitted() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testEmptyFolderCommitted() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileCommittedChangedNotModified() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFile();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileInFolderCommittedChangedNotModified() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolder();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileCommittedModified() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileModified();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE);
	}

	@Test
	public void testFileInFolderCommittedModified() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModified();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileCommittedDeleted() throws Exception {
		RevCommit commit = writeFileAndCommit();
		deleteFile();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE);
	}

	@Test
	public void testFileInFolderCommittedDeleted() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteFileInFolder();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileInFolderCommittedAllDeleted() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testEmptyFolderCommittedDeleted() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		deleteFolder();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileCommittedModifiedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileModifiedAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE);
	}

	@Test
	public void testFileInFolderCommittedModifiedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModifiedAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileCommittedDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileAndCommit();
		deleteFileAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE);
	}

	@Test
	public void testFileInFolderCommittedDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteFileInFolderAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileInFolderCommittedAllDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAllAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testEmptyFolderCommittedDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		deleteFolderAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileUntracked() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileUntracked();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, UNTRACKED_FILE);
	}

	@Test
	public void testFileInFolderUntracked() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderUntracked();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, UNTRACKED_FILE_IN_FOLDER);
	}

	@Test
	public void testEmptyFolderUntracked() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		createEmptyFolderUntracked();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileIgnored() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileInFolderIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileInFolderAllIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderAllIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testEmptyFolderIgnored() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		createEmptyFolderIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileIgnoredNotHonored() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileIgnored();
		TreeWalk treeWalk = createTreeWalkDishonorIgnores(commit);
		assertPaths(treeWalk, IGNORED_FILE, GITIGNORE);
	}

	@Test
	public void testFileCommittedModifiedIgnored() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileModifiedIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE);
	}

	@Test
	public void testFileInFolderCommittedModifiedIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModifiedIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileInFolderCommittedModifiedAllIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModifiedAllIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileCommittedDeletedCommittedIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileAndCommit();
		deleteFileAndCommit();
		rewriteFileIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE);
	}

	@Test
	public void testFileInFolderCommittedDeletedCommittedIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteFileInFolderAndCommit();
		rewriteFileInFolderIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testFileInFolderCommittedAllDeletedCommittedAllIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAllAndCommit();
		rewriteFileInFolderAllIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FILE_IN_FOLDER);
	}

	@Test
	public void testEmptyFolderCommittedDeletedCommittedIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		deleteFolderAndCommit();
		recreateEmptyFolderIgnored();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertFalse(treeWalk.next());
	}

	@Test
	public void testFileInFolderCommittedNonRecursive() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		TreeWalk treeWalk = createNonRecursiveTreeWalk(commit);
		assertPaths(treeWalk, FOLDER);
	}

	@Test
	public void testFolderChangedToFile() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderName();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FOLDER, FILE_IN_FOLDER);
	}

	@Test
	public void testFolderChangedToFileCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderNameAndCommit();
		TreeWalk treeWalk = createTreeWalk(commit);
		assertPaths(treeWalk, FOLDER, FILE_IN_FOLDER);
	}

	private void writeFile() throws Exception {
		writeTrashFile(FILE, FILE_CONTENT);
	}

	private RevCommit writeFileAndCommit() throws Exception {
		writeFile();
		return commitAdd();
	}

	private void writeFileModified() throws Exception {
		writeTrashFile(FILE, MODIFIED_FILE_CONTENT);
	}

	private void writeFileModifiedAndCommit() throws Exception {
		writeFileModified();
		commitAdd();
	}

	private void writeFileUntracked() throws Exception {
		writeTrashFile(UNTRACKED_FILE, FILE_CONTENT);
	}

	private void writeFileIgnored() throws Exception {
		writeTrashFile(IGNORED_FILE, FILE_CONTENT);
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + IGNORED_FILE);
	}

	private void writeFileModifiedIgnored() throws Exception {
		writeFileModified();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FILE);
	}

	private void rewriteFileIgnored() throws Exception {
		writeFile();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FILE);
	}

	private void writeFileWithFolderName() throws Exception {
		writeTrashFile(FOLDER, FILE_CONTENT);
	}

	private void writeFileWithFolderNameAndCommit() throws Exception {
		writeFileWithFolderName();
		commitAdd();
	}

	private void deleteFile() throws Exception {
		deleteTrashFile(FILE);
	}

	private void deleteFileAndCommit() throws Exception {
		deleteFile();
		commitRm(FILE);
	}

	private void writeFileInFolder() throws Exception {
		writeTrashFile(FILE_IN_FOLDER, FILE_CONTENT);
	}

	private RevCommit writeFileInFolderAndCommit() throws Exception {
		writeFileInFolder();
		return commitAdd();
	}

	private void writeFileInFolderModified() throws Exception {
		writeTrashFile(FILE_IN_FOLDER, MODIFIED_FILE_CONTENT);
	}

	private void writeFileInFolderModifiedAndCommit() throws Exception {
		writeFileInFolderModified();
		commitAdd();
	}

	private void writeFileInFolderUntracked() throws Exception {
		writeTrashFile(UNTRACKED_FILE_IN_FOLDER, FILE_CONTENT);
	}

	private void writeFileInFolderIgnored() throws Exception {
		writeTrashFile(IGNORED_FILE_IN_FOLDER, FILE_CONTENT);
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + IGNORED_FILE_IN_FOLDER);
	}

	private void writeFileInFolderAllIgnored() throws Exception {
		writeTrashFile(FILE_IN_IGNORED_FOLDER, FILE_CONTENT);
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + IGNORED_FOLDER + "/");
	}

	private void writeFileInFolderModifiedIgnored() throws Exception {
		writeFileInFolderModified();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FILE_IN_FOLDER);
	}

	private void rewriteFileInFolderIgnored() throws Exception {
		writeFileInFolder();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FILE_IN_FOLDER);
	}

	private void writeFileInFolderModifiedAllIgnored() throws Exception {
		writeFileInFolderModified();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FOLDER + "/");
	}

	private void rewriteFileInFolderAllIgnored() throws Exception {
		writeFileInFolder();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FOLDER + "/");
	}

	private void deleteFileInFolder() throws Exception {
		deleteTrashFile(FILE_IN_FOLDER);
	}

	private void deleteFileInFolderAndCommit() throws Exception {
		deleteFileInFolder();
		commitRm(FILE_IN_FOLDER);
	}

	private void createEmptyFolder() throws Exception {
		File path = new File(db.getWorkTree(), FOLDER);
		FileUtils.mkdir(path);
	}

	private RevCommit createEmptyFolderAndCommit() throws Exception {
		createEmptyFolder();
		return commitAdd();
	}

	private void createEmptyFolderUntracked() throws Exception {
		File path = new File(db.getWorkTree(), UNTRACKED_FOLDER);
		FileUtils.mkdir(path);
	}

	private void createEmptyFolderIgnored() throws Exception {
		File path = new File(db.getWorkTree(), IGNORED_FOLDER);
		FileUtils.mkdir(path);
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + IGNORED_FOLDER + "/");
	}

	private void recreateEmptyFolderIgnored() throws Exception {
		createEmptyFolder();
		writeTrashFile(GITIGNORE, GITIGNORE + "\n" + FOLDER + "/");
	}

	private void deleteFolder() throws Exception {
		deleteTrashFile(FOLDER);
	}

	private void deleteFolderAndCommit() throws Exception {
		deleteFolder();
		commitRm(FOLDER);
	}

	private void deleteAll() throws Exception {
		deleteFileInFolder();
		deleteFolder();
	}

	private void deleteAllAndCommit() throws Exception {
		deleteFileInFolderAndCommit();
		deleteFolderAndCommit();
	}

	private RevCommit commitAdd() throws Exception {
		git.add().addFilepattern(".").call();
		return git.commit().setMessage("commit").call();
	}

	private RevCommit commitRm(String path) throws Exception {
		git.rm().addFilepattern(path).call();
		return git.commit().setMessage("commit").call();
	}

	private TreeWalk createTreeWalk(RevCommit commit) throws Exception {
		return createTreeWalk(commit, true, true);
	}

	private TreeWalk createTreeWalkDishonorIgnores(RevCommit commit)
			throws Exception {
		return createTreeWalk(commit, true, false);
	}

	private TreeWalk createNonRecursiveTreeWalk(RevCommit commit)
			throws Exception {
		return createTreeWalk(commit, false, true);
	}

	private TreeWalk createTreeWalk(RevCommit commit, boolean isRecursive,
			boolean honorIgnores) throws Exception {
		TreeWalk treeWalk = new TreeWalk(db);
		treeWalk.setRecursive(isRecursive);
		treeWalk.addTree(commit.getTree());
		treeWalk.addTree(new DirCacheIterator(db.readDirCache()));
		treeWalk.addTree(new FileTreeIterator(db));
		if (!honorIgnores)
			treeWalk.setFilter(new IndexDiffFilter(1, 2, honorIgnores));
		else
			treeWalk.setFilter(new IndexDiffFilter(1, 2));
		return treeWalk;
	}

	private static void assertPaths(TreeWalk treeWalk, String... paths)
			throws Exception {
		for (int i = 0; i < paths.length; i++) {
			assertTrue(treeWalk.next());
			assertPath(treeWalk.getPathString(), paths);
		}
		assertFalse(treeWalk.next());
	}

	private static void assertPath(String path, String... paths) {
		for (String p : paths)
			if (p.equals(path))
				return;
		fail("Expected path '" + path + "' is not returned");
	}
}
