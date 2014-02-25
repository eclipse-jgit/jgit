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

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileEntry;
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
	 * <p>
	 * 1.Add file 'a'
	 * <p>
	 * 2.Commit
	 * <p>
	 * 3.Create branch '1'
	 * <p>
	 * 4.modify file 'a'
	 * <p>
	 * 5.Commit
	 * <p>
	 * 6.Delete file 'a' in the working tree
	 * <p>
	 * 7.Checkout branch '1'
	 * <p>
	 * The working tree should contain 'a' with FileMode.REGULAR_FILE after the
	 * checkout.
	 *
	 * @throws Exception
	 */
	@Test
	public void fileModeTestFileWithMissingInWorkingTree() throws Exception {
		Git git = new Git(db);
		File fileA = writeTrashFile("a", "Hello world a");
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

		FileUtils.delete(fileA);

		git.checkout().setName(branch_1.getName()).call();

		entry = new FileTreeIterator.FileEntry(new File(db.getWorkTree(), "a"),
				db.getFS());
		assertEquals(FileMode.REGULAR_FILE, entry.getMode());
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
