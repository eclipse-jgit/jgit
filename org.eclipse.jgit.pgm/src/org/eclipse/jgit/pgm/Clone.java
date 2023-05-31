/*
 * Copyright (C) 2008-2010, Google Inc. and others
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.SystemReader;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_cloneRepositoryIntoNewDir")
class Clone extends AbstractFetchCommand implements CloneCommand.Callback {
	@Option(name = "--origin", aliases = { "-o" }, metaVar = "metaVar_remoteName", usage = "usage_useNameInsteadOfOriginToTrackUpstream")
	private String remoteName = Constants.DEFAULT_REMOTE_NAME;

	@Option(name = "--branch", aliases = { "-b" }, metaVar = "metaVar_branchName", usage = "usage_checkoutBranchAfterClone")
	private String branch;

	@Option(name = "--no-checkout", aliases = { "-n" }, usage = "usage_noCheckoutAfterClone")
	private boolean noCheckout;

	@Option(name = "--bare", usage = "usage_bareClone")
	private boolean isBare;

	@Option(name = "--mirror", usage = "usage_mirrorClone")
	private boolean isMirror;

	@Option(name = "--quiet", usage = "usage_quiet")
	private Boolean quiet;

	@Option(name = "--depth", metaVar = "metaVar_depth", usage = "usage_depth")
	private Integer depth = null;

	@Option(name = "--shallow-since", metaVar = "metaVar_shallowSince", usage = "usage_shallowSince")
	private Instant shallowSince = null;

	@Option(name = "--shallow-exclude", metaVar = "metaVar_shallowExclude", usage = "usage_shallowExclude")
	private List<String> shallowExcludes = new ArrayList<>();

	@Option(name = "--recurse-submodules", usage = "usage_recurseSubmodules")
	private boolean cloneSubmodules;

	@Option(name = "--timeout", metaVar = "metaVar_seconds", usage = "usage_abortConnectionIfNoActivity")
	int timeout = -1;

	@Argument(index = 0, required = true, metaVar = "metaVar_uriish")
	private String sourceUri;

	@Argument(index = 1, metaVar = "metaVar_directory")
	private String localName;

	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws Exception {
		if (localName != null && gitdir != null)
			throw die(CLIText.get().conflictingUsageOf_git_dir_andArguments);

		final URIish uri = new URIish(sourceUri);
		File localNameF;
		if (localName == null) {
			try {
				localName = uri.getHumanishName();
				if (isBare || isMirror) {
					localName = localName + Constants.DOT_GIT_EXT;
				}
				localNameF = new File(SystemReader.getInstance().getProperty(
						Constants.OS_USER_DIR), localName);
			} catch (IllegalArgumentException e) {
				throw die(MessageFormat.format(
						CLIText.get().cannotGuessLocalNameFrom, sourceUri), e);
			}
		} else
			localNameF = new File(localName);

		if (branch == null)
			branch = Constants.HEAD;

		CloneCommand command = Git.cloneRepository();
		command.setURI(sourceUri).setRemote(remoteName).setBare(isBare)
				.setMirror(isMirror).setNoCheckout(noCheckout).setBranch(branch)
				.setCloneSubmodules(cloneSubmodules).setTimeout(timeout);

		if (depth != null) {
			command.setDepth(depth.intValue());
		}
		if (shallowSince != null) {
			command.setShallowSince(shallowSince);
		}
		for (String shallowExclude : shallowExcludes) {
			command.addShallowExclude(shallowExclude);
		}

		command.setGitDir(gitdir == null ? null : new File(gitdir));
		command.setDirectory(localNameF);
		boolean msgs = quiet == null || !quiet.booleanValue();
		if (msgs) {
			command.setProgressMonitor(new TextProgressMonitor(errw))
					.setCallback(this);
			outw.println(MessageFormat.format(
					CLIText.get().cloningInto, localName));
			outw.flush();
		}
		try {
			db = command.call().getRepository();
			if (msgs && db.resolve(Constants.HEAD) == null)
				outw.println(CLIText.get().clonedEmptyRepository);
		} catch (TransportException e) {
			throw die(e.getMessage(), e);
		} catch (InvalidRemoteException e) {
			throw die(MessageFormat.format(CLIText.get().doesNotExist,
					sourceUri), e);
		} finally {
			if (db != null)
				db.close();
		}
		if (msgs) {
			outw.println();
			outw.flush();
		}
	}

	@Override
	public void initializedSubmodules(Collection<String> submodules) {
		try {
			for (String submodule : submodules) {
				outw.println(MessageFormat
						.format(CLIText.get().submoduleRegistered, submodule));
			}
			outw.flush();
		} catch (IOException e) {
			// ignore
		}
	}

	@Override
	public void cloningSubmodule(String path) {
		try {
			outw.println(MessageFormat.format(
					CLIText.get().cloningInto, path));
			outw.flush();
		} catch (IOException e) {
			// ignore
		}
	}

	@Override
	public void checkingOut(AnyObjectId commit, String path) {
		try {
			outw.println(MessageFormat.format(CLIText.get().checkingOut,
					path, commit.getName()));
			outw.flush();
		} catch (IOException e) {
			// ignore
		}
	}
}
