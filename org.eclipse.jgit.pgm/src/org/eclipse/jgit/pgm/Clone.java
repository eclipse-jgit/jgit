/*
 * Copyright (C) 2008-2010, Google Inc.
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

	@Option(name = "--quiet", usage = "usage_quiet")
	private Boolean quiet;

	@Option(name = "--recurse-submodules", usage = "usage_recurseSubmodules")
	private boolean cloneSubmodules;

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
				if (isBare) {
					localName = localName + Constants.DOT_GIT_EXT;
				}
				localNameF = new File(SystemReader.getInstance().getProperty(
						Constants.OS_USER_DIR), localName);
			} catch (IllegalArgumentException e) {
				throw die(MessageFormat.format(
						CLIText.get().cannotGuessLocalNameFrom, sourceUri));
			}
		} else
			localNameF = new File(localName);

		if (branch == null)
			branch = Constants.HEAD;

		CloneCommand command = Git.cloneRepository();
		command.setURI(sourceUri).setRemote(remoteName).setBare(isBare)
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
					sourceUri));
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
