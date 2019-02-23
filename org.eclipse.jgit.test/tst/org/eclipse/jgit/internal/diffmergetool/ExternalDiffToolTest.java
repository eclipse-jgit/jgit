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

import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.lib.BooleanTriState;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Testing external diff tools.
 */
public class ExternalDiffToolTest extends ExternalToolTest {

	@Test
	public void testToolNames() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getToolNames();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of external diff tool names",
				expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testAllTools() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getAvailableTools().keySet();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of available external diff tools",
				expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testUserDefinedTools() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getUserDefinedTools().keySet();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of user defined external diff tools",
				expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testNotAvailableTools() {
		DiffTools manager = new DiffTools(db);
		Set<String> actualToolNames = manager.getNotAvailableTools().keySet();
		Set<String> expectedToolNames = Collections.emptySet();
		assertEquals("Incorrect set of not available external diff tools",
				expectedToolNames,
				actualToolNames);
	}

	@Test
	public void testCompare() {
		DiffTools manager = new DiffTools(db);

		String newPath = "";
		String oldPath = "";
		String newId = "";
		String oldId = "";
		String toolName = "";
		BooleanTriState prompt = BooleanTriState.UNSET;
		BooleanTriState gui = BooleanTriState.UNSET;
		BooleanTriState trustExitCode = BooleanTriState.UNSET;

		int expectedCompareResult = 0;
		int compareResult = manager.compare(newPath, oldPath, newId, oldId,
				toolName, prompt, gui, trustExitCode);
		assertEquals("Incorrect compare result for external diff tool",
				expectedCompareResult,
				compareResult);
	}

	@Test
	public void testDefaultTool() throws Exception {
		FileBasedConfig config = db.getConfig();
		// the default diff tool is configured without a subsection
		String subsection = null;
		config.setString("diff", subsection, "tool", "customTool");

		DiffTools manager = new DiffTools(db);
		BooleanTriState gui = BooleanTriState.UNSET;
		String defaultToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				"my_default_toolname", defaultToolName);

		gui = BooleanTriState.TRUE;
		String defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff tool",
				"my_gui_tool", defaultGuiToolName);

		config.setString("diff", subsection, "guitool", "customGuiTool");
		manager = new DiffTools(db);
		defaultGuiToolName = manager.getDefaultToolName(gui);
		assertEquals(
				"Expected configured difftool to be the default external diff guitool",
				"my_gui_tool", defaultGuiToolName);
	}
}
