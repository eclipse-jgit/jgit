/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.File;
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

	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws Exception {
		final org.eclipse.jgit.transport.UploadPack up;

		try {
			FileKey key = FileKey.lenient(srcGitdir, FS.DETECTED);
			db = key.open(true /* must exist */);
		} catch (RepositoryNotFoundException notFound) {
			throw die(MessageFormat.format(CLIText.get().notAGitRepository,
					srcGitdir.getPath()));
		}

		up = new org.eclipse.jgit.transport.UploadPack(db);
		if (0 <= timeout)
			up.setTimeout(timeout);
		up.upload(ins, outs, err);
	}
}
