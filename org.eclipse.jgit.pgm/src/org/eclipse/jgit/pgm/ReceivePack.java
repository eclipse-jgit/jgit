/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009-2010, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Argument;

@Command(common = false, usage = "usage_ServerSideBackendForJgitPush")
class ReceivePack extends TextBuiltin {
	@Argument(index = 0, required = true, metaVar = "metaVar_directory", usage = "usage_RepositoryToReceiveInto")
	File dstGitdir;

	/** {@inheritDoc} */
	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void run() {
		final org.eclipse.jgit.transport.ReceivePack rp;

		try {
			FileKey key = FileKey.lenient(dstGitdir, FS.DETECTED);
			db = key.open(true /* must exist */);
		} catch (RepositoryNotFoundException notFound) {
			throw die(MessageFormat.format(CLIText.get().notAGitRepository,
					dstGitdir.getPath()), notFound);
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}

		rp = new org.eclipse.jgit.transport.ReceivePack(db);
		try {
			rp.receive(ins, outs, errs);
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
