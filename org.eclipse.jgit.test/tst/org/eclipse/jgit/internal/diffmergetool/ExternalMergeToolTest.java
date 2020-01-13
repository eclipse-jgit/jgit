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
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Test;

/**
 * Testing external diff tools.
 */
public class ExternalMergeToolTest extends ExternalToolTest {


	@Test(expected = ToolException.class)
	public void testUserToolWithError() throws Exception {
		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", command);

		MergeTools manager = new MergeTools(db);
		Map<String, ExternalMergeTool> tools = manager.getUserDefinedTools();
		ExternalMergeTool externalTool = tools.get("customTool");
		manager.merge(local, remote, merged, null, null, externalTool);
		fail("Expected exception to be thrown due to external tool exiting with error code");
	}

	@Test
	public void testAllTools() {
		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", "echo");

		MergeTools manager = new MergeTools(db);
		Set<String> actualToolNames = manager.getAllToolNames();
		Set<String> expectedToolNames = new LinkedHashSet<>();
		expectedToolNames.add("customTool");
		CommandLineMergeTool[] defaultTools = CommandLineMergeTool.values();
		for (CommandLineMergeTool defaultTool : defaultTools) {
			String toolName = defaultTool.name();
			expectedToolNames.add(toolName);
		}
		assertEquals("Incorrect set of external merge tools", expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testKdiff3() throws Exception {
		assumePosixPlatform();

		CommandLineMergeTool autoMergingTool = CommandLineMergeTool.kdiff3;
		assumeMergeToolIsAvailable(autoMergingTool);

		CommandLineMergeTool tool = autoMergingTool;
		PreDefinedMergeTool externalTool = new PreDefinedMergeTool(
				tool.name(), tool.getPath(), tool.getParameters(true),
				tool.getParameters(false),
				BooleanOption.toConfigured(tool.isExitCodeTrustable()));

				MergeTools manager = new MergeTools(db);
		ExecutionResult result = manager.merge(local, remote, merged,
				null, null, externalTool);
		assertEquals("Expected merge tool to succeed", 0,
				result.getRc());

		List<String> actualLines = Files
				.readAllLines(mergedFile.toPath());
		String actualMergeResult = String.join(System.lineSeparator(),
				actualLines);
		String expectedMergeResult = DEFAULT_CONTENT;
		assertEquals(
				"Failed to merge equal local and remote versions with pre-defined tool: "
						+ tool.getPath(),
				expectedMergeResult, actualMergeResult);
	}

	@Test
	public void testUserDefinedTool() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", command);

		MergeTools manager = new MergeTools(db);
		Map<String, ExternalMergeTool> tools = manager.getUserDefinedTools();
		ExternalMergeTool externalTool = tools.get("customTool");
		manager.merge(local, remote, merged, base, null, externalTool);

		assertEchoCommandHasCorrectOutput();
	}

	@Test
	public void testUserDefinedToolWithPrompt() throws Exception {
		String command = getEchoCommand();

		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", command);

		MergeTools manager = new MergeTools(db);

		PromptHandler promptHandler = PromptHandler.acceptPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.merge(local, remote, merged, base, null,
				Optional.of("customTool"), Optional.of(Boolean.TRUE), false,
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
		MergeTools manager = new MergeTools(db);

		PromptHandler promptHandler = PromptHandler.cancelPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		Optional<ExecutionResult> result = manager.merge(local, remote, merged,
				base, null, Optional.empty(),
				Optional.of(Boolean.TRUE), false, promptHandler, noToolHandler);
		assertFalse("Expected no result if user cancels the operation",
				result.isPresent());
	}

	@Test
	public void testDefaultTool() throws Exception {
		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString("merge", subsection, "tool", "customTool");
		config.setString("merge", subsection, "guitool", "customGuiTool");

		MergeTools manager = new MergeTools(db);
		boolean gui = false;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external merge tool",
				"customTool", defaultToolName);

		gui = true;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external merge tool",
				"customTool", defaultGuiToolName);

		config.setString("merge", subsection, "guitool", "customGuiTool");
		manager = new MergeTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external merge guitool",
				"customGuiTool", defaultGuiToolName);
	}

	@Test
	public void testDefaultToolExecutionWithPrompt() throws Exception {
		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString("merge", subsection, "tool", "customTool");

		String command = getEchoCommand();

		config.setString("mergetool", "customTool", "cmd", command);

		MergeTools manager = new MergeTools(db);

		PromptHandler promptHandler = PromptHandler.acceptPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.merge(local, remote, merged, base, null,
				Optional.empty(), Optional.of(Boolean.TRUE), false,
				promptHandler, noToolHandler);

				assertEchoCommandHasCorrectOutput();
	}

	@Test
	public void testNoDefaultToolName() {
		MergeTools manager = new MergeTools(db);
		boolean gui = false;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertNull("Expected no default tool when none is configured",
				defaultToolName);

		gui = true;
		defaultToolName = manager.getDefaultToolName(gui);
		assertNull("Expected no default tool when none is configured",
				defaultToolName);
	}

	@Test(expected = ToolException.class)
	public void testNullTool() throws Exception {
		MergeTools manager = new MergeTools(db);

		ExternalMergeTool tool = null;
		manager.merge(local, remote, merged, base, null, tool);
	}

	@Test(expected = ToolException.class)
	public void testNullToolWithPrompt() throws Exception {
		MergeTools manager = new MergeTools(db);

		PromptHandler promptHandler = PromptHandler.cancelPrompt();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		Optional<String> tool = null;

		manager.merge(local, remote, merged, base, null, tool,
				Optional.of(Boolean.TRUE), false, promptHandler, noToolHandler);
	}

	private String getEchoCommand() {
		return "(echo $LOCAL $REMOTE $MERGED $BASE) > "
				+ commandResult.getAbsolutePath();
	}

	private void assertEchoCommandHasCorrectOutput() throws IOException {
		List<String> actualLines = Files.readAllLines(commandResult.toPath());
		String actualContent = String.join(System.lineSeparator(), actualLines);
		actualLines = Arrays.asList(actualContent.split(" "));
		List<String> expectedLines = Arrays.asList(localFile.getAbsolutePath(),
				remoteFile.getAbsolutePath(), mergedFile.getAbsolutePath(),
				baseFile.getAbsolutePath());
		assertEquals("Dummy test tool called with unexpected arguments",
				expectedLines, actualLines);
	}

	private void assumeMergeToolIsAvailable(
			CommandLineMergeTool autoMergingTool) {
		boolean isAvailable = ExternalToolUtils.isToolAvailable(db.getFS(),
				db.getDirectory(), db.getWorkTree(), autoMergingTool.getPath());
		assumeTrue("Assuming external tool is available: "
				+ autoMergingTool.name(), isAvailable);
	}
}
