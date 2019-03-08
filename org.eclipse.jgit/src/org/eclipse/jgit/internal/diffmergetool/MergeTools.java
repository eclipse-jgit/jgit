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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
	public ExecutionResult merge(Repository repo, FileElement localFile,
			FileElement remoteFile, FileElement baseFile, String mergedFilePath,
			String toolName, BooleanTriState prompt, BooleanTriState gui)
			throws ToolException {
		ExternalMergeTool tool = guessTool(toolName, gui);
		FileElement backup = null;
		File tempDir = null;
		ExecutionResult result = null;
		try {
			File workingDir = repo.getWorkTree();
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
			boolean trust = tool.getTrustExitCode() == BooleanTriState.TRUE;
			String command = prepareCommand(mergedFilePath, localFilePath,
					remoteFilePath, baseFilePath,
					tool.getCommand(baseFile != null));
			// prepare the environment
			Map<String, String> env = prepareEnvironment(repo, mergedFilePath,
					localFilePath, remoteFilePath, baseFilePath);
			CommandExecutor cmdExec = new CommandExecutor(repo.getFS(), trust);
			result = cmdExec.run(command, workingDir, env);
			// keep backup as .orig file
			if (backup != null) {
				keepBackupFile(mergedFilePath, backup);
			}
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

	private static FileElement createBackupFile(String mergedFilePath,
			File tempDir) throws IOException {
		FileElement backup = null;
		Path path = Paths.get(tempDir.getPath(), mergedFilePath);
		if (Files.exists(path)) {
			backup = new FileElement(mergedFilePath, "NOID", null); //$NON-NLS-1$
			Files.copy(path, backup.getFile(tempDir, "BACKUP").toPath(), //$NON-NLS-1$
					StandardCopyOption.REPLACE_EXISTING);
		}
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

	private String prepareCommand(String mergedFilePath, String localFilePath,
			String remoteFilePath, String baseFilePath, String command) {
		command = command.replace("$LOCAL", localFilePath); //$NON-NLS-1$
		command = command.replace("$REMOTE", remoteFilePath); //$NON-NLS-1$
		command = command.replace("$MERGED", mergedFilePath); //$NON-NLS-1$
		command = command.replace("$BASE", baseFilePath); //$NON-NLS-1$
		return command;
	}

	private Map<String, String> prepareEnvironment(Repository repo,
			String mergedFilePath, String localFilePath, String remoteFilePath,
			String baseFilePath) {
		Map<String, String> env = new TreeMap<>();
		env.put(Constants.GIT_DIR_KEY, repo.getDirectory().getAbsolutePath());
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
