/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class PullCommandTest extends RepositoryTestCase {
	/** Second Test repository */
	protected Repository dbTarget;

	private Git source;

	private Git target;

	private File sourceFile;

	private File targetFile;

	@Test
	public void testPullFastForward() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus().equals(
				MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the source file
		writeToFile(sourceFile, "Another change");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Some change in remote").call();

		res = target.pull().call();

		assertFalse(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertEquals(res.getMergeResult().getMergeStatus(),
				MergeStatus.FAST_FORWARD);
		assertFileContentsEqual(targetFile, "Another change");
		assertEquals(RepositoryState.SAFE, target.getRepository()
				.getRepositoryState());

		res = target.pull().call();
		assertEquals(res.getMergeResult().getMergeStatus(),
				MergeStatus.ALREADY_UP_TO_DATE);
	}

	@Test
	public void testPullMerge() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus()
				.equals(MergeStatus.ALREADY_UP_TO_DATE));

		writeToFile(sourceFile, "Source change");
		source.add().addFilepattern("SomeFile.txt");
		RevCommit sourceCommit = source.commit()
				.setMessage("Source change in remote").call();

		File targetFile2 = new File(dbTarget.getWorkTree(), "OtherFile.txt");
		writeToFile(targetFile2, "Unconflicting change");
		target.add().addFilepattern("OtherFile.txt").call();
		RevCommit targetCommit = target.commit()
				.setMessage("Unconflicting change in local").call();

		res = target.pull().call();

		MergeResult mergeResult = res.getMergeResult();
		ObjectId[] mergedCommits = mergeResult.getMergedCommits();
		assertEquals(targetCommit.getId(), mergedCommits[0]);
		assertEquals(sourceCommit.getId(), mergedCommits[1]);
		RevCommit mergeCommit = new RevWalk(dbTarget).parseCommit(mergeResult
				.getNewHead());
		String message = "Merge branch 'master' of "
				+ db.getWorkTree().getAbsolutePath();
		assertEquals(message, mergeCommit.getShortMessage());
	}

	@Test
	public void testPullConflict() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus().equals(
				MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the source file
		writeToFile(sourceFile, "Source change");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Source change in remote").call();

		// change the target file
		writeToFile(targetFile, "Target change");
		target.add().addFilepattern("SomeFile.txt").call();
		target.commit().setMessage("Target change in local").call();

		res = target.pull().call();

		String sourceChangeString = "Source change\n>>>>>>> branch 'master' of "
				+ target.getRepository().getConfig().getString("remote",
						"origin", "url");

		assertFalse(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertEquals(res.getMergeResult().getMergeStatus(),
				MergeStatus.CONFLICTING);
		String result = "<<<<<<< HEAD\nTarget change\n=======\n"
				+ sourceChangeString + "\n";
		assertFileContentsEqual(targetFile, result);
		assertEquals(RepositoryState.MERGING, target.getRepository()
				.getRepositoryState());
	}

	@Test
	public void testPullLocalConflict() throws Exception {
		target.branchCreate().setName("basedOnMaster").setStartPoint(
				"refs/heads/master").setUpstreamMode(SetupUpstreamMode.TRACK)
				.call();
		target.getRepository().updateRef(Constants.HEAD).link(
				"refs/heads/basedOnMaster");
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertNull(res.getFetchResult());
		assertTrue(res.getMergeResult().getMergeStatus().equals(
				MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the file in master
		target.getRepository().updateRef(Constants.HEAD).link(
				"refs/heads/master");
		writeToFile(targetFile, "Master change");
		target.add().addFilepattern("SomeFile.txt").call();
		target.commit().setMessage("Source change in master").call();

		// change the file in slave
		target.getRepository().updateRef(Constants.HEAD).link(
				"refs/heads/basedOnMaster");
		writeToFile(targetFile, "Slave change");
		target.add().addFilepattern("SomeFile.txt").call();
		target.commit().setMessage("Source change in based on master").call();

		res = target.pull().call();

		String sourceChangeString = "Master change\n>>>>>>> branch 'master' of local repository";

		assertNull(res.getFetchResult());
		assertEquals(res.getMergeResult().getMergeStatus(),
				MergeStatus.CONFLICTING);
		String result = "<<<<<<< HEAD\nSlave change\n=======\n"
				+ sourceChangeString + "\n";
		assertFileContentsEqual(targetFile, result);
		assertEquals(RepositoryState.MERGING, target.getRepository()
				.getRepositoryState());
	}

	@Test(expected = NoHeadException.class)
	public void testPullEmptyRepository() throws Exception {
		Repository empty = createWorkRepository();
		RefUpdate delete = empty.updateRef(Constants.HEAD, true);
		delete.setForceUpdate(true);
		delete.delete();
		Git.wrap(empty).pull().call();
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		dbTarget = createWorkRepository();
		source = new Git(db);
		target = new Git(dbTarget);

		// put some file in the source repo
		sourceFile = new File(db.getWorkTree(), "SomeFile.txt");
		writeToFile(sourceFile, "Hello world");
		// and commit it
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Initial commit for source").call();

		// configure the target repo to connect to the source via "origin"
		StoredConfig targetConfig = dbTarget.getConfig();
		targetConfig.setString("branch", "master", "remote", "origin");
		targetConfig
				.setString("branch", "master", "merge", "refs/heads/master");
		RemoteConfig config = new RemoteConfig(targetConfig, "origin");

		config
				.addURI(new URIish(source.getRepository().getWorkTree()
						.getAbsolutePath()));
		config.addFetchRefSpec(new RefSpec(
				"+refs/heads/*:refs/remotes/origin/*"));
		config.update(targetConfig);
		targetConfig.save();

		targetFile = new File(dbTarget.getWorkTree(), "SomeFile.txt");
		// make sure we have the same content
		target.pull().call();
		assertFileContentsEqual(targetFile, "Hello world");
	}

	private static void writeToFile(File actFile, String string)
			throws IOException {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(actFile);
			fos.write(string.getBytes("UTF-8"));
			fos.close();
		} finally {
			if (fos != null)
				fos.close();
		}
	}

	private static void assertFileContentsEqual(File actFile, String string)
			throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		FileInputStream fis = null;
		byte[] buffer = new byte[100];
		try {
			fis = new FileInputStream(actFile);
			int read = fis.read(buffer);
			while (read > 0) {
				bos.write(buffer, 0, read);
				read = fis.read(buffer);
			}
			String content = new String(bos.toByteArray(), "UTF-8");
			assertEquals(string, content);
		} finally {
			if (fis != null)
				fis.close();
		}
	}
}
