/*
 * Copyright (C) 2021, Simeon Andreev <simeon.danailov.andreev@gmail.com> and others.
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
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.internal.diffmergetool.CommandLineDiffTool;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.Argument;

/**
 * Testing the {@code difftool} command.
 */
public class DiffToolTest extends CLIRepositoryTestCase {
	public static class GitCliJGitWrapperParser {
		@Argument(index = 0, metaVar = "metaVar_command", required = true, handler = SubcommandHandler.class)
		TextBuiltin subcommand;

		@Argument(index = 1, metaVar = "metaVar_arg")
		List<String> arguments = new ArrayList<>();
	}

	private String[] runAndCaptureUsingInitRaw(String... args)
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

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
	}

	@Test
	public void testTool() throws Exception {
		RevCommit commit = createUnstagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedDiffToolOutput(changes);

		String[] options = {
				"--tool",
				"-t",
		};

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput,
					runAndCaptureUsingInitRaw("difftool", option,
							"some_tool"));
		}
	}

	@Test
	public void testToolTrustExitCode() throws Exception {
		RevCommit commit = createUnstagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedDiffToolOutput(changes);

		String[] options = { "--tool", "-t", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw("difftool",
							"--trust-exit-code", option, "some_tool"));
		}
	}

	@Test
	public void testToolNoGuiNoPromptNoTrustExitcode() throws Exception {
		RevCommit commit = createUnstagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedDiffToolOutput(changes);

		String[] options = { "--tool", "-t", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw("difftool",
							"--no-gui", "--no-prompt", "--no-trust-exit-code",
							option, "some_tool"));
		}
	}

	@Test
	public void testToolCached() throws Exception {
		RevCommit commit = createStagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedDiffToolOutput(changes);

		String[] options = { "--cached", "--staged", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw("difftool",
							option, "--tool", "some_tool"));
		}
	}

	@Test
	public void testToolHelp() throws Exception {
		CommandLineDiffTool[] defaultTools = CommandLineDiffTool.values();
		List<String> expectedOutput = new ArrayList<>();
		expectedOutput.add("git difftool --tool=<tool> may be set to one of the following:");
		for (CommandLineDiffTool defaultTool : defaultTools) {
			String toolName = defaultTool.name();
			expectedOutput.add(toolName);
		}
		String[] userDefinedToolsHelp = {
				"user-defined:",
				"The following tools are valid, but not currently available:",
				"Some of the tools listed above only work in a windowed",
				"environment. If run in a terminal-only session, they will fail.",
		};
		expectedOutput.addAll(Arrays.asList(userDefinedToolsHelp));

		String option = "--tool-help";
		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput.toArray(new String[0]), runAndCaptureUsingInitRaw("difftool", option));
	}

	private RevCommit createUnstagedChanges() throws Exception {
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		git.add().addFilepattern(".").call();
		RevCommit commit = git.commit().setMessage("files a & b").call();
		writeTrashFile("a", "New Hello world a");
		writeTrashFile("b", "New Hello world b");
		return commit;
	}

	private RevCommit createStagedChanges() throws Exception {
		RevCommit commit = createUnstagedChanges();
		git.add().addFilepattern(".").call();
		return commit;
	}

	private List<DiffEntry> getRepositoryChanges(RevCommit commit)
			throws Exception {
		TreeWalk tw = new TreeWalk(db);
		tw.addTree(commit.getTree());
		FileTreeIterator modifiedTree = new FileTreeIterator(db);
		tw.addTree(modifiedTree);
		List<DiffEntry> changes = DiffEntry.scan(tw);
		return changes;
	}

	private String[] getExpectedDiffToolOutput(List<DiffEntry> changes) {
		String[] expectedToolOutput = new String[changes.size()];
		for (int i = 0; i < changes.size(); ++i) {
			DiffEntry change = changes.get(i);
			String newPath = change.getNewPath();
			String oldPath = change.getOldPath();
			String newIdName = change.getNewId().name();
			String oldIdName = change.getOldId().name();
			String expectedLine = "M\t" + newPath + " (" + newIdName + ")"
					+ "\t" + oldPath + " (" + oldIdName + ")";
			expectedToolOutput[i] = expectedLine;
		}
		return expectedToolOutput;
	}

	private static void assertArrayOfLinesEquals(String failMessage,
			String[] expected, String[] actual) {
		assertEquals(failMessage, toString(expected), toString(actual));
	}
}
