/*
 * Copyright (C) 2020, Simeon Andreev <simeon.danailov.andreev@gmail.com>
 * Copyright (C) 2020, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.diffmergetool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

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
		assumePosixPlatform();

		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", command);

		MergeToolManager manager = new MergeToolManager(db);
		Map<String, ExternalMergeTool> tools = manager.getUserDefinedTools();
		ExternalMergeTool externalTool = tools.get("customTool");
		manager.merge(local, remote, merged, null, null, externalTool);
		fail("Expected exception to be thrown due to external tool exiting with error code");
	}

	@Test
	public void testAllTools() {
		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", "echo");

		MergeToolManager manager = new MergeToolManager(db);
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

		MergeToolManager manager = new MergeToolManager(db);
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
		assumePosixPlatform();

		String command = "echo \"$LOCAL\n$REMOTE\n$MERGED\n$BASE\" &> "
				+ commandResult.getAbsolutePath();

		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", command);

		MergeToolManager manager = new MergeToolManager(db);
		Map<String, ExternalMergeTool> tools = manager.getUserDefinedTools();
		ExternalMergeTool externalTool = tools.get("customTool");
		manager.merge(local, remote, merged, base, null, externalTool);

		List<String> actualLines = Files.readAllLines(commandResult.toPath());
		List<String> expectedLines = Arrays.asList(localFile.getAbsolutePath(),
				remoteFile.getAbsolutePath(), mergedFile.getAbsolutePath(),
				baseFile.getAbsolutePath());
		assertEquals("Dummy test tool called with unexpected arguments",
				expectedLines, actualLines);
	}

	@Test
	public void testUserDefinedToolWithPrompt() throws Exception {
		assumePosixPlatform();

		String command = "echo \"$LOCAL\n$REMOTE\n$MERGED\n$BASE\" &> "
				+ commandResult.getAbsolutePath();

		FileBasedConfig config = db.getConfig();
		config.setString("mergetool", "customTool", "cmd", command);

		MergeToolManager manager = new MergeToolManager(db);

		PromptHandler promptHandler = new PromptHandler();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.merge(local, remote, merged, base, null,
				Optional.of("customTool"), Optional.of(Boolean.TRUE), false,
				promptHandler, noToolHandler);

		List<String> actualLines = Files.readAllLines(commandResult.toPath());
		List<String> expectedLines = Arrays.asList(localFile.getAbsolutePath(),
				remoteFile.getAbsolutePath(), mergedFile.getAbsolutePath(),
				baseFile.getAbsolutePath());
		assertEquals("Dummy test tool called with unexpected arguments",
				expectedLines, actualLines);

		List<String> actualToolPrompts = promptHandler.toolPrompts;
		List<String> expectedToolPrompts = Arrays.asList("customTool");
		assertEquals("Expected a user prompt for custom tool call",
				expectedToolPrompts, actualToolPrompts);

		assertEquals("Expected to no informing about missing tools",
				Collections.EMPTY_LIST, noToolHandler.missingTools);
	}

	private void assumeMergeToolIsAvailable(
			CommandLineMergeTool autoMergingTool) {
		boolean isAvailable = ExternalToolUtils.isToolAvailable(db.getFS(),
				db.getDirectory(), db.getWorkTree(), autoMergingTool.getPath());
		assumeTrue("Assuming external tool is available: "
				+ autoMergingTool.name(), isAvailable);
	}
}
