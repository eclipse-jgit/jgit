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
import org.kohsuke.args4j.Option;

@Command(common = false, usage = "usage_ServerSideBackendForJgitFetch")
class UploadPack extends TextBuiltin {
	@Option(name = "--timeout", metaVar = "metaVar_seconds", usage = "usage_abortConnectionIfNoActivity")
	int timeout = -1;

	@Argument(index = 0, required = true, metaVar = "metaVar_directory", usage = "usage_RepositoryToReadFrom")
	File srcGitdir;

	/** {@inheritDoc} */
	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try {
			FileKey key = FileKey.lenient(srcGitdir, FS.DETECTED);
			db = key.open(true /* must exist */);
			org.eclipse.jgit.transport.UploadPack up = new org.eclipse.jgit.transport.UploadPack(
					db);
			if (0 <= timeout) {
				up.setTimeout(timeout);
			}
			up.upload(ins, outs, errs);
		} catch (RepositoryNotFoundException notFound) {
			throw die(MessageFormat.format(CLIText.get().notAGitRepository,
					srcGitdir.getPath()), notFound);
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
