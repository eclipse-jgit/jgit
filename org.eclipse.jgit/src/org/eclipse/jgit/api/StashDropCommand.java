/*
 * Copyright (C) 2012, GitHub Inc.
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

import static org.eclipse.jgit.lib.Constants.R_STASH;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.ReflogEntry;
import org.eclipse.jgit.internal.storage.file.ReflogReader;
import org.eclipse.jgit.internal.storage.file.ReflogWriter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * Command class to delete a stashed commit reference
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 * @since 2.0
 */
public class StashDropCommand extends GitCommand<ObjectId> {

	private int stashRefEntry;

	private boolean all;

	/**
	 * @param repo
	 */
	public StashDropCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the stash reference to drop (0-based).
	 * <p>
	 * This will default to drop the latest stashed commit (stash@{0}) if
	 * unspecified
	 *
	 * @param stashRef
	 * @return {@code this}
	 */
	public StashDropCommand setStashRef(final int stashRef) {
		if (stashRef < 0)
			throw new IllegalArgumentException();

		stashRefEntry = stashRef;
		return this;
	}

	/**
	 * Set wheter drop all stashed commits
	 *
	 * @param all
	 *            true to drop all stashed commits, false to drop only the
	 *            stashed commit set via calling {@link #setStashRef(int)}
	 * @return {@code this}
	 */
	public StashDropCommand setAll(final boolean all) {
		this.all = all;
		return this;
	}

	private Ref getRef() throws GitAPIException {
		try {
			return repo.getRef(R_STASH);
		} catch (IOException e) {
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().cannotRead, R_STASH), e);
		}
	}

	private RefUpdate createRefUpdate(final Ref stashRef) throws IOException {
		RefUpdate update = repo.updateRef(R_STASH);
		update.disableRefLog();
		update.setExpectedOldObjectId(stashRef.getObjectId());
		update.setForceUpdate(true);
		return update;
	}

	private void deleteRef(final Ref stashRef) {
		try {
			Result result = createRefUpdate(stashRef).delete();
			if (Result.FORCED != result)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().stashDropDeleteRefFailed, result));
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashDropFailed, e);
		}
	}

	private void updateRef(Ref stashRef, ObjectId newId) {
		try {
			RefUpdate update = createRefUpdate(stashRef);
			update.setNewObjectId(newId);
			Result result = update.update();
			switch (result) {
			case FORCED:
			case NEW:
			case NO_CHANGE:
				return;
			default:
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().updatingRefFailed, R_STASH, newId,
						result));
			}
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashDropFailed, e);
		}
	}

	/**
	 * Drop the configured entry from the stash reflog and return value of the
	 * stash reference after the drop occurs
	 *
	 * @return commit id of stash reference or null if no more stashed changes
	 * @throws GitAPIException
	 */
	public ObjectId call() throws GitAPIException {
		checkCallable();

		Ref stashRef = getRef();
		if (stashRef == null)
			return null;

		if (all) {
			deleteRef(stashRef);
			return null;
		}

		ReflogReader reader = new ReflogReader(repo, R_STASH);
		List<ReflogEntry> entries;
		try {
			entries = reader.getReverseEntries();
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashDropFailed, e);
		}

		if (stashRefEntry >= entries.size())
			throw new JGitInternalException(
					JGitText.get().stashDropMissingReflog);

		if (entries.size() == 1) {
			deleteRef(stashRef);
			return null;
		}

		ReflogWriter writer = new ReflogWriter(repo, true);
		String stashLockRef = ReflogWriter.refLockFor(R_STASH);
		File stashLockFile = writer.logFor(stashLockRef);
		File stashFile = writer.logFor(R_STASH);
		if (stashLockFile.exists())
			throw new JGitInternalException(JGitText.get().stashDropFailed,
					new LockFailedException(stashFile));

		entries.remove(stashRefEntry);
		ObjectId entryId = ObjectId.zeroId();
		try {
			for (int i = entries.size() - 1; i >= 0; i--) {
				ReflogEntry entry = entries.get(i);
				writer.log(stashLockRef, entryId, entry.getNewId(),
						entry.getWho(), entry.getComment());
				entryId = entry.getNewId();
			}
			if (!stashLockFile.renameTo(stashFile)) {
				FileUtils.delete(stashFile);
				if (!stashLockFile.renameTo(stashFile))
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().couldNotWriteFile,
							stashLockFile.getPath(), stashFile.getPath()));
			}
		} catch (IOException e) {
			throw new JGitInternalException(JGitText.get().stashDropFailed, e);
		}
		updateRef(stashRef, entryId);

		try {
			Ref newStashRef = repo.getRef(R_STASH);
			return newStashRef != null ? newStashRef.getObjectId() : null;
		} catch (IOException e) {
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().cannotRead, R_STASH), e);
		}
	}
}
