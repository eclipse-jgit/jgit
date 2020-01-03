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

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;

@Command(usage = "usage_WriteDirCache")
class WriteDirCache extends TextBuiltin {
	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		final DirCache cache = db.readDirCache();
		if (!cache.lock())
			throw die(CLIText.get().failedToLockIndex);
		cache.read();
		cache.write();
		if (!cache.commit())
			throw die(CLIText.get().failedToCommitIndex);
	}
}
