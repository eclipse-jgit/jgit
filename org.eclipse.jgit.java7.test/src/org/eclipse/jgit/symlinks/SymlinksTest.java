/*
 * Copyright (C) 2013-2014 Axel Richard <axel.richard@obeo.fr>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.symlinks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileEntry;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class SymlinksTest extends RepositoryTestCase {

	@Before
	public void beforeMethod() {
		// If this assumption fails the tests are skipped. When running on a
		// filesystem not supporting symlinks I don't want this tests
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
	}

	/**
	 * Steps: 1.Add file 'a' 2.Commit 3.Create branch '1' 4.Replace file 'a' by
	 * symlink 'a' 5.Commit 6.Checkout branch '1'
	 *
	 * The working tree should contain 'a' with FileMode.REGULAR_FILE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenSymlink() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());
	}

	/**
	 * Steps: 1.Add symlink 'a' 2.Commit 3.Create branch '1' 4.Replace symlink
	 * 'a' by file 'a' 5.Commit 6.Checkout branch '1'
	 *
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFile() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps: 1.Add folder 'a' 2.Commit 3.Create branch '1' 4.Replace folder 'a'
	 * by symlink 'a' 5.Commit 6.Checkout branch '1'
	 *
	 * The working tree should contain 'a' with FileMode.TREE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenSymlink() throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/b", "Hello world b");
		writeTrashFile("c", "Hello world c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "c");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());
	}

	/**
	 * Steps: 1.Add symlink 'a' 2.Commit 3.Create branch '1' 4.Replace symlink
	 * 'a' by folder 'a' 5.Commit 6.Checkout branch '1'
	 *
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFolder() throws Exception {
		Git git = new Git(db);
		writeTrashFile("c", "Hello world c");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/b", "Hello world b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add folder a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps: 1.Add file 'b' 2.Commit 3.Create branch '1' 4.Add symlink 'a'
	 * 5.Commit 6.Checkout branch '1'
	 *
	 * The working tree should not contain 'a' -> FileMode.MISSING after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenSymlink() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();
		RevCommit commit2 = git.commit().setMessage("add symlink a").call();

		git.checkout().setName(branch_1.getName()).call();

		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit1.getTree());
		tw.addTree(commit2.getTree());
		List<DiffEntry> scan = DiffEntry.scan(tw);
		assertEquals(1, scan.size());
		assertEquals(FileMode.SYMLINK, scan.get(0).getNewMode());
		assertEquals(FileMode.MISSING, scan.get(0).getOldMode());
	}

	/**
	 * Steps: 1.Add symlink 'a' 2.Commit 3.Create branch '1' 4.Delete symlink
	 * 'a' 5.Commit 6.Checkout branch '1'
	 *
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenMissing() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("add file b & symlink a")
				.call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		RevCommit commit2 = git.commit().setMessage("delete symlink a").call();

		git.checkout().setName(branch_1.getName()).call();

		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit1.getTree());
		tw.addTree(commit2.getTree());
		List<DiffEntry> scan = DiffEntry.scan(tw);
		assertEquals(1, scan.size());
		assertEquals(FileMode.MISSING, scan.get(0).getNewMode());
		assertEquals(FileMode.SYMLINK, scan.get(0).getOldMode());
	}

	@Test
	public void createSymlinkAfterTarget() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "start");
		git.add().addFilepattern("a").call();
		RevCommit base = git.commit().setMessage("init").call();
		writeTrashFile("target", "someData");
		FileUtils.createSymLink(new File(db.getWorkTree(), "link"), "target");
		git.add().addFilepattern("target").addFilepattern("link").call();
		git.commit().setMessage("add target").call();
		assertEquals(4, db.getWorkTree().list().length); // self-check
		git.checkout().setName(base.name()).call();
		assertEquals(2, db.getWorkTree().list().length); // self-check
		git.checkout().setName("master").call();
		assertEquals(4, db.getWorkTree().list().length);
		String data = read(new File(db.getWorkTree(), "target"));
		assertEquals(8, new File(db.getWorkTree(), "target").length());
		assertEquals("someData", data);
		data = read(new File(db.getWorkTree(), "link"));
		assertEquals("target",
				FileUtils.readSymLink(new File(db.getWorkTree(), "link")));
		assertEquals("someData", data);
	}

	@Test
	public void createFileSymlinkBeforeTarget() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "start");
		git.add().addFilepattern("a").call();
		RevCommit base = git.commit().setMessage("init").call();
		writeTrashFile("target", "someData");
		FileUtils.createSymLink(new File(db.getWorkTree(), "tlink"), "target");
		git.add().addFilepattern("target").addFilepattern("tlink").call();
		git.commit().setMessage("add target").call();
		assertEquals(4, db.getWorkTree().list().length); // self-check
		git.checkout().setName(base.name()).call();
		assertEquals(2, db.getWorkTree().list().length); // self-check
		git.checkout().setName("master").call();
		assertEquals(4, db.getWorkTree().list().length);
		String data = read(new File(db.getWorkTree(), "target"));
		assertEquals(8, new File(db.getWorkTree(), "target").length());
		assertEquals("someData", data);
		data = read(new File(db.getWorkTree(), "tlink"));
		assertEquals("target",
				FileUtils.readSymLink(new File(db.getWorkTree(), "tlink")));
		assertEquals("someData", data);
	}

	@Test
	public void createDirSymlinkBeforeTarget() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "start");
		git.add().addFilepattern("a").call();
		RevCommit base = git.commit().setMessage("init").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "link"), "target");
		FileUtils.mkdir(new File(db.getWorkTree(), "target"));
		writeTrashFile("target/file", "someData");
		git.add().addFilepattern("target").addFilepattern("link").call();
		git.commit().setMessage("add target").call();
		assertEquals(4, db.getWorkTree().list().length); // self-check
		git.checkout().setName(base.name()).call();
		assertEquals(2, db.getWorkTree().list().length); // self-check
		git.checkout().setName("master").call();
		assertEquals(4, db.getWorkTree().list().length);
		String data = read(new File(db.getWorkTree(), "target/file"));
		assertEquals(8, new File(db.getWorkTree(), "target/file").length());
		assertEquals("someData", data);
		data = read(new File(db.getWorkTree(), "link/file"));
		assertEquals("target",
				FileUtils.readSymLink(new File(db.getWorkTree(), "link")));
		assertEquals("someData", data);
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace file 'a' by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by folder 'a' in the working tree
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenSymlinkWithFolderInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		FileUtils.delete(symlinkA);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");

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
		assertEquals(2, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));
		assertEquals("a/c", exception.getConflictingPaths().get(1));
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace folder 'a' by file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' & replace by symlink 'a' in the working tree
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenFileWithSymlinkInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a & file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File fileA = writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		FileUtils.delete(fileA);
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete folder 'a' & replace by file 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 *
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFolderWithFileInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add symlink a & file b").call();
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
		writeTrashFile("a", "Hello world a");

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace file 'a' by folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete folder 'a' & replace by symlink 'a' in the working tree
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenFolderWithSymlinkInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
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
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");

		entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace folder 'a' by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by file 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenSymlinkWithFileInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/b", "Hello world b");
		writeTrashFile("c", "Hello world c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "c");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		FileUtils.delete(symlinkA);
		writeTrashFile("a", "Hello world a");

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' & replace by folder 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFileWithFolderInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File fileA = writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		FileUtils.delete(fileA);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");

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
		assertEquals(2, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));
		assertEquals("a/c", exception.getConflictingPaths().get(1));
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Replace file 'a' by symlink 'a' in the working tree
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenFileWithSymlinkInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		File fileA = writeTrashFile("a", "Hello world a");
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		FileUtils.delete(fileA);
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");

		entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add file 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenMissingWithFileInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		git.commit().setMessage("delete symlink a").call();

		writeTrashFile("a", "Hello world a");

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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace file 'a' by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.REGULAR_FILE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenSymlinkWithMissingInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		FileUtils.delete(symlinkA);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by file 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 *
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenSymlinkWithFileInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileUtils.delete(symlinkA);
		writeTrashFile("a", "Hello world a");

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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add symlink 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenMissingWithSymlinkInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		RevCommit commit2 = git.commit().setMessage("delete file a").call();

		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit1.getTree());
		tw.addTree(commit2.getTree());
		List<DiffEntry> scan = DiffEntry.scan(tw);
		assertEquals(1, scan.size());
		assertEquals(FileMode.MISSING, scan.get(0).getNewMode());
		assertEquals(FileMode.REGULAR_FILE, scan.get(0).getOldMode());

		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' & in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFileWithMissingInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File fileA = writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		FileUtils.delete(fileA);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Replace folder 'a' by symlink 'a' in the working tree
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenFolderWithSymlinkInWorkingTree()
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
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by folder 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 *
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenSymlinkWithFolderInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileUtils.delete(symlinkA);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"),
				db.getFS());
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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete folder 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFolderWithMissingInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
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
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add folder 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenMissingWithFolderInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a")
				.call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		git.commit().setMessage("delete symlink a").call();

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
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add symlink 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenMissingWithSymlinkInWorkingTree()
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

		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace folder 'a'by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.TREE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenSymlinkWithMissingInWorkingTree()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a & file b")
				.call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		FileUtils.delete(symlinkA);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace file 'a' by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by folder 'a' in the working tree & index
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenSymlinkWithFolderInIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace folder 'a' by file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' & replace by symlink 'a' in the working tree & index.
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenFileWithSymlinkInIndex() throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a & file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.rm().addFilepattern("a").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete folder 'a' & replace by file 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 *
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFolderWithFileInIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("c", "Hello world c");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File folderA = new File(db.getWorkTree(), "a");
		FileUtils.mkdirs(folderA);
		writeTrashFile("a/b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace file 'a' by folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete folder 'a' & replace by symlink 'a' in the working tree & index
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenFolderWithSymlinkInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
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

		git.rm().addFilepattern("a").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace folder 'a' by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by file 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenSymlinkWithFileInIndex()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/b", "Hello world b");
		writeTrashFile("c", "Hello world c");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "c");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' & replace by folder 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting paths are 'a'
	 * and 'a/c'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFileWithFolderInIndex() throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
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
		assertEquals(2, exception.getConflictingPaths().size());
		assertEquals("a", exception.getConflictingPaths().get(0));
		assertEquals("a/c", exception.getConflictingPaths().get(1));
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Replace file 'a' by symlink 'a' in the working tree & index
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenFileWithSymlinkInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		writeTrashFile("a", "Hello world a");
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.rm().addFilepattern("a").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add file 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenMissingWithFileInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		git.commit().setMessage("delete symlink a").call();

		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();

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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace file 'a' by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenSymlinkWithMissingInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.rm().addFilepattern("a").call();

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
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by file 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 *
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenSymlinkWithFileInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();

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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add symlink 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileThenMissingWithSymlinkInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("add files a & b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		RevCommit commit2 = git.commit().setMessage("delete file a").call();

		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit1.getTree());
		tw.addTree(commit2.getTree());
		List<DiffEntry> scan = DiffEntry.scan(tw);
		assertEquals(1, scan.size());
		assertEquals(FileMode.MISSING, scan.get(0).getNewMode());
		assertEquals(FileMode.REGULAR_FILE, scan.get(0).getOldMode());

		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' & in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFileWithMissingInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		writeTrashFile("a", "Hello world a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add file a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());

		git.rm().addFilepattern("a").call();

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
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Replace folder 'a' by symlink 'a' in the working tree & index
	 * <p>
	 * 6.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenFolderWithSymlinkInIndex()
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

		git.rm().addFilepattern("a").call();
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add file 'b'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Add symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' & replace by folder 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 *
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestMissingThenSymlinkWithFolderInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("add file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		RevCommit commit2 = git.commit().setMessage("add symlink a").call();

		git.rm().addFilepattern("a").call();
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		git.add().addFilepattern(".").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit1.getTree());
		tw.addTree(commit2.getTree());
		List<DiffEntry> scan = DiffEntry.scan(tw);
		assertEquals(1, scan.size());
		assertEquals(FileMode.SYMLINK, scan.get(0).getNewMode());
		assertEquals(FileMode.MISSING, scan.get(0).getOldMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace symlink 'a' by folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete folder 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenFolderWithMissingInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
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

		git.rm().addFilepattern("a").call();

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add symlink 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add folder 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.SYMLINK after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestSymlinkThenMissingWithFolderInIndex()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("b", "Hello world b");
		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add file b & symlink a").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		git.commit().setMessage("delete symlink a").call();

		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		git.add().addFilepattern(".").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());
	}

	/**
	 * Steps:
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Delete folder 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Add symlink 'a' in the working tree & index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The checkout command should raise an error. The conflicting path is 'a'.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenMissingWithSymlinkInIndex()
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

		FileUtils.createSymLink(new File(db.getWorkTree(), "a"), "b");
		git.add().addFilepattern("a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

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
	 * <p>
	 * 1.Add folder 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.Replace folder 'a'by symlink 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete symlink 'a' in the working tree & add to index
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.TREE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFolderThenSymlinkWithMissingInIndex()
			throws Exception {
		Git git = new Git(db);
		FileUtils.mkdirs(new File(db.getWorkTree(), "a"));
		writeTrashFile("a/c", "Hello world c");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("add folder a & file b").call();
		Ref branch_1 = git.branchCreate().setName("branch_1").call();
		git.rm().addFilepattern("a").call();
		File symlinkA = new File(db.getWorkTree(), "a");
		FileUtils.createSymLink(symlinkA, "b");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("add symlink a").call();

		FileEntry entry = new FileTreeIterator.FileEntry(new File(
				db.getWorkTree(), "a"), db.getFS());
		assertEquals(FileMode.SYMLINK, entry.getMode());

		git.rm().addFilepattern("a").call();

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.TREE, entry.getMode());
	}
}
