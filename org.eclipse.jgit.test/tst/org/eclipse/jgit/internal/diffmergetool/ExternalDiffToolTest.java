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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import org.junit.jupiter.api.Test;

/**
 * Testing external diff tools.
 */
public class ExternalDiffToolTest extends ExternalToolTestCase {

	@Test
	void testUserToolWithError() throws Exception {
		assertThrows(ToolException.class, () -> {
			String toolName = "customTool";

			int errorReturnCode = 1;
			String command = "exit " + errorReturnCode;

			FileBasedConfig config = db.getConfig();
			config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
					command);

			invokeCompare(toolName);

			fail("Expected exception to be thrown due to external tool exiting with error code: "
					+ errorReturnCode);
		});
	}

	@Test
	void testUserToolWithCommandNotFoundError() throws Exception {
		assertThrows(ToolException.class, () -> {
			String toolName = "customTool";

			int errorReturnCode = 127; // command not found
			String command = "exit " + errorReturnCode;

			FileBasedConfig config = db.getConfig();
			config.setString(CONFIG_DIFFTOOL_SECTION, toolName, CONFIG_KEY_CMD,
					command);

			invokeCompare(toolName);
			fail("Expected exception to be thrown due to external tool exiting with error code: "
					+ errorReturnCode);
		});
	}

	@Test
	void testUserDefinedTool() throws Exception {
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
	void testUserDefinedToolWithPrompt() throws Exception {
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
		assertEquals(expectedToolPrompts, actualToolPrompts, "Expected a user prompt for custom tool call");

		assertEquals(Collections.EMPTY_LIST, noToolHandler.missingTools, "Expected to no informing about missing tools");
	}

	@Test
	void testUserDefinedToolWithCancelledPrompt() throws Exception {
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
		assertFalse(result.isPresent(),
				"Expected no result if user cancels the operation");
	}

	@Test
	void testAllTools() {
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
		assertEquals(expectedToolNames,
				actualToolNames,
				"Incorrect set of external diff tools");
	}

	@Test
	void testOverridePredefinedToolPath() {
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
		assertNotNull(diffTool,
				"Expected tool \"" + toolName + "\" to be user defined");

		String toolPath = diffTool.getPath();
		assertEquals(customToolPath, toolPath, "Expected external diff tool to have an overriden path");
	}

	@Test
	void testUserDefinedTools() {
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
		assertEquals(expectedToolNames,
				actualToolNames,
				"Incorrect set of external diff tools");
	}

	@Test
	void testCompare() throws ToolException {
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
		assertTrue(result.isPresent(),
				"Expected external diff tool result to be available");
		int expectedCompareResult = 0;
		assertEquals(expectedCompareResult, result.get().getRc(), "Incorrect compare result for external diff tool");
	}

	@Test
	void testDefaultTool() throws Exception {
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
				toolName, defaultToolName, "Expected configured difftool to be the default external diff tool");

		gui = true;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				toolName, defaultGuiToolName, "Expected default gui difftool to be the default tool if no gui tool is set");

		config.setString(CONFIG_DIFF_SECTION, subsection, CONFIG_KEY_GUITOOL,
				guiToolName);
		manager = new DiffTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				guiToolName, defaultGuiToolName, "Expected configured difftool to be the default external diff guitool");
	}

	@Test
	void testOverridePreDefinedToolPath() {
		String newToolPath = "/tmp/path/";

		CommandLineDiffTool[] defaultTools = CommandLineDiffTool.values();
		assertTrue(defaultTools.length > 0,
				"Expected to find pre-defined external diff tools");

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
				overridenToolPath, actualDiffToolPath, "Expected pre-defined external diff tool to have overriden path");
		String expectedDiffToolCommand = overridenToolPath + " "
				+ overridenTool.getParameters();
		String actualDiffToolCommand = externalDiffTool.getCommand();
		assertEquals(
				expectedDiffToolCommand, actualDiffToolCommand, "Expected pre-defined external diff tool to have overriden command");
	}

	@Test
	void testUndefinedTool() throws Exception {
		assertThrows(ToolException.class, () -> {
			String toolName = "undefined";
			invokeCompare(toolName);
			fail("Expected exception to be thrown due to not defined external diff tool");
		});
	}

	@Test
	void testDefaultToolExecutionWithPrompt() throws Exception {
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
	void testNoDefaultToolName() {
		DiffTools manager = new DiffTools(db);
		boolean gui = false;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertNull(defaultToolName,
				"Expected no default tool when none is configured");

		gui = true;
		defaultToolName = manager.getDefaultToolName(gui);
		assertNull(defaultToolName,
				"Expected no default tool when none is configured");
	}

	@Test
	void testExternalToolInGitAttributes() throws Exception {
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
			assertTrue(tool.isPresent(), "Failed to find user defined tool");
			assertEquals("customTool",
					tool.get(),
					"Failed to find user defined tool");
		} finally {
			Files.delete(gitattributes.toPath());
		}
	}

	@Test
	void testNotExternalToolInGitAttributes() throws Exception {
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
					tool.isPresent(),
					"Expected no external tool if no default tool is specified in .gitattributes");
		} finally {
			Files.delete(gitattributes.toPath());
		}
	}

	@Test
	void testNullTool() throws Exception {
		assertThrows(ToolException.class, () -> {
			DiffTools manager = new DiffTools(db);

			boolean trustExitCode = true;
			ExternalDiffTool tool = null;
			manager.compare(local, remote, tool, trustExitCode);
		});
	}

	@Test
	void testNullToolWithPrompt() throws Exception {
		assertThrows(ToolException.class, () -> {
			DiffTools manager = new DiffTools(db);

			PromptHandler promptHandler = PromptHandler.cancelPrompt();
			MissingToolHandler noToolHandler = new MissingToolHandler();

			Optional<String> tool = null;
			manager.compare(local, remote, tool, BooleanTriState.TRUE, false,
					BooleanTriState.TRUE, promptHandler, noToolHandler);
		});
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
		assertEquals(expectedLines, actualLines, "Dummy test tool called with unexpected arguments");
	}
}
