/*
 * Copyright (C) 2020-2021, Simeon Andreev <simeon.danailov.andreev@gmail.com> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.diffmergetool;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFFTOOL_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFF_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CMD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_GUITOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMPT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Test;

/**
 * Testing external diff tools.
 */
public class ExternalDiffToolTest extends ExternalToolTest {

	@Test(expected = ToolException.class)
	public void testUserToolWithError() throws Exception {
		String toolName = "customTool";

		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);

		DiffTools manager = new DiffTools(db);

		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;
		BooleanTriState trustExitCode = BooleanTriState.TRUE;

		manager.compare(db, local, remote, merged.getPath(), toolName, prompt,
				gui, trustExitCode);

		fail("Expected exception to be thrown due to external tool exiting with error code: "
				+ errorReturnCode);
	}

	@Test(expected = ToolException.class)
	public void testUserToolWithCommandNotFoundError() throws Exception {
		String toolName = "customTool";

		int errorReturnCode = 127; // command not found
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);

		DiffTools manager = new DiffTools(db);

		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;
		BooleanTriState trustExitCode = BooleanTriState.FALSE;

		manager.compare(db, local, remote, merged.getPath(), toolName, prompt,
				gui, trustExitCode);

		fail("Expected exception to be thrown due to external tool exiting with error code: "
				+ errorReturnCode);
	}

	@Test
	public void testToolNames() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getToolNames();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of external diff tool names",
				expectedToolNames, actualToolNames);
	}

	@Test
	public void testAllTools() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getAvailableTools().keySet();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		CommandLineDiffTool[] defaultTools = CommandLineDiffTool.values();
		for (CommandLineDiffTool defaultTool : defaultTools) {
			String toolName = defaultTool.name();
			expectedToolNames.add(toolName);
		}
		assertEquals("Incorrect set of external diff tools", expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testOverridePredefinedToolPath() {
		String toolName = CommandLineDiffTool.guiffy.name();
		String customToolPath = "/usr/bin/echo";

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
				"echo");
		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_PATH,
				customToolPath);

		DiffTools manager = new DiffTools(db);
		Map<String, ExternalDiffTool> tools = manager.getUserDefinedTools();
		ExternalDiffTool diffTool = tools.get(toolName);
		assertNotNull("Expected tool \"" + toolName + "\" to be user defined",
				diffTool);

		String toolPath = diffTool.getPath();
		assertEquals("Expected external diff tool to have an overriden path",
				customToolPath, toolPath);
	}

	@Test
	public void testUserDefinedTools() {
		FileBasedConfig config = db.getConfig();
		String customToolname = "customTool";
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolname,
				CONFIG_KEY_CMD, "echo");
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolname,
				CONFIG_KEY_PATH, "/usr/bin/echo");
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolname,
				CONFIG_KEY_PROMPT, String.valueOf(false));
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolname,
				CONFIG_KEY_GUITOOL, String.valueOf(false));
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolname,
				CONFIG_KEY_TRUST_EXIT_CODE, String.valueOf(false));
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getUserDefinedTools().keySet();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		expectedToolNames.add(customToolname);
		assertEquals("Incorrect set of external diff tools", expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testNotAvailableTools() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getNotAvailableTools().keySet();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of not available external diff tools",
				expectedToolNames, actualToolNames);
	}

	@Test
	public void testCompare() throws ToolException {
		String toolName = "customTool";

		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString(CONFIG_DIFF_SECTION, subsection, CONFIG_KEY_TOOL,
				toolName);

		String command = getEchoCommand();

		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);

		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;
		BooleanTriState trustExitCode = BooleanTriState.UNSET;

		DiffTools manager = new DiffTools(db);

		int expectedCompareResult = 0;
		ExecutionResult compareResult = manager.compare(db, local, remote,
				merged.getPath(), toolName, prompt, gui, trustExitCode);
		assertEquals("Incorrect compare result for external diff tool",
				expectedCompareResult, compareResult.getRc());
	}

	@Test
	public void testDefaultTool() throws Exception {
		String toolName = "customTool";
		String guiToolName = "customGuiTool";

		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString(CONFIG_DIFF_SECTION, subsection, CONFIG_KEY_TOOL,
				toolName);

		DiffTools manager = new DiffTools(db);
		BooleanTriState gui = BooleanTriState.UNSET;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				toolName, defaultToolName);

		gui = BooleanTriState.TRUE;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				"my_gui_tool", defaultGuiToolName);

		config.setString(CONFIG_DIFF_SECTION, subsection, CONFIG_KEY_GUITOOL,
				guiToolName);
		manager = new DiffTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff guitool",
				"my_gui_tool", defaultGuiToolName);
	}

	@Test
	public void testOverridePreDefinedToolPath() {
		String newToolPath = "/tmp/path/";

		CommandLineDiffTool[] defaultTools = CommandLineDiffTool.values();
		assertTrue("Expected to find pre-defined external diff tools",
				defaultTools.length > 0);

		CommandLineDiffTool overridenTool = defaultTools[0];
		String overridenToolName = overridenTool.name();
		String overridenToolPath = newToolPath + overridenToolName;
		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_DIFFTOOL_SECTION, overridenToolName,
				CONFIG_KEY_PATH, overridenToolPath);

		DiffTools manager = new DiffTools(db);
		Map<String, ExternalDiffTool> availableTools = manager
				.getAvailableTools();
		ExternalDiffTool externalDiffTool = availableTools
				.get(overridenToolName);
		String actualDiffToolPath = externalDiffTool.getPath();
		assertEquals(
				"Expected pre-defined external diff tool to have overriden path",
				overridenToolPath, actualDiffToolPath);
		String expectedDiffToolCommand = overridenToolPath + " "
				+ overridenTool.getParameters();
		String actualDiffToolCommand = externalDiffTool.getCommand();
		assertEquals(
				"Expected pre-defined external diff tool to have overriden command",
				expectedDiffToolCommand, actualDiffToolCommand);
	}

	@Test(expected = ToolException.class)
	public void testUndefinedTool() throws Exception {
		DiffTools manager = new DiffTools(db);

		String toolName = "undefined";
		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;
		BooleanTriState trustExitCode = BooleanTriState.UNSET;

		manager.compare(db, local, remote, merged.getPath(), toolName, prompt,
				gui, trustExitCode);
		fail("Expected exception to be thrown due to not defined external diff tool");
	}

	private String getEchoCommand() {
		return "(echo \"$LOCAL\" \"$REMOTE\") > "
				+ commandResult.getAbsolutePath();
	}
}
