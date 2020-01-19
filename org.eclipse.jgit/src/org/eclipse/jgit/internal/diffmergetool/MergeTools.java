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

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Manages merge tools.
 */
public class MergeTools {

	private final MergeToolConfig config;

	private final Map<String, ExternalMergeTool> predefinedTools;

	private final Map<String, ExternalMergeTool> userDefinedTools;

	/**
	 * @param repo
	 *            the repository database
	 */
	public MergeTools(Repository repo) {
		config = repo.getConfig().get(MergeToolConfig.KEY);
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(config, predefinedTools);
	}

	/**
	 * @param repo
	 *            the repository
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param baseFile
	 *            the base file element
	 * @param mergedFilePath
	 *            the path of 'merged' file
	 * @param toolName
	 *            the selected tool name (can be null)
	 * @param prompt
	 *            the prompt option
	 * @param gui
	 *            the GUI option
	 * @return the execution result from tool
	 * @throws ToolException
	 */
	public ExecutionResult merge(Repository repo, FileElement localFile,
			FileElement remoteFile, FileElement baseFile, String mergedFilePath,
			String toolName, BooleanTriState prompt, BooleanTriState gui)
			throws ToolException {
		ExternalMergeTool tool = guessTool(toolName, gui);
		try {
			File workingDir = repo.getWorkTree();
			String localFilePath = localFile.getFile().getPath();
			String remoteFilePath = remoteFile.getFile().getPath();
			String baseFilePath = baseFile.getFile().getPath();
			String command = tool.getCommand();
			command = command.replace("$LOCAL", localFilePath); //$NON-NLS-1$
			command = command.replace("$REMOTE", remoteFilePath); //$NON-NLS-1$
			command = command.replace("$MERGED", mergedFilePath); //$NON-NLS-1$
			command = command.replace("$BASE", baseFilePath); //$NON-NLS-1$
			Map<String, String> env = new TreeMap<>();
			env.put(Constants.GIT_DIR_KEY,
					repo.getDirectory().getAbsolutePath());
			env.put("LOCAL", localFilePath); //$NON-NLS-1$
			env.put("REMOTE", remoteFilePath); //$NON-NLS-1$
			env.put("MERGED", mergedFilePath); //$NON-NLS-1$
			env.put("BASE", baseFilePath); //$NON-NLS-1$
			boolean trust = tool.getTrustExitCode() == BooleanTriState.TRUE;
			CommandExecutor cmdExec = new CommandExecutor(repo.getFS(), trust);
			return cmdExec.run(command, workingDir, env);
		} catch (Exception e) {
			throw new ToolException(e);
		} finally {
			localFile.cleanTemporaries();
			remoteFile.cleanTemporaries();
			baseFile.cleanTemporaries();
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
	public Map<String, ExternalMergeTool> getUserDefinedTools() {
		return userDefinedTools;
	}

	/**
	 * @return the available predefined tools
	 */
	public Map<String, ExternalMergeTool> getAvailableTools() {
		return predefinedTools;
	}

	/**
	 * @return the NOT available predefined tools
	 */
	public Map<String, ExternalMergeTool> getNotAvailableTools() {
		return new TreeMap<>();
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

	private ExternalMergeTool guessTool(String toolName, BooleanTriState gui)
			throws ToolException {
		if ((toolName == null) || toolName.isEmpty()) {
			toolName = getDefaultToolName(gui);
		}
		ExternalMergeTool tool = getTool(toolName);
		if (tool == null) {
			throw new ToolException("Unknown diff tool " + toolName); //$NON-NLS-1$
		}
		return tool;
	}

	private ExternalMergeTool getTool(final String name) {
		ExternalMergeTool tool = userDefinedTools.get(name);
		if (tool == null) {
			tool = predefinedTools.get(name);
		}
		return tool;
	}

	private Map<String, ExternalMergeTool> setupPredefinedTools() {
		Map<String, ExternalMergeTool> tools = new TreeMap<>();
		for (CommandLineMergeTool tool : CommandLineMergeTool.values()) {
			tools.put(tool.name(), new PreDefinedMergeTool(tool));
		}
		return tools;
	}

	private Map<String, ExternalMergeTool> setupUserDefinedTools(
			MergeToolConfig cfg, Map<String, ExternalMergeTool> predefTools) {
		Map<String, ExternalMergeTool> tools = new TreeMap<>();
		Map<String, ExternalMergeTool> userTools = cfg.getTools();
		for (String name : userTools.keySet()) {
			ExternalMergeTool userTool = userTools.get(name);
			// if mergetool.<name>.cmd is defined we have user defined tool
			if (userTool.getCommand() != null) {
				tools.put(name, userTool);
			} else if (userTool.getPath() != null) {
				// if mergetool.<name>.path is defined we just overload the path
				// of predefined tool
				PreDefinedMergeTool predefTool = (PreDefinedMergeTool) predefTools
						.get(name);
				if (predefTool != null) {
					predefTool.setPath(userTool.getPath());
					if (userTool.getTrustExitCode() != BooleanTriState.UNSET) {
						predefTool
								.setTrustExitCode(userTool.getTrustExitCode());
					}
				}
			}
		}
		return tools;
	}

}
