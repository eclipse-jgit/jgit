/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.diffmergetool;

import java.util.TreeMap;
import java.io.File;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Manages diff tools.
 *
 */
public class DiffToolManager {

	private final DiffToolConfig config;

	private final Map<String, ITool> predefinedTools;

	private final Map<String, ITool> userDefinedTools;

	/**
	 * @param db the repository database
	 */
	public DiffToolManager(Repository db) {
		config = db.getConfig().get(DiffToolConfig.KEY);
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(config, predefinedTools);
	}

	/**
	 * @param db
	 *            the repository
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param mergedFilePath
	 *            the path of 'merged' file, it equals local or remote path for
	 *            difftool
	 * @param toolName
	 *            the selected tool name (can be null)
	 * @param prompt
	 *            the prompt option
	 * @param gui
	 *            the GUI option
	 * @param trustExitCode
	 *            the "trust exit code" option
	 * @return the return code from executed tool
	 * @throws DiffToolException
	 */
	public int compare(Repository db, FileElement localFile,
			FileElement remoteFile, String mergedFilePath,
			String toolName, BooleanOption prompt,
			BooleanOption gui, BooleanOption trustExitCode)
			throws DiffToolException {
		int rc = 0;
		ITool tool = guessTool(toolName, gui);
		try {
			File workingDir = db.getWorkTree();
			String localFilePath = localFile.getFile(workingDir).getPath();
			String remoteFilePath = remoteFile.getFile(workingDir).getPath();
			String command = tool.getCommand();
			command = command.replace("$LOCAL", localFilePath); //$NON-NLS-1$
			command = command.replace("$REMOTE", remoteFilePath); //$NON-NLS-1$
			command = command.replace("$MERGED", mergedFilePath); //$NON-NLS-1$
			Map<String, String> env = new TreeMap<>();
			env.put(Constants.GIT_DIR_KEY, db.getDirectory().getAbsolutePath());
			env.put("LOCAL", localFilePath); //$NON-NLS-1$
			env.put("REMOTE", remoteFilePath); //$NON-NLS-1$
			env.put("MERGED", mergedFilePath); //$NON-NLS-1$
			ExecutionResult result = CommandExecutor.run(db.getFS(),
					command,
					workingDir, env, false);
			rc = result.getRc();
			if (rc != 0) {
				boolean trust = tool.isTrustExitCode();
				if (trustExitCode.isDefined()) {
					trust = trustExitCode.toBoolean();
				}
				if (trust) {
					throw new DiffToolException(
							new String(result.getStderr().toByteArray()));
				}
			}
		} catch (Exception e) {
			throw new DiffToolException(e);
		} finally {
			localFile.cleanTemporaries();
			remoteFile.cleanTemporaries();
		}
		return rc;
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
	public Map<String, ITool> getUserDefinedTools() {
		return userDefinedTools;
	}

	/**
	 * @return the available predefined tools
	 */
	public Map<String, ITool> getAvailableTools() {
		// TODO: change to return only available tools instead of all
		return predefinedTools;
	}

	/**
	 * @return the NOT available predefined tools
	 */
	public Map<String, ITool> getNotAvailableTools() {
		// TODO: return not available tools
		return new TreeMap<>();
	}

	/**
	 * @param gui
	 *            use the diff.guitool setting ?
	 * @return the default tool name
	 */
	public String getDefaultToolName(BooleanOption gui) {
		return gui.toBoolean() ? config.getDefaultGuiToolName()
				: config.getDefaultToolName();
	}

	/**
	 * @return id prompt enabled?
	 */
	public boolean isPrompt() {
		return config.isPrompt();
	}

	private ITool guessTool(String toolName, BooleanOption gui)
			throws DiffToolException {
		if ((toolName == null) || toolName.isEmpty()) {
			toolName = getDefaultToolName(gui);
		}
		ITool tool = getTool(toolName);
		if (tool == null) {
			throw new DiffToolException("Unknown diff tool " + toolName); //$NON-NLS-1$
		}
		return tool;
	}

	private ITool getTool(final String name) {
		ITool tool = userDefinedTools.get(name);
		if (tool == null) {
			tool = predefinedTools.get(name);
		}
		return tool;
	}

	private Map<String, ITool> setupPredefinedTools() {
		Map<String, ITool> tools = new TreeMap<>();
		for (PreDefinedDiffTools tool : PreDefinedDiffTools.values()) {
			tools
					.put(tool.name(),
							new PreDefinedDiffTool(tool.name(), tool.getPath(),
							tool.getParameters(), tool.isExitCodeTrustable()));
		}
		return tools;
	}

	private Map<String, ITool> setupUserDefinedTools(DiffToolConfig cfg,
			Map<String, ITool> predefTools) {
		Map<String, ITool> tools = new TreeMap<>();
		Map<String, ITool> userTools = cfg.getTools();
		for (String name : userTools.keySet()) {
			ITool userTool = userTools.get(name);
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
					predefTool.setTrustExitCode(userTool.isTrustExitCode());
				}
			}
		}
		return tools;
	}

}
