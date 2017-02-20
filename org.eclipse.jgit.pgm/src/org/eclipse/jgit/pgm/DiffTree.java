/*
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_ShowDiffTree")
class DiffTree extends TextBuiltin {
	@Option(name = "--recursive", usage = "usage_recurseIntoSubtrees", aliases = { "-r" })
	private boolean recursive;

	@Argument(index = 0, metaVar = "metaVar_treeish", required = true)
	void tree_0(final AbstractTreeIterator c) {
		trees.add(c);
	}

	@Argument(index = 1, metaVar = "metaVar_treeish", required = true)
	private final List<AbstractTreeIterator> trees = new ArrayList<>();

	@Option(name = "--", metaVar = "metaVar_path", multiValued = true, handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	@Override
	protected void run() throws Exception {
		try (final TreeWalk walk = new TreeWalk(db)) {
			walk.setRecursive(recursive);
			for (final AbstractTreeIterator i : trees)
				walk.addTree(i);
			walk.setFilter(AndTreeFilter.create(TreeFilter.ANY_DIFF, pathFilter));

			final int nTree = walk.getTreeCount();
			while (walk.next()) {
				for (int i = 1; i < nTree; i++)
					outw.print(':');
				for (int i = 0; i < nTree; i++) {
					final FileMode m = walk.getFileMode(i);
					final String s = m.toString();
					for (int pad = 6 - s.length(); pad > 0; pad--)
						outw.print('0');
					outw.print(s);
					outw.print(' ');
				}

				for (int i = 0; i < nTree; i++) {
					outw.print(walk.getObjectId(i).name());
					outw.print(' ');
				}

				char chg = 'M';
				if (nTree == 2) {
					final int m0 = walk.getRawMode(0);
					final int m1 = walk.getRawMode(1);
					if (m0 == 0 && m1 != 0)
						chg = 'A';
					else if (m0 != 0 && m1 == 0)
						chg = 'D';
					else if (m0 != m1 && walk.idEqual(0, 1))
						chg = 'T';
				}
				outw.print(chg);

				outw.print('\t');
				outw.print(walk.getPathString());
				outw.println();
			}
		}
	}
}
