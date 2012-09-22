/*
 * Copyright (C) 2011, François Rey <eclipse.org_@_francois_._rey_._name>
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
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Command(usage = "usage_Status", common = true)
class Status extends TextBuiltin {

	protected final String lineFormat = CLIText.get().lineFormat;

	protected final String statusFileListFormat = CLIText.get().statusFileListFormat;

	protected final String statusFileListFormatWithPrefix = CLIText.get().statusFileListFormatWithPrefix;

	@Override
	protected void run() throws Exception {
		// Print current branch name
		final Ref head = db.getRef(Constants.HEAD);
		boolean firstHeader = true;
		if (head != null && head.isSymbolic()) {
			String branch = Repository.shortenRefName(head.getLeaf().getName());
			outw.println(CLIText.formatLine(
					MessageFormat.format(CLIText.get().onBranch, branch)));
		} else
			outw.println(CLIText.formatLine(CLIText.get().notOnAnyBranch));
		// List changes
		org.eclipse.jgit.api.Status status = new Git(db).status().call();
		Collection<String> added = status.getAdded();
		Collection<String> changed = status.getChanged();
		Collection<String> removed = status.getRemoved();
		Collection<String> modified = status.getModified();
		Collection<String> missing = status.getMissing();
		Collection<String> untracked = status.getUntracked();
		Collection<String> unmerged = status.getConflicting();
		Collection<String> toBeCommitted = new ArrayList<String>(added);
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
		Collection<String> notStagedForCommit = new ArrayList<String>(modified);
		notStagedForCommit.addAll(missing);
		int nbNotStagedForCommit = notStagedForCommit.size();
		if (nbNotStagedForCommit > 0) {
			if (!firstHeader)
				printSectionHeader("");
			printSectionHeader(CLIText.get().changesNotStagedForCommit);
			printList(CLIText.get().statusModified,
					CLIText.get().statusRemoved, null, notStagedForCommit,
					modified, missing, null);
			firstHeader = false;
		}
		int nbUnmerged = unmerged.size();
		if (nbUnmerged > 0) {
			if (!firstHeader)
				printSectionHeader("");
			printSectionHeader(CLIText.get().unmergedPaths);
			printList(unmerged);
			firstHeader = false;
		}
		int nbUntracked = untracked.size();
		if (nbUntracked > 0) {
			if (!firstHeader)
				printSectionHeader("");
			printSectionHeader(CLIText.get().untrackedFiles);
			printList(untracked);
		}
	}

	protected void printSectionHeader(String pattern, Object... arguments)
			throws IOException {
		outw.println(CLIText.formatLine(MessageFormat
				.format(pattern, arguments)));
		if (!pattern.equals(""))
			outw.println(CLIText.formatLine(""));
		outw.flush();
	}

	protected int printList(Collection<String> list) throws IOException {
		if (!list.isEmpty()) {
			List<String> sortedList = new ArrayList<String>(list);
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

	protected int printList(String status1, String status2, String status3,
			Collection<String> list, Collection<String> set1,
			Collection<String> set2,
			@SuppressWarnings("unused") Collection<String> set3)
			throws IOException {
		List<String> sortedList = new ArrayList<String>(list);
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
}
