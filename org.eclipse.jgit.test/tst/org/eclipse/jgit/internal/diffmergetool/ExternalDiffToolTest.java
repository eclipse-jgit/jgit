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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Test;

/**
 * Testing external diff tools.
 */
public class ExternalDiffToolTest extends ExternalToolTest {

	@Test(expected = ToolException.class)
	public void testUserToolWithError() throws Exception {
		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", command);

		DiffTools manager = new DiffTools(db);

		Map<String, ExternalDiffTool> tools = manager.getUserDefinedTools();
		ExternalDiffTool externalTool = tools.get("customTool");
		boolean trustExitCode = true;
		manager.compare(local, remote, externalTool, trustExitCode);
		fail("Expected exception to be thrown due to external tool exiting with error code");
	}

	@Test
	public void testAllTools() {
		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", "echo");

		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getAllToolNames();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		expectedToolNames.add("customTool");
		CommandLineDiffTool[] defaultTools = CommandLineDiffTool.values();
		for (CommandLineDiffTool defaultTool : defaultTools) {
			String toolName = defaultTool.name();
			expectedToolNames.add(toolName);
		}
		assertEquals("Incorrect set of external diff tools", expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testUserDefinedTool() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", command);

		DiffTools manager = new DiffTools(db);

		Map<String, ExternalDiffTool> tools = manager.getUserDefinedTools();
		ExternalDiffTool externalTool = tools.get("customTool");
		boolean trustExitCode = true;
		manager.compare(local, remote, externalTool, trustExitCode);

		assertEchoCommandHasCorrectOutput();
	}

	@Test
	public void testUserDefinedToolWithPrompt() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", command);

		DiffTools manager = new DiffTools(db);

		PromptHandler promptHandler = PromptHandler.acceptPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.compare(local, remote, Optional.of("customTool"),
				Optional.of(Boolean.TRUE), false, Optional.of(Boolean.TRUE),
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
		DiffTools manager = new DiffTools(db);

		PromptHandler promptHandler = PromptHandler.cancelPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		Optional<ExecutionResult> result = manager.compare(local, remote,
				Optional.empty(), Optional.of(Boolean.TRUE), false,
				Optional.of(Boolean.TRUE), promptHandler, noToolHandler);
		assertFalse("Expected no result if user cancels the operation",
				result.isPresent());
	}

	@Test
	public void testDefaultTool() throws Exception {
		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString("diff", subsection, "tool", "customTool");

		DiffTools manager = new DiffTools(db);
		boolean gui = false;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				"customTool", defaultToolName);

		gui = true;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				"customTool", defaultGuiToolName);

		config.setString("diff", subsection, "guitool", "customGuiTool");
		manager = new DiffTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff guitool",
				"customGuiTool", defaultGuiToolName);
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

		manager.compare(local, remote, Optional.empty(),
				Optional.of(Boolean.TRUE), false, Optional.of(Boolean.TRUE),
				promptHandler, noToolHandler);

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
			testRepository.git().add().addFilepattern(localFile.getName()).call();

			testRepository.git().add().addFilepattern(".gitattributes").call();

			testRepository.branch("master").commit().message("first commit")
					.create();

			DiffTools manager = new DiffTools(db);
			Optional<String> tool = manager.getExternalToolFromAttributes(localFile.getName());
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
		manager.compare(local, remote, tool, Optional.of(Boolean.TRUE), false,
				Optional.of(Boolean.TRUE), promptHandler, noToolHandler);
	}

	private String getEchoCommand() {
		return "(echo $LOCAL $REMOTE) > " + commandResult.getAbsolutePath();
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
