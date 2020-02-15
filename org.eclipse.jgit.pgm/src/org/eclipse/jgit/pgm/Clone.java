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
import java.util.Collection;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
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

	@Option(name = "--recurse-submodules", usage = "usage_recurseSubmodules")
	private boolean cloneSubmodules;

	@Argument(index = 0, required = true, metaVar = "metaVar_uriish")
	private String sourceUri;

	@Argument(index = 1, metaVar = "metaVar_directory")
	private String localName;

	/** {@inheritDoc} */
	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	/** {@inheritDoc} */
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
				.setMirror(isMirror)
				.setNoCheckout(noCheckout).setBranch(branch)
				.setCloneSubmodules(cloneSubmodules);

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

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
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
