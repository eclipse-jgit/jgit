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
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.hooks.CommitMsgHook;
import org.eclipse.jgit.hooks.PostCheckoutHook;
import org.eclipse.jgit.hooks.PostCommitHook;
import org.eclipse.jgit.hooks.PreCommitHook;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
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
	public void testFindPostCheckoutHook() throws Exception {
		assumeSupportedPlatform();

		assertNull("no hook should be installed",
				FS.DETECTED.findHook(db, PostCheckoutHook.NAME));
		File hookFile = writeHookFile(PostCheckoutHook.NAME,
				"#!/bin/bash\necho \"test $1 $2\"");
		assertEquals("expected to find post-checkout hook", hookFile,
				FS.DETECTED.findHook(db, PostCheckoutHook.NAME));
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
	public void testPostCheckoutRunHook() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PostCheckoutHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\necho 1>&2 \"stderr\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		ProcessResult res = FS.DETECTED.runHookIfPresent(db,
				PostCheckoutHook.NAME, new String[] { "arg1", "arg2" },
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
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		try {
			git.commit().setMessage("commit")
					.setHookOutputStream(new PrintStream(out))
					.setHookErrorStream(new PrintStream(err)).call();
		} catch (AbortedByHookException e) {
			throw new AssertionError("unexpected hook failure", e);
		}
		assertEquals("unexpected hook output stream",
				"test pre-commit\ntest commit-msg .git/COMMIT_EDITMSG\ntest post-commit\n",
				out.toString(UTF_8));
		assertEquals("unexpected hook error stream",
				"stderr pre-commit\nstderr commit-msg\nstderr post-commit\n",
				err.toString(UTF_8));
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
	public void testPostCheckoutHook() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PostCheckoutHook.NAME,
				"#!/bin/sh\necho \"test post-checkout out\"\necho 1>&2 \"stderr post-checkout err\"\nexit 0");
		Git git = Git.wrap(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();

		ByteArrayOutputStream outTrash = new ByteArrayOutputStream();
		git.commit().setMessage("commit")
				.setHookOutputStream(new PrintStream(outTrash)).call();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		try {
			git.checkout().setName("testbranch").setCreateBranch(true)
					.setHookOutputStream(new PrintStream(out))
					.setHookErrorStream(new PrintStream(err)).call();
		} catch (Exception e) {
			throw new AssertionError(
					"unexpected error during checkout operation while testing post-checkout hook",
					e);
		}

		assertEquals("unexpected hook output stream",
				"test post-checkout out\n",
				out.toString(UTF_8));
		assertEquals("unexpected hook error stream",
				"stderr post-checkout err\n",
				err.toString(UTF_8));
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
