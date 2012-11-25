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
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

/**
 * Used to rename branches.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-branch.html"
 *      >Git documentation about Branch</a>
 */
public class RenameBranchCommand extends GitCommand<Ref> {
	private String oldName;

	private String newName;

	/**
	 * @param repo
	 */
	protected RenameBranchCommand(Repository repo) {
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
	 * @throws DetachedHeadException
	 *             if rename is tried without specifying the old name and HEAD
	 *             is detached
	 */
	public Ref call() throws GitAPIException, RefNotFoundException, InvalidRefNameException,
			RefAlreadyExistsException, DetachedHeadException {
		checkCallable();

		if (newName == null)
			throw new InvalidRefNameException(MessageFormat.format(JGitText
					.get().branchNameInvalid, "<null>")); //$NON-NLS-1$

		try {
			String fullOldName;
			String fullNewName;
			if (repo.getRef(newName) != null)
				throw new RefAlreadyExistsException(MessageFormat.format(
						JGitText.get().refAlreadyExists1, newName));
			if (oldName != null) {
				Ref ref = repo.getRef(oldName);
				if (ref == null)
					throw new RefNotFoundException(MessageFormat.format(
							JGitText.get().refNotResolved, oldName));
				if (ref.getName().startsWith(Constants.R_TAGS))
					throw new RefNotFoundException(MessageFormat.format(
							JGitText.get().renameBranchFailedBecauseTag,
							oldName));
				fullOldName = ref.getName();
			} else {
				fullOldName = repo.getFullBranch();
				if (ObjectId.isId(fullOldName))
					throw new DetachedHeadException();
			}

			if (fullOldName.startsWith(Constants.R_REMOTES))
				fullNewName = Constants.R_REMOTES + newName;
			else {
				fullNewName = Constants.R_HEADS + newName;
			}

			if (!Repository.isValidRefName(fullNewName))
				throw new InvalidRefNameException(MessageFormat.format(JGitText
						.get().branchNameInvalid, fullNewName));

			RefRename rename = repo.renameRef(fullOldName, fullNewName);
			Result renameResult = rename.rename();

			setCallable(false);

			if (Result.RENAMED != renameResult)
				throw new JGitInternalException(MessageFormat.format(JGitText
						.get().renameBranchUnexpectedResult, renameResult
						.name()));

			if (fullNewName.startsWith(Constants.R_HEADS)) {
				String shortOldName = fullOldName.substring(Constants.R_HEADS
						.length());
				final StoredConfig repoConfig = repo.getConfig();
				// Copy all configuration values over to the new branch
				for (String name : repoConfig.getNames(
						ConfigConstants.CONFIG_BRANCH_SECTION, shortOldName)) {
					String[] values = repoConfig.getStringList(
							ConfigConstants.CONFIG_BRANCH_SECTION,
							shortOldName, name);
					if (values.length == 0)
						continue;
					// Keep any existing values already configured for the
					// new branch name
					String[] existing = repoConfig.getStringList(
							ConfigConstants.CONFIG_BRANCH_SECTION, newName,
							name);
					if (existing.length > 0) {
						String[] newValues = new String[values.length
								+ existing.length];
						System.arraycopy(existing, 0, newValues, 0,
								existing.length);
						System.arraycopy(values, 0, newValues, existing.length,
								values.length);
						values = newValues;
					}

					repoConfig.setStringList(
							ConfigConstants.CONFIG_BRANCH_SECTION, newName,
							name, Arrays.asList(values));
				}
				repoConfig.unsetSection(ConfigConstants.CONFIG_BRANCH_SECTION,
						shortOldName);
				repoConfig.save();
			}

			Ref resultRef = repo.getRef(newName);
			if (resultRef == null)
				throw new JGitInternalException(
						JGitText.get().renameBranchFailedUnknownReason);
			return resultRef;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * @param newName
	 *            the new name
	 * @return this instance
	 */
	public RenameBranchCommand setNewName(String newName) {
		checkCallable();
		this.newName = newName;
		return this;
	}

	/**
	 * @param oldName
	 *            the name of the branch to rename; if not set, the currently
	 *            checked out branch (if any) will be renamed
	 * @return this instance
	 */
	public RenameBranchCommand setOldName(String oldName) {
		checkCallable();
		this.oldName = oldName;
		return this;
	}
}
