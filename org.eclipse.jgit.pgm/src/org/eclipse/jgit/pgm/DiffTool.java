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

package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.ContentSource.Pair;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diffmergetool.BooleanOption;
import org.eclipse.jgit.diffmergetool.ToolException;
import org.eclipse.jgit.diffmergetool.DiffToolManager;
import org.eclipse.jgit.diffmergetool.FileElement;
import org.eclipse.jgit.diffmergetool.IDiffTool;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(name = "difftool", common = true, usage = "usage_DiffTool")
class DiffTool extends TextBuiltin {
	private DiffFormatter diffFmt;

	private DiffToolManager diffToolMgr;

	@Argument(index = 0, metaVar = "metaVar_treeish")
	private AbstractTreeIterator oldTree;

	@Argument(index = 1, metaVar = "metaVar_treeish")
	private AbstractTreeIterator newTree;

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForDiff")
	private String toolName;

	@Option(name = "--cached", aliases = { "--staged" }, usage = "usage_cached")
	private boolean cached;

	private BooleanOption prompt = BooleanOption.NOT_DEFINED_FALSE;

	@Option(name = "--prompt", usage = "usage_prompt")
	void setPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanOption.TRUE;
	}

	@Option(name = "--no-prompt", aliases = { "-y" }, usage = "usage_noPrompt")
	void noPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanOption.FALSE;
	}

	@Option(name = "--tool-help", usage = "usage_toolHelp")
	private boolean toolHelp;

	private BooleanOption gui = BooleanOption.NOT_DEFINED_FALSE;

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_Gui")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanOption.TRUE;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanOption.FALSE;
	}

	private BooleanOption trustExitCode = BooleanOption.NOT_DEFINED_FALSE;

	@Option(name = "--trust-exit-code", usage = "usage_trustExitCode")
	void setTrustExitCode(@SuppressWarnings("unused") boolean on) {
		trustExitCode = BooleanOption.TRUE;
	}

	@Option(name = "--no-trust-exit-code", usage = "usage_noTrustExitCode")
	void noTrustExitCode(@SuppressWarnings("unused") boolean on) {
		trustExitCode = BooleanOption.FALSE;
	}

	@Option(name = "--", metaVar = "metaVar_paths", handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
		diffToolMgr = new DiffToolManager(repository);
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				showToolHelp();
			} else {
				// get prompt and tool name needed for prompt
				boolean showPrompt = diffToolMgr.isPrompt();
				if (prompt.isDefined()) {
					showPrompt = prompt.toBoolean();
				}
				String toolNamePrompt = toolName;
				if (showPrompt) {
					if ((toolNamePrompt == null) || toolNamePrompt.isEmpty()) {
						toolNamePrompt = diffToolMgr.getDefaultToolName(gui);
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
						ExecutionResult result = diffToolMgr.compare(db, local,
								remote, mergedFilePath,
								toolName, prompt, gui, trustExitCode);
						outw.println(new String(result.getStdout().toByteArray()));
						errw.println(
								new String(result.getStderr().toByteArray()));
					} catch (ToolException e) {
						outw.println(e.getResultStdout());
						outw.flush();
						errw.println(e.getMessage());
						throw die("external diff died, stopping at " //$NON-NLS-1$
								+ mergedFilePath, e);
					}
				} else {
					break;
				}
			}
		} finally {
			sourcePair.close();
		}
	}

	private boolean isLaunchCompare(int fileIndex, int fileCount,
			String fileName, String toolNamePrompt) throws IOException {
		boolean launchCompare = true;
		outw.println("Viewing (" + fileIndex + "/" + fileCount //$NON-NLS-1$ //$NON-NLS-2$
				+ "): '" + fileName + "'"); //$NON-NLS-1$ //$NON-NLS-2$
		outw.print("Launch '" + toolNamePrompt + "' [Y/n]? "); //$NON-NLS-1$ //$NON-NLS-2$
		outw.flush();
		BufferedReader br = new BufferedReader(new InputStreamReader(ins));
		String line = null;
		if ((line = br.readLine()) != null) {
			if (!line.equalsIgnoreCase("Y")) { //$NON-NLS-1$
				launchCompare = false;
			}
		}
		return launchCompare;
	}

	private void showToolHelp() throws IOException {
		outw.println(
				"'git difftool --tool=<tool>' may be set to one of the following:"); //$NON-NLS-1$
		for (String name : diffToolMgr.getAvailableTools().keySet()) {
			outw.println("\t\t" + name); //$NON-NLS-1$
		}
		outw.println(""); //$NON-NLS-1$
		outw.println("\tuser-defined:"); //$NON-NLS-1$
		Map<String, IDiffTool> userTools = diffToolMgr.getUserDefinedTools();
		for (String name : userTools.keySet()) {
			outw.println("\t\t" + name + ".cmd " //$NON-NLS-1$ //$NON-NLS-2$
					+ userTools.get(name).getCommand());
		}
		outw.println(""); //$NON-NLS-1$
		outw.println(
				"The following tools are valid, but not currently available:"); //$NON-NLS-1$
		for (String name : diffToolMgr.getNotAvailableTools().keySet()) {
			outw.println("\t\t" + name); //$NON-NLS-1$
		}
		outw.println(""); //$NON-NLS-1$
		outw.println("Some of the tools listed above only work in a windowed"); //$NON-NLS-1$
		outw.println(
				"environment. If run in a terminal-only session, they will fail."); //$NON-NLS-1$
		return;
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
		if (iterator instanceof WorkingTreeIterator)
			return ContentSource.create((WorkingTreeIterator) iterator);
		return ContentSource.create(db.newObjectReader());
	}

}
