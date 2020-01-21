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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.diffmergetool.FileElement.Type;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Manages merge tools.
 */
public class MergeTools {

	private final FS fs;

	private final File gitDir;

	private final File workTree;

	private final MergeToolConfig config;

	private final Repository repo;

	private final Map<String, ExternalMergeTool> predefinedTools;

	private final Map<String, ExternalMergeTool> userDefinedTools;

	/**
	 * Creates the external merge-tools manager for given repository.
	 *
	 * @param repo
	 *            the repository
	 */
	public MergeTools(Repository repo) {
		this(repo, repo.getConfig());
	}

	/**
	 * Creates the external diff-tools manager for given configuration.
	 *
	 * @param config
	 *            the git configuration
	 */
	public MergeTools(StoredConfig config) {
		this(null, config);
	}

	private MergeTools(Repository repo, StoredConfig config) {
		this.repo = repo;
		this.config = config.get(MergeToolConfig.KEY);
		this.gitDir = repo == null ? null : repo.getDirectory();
		this.fs = repo == null ? FS.DETECTED : repo.getFS();
		this.workTree = repo == null ? null : repo.getWorkTree();
		predefinedTools = setupPredefinedTools();
		userDefinedTools = setupUserDefinedTools(predefinedTools);
	}

	/**
	 * Merge two versions of a file with optional base file.
	 *
	 * @param localFile
	 *            The local/left version of the file.
	 * @param remoteFile
	 *            The remote/right version of the file.
	 * @param mergedFile
	 *            The file for the result.
	 * @param baseFile
	 *            The base version of the file. May be null.
	 * @param tempDir
	 *            The tmepDir used for the files. May be null.
	 * @param toolName
	 *            Optionally the name of the tool to use. If not given the
	 *            default tool will be used.
	 * @param prompt
	 *            Optionally a flag whether to prompt the user before compare.
	 *            If not given the default will be used.
	 * @param gui
	 *            A flag whether to prefer a gui tool.
	 * @param promptHandler
	 *            The handler to use when needing to prompt the user if he wants
	 *            to continue.
	 * @param noToolHandler
	 *            The handler to use when needing to inform the user, that no
	 *            tool is configured.
	 * @return the optional result of executing the tool if it was executed
	 * @throws ToolException
	 *             when the tool fails
	 */
	public Optional<ExecutionResult> merge(FileElement localFile,
			FileElement remoteFile, FileElement mergedFile,
			FileElement baseFile, File tempDir, Optional<String> toolName,
			BooleanTriState prompt, boolean gui,
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

			if (StringUtils.isEmptyOrNull(toolNameToUse)) {
				noToolHandler.inform(new ArrayList<>(predefinedTools.keySet()));
				toolNameToUse = getFirstAvailableTool();
			}
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

		ExternalMergeTool tool = getTool(toolNameToUse);
		if (tool == null) {
			throw new ToolException(
					"External merge tool is not defined: " + toolNameToUse); //$NON-NLS-1$
		}

		return Optional.of(merge(localFile, remoteFile, mergedFile, baseFile,
				tempDir, tool));
	}

