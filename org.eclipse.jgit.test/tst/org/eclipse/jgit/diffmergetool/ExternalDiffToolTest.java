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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
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
import org.junit.Test;

/**
 * Testing external diff tools.
 */
public class ExternalDiffToolTest extends ExternalToolTest {

	@Test(expected = ToolException.class)
	public void testUserToolWithError() throws Exception {
		assumePosixPlatform();

		int errorReturnCode = 1;
		String command = "exit " + errorReturnCode;

		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", command);

		DiffToolManager manager = new DiffToolManager(db);

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

		DiffToolManager manager = new DiffToolManager(db);
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
		assumePosixPlatform();

		String command = "echo \"$LOCAL\n$REMOTE\" &> "
				+ commandResult.getAbsolutePath();

		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", command);

		DiffToolManager manager = new DiffToolManager(db);

		Map<String, ExternalDiffTool> tools = manager.getUserDefinedTools();
		ExternalDiffTool externalTool = tools.get("customTool");
		boolean trustExitCode = true;
		manager.compare(local, remote, externalTool, trustExitCode);

		List<String> actualLines = Files.readAllLines(commandResult.toPath());
		List<String> expectedLines = Arrays.asList(localFile.getAbsolutePath(),
				remoteFile.getAbsolutePath());
		assertEquals("Dummy test tool called with unexpected arguments",
				expectedLines, actualLines);
	}

	@Test
	public void testUserDefinedToolWithPrompt() throws Exception {
		assumePosixPlatform();

		String command = "echo \"$LOCAL\n$REMOTE\" &> "
				+ commandResult.getAbsolutePath();

		FileBasedConfig config = db.getConfig();
		config.setString("difftool", "customTool", "cmd", command);

		DiffToolManager manager = new DiffToolManager(db);

		PromptHandler promptHandler = new PromptHandler();
		MissingToolHandler noToolHandler = new MissingToolHandler();

		manager.compare(local, remote, Optional.of("customTool"),
				Optional.of(Boolean.TRUE), false, Optional.of(Boolean.TRUE),
				promptHandler, noToolHandler);

		List<String> actualLines = Files.readAllLines(commandResult.toPath());
		List<String> expectedLines = Arrays.asList(localFile.getAbsolutePath(),
				remoteFile.getAbsolutePath());
		assertEquals("Dummy test tool called with unexpected arguments",
				expectedLines, actualLines);

		List<String> actualToolPrompts = promptHandler.toolPrompts;
		List<String> expectedToolPrompts = Arrays.asList("customTool");
		assertEquals("Expected a user prompt for custom tool call",
				expectedToolPrompts, actualToolPrompts);

		assertEquals("Expected to no informing about missing tools",
				Collections.EMPTY_LIST, noToolHandler.missingTools);
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

			DiffToolManager manager = new DiffToolManager(db);
			Optional<String> tool = manager.getExternalToolFromAttributes(db,
					localFile.getName());
			assertTrue("Failed to find user defined tool", tool.isPresent());
			assertEquals("Failed to find user defined tool", "customTool",
					tool.get());
		} finally {
			Files.delete(gitattributes.toPath());
		}
	}
}
