/*
 * Copyright (C) 2018-2022, Andre Bossert <andre.bossert@siemens.com>
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
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.StringUtils;

/**
 * Manages diff tools.
 */
public class DiffTools {

	private final Repository repo;

	private final DiffToolConfig config;

	private final Map<String, ExternalDiffTool> predefinedTools;

	private final Map<String, ExternalDiffTool> userDefinedTools;

	/**
	 * Creates the external diff-tools manager for given repository.
	 *
	 * @param repo
	 *            the repository
	 */
	public DiffTools(Repository repo) {
		this.repo = repo;
		config = repo.getConfig().get(DiffToolConfig.KEY);
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(config, predefinedTools);
	}

	/**
	 * Compare two versions of a file.
	 *
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param mergedFile
	 *            the merged file element, it's path equals local or remote
	 *            element path
	 * @param toolName
	 *            the selected tool name (can be null)
	 * @param prompt
	 *            the prompt option
	 * @param gui
	 *            the GUI option
	 * @param trustExitCode
	 *            the "trust exit code" option
	 * @return the execution result from tool
	 * @throws ToolException
	 */
	public ExecutionResult compare(FileElement localFile,
			FileElement remoteFile, FileElement mergedFile, String toolName,
			BooleanTriState prompt, BooleanTriState gui,
			BooleanTriState trustExitCode) throws ToolException {
		try {
			// prepare the command (replace the file paths)
			String command = ExternalToolUtils.prepareCommand(
					guessTool(toolName, gui).getCommand(), localFile,
					remoteFile, mergedFile, null);
			// prepare the environment
			Map<String, String> env = ExternalToolUtils.prepareEnvironment(repo,
					localFile, remoteFile, mergedFile, null);
			boolean trust = config.isTrustExitCode();
			if (trustExitCode != BooleanTriState.UNSET) {
				trust = trustExitCode == BooleanTriState.TRUE;
			}
			// execute the tool
			CommandExecutor cmdExec = new CommandExecutor(repo.getFS(), trust);
			return cmdExec.run(command, repo.getWorkTree(), env);
		} catch (IOException | InterruptedException e) {
			throw new ToolException(e);
		} finally {
			localFile.cleanTemporaries();
			remoteFile.cleanTemporaries();
			mergedFile.cleanTemporaries();
		}
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
	 * @param checkAvailability
	 *            true: for checking if tools can be executed; ATTENTION: this
	 *            check took some time, do not execute often (store the map for
	 *            other actions); false: availability is NOT checked:
	 *            isAvailable() returns default false is this case!
	 * @return the predefined tools with optionally checked availability (long
	 *         running operation)
	 */
	public Map<String, ExternalDiffTool> getPredefinedTools(
			boolean checkAvailability) {
		if (checkAvailability) {
			for (ExternalDiffTool tool : predefinedTools.values()) {
				PreDefinedDiffTool predefTool = (PreDefinedDiffTool) tool;
				predefTool.setAvailable(ExternalToolUtils.isToolAvailable(repo,
						predefTool.getPath()));
			}
		}
		return Collections.unmodifiableMap(predefinedTools);
	}

	/**
	 * @return the name of first available predefined tool or null
	 */
	public String getFirstAvailableTool() {
		String name = null;
		for (ExternalDiffTool tool : predefinedTools.values()) {
			if (ExternalToolUtils.isToolAvailable(repo, tool.getPath())) {
				name = tool.getName();
				break;
			}
		}
		return name;
	}

	/**
	 * @param gui
	 *            use the diff.guitool setting ?
	 * @return the default tool name
	 */
	public String getDefaultToolName(BooleanTriState gui) {
		return gui != BooleanTriState.UNSET ? "my_gui_tool" //$NON-NLS-1$
				: config.getDefaultToolName();
	}

	/**
	 * @return is interactive (config prompt enabled) ?
	 */
	public boolean isInteractive() {
		return config.isPrompt();
	}

	private ExternalDiffTool guessTool(String toolName, BooleanTriState gui)
			throws ToolException {
		if (StringUtils.isEmptyOrNull(toolName)) {
			toolName = getDefaultToolName(gui);
		}
		ExternalDiffTool tool = null;
		if (!StringUtils.isEmptyOrNull(toolName)) {
			tool = getTool(toolName);
		}
		if (tool == null) {
			throw new ToolException("Unknown diff tool '" + toolName + "'"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return tool;
	}

	private ExternalDiffTool getTool(final String name) {
		ExternalDiffTool tool = userDefinedTools.get(name);
		if (tool == null) {
			tool = predefinedTools.get(name);
		}
		return tool;
	}

	private static Map<String, ExternalDiffTool> setupPredefinedTools() {
		Map<String, ExternalDiffTool> tools = new TreeMap<>();
		for (CommandLineDiffTool tool : CommandLineDiffTool.values()) {
			tools.put(tool.name(), new PreDefinedDiffTool(tool));
		}
		return tools;
	}

	private static Map<String, ExternalDiffTool> setupUserDefinedTools(
			DiffToolConfig cfg, Map<String, ExternalDiffTool> predefTools) {
		Map<String, ExternalDiffTool> tools = new TreeMap<>();
		Map<String, ExternalDiffTool> userTools = cfg.getTools();
		for (String name : userTools.keySet()) {
			ExternalDiffTool userTool = userTools.get(name);
			// if difftool.<name>.cmd is defined we have user defined tool
			if (userTool.getCommand() != null) {
				tools.put(name, userTool);
			} else if (userTool.getPath() != null) {
				// if difftool.<name>.path is defined we just overload the path
				// of predefined tool
				PreDefinedDiffTool predefTool = (PreDefinedDiffTool) predefTools
						.get(name);
				if (predefTool != null) {
					predefTool.setPath(userTool.getPath());
				}
			}
		}
		return tools;
	}

}
