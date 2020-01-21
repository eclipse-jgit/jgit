/*
 * Copyright (C) 2018-2022, Andre Bossert <andre.bossert@siemens.com>
 * Copyright (C) 2019, Tim Neumann <tim.neumann@advantest.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.StringUtils;

/**
 * Manages diff tools.
 */
public class DiffTools {

	private final FS fs;

	private final File gitDir;

	private final File workTree;

	private final DiffToolConfig config;

	private final Repository repo;

	private final Map<String, ExternalDiffTool> predefinedTools;

	private final Map<String, ExternalDiffTool> userDefinedTools;

	/**
	 * Creates the external diff-tools manager for given repository.
	 *
	 * @param repo
	 *            the repository
	 */
	public DiffTools(Repository repo) {
		this(repo, repo.getConfig());
	}

	/**
	 * Creates the external merge-tools manager for given configuration.
	 *
	 * @param config
	 *            the git configuration
	 */
	public DiffTools(StoredConfig config) {
		this(null, config);
	}

	private DiffTools(Repository repo, StoredConfig config) {
		this.repo = repo;
		this.config = config.get(DiffToolConfig.KEY);
		this.gitDir = repo == null ? null : repo.getDirectory();
		this.fs = repo == null ? FS.DETECTED : repo.getFS();
		this.workTree = repo == null ? null : repo.getWorkTree();
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(predefinedTools);
	}

	/**
	 * Compare two versions of a file.
	 *
	 * @param localFile
	 *            The local/left version of the file.
	 * @param remoteFile
	 *            The remote/right version of the file.
	 * @param toolName
	 *            Optionally the name of the tool to use. If not given the
	 *            default tool will be used.
	 * @param prompt
	 *            Optionally a flag whether to prompt the user before compare.
	 *            If not given the default will be used.
	 * @param gui
	 *            A flag whether to prefer a gui tool.
	 * @param trustExitCode
	 *            Optionally a flag whether to trust the exit code of the tool.
	 *            If not given the default will be used.
	 * @param promptHandler
	 *            The handler to use when needing to prompt the user if he wants
	 *            to continue.
	 * @param noToolHandler
	 *            The handler to use when needing to inform the user, that no
	 *            tool is configured.
	 * @return the optioanl result of executing the tool if it was executed
	 * @throws ToolException
	 *             when the tool fails
	 */
	public Optional<ExecutionResult> compare(FileElement localFile,
			FileElement remoteFile, Optional<String> toolName,
			BooleanTriState prompt, boolean gui, BooleanTriState trustExitCode,
			PromptContinueHandler promptHandler,
			InformNoToolHandler noToolHandler) throws ToolException {

		String toolNameToUse;

		if (toolName == null) {
			throw new ToolException(JGitText.get().diffToolNullError);
		}

		if (toolName.isPresent()) {
			toolNameToUse = toolName.get();
		} else {
			toolNameToUse = getDefaultToolName(gui);
		}

		if (StringUtils.isEmptyOrNull(toolNameToUse)) {
			throw new ToolException(JGitText.get().diffToolNotGivenError);
		}

		boolean doPrompt;
		if (prompt != BooleanTriState.UNSET) {
			doPrompt = prompt == BooleanTriState.TRUE;
		} else {
			doPrompt = isInteractive();
		}

		if (doPrompt) {
			if (!promptHandler.prompt(toolNameToUse)) {
				return Optional.empty();
			}
		}

		boolean trust;
		if (trustExitCode != BooleanTriState.UNSET) {
			trust = trustExitCode == BooleanTriState.TRUE;
		} else {
			trust = config.isTrustExitCode();
		}

		ExternalDiffTool tool = getTool(toolNameToUse);
		if (tool == null) {
			throw new ToolException(
					"External diff tool is not defined: " + toolNameToUse); //$NON-NLS-1$
		}

		return Optional.of(
				compare(localFile, remoteFile, tool, trust));
	}

