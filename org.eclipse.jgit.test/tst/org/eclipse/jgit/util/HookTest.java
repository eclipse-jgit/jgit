/*
 * Copyright (C) 2014 Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.hooks.CommitMsgHook;
import org.eclipse.jgit.hooks.Hooks;
import org.eclipse.jgit.hooks.PostCommitHook;
import org.eclipse.jgit.hooks.PostReceiveHook;
import org.eclipse.jgit.hooks.PreCommitHook;
import org.eclipse.jgit.hooks.PrepareCommitMsgHook;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.CommitConfig.CleanupMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Assume;
import org.junit.Test;

public class HookTest extends RepositoryTestCase {

	@Test
	public void testFindHook() throws Exception {
		assumeSupportedPlatform();

		assertNull("no hook should be installed",
				FS.DETECTED.findHook(db, PreCommitHook.NAME));
		File hookFile = writeHookFile(PreCommitHook.NAME,
				"#!/bin/bash\necho \"test $1 $2\"");
		assertEquals("expected to find pre-commit hook", hookFile,
				FS.DETECTED.findHook(db, PreCommitHook.NAME));
	}

	@Test
	public void testFindPostCommitHook() throws Exception {
		assumeSupportedPlatform();

		assertNull("no hook should be installed",
				FS.DETECTED.findHook(db, PostCommitHook.NAME));
		File hookFile = writeHookFile(PostCommitHook.NAME,
				"#!/bin/bash\necho \"test $1 $2\"");
		assertEquals("expected to find post-commit hook", hookFile,
				FS.DETECTED.findHook(db, PostCommitHook.NAME));
	}

	@Test
	public void testFailedCommitMsgHookBlocksCommit() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(CommitMsgHook.NAME,
				"#!/bin/sh\necho \"test\"\n\necho 1>&2 \"stderr\"\nexit 1");
		Git git = Git.wrap(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			git.commit().setMessage("commit")
					.setHookOutputStream(new PrintStream(out)).call();
			fail("expected commit-msg hook to abort commit");
		} catch (AbortedByHookException e) {
			assertEquals("unexpected error message from commit-msg hook",
					"Rejected by \"commit-msg\" hook.\nstderr\n",
					e.getMessage());
			assertEquals("unexpected output from commit-msg hook", "test\n",
					out.toString(UTF_8));
		}
	}

	@Test
	public void testCommitMsgHookReceivesCorrectParameter() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(CommitMsgHook.NAME,
				"#!/bin/sh\necho $1\n\necho 1>&2 \"stderr\"\nexit 0");
		Git git = Git.wrap(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		git.commit().setMessage("commit")
				.setHookOutputStream(new PrintStream(out)).call();
		assertEquals(".git/COMMIT_EDITMSG\n",
				out.toString(UTF_8));
	}

	@Test
	public void testFailedCommitMsgHookKeepsSquashMessage() throws Exception {
		assumeSupportedPlatform();

		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		db.writeSquashCommitMsg("squashed");

		File hook = writeHookFile(CommitMsgHook.NAME, "#!/bin/sh\nexit 1");
		assertThrows(AbortedByHookException.class,
				() -> git.commit()
						.setHookErrorStream(
								new PrintStream(new ByteArrayOutputStream()))
						.call());
		assertEquals("squashed", db.readSquashCommitMsg());

		assertTrue(hook.delete());
		RevCommit revCommit = git.commit().call();
		assertEquals("squashed", revCommit.getFullMessage());
		assertNull(db.readSquashCommitMsg());
	}

	@Test
	public void testCommitMsgHookCanModifyCommitMessage() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(CommitMsgHook.NAME,
				"#!/bin/sh\necho \"new message\" > $1\nexit 0");
		Git git = Git.wrap(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RevCommit revCommit = git.commit().setMessage("commit")
				.setHookOutputStream(new PrintStream(out)).call();
		assertEquals("new message\n", revCommit.getFullMessage());
	}

	@Test
	public void testPrepareCommitMsgHookReceivesParameters() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\necho \"$1 $2\"\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		git.commit().setMessage("commit")
				.setHookOutputStream(new PrintStream(out)).call();
		assertEquals(".git/COMMIT_EDITMSG message\n", out.toString(UTF_8));
	}

	@Test
	public void testPrepareCommitMsgHookCanModifyMessageAndRunsWithNoVerify()
			throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\necho \"prepared\" > $1\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit revCommit = git.commit().setMessage("commit")
				.setNoVerify(true).call();
		assertEquals("prepared\n", revCommit.getFullMessage());
	}

	@Test
	public void testNoPrepareCommitMsgHookKeepsMessageAndWritesEditMsg()
			throws Exception {
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit revCommit = git.commit().setMessage("commit\n\nbody")
				.call();
		assertEquals("commit\n\nbody", revCommit.getFullMessage());
		assertEquals("commit\n\nbody", db.readCommitEditMsg());
	}

	@Test
	public void testPrepareCommitMsgHookKeepsExplicitEmptyMessage()
			throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME, "#!/bin/sh\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit revCommit = git.commit().setMessage("").call();
		assertEquals("", revCommit.getFullMessage());
	}

	@Test
	public void testPrepareCommitMsgHookDeletingEmptyMessageBlocksCommit()
			throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\nrm \"$1\"\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		assertThrows(NoMessageException.class,
				() -> git.commit().setMessage("").call());
		assertThrows(NoMessageException.class,
				() -> git.commit().setMessage("").setNoVerify(true).call());
	}

	@Test
	public void testPrepareCommitMsgHookLeavingOnlyCommentsBlocksCommit()
			throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\necho \"# comment\" > $1\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		assertThrows(NoMessageException.class, () -> git.commit()
				.setMessage("commit").setCleanupMode(CleanupMode.STRIP).call());
	}

	@Test
	public void testPrepareCommitMsgHookNotRunForCherryPick() throws Exception {
		assumeSupportedPlatform();

		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit base = git.commit().setMessage("base").call();
		writeTrashFile("b.txt", "content");
		git.add().addFilepattern("b.txt").call();
		RevCommit toPick = git.commit().setMessage("to pick").call();
		git.reset().setMode(ResetType.HARD).setRef(base.name()).call();
		writeTrashFile("c.txt", "content");
		git.add().addFilepattern("c.txt").call();
		git.commit().setMessage("other").call();

		writeHookFile(PrepareCommitMsgHook.NAME, "#!/bin/sh\nexit 1");
		CherryPickResult result = git.cherryPick().include(toPick).call();
		assertEquals(CherryPickStatus.OK, result.getStatus());
		assertEquals("to pick", result.getNewHead().getFullMessage());
	}

	@Test
	public void testPrepareCommitMsgHookNotRunForRebase() throws Exception {
		assumeSupportedPlatform();

		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit base = git.commit().setMessage("base").call();
		writeTrashFile("b.txt", "content");
		git.add().addFilepattern("b.txt").call();
		RevCommit upstream = git.commit().setMessage("upstream").call();
		git.checkout().setCreateBranch(true).setName("topic")
				.setStartPoint(base).call();
		writeTrashFile("c.txt", "content");
		git.add().addFilepattern("c.txt").call();
		git.commit().setMessage("topic").call();

		writeHookFile(PrepareCommitMsgHook.NAME, "#!/bin/sh\nexit 1");
		RebaseResult result = git.rebase().setUpstream(upstream).call();
		assertEquals(RebaseResult.Status.OK, result.getStatus());
	}

	@Test
	public void testPrepareCommitMsgHookSquashSource() throws Exception {
		assumeSupportedPlatform();

		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		db.writeSquashCommitMsg("squashed");

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\necho \"$2\"\nexit 0");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RevCommit revCommit = git.commit()
				.setHookOutputStream(new PrintStream(out)).call();
		assertEquals("squash\n", out.toString(UTF_8));
		assertEquals("squashed", revCommit.getFullMessage());
	}

	@Test
	public void testFailedPrepareCommitMsgHookKeepsSquashMessage()
			throws Exception {
		assumeSupportedPlatform();

		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		db.writeSquashCommitMsg("squashed");

		File hook = writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\nexit 1");
		assertThrows(AbortedByHookException.class,
				() -> git.commit()
						.setHookErrorStream(
								new PrintStream(new ByteArrayOutputStream()))
						.call());
		assertEquals("squashed", db.readSquashCommitMsg());

		assertTrue(hook.delete());
		RevCommit revCommit = git.commit().call();
		assertEquals("squashed", revCommit.getFullMessage());
		assertNull(db.readSquashCommitMsg());
	}

	@Test
	public void testPrepareCommitMsgHookMergeSource() throws Exception {
		assumeSupportedPlatform();

		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		RevCommit first = git.commit().setMessage("first").call();
		writeTrashFile("b.txt", "more");
		git.add().addFilepattern("b.txt").call();
		db.writeMergeHeads(Arrays.asList(first.getId()));
		db.writeMergeCommitMsg("merge message");

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\necho \"$2\"\nexit 0");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RevCommit revCommit = git.commit()
				.setHookOutputStream(new PrintStream(out)).call();
		assertEquals("merge\n", out.toString(UTF_8));
		assertEquals("merge message", revCommit.getFullMessage());
	}

	@Test
	public void testPrepareCommitMsgHookEmptyingMessageBlocksCommit()
			throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\n: > $1\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		assertThrows(NoMessageException.class,
				() -> git.commit().setMessage("commit").call());
	}

	@Test
	public void testPrepareCommitMsgHookDeletingMessageFileBlocksCommit()
			throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\nrm \"$1\"\nexit 0");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		assertThrows(NoMessageException.class,
				() -> git.commit().setMessage("commit").call());
	}

	@Test
	public void testFailedPrepareCommitMsgHookBlocksCommit() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PrepareCommitMsgHook.NAME,
				"#!/bin/sh\necho 1>&2 \"stderr\"\nexit 1");
		Git git = Git.wrap(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		try {
			git.commit().setMessage("commit")
					.setHookErrorStream(new PrintStream(
							new ByteArrayOutputStream()))
					.call();
			fail("expected prepare-commit-msg hook to abort commit");
		} catch (AbortedByHookException e) {
			assertEquals("Rejected by \"prepare-commit-msg\" hook.\nstderr\n",
					e.getMessage());
		}
	}

	@Test
	public void testPostCommitRunHook() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PostCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\necho 1>&2 \"stderr\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		ProcessResult res = FS.DETECTED.runHookIfPresent(db,
				PostCommitHook.NAME,
				new String[] {
				"arg1", "arg2" },
				new PrintStream(out), new PrintStream(err), "stdin");

		assertEquals("unexpected hook output", "test arg1 arg2\nstdin\n",
				out.toString(UTF_8));
		assertEquals("unexpected output on stderr stream", "stderr\n",
				err.toString(UTF_8));
		assertEquals("unexpected exit code", 0, res.getExitCode());
		assertEquals("unexpected process status", ProcessResult.Status.OK,
				res.getStatus());
	}

	@Test
	public void testAllCommitHooks() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PreCommitHook.NAME,
				"#!/bin/sh\necho \"test pre-commit\"\n\necho 1>&2 \"stderr pre-commit\"\nexit 0");
		writeHookFile(CommitMsgHook.NAME,
				"#!/bin/sh\necho \"test commit-msg $1\"\n\necho 1>&2 \"stderr commit-msg\"\nexit 0");
		writeHookFile(PostCommitHook.NAME,
				"#!/bin/sh\necho \"test post-commit\"\necho 1>&2 \"stderr post-commit\"\nexit 0");
		Git git = Git.wrap(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			git.commit().setMessage("commit")
					.setHookOutputStream(new PrintStream(out)).call();
		} catch (AbortedByHookException e) {
			throw new AssertionError("unexpected hook failure", e);
		}
		assertEquals("unexpected hook output",
				"test pre-commit\ntest commit-msg .git/COMMIT_EDITMSG\ntest post-commit\n",
				out.toString(UTF_8));
	}

	@Test
	public void testRunHook() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PreCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		ProcessResult res = FS.DETECTED.runHookIfPresent(db,
				PreCommitHook.NAME,
				new String[] {
				"arg1", "arg2" },
				new PrintStream(out), new PrintStream(err), "stdin");

		assertEquals("unexpected hook output",
				"test arg1 arg2\nstdin\n" + db.getDirectory().getAbsolutePath()
						+ '\n' + db.getWorkTree().getAbsolutePath() + '\n',
				out.toString(UTF_8));
		assertEquals("unexpected output on stderr stream", "stderr\n",
				err.toString(UTF_8));
		assertEquals("unexpected exit code", 0, res.getExitCode());
		assertEquals("unexpected process status", ProcessResult.Status.OK,
				res.getStatus());
	}

	@Test
	public void testRunHookHooksPathRelative() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PreCommitHook.NAME,
				"#!/bin/sh\necho \"Wrong hook $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		writeHookFile("../../" + PreCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		StoredConfig cfg = db.getConfig();
		cfg.load();
		cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_HOOKS_PATH, ".");
		cfg.save();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ByteArrayOutputStream err = new ByteArrayOutputStream()) {
			ProcessResult res = FS.DETECTED.runHookIfPresent(db,
					PreCommitHook.NAME, new String[] { "arg1", "arg2" },
					new PrintStream(out), new PrintStream(err), "stdin");

			assertEquals("unexpected hook output",
					"test arg1 arg2\nstdin\n"
							+ db.getDirectory().getAbsolutePath() + '\n'
							+ db.getWorkTree().getAbsolutePath() + '\n',
					out.toString(UTF_8));
			assertEquals("unexpected output on stderr stream", "stderr\n",
					err.toString(UTF_8));
			assertEquals("unexpected exit code", 0, res.getExitCode());
			assertEquals("unexpected process status", ProcessResult.Status.OK,
					res.getStatus());
		}
	}

	@Test
	public void testRunHookHooksPathAbsolute() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PreCommitHook.NAME,
				"#!/bin/sh\necho \"Wrong hook $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		writeHookFile("../../" + PreCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		StoredConfig cfg = db.getConfig();
		cfg.load();
		cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_HOOKS_PATH,
				db.getWorkTree().getAbsolutePath());
		cfg.save();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ByteArrayOutputStream err = new ByteArrayOutputStream()) {
			ProcessResult res = FS.DETECTED.runHookIfPresent(db,
					PreCommitHook.NAME, new String[] { "arg1", "arg2" },
					new PrintStream(out), new PrintStream(err), "stdin");

			assertEquals("unexpected hook output",
					"test arg1 arg2\nstdin\n"
							+ db.getDirectory().getAbsolutePath() + '\n'
							+ db.getWorkTree().getAbsolutePath() + '\n',
					out.toString(UTF_8));
			assertEquals("unexpected output on stderr stream", "stderr\n",
					err.toString(UTF_8));
			assertEquals("unexpected exit code", 0, res.getExitCode());
			assertEquals("unexpected process status", ProcessResult.Status.OK,
					res.getStatus());
		}
	}

	@Test
	public void testHookPathWithBlank() throws Exception {
		assumeSupportedPlatform();

		File file = writeHookFile("../../a directory/" + PreCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		StoredConfig cfg = db.getConfig();
		cfg.load();
		cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_HOOKS_PATH,
				file.getParentFile().getAbsolutePath());
		cfg.save();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ByteArrayOutputStream err = new ByteArrayOutputStream()) {
			ProcessResult res = FS.DETECTED.runHookIfPresent(db,
					PreCommitHook.NAME, new String[] { "arg1", "arg2" },
					new PrintStream(out), new PrintStream(err), "stdin");

			assertEquals("unexpected hook output",
					"test arg1 arg2\nstdin\n"
							+ db.getDirectory().getAbsolutePath() + '\n'
							+ db.getWorkTree().getAbsolutePath() + '\n',
					out.toString(UTF_8));
			assertEquals("unexpected output on stderr stream", "stderr\n",
					err.toString(UTF_8));
			assertEquals("unexpected exit code", 0, res.getExitCode());
			assertEquals("unexpected process status", ProcessResult.Status.OK,
					res.getStatus());
		}
	}

	@Test
	public void testFailedPreCommitHookBlockCommit() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PreCommitHook.NAME,
				"#!/bin/sh\necho \"test\"\n\necho 1>&2 \"stderr\"\nexit 1");
		Git git = Git.wrap(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			git.commit().setMessage("commit")
					.setHookOutputStream(new PrintStream(out)).call();
			fail("expected pre-commit hook to abort commit");
		} catch (AbortedByHookException e) {
			assertEquals("unexpected error message from pre-commit hook",
					"Rejected by \"pre-commit\" hook.\nstderr\n",
					e.getMessage());
			assertEquals("unexpected output from pre-commit hook", "test\n",
					out.toString(UTF_8));
		}
	}

	@Test
	public void testPostReceiveHookReceivesCommands() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PostReceiveHook.NAME,
				"#!/bin/sh\ncat\necho 1>&2 \"stderr\"\nexit 1");
		RevCommit commit = Git.wrap(db).commit().setMessage("commit").call();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		PostReceiveHook hook = Hooks.postReceive(db, new PrintStream(out),
				new PrintStream(err));
		hook.setCommands(List.of(new ReceiveCommand(ObjectId.zeroId(),
				commit, "refs/heads/new")));
		hook.call();

		assertEquals("unexpected hook output", ObjectId.zeroId().name() + " "
				+ commit.name() + " refs/heads/new\n", out.toString(UTF_8));
		assertEquals("unexpected output on stderr stream", "stderr\n",
				err.toString(UTF_8));
	}

	@Test
	public void testPostReceiveHookNotRunWithoutCommands() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PostReceiveHook.NAME, "#!/bin/sh\necho \"ran\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PostReceiveHook hook = Hooks.postReceive(db, new PrintStream(out),
				null);
		hook.setCommands(List.of());
		hook.call();

		assertEquals("hook should not have run", "", out.toString(UTF_8));
	}

	private File writeHookFile(String name, String data)
			throws IOException {
		File path = new File(db.getWorkTree() + "/.git/hooks/", name);
		JGitTestUtil.write(path, data);
		FS.DETECTED.setExecute(path, true);
		return path;
	}

	private void assumeSupportedPlatform() {
		Assume.assumeTrue(FS.DETECTED instanceof FS_POSIX
				|| FS.DETECTED instanceof FS_Win32_Cygwin);
	}
}