	/**
	 * Merge two versions of a file with optional base file.
	 *
	 * @param localFile
	 *            the local file element
	 * @param remoteFile
	 *            the remote file element
	 * @param mergedFile
	 *            the merged file element
	 * @param baseFile
	 *            the base file element (can be null)
	 * @param tempDir
	 *            the temporary directory (needed for backup and auto-remove,
	 *            can be null)
	 * @param tool
	 *            the selected tool
	 * @return the execution result from tool
	 * @throws ToolException
	 */
	public ExecutionResult merge(FileElement localFile, FileElement remoteFile,
			FileElement mergedFile, FileElement baseFile, File tempDir,
			ExternalMergeTool tool) throws ToolException {
		FileElement backup = null;
		ExecutionResult result = null;
		try {
			// create additional backup file (copy worktree file)
			backup = createBackupFile(mergedFile,
					tempDir != null ? tempDir : workTree);
			// prepare the command (replace the file paths)
			String command = ExternalToolUtils.prepareCommand(
					tool.getCommand(baseFile != null), localFile, remoteFile,
					mergedFile, baseFile);
			// prepare the environment
			Map<String, String> env = ExternalToolUtils.prepareEnvironment(
					gitDir, localFile, remoteFile, mergedFile, baseFile);
			boolean trust = tool.getTrustExitCode() == BooleanTriState.TRUE;
			// execute the tool
			CommandExecutor cmdExec = new CommandExecutor(fs, trust);
			result = cmdExec.run(command, workTree, env);
			// keep backup as .orig file
			if (backup != null) {
				keepBackupFile(mergedFile.getPath(), backup);
			}
			return result;
		} catch (IOException | InterruptedException e) {
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

	private FileElement createBackupFile(FileElement from, File toParentDir)
			throws IOException {
		FileElement backup = null;
		Path path = Paths.get(from.getPath());
		if (Files.exists(path)) {
			backup = new FileElement(from.getPath(), Type.BACKUP);
			Files.copy(path, backup.createTempFile(toParentDir).toPath(),
					StandardCopyOption.REPLACE_EXISTING);
		}
		return backup;
	}

	/**
	 * Create temporary directory.
	 *
	 * @return the created temporary directory if (mergetol.writeToTemp == true)
	 *         or null if not configured or false.
	 * @throws IOException
	 */
	public File createTempDirectory() throws IOException {
		return config.isWriteToTemp()
				? Files.createTempDirectory("jgit-mergetool-").toFile() //$NON-NLS-1$
				: null;
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
	 * Provides {@link Optional} with the name of an external merge tool if
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
				ExternalToolUtils.KEY_MERGE_TOOL);
	}

	/**
	 * Checks the availability of the predefined tools in the system.
	 *
	 * @return set of predefined available tools
	 */
	public Set<String> getPredefinedAvailableTools() {
		Map<String, ExternalMergeTool> defTools = getPredefinedTools(true);
		Set<String> availableTools = new LinkedHashSet<>();
		for (Entry<String, ExternalMergeTool> elem : defTools.entrySet()) {
			if (elem.getValue().isAvailable()) {
				availableTools.add(elem.getKey());
			}
		}
		return availableTools;
	}

	/**
	 * @return the user defined tools
	 */
	public Map<String, ExternalMergeTool> getUserDefinedTools() {
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
	public Map<String, ExternalMergeTool> getPredefinedTools(
			boolean checkAvailability) {
		if (checkAvailability) {
			for (ExternalMergeTool tool : predefinedTools.values()) {
				PreDefinedMergeTool predefTool = (PreDefinedMergeTool) tool;
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
		String name = null;
		for (ExternalMergeTool tool : predefinedTools.values()) {
			if (ExternalToolUtils.isToolAvailable(fs, gitDir, workTree,
					tool.getPath())) {
				name = tool.getName();
				break;
			}
		}
		return name;
	}

	/**
	 * Is interactive merge (prompt enabled) ?
	 *
	 * @return is interactive (config prompt enabled) ?
	 */
	public boolean isInteractive() {
		return config.isPrompt();
	}

	/**
	 * Get the default (gui-)tool name.
	 *
	 * @param gui
	 *            use the diff.guitool setting ?
	 * @return the default tool name
	 */
	public String getDefaultToolName(boolean gui) {
		return gui ? config.getDefaultGuiToolName()
				: config.getDefaultToolName();
	}

	private ExternalMergeTool getTool(final String name) {
		ExternalMergeTool tool = userDefinedTools.get(name);
		if (tool == null) {
			tool = predefinedTools.get(name);
		}
		return tool;
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
			Map<String, ExternalMergeTool> predefTools) {
		Map<String, ExternalMergeTool> tools = new TreeMap<>();
		Map<String, ExternalMergeTool> userTools = config.getTools();
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
