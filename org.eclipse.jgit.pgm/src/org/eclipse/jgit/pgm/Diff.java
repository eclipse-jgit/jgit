/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

@Command(common = true, usage = "Show diffs")
class Diff extends TextBuiltin {
	@Argument(index = 0, metaVar = "tree-ish", required = true)
	void tree_0(final AbstractTreeIterator c) {
		trees.add(c);
	}

	@Argument(index = 1, metaVar = "tree-ish", required = true)
	private final List<AbstractTreeIterator> trees = new ArrayList<AbstractTreeIterator>();

	@Option(name = "--", metaVar = "path", multiValued = true, handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	private DiffFormatter fmt = new DiffFormatter();

	@Override
	protected void run() throws Exception {
		final TreeWalk walk = new TreeWalk(db);
		walk.reset();
		walk.setRecursive(true);
		for (final AbstractTreeIterator i : trees)
			walk.addTree(i);
		walk.setFilter(AndTreeFilter.create(TreeFilter.ANY_DIFF, pathFilter));

		while (walk.next())
			outputDiff(System.out, walk.getPathString(),
				walk.getObjectId(0), walk.getFileMode(0),
				walk.getObjectId(1), walk.getFileMode(1));
	}

	protected void outputDiff(PrintStream out, String path,
			ObjectId id1, FileMode mode1, ObjectId id2, FileMode mode2) throws IOException {
		String name1 = "a/" + path;
		String name2 =  "b/" + path;
		out.println("diff --git " + name1 + " " + name2);
		boolean isNew=false;
		boolean isDelete=false;
		if (id1.equals(ObjectId.zeroId())) {
			out.println("new file mode " + mode2);
			isNew=true;
		} else if (id2.equals(ObjectId.zeroId())) {
			out.println("deleted file mode " + mode1);
			isDelete=true;
		} else if (!mode1.equals(mode2)) {
			out.println("old mode " + mode1);
			out.println("new mode " + mode2);
		}
		out.println("index " + id1.abbreviate(db, 7).name()
			+ ".." + id2.abbreviate(db, 7).name()
			+ (mode1.equals(mode2) ? " " + mode1 : ""));
		out.println("--- " + (isNew ?  "/dev/null" : name1));
		out.println("+++ " + (isDelete ?  "/dev/null" : name2));
		RawText a = getRawText(id1);
		RawText b = getRawText(id2);
		MyersDiff diff = new MyersDiff(a, b);
		fmt.formatEdits(out, a, b, diff.getEdits());
	}

	private RawText getRawText(ObjectId id) throws IOException {
		if (id.equals(ObjectId.zeroId()))
			return new RawText(new byte[] { });
		return new RawText(db.openBlob(id).getCachedBytes());
	}
}

