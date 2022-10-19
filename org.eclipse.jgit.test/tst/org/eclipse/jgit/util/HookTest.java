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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.hooks.CommitMsgHook;
import org.eclipse.jgit.hooks.PostCommitHook;
import org.eclipse.jgit.hooks.PreCommitHook;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class HookTest extends RepositoryTestCase {

	@Test
	void testFindHook() throws Exception {
		assumeSupportedPlatform();

		assertNull(FS.DETECTED.findHook(db, PreCommitHook.NAME),
				"no hook should be installed");
		File hookFile = writeHookFile(PreCommitHook.NAME,
				"#!/bin/bash\necho \"test $1 $2\"");
		assertEquals(hookFile,
				FS.DETECTED.findHook(db, PreCommitHook.NAME),
				"expected to find pre-commit hook");
	}

	@Test
	void testFindPostCommitHook() throws Exception {
		assumeSupportedPlatform();

		assertNull(FS.DETECTED.findHook(db, PostCommitHook.NAME),
				"no hook should be installed");
		File hookFile = writeHookFile(PostCommitHook.NAME,
				"#!/bin/bash\necho \"test $1 $2\"");
		assertEquals(hookFile,
				FS.DETECTED.findHook(db, PostCommitHook.NAME),
				"expected to find post-commit hook");
	}

	@Test
	void testFailedCommitMsgHookBlocksCommit() throws Exception {
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
			assertEquals("Rejected by \"commit-msg\" hook.\nstderr\n",
					e.getMessage(),
					"unexpected error message from commit-msg hook");
			assertEquals("test\n",
					out.toString(UTF_8),
					"unexpected output from commit-msg hook");
		}
	}

	@Test
	void testCommitMsgHookReceivesCorrectParameter() throws Exception {
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
	void testCommitMsgHookCanModifyCommitMessage() throws Exception {
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
	void testPostCommitRunHook() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PostCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\necho 1>&2 \"stderr\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		ProcessResult res = FS.DETECTED.runHookIfPresent(db,
				PostCommitHook.NAME,
				new String[]{
						"arg1", "arg2"},
				new PrintStream(out), new PrintStream(err), "stdin");

		assertEquals("test arg1 arg2\nstdin\n",
				out.toString(UTF_8),
				"unexpected hook output");
		assertEquals("stderr\n",
				err.toString(UTF_8),
				"unexpected output on stderr stream");
		assertEquals(0, res.getExitCode(), "unexpected exit code");
		assertEquals(ProcessResult.Status.OK,
				res.getStatus(),
				"unexpected process status");
	}

	@Test
	void testAllCommitHooks() throws Exception {
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
			fail("unexpected hook failure");
		}
		assertEquals("test pre-commit\ntest commit-msg .git/COMMIT_EDITMSG\ntest post-commit\n",
				out.toString(UTF_8),
				"unexpected hook output");
	}

	@Test
	void testRunHook() throws Exception {
		assumeSupportedPlatform();

		writeHookFile(PreCommitHook.NAME,
				"#!/bin/sh\necho \"test $1 $2\"\nread INPUT\necho $INPUT\n"
						+ "echo $GIT_DIR\necho $GIT_WORK_TREE\necho 1>&2 \"stderr\"");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		ProcessResult res = FS.DETECTED.runHookIfPresent(db,
				PreCommitHook.NAME,
				new String[]{
						"arg1", "arg2"},
				new PrintStream(out), new PrintStream(err), "stdin");

		assertEquals("test arg1 arg2\nstdin\n" + db.getDirectory().getAbsolutePath()
				+ '\n' + db.getWorkTree().getAbsolutePath() + '\n',
				out.toString(UTF_8),
				"unexpected hook output");
		assertEquals("stderr\n",
				err.toString(UTF_8),
				"unexpected output on stderr stream");
		assertEquals(0, res.getExitCode(), "unexpected exit code");
		assertEquals(ProcessResult.Status.OK,
				res.getStatus(),
				"unexpected process status");
	}

	@Test
	void testRunHookHooksPathRelative() throws Exception {
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
					PreCommitHook.NAME, new String[]{"arg1", "arg2"},
					new PrintStream(out), new PrintStream(err), "stdin");

			assertEquals("test arg1 arg2\nstdin\n"
					+ db.getDirectory().getAbsolutePath() + '\n'
					+ db.getWorkTree().getAbsolutePath() + '\n',
					out.toString(UTF_8),
					"unexpected hook output");
			assertEquals("stderr\n",
					err.toString(UTF_8),
					"unexpected output on stderr stream");
			assertEquals(0, res.getExitCode(), "unexpected exit code");
			assertEquals(ProcessResult.Status.OK,
					res.getStatus(),
					"unexpected process status");
		}
	}

	@Test
	void testRunHookHooksPathAbsolute() throws Exception {
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
					PreCommitHook.NAME, new String[]{"arg1", "arg2"},
					new PrintStream(out), new PrintStream(err), "stdin");

			assertEquals("test arg1 arg2\nstdin\n"
					+ db.getDirectory().getAbsolutePath() + '\n'
					+ db.getWorkTree().getAbsolutePath() + '\n',
					out.toString(UTF_8),
					"unexpected hook output");
			assertEquals("stderr\n",
					err.toString(UTF_8),
					"unexpected output on stderr stream");
			assertEquals(0, res.getExitCode(), "unexpected exit code");
			assertEquals(ProcessResult.Status.OK,
					res.getStatus(),
					"unexpected process status");
		}
	}

	@Test
	void testHookPathWithBlank() throws Exception {
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
					PreCommitHook.NAME, new String[]{"arg1", "arg2"},
					new PrintStream(out), new PrintStream(err), "stdin");

			assertEquals("test arg1 arg2\nstdin\n"
					+ db.getDirectory().getAbsolutePath() + '\n'
					+ db.getWorkTree().getAbsolutePath() + '\n',
					out.toString(UTF_8),
					"unexpected hook output");
			assertEquals("stderr\n",
					err.toString(UTF_8),
					"unexpected output on stderr stream");
			assertEquals(0, res.getExitCode(), "unexpected exit code");
			assertEquals(ProcessResult.Status.OK,
					res.getStatus(),
					"unexpected process status");
		}
	}

	@Test
	void testFailedPreCommitHookBlockCommit() throws Exception {
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
			assertEquals("Rejected by \"pre-commit\" hook.\nstderr\n",
					e.getMessage(),
					"unexpected error message from pre-commit hook");
			assertEquals("test\n",
					out.toString(UTF_8),
					"unexpected output from pre-commit hook");
		}
	}

	private File writeHookFile(String name, String data)
			throws IOException {
		File path = new File(db.getWorkTree() + "/.git/hooks/", name);
		JGitTestUtil.write(path, data);
		FS.DETECTED.setExecute(path, true);
		return path;
	}

	private void assumeSupportedPlatform() {
		Assumptions.assumeTrue(FS.DETECTED instanceof FS_POSIX
				|| FS.DETECTED instanceof FS_Win32_Cygwin);
	}
}
