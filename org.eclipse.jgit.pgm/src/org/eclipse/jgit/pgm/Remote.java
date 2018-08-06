/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com>
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
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteListCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.io.ThrowingPrintWriter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = false, usage = "usage_Remote")
class Remote extends TextBuiltin {

	@Option(name = "--verbose", aliases = { "-v" }, usage = "usage_beVerbose")
	private boolean verbose = false;

	@Option(name = "--prune", aliases = {
			"-p" }, usage = "usage_pruneStaleTrackingRefs")
	private boolean prune;

	@Option(name = "--push", usage = "usage_pushUrls")
	private boolean push;

	@Argument(index = 0, metaVar = "metaVar_command")
	private String command;

	@Argument(index = 1, metaVar = "metaVar_remoteName")
	private String name;

	@Argument(index = 2, metaVar = "metaVar_uriish")
	private String uri;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		try (Git git = new Git(db)) {
			if (command == null) {
				RemoteListCommand cmd = git.remoteList();
				List<RemoteConfig> remotes = cmd.call();
				print(remotes);
			} else if ("add".equals(command)) { //$NON-NLS-1$
				RemoteAddCommand cmd = git.remoteAdd();
				cmd.setName(name);
				cmd.setUri(new URIish(uri));
				cmd.call();
			} else if ("remove".equals(command) || "rm".equals(command)) { //$NON-NLS-1$ //$NON-NLS-2$
				RemoteRemoveCommand cmd = git.remoteRemove();
				cmd.setName(name);
				cmd.call();
			} else if ("set-url".equals(command)) { //$NON-NLS-1$
				RemoteSetUrlCommand cmd = git.remoteSetUrl();
				cmd.setName(name);
				cmd.setUri(new URIish(uri));
				cmd.setPush(push);
				cmd.call();
			} else if ("update".equals(command)) { //$NON-NLS-1$
				// reuse fetch command for basic implementation of remote update
				Fetch fetch = new Fetch();
				fetch.init(db, gitdir);

				// redirect the output stream
				StringWriter osw = new StringWriter();
				fetch.outw = new ThrowingPrintWriter(osw);
				// redirect the error stream
				StringWriter esw = new StringWriter();
				fetch.errw = new ThrowingPrintWriter(esw);

				List<String> fetchArgs = new ArrayList<>();
				if (verbose) {
					fetchArgs.add("--verbose"); //$NON-NLS-1$
				}
				if (prune) {
					fetchArgs.add("--prune"); //$NON-NLS-1$
				}
				if (name != null) {
					fetchArgs.add(name);
				}

				fetch.execute(fetchArgs.toArray(new String[0]));

				// flush the streams
				fetch.outw.flush();
				fetch.errw.flush();
				outw.println(osw.toString());
				errw.println(esw.toString());
			} else {
				throw new JGitInternalException(MessageFormat
						.format(CLIText.get().unknownSubcommand, command));
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void printUsage(String message, CmdLineParser clp)
			throws IOException {
		errw.println(message);
		errw.println("jgit remote [--verbose (-v)] [--help (-h)]"); //$NON-NLS-1$
		errw.println("jgit remote add name uri-ish [--help (-h)]"); //$NON-NLS-1$
		errw.println("jgit remote remove name [--help (-h)]"); //$NON-NLS-1$
		errw.println("jgit remote rm name [--help (-h)]"); //$NON-NLS-1$
		errw.println(
				"jgit remote [--verbose (-v)] update [name] [--prune (-p)] [--help (-h)]"); //$NON-NLS-1$
		errw.println("jgit remote set-url name uri-ish [--push] [--help (-h)]"); //$NON-NLS-1$

		errw.println();
		clp.printUsage(errw, getResourceBundle());
		errw.println();

		errw.flush();
	}

	private void print(List<RemoteConfig> remotes) throws IOException {
		for (RemoteConfig remote : remotes) {
			String remoteName = remote.getName();
			if (verbose) {
				List<URIish> fetchURIs = remote.getURIs();
				List<URIish> pushURIs = remote.getPushURIs();

				String fetchURI = ""; //$NON-NLS-1$
				if (!fetchURIs.isEmpty()) {
					fetchURI = fetchURIs.get(0).toString();
				} else if (!pushURIs.isEmpty()) {
					fetchURI = pushURIs.get(0).toString();
				}

				String pushURI = ""; //$NON-NLS-1$
				if (!pushURIs.isEmpty()) {
					pushURI = pushURIs.get(0).toString();
				} else if (!fetchURIs.isEmpty()) {
					pushURI = fetchURIs.get(0).toString();
				}

				outw.println(
						String.format("%s\t%s (fetch)", remoteName, fetchURI)); //$NON-NLS-1$
				outw.println(
						String.format("%s\t%s (push)", remoteName, pushURI)); //$NON-NLS-1$
			} else {
				outw.println(remoteName);
			}
		}
	}

}
