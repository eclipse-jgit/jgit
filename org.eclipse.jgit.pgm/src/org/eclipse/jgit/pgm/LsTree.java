/*
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.QuotedString;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StopOptionHandler;

@Command(common = true, usage = "usage_LsTree")
class LsTree extends TextBuiltin {
	@Option(name = "--recursive", usage = "usage_recurseIntoSubtrees", aliases = { "-r" })
	private boolean recursive;

	@Argument(index = 0, required = true, metaVar = "metaVar_treeish")
	private AbstractTreeIterator tree;

	@Argument(index = 1)
	@Option(name = "--", metaVar = "metaVar_paths", handler = StopOptionHandler.class)
	private List<String> paths = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.reset(); // drop the first empty tree, which we do not need here
			if (!paths.isEmpty()) {
				walk.setFilter(PathFilterGroup.createFromStrings(paths));
			}
			walk.setRecursive(recursive);
			walk.addTree(tree);

			while (walk.next()) {
				final FileMode mode = walk.getFileMode(0);
				if (mode == FileMode.TREE) {
					outw.print('0');
				}
				outw.print(mode);
				outw.print(' ');
				outw.print(Constants.typeString(mode.getObjectType()));

				outw.print(' ');
				outw.print(walk.getObjectId(0).name());

				outw.print('\t');
				outw.print(QuotedString.GIT_PATH.quote(walk.getPathString()));
				outw.println();
			}
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
