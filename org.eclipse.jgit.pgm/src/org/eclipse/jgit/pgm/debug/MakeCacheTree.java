/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import static java.lang.Integer.valueOf;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheTree;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;

@Command(usage = "usage_MakeCacheTree")
class MakeCacheTree extends TextBuiltin {
	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		final DirCache cache = db.readDirCache();
		final DirCacheTree tree = cache.getCacheTree(true);
		show(tree);
	}

	private void show(DirCacheTree tree) throws IOException {
		outw.println(MessageFormat.format(CLIText.get().cacheTreePathInfo,
				tree.getPathString(), valueOf(tree.getEntrySpan()),
				valueOf(tree.getChildCount())));

		for (int i = 0; i < tree.getChildCount(); i++)
			show(tree.getChild(i));
	}
}
