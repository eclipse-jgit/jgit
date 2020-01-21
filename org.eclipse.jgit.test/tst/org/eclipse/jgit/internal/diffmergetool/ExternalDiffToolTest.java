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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Test;

/**
 * Testing external diff tools.
 */
public class ExternalDiffToolTest extends ExternalToolTestCase {

	@Test(expected = ToolException.class)
	public void testUserToolWithError() throws Exception {
		String toolName = "customTool";

		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
				command);

		invokeCompare(toolName);

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

		invokeCompare(toolName);
		fail("Expected exception to be thrown due to external tool exiting with error code: "
				+ errorReturnCode);
	}

	@Test
	public void testUserDefinedTool() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		String customToolName = "customTool";
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolName,
				CONFIG_KEY_CMD, command);

		DiffTools manager = new DiffTools(db);

		Map<String, ExternalDiffTool> tools = manager.getUserDefinedTools();
		ExternalDiffTool externalTool = tools.get(customToolName);
		boolean trustExitCode = true;
		manager.compare(local, remote, externalTool, trustExitCode);

		assertEchoCommandHasCorrectOutput();
	}

	@Test
	public void testUserDefinedToolWithPrompt() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		String customToolName = "customTool";
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolName,
				CONFIG_KEY_CMD, command);

		DiffTools manager = new DiffTools(db);

		PromptHandler promptHandler = PromptHandler.acceptPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.compare(local, remote, Optional.of(customToolName),
				BooleanTriState.TRUE, false, BooleanTriState.TRUE,
				promptHandler, noToolHandler);

		assertEchoCommandHasCorrectOutput();

		List<String> actualToolPrompts = promptHandler.toolPrompts;
		List<String> expectedToolPrompts = Arrays.asList("customTool");
		assertEquals("Expected a user prompt for custom tool call",
				expectedToolPrompts, actualToolPrompts);

		assertEquals("Expected to no informing about missing tools",
				Collections.EMPTY_LIST, noToolHandler.missingTools);
	}

	@Test
	public void testUserDefinedToolWithCancelledPrompt() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		String customToolName = "customTool";
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolName,
				CONFIG_KEY_CMD, command);

		DiffTools manager = new DiffTools(db);

		PromptHandler promptHandler = PromptHandler.cancelPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		Optional<ExecutionResult> result = manager.compare(local, remote,
				Optional.of(customToolName), BooleanTriState.TRUE, false,
				BooleanTriState.TRUE, promptHandler, noToolHandler);
		assertFalse("Expected no result if user cancels the operation",
				result.isPresent());
	}

	@Test
	public void testAllTools() {
		FileBasedConfig config = db.getConfig();
		String customToolName = "customTool";
		config.setString(CONFIG_DIFFTOOL_SECTION, customToolName,
				CONFIG_KEY_CMD, "echo");

		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getAllToolNames();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		expectedToolNames.add(customToolName);
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
		Optional<ExecutionResult> result = invokeCompare(toolName);
		assertTrue("Expected external diff tool result to be available",
				result.isPresent());
		int expectedCompareResult = 0;
		assertEquals("Incorrect compare result for external diff tool",
				expectedCompareResult, result.get().getRc());
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
		boolean gui = false;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				toolName, defaultToolName);

		gui = true;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected default gui difftool to be the default tool if no gui tool is set",
				toolName, defaultGuiToolName);

		config.setString(CONFIG_DIFF_SECTION, subsection, CONFIG_KEY_GUITOOL,
				guiToolName);
		manager = new DiffTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff guitool",
				guiToolName, defaultGuiToolName);
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
				.getPredefinedTools(true);
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
		String toolName = "undefined";
		invokeCompare(toolName);
		fail("Expected exception to be thrown due to not defined external diff tool");
	}

	@Test
	public void testDefaultToolExecutionWithPrompt() throws Exception {
		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString("diff", subsection, "tool", "customTool");

		String command = getEchoCommand();

		config.setString("difftool", "customTool", "cmd", command);

		DiffTools manager = new DiffTools(db);

		PromptHandler promptHandler = PromptHandler.acceptPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.compare(local, remote, Optional.empty(), BooleanTriState.TRUE,
				false, BooleanTriState.TRUE, promptHandler, noToolHandler);

		assertEchoCommandHasCorrectOutput();
	}

	@Test
	public void testNoDefaultToolName() {
		DiffTools manager = new DiffTools(db);
		boolean gui = false;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertNull("Expected no default tool when none is configured",
				defaultToolName);

		gui = true;
		defaultToolName = manager.getDefaultToolName(gui);
		assertNull("Expected no default tool when none is configured",
				defaultToolName);
	}

	@Test
	public void testExternalToolInGitAttributes() throws Exception {
		String content = "attributes:\n*.txt 		difftool=customTool";
		File gitattributes = writeTrashFile(".gitattributes", content);
		gitattributes.deleteOnExit();
		try (TestRepository<Repository> testRepository = new TestRepository<>(
				db)) {
			FileBasedConfig config = db.getConfig();
			config.setString("difftool", "customTool", "cmd", "echo");
			testRepository.git().add().addFilepattern(localFile.getName())
					.call();

			testRepository.git().add().addFilepattern(".gitattributes").call();

			testRepository.branch("master").commit().message("first commit")
					.create();

			DiffTools manager = new DiffTools(db);
			Optional<String> tool = manager
					.getExternalToolFromAttributes(localFile.getName());
			assertTrue("Failed to find user defined tool", tool.isPresent());
			assertEquals("Failed to find user defined tool", "customTool",
					tool.get());
		} finally {
			Files.delete(gitattributes.toPath());
		}
	}

	@Test
	public void testNotExternalToolInGitAttributes() throws Exception {
		String content = "";
		File gitattributes = writeTrashFile(".gitattributes", content);
		gitattributes.deleteOnExit();
		try (TestRepository<Repository> testRepository = new TestRepository<>(
				db)) {
			FileBasedConfig config = db.getConfig();
			config.setString("difftool", "customTool", "cmd", "echo");
			testRepository.git().add().addFilepattern(localFile.getName())
					.call();

			testRepository.git().add().addFilepattern(".gitattributes").call();

			testRepository.branch("master").commit().message("first commit")
					.create();

			DiffTools manager = new DiffTools(db);
			Optional<String> tool = manager
					.getExternalToolFromAttributes(localFile.getName());
			assertFalse(
					"Expected no external tool if no default tool is specified in .gitattributes",
					tool.isPresent());
		} finally {
			Files.delete(gitattributes.toPath());
		}
	}

	@Test(expected = ToolException.class)
	public void testNullTool() throws Exception {
		DiffTools manager = new DiffTools(db);

		boolean trustExitCode = true;
		ExternalDiffTool tool = null;
		manager.compare(local, remote, tool, trustExitCode);
	}

	@Test(expected = ToolException.class)
	public void testNullToolWithPrompt() throws Exception {
		DiffTools manager = new DiffTools(db);

		PromptHandler promptHandler = PromptHandler.cancelPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		Optional<String> tool = null;
		manager.compare(local, remote, tool, BooleanTriState.TRUE, false,
				BooleanTriState.TRUE, promptHandler, noToolHandler);
	}

	private Optional<ExecutionResult> invokeCompare(String toolName)
			throws ToolException {
		DiffTools manager = new DiffTools(db);

		BooleanTriState prompt = BooleanTriState.UNSET;
		boolean gui = false;
		BooleanTriState trustExitCode = BooleanTriState.TRUE;
		PromptHandler promptHandler = PromptHandler.acceptPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		Optional<ExecutionResult> result = manager.compare(local, remote,
				Optional.of(toolName), prompt, gui, trustExitCode,
				promptHandler, noToolHandler);
		return result;
	}

	private String getEchoCommand() {
		return "(echo \"$LOCAL\" \"$REMOTE\") > "
				+ commandResult.getAbsolutePath();
	}

	private void assertEchoCommandHasCorrectOutput() throws IOException {
		List<String> actualLines = Files.readAllLines(commandResult.toPath());
		String actualContent = String.join(System.lineSeparator(), actualLines);
		actualLines = Arrays.asList(actualContent.split(" "));
		List<String> expectedLines = Arrays.asList(localFile.getAbsolutePath(),
				remoteFile.getAbsolutePath());
		assertEquals("Dummy test tool called with unexpected arguments",
				expectedLines, actualLines);
	}
}
