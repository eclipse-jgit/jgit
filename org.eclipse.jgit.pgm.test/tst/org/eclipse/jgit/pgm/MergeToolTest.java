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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.diffmergetool.ExternalMergeTool;
import org.eclipse.jgit.internal.diffmergetool.MergeTools;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * Testing the {@code mergetool} command.
 */
public class MergeToolTest extends ToolTestCase {

	private static final String MERGE_TOOL = CONFIG_MERGETOOL_SECTION;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		configureEchoTool(TOOL_NAME);
	}

	@Test
	public void testAbortMerge() throws Exception {
		String[] inputLines = {
				"y", // start tool for merge resolution
				"n", // don't accept merge tool result
				"n", // don't continue resolution
		};
		String[] conflictingFilenames = createMergeConflict();
		int abortIndex = 1;
		String[] expectedOutput = getExpectedAbortMergeOutput(
				conflictingFilenames,
				abortIndex);

		String option = "--tool";

		InputStream inputStream = createInputStream(inputLines);
		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput, runAndCaptureUsingInitRaw(inputStream,
						MERGE_TOOL, "--prompt", option, TOOL_NAME));
	}

	@Test
	public void testAbortLaunch() throws Exception {
		String[] inputLines = {
				"n", // abort merge tool launch
		};
		String[] conflictingFilenames = createMergeConflict();
		String[] expectedOutput = getExpectedAbortLaunchOutput(
				conflictingFilenames);

		String option = "--tool";

		InputStream inputStream = createInputStream(inputLines);
		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput, runAndCaptureUsingInitRaw(inputStream,
						MERGE_TOOL, "--prompt", option, TOOL_NAME));
	}

	@Test
	public void testMergeConflict() throws Exception {
		String[] inputLines = {
				"y", // start tool for merge resolution
				"y", // accept merge result as successful
				"y", // start tool for merge resolution
				"y", // accept merge result as successful
		};
		String[] conflictingFilenames = createMergeConflict();
		String[] expectedOutput = getExpectedMergeConflictOutput(
				conflictingFilenames);

		String option = "--tool";

		InputStream inputStream = createInputStream(inputLines);
		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput, runAndCaptureUsingInitRaw(inputStream,
						MERGE_TOOL, "--prompt", option, TOOL_NAME));
	}

	@Test
	public void testDeletedConflict() throws Exception {
		String[] inputLines = {
				"d", // choose delete option to resolve conflict
				"m", // choose merge option to resolve conflict
		};
		String[] conflictingFilenames = createDeletedConflict();
		String[] expectedOutput = getExpectedDeletedConflictOutput(
				conflictingFilenames);

		String option = "--tool";

		InputStream inputStream = createInputStream(inputLines);
		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput, runAndCaptureUsingInitRaw(inputStream,
						MERGE_TOOL, "--prompt", option, TOOL_NAME));
	}

	@Test
	public void testNoConflict() throws Exception {
		createStagedChanges();
		String[] expectedOutput = { "No files need merging" };

		String[] options = { "--tool", "-t", };

		for (String option : options) {
			assertArrayOfLinesEquals("Incorrect output for option: " + option,
					expectedOutput,
					runAndCaptureUsingInitRaw(MERGE_TOOL, option, TOOL_NAME));
		}
	}

	@Test
	public void testMergeConflictNoPrompt() throws Exception {
		String[] conflictingFilenames = createMergeConflict();
		String[] expectedOutput = getExpectedMergeConflictOutputNoPrompt(
				conflictingFilenames);

		String option = "--tool";

		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput,
				runAndCaptureUsingInitRaw(MERGE_TOOL, option, TOOL_NAME));
	}

	@Test
	public void testMergeConflictNoGuiNoPrompt() throws Exception {
		String[] conflictingFilenames = createMergeConflict();
		String[] expectedOutput = getExpectedMergeConflictOutputNoPrompt(
				conflictingFilenames);

		String option = "--tool";

		assertArrayOfLinesEquals("Incorrect output for option: " + option,
				expectedOutput, runAndCaptureUsingInitRaw(MERGE_TOOL,
						"--no-gui", "--no-prompt", option, TOOL_NAME));
	}

	@Test
	public void testToolHelp() throws Exception {
		List<String> expectedOutput = new ArrayList<>();

		MergeTools diffTools = new MergeTools(db);
		Map<String, ExternalMergeTool> predefinedTools = diffTools
				.getPredefinedTools(true);
		List<ExternalMergeTool> availableTools = new ArrayList<>();
		List<ExternalMergeTool> notAvailableTools = new ArrayList<>();
		for (ExternalMergeTool tool : predefinedTools.values()) {
			if (tool.isAvailable()) {
				availableTools.add(tool);
			} else {
				notAvailableTools.add(tool);
			}
		}

		expectedOutput.add(
				"'git mergetool --tool=<tool>' may be set to one of the following:");
		for (ExternalMergeTool tool : availableTools) {
			String toolName = tool.getName();
			expectedOutput.add(toolName);
		}
		String customToolHelpLine = TOOL_NAME + "." + CONFIG_KEY_CMD + " "
				+ getEchoCommand();
		expectedOutput.add("user-defined:");
		expectedOutput.add(customToolHelpLine);
		expectedOutput.add(
				"The following tools are valid, but not currently available:");
		for (ExternalMergeTool tool : notAvailableTools) {
			String toolName = tool.getName();
			expectedOutput.add(toolName);
		}
		String[] userDefinedToolsHelp = {
				"Some of the tools listed above only work in a windowed",
				"environment. If run in a terminal-only session, they will fail.", };
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

	private static String[] getExpectedMergeConflictOutputNoPrompt(
			String[] conflictFilenames) {
		List<String> expected = new ArrayList<>();
		expected.add("Merging:");
		for (String conflictFilename : conflictFilenames) {
			expected.add(conflictFilename);
		}
		for (String conflictFilename : conflictFilenames) {
			expected.add("Normal merge conflict for '" + conflictFilename
					+ "':");
			expected.add("{local}: modified file");
			expected.add("{remote}: modified file");
			expected.add(conflictFilename);
			expected.add(conflictFilename + " seems unchanged.");
		}
		return expected.toArray(new String[0]);
	}

	private static String[] getExpectedAbortLaunchOutput(
			String[] conflictFilenames) {
		List<String> expected = new ArrayList<>();
		expected.add("Merging:");
		for (String conflictFilename : conflictFilenames) {
			expected.add(conflictFilename);
		}
		if (conflictFilenames.length > 1) {
			String conflictFilename = conflictFilenames[0];
			expected.add(
					"Normal merge conflict for '" + conflictFilename + "':");
			expected.add("{local}: modified file");
			expected.add("{remote}: modified file");
			expected.add("Hit return to start merge resolution tool ("
					+ TOOL_NAME + "):");
		}
		return expected.toArray(new String[0]);
	}

	private static String[] getExpectedAbortMergeOutput(
			String[] conflictFilenames, int abortIndex) {
		List<String> expected = new ArrayList<>();
		expected.add("Merging:");
		for (String conflictFilename : conflictFilenames) {
			expected.add(conflictFilename);
		}
		for (int i = 0; i < conflictFilenames.length; ++i) {
			if (i == abortIndex) {
				break;
			}

			String conflictFilename = conflictFilenames[i];
			expected.add(
					"Normal merge conflict for '" + conflictFilename + "':");
			expected.add("{local}: modified file");
			expected.add("{remote}: modified file");
			expected.add("Hit return to start merge resolution tool ("
					+ TOOL_NAME + "): " + conflictFilename);
			expected.add(conflictFilename + " seems unchanged.");
			expected.add("Was the merge successful [y/n]?");
			if (i < conflictFilenames.length - 1) {
				expected.add(
						"\tContinue merging other unresolved paths [y/n]?");
			}
		}
		return expected.toArray(new String[0]);
	}

	private static String[] getExpectedMergeConflictOutput(
			String[] conflictFilenames) {
		List<String> expected = new ArrayList<>();
		expected.add("Merging:");
		for (String conflictFilename : conflictFilenames) {
			expected.add(conflictFilename);
		}
		for (int i = 0; i < conflictFilenames.length; ++i) {
			String conflictFilename = conflictFilenames[i];
			expected.add("Normal merge conflict for '" + conflictFilename
					+ "':");
			expected.add("{local}: modified file");
			expected.add("{remote}: modified file");
			expected.add("Hit return to start merge resolution tool ("
					+ TOOL_NAME + "): " + conflictFilename);
			expected.add(conflictFilename + " seems unchanged.");
			expected.add("Was the merge successful [y/n]?");
			if (i < conflictFilenames.length - 1) {
				// expected.add(
				// "\tContinue merging other unresolved paths [y/n]?");
			}
		}
		return expected.toArray(new String[0]);
	}

	private static String[] getExpectedDeletedConflictOutput(
			String[] conflictFilenames) {
		List<String> expected = new ArrayList<>();
		expected.add("Merging:");
		for (String mergeConflictFilename : conflictFilenames) {
			expected.add(mergeConflictFilename);
		}
		for (int i = 0; i < conflictFilenames.length; ++i) {
			String conflictFilename = conflictFilenames[i];
			expected.add(conflictFilename + " seems unchanged.");
			expected.add("{local}: deleted");
			expected.add("{remote}: modified file");
			expected.add("Use (m)odified or (d)eleted file, or (a)bort?");
		}
		return expected.toArray(new String[0]);
	}
}
