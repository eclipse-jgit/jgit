/*
 * Copyright (C) 2018-2022, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.diffmergetool.ExternalMergeTool;
import org.eclipse.jgit.internal.diffmergetool.FileElement;
import org.eclipse.jgit.internal.diffmergetool.MergeTools;
import org.eclipse.jgit.internal.diffmergetool.ToolException;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(name = "mergetool", common = true, usage = "usage_MergeTool")
class MergeTool extends TextBuiltin {
	private MergeTools mergeTools;

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForMerge")
	private String toolName;

	private BooleanTriState prompt = BooleanTriState.UNSET;

	@Option(name = "--prompt", usage = "usage_prompt")
	void setPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanTriState.TRUE;
	}

	@Option(name = "--no-prompt", aliases = { "-y" }, usage = "usage_noPrompt")
	void noPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanTriState.FALSE;
	}

	@Option(name = "--tool-help", usage = "usage_toolHelp")
	private boolean toolHelp;

	private BooleanTriState gui = BooleanTriState.UNSET;

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_MergeGuiTool")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanTriState.TRUE;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanTriState.FALSE;
	}

	@Argument(required = false, index = 0, metaVar = "metaVar_paths")
	@Option(name = "--", metaVar = "metaVar_paths", handler = RestOfArgumentsHandler.class)
	protected List<String> filterPaths;

	private BufferedReader inputReader;

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		mergeTools = new MergeTools(repository);
		inputReader = new BufferedReader(new InputStreamReader(ins));
	}

	enum MergeResult {
		SUCCESSFUL, FAILED, ABORTED
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				showToolHelp();
			} else {
				// get prompt
				boolean showPrompt = mergeTools.isInteractive();
				if (prompt != BooleanTriState.UNSET) {
					showPrompt = prompt == BooleanTriState.TRUE;
				}
				// get passed or default tool name
				String toolNameSelected = toolName;
				if ((toolNameSelected == null) || toolNameSelected.isEmpty()) {
					toolNameSelected = mergeTools.getDefaultToolName(gui);
				}
				// get the changed files
				Map<String, StageState> files = getFiles();
				if (files.size() > 0) {
					merge(files, showPrompt, toolNameSelected);
				} else {
					outw.println(CLIText.get().mergeToolNoFiles);
				}
			}
			outw.flush();
		} catch (Exception e) {
			throw die(e.getMessage(), e);
		}
	}

	private void merge(Map<String, StageState> files, boolean showPrompt,
			String toolNamePrompt) throws Exception {
		// sort file names
		List<String> mergedFilePaths = new ArrayList<>(files.keySet());
		Collections.sort(mergedFilePaths);
		// show the files
		StringBuilder mergedFiles = new StringBuilder();
		for (String mergedFilePath : mergedFilePaths) {
			mergedFiles.append(MessageFormat.format("{0}\n", mergedFilePath)); //$NON-NLS-1$
		}
		outw.println(MessageFormat.format(CLIText.get().mergeToolMerging,
				mergedFiles));
		outw.flush();
		// merge the files
		MergeResult mergeResult = MergeResult.SUCCESSFUL;
		for (String mergedFilePath : mergedFilePaths) {
			// if last merge failed...
			if (mergeResult == MergeResult.FAILED) {
				// check if user wants to continue
				if (showPrompt && !isContinueUnresolvedPaths()) {
					mergeResult = MergeResult.ABORTED;
				}
			}
			// aborted ?
			if (mergeResult == MergeResult.ABORTED) {
				break;
			}
			// get file stage state and merge
			StageState fileState = files.get(mergedFilePath);
			if (fileState == StageState.BOTH_MODIFIED) {
				mergeResult = mergeModified(mergedFilePath, showPrompt,
						toolNamePrompt);
			} else if ((fileState == StageState.DELETED_BY_US)
					|| (fileState == StageState.DELETED_BY_THEM)) {
				mergeResult = mergeDeleted(mergedFilePath,
						fileState == StageState.DELETED_BY_US);
			} else {
				outw.println(MessageFormat.format(
						CLIText.get().mergeToolUnknownConflict,
						mergedFilePath));
				mergeResult = MergeResult.ABORTED;
			}
		}
	}

	private MergeResult mergeModified(String mergedFilePath, boolean showPrompt,
			String toolNamePrompt) throws Exception {
		outw.println(MessageFormat.format(CLIText.get().mergeToolNormalConflict,
				mergedFilePath));
		outw.flush();
		// check if user wants to launch merge resolution tool
		boolean launch = true;
		if (showPrompt) {
			launch = isLaunch(toolNamePrompt);
		}
		if (!launch) {
			return MergeResult.ABORTED; // abort
		}
		boolean isMergeSuccessful = true;
		ContentSource baseSource = ContentSource.create(db.newObjectReader());
		ContentSource localSource = ContentSource.create(db.newObjectReader());
		ContentSource remoteSource = ContentSource.create(db.newObjectReader());
		try {
			FileElement base = null;
			FileElement local = null;
			FileElement remote = null;
			DirCache cache = db.readDirCache();
			int firstIndex = cache.findEntry(mergedFilePath);
			if (firstIndex >= 0) {
				int nextIndex = cache.nextEntry(firstIndex);
				for (; firstIndex < nextIndex; firstIndex++) {
					DirCacheEntry entry = cache.getEntry(firstIndex);
					ObjectId id = entry.getObjectId();
					switch (entry.getStage()) {
					case DirCacheEntry.STAGE_1:
						base = new FileElement(mergedFilePath, id.name(),
								baseSource.open(mergedFilePath, id)
										.openStream());
						break;
					case DirCacheEntry.STAGE_2:
						local = new FileElement(mergedFilePath, id.name(),
								localSource.open(mergedFilePath, id)
										.openStream());
						break;
					case DirCacheEntry.STAGE_3:
						remote = new FileElement(mergedFilePath, id.name(),
								remoteSource.open(mergedFilePath, id)
										.openStream());
						break;
					}
				}
			}
			if ((local == null) || (remote == null)) {
				throw die(MessageFormat.format(CLIText.get().mergeToolDied,
						mergedFilePath));
			}
			File merged = new File(mergedFilePath);
			long modifiedBefore = merged.lastModified();
			try {
				// TODO: check how to return the exit-code of the
				// tool to jgit / java runtime ?
				// int rc =...
				ExecutionResult executionResult = mergeTools.merge(db, local,
						remote, base, mergedFilePath, toolName, prompt, gui);
				outw.println(
						new String(executionResult.getStdout().toByteArray()));
				outw.flush();
				errw.println(
						new String(executionResult.getStderr().toByteArray()));
				errw.flush();
			} catch (ToolException e) {
				isMergeSuccessful = false;
				outw.println(e.getResultStdout());
				outw.flush();
				errw.println(MessageFormat.format(
						CLIText.get().mergeToolMergeFailed, mergedFilePath));
				errw.flush();
				if (e.isCommandExecutionError()) {
					errw.println(e.getMessage());
					throw die(CLIText.get().mergeToolExecutionError, e);
				}
			}
			// if merge was successful check file modified
			if (isMergeSuccessful) {
				long modifiedAfter = merged.lastModified();
				if (modifiedBefore == modifiedAfter) {
					outw.println(MessageFormat.format(
							CLIText.get().mergeToolFileUnchanged,
							mergedFilePath));
					isMergeSuccessful = !showPrompt || isMergeSuccessful();
				}
			}
			// if automatically or manually successful
			// -> add the file to the index
			if (isMergeSuccessful) {
				addFile(mergedFilePath);
			}
		} finally {
			baseSource.close();
			localSource.close();
			remoteSource.close();
		}
		return isMergeSuccessful ? MergeResult.SUCCESSFUL : MergeResult.FAILED;
	}

	private MergeResult mergeDeleted(String mergedFilePath, boolean deletedByUs)
			throws Exception {
		outw.println(MessageFormat.format(CLIText.get().mergeToolFileUnchanged,
				mergedFilePath));
		if (deletedByUs) {
			outw.println(CLIText.get().mergeToolDeletedConflictByUs);
		} else {
			outw.println(CLIText.get().mergeToolDeletedConflictByThem);
		}
		int mergeDecision = getDeletedMergeDecision();
		if (mergeDecision == 1) {
			// add modified file
			addFile(mergedFilePath);
		} else if (mergeDecision == -1) {
			// remove deleted file
			rmFile(mergedFilePath);
		} else {
			return MergeResult.ABORTED;
		}
		return MergeResult.SUCCESSFUL;
	}

	private void addFile(String fileName) throws Exception {
		try (Git git = new Git(db)) {
			git.add().addFilepattern(fileName).call();
		}
	}

	private void rmFile(String fileName) throws Exception {
		try (Git git = new Git(db)) {
			git.rm().addFilepattern(fileName).call();
		}
	}

	private boolean hasUserAccepted(String message) throws IOException {
		boolean yes = true;
		outw.print(message + " "); //$NON-NLS-1$
		outw.flush();
		BufferedReader br = inputReader;
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.equalsIgnoreCase("y")) { //$NON-NLS-1$
				yes = true;
				break;
			} else if (line.equalsIgnoreCase("n")) { //$NON-NLS-1$
				yes = false;
				break;
			}
			outw.print(message);
			outw.flush();
		}
		return yes;
	}

	private boolean isContinueUnresolvedPaths() throws IOException {
		return hasUserAccepted(CLIText.get().mergeToolContinueUnresolvedPaths);
	}

	private boolean isMergeSuccessful() throws IOException {
		return hasUserAccepted(CLIText.get().mergeToolWasMergeSuccessfull);
	}

	private boolean isLaunch(String toolNamePrompt) throws IOException {
		boolean launch = true;
		outw.print(MessageFormat.format(CLIText.get().mergeToolLaunch,
				toolNamePrompt) + " "); //$NON-NLS-1$
		outw.flush();
		BufferedReader br = inputReader;
		String line = null;
		if ((line = br.readLine()) != null) {
			if (!line.equalsIgnoreCase("y") && !line.equalsIgnoreCase("")) { //$NON-NLS-1$ //$NON-NLS-2$
				launch = false;
			}
		}
		return launch;
	}

	private int getDeletedMergeDecision() throws IOException {
		int ret = 0; // abort
		final String message = CLIText.get().mergeToolDeletedMergeDecision
				+ " "; //$NON-NLS-1$
		outw.print(message);
		outw.flush();
		BufferedReader br = inputReader;
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.equalsIgnoreCase("m")) { //$NON-NLS-1$
				ret = 1; // modified
				break;
			} else if (line.equalsIgnoreCase("d")) { //$NON-NLS-1$
				ret = -1; // deleted
				break;
			} else if (line.equalsIgnoreCase("a")) { //$NON-NLS-1$
				break;
			}
			outw.print(message);
			outw.flush();
		}
		return ret;
	}

	private void showToolHelp() throws IOException {
		StringBuilder availableToolNames = new StringBuilder();
		for (String name : mergeTools.getAvailableTools().keySet()) {
			availableToolNames.append(MessageFormat.format("\t\t{0}\n", name)); //$NON-NLS-1$
		}
		StringBuilder notAvailableToolNames = new StringBuilder();
		for (String name : mergeTools.getNotAvailableTools().keySet()) {
			notAvailableToolNames
					.append(MessageFormat.format("\t\t{0}\n", name)); //$NON-NLS-1$
		}
		StringBuilder userToolNames = new StringBuilder();
		Map<String, ExternalMergeTool> userTools = mergeTools
				.getUserDefinedTools();
		for (String name : userTools.keySet()) {
			userToolNames.append(MessageFormat.format("\t\t{0}.cmd {1}\n", //$NON-NLS-1$
					name, userTools.get(name).getCommand()));
		}
		outw.println(MessageFormat.format(
				CLIText.get().mergeToolHelpSetToFollowing, availableToolNames,
				userToolNames, notAvailableToolNames));
	}

	private Map<String, StageState> getFiles() throws RevisionSyntaxException,
			NoWorkTreeException, GitAPIException {
		Map<String, StageState> files = new TreeMap<>();
		try (Git git = new Git(db)) {
			StatusCommand statusCommand = git.status();
			if (filterPaths != null && filterPaths.size() > 0) {
				for (String path : filterPaths) {
					statusCommand.addPath(path);
				}
			}
			Status status = statusCommand.call();
			files = status.getConflictingStageState();
		}
		return files;
	}

}
