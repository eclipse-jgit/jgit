/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.BufferedInputStream;
import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.ObjectDirectoryPackParser;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.PackParser;
import org.kohsuke.args4j.Option;

@Command(usage = "usage_IndexPack")
class IndexPack extends TextBuiltin {
	@Option(name = "--fix-thin", usage = "usage_fixAThinPackToBeComplete")
	private boolean fixThin;

	@Option(name = "--index-version", usage = "usage_indexFileFormatToCreate")
	private int indexVersion = -1;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		BufferedInputStream in = new BufferedInputStream(ins);
		try (ObjectInserter inserter = db.newObjectInserter()) {
			PackParser p = inserter.newPackParser(in);
			p.setAllowThin(fixThin);
			if (indexVersion != -1 && p instanceof ObjectDirectoryPackParser) {
				ObjectDirectoryPackParser imp = (ObjectDirectoryPackParser) p;
				imp.setIndexVersion(indexVersion);
			}
			p.parse(new TextProgressMonitor(errw));
			inserter.flush();
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
