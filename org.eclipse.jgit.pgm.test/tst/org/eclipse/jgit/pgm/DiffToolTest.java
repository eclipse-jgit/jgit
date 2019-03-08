/*
 * Copyright (C) 2021-2022, Simeon Andreev <simeon.danailov.andreev@gmail.com> and others.
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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.internal.diffmergetool.CommandLineDiffTool;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Testing the {@code difftool} command.
 */
public class DiffToolTest extends ExternalToolTestCase {

	private static final String DIFF_TOOL = CONFIG_DIFFTOOL_SECTION;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		configureEchoTool(TOOL_NAME);
	}

	@Test(expected = Die.class)
	public void testNotDefinedTool() throws Exception {
		createUnstagedChanges();

		runAndCaptureUsingInitRaw(DIFF_TOOL, "--tool", "undefined");
		fail("Expected exception when trying to run undefined tool");
	}

	@Test
	public void testTool() throws Exception {
		RevCommit commit = createUnstagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedToolOutput(changes);

		String[] options = {
				"--tool",
				"-t",
		};

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput,
					runAndCaptureUsingInitRaw(DIFF_TOOL, option,
							TOOL_NAME));
		}
	}

	@Test
	public void testToolTrustExitCode() throws Exception {
		RevCommit commit = createUnstagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedToolOutput(changes);

		String[] options = { "--tool", "-t", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw(DIFF_TOOL,
							"--trust-exit-code", option, TOOL_NAME));
		}
	}

	@Test
	public void testToolNoGuiNoPromptNoTrustExitcode() throws Exception {
		RevCommit commit = createUnstagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedToolOutput(changes);

		String[] options = { "--tool", "-t", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw(DIFF_TOOL,
							"--no-gui", "--no-prompt", "--no-trust-exit-code",
							option, TOOL_NAME));
		}
	}

	@Test
	public void testToolCached() throws Exception {
		RevCommit commit = createStagedChanges();
		List<DiffEntry> changes = getRepositoryChanges(commit);
		String[] expectedOutput = getExpectedToolOutput(changes);

		String[] options = { "--cached", "--staged", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw(DIFF_TOOL,
							option, "--tool", TOOL_NAME));
		}
	}

	@Test
	public void testToolHelp() throws Exception {
		CommandLineDiffTool[] defaultTools = CommandLineDiffTool.values();
		List<String> expectedOutput = new ArrayList<>();
		expectedOutput.add(
				"'git difftool --tool=<tool>' may be set to one of the following:");
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
				expectedOutput.toArray(new String[0]),
				runAndCaptureUsingInitRaw(DIFF_TOOL, option));
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

	private String[] getExpectedToolOutput(List<DiffEntry> changes) {
		String[] expectedToolOutput = new String[changes.size()];
		for (int i = 0; i < changes.size(); ++i) {
			DiffEntry change = changes.get(i);
			String newPath = change.getNewPath();
			String expectedLine = newPath;
			expectedToolOutput[i] = expectedLine;
		}
		return expectedToolOutput;
	}
}
