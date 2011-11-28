/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class DirCacheCheckoutTest extends ReadTreeTest {
	private DirCacheCheckout dco;
	@Override
	public void prescanTwoTrees(Tree head, Tree merge)
			throws IllegalStateException, IOException {
		DirCache dc = db.lockDirCache();
		try {
			dco = new DirCacheCheckout(db, head.getId(), dc, merge.getId());
			dco.preScanTwoTrees();
		} finally {
			dc.unlock();
		}
	}

	@Override
	public void checkout() throws IOException {
		DirCache dc = db.lockDirCache();
		try {
			dco = new DirCacheCheckout(db, theHead.getId(), dc, theMerge.getId());
			dco.checkout();
		} finally {
			dc.unlock();
		}
	}

	@Override
	public List<String> getRemoved() {
		return dco.getRemoved();
	}

	@Override
	public Map<String, ObjectId> getUpdated() {
		return dco.getUpdated();
	}

	@Override
	public List<String> getConflicts() {
		return dco.getConflicts();
	}

	@Test
	public void testResetHard() throws IOException, NoFilepatternException,
			GitAPIException {
		Git git = new Git(db);
		writeTrashFile("f", "f()");
		writeTrashFile("D/g", "g()");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("inital").call();
		assertIndex(mkmap("f", "f()", "D/g", "g()"));

		git.branchCreate().setName("topic").call();

		writeTrashFile("f", "f()\nmaster");
		writeTrashFile("D/g", "g()\ng2()");
		writeTrashFile("E/h", "h()");
		git.add().addFilepattern(".").call();
		RevCommit master = git.commit().setMessage("master-1").call();
		assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));

		checkoutBranch("refs/heads/topic");
		assertIndex(mkmap("f", "f()", "D/g", "g()"));

		writeTrashFile("f", "f()\nside");
		assertTrue(new File(db.getWorkTree(), "D/g").delete());
		writeTrashFile("G/i", "i()");
		git.add().addFilepattern(".").call();
		git.add().addFilepattern(".").setUpdate(true).call();
		RevCommit topic = git.commit().setMessage("topic-1").call();
		assertIndex(mkmap("f", "f()\nside", "G/i", "i()"));

		writeTrashFile("untracked", "untracked");

		resetHard(master);
		assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
		resetHard(topic);
		assertIndex(mkmap("f", "f()\nside", "G/i", "i()"));
		assertWorkDir(mkmap("f", "f()\nside", "G/i", "i()", "untracked",
				"untracked"));

		assertEquals(MergeStatus.CONFLICTING, git.merge().include(master)
				.call().getMergeStatus());
		assertEquals(
				"[D/g, mode:100644, stage:1][D/g, mode:100644, stage:3][E/h, mode:100644][G/i, mode:100644][f, mode:100644, stage:1][f, mode:100644, stage:2][f, mode:100644, stage:3]",
				indexState(0));

		resetHard(master);
		assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
		assertWorkDir(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h",
				"h()", "untracked", "untracked"));
	}

	/**
	 * Reset hard from unclean condition.
	 * <p>
	 * WorkDir: Empty <br/>
	 * Index: f/g <br/>
	 * Merge: x
	 *
	 * @throws Exception
	 */
	@Test
	public void testResetHardFromIndexEntryWithoutFileToTreeWithoutFile()
			throws Exception {
		Git git = new Git(db);
		writeTrashFile("x", "x");
		git.add().addFilepattern("x").call();
		RevCommit id1 = git.commit().setMessage("c1").call();

		writeTrashFile("f/g", "f/g");
		git.rm().addFilepattern("x").call();
		git.add().addFilepattern("f/g").call();
		git.commit().setMessage("c2").call();
		deleteTrashFile("f/g");
		deleteTrashFile("f");

		// The actual test
		git.reset().setMode(ResetType.HARD).setRef(id1.getName()).call();
		assertIndex(mkmap("x", "x"));
	}

	/**
	 * A paranoid test of the test
	 *
	 * @throws Exception
	 */
	@Test
	public void testMaliciousAbsolutePathIsOk()
			throws Exception {
		testMaliciousPath(true, "ok");
	}

	@Test
	public void testMaliciousAbsolutePath() throws Exception {
		testMaliciousPath(false, "/tmp/x");
	}

	@Test
	public void testMaliciousAbsoluteCurDrivePathWindows() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteCurDrivePathWindowsOnUnix()
			throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(true, "\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "\\\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows1OnUnix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(true, "\\\\somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows2() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "\\/somepath");
	}

	@Test
	public void testMaliciousAbsoluteUNCPathWindows2OnUnix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(false, "\\/somepath"); // '/' is no good anywhere
	}

	@Test
	public void testMaliciousAbsoluteWindowsPath1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "c:\\temp\\x");
	}

	@Test
	public void testMaliciousAbsoluteWindowsPath1OnUnix() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(true, "c:\\temp\\x");
	}

	@Test
	public void testMaliciousAbsoluteWindowsPath2() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPath(false, "c:/temp/x");
	}

	@Test
	public void testMaliciousGitPath1()
			throws Exception {
		testMaliciousPath(false, ".git/konfig");
	}

	@Test
	public void testMaliciousGitPath2() throws Exception {
		testMaliciousPath(false, ".git", "konfig");
	}

	@Test
	public void testMaliciousGitPath1Case() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows(); // or OS X
		testMaliciousPath(false, ".Git/konfig");
	}

	@Test
	public void testMaliciousGitPath2Case() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows(); // or OS X
		testMaliciousPath(false, ".giT", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndSpaceWindows() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, ".git ", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndSpaceUnixOk() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(true, ".git ", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndDotWindows1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, ".git.", "konfig");
	}

	@Test
	public void testMaliciousGitPathEndDotWindows2() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, ".f.");
	}

	@Test
	public void testMaliciousGitPathEndDotWindows3() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(true, ".f");
	}

	@Test
	public void testMaliciousGitPathEndDotUnixOk() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(true, ".git.", "konfig");
	}

	@Test
	public void testMaliciousPathDotDot() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPath(false, "..", "no");
	}

	@Test
	public void testMaliciousPathDot() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPath(false, ".", "no");
	}

	@Test
	public void testMaliciousPathEmpty() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setCurrentPlatform();
		testMaliciousPath(false, "", "no");
	}

	@Test
	public void testMaliciousWindowsADS() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "some:path");
	}

	@Test
	public void testMaliciousWindowsADSOnUnix() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		((MockSystemReader) SystemReader.getInstance()).setUnix();
		testMaliciousPath(true, "some:path");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgCon() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "con");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgConDotSuffix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "con.txt");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgLpt1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "lpt1");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgLpt1DotSuffix() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(false, "lpt1.txt");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgDotCon() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(true, ".con");
	}

	@Test
	public void testForbiddenNamesOnWindowsEgLpr() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(true, "lpt"); // good name
	}

	@Test
	public void testForbiddenNamesOnWindowsEgCon1() throws Exception {
		((MockSystemReader) SystemReader.getInstance()).setWindows();
		testMaliciousPath(true, "con1"); // good name
	}

	@Test
	public void testForbiddenWindowsNamesOnUnixEgCon() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		testMaliciousPath(true, "con");
	}

	@Test
	public void testForbiddenWindowsNamesOnUnixEgLpt1() throws Exception {
		if (File.separatorChar == '\\')
			return; // cannot emulate Unix on Windows for this test
		testMaliciousPath(true, "lpt1");
	}

	/**
	 * Create a bad tree and tries to check it out
	 *
	 * @param good
	 *            true if we expect this to pass
	 * @param path
	 *            to the blob, one or more levels
	 * @throws IOException
	 * @throws RefAlreadyExistsException
	 * @throws RefNotFoundException
	 * @throws InvalidRefNameException
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 */
	private void testMaliciousPath(boolean good, String... path)
			throws IOException, RefAlreadyExistsException,
			RefNotFoundException, InvalidRefNameException,
			MissingObjectException, IncorrectObjectTypeException {
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
			treeFormatter.append(path[i], mode, insertId);
			insertId = newObjectInserter.insert(treeFormatter);
		}
		newObjectInserter = git.getRepository().newObjectInserter();
		CommitBuilder commitBuilder = new CommitBuilder();
		commitBuilder.setAuthor(author);
		commitBuilder.setCommitter(committer);
		commitBuilder.setMessage("foo");
		commitBuilder.setTreeId(insertId);
		ObjectId commitId = newObjectInserter.insert(commitBuilder);
		RevWalk revWalk = new RevWalk(git.getRepository());
		try {
			git.checkout().setStartPoint(revWalk.parseCommit(commitId))
					.setName("refs/heads/master").setCreateBranch(true).call();
			if (!good)
				fail("Checkout of Tree " + Arrays.asList(path) + " should fail");
		} catch (InvalidPathException e) {
			if (good)
				throw e;
			assertThat(e.getMessage(), startsWith("Invalid path: "));
		}
	}

	private DirCacheCheckout resetHard(RevCommit commit)
			throws NoWorkTreeException,
			CorruptObjectException, IOException {
		DirCacheCheckout dc;
		dc = new DirCacheCheckout(db, null, db.lockDirCache(),
				commit.getTree());
		dc.setFailOnConflict(true);
		assertTrue(dc.checkout());
		return dc;
	}

}
