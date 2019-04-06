/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

	private void show(AnyObjectId id, String name) throws IOException {
		outw.print(id.name());
		outw.print('\t');
		outw.print(name);
		outw.println();
	}
}
