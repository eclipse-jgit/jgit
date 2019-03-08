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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Manages merge tools.
 *
 * @since 5.4
 */
public class MergeToolManager {

	private final MergeToolConfig config;

	private final Map<String, IMergeTool> predefinedTools;

	private final Map<String, IMergeTool> userDefinedTools;

	/**
	 * @param db the repository database
	 */
	public MergeToolManager(Repository db) {
		config = db.getConfig().get(MergeToolConfig.KEY);
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
	 * @param baseFile
	 *            the base file element (can be null)
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
	public ExecutionResult merge(Repository db, FileElement localFile,
			FileElement remoteFile, FileElement baseFile, String mergedFilePath,
			String toolName, BooleanOption prompt,
			BooleanOption gui)
			throws ToolException {
		IMergeTool tool = guessTool(toolName, gui);
		FileElement backup = null;
		File tempDir = null;
		ExecutionResult result = null;
		try {
			File workingDir = db.getWorkTree();
			// crate temp-directory or use working directory
			tempDir = config.isWriteToTemp()
					? Files.createTempDirectory("jgit-mergetool-").toFile() //$NON-NLS-1$
					: workingDir;
			// create additional backup file (copy worktree file)
			backup = createBackupFile(mergedFilePath, tempDir);
			// get local, remote and base file paths
			String localFilePath = localFile.getFile(tempDir, "LOCAL") //$NON-NLS-1$
					.getPath();
			String remoteFilePath = remoteFile.getFile(tempDir, "REMOTE") //$NON-NLS-1$
					.getPath();
			String baseFilePath = ""; //$NON-NLS-1$
			if (baseFile != null) {
				baseFilePath = baseFile.getFile(tempDir, "BASE").getPath(); //$NON-NLS-1$
			}
			// prepare the command (replace the file paths)
			String command = prepareCommand(mergedFilePath, localFilePath,
					remoteFilePath, baseFilePath,
					tool.getCommand(baseFile != null));
			// prepare the environment
			Map<String, String> env = prepareEnvironment(db, mergedFilePath,
					localFilePath,
					remoteFilePath, baseFilePath);
			boolean trust = tool.getTrustExitCode().toBoolean();
			CommandExecutor cmdExec = new CommandExecutor(db.getFS(), trust);
			result = cmdExec.run(command, workingDir, env);
			// keep backup as .orig file
			keepBackupFile(mergedFilePath, backup);
			return result;
		} catch (Exception e) {
			throw new ToolException(e);
		} finally {
			// always delete backup file (ignore that it was may be already
			// moved to keep-backup file)
			if (backup != null) {
				backup.cleanTemporaries();
			}
			// if the tool returns an error and keepTemporaries is set to true,
			// then these temporary files will be preserved
			if (!((result == null) && config.isKeepTemporaries())) {
				// delete the files
				localFile.cleanTemporaries();
				remoteFile.cleanTemporaries();
				if (baseFile != null) {
					baseFile.cleanTemporaries();
				}
				// delete temporary directory if needed
				if (config.isWriteToTemp() && (tempDir != null)
						&& tempDir.exists()) {
					tempDir.delete();
				}
			}
		}
	}

	private FileElement createBackupFile(String mergedFilePath, File tempDir)
			throws IOException {
		FileElement backup = new FileElement(mergedFilePath, "NOID", null); //$NON-NLS-1$
		Files.copy(Paths.get(mergedFilePath),
				backup.getFile(tempDir, "BACKUP").toPath(), //$NON-NLS-1$
				StandardCopyOption.REPLACE_EXISTING);
		return backup;
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
	public Map<String, IMergeTool> getUserDefinedTools() {
		return userDefinedTools;
	}

	/**
	 * @return the available predefined tools
	 */
	public Map<String, IMergeTool> getAvailableTools() {
		// TODO: change to return only available tools instead of all
		return predefinedTools;
	}

	/**
	 * @return the NOT available predefined tools
	 */
	public Map<String, IMergeTool> getNotAvailableTools() {
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

	private IMergeTool guessTool(String toolName, BooleanOption gui)
			throws ToolException {
		if ((toolName == null) || toolName.isEmpty()) {
			toolName = getDefaultToolName(gui);
		}
		IMergeTool tool = getTool(toolName);
		if (tool == null) {
			throw new ToolException("Unknown diff tool " + toolName); //$NON-NLS-1$
		}
		return tool;
	}

	private IMergeTool getTool(final String name) {
		IMergeTool tool = userDefinedTools.get(name);
		if (tool == null) {
			tool = predefinedTools.get(name);
		}
		return tool;
	}

	private String prepareCommand(String mergedFilePath, String localFilePath,
			String remoteFilePath, String baseFilePath, String command) {
		command = command.replace("$LOCAL", localFilePath); //$NON-NLS-1$
		command = command.replace("$REMOTE", remoteFilePath); //$NON-NLS-1$
		command = command.replace("$MERGED", mergedFilePath); //$NON-NLS-1$
		command = command.replace("$BASE", baseFilePath); //$NON-NLS-1$
		return command;
	}

	private Map<String, String> prepareEnvironment(Repository db,
			String mergedFilePath,
			String localFilePath, String remoteFilePath, String baseFilePath) {
		Map<String, String> env = new TreeMap<>();
		env.put(Constants.GIT_DIR_KEY, db.getDirectory().getAbsolutePath());
		env.put("LOCAL", localFilePath); //$NON-NLS-1$
		env.put("REMOTE", remoteFilePath); //$NON-NLS-1$
		env.put("MERGED", mergedFilePath); //$NON-NLS-1$
		env.put("BASE", baseFilePath); //$NON-NLS-1$
		return env;
	}

	private void keepBackupFile(String mergedFilePath, FileElement backup)
			throws IOException {
		if (config.isKeepBackup()) {
			Path backupPath = backup.getFile().toPath();
			Files.move(backupPath,
					backupPath.resolveSibling(
							Paths.get(mergedFilePath).getFileName() + ".orig"), //$NON-NLS-1$
					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Map<String, IMergeTool> setupPredefinedTools() {
		Map<String, IMergeTool> tools = new TreeMap<>();
		for (PreDefinedMergeTools tool : PreDefinedMergeTools.values()) {
			tools
					.put(tool.name(),
							new PreDefinedMergeTool(tool.name(), tool.getPath(),
									tool.getParameters(true),
									tool.getParameters(false),
									BooleanOption.defined(
											tool.isExitCodeTrustable())));
		}
		return tools;
	}

	private Map<String, IMergeTool> setupUserDefinedTools(MergeToolConfig cfg,
			Map<String, IMergeTool> predefTools) {
		Map<String, IMergeTool> tools = new TreeMap<>();
		Map<String, IMergeTool> userTools = cfg.getTools();
		for (String name : userTools.keySet()) {
			IMergeTool userTool = userTools.get(name);
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
					if (userTool.getTrustExitCode().isDefined()) {
						predefTool
								.setTrustExitCode(userTool.getTrustExitCode());
					}
				}
			}
		}
		return tools;
	}

}