	/**
	 * Compare two versions of a file.
	 *
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param tool
	 *            the selected tool
	 * @param trustExitCode
	 *            the "trust exit code" option
	 * @return the execution result from tool
	 * @throws ToolException
	 */
	public ExecutionResult compare(FileElement localFile,
			FileElement remoteFile, ExternalDiffTool tool,
			boolean trustExitCode) throws ToolException {
		try {
			if (tool == null) {
				throw new ToolException(JGitText
						.get().diffToolNotSpecifiedInGitAttributesError);
			}
			// prepare the command (replace the file paths)
			String command = ExternalToolUtils.prepareCommand(tool.getCommand(),
					localFile, remoteFile, null, null);
			// prepare the environment
			Map<String, String> env = ExternalToolUtils.prepareEnvironment(
					gitDir, localFile, remoteFile, null, null);
			// execute the tool
			CommandExecutor cmdExec = new CommandExecutor(fs, trustExitCode);
			return cmdExec.run(command, workTree, env);
		} catch (IOException | InterruptedException e) {
			throw new ToolException(e);
		} finally {
			localFile.cleanTemporaries();
			remoteFile.cleanTemporaries();
		}
	}

	/**
	 * Get user defined tool names.
	 *
	 * @return the user defined tool names
	 */
	public Set<String> getUserDefinedToolNames() {
		return userDefinedTools.keySet();
	}

	/**
	 * Get predefined tool names.
	 *
	 * @return the predefined tool names
	 */
	public Set<String> getPredefinedToolNames() {
		return predefinedTools.keySet();
	}

	/**
	 * Get all tool names.
	 *
	 * @return the all tool names (default or available tool name is the first
	 *         in the set)
	 */
	public Set<String> getAllToolNames() {
		String defaultName = getDefaultToolName(false);
		if (defaultName == null) {
			defaultName = getFirstAvailableTool();
		}
		return ExternalToolUtils.createSortedToolSet(defaultName,
				getUserDefinedToolNames(), getPredefinedToolNames());
	}

	/**
	 * Provides {@link Optional} with the name of an external diff tool if
	 * specified in git configuration for a path.
	 *
	 * The formed git configuration results from global rules as well as merged
	 * rules from info and worktree attributes.
	 *
	 * Triggers {@link TreeWalk} until specified path found in the tree.
	 *
	 * @param path
	 *            path to the node in repository to parse git attributes for
	 * @return name of the difftool if set
	 * @throws ToolException
	 */
	public Optional<String> getExternalToolFromAttributes(final String path)
			throws ToolException {
		return ExternalToolUtils.getExternalToolFromAttributes(repo, path,
				ExternalToolUtils.KEY_DIFF_TOOL);
	}

	/**
	 * Checks the availability of the predefined tools in the system.
	 *
	 * @return set of predefined available tools
	 */
	public Set<String> getPredefinedAvailableTools() {
		Map<String, ExternalDiffTool> defTools = getPredefinedTools(true);
		Set<String> availableTools = new LinkedHashSet<>();
		for (Entry<String, ExternalDiffTool> elem : defTools.entrySet()) {
			if (elem.getValue().isAvailable()) {
				availableTools.add(elem.getKey());
			}
		}
		return availableTools;
	}

	/**
	 * Get user defined tools map.
	 *
	 * @return the user defined tools
	 */
	public Map<String, ExternalDiffTool> getUserDefinedTools() {
		return Collections.unmodifiableMap(userDefinedTools);
	}

	/**
	 * Get predefined tools map.
	 *
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
				predefTool.setAvailable(ExternalToolUtils.isToolAvailable(fs,
						gitDir, workTree, predefTool.getPath()));
			}
		}
		return Collections.unmodifiableMap(predefinedTools);
	}

	/**
	 * Get first available tool name.
	 *
	 * @return the name of first available predefined tool or null
	 */
	public String getFirstAvailableTool() {
		for (ExternalDiffTool tool : predefinedTools.values()) {
			if (ExternalToolUtils.isToolAvailable(fs, gitDir, workTree,
					tool.getPath())) {
				return tool.getName();
			}
		}
		return null;
	}

	/**
	 * Get default (gui-)tool name.
	 *
	 * @param gui
	 *            use the diff.guitool setting ?
	 * @return the default tool name
	 */
	public String getDefaultToolName(boolean gui) {
		String guiToolName;
		if (gui) {
			guiToolName = config.getDefaultGuiToolName();
			if (guiToolName != null) {
				return guiToolName;
			}
		}
		return config.getDefaultToolName();
	}

	/**
	 * Is interactive diff (prompt enabled) ?
	 *
	 * @return is interactive (config prompt enabled) ?
	 */
	public boolean isInteractive() {
		return config.isPrompt();
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

	private Map<String, ExternalDiffTool> setupUserDefinedTools(
			Map<String, ExternalDiffTool> predefTools) {
		Map<String, ExternalDiffTool> tools = new TreeMap<>();
		Map<String, ExternalDiffTool> userTools = config.getTools();
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
