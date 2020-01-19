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

package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.treewalk.TreeWalk.OperationType.CHECKOUT_OP;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.diffmergetool.ExternalMergeTool;
import org.eclipse.jgit.internal.diffmergetool.FileElement;
import org.eclipse.jgit.internal.diffmergetool.FileElement.Type;
import org.eclipse.jgit.internal.diffmergetool.MergeTools;
import org.eclipse.jgit.internal.diffmergetool.ToolException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(name = "mergetool", common = true, usage = "usage_MergeTool")
class MergeTool extends TextBuiltin {
	private MergeTools mergeTools;

	private Optional<String> toolName = Optional.empty();

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForMerge")
	void setToolName(String name) {
		toolName = Optional.of(name);
	}

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

	private boolean gui = false;

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_MergeGuiTool")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = true;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = false;
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
				// get the changed files
				Map<String, StageState> files = getFiles();
				if (files.size() > 0) {
					merge(files);
				} else {
					outw.println(CLIText.get().mergeToolNoFiles);
				}
			}
			outw.flush();
		} catch (Exception e) {
			throw die(e.getMessage(), e);
		}
	}

	private void informUserNoTool(List<String> tools) {
		try {
			StringBuilder toolNames = new StringBuilder();
			for (String name : tools) {
				toolNames.append(name + " "); //$NON-NLS-1$
			}
			outw.println(MessageFormat
					.format(CLIText.get().mergeToolPromptToolName, toolNames));
			outw.flush();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot output text", e); //$NON-NLS-1$
		}
	}

	private void merge(Map<String, StageState> files) throws Exception {
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
		boolean showPrompt = mergeTools.isInteractive();
		if (prompt != BooleanTriState.UNSET) {
			showPrompt = prompt == BooleanTriState.TRUE;
		}
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
				mergeResult = mergeModified(mergedFilePath, showPrompt);
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

	private MergeResult mergeModified(String mergedFilePath, boolean showPrompt)
			throws Exception {
		outw.println(MessageFormat.format(CLIText.get().mergeToolNormalConflict,
				mergedFilePath));
		outw.flush();
		boolean isMergeSuccessful = true;
		ContentSource baseSource = ContentSource.create(db.newObjectReader());
		ContentSource localSource = ContentSource.create(db.newObjectReader());
		ContentSource remoteSource = ContentSource.create(db.newObjectReader());
		// temporary directory if mergetool.writeToTemp == true
		File tempDir = mergeTools.createTempDirectory();
		// the parent directory for temp files (can be same as tempDir or just
		// the worktree dir)
		File tempFilesParent = tempDir != null ? tempDir : db.getWorkTree();
		try {
			FileElement base = null;
			FileElement local = null;
			FileElement remote = null;
			FileElement merged = new FileElement(mergedFilePath, Type.MERGED,
					db.getWorkTree());
			DirCache cache = db.readDirCache();
			try (RevWalk revWalk = new RevWalk(db);
					TreeWalk treeWalk = new TreeWalk(db,
							revWalk.getObjectReader())) {
				treeWalk.setFilter(
						PathFilterGroup.createFromStrings(mergedFilePath));
				DirCacheIterator cacheIter = new DirCacheIterator(cache);
				treeWalk.addTree(cacheIter);
				while (treeWalk.next()) {
					final EolStreamType eolStreamType = treeWalk
							.getEolStreamType(CHECKOUT_OP);
					final String filterCommand = treeWalk.getFilterCommand(
							Constants.ATTR_FILTER_TYPE_SMUDGE);
					WorkingTreeOptions opt = db.getConfig()
							.get(WorkingTreeOptions.KEY);
					CheckoutMetadata checkoutMetadata = new CheckoutMetadata(
							eolStreamType, filterCommand);
					DirCacheEntry entry = treeWalk
							.getTree(DirCacheIterator.class).getDirCacheEntry();
					ObjectId id = entry.getObjectId();
					switch (entry.getStage()) {
					case DirCacheEntry.STAGE_1:
						base = new FileElement(mergedFilePath, Type.BASE);
						DirCacheCheckout.getContent(db, mergedFilePath,
								checkoutMetadata,
								baseSource.open(mergedFilePath, id), opt,
								new FileOutputStream(
										base.createTempFile(tempFilesParent)));
						break;
					case DirCacheEntry.STAGE_2:
						local = new FileElement(mergedFilePath, Type.LOCAL);
						DirCacheCheckout.getContent(db, mergedFilePath,
								checkoutMetadata,
								localSource.open(mergedFilePath, id), opt,
								new FileOutputStream(
										local.createTempFile(tempFilesParent)));
						break;
					case DirCacheEntry.STAGE_3:
						remote = new FileElement(mergedFilePath, Type.REMOTE);
						DirCacheCheckout.getContent(db, mergedFilePath,
								checkoutMetadata,
								remoteSource.open(mergedFilePath, id), opt,
								new FileOutputStream(remote
										.createTempFile(tempFilesParent)));
						break;
					}
				}
			}
			if ((local == null) || (remote == null)) {
				throw die(MessageFormat.format(CLIText.get().mergeToolDied,
						mergedFilePath));
			}
			long modifiedBefore = merged.getFile().lastModified();
			try {
				// TODO: check how to return the exit-code of the
				// tool to jgit / java runtime ?
				// int rc =...
				Optional<ExecutionResult> optionalResult = mergeTools.merge(
						local, remote, merged, base, tempDir, toolName, prompt,
						gui, this::promptForLaunch, this::informUserNoTool);
				if (optionalResult.isPresent()) {
					ExecutionResult result = optionalResult.get();
					outw.println(new String(result.getStdout().toByteArray()));
					outw.flush();
					errw.println(new String(result.getStderr().toByteArray()));
					errw.flush();
				} else {
					return MergeResult.ABORTED;
				}
			} catch (ToolException e) {
				isMergeSuccessful = false;
				outw.println(e.getResultStdout());
				outw.flush();
				errw.println(e.getMessage());
				errw.println(MessageFormat.format(
						CLIText.get().mergeToolMergeFailed, mergedFilePath));
				errw.flush();
				if (e.isCommandExecutionError()) {
					throw die(CLIText.get().mergeToolExecutionError, e);
				}
			}
			// if merge was successful check file modified
			if (isMergeSuccessful) {
				long modifiedAfter = merged.getFile().lastModified();
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

	private boolean promptForLaunch(String toolNamePrompt) {
		try {
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
		} catch (IOException e) {
			throw new IllegalStateException("Cannot output text", e); //$NON-NLS-1$
		}
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
		Map<String, ExternalMergeTool> predefTools = mergeTools
				.getPredefinedTools(true);
		StringBuilder availableToolNames = new StringBuilder();
		StringBuilder notAvailableToolNames = new StringBuilder();
		for (String name : predefTools.keySet()) {
			if (predefTools.get(name).isAvailable()) {
				availableToolNames.append(MessageFormat.format("\t\t{0}\n", name)); //$NON-NLS-1$
			} else {
				notAvailableToolNames.append(MessageFormat.format("\t\t{0}\n", name)); //$NON-NLS-1$
			}
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
