/*
 * Copyright (C) 2012 Google Inc.
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

import java.lang.String;
import java.lang.System;
import java.text.MessageFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.pgm.CLIText;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Argument;

@Command(common = true, usage = "usage_archive")
class Archive extends TextBuiltin {
	@Argument(index = 0, metaVar = "metaVar_treeish")
	private AbstractTreeIterator tree;

	@Override
	protected void run() throws Exception {
		final TreeWalk walk = new TreeWalk(db);
		final ObjectReader reader = walk.getObjectReader();
		final MutableObjectId idBuf = new MutableObjectId();
		final ZipOutputStream out = new ZipOutputStream(outs);

		if (tree == null)
			throw die(CLIText.get().treeIsRequired);

		walk.reset();
		walk.addTree(tree);
		walk.setRecursive(true);
		while (walk.next()) {
			final String name = walk.getPathString();
			final FileMode mode = walk.getFileMode(0);
			walk.getObjectId(idBuf, 0);

			if (mode == FileMode.TREE)
				// ZIP entries for directories are optional.
				// Leave them out, mimicking "git archive".
				continue;

			final ZipEntry entry = new ZipEntry(name);
			final ObjectLoader loader = reader.open(idBuf);
			entry.setSize(loader.getSize());
			out.putNextEntry(entry);
			loader.copyTo(out);

			if (mode != FileMode.REGULAR_FILE)
				System.err.println(MessageFormat.format( //
						CLIText.get().archiveEntryModeIgnored, //
						name));
		}

		out.close();
	}
}
