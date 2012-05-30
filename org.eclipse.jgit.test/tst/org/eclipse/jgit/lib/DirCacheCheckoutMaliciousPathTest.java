/*
 * Copyright (C) 2011, Robin Rosenberg <robin.rosenberg@dewire.com>
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
package org.eclipse.jgit.lib;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class DirCacheCheckoutMaliciousPathTest extends RepositoryTestCase {
	protected ObjectId theHead;
	protected ObjectId theMerge;

	@Test
	public void testMaliciousAbsolutePathIsOk() throws Exception {
		testMaliciousPathGoodFirstCheckout("ok");
	}

	@Test
	public void testMaliciousAbsolutePathIsOkSecondCheckout() throws Exception {
		testMaliciousPathGoodSecondCheckout("ok");
	}

	@Test
	public void testMaliciousAbsolutePathIsOkTwoLevels() throws Exception {
		testMaliciousPathGoodSecondCheckout("a", "ok");
	}

	@Test
	public void testMaliciousAbsolutePath() throws Exception {
		testMaliciousPathBadFirstCheckout("/tmp/x");
	}

	@Test
	public void testMaliciousAbsolutePathSecondCheckout() throws Exception {
		testMaliciousPathBadSecondCheckout("/tmp/x");
	}

	@Test
	public void testMaliciousAbsolutePathTwoLevelsFirstBad() throws Exception {
		testMaliciousPathBadFirstCheckout("/tmp/x", "y");
	}

	@Test
	public void testMaliciousAbsolutePathTwoLevelsSecondBad() throws Exception {
		testMaliciousPathBadFirstCheckout("y", "/tmp/x");
	}

	@Test
	public void testMaliciousAbsoluteCurDrivePathWindows() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteCurDrivePathWindowsOnUnix()
			throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathGoodFirstCheckout("\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("\\\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows1OnUnix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathGoodFirstCheckout("\\\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows2() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("\\/somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows2OnUnix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathBadFirstCheckout("\\/somepath");
	}

	@Test
	public void testMaliciousAbsoluteWindowsPath1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("c:\\temp\\x");
	}

	@Test
	public void testMaliciousAbsoluteWindowsPath1OnUnix() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathGoodFirstCheckout("c:\\temp\\x");
	}

	@Test
	public void testMaliciousAbsoluteWindowsPath2() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPathBadFirstCheckout("c:/temp/x");
	}

	@Test
	public void testMaliciousGitPath1() throws Exception {
		testMaliciousPathBadFirstCheckout(".git/konfig");
	}

	@Test
	public void testMaliciousGitPath2() throws Exception {
		testMaliciousPathBadFirstCheckout(".git", "konfig");
	}

	@Test
	public void testMaliciousGitPath1Case() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows(); // or OS X
		testMaliciousPathBadFirstCheckout(".Git/konfig");
	}

	@Test
	public void testMaliciousGitPath2Case() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows(); // or OS X
		testMaliciousPathBadFirstCheckout(".gIt", "konfig");
	}

	@Test
	public void testMaliciousGitPath3Case() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows(); // or OS X
		testMaliciousPathBadFirstCheckout(".giT", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndSpaceWindows() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout(".git ", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndSpaceUnixOk() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathGoodFirstCheckout(".git ", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndDotWindows1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout(".git.", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndDotWindows2() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout(".f.");
	}

	@Test
	public void testMaliciousGitPathEndDotWindows3() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathGoodFirstCheckout(".f");
	}

	@Test
	public void testMaliciousGitPathEndDotUnixOk() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathGoodFirstCheckout(".git.", "konfig");
	}

	@Test
	public void testMaliciousPathDotDot() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPathBadFirstCheckout("..", "no");
	}

	@Test
	public void testMaliciousPathDot() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPathBadFirstCheckout(".", "no");
	}

	@Test
	public void testMaliciousPathEmpty() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPathBadFirstCheckout("", "no");
	}

	@Test
	public void testMaliciousWindowsADS() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("some:path");
	}

	@Test
	public void testMaliciousWindowsADSOnUnix() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPathGoodFirstCheckout("some:path");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgCon() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("con");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgConDotSuffix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("con.txt");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgLpt1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("lpt1");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgLpt1DotSuffix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathBadFirstCheckout("lpt1.txt");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgDotCon() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathGoodFirstCheckout(".con");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgLpr() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathGoodFirstCheckout("lpt"); // good name
	}

	@Test
	public void testForbiddenNamesOnWindowsEgCon1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPathGoodFirstCheckout("con1"); // good name
	}

	@Test
	public void testForbiddenWindowsNamesOnUnixEgCon() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		testMaliciousPathGoodFirstCheckout("con");
	}

	@Test
	public void testForbiddenWindowsNamesOnUnixEgLpt1() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		testMaliciousPathGoodFirstCheckout("lpt1");
	}

	private void testMaliciousPathBadFirstCheckout(String... paths)
			throws Exception {
		testMaliciousPath(false, false, paths);
	}

	private void testMaliciousPathBadSecondCheckout(String... paths) throws Exception {
		testMaliciousPath(false, true, paths);
	}

	private void testMaliciousPathGoodFirstCheckout(String... paths)
			throws Exception {
		testMaliciousPath(true, false, paths);
	}

	private void testMaliciousPathGoodSecondCheckout(String... paths) throws Exception {
		testMaliciousPath(true, true, paths);
	}

	/**
	 * Create a bad tree and tries to check it out
	 *
	 * @param good
	 *            true if we expect this to pass
	 * @param secondCheckout
	 *            perform the actual test on the second checkout
	 * @param path
	 *            to the blob, one or more levels
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private void testMaliciousPath(boolean good, boolean secondCheckout,
			String... path) throws GitAPIException, IOException {
		Git git = new Git(db);
		ObjectInserter newObjectInserter;
		newObjectInserter = git.getRepository().newObjectInserter();
		ObjectId blobId = newObjectInserter.insert(Constants.OBJ_BLOB,
				"data".getBytes());
		newObjectInserter = git.getRepository().newObjectInserter();
		FileMode mode = FileMode.REGULAR_FILE;
		ObjectId insertId = blobId;
		for (int i = path.length - 1; i >= 0; --i) {
			TreeFormatter treeFormatter = new TreeFormatter();
			treeFormatter.append("goodpath", mode, insertId);
			insertId = newObjectInserter.insert(treeFormatter);
			mode = FileMode.TREE;
		}
		newObjectInserter = git.getRepository().newObjectInserter();
		CommitBuilder commitBuilder = new CommitBuilder();
		commitBuilder.setAuthor(author);
		commitBuilder.setCommitter(committer);
		commitBuilder.setMessage("foo#1");
		commitBuilder.setTreeId(insertId);
		ObjectId firstCommitId = newObjectInserter.insert(commitBuilder);

		newObjectInserter = git.getRepository().newObjectInserter();
		mode = FileMode.REGULAR_FILE;
		insertId = blobId;
		for (int i = path.length - 1; i >= 0; --i) {
			TreeFormatter treeFormatter = new TreeFormatter();
			treeFormatter.append(path[i], mode, insertId);
			insertId = newObjectInserter.insert(treeFormatter);
			mode = FileMode.TREE;
		}

		// Create another commit
		commitBuilder = new CommitBuilder();
		commitBuilder.setAuthor(author);
		commitBuilder.setCommitter(committer);
		commitBuilder.setMessage("foo#2");
		commitBuilder.setTreeId(insertId);
		commitBuilder.setParentId(firstCommitId);
		ObjectId commitId = newObjectInserter.insert(commitBuilder);

		RevWalk revWalk = new RevWalk(git.getRepository());
		if (!secondCheckout)
			git.checkout().setStartPoint(revWalk.parseCommit(firstCommitId))
					.setName("refs/heads/master").setCreateBranch(true).call();
		try {
			if (secondCheckout) {
				git.checkout().setStartPoint(revWalk.parseCommit(commitId))
						.setName("refs/heads/master").setCreateBranch(true)
						.call();
			} else {
				git.branchCreate().setName("refs/heads/next")
						.setStartPoint(commitId.name()).call();
				git.checkout().setName("refs/heads/next")
						.call();
			}
			if (!good)
				fail("Checkout of Tree " + Arrays.asList(path) + " should fail");
		} catch (InvalidPathException e) {
			if (good)
				throw e;
			assertThat(e.getMessage(), startsWith("Invalid path: "));
		}
	}

}
