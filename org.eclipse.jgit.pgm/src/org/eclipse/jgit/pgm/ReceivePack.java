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
import java.util.Collection;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Argument;

@Command(common = false, usage = "usage_ServerSideBackendForJgitPush")
class ReceivePack extends TextBuiltin {
	@Argument(index = 0, required = true, metaVar = "metaVar_directory", usage = "usage_RepositoryToReceiveInto")
	File dstGitdir;

	@Override
	protected final boolean requiresRepository() {
		return false;
	}

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
		rp.setPostReceiveHook(this::runPostReceiveHook);
		try {
			rp.receive(ins, outs, errs);
		} catch (IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	private void runPostReceiveHook(
			org.eclipse.jgit.transport.ReceivePack receivePack,
			Collection<ReceiveCommand> commands) {
		if (commands.isEmpty()) {
			return;
		}
		StringBuilder stdin = new StringBuilder();
		for (ReceiveCommand cmd : commands) {
			stdin.append(cmd.getOldId().name()).append(' ')
					.append(cmd.getNewId().name()).append(' ')
					.append(cmd.getRefName()).append('\n');
		}
		try {
			db.getFS().runHookIfPresent(db, "post-receive", new String[0], //$NON-NLS-1$
					errs, errs, stdin.toString());
		} catch (JGitInternalException e) {
			receivePack.sendMessage(e.getMessage());
		}
	}
}
