/*
 * Copyright (C) 2013, Axel Richard <axel.richard@obeo.fr>
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

import java.io.File;
import java.util.List;

import org.eclipse.jgit.api.Git;
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
	 * The working tree should contains 'a' with FileMode.REGULAR_FILE after the
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
	 * The working tree should contains 'a' with FileMode.SYMLINK after the
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
	 * The working tree should contains 'a' with FileMode.TREE after the
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
	 * The working tree should contains 'a' with FileMode.SYMLINK after the
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
	 * The working tree should not contains 'a' -> FileMode.MISSING after the
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
	 * The working tree should contains 'a' with FileMode.SYMLINK after the
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
}
