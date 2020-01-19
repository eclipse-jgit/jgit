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

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.treewalk.TreeWalk.OperationType.CHECKOUT_OP;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.ContentSource.Pair;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.diffmergetool.DiffTools;
import org.eclipse.jgit.internal.diffmergetool.ExternalDiffTool;
import org.eclipse.jgit.internal.diffmergetool.FileElement;
import org.eclipse.jgit.internal.diffmergetool.PromptContinueHandler;
import org.eclipse.jgit.internal.diffmergetool.ToolException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.lib.internal.BooleanTriState;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
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

	private Optional<String> toolName = Optional.empty();

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForDiff")
	void setToolName(String name) {
		toolName = Optional.of(name);
	}

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

	private boolean gui = false;

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_DiffGuiTool")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = true;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = false;
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

	private BufferedReader inputReader;

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
		diffTools = new DiffTools(repository);
		inputReader = new BufferedReader(new InputStreamReader(ins, StandardCharsets.UTF_8));
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				showToolHelp();
			} else {
				// get the changed files
				List<DiffEntry> files = getFiles();
				if (files.size() > 0) {
					compare(files);
				}
			}
		} catch (RevisionSyntaxException | IOException e) {
			throw die(e.getMessage(), e);
		} finally {
			diffFmt.close();
		}
	}

	private void informUserNoTool(List<String> tools) {
		try {
			StringBuilder toolNames = new StringBuilder();
			for (String name : tools) {
				toolNames.append(name + " "); //$NON-NLS-1$
			}
			outw.println(MessageFormat.format(
					CLIText.get().diffToolPromptToolName, toolNames));
			outw.flush();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot output text", e); //$NON-NLS-1$
		}
	}

	private class CountingPromptContinueHandler
			implements PromptContinueHandler {
		private final int fileIndex;

		private final int fileCount;

		private final String fileName;

		public CountingPromptContinueHandler(int fileIndex, int fileCount,
				String fileName) {
			this.fileIndex = fileIndex;
			this.fileCount = fileCount;
			this.fileName = fileName;
		}

		@SuppressWarnings("boxing")
		@Override
		public boolean prompt(String toolToLaunchName) {
			try {
				boolean launchCompare = true;
				outw.println(MessageFormat.format(CLIText.get().diffToolLaunch,
						fileIndex, fileCount, fileName, toolToLaunchName)
						+ " "); //$NON-NLS-1$
				outw.flush();
				BufferedReader br = inputReader;
				String line = null;
				if ((line = br.readLine()) != null) {
					if (!line.equalsIgnoreCase("Y")) { //$NON-NLS-1$
						launchCompare = false;
					}
				}
				return launchCompare;
			} catch (IOException e) {
				throw new IllegalStateException("Cannot output text", e); //$NON-NLS-1$
			}
		}
	}

	private void compare(List<DiffEntry> files) throws IOException {
		ContentSource.Pair sourcePair = new ContentSource.Pair(source(oldTree),
				source(newTree));
		try {
			for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
				DiffEntry ent = files.get(fileIndex);

				String filePath = ent.getNewPath();
				if (filePath.equals(DiffEntry.DEV_NULL)) {
					filePath = ent.getOldPath();
				}

				try {
					FileElement local = createFileElement(
							FileElement.Type.LOCAL, sourcePair, Side.OLD, ent);
					FileElement remote = createFileElement(
							FileElement.Type.REMOTE, sourcePair, Side.NEW, ent);

					PromptContinueHandler promptContinueHandler = new CountingPromptContinueHandler(
							fileIndex + 1, files.size(), filePath);

					Optional<ExecutionResult> optionalResult = diffTools
							.compare(local, remote, toolName, prompt, gui,
									trustExitCode, promptContinueHandler,
									this::informUserNoTool);

					if (optionalResult.isPresent()) {
						ExecutionResult result = optionalResult.get();
						// TODO: check how to return the exit-code of the tool
						// to jgit / java runtime ?
						// int rc =...
						outw.println(
								new String(result.getStdout().toByteArray()));
						outw.flush();
						errw.println(
								new String(result.getStderr().toByteArray()));
						errw.flush();
					}
				} catch (ToolException e) {
					outw.println(e.getResultStdout());
					outw.flush();
					errw.println(e.getMessage());
					errw.flush();
					throw die(MessageFormat.format(CLIText.get().diffToolDied,
							filePath), e);
				}
			}
		} finally {
			sourcePair.close();
		}
	}

	private void showToolHelp() throws IOException {
		Map<String, ExternalDiffTool> predefTools = diffTools
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
		Map<String, ExternalDiffTool> userTools = diffTools
				.getUserDefinedTools();
		for (String name : userTools.keySet()) {
			userToolNames.append(MessageFormat.format("\t\t{0}.cmd {1}\n", //$NON-NLS-1$
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

	private FileElement createFileElement(FileElement.Type elementType,
			Pair pair, Side side, DiffEntry entry) throws NoWorkTreeException,
			CorruptObjectException, IOException, ToolException {
		String entryPath = side == Side.NEW ? entry.getNewPath()
				: entry.getOldPath();
		FileElement fileElement = new FileElement(entryPath, elementType,
				db.getWorkTree());
		if (!pair.isWorkingTreeSource(side) && !fileElement.isNullPath()) {
			try (RevWalk revWalk = new RevWalk(db);
					TreeWalk treeWalk = new TreeWalk(db,
							revWalk.getObjectReader())) {
				treeWalk.setFilter(
						PathFilterGroup.createFromStrings(entryPath));
				if (side == Side.NEW) {
					newTree.reset();
					treeWalk.addTree(newTree);
				} else {
					oldTree.reset();
					treeWalk.addTree(oldTree);
				}
				if (treeWalk.next()) {
					final EolStreamType eolStreamType = treeWalk
							.getEolStreamType(CHECKOUT_OP);
					final String filterCommand = treeWalk.getFilterCommand(
							Constants.ATTR_FILTER_TYPE_SMUDGE);
					WorkingTreeOptions opt = db.getConfig()
							.get(WorkingTreeOptions.KEY);
					CheckoutMetadata checkoutMetadata = new CheckoutMetadata(
							eolStreamType, filterCommand);
					DirCacheCheckout.getContent(db, entryPath,
							checkoutMetadata, pair.open(side, entry), opt,
							new FileOutputStream(
									fileElement.createTempFile(null)));
				} else {
					throw new ToolException("cannot find path '" + entryPath //$NON-NLS-1$
							+ "' in staging area!", //$NON-NLS-1$
							null);
				}
			}
		}
		return fileElement;
	}

	private ContentSource source(AbstractTreeIterator iterator) {
		if (iterator instanceof WorkingTreeIterator) {
			return ContentSource.create((WorkingTreeIterator) iterator);
		}
		return ContentSource.create(db.newObjectReader());
	}

}
