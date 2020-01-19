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
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.StringUtils;

/**
 * Manages diff tools.
 */
public class DiffTools {

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
		config = repo.getConfig().get(DiffToolConfig.KEY);
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(config, predefinedTools);
	}

	/**
	 * Compare two versions of a file.
	 *
	 * @param repo
	 *            the repository
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param mergedFilePath
	 *            the path of 'merged' file, it equals local or remote path
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
	public ExecutionResult compare(Repository repo, FileElement localFile,
			FileElement remoteFile, String mergedFilePath, String toolName,
			BooleanTriState prompt, BooleanTriState gui,
			BooleanTriState trustExitCode) throws ToolException {
		ExternalDiffTool tool = guessTool(toolName, gui);
		try {
			File workingDir = repo.getWorkTree();
			String localFilePath = localFile.getFile().getPath();
			String remoteFilePath = remoteFile.getFile().getPath();
			String command = tool.getCommand();
			command = command.replace("$LOCAL", localFilePath); //$NON-NLS-1$
			command = command.replace("$REMOTE", remoteFilePath); //$NON-NLS-1$
			command = command.replace("$MERGED", mergedFilePath); //$NON-NLS-1$
			Map<String, String> env = new TreeMap<>();
			env.put(Constants.GIT_DIR_KEY,
					repo.getDirectory().getAbsolutePath());
			env.put("LOCAL", localFilePath); //$NON-NLS-1$
			env.put("REMOTE", remoteFilePath); //$NON-NLS-1$
			env.put("MERGED", mergedFilePath); //$NON-NLS-1$
			boolean trust = config.isTrustExitCode();
			if (trustExitCode != BooleanTriState.UNSET) {
				trust = trustExitCode == BooleanTriState.TRUE;
			}
			CommandExecutor cmdExec = new CommandExecutor(repo.getFS(), trust);
			return cmdExec.run(command, workingDir, env);
		} catch (IOException | InterruptedException e) {
			throw new ToolException(e);
		} finally {
			localFile.cleanTemporaries();
			remoteFile.cleanTemporaries();
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
		ExternalDiffTool tool = getTool(toolName);
		if (tool == null) {
			throw new ToolException("Unknown diff tool " + toolName); //$NON-NLS-1$
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
