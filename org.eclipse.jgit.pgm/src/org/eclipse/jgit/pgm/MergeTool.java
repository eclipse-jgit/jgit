/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.eclipse.jgit.internal.diffmergetool.ExternalMergeTool;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.diffmergetool.MergeTools;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(name = "mergetool", common = true, usage = "usage_MergeTool")
class MergeTool extends TextBuiltin {
	private MergeTools mergeTools;

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForMerge")
	private String toolName;

	private Optional<Boolean> prompt = Optional.empty();

	@Option(name = "--prompt", usage = "usage_prompt")
	void setPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = Optional.of(Boolean.TRUE);
	}

	@Option(name = "--no-prompt", aliases = { "-y" }, usage = "usage_noPrompt")
	void noPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = Optional.of(Boolean.FALSE);
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

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		mergeTools = new MergeTools(repository);
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				showToolHelp();
			} else {
				// get prompt
				boolean showPrompt = mergeTools.isInteractive();
				if (prompt.isPresent()) {
					showPrompt = prompt.get().booleanValue();
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
					outw.println("No files need merging"); //$NON-NLS-1$
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
		List<String> fileNames = new ArrayList<>(files.keySet());
		Collections.sort(fileNames);
		// show the files
		outw.println("Merging:"); //$NON-NLS-1$
		for (String fileName : fileNames) {
			outw.println(fileName);
		}
		outw.flush();
		for (String fileName : fileNames) {
			StageState fileState = files.get(fileName);
			// only both-modified is valid for mergetool
			if (fileState == StageState.BOTH_MODIFIED) {
				outw.println("\nNormal merge conflict for '" + fileName + "':"); //$NON-NLS-1$ //$NON-NLS-2$
				outw.println("  {local}: modified file"); //$NON-NLS-1$
				outw.println("  {remote}: modified file"); //$NON-NLS-1$
				// check if user wants to launch merge resolution tool
				boolean launch = true;
				if (showPrompt) {
					launch = isLaunch(toolNamePrompt);
				}
				if (launch) {
					outw.println("TODO: Launch mergetool '" + toolNamePrompt //$NON-NLS-1$
							+ "' for path '" + fileName + "'..."); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					break;
				}
			} else if ((fileState == StageState.DELETED_BY_US) || (fileState == StageState.DELETED_BY_THEM)) {
				outw.println("\nDeleted merge conflict for '" + fileName + "':"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				outw.println(
						"\nUnknown merge conflict for '" + fileName + "':"); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			}
		}
	}

	private boolean isLaunch(String toolNamePrompt)
			throws IOException {
		boolean launch = true;
		outw.println("Hit return to start merge resolution tool (" //$NON-NLS-1$
				+ toolNamePrompt + "): "); //$NON-NLS-1$
		outw.flush();
		BufferedReader br = new BufferedReader(new InputStreamReader(ins));
		String line = null;
		if ((line = br.readLine()) != null) {
			if (!line.equalsIgnoreCase("Y") && !line.equalsIgnoreCase("")) { //$NON-NLS-1$ //$NON-NLS-2$
				launch = false;
			}
		}
		return launch;
	}

	private void showToolHelp() throws IOException {
		outw.println(
				"'git mergetool --tool=<tool>' may be set to one of the following:"); //$NON-NLS-1$
		for (String name : mergeTools.getAvailableTools().keySet()) {
			outw.println("\t\t" + name); //$NON-NLS-1$
		}
		outw.println(""); //$NON-NLS-1$
		outw.println("\tuser-defined:"); //$NON-NLS-1$
		Map<String, ExternalMergeTool> userTools = mergeTools
				.getUserDefinedTools();
		for (String name : userTools.keySet()) {
			outw.println("\t\t" + name + ".cmd " //$NON-NLS-1$ //$NON-NLS-2$
					+ userTools.get(name).getCommand());
		}
		outw.println(""); //$NON-NLS-1$
		outw.println(
				"The following tools are valid, but not currently available:"); //$NON-NLS-1$
		for (String name : mergeTools.getNotAvailableTools().keySet()) {
			outw.println("\t\t" + name); //$NON-NLS-1$
		}
		outw.println(""); //$NON-NLS-1$
		outw.println("Some of the tools listed above only work in a windowed"); //$NON-NLS-1$
		outw.println(
				"environment. If run in a terminal-only session, they will fail."); //$NON-NLS-1$
		return;
	}

	private Map<String, StageState> getFiles()
			throws RevisionSyntaxException, NoWorkTreeException,
			GitAPIException {
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
