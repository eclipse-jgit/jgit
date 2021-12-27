/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import java.util.TreeMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;

/**
 * Manages diff tools.
 */
public class DiffTools {

	private final DiffToolConfig config;

	private Map<String, ExternalDiffTool> predefinedTools;

	private Map<String, ExternalDiffTool> userDefinedTools;

	/**
	 * Creates the external diff-tools manager for given repository.
	 *
	 * @param repo
	 *            the repository
	 */
	public DiffTools(Repository repo) {
		config = repo.getConfig().get(DiffToolConfig.KEY);
		setupPredefinedTools();
		setupUserDefinedTools();
	}

	/**
	 * Compare two versions of a file.
	 *
	 * @param newPath
	 *            the new file path
	 * @param oldPath
	 *            the old file path
	 * @param newId
	 *            the new object ID
	 * @param oldId
	 *            the old object ID
	 * @param toolName
	 *            the selected tool name (can be null)
	 * @param prompt
	 *            the prompt option
	 * @param gui
	 *            the GUI option
	 * @param trustExitCode
	 *            the "trust exit code" option
	 * @return the return code from executed tool
	 */
	public int compare(String newPath, String oldPath, String newId,
			String oldId, String toolName, BooleanTriState prompt,
			BooleanTriState gui, BooleanTriState trustExitCode) {
		return 0;
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return config.getToolNames();
	}

	/**
	 * @return the user defined tools
	 */
	public Map<String, ExternalDiffTool> getUserDefinedTools() {
		return Collections.unmodifiableMap(userDefinedTools);
	}

	/**
	 * @return the available predefined tools
	 */
	public Map<String, ExternalDiffTool> getAvailableTools() {
		return Collections.unmodifiableMap(predefinedTools);
	}

	/**
	 * @return the NOT available predefined tools
	 */
	public Map<String, ExternalDiffTool> getNotAvailableTools() {
		return Collections.unmodifiableMap(new TreeMap<>());
	}

	/**
	 * @param gui
	 *            use the diff.guitool setting ?
	 * @return the default tool name
	 */
	public String getDefaultToolName(BooleanTriState gui) {
		return gui != BooleanTriState.UNSET ? "my_gui_tool" //$NON-NLS-1$
				: "my_default_toolname"; //$NON-NLS-1$
	}

	/**
	 * @return is interactive (config prompt enabled) ?
	 */
	public boolean isInteractive() {
		return false;
	}

	private void setupPredefinedTools() {
		predefinedTools = new TreeMap<>();
		for (CommandLineDiffTool tool : CommandLineDiffTool.values()) {
			predefinedTools.put(tool.name(), new PreDefinedDiffTool(tool));
		}
	}

	private void setupUserDefinedTools() {
		userDefinedTools = new TreeMap<>();
		Map<String, ExternalDiffTool> userTools = config.getTools();
		for (String name : userTools.keySet()) {
			ExternalDiffTool userTool = userTools.get(name);
			// if difftool.<name>.cmd is defined we have user defined tool
			if (userTool.getCommand() != null) {
				userDefinedTools.put(name, userTool);
			} else if (userTool.getPath() != null) {
				// if difftool.<name>.path is defined we just overload the path
				// of predefined tool
				PreDefinedDiffTool predefTool = (PreDefinedDiffTool) predefinedTools
						.get(name);
				if (predefTool != null) {
					predefTool.setPath(userTool.getPath());
				}
			}
		}
	}

}
