/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
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
		try (RevWalk rw = new RevWalk(dbTarget)) {
			RevCommit mergeCommit = rw.parseCommit(mergeResult.getNewHead());
			String message = "Merge branch 'master' of "
					+ db.getWorkTree().getAbsolutePath();
			assertEquals(message, mergeCommit.getShortMessage());
		}
		assertTrue(target.status().call().isClean());
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
		Status status = target.status().call();
		Map<String, StageState> conflicting = status.getConflictingStageState();
		assertEquals(1, conflicting.size());
		assertEquals(StageState.BOTH_MODIFIED, conflicting.get("SomeFile.txt"));
	}

	@Test
	public void testPullConflictTheirs() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus()
				.equals(MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the source file
		writeToFile(sourceFile, "Source change");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Source change in remote").call();

		// change the target file
		writeToFile(targetFile, "Target change");
		target.add().addFilepattern("SomeFile.txt").call();
		target.commit().setMessage("Target change in local").call();

		res = target.pull().setStrategy(MergeStrategy.THEIRS).call();

		assertTrue(res.isSuccessful());
		assertFileContentsEqual(targetFile, "Source change");
		assertEquals(RepositoryState.SAFE,
				target.getRepository().getRepositoryState());
		assertTrue(target.status().call().isClean());
	}

	@Test
	public void testPullConflictXtheirs() throws Exception {
		PullResult res = target.pull().call();
		// nothing to update since we don't have different data yet
		assertTrue(res.getFetchResult().getTrackingRefUpdates().isEmpty());
		assertTrue(res.getMergeResult().getMergeStatus()
				.equals(MergeStatus.ALREADY_UP_TO_DATE));

		assertFileContentsEqual(targetFile, "Hello world");

		// change the source file
		writeToFile(sourceFile, "a\nHello\nb\n");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Multi-line change in remote").call();

		// Pull again
		res = target.pull().call();
		assertTrue(res.isSuccessful());
		assertFileContentsEqual(targetFile, "a\nHello\nb\n");

		// change the source file
		writeToFile(sourceFile, "a\nSource change\nb\n");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Source change in remote").call();

		// change the target file
		writeToFile(targetFile, "a\nTarget change\nb\nc\n");
		target.add().addFilepattern("SomeFile.txt").call();
		target.commit().setMessage("Target change in local").call();

		res = target.pull().setContentMergeStrategy(ContentMergeStrategy.THEIRS)
				.call();

		assertTrue(res.isSuccessful());
		assertFileContentsEqual(targetFile, "a\nSource change\nb\nc\n");
		assertEquals(RepositoryState.SAFE,
				target.getRepository().getRepositoryState());
		assertTrue(target.status().call().isClean());
	}

	@Test
	public void testPullWithUntrackedStash() throws Exception {
		target.pull().call();

		// change the source file
		writeToFile(sourceFile, "Source change");
		source.add().addFilepattern("SomeFile.txt").call();
		source.commit().setMessage("Source change in remote").call();

		// write untracked file
		writeToFile(new File(dbTarget.getWorkTree(), "untracked.txt"),
				"untracked");
		RevCommit stash = target.stashCreate().setIndexMessage("message here")
				.setIncludeUntracked(true).call();
		assertNotNull(stash);
		assertTrue(target.status().call().isClean());

		// pull from source
		assertTrue(target.pull().call().isSuccessful());
		assertEquals("[SomeFile.txt, mode:100644, content:Source change]",
				indexState(dbTarget, CONTENT));
		assertFalse(JGitTestUtil.check(dbTarget, "untracked.txt"));
		assertEquals("Source change",
				JGitTestUtil.read(dbTarget, "SomeFile.txt"));

		// apply the stash
		target.stashApply().setStashRef(stash.getName()).call();
		assertEquals("[SomeFile.txt, mode:100644, content:Source change]",
				indexState(dbTarget, CONTENT));
		assertEquals("untracked", JGitTestUtil.read(dbTarget, "untracked.txt"));
		assertEquals("Source change",
				JGitTestUtil.read(dbTarget, "SomeFile.txt"));
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

	@Test
	public void testPullMergeProgrammaticConfiguration() throws Exception {
		// create another commit on another branch in source
		source.checkout().setCreateBranch(true).setName("other").call();
		sourceFile = new File(db.getWorkTree(), "file2.txt");
		writeToFile(sourceFile, "content");
		source.add().addFilepattern("file2.txt").call();
		RevCommit sourceCommit = source.commit()
				.setMessage("source commit on branch other").call();

		File targetFile2 = new File(dbTarget.getWorkTree(), "OtherFile.txt");
		writeToFile(targetFile2, "Unconflicting change");
		target.add().addFilepattern("OtherFile.txt").call();
		RevCommit targetCommit = target.commit()
				.setMessage("Unconflicting change in local").call();

		PullResult res = target.pull().setRemote("origin")
				.setRemoteBranchName("other")
				.setRebase(false).call();

		MergeResult mergeResult = res.getMergeResult();
		ObjectId[] mergedCommits = mergeResult.getMergedCommits();
		assertEquals(targetCommit.getId(), mergedCommits[0]);
		assertEquals(sourceCommit.getId(), mergedCommits[1]);
		try (RevWalk rw = new RevWalk(dbTarget)) {
			RevCommit mergeCommit = rw.parseCommit(mergeResult.getNewHead());
			String message = "Merge branch 'other' of "
					+ db.getWorkTree().getAbsolutePath();
			assertEquals(message, mergeCommit.getShortMessage());
		}
	}

	@Test
	public void testPullMergeProgrammaticConfigurationImpliedTargetBranch()
			throws Exception {
		// create another commit on another branch in source
		source.checkout().setCreateBranch(true).setName("other").call();
		sourceFile = new File(db.getWorkTree(), "file2.txt");
		writeToFile(sourceFile, "content");
		source.add().addFilepattern("file2.txt").call();
		RevCommit sourceCommit = source.commit()
				.setMessage("source commit on branch other").call();

		target.checkout().setCreateBranch(true).setName("other").call();
		File targetFile2 = new File(dbTarget.getWorkTree(), "OtherFile.txt");
		writeToFile(targetFile2, "Unconflicting change");
		target.add().addFilepattern("OtherFile.txt").call();
		RevCommit targetCommit = target.commit()
				.setMessage("Unconflicting change in local").call();

		// the source branch "other" matching the target branch should be
		// implied
		PullResult res = target.pull().setRemote("origin").setRebase(false)
				.call();

		MergeResult mergeResult = res.getMergeResult();
		ObjectId[] mergedCommits = mergeResult.getMergedCommits();
		assertEquals(targetCommit.getId(), mergedCommits[0]);
		assertEquals(sourceCommit.getId(), mergedCommits[1]);
		try (RevWalk rw = new RevWalk(dbTarget)) {
			RevCommit mergeCommit = rw.parseCommit(mergeResult.getNewHead());
			String message = "Merge branch 'other' of "
					+ db.getWorkTree().getAbsolutePath() + " into other";
			assertEquals(message, mergeCommit.getShortMessage());
		}
	}

	private enum TestPullMode {
		MERGE, REBASE, REBASE_MERGES
	}

	@Test
	/** global rebase config should be respected */
	public void testPullWithRebaseMerges1Config() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "merges");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE_MERGES);
	}

	@Test
	/**
	 * global rebase config using old "preserve" value which was renamed to
	 * "merges" should be respected to ensure backwards compatibility
	 */
	public void testPullWithRebaseMerges1ConfigAlias() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "preserve");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE_MERGES);
	}

	@Test
	/** the branch-local config should win over the global config */
	public void testPullWithRebaseMergesConfig2() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "false");
			config.setString("branch", "master", "rebase", "merges");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE_MERGES);
	}

	@Test
	/** the branch-local config should be respected */
	public void testPullWithRebaseMergesConfig3() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("branch", "master", "rebase", "merges");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE_MERGES);
	}

	@Test
	/** global rebase config should be respected */
	public void testPullWithRebaseConfig1() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "true");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE);
	}

	@Test
	/** the branch-local config should win over the global config */
	public void testPullWithRebaseConfig2() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "merges");
			config.setString("branch", "master", "rebase", "true");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE);
	}

	@Test
	/** the branch-local config should be respected */
	public void testPullWithRebaseConfig3() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("branch", "master", "rebase", "true");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.REBASE);
	}

	@Test
	/** without config it should merge */
	public void testPullWithoutConfig() throws Exception {
		Callable<PullResult> setup = target.pull();
		doTestPullWithRebase(setup, TestPullMode.MERGE);
	}

	@Test
	/** the branch local config should win over the global config */
	public void testPullWithMergeConfig() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "true");
			config.setString("branch", "master", "rebase", "false");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.MERGE);
	}

	@Test
	/** the branch local config should win over the global config */
	public void testPullWithMergeConfig2() throws Exception {
		Callable<PullResult> setup = () -> {
			StoredConfig config = dbTarget.getConfig();
			config.setString("pull", null, "rebase", "false");
			config.save();
			return target.pull().call();
		};
		doTestPullWithRebase(setup, TestPullMode.MERGE);
	}

	private void doTestPullWithRebase(Callable<PullResult> pullSetup,
			TestPullMode expectedPullMode) throws Exception {
		// simple upstream change
		writeToFile(sourceFile, "content");
		source.add().addFilepattern(sourceFile.getName()).call();
		RevCommit sourceCommit = source.commit().setMessage("source commit")
				.call();

		// create a merge commit in target
		File loxalFile = new File(dbTarget.getWorkTree(), "local.txt");
		writeToFile(loxalFile, "initial\n");
		target.add().addFilepattern("local.txt").call();
		RevCommit t1 = target.commit().setMessage("target commit 1").call();

		target.checkout().setCreateBranch(true).setName("side").call();

		String newContent = "initial\n" + "and more\n";
		writeToFile(loxalFile, newContent);
		target.add().addFilepattern("local.txt").call();
		RevCommit t2 = target.commit().setMessage("target commit 2").call();

		target.checkout().setName("master").call();

		MergeResult mergeResult = target.merge()
				.setFastForward(MergeCommand.FastForwardMode.NO_FF).include(t2)
				.call();
		assertEquals(MergeStatus.MERGED, mergeResult.getMergeStatus());
		assertFileContentsEqual(loxalFile, newContent);
		ObjectId merge = mergeResult.getNewHead();

		// pull
		PullResult res = pullSetup.call();
		assertNotNull(res.getFetchResult());

		if (expectedPullMode == TestPullMode.MERGE) {
			assertEquals(MergeStatus.MERGED, res.getMergeResult()
					.getMergeStatus());
			assertNull(res.getRebaseResult());
		} else {
			assertNull(res.getMergeResult());
			assertEquals(RebaseResult.OK_RESULT, res.getRebaseResult());
		}
		assertFileContentsEqual(sourceFile, "content");

		try (RevWalk rw = new RevWalk(dbTarget)) {
			rw.sort(RevSort.TOPO);
			rw.markStart(rw.parseCommit(dbTarget.resolve("refs/heads/master")));

			RevCommit next;
			if (expectedPullMode == TestPullMode.MERGE) {
				next = rw.next();
				assertEquals(2, next.getParentCount());
				assertEquals(merge, next.getParent(0));
				assertEquals(sourceCommit, next.getParent(1));
				// since both parents are known do no further checks here
			} else {
				if (expectedPullMode == TestPullMode.REBASE_MERGES) {
					next = rw.next();
					assertEquals(2, next.getParentCount());
				}
				next = rw.next();
				assertEquals(t2.getShortMessage(), next.getShortMessage());
				next = rw.next();
				assertEquals(t1.getShortMessage(), next.getShortMessage());
				next = rw.next();
				assertEquals(sourceCommit, next);
				next = rw.next();
				assertEquals("Initial commit for source",
						next.getShortMessage());
				next = rw.next();
				assertNull(next);
			}
		}
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		dbTarget = createWorkRepository();
		addRepoToClose(dbTarget);
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
		try (FileOutputStream fos = new FileOutputStream(actFile)) {
			fos.write(string.getBytes(UTF_8));
		}
	}

	private static void assertFileContentsEqual(File actFile, String string)
			throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[100];
		try (FileInputStream fis = new FileInputStream(actFile)) {
			int read = fis.read(buffer);
			while (read > 0) {
				bos.write(buffer, 0, read);
				read = fis.read(buffer);
			}
			String content = new String(bos.toByteArray(), UTF_8);
			assertEquals(string, content);
		}
	}
}
