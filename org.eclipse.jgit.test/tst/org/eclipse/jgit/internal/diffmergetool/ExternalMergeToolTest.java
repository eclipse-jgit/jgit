/*
 * Copyright (C) 2020-2022, Simeon Andreev <simeon.danailov.andreev@gmail.com> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.diffmergetool;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CMD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_GUITOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMPT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGETOOL_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGE_SECTION;
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
 * Testing external merge tools.
 */
public class ExternalMergeToolTest extends ExternalToolTestCase {

	@Test(expected = ToolException.class)
	public void testUserToolWithError() throws Exception {
		String toolName = "customTool";

		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);
		config.setString(CONFIG_MERGETOOL_SECTION, toolName,
				CONFIG_KEY_TRUST_EXIT_CODE, String.valueOf(Boolean.TRUE));

		MergeTools manager = new MergeTools(db);

		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;

		manager.merge(local, remote, merged, base, null, toolName, prompt, gui);

		fail("Expected exception to be thrown due to external tool exiting with error code: "
				+ errorReturnCode);
	}

	@Test(expected = ToolException.class)
	public void testUserToolWithCommandNotFoundError() throws Exception {
		String toolName = "customTool";

		int errorReturnCode = 127; // command not found
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);

		MergeTools manager = new MergeTools(db);

		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;

		manager.merge(local, remote, merged, base, null, toolName, prompt, gui);

		fail("Expected exception to be thrown due to external tool exiting with error code: "
				+ errorReturnCode);
	}

	@Test
	public void testToolNames() {
		MergeTools manager = new MergeTools(db);
		Set<String> actualToolNames = manager.getToolNames();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of external merge tool names",
				expectedToolNames, actualToolNames);
	}

	@Test
	public void testAllTools() {
		MergeTools manager = new MergeTools(db);
		Set<String> actualToolNames = manager.getPredefinedTools(true).keySet();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		CommandLineMergeTool[] defaultTools = CommandLineMergeTool.values();
		for (CommandLineMergeTool defaultTool : defaultTools) {
			String toolName = defaultTool.name();
			expectedToolNames.add(toolName);
		}
		assertEquals("Incorrect set of external merge tools", expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testOverridePredefinedToolPath() {
		String toolName = CommandLineMergeTool.guiffy.name();
		String customToolPath = "/usr/bin/echo";

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_CMD,
				"echo");
		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_PATH,
				customToolPath);

		MergeTools manager = new MergeTools(db);
		Map<String, ExternalMergeTool> tools = manager.getUserDefinedTools();
		ExternalMergeTool mergeTool = tools.get(toolName);
		assertNotNull("Expected tool \"" + toolName + "\" to be user defined",
				mergeTool);

		String toolPath = mergeTool.getPath();
		assertEquals("Expected external merge tool to have an overriden path",
				customToolPath, toolPath);
	}

	@Test
	public void testUserDefinedTools() {
		FileBasedConfig config = db.getConfig();
		String customToolname = "customTool";
		config.setString(CONFIG_MERGETOOL_SECTION, customToolname,
				CONFIG_KEY_CMD, "echo");
		config.setString(CONFIG_MERGETOOL_SECTION, customToolname,
				CONFIG_KEY_PATH, "/usr/bin/echo");
		config.setString(CONFIG_MERGETOOL_SECTION, customToolname,
				CONFIG_KEY_PROMPT, String.valueOf(false));
		config.setString(CONFIG_MERGETOOL_SECTION, customToolname,
				CONFIG_KEY_GUITOOL, String.valueOf(false));
		config.setString(CONFIG_MERGETOOL_SECTION, customToolname,
				CONFIG_KEY_TRUST_EXIT_CODE, String.valueOf(false));
		MergeTools manager = new MergeTools(db);
		Set<String> actualToolNames = manager.getUserDefinedTools().keySet();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		expectedToolNames.add(customToolname);
		assertEquals("Incorrect set of external merge tools", expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testCompare() throws ToolException {
		String toolName = "customTool";

		FileBasedConfig config = db.getConfig();
		// the default merge tool is configured without a subsection
		String subsection = null;
		config.setString(CONFIG_MERGE_SECTION, subsection, CONFIG_KEY_TOOL,
				toolName);

		String command = getEchoCommand();

		config.setString(CONFIG_MERGETOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);

		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;

		MergeTools manager = new MergeTools(db);

		int expectedCompareResult = 0;
		ExecutionResult compareResult = manager.merge(local, remote, merged,
				base, null, toolName, prompt, gui);
		assertEquals("Incorrect compare result for external merge tool",
				expectedCompareResult, compareResult.getRc());
	}

	@Test
	public void testDefaultTool() throws Exception {
		String toolName = "customTool";
		String guiToolName = "customGuiTool";

		FileBasedConfig config = db.getConfig();
		// the default merge tool is configured without a subsection
		String subsection = null;
		config.setString(CONFIG_MERGE_SECTION, subsection, CONFIG_KEY_TOOL,
				toolName);

		MergeTools manager = new MergeTools(db);
		BooleanTriState gui = BooleanTriState.UNSET;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured mergetool to be the default external merge tool",
				toolName, defaultToolName);

		gui = BooleanTriState.TRUE;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured mergetool to be the default external merge tool",
				"my_gui_tool", defaultGuiToolName);

		config.setString(CONFIG_MERGE_SECTION, subsection, CONFIG_KEY_GUITOOL,
				guiToolName);
		manager = new MergeTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured mergetool to be the default external merge guitool",
				"my_gui_tool", defaultGuiToolName);
	}

	@Test
	public void testOverridePreDefinedToolPath() {
		String newToolPath = "/tmp/path/";

		CommandLineMergeTool[] defaultTools = CommandLineMergeTool.values();
		assertTrue("Expected to find pre-defined external merge tools",
				defaultTools.length > 0);

		CommandLineMergeTool overridenTool = defaultTools[0];
		String overridenToolName = overridenTool.name();
		String overridenToolPath = newToolPath + overridenToolName;
		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_MERGETOOL_SECTION, overridenToolName,
				CONFIG_KEY_PATH, overridenToolPath);

		MergeTools manager = new MergeTools(db);
		Map<String, ExternalMergeTool> availableTools = manager
				.getPredefinedTools(true);
		ExternalMergeTool externalMergeTool = availableTools
				.get(overridenToolName);
		String actualMergeToolPath = externalMergeTool.getPath();
		assertEquals(
				"Expected pre-defined external merge tool to have overriden path",
				overridenToolPath, actualMergeToolPath);
		boolean withBase = true;
		String expectedMergeToolCommand = overridenToolPath + " "
				+ overridenTool.getParameters(withBase);
		String actualMergeToolCommand = externalMergeTool.getCommand();
		assertEquals(
				"Expected pre-defined external merge tool to have overriden command",
				expectedMergeToolCommand, actualMergeToolCommand);
	}

	@Test(expected = ToolException.class)
	public void testUndefinedTool() throws Exception {
		MergeTools manager = new MergeTools(db);

		String toolName = "undefined";
		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;

		manager.merge(local, remote, merged, base, null, toolName, prompt, gui);
		fail("Expected exception to be thrown due to not defined external merge tool");
	}

	private String getEchoCommand() {
		return "(echo \"$LOCAL\" \"$REMOTE\") > "
				+ commandResult.getAbsolutePath();
	}
}
