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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFFTOOL_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFF_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CMD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMPT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TOOL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.internal.diffmergetool.CommandLineDiffTool;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
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

	private static final String TOOL_NAME = "some_tool";
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
		configureEchoTool(TOOL_NAME);
	}

	@Test(expected = Die.class)
	public void testNotDefinedTool() throws Exception {
		createUnstagedChanges();

		runAndCaptureUsingInitRaw("difftool", "--tool", "undefined");
		fail("Expected exception when trying to run undefined tool");
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
							TOOL_NAME));
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
							"--trust-exit-code", option, TOOL_NAME));
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
							option, TOOL_NAME));
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
							option, "--tool", TOOL_NAME));
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
		String customToolHelpLine = TOOL_NAME + "." + CONFIG_KEY_CMD + " "
				+ getEchoCommand();
		expectedOutput.add("user-defined:");
		expectedOutput.add(customToolHelpLine);
		String[] userDefinedToolsHelp = {
				"The following tools are valid, but not currently available:",
				"Some of the tools listed above only work in a windowed",
				"environment. If run in a terminal-only session, they will fail.",
		};
		expectedOutput.addAll(Arrays.asList(userDefinedToolsHelp));

		String option = "--tool-help";
		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput.toArray(new String[0]), runAndCaptureUsingInitRaw("difftool", option));
	}

	private void configureEchoTool(String toolName) {
		StoredConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString(CONFIG_DIFF_SECTION, subsection, CONFIG_KEY_TOOL,
				toolName);

		String command = getEchoCommand();

		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);
		/*
		 * prevent prompts as we are running in tests and there is no user to
		 * interact with on the command line
		 */
		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_PROMPT,
				String.valueOf(false));
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
			String expectedLine = newPath;
			expectedToolOutput[i] = expectedLine;
		}
		return expectedToolOutput;
	}

	private static void assertArrayOfLinesEquals(String failMessage,
			String[] expected, String[] actual) {
		assertEquals(failMessage, toString(expected), toString(actual));
	}

	private static String getEchoCommand() {
		/*
		 * use 'MERGED' placeholder, as both 'LOCAL' and 'REMOTE' will be
		 * replaced with full paths to a temporary file during some of the tests
		 */
		return "(echo \"$MERGED\")";
	}
}
