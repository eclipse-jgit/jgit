/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010, Philipp Thun <philipp.thun@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	void testRecursiveTreeWalk() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderName();

		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertTrue(treeWalk.next());
			assertEquals("folder", treeWalk.getPathString());
			assertTrue(treeWalk.next());
			assertEquals("folder/file", treeWalk.getPathString());
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testNonRecursiveTreeWalk() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderName();

		try (TreeWalk treeWalk = createNonRecursiveTreeWalk(commit)) {
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
	}

	@Test
	void testFileCommitted() throws Exception {
		RevCommit commit = writeFileAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testConflicts() throws Exception {
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

		try (TreeWalk treeWalk = createTreeWalk(side)) {
			int count = 0;
			while (treeWalk.next())
				count++;
			assertEquals(2, count);
		}
	}

	@Test
	void testFileInFolderCommitted() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testEmptyFolderCommitted() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileCommittedChangedNotModified() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFile();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileInFolderCommittedChangedNotModified() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolder();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileCommittedModified() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileModified();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE);
		}
	}

	@Test
	void testFileInFolderCommittedModified() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModified();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileCommittedDeleted() throws Exception {
		RevCommit commit = writeFileAndCommit();
		deleteFile();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE);
		}
	}

	@Test
	void testFileInFolderCommittedDeleted() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteFileInFolder();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileInFolderCommittedAllDeleted() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testEmptyFolderCommittedDeleted() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		deleteFolder();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileCommittedModifiedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileModifiedAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE);
		}
	}

	@Test
	void testFileInFolderCommittedModifiedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModifiedAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileCommittedDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileAndCommit();
		deleteFileAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE);
		}
	}

	@Test
	void testFileInFolderCommittedDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteFileInFolderAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileInFolderCommittedAllDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAllAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testEmptyFolderCommittedDeletedCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		deleteFolderAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileUntracked() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileUntracked();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, UNTRACKED_FILE);
		}
	}

	@Test
	void testFileInFolderUntracked() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderUntracked();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, UNTRACKED_FILE_IN_FOLDER);
		}
	}

	@Test
	void testEmptyFolderUntracked() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		createEmptyFolderUntracked();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileIgnored() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileInFolderIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileInFolderAllIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderAllIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testEmptyFolderIgnored() throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		createEmptyFolderIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileIgnoredNotHonored() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileIgnored();
		try (TreeWalk treeWalk = createTreeWalkDishonorIgnores(commit)) {
			assertPaths(treeWalk, IGNORED_FILE, GITIGNORE);
		}
	}

	@Test
	void testFileCommittedModifiedIgnored() throws Exception {
		RevCommit commit = writeFileAndCommit();
		writeFileModifiedIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE);
		}
	}

	@Test
	void testFileInFolderCommittedModifiedIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModifiedIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileInFolderCommittedModifiedAllIgnored() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		writeFileInFolderModifiedAllIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileCommittedDeletedCommittedIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileAndCommit();
		deleteFileAndCommit();
		rewriteFileIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE);
		}
	}

	@Test
	void testFileInFolderCommittedDeletedCommittedIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteFileInFolderAndCommit();
		rewriteFileInFolderIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFileInFolderCommittedAllDeletedCommittedAllIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAllAndCommit();
		rewriteFileInFolderAllIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FILE_IN_FOLDER);
		}
	}

	@Test
	void testEmptyFolderCommittedDeletedCommittedIgnoredComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = createEmptyFolderAndCommit();
		deleteFolderAndCommit();
		recreateEmptyFolderIgnored();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertFalse(treeWalk.next());
		}
	}

	@Test
	void testFileInFolderCommittedNonRecursive() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		try (TreeWalk treeWalk = createNonRecursiveTreeWalk(commit)) {
			assertPaths(treeWalk, FOLDER);
		}
	}

	@Test
	void testFolderChangedToFile() throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderName();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FOLDER, FILE_IN_FOLDER);
		}
	}

	@Test
	void testFolderChangedToFileCommittedComparedWithInitialCommit()
			throws Exception {
		RevCommit commit = writeFileInFolderAndCommit();
		deleteAll();
		writeFileWithFolderNameAndCommit();
		try (TreeWalk treeWalk = createTreeWalk(commit)) {
			assertPaths(treeWalk, FOLDER, FILE_IN_FOLDER);
		}
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
