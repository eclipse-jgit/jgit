/*
 * Copyright (C) 2022, Simeon Andreev <simeon.danailov.andreev@gmail.com> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.kohsuke.args4j.Argument;

/**
 * Base test case for the {@code difftool} and {@code mergetool} commands.
 */
public abstract class ExternalToolTestCase extends CLIRepositoryTestCase {

	public static class GitCliJGitWrapperParser {
		@Argument(index = 0, metaVar = "metaVar_command", required = true, handler = SubcommandHandler.class)
		TextBuiltin subcommand;

		@Argument(index = 1, metaVar = "metaVar_arg")
		List<String> arguments = new ArrayList<>();
	}

	protected static final String TOOL_NAME = "some_tool";

	private static final String TEST_BRANCH_NAME = "test_branch";

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
		git.branchCreate().setName(TEST_BRANCH_NAME).call();
	}

	protected String[] runAndCaptureUsingInitRaw(String... args)
			throws Exception {
		CLIGitCommand.Result result = new CLIGitCommand.Result();

		GitCliJGitWrapperParser bean = new GitCliJGitWrapperParser();
		CmdLineParser clp = new CmdLineParser(bean);
		clp.parseArgument(args);

		TextBuiltin cmd = bean.subcommand;
		cmd.initRaw(db, null, null, result.out, result.err);
		cmd.execute(bean.arguments.toArray(new String[bean.arguments.size()]));
		if (cmd.getOutputWriter() != null) {
			cmd.getOutputWriter().flush();
		}
		if (cmd.getErrorWriter() != null) {
			cmd.getErrorWriter().flush();
		}
		return result.outLines().toArray(new String[0]);
	}

	protected CherryPickResult createMergeConflict() throws Exception {
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("files a & b added").call();
		writeTrashFile("a", "Hello world a 1");
		writeTrashFile("b", "Hello world b 1");
		git.add().addFilepattern(".").call();
		RevCommit commit1 = git.commit().setMessage("files a & b commit 1")
				.call();
		git.branchCreate().setName("branch_1").call();
		git.checkout().setName(TEST_BRANCH_NAME).call();
		writeTrashFile("a", "Hello world a 2");
		writeTrashFile("b", "Hello world b 2");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("files a & b commit 2").call();
		git.branchCreate().setName("branch_2").call();
		CherryPickResult result = git.cherryPick().include(commit1).call();
		return result;
	}

	protected RevCommit createUnstagedChanges() throws Exception {
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit = git.commit().setMessage("files a & b").call();
		writeTrashFile("a", "New Hello world a");
		writeTrashFile("b", "New Hello world b");
		return commit;
	}

	protected RevCommit createStagedChanges() throws Exception {
		RevCommit commit = createUnstagedChanges();
		git.add().addFilepattern(".").call();
		return commit;
	}

	protected List<DiffEntry> getRepositoryChanges(RevCommit commit)
			throws Exception {
		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit.getTree());
		FileTreeIterator modifiedTree = new FileTreeIterator(db);
		tw.addTree(modifiedTree);
		List<DiffEntry> changes = DiffEntry.scan(tw);
		return changes;
	}

	protected static void assertArrayOfLinesEquals(String failMessage,
			String[] expected, String[] actual) {
		assertEquals(failMessage, toString(expected), toString(actual));
	}

	protected static String getEchoCommand() {
		/*
		 * use 'MERGED' placeholder, as both 'LOCAL' and 'REMOTE' will be
		 * replaced with full paths to a temporary file during some of the tests
		 */
		return "(echo \"$MERGED\")";
	}
}
