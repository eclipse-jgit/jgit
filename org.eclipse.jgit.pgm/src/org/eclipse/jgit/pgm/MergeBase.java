/*
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_MergeBase")
class MergeBase extends TextBuiltin {
	@Option(name = "--all", usage = "usage_displayAllPossibleMergeBases")
	private boolean all;

	@Argument(index = 0, metaVar = "metaVar_commitish", required = true)
	void commit_0(final RevCommit c) {
		commits.add(c);
	}

	@Argument(index = 1, metaVar = "metaVar_commitish", required = true)
	private List<RevCommit> commits = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try {
			for (RevCommit c : commits) {
				argWalk.markStart(c);
			}
			argWalk.setRevFilter(RevFilter.MERGE_BASE);
			int max = all ? Integer.MAX_VALUE : 1;
			while (max-- > 0) {
				final RevCommit b = argWalk.next();
				if (b == null) {
					break;
				}
				outw.println(b.getId().name());
			}
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
