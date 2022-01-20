/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import org.eclipse.jgit.api.RemoteSetUrlCommand.UriType;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.stream.ThrowingPrintWriter;
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
	protected void run() {
		try (Git git = new Git(db)) {
			if (command == null) {
				RemoteListCommand cmd = git.remoteList();
				List<RemoteConfig> remotes = cmd.call();
				print(remotes);
				return;
			}
			switch (command) {
			case "add": //$NON-NLS-1$
				RemoteAddCommand add = git.remoteAdd();
				add.setName(name);
				add.setUri(new URIish(uri));
				add.call();
				break;
			case "remove": //$NON-NLS-1$
			case "rm": //$NON-NLS-1$
				RemoteRemoveCommand rm = git.remoteRemove();
				rm.setRemoteName(name);
				rm.call();
				break;
			case "set-url": //$NON-NLS-1$
				RemoteSetUrlCommand remoteSetUrl = git.remoteSetUrl();
				remoteSetUrl.setRemoteName(name);
				remoteSetUrl.setRemoteUri(new URIish(uri));
				remoteSetUrl.setUriType(push ? UriType.PUSH : UriType.FETCH);
				remoteSetUrl.call();
				break;
			case "update": //$NON-NLS-1$
				Fetch fetch = new Fetch();
				fetch.init(db, gitdir);
				StringWriter osw = new StringWriter();
				fetch.outw = new ThrowingPrintWriter(osw);
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
				fetch.outw.flush();
				fetch.errw.flush();
				outw.println(osw.toString());
				errw.println(esw.toString());
				break;
			default:
				throw new JGitInternalException(MessageFormat
						.format(CLIText.get().unknownSubcommand, command));
			}
		} catch (Exception e) {
			throw die(e.getMessage(), e);
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
