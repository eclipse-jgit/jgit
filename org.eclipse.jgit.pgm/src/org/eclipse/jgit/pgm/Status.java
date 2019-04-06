/*
 * Copyright (C) 2011, 2015 François Rey <eclipse.org_@_francois_._rey_._name>
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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.UntrackedFilesHandler;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

/**
 * Status command
 */
@Command(usage = "usage_Status", common = true)
class Status extends TextBuiltin {

	protected final String statusFileListFormat = CLIText.get().statusFileListFormat;

	protected final String statusFileListFormatWithPrefix = CLIText.get().statusFileListFormatWithPrefix;

	protected final String statusFileListFormatUnmerged = CLIText.get().statusFileListFormatUnmerged;

	@Option(name = "--porcelain", usage = "usage_machineReadableOutput")
	protected boolean porcelain;

	@Option(name = "--untracked-files", aliases = { "-u", "-uno", "-uall" }, usage = "usage_untrackedFilesMode", handler = UntrackedFilesHandler.class)
	protected String untrackedFilesMode = "all"; // default value //$NON-NLS-1$

	@Argument(required = false, index = 0, metaVar = "metaVar_paths")
	@Option(name = "--", metaVar = "metaVar_paths", handler = RestOfArgumentsHandler.class)
	protected List<String> filterPaths;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			StatusCommand statusCommand = git.status();
			if (filterPaths != null && filterPaths.size() > 0) {
				for (String path : filterPaths) {
					statusCommand.addPath(path);
				}
			}
			org.eclipse.jgit.api.Status status = statusCommand.call();
			printStatus(status);
		} catch (GitAPIException | NoWorkTreeException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	private void printStatus(org.eclipse.jgit.api.Status status)
			throws IOException {
		if (porcelain)
			printPorcelainStatus(status);
		else
			printLongStatus(status);
	}

	private void printPorcelainStatus(org.eclipse.jgit.api.Status status)
			throws IOException {

		Collection<String> added = status.getAdded();
		Collection<String> changed = status.getChanged();
		Collection<String> removed = status.getRemoved();
		Collection<String> modified = status.getModified();
		Collection<String> missing = status.getMissing();
		Map<String, StageState> conflicting = status.getConflictingStageState();

		// build a sorted list of all paths except untracked and ignored
		TreeSet<String> sorted = new TreeSet<>();
		sorted.addAll(added);
		sorted.addAll(changed);
		sorted.addAll(removed);
		sorted.addAll(modified);
		sorted.addAll(missing);
		sorted.addAll(conflicting.keySet());

		// list each path
		for (String path : sorted) {
			char x = ' ';
			char y = ' ';

			if (added.contains(path))
				x = 'A';
			else if (changed.contains(path))
				x = 'M';
			else if (removed.contains(path))
				x = 'D';

			if (modified.contains(path))
				y = 'M';
			else if (missing.contains(path))
				y = 'D';

			if (conflicting.containsKey(path)) {
				StageState stageState = conflicting.get(path);

				switch (stageState) {
				case BOTH_DELETED:
					x = 'D';
					y = 'D';
					break;
				case ADDED_BY_US:
					x = 'A';
					y = 'U';
					break;
				case DELETED_BY_THEM:
					x = 'U';
					y = 'D';
					break;
				case ADDED_BY_THEM:
					x = 'U';
					y = 'A';
					break;
				case DELETED_BY_US:
					x = 'D';
					y = 'U';
					break;
				case BOTH_ADDED:
					x = 'A';
					y = 'A';
					break;
				case BOTH_MODIFIED:
					x = 'U';
					y = 'U';
					break;
				default:
					throw new IllegalArgumentException("Unknown StageState: " //$NON-NLS-1$
							+ stageState);
				}
			}

			printPorcelainLine(x, y, path);
		}

		// untracked are always at the end of the list
		if ("all".equals(untrackedFilesMode)) { //$NON-NLS-1$
			TreeSet<String> untracked = new TreeSet<>(
					status.getUntracked());
			for (String path : untracked)
				printPorcelainLine('?', '?', path);
		}
	}

	private void printPorcelainLine(char x, char y, String path)
			throws IOException {
		StringBuilder lineBuilder = new StringBuilder();
		lineBuilder.append(x).append(y).append(' ').append(path);
		outw.println(lineBuilder.toString());
	}

	private void printLongStatus(org.eclipse.jgit.api.Status status)
			throws IOException {
		// Print current branch name
		final Ref head = db.exactRef(Constants.HEAD);
		if (head != null && head.isSymbolic()) {
			String branch = Repository.shortenRefName(head.getLeaf().getName());
			outw.println(CLIText.formatLine(MessageFormat.format(
					CLIText.get().onBranch, branch)));
		} else
			outw.println(CLIText.formatLine(CLIText.get().notOnAnyBranch));

		// List changes
		boolean firstHeader = true;

		Collection<String> added = status.getAdded();
		Collection<String> changed = status.getChanged();
		Collection<String> removed = status.getRemoved();
		Collection<String> modified = status.getModified();
		Collection<String> missing = status.getMissing();
		Collection<String> untracked = status.getUntracked();
		Map<String, StageState> unmergedStates = status
				.getConflictingStageState();
		Collection<String> toBeCommitted = new ArrayList<>(added);
		toBeCommitted.addAll(changed);
		toBeCommitted.addAll(removed);
		int nbToBeCommitted = toBeCommitted.size();
		if (nbToBeCommitted > 0) {
			printSectionHeader(CLIText.get().changesToBeCommitted);
			printList(CLIText.get().statusNewFile,
					CLIText.get().statusModified, CLIText.get().statusRemoved,
					toBeCommitted, added, changed, removed);
			firstHeader = false;
		}
		Collection<String> notStagedForCommit = new ArrayList<>(modified);
		notStagedForCommit.addAll(missing);
		int nbNotStagedForCommit = notStagedForCommit.size();
		if (nbNotStagedForCommit > 0) {
			if (!firstHeader)
				printSectionHeader(""); //$NON-NLS-1$
			printSectionHeader(CLIText.get().changesNotStagedForCommit);
			printList(CLIText.get().statusModified,
					CLIText.get().statusRemoved, null, notStagedForCommit,
					modified, missing, null);
			firstHeader = false;
		}
		int nbUnmerged = unmergedStates.size();
		if (nbUnmerged > 0) {
			if (!firstHeader)
				printSectionHeader(""); //$NON-NLS-1$
			printSectionHeader(CLIText.get().unmergedPaths);
			printUnmerged(unmergedStates);
			firstHeader = false;
		}
		int nbUntracked = untracked.size();
		if (nbUntracked > 0 && ("all".equals(untrackedFilesMode))) { //$NON-NLS-1$
			if (!firstHeader)
				printSectionHeader(""); //$NON-NLS-1$
			printSectionHeader(CLIText.get().untrackedFiles);
			printList(untracked);
		}
	}

	/**
	 * Print section header
	 *
	 * @param pattern
	 *            a {@link java.lang.String} object.
	 * @param arguments
	 *            a {@link java.lang.Object} object.
	 * @throws java.io.IOException
	 */
	protected void printSectionHeader(String pattern, Object... arguments)
			throws IOException {
		if (!porcelain) {
			outw.println(CLIText.formatLine(MessageFormat.format(pattern,
					arguments)));
			if (!pattern.isEmpty())
				outw.println(CLIText.formatLine("")); //$NON-NLS-1$
			outw.flush();
		}
	}

	/**
	 * Print String list
	 *
	 * @param list
	 *            a {@link java.util.Collection} object.
	 * @return a int.
	 * @throws java.io.IOException
	 */
	protected int printList(Collection<String> list) throws IOException {
		if (!list.isEmpty()) {
			List<String> sortedList = new ArrayList<>(list);
			java.util.Collections.sort(sortedList);
			for (String filename : sortedList) {
				outw.println(CLIText.formatLine(String.format(
						statusFileListFormat, filename)));
			}
			outw.flush();
			return list.size();
		} else
			return 0;
	}

	/**
	 * Print String list
	 *
	 * @param status1
	 *            a {@link java.lang.String} object.
	 * @param status2
	 *            a {@link java.lang.String} object.
	 * @param status3
	 *            a {@link java.lang.String} object.
	 * @param list
	 *            a {@link java.util.Collection} object.
	 * @param set1
	 *            a {@link java.util.Collection} object.
	 * @param set2
	 *            a {@link java.util.Collection} object.
	 * @param set3
	 *            a {@link java.util.Collection} object.
	 * @return a int.
	 * @throws java.io.IOException
	 */
	protected int printList(String status1, String status2, String status3,
			Collection<String> list, Collection<String> set1,
			Collection<String> set2,
			Collection<String> set3)
			throws IOException {
		List<String> sortedList = new ArrayList<>(list);
		java.util.Collections.sort(sortedList);
		for (String filename : sortedList) {
			String prefix;
			if (set1.contains(filename))
				prefix = status1;
			else if (set2.contains(filename))
				prefix = status2;
			else
				// if (set3.contains(filename))
				prefix = status3;
			outw.println(CLIText.formatLine(String.format(
					statusFileListFormatWithPrefix, prefix, filename)));
			outw.flush();
		}
		return list.size();
	}

	private void printUnmerged(Map<String, StageState> unmergedStates)
			throws IOException {
		List<String> paths = new ArrayList<>(unmergedStates.keySet());
		Collections.sort(paths);
		for (String path : paths) {
			StageState state = unmergedStates.get(path);
			String stateDescription = getStageStateDescription(state);
			outw.println(CLIText.formatLine(String.format(
					statusFileListFormatUnmerged, stateDescription, path)));
			outw.flush();
		}
	}

	private static String getStageStateDescription(StageState stageState) {
		CLIText text = CLIText.get();
		switch (stageState) {
		case BOTH_DELETED:
			return text.statusBothDeleted;
		case ADDED_BY_US:
			return text.statusAddedByUs;
		case DELETED_BY_THEM:
			return text.statusDeletedByThem;
		case ADDED_BY_THEM:
			return text.statusAddedByThem;
		case DELETED_BY_US:
			return text.statusDeletedByUs;
		case BOTH_ADDED:
			return text.statusBothAdded;
		case BOTH_MODIFIED:
			return text.statusBothModified;
		default:
			throw new IllegalArgumentException("Unknown StageState: " //$NON-NLS-1$
					+ stageState);
		}
	}
}
