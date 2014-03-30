/*
 * Copyright (C) 2012, IBM Corporation
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
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileEntry;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class CheckoutTest extends CLIRepositoryTestCase {

	@Test
	public void testCheckoutSelf() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("Already on 'master'", execute("git checkout master"));
	}

	@Test
	public void testCheckoutBranch() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();
		new Git(db).branchCreate().setName("side").call();

		assertEquals("Switched to branch 'side'", execute("git checkout side"));
	}

	@Test
	public void testCheckoutNewBranch() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("Switched to a new branch 'side'",
				execute("git checkout -b side"));
	}

	@Test
	public void testCheckoutNonExistingBranch() throws Exception {
		assertEquals(
				"error: pathspec 'side' did not match any file(s) known to git.",
				execute("git checkout side"));
	}

	@Test
	public void testCheckoutNewBranchThatAlreadyExists() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("fatal: A branch named 'master' already exists.",
				execute("git checkout -b master"));
	}

	@Test
	public void testCheckoutNewBranchOnBranchToBeBorn() throws Exception {
		assertEquals("fatal: You are on a branch yet to be born",
				execute("git checkout -b side"));
	}

	@Test
	public void testCheckoutUnresolvedHead() throws Exception {
		assertEquals(
				"error: pathspec 'HEAD' did not match any file(s) known to git.",
				execute("git checkout HEAD"));
	}

	@Test
	public void testCheckoutHead() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("", execute("git checkout HEAD"));
	}

	@Test
	public void testCheckoutExistingBranchWithConflict() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("commit file a").call();
		git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/b", "Hello world b");
		git.add().addFilepattern("a/b").call();
		git.commit().setMessage("commit folder a").call();
		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "New Hello world a");
		git.add().addFilepattern(".").call();

		String[] execute = execute("git checkout branch_1");
		Assert.assertEquals(
				"error: Your local changes to the following files would be overwritten by checkout:",
				execute[0]);
		Assert.assertEquals("\ta", execute[1]);
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add file 'a' and 'b'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>modify file 'a'
	 * <li>Commit
	 * <li>Delete file 'a' in the working tree
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The working tree should contain 'a' with FileMode.REGULAR_FILE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void testCheckoutWithMissingWorkingTreeFile() throws Exception {
		Git git = new Git(db);
		File fileA = writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		writeTrashFile("a", "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("modify file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		FileUtils.delete(fileA);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());
		assertEquals("Hello world a", read(fileA));
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add file 'b'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Add folder 'a'
	 * <li>Commit
	 * <li>Replace folder 'a' by file 'a' in the working tree
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The working tree should contain 'a' with FileMode.REGULAR_FILE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenFolderWithFileInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		File folderA = new File(db.getWorkTree(), "a");
		FileUtils.mkdirs(folderA);
		writeTrashFile("a/c", "Hello world c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		FileUtils.delete(folderA, FileUtils.RECURSIVE);
		writeTrashFile("a", "b");

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add file 'a'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Replace file 'a' by folder 'a'
	 * <li>Commit
	 * <li>Delete folder 'a' in the working tree
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The working tree should contain 'a' with FileMode.REGULAR_FILE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderWithMissingInWorkingTree() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		writeTrashFile("a", "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & file a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File folderA = new File(db.getWorkTree(), "a");
		FileUtils.mkdirs(folderA);
		writeTrashFile("a/c", "Hello world c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		FileUtils.delete(folderA, FileUtils.RECURSIVE);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add file 'a'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Delete file 'a'
	 * <li>Commit
	 * <li>Add folder 'a' in the working tree
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingWithFolderInWorkingTree() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		writeTrashFile("a", "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & file a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		git.commit().setMessage("delete file a").call();

		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		CheckoutConflictException exception = null;
		try {
			git.checkout().setName(branch_1.getName()).call();
		} catch (CheckoutConflictException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertEquals(2, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));
		assertEquals("a/c", exception.getConflictingPaths().get(1));
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add folder 'a'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Delete folder 'a'
	 * <li>Commit
	 * <li>Add file 'a' in the working tree
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenMissingWithFileInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("add folder a & file b")
				.call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		RevCommit commit2 = git.commit().setMessage("delete folder a").call();

		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit1.getTree());
		tw.addTree(commit2.getTree());
		List<DiffEntry> scan = DiffEntry.scan(tw);
		assertEquals(1, scan.size());
		assertEquals(FileMode.MISSING, scan.get(0).getNewMode());
		assertEquals(FileMode.TREE, scan.get(0).getOldMode());

		writeTrashFile("a", "b");

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		CheckoutConflictException exception = null;
		try {
			git.checkout().setName(branch_1.getName()).call();
		} catch (CheckoutConflictException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertEquals(1, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add folder 'a'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Replace folder 'a'by file 'a'
	 * <li>Commit
	 * <li>Delete file 'a' in the working tree
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The working tree should contain 'a' with FileMode.TREE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenFileWithMissingInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a & file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File fileA = new File(db.getWorkTree(), "a");
		writeTrashFile("a", "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		FileUtils.delete(fileA);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add file 'a'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Replace file 'a' by file 'a'
	 * <li>Commit
	 * <li>Delete file 'a' & replace by folder 'a' in the working tree & index
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenFileWithFolderInIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.rm().addFilepattern("a").call();
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		git.add().addFilepattern(".").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		CheckoutConflictException exception = null;
		try {
			git.checkout().setName(branch_1.getName()).call();
		} catch (CheckoutConflictException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertEquals(1, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));
	}

	/**
	 * Steps:
	 * <ol>
	 * <li>Add file 'a'
	 * <li>Commit
	 * <li>Create branch '1'
	 * <li>Replace file 'a' by file 'a'
	 * <li>Commit
	 * <li>Delete file 'a' & replace by folder 'a' in the working tree & index
	 * <li>Checkout branch '1'
	 * </ol>
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileWithFolderInIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		writeTrashFile("a", "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & file a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.rm().addFilepattern("a").call();
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		git.add().addFilepattern(".").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		CheckoutConflictException exception = null;
		try {
			git.checkout().setName(branch_1.getName()).call();
		} catch (CheckoutConflictException e) {
			exception = e;
		}
		assertNotNull(exception);
		assertEquals(1, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));

		// TODO: ideally we'd like to get two paths from this exception
		// assertEquals(2, exception.getConflictingPaths().size());
		// assertEquals("a", exception.getConflictingPaths().get(0));
		// assertEquals("a/c", exception.getConflictingPaths().get(1));
	}

	static private void assertEquals(Object expected, Object actual) {
		Assert.assertEquals(expected, actual);
	}

	static private void assertEquals(String expected, String[] actual) {
		// if there is more than one line, ignore last one if empty
		Assert.assertEquals(
				1,
				actual.length > 1 && actual[actual.length - 1].equals("") ? actual.length - 1
						: actual.length);
		Assert.assertEquals(expected, actual[0]);
	}
}
