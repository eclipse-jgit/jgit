/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
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
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_LsRemote")
class LsRemote extends TextBuiltin {
	@Option(name = "--heads", usage = "usage_lsRemoteHeads")
	private boolean heads;

	@Option(name = "--tags", usage = "usage_lsRemoteTags", aliases = { "-t" })
	private boolean tags;

	@Option(name = "--timeout", metaVar = "metaVar_service", usage = "usage_abortConnectionIfNoActivity")
	int timeout = -1;

	@Argument(index = 0, metaVar = "metaVar_uriish", required = true)
	private String remote;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		LsRemoteCommand command = Git.lsRemoteRepository().setRemote(remote)
				.setTimeout(timeout).setHeads(heads).setTags(tags);
		TreeSet<Ref> refs = new TreeSet<>(
				(Ref r1, Ref r2) -> r1.getName().compareTo(r2.getName()));
		try {
			refs.addAll(command.call());
			for (Ref r : refs) {
				show(r.getObjectId(), r.getName());
				if (r.getPeeledObjectId() != null) {
					show(r.getPeeledObjectId(), r.getName() + "^{}"); //$NON-NLS-1$
				}
			}
		} catch (GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected boolean requiresRepository() {
		return false;
	}

	private void show(AnyObjectId id, String name)
			throws IOException {
		outw.print(id.name());
		outw.print('\t');
		outw.print(name);
		outw.println();
	}
}
