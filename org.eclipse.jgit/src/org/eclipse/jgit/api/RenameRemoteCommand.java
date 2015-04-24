/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Used to rename remotes.
 *
 * @see <a
 *      href="https://www.kernel.org/pub/software/scm/git/docs/git-remote.html"
 *      >Git documentation about Remote</a>
 */
public class RenameRemoteCommand extends GitCommand<RemoteConfig> {
	private String oldName;

	private String newName;

	/**
	 * @param repo
	 */
	protected RenameRemoteCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @throws RefNotFoundException
	 *             if the old branch can not be found (branch with provided old
	 *             name does not exist or old name resolves to a tag)
	 * @throws InvalidRefNameException
	 *             if the provided new name is <code>null</code> or otherwise
	 *             invalid
	 * @throws RefAlreadyExistsException
	 *             if a branch with the new name already exists
	 */
	public RemoteConfig call() throws GitAPIException, RefNotFoundException,
			InvalidRefNameException, RefAlreadyExistsException {
		checkCallable();

		if (newName == null)
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().remoteNameInvalid, "<null>")); //$NON-NLS-1$

		try {
			final StoredConfig config = repo.getConfig();

			if (!Repository.isValidRefName(Constants.R_REMOTES + newName))
				throw new InvalidRefNameException(MessageFormat.format(
						JGitText.get().remoteNameInvalid, newName));

			// copy old remote with new name
			if (config.getNames(ConfigConstants.CONFIG_REMOTE_SECTION, newName)
					.size() > 0) {
				throw new RefAlreadyExistsException(MessageFormat.format(
						JGitText.get().refAlreadyExists1, newName));
			}
			if (config.getNames(ConfigConstants.CONFIG_REMOTE_SECTION, oldName)
					.size() == 0) {
				throw new RefNotFoundException(MessageFormat.format(
						JGitText.get().remoteNotFound, oldName));
			}

			final RemoteConfig newRemote = new RemoteConfig(config, oldName);
			newRemote.setName(newName);

			int fetchUpdateCount = 0;

			// change fetch if standard
			final String oldFetchName = Constants.R_REMOTES + oldName
					+ RefSpec.WILDCARD_SUFFIX;
			final List<RefSpec> fetches = newRemote.getFetchRefSpecs();

			for (RefSpec fetch : fetches) {
				if (fetch.matchDestination(oldFetchName)) {
					newRemote.addFetchRefSpec(fetch
							.setDestination(Constants.R_REMOTES + newName
									+ RefSpec.WILDCARD_SUFFIX));
					newRemote.removeFetchRefSpec(fetch);
					fetchUpdateCount++;
				}
			}

			// verify new remote exists
			newRemote.update(config);

			if (config.getNames(ConfigConstants.CONFIG_REMOTE_SECTION, newName)
					.size() == 0) {
				throw new JGitInternalException(
						JGitText.get().renameRemoteFailedUnknownReason);
			}

			// remove old remote
			config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, oldName);

			// change remotes if fetch was updated
			if (fetchUpdateCount > 0) {
				final Collection<Ref> refs = new ArrayList<Ref>();

				refs.addAll(getRefs(Constants.R_REMOTES));

				final String oldRemoteBranchName = Constants.R_REMOTES
						+ oldName + "/"; //$NON-NLS-1$
				final String newRemoteBranchName = Constants.R_REMOTES
						+ newName + "/"; //$NON-NLS-1$

				for (Ref branch : refs) {
					final String remoteBranchName = branch.getName();
					final String branchName = remoteBranchName.split("/", 4)[3]; //$NON-NLS-1$

					// rename all remote tracking branches using new name
					if (remoteBranchName.startsWith(oldRemoteBranchName)) {
						final Result renameResult = repo.renameRef(
								remoteBranchName,
								newRemoteBranchName + branchName).rename();

						if (renameResult != Result.RENAMED) {
							throw new JGitInternalException(
									MessageFormat.format(
											JGitText.get().renameBranchUnexpectedResult,
											renameResult.name()));
						}
					}

					// change all branches with old remote to new one
					final String remote = config.getString(
							ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
							ConfigConstants.CONFIG_KEY_REMOTE);

					if (oldName.equals(remote)) {
						config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
								branchName, ConfigConstants.CONFIG_KEY_REMOTE,
								newName);
					}
				}
			}

			return newRemote;
		} catch (URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * @param newName
	 *            the new name
	 * @return this instance
	 */
	public RenameRemoteCommand setNewName(String newName) {
		checkCallable();
		this.newName = newName;
		return this;
	}

	/**
	 * @param oldName
	 *            the name of the remote to rename
	 * @return this instance
	 */
	public RenameRemoteCommand setOldName(String oldName) {
		checkCallable();
		this.oldName = oldName;
		return this;
	}

	private Collection<Ref> getRefs(String prefix) throws IOException {
		return repo.getRefDatabase().getRefs(prefix).values();
	}
}
