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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CMD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMPT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGETOOL_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGE_SECTION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.diffmergetool.CommandLineMergeTool;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Testing the {@code mergetool} command.
 */
public class MergeToolTest extends ExternalToolTestCase {

	private static final String MERGE_TOOL = CONFIG_MERGETOOL_SECTION;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		configureEchoTool(TOOL_NAME);
	}

	@Test
	public void testTool() throws Exception {
		createMergeConflict();
		String[] expectedOutput = getExpectedToolOutput();

		String[] options = {
				"--tool",
				"-t",
		};

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput,
					runAndCaptureUsingInitRaw(MERGE_TOOL, option,
							TOOL_NAME));
		}
	}

	@Test
	public void testToolNoGuiNoPrompt() throws Exception {
		createMergeConflict();
		String[] expectedOutput = getExpectedToolOutput();

		String[] options = { "--tool", "-t", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput, runAndCaptureUsingInitRaw(MERGE_TOOL,
							"--no-gui", "--no-prompt", option, TOOL_NAME));
		}
	}

	@Test
	public void testToolHelp() throws Exception {
		CommandLineMergeTool[] defaultTools = CommandLineMergeTool.values();
		List<String> expectedOutput = new ArrayList<>();
		expectedOutput.add(
				"'git mergetool --tool=<tool>' may be set to one of the following:");
		for (CommandLineMergeTool defaultTool : defaultTools) {
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
				runAndCaptureUsingInitRaw(MERGE_TOOL, option));
	}

	private void configureEchoTool(String toolName) {
		StoredConfig config = db.getConfig();
		// the default merge tool is configured without a subsection
		String subsection = null;
		config.setString(CONFIG_MERGE_SECTION, subsection, CONFIG_KEY_TOOL,
				toolName);

		String command = getEchoCommand();

		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);
		/*
		 * prevent prompts as we are running in tests and there is no user to
		 * interact with on the command line
		 */
		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_PROMPT,
				String.valueOf(false));
	}

	private String[] getExpectedToolOutput() {
		String[] mergeConflictFilenames = { "a", "b", };
		List<String> expected = new ArrayList<>();
		expected.add("Merging:");
		for (String mergeConflictFilename : mergeConflictFilenames) {
			expected.add(mergeConflictFilename);
		}
		for (int i = 0; i < mergeConflictFilenames.length; ++i) {
			String mergeConflictFilename = mergeConflictFilenames[i];
			expected.add("Normal merge conflict for '"
					+ mergeConflictFilename + "':");
			expected.add("{local}: modified file");
			expected.add("{remote}: modified file");
			if (i < mergeConflictFilenames.length - 1) {
				expected.add("Continue merging other unresolved paths [y/n]?");
			}
		}
		return expected.toArray(new String[0]);
	}
}
