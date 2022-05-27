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

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.ContentSource.Pair;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.diffmergetool.DiffTools;
import org.eclipse.jgit.internal.diffmergetool.ExternalDiffTool;
import org.eclipse.jgit.internal.diffmergetool.FileElement;
import org.eclipse.jgit.internal.diffmergetool.ToolException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(name = "difftool", common = true, usage = "usage_DiffTool")
class DiffTool extends TextBuiltin {
	private DiffFormatter diffFmt;

	private DiffTools diffTools;

	@Argument(index = 0, metaVar = "metaVar_treeish")
	private AbstractTreeIterator oldTree;

	@Argument(index = 1, metaVar = "metaVar_treeish")
	private AbstractTreeIterator newTree;

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForDiff")
	private String toolName;

	@Option(name = "--cached", aliases = { "--staged" }, usage = "usage_cached")
	private boolean cached;

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

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_DiffGuiTool")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanTriState.TRUE;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanTriState.FALSE;
	}

	private BooleanTriState trustExitCode = BooleanTriState.UNSET;

	@Option(name = "--trust-exit-code", usage = "usage_trustExitCode")
	void setTrustExitCode(@SuppressWarnings("unused") boolean on) {
		trustExitCode = BooleanTriState.TRUE;
	}

	@Option(name = "--no-trust-exit-code", usage = "usage_noTrustExitCode")
	void noTrustExitCode(@SuppressWarnings("unused") boolean on) {
		trustExitCode = BooleanTriState.FALSE;
	}

	@Option(name = "--", metaVar = "metaVar_paths", handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
		diffTools = new DiffTools(repository);
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				showToolHelp();
			} else {
				boolean showPrompt = diffTools.isInteractive();
				if (prompt != BooleanTriState.UNSET) {
					showPrompt = prompt == BooleanTriState.TRUE;
				}
				String toolNamePrompt = toolName;
				if (showPrompt) {
					if (StringUtils.isEmptyOrNull(toolNamePrompt)) {
						toolNamePrompt = diffTools.getDefaultToolName(gui);
					}
				}
				// get the changed files
				List<DiffEntry> files = getFiles();
				if (files.size() > 0) {
					compare(files, showPrompt, toolNamePrompt);
				}
			}
			outw.flush();
		} catch (RevisionSyntaxException | IOException e) {
			throw die(e.getMessage(), e);
		} finally {
			diffFmt.close();
		}
	}

	private void compare(List<DiffEntry> files, boolean showPrompt,
			String toolNamePrompt) throws IOException {
		ContentSource.Pair sourcePair = new ContentSource.Pair(source(oldTree),
				source(newTree));
		try {
			for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
				DiffEntry ent = files.get(fileIndex);
				String mergedFilePath = ent.getNewPath();
				if (mergedFilePath.equals(DiffEntry.DEV_NULL)) {
					mergedFilePath = ent.getOldPath();
				}
				FileElement local = new FileElement(ent.getOldPath(),
						ent.getOldId().name(),
						getObjectStream(sourcePair, Side.OLD, ent));
				FileElement remote = new FileElement(ent.getNewPath(),
						ent.getNewId().name(),
						getObjectStream(sourcePair, Side.NEW, ent));
				// check if user wants to launch compare
				boolean launchCompare = true;
				if (showPrompt) {
					launchCompare = isLaunchCompare(fileIndex + 1, files.size(),
							mergedFilePath, toolNamePrompt);
				}
				if (launchCompare) {
					try {
						// TODO: check how to return the exit-code of
						// the
						// tool
						// to
						// jgit / java runtime ?
						// int rc =...
						ExecutionResult result = diffTools.compare(db, local,
								remote, mergedFilePath,
								toolName, prompt, gui, trustExitCode);
						outw.println(new String(result.getStdout().toByteArray()));
						errw.println(
								new String(result.getStderr().toByteArray()));
					} catch (ToolException e) {
						outw.println(e.getResultStdout());
						outw.flush();
						errw.println(e.getMessage());
						throw die(MessageFormat.format(
								CLIText.get().diffToolDied, mergedFilePath, e));
					}
				} else {
					break;
				}
			}
		} finally {
			sourcePair.close();
		}
	}

	@SuppressWarnings("boxing")
	private boolean isLaunchCompare(int fileIndex, int fileCount,
			String fileName, String toolNamePrompt) throws IOException {
		boolean launchCompare = true;
		outw.println(MessageFormat.format(CLIText.get().diffToolLaunch,
				fileIndex, fileCount, fileName, toolNamePrompt));
		outw.flush();
		BufferedReader br = new BufferedReader(
				new InputStreamReader(ins, StandardCharsets.UTF_8));
		String line = null;
		if ((line = br.readLine()) != null) {
			if (!line.equalsIgnoreCase("Y")) { //$NON-NLS-1$
				launchCompare = false;
			}
		}
		return launchCompare;
	}

	private void showToolHelp() throws IOException {
		StringBuilder availableToolNames = new StringBuilder();
		for (String name : diffTools.getAvailableTools().keySet()) {
			availableToolNames.append(String.format("\t\t%s\n", name)); //$NON-NLS-1$
		}
		StringBuilder notAvailableToolNames = new StringBuilder();
		for (String name : diffTools.getNotAvailableTools().keySet()) {
			notAvailableToolNames.append(String.format("\t\t%s\n", name)); //$NON-NLS-1$
		}
		StringBuilder userToolNames = new StringBuilder();
		Map<String, ExternalDiffTool> userTools = diffTools
				.getUserDefinedTools();
		for (String name : userTools.keySet()) {
			userToolNames.append(String.format("\t\t%s.cmd %s\n", //$NON-NLS-1$
					name, userTools.get(name).getCommand()));
		}
		outw.println(MessageFormat.format(
				CLIText.get().diffToolHelpSetToFollowing, availableToolNames,
				userToolNames, notAvailableToolNames));
	}

	private List<DiffEntry> getFiles()
			throws RevisionSyntaxException, AmbiguousObjectException,
			IncorrectObjectTypeException, IOException {
		diffFmt.setRepository(db);
		if (cached) {
			if (oldTree == null) {
				ObjectId head = db.resolve(HEAD + "^{tree}"); //$NON-NLS-1$
				if (head == null) {
					die(MessageFormat.format(CLIText.get().notATree, HEAD));
				}
				CanonicalTreeParser p = new CanonicalTreeParser();
				try (ObjectReader reader = db.newObjectReader()) {
					p.reset(reader, head);
				}
				oldTree = p;
			}
			newTree = new DirCacheIterator(db.readDirCache());
		} else if (oldTree == null) {
			oldTree = new DirCacheIterator(db.readDirCache());
			newTree = new FileTreeIterator(db);
		} else if (newTree == null) {
			newTree = new FileTreeIterator(db);
		}

		TextProgressMonitor pm = new TextProgressMonitor(errw);
		pm.setDelayStart(2, TimeUnit.SECONDS);
		diffFmt.setProgressMonitor(pm);
		diffFmt.setPathFilter(pathFilter);

		List<DiffEntry> files = diffFmt.scan(oldTree, newTree);
		return files;
	}

	private ObjectStream getObjectStream(Pair pair, Side side, DiffEntry ent) {
		ObjectStream stream = null;
		if (!pair.isWorkingTreeSource(side)) {
			try {
				stream = pair.open(side, ent).openStream();
			} catch (Exception e) {
				stream = null;
			}
		}
		return stream;
	}

	private ContentSource source(AbstractTreeIterator iterator) {
		if (iterator instanceof WorkingTreeIterator) {
			return ContentSource.create((WorkingTreeIterator) iterator);
		}
		return ContentSource.create(db.newObjectReader());
	}

}
