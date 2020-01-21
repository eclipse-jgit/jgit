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
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.util.FS;

/**
 * Utilities for diff- and merge-tools.
 *
 * @since 5.13
 */
public class ExternalToolUtils {

	/**
	 * Key for merge tool git configuration section
	 */
	public static final String KEY_MERGE_TOOL = "mergetool"; //$NON-NLS-1$

	/**
	 * Key for diff tool git configuration section
	 */
	public static final String KEY_DIFF_TOOL = "difftool"; //$NON-NLS-1$

	/**
	 * Prepare command for execution.
	 *
	 * @param command
	 *            the input "command" string
	 * @param localFile
	 *            the local file (ours)
	 * @param remoteFile
	 *            the remote file (theirs)
	 * @param mergedFile
	 *            the merged file (worktree)
	 * @param baseFile
	 *            the base file (can be null)
	 * @return the prepared (with replaced variables) command string
	 * @throws IOException
	 */
	public static String prepareCommand(String command, FileElement localFile,
			FileElement remoteFile, FileElement mergedFile,
			FileElement baseFile) throws IOException {
		if (localFile != null) {
			command = localFile.replaceVariable(command);
		}
		if (remoteFile != null) {
			command = remoteFile.replaceVariable(command);
		}
		if (mergedFile != null) {
			command = mergedFile.replaceVariable(command);
		}
		if (baseFile != null) {
			command = baseFile.replaceVariable(command);
		}
		return command;
	}

	/**
	 * Prepare environment needed for execution.
	 *
	 * @param gitDir
	 *            the .git directory
	 * @param localFile
	 *            the local file (ours)
	 * @param remoteFile
	 *            the remote file (theirs)
	 * @param mergedFile
	 *            the merged file (worktree)
	 * @param baseFile
	 *            the base file (can be null)
	 * @return the environment map with variables and values (file paths)
	 * @throws IOException
	 */
	public static Map<String, String> prepareEnvironment(File gitDir,
			FileElement localFile, FileElement remoteFile,
			FileElement mergedFile, FileElement baseFile) throws IOException {
		Map<String, String> env = new TreeMap<>();
		if (gitDir != null) {
			env.put(Constants.GIT_DIR_KEY, gitDir.getAbsolutePath());
		}
		if (localFile != null) {
			localFile.addToEnv(env);
		}
		if (remoteFile != null) {
			remoteFile.addToEnv(env);
		}
		if (mergedFile != null) {
			mergedFile.addToEnv(env);
		}
		if (baseFile != null) {
			baseFile.addToEnv(env);
		}
		return env;
	}

	/**
	 * @param path
	 *            the path to be quoted
	 * @return quoted path if it contains spaces
	 */
	@SuppressWarnings("nls")
	public static String quotePath(String path) {
		// handling of spaces in path
		if (path.contains(" ")) {
			// add quotes before if needed
			if (!path.startsWith("\"")) {
				path = "\"" + path;
			}
			// add quotes after if needed
			if (!path.endsWith("\"")) {
				path = path + "\"";
			}
		}
		return path;
	}

	/**
	 * @param fs
	 *            the file system abstraction
	 * @param gitDir
	 *            the .git directory
	 * @param directory
	 *            the working directory
	 * @param path
	 *            the tool path
	 * @return true if tool available and false otherwise
	 */
	public static boolean isToolAvailable(FS fs, File gitDir, File directory,
			String path) {
		boolean available = true;
		try {
			CommandExecutor cmdExec = new CommandExecutor(fs, false);
			available = cmdExec.checkExecutable(path, directory,
					prepareEnvironment(gitDir, null, null, null, null));
		} catch (Exception e) {
			available = false;
		}
		return available;
	}

	/**
	 * @param defaultName
	 *            the default tool name
	 * @param userDefinedNames
	 *            the user defined tool names
	 * @param preDefinedNames
	 *            the pre defined tool names
	 * @return the sorted tool names set: first element is default tool name if
	 *         valid, then user defined tool names and then pre defined tool
	 *         names
	 */
	public static Set<String> createSortedToolSet(String defaultName,
			Set<String> userDefinedNames, Set<String> preDefinedNames) {
		Set<String> names = new LinkedHashSet<>();
		if (defaultName != null) {
			// remove defaultName from both sets
			Set<String> namesPredef = new LinkedHashSet<>();
			Set<String> namesUser = new LinkedHashSet<>();
			namesUser.addAll(userDefinedNames);
			namesUser.remove(defaultName);
			namesPredef.addAll(preDefinedNames);
			namesPredef.remove(defaultName);
			// add defaultName as first in set
			names.add(defaultName);
			names.addAll(namesUser);
			names.addAll(namesPredef);
		} else {
			names.addAll(userDefinedNames);
			names.addAll(preDefinedNames);
		}
		return names;
	}

	/**
	 * Provides {@link Optional} with the name of an external tool if specified
	 * in git configuration for a path.
	 *
	 * The formed git configuration results from global rules as well as merged
	 * rules from info and worktree attributes.
	 *
	 * Triggers {@link TreeWalk} until specified path found in the tree.
	 *
	 * @param repository
	 *            target repository to traverse into
	 * @param path
	 *            path to the node in repository to parse git attributes for
	 * @param toolKey
	 *            config key name for the tool
	 * @return attribute value for the given tool key if set
	 * @throws ToolException
	 */
	public static Optional<String> getExternalToolFromAttributes(
			final Repository repository, final String path,
			final String toolKey) throws ToolException {
		try {
			WorkingTreeIterator treeIterator = new FileTreeIterator(repository);
			try (TreeWalk walk = new TreeWalk(repository)) {
				walk.addTree(treeIterator);
				walk.setFilter(new NotIgnoredFilter(0));
				while (walk.next()) {
					String treePath = walk.getPathString();
					if (treePath.equals(path)) {
						Attributes attrs = walk.getAttributes();
						if (attrs.containsKey(toolKey)) {
							return Optional.of(attrs.getValue(toolKey));
						}
					}
					if (walk.isSubtree()) {
						walk.enterSubtree();
					}
				}
				// no external tool specified
				return Optional.empty();
			}

		} catch (RevisionSyntaxException | IOException e) {
			throw new ToolException(e);
		}
	}

}
