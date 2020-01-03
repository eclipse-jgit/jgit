/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Command class to list the stashed commits in a repository.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-stash.html"
 *      >Git documentation about Stash</a>
 */
public class StashListCommand extends GitCommand<Collection<RevCommit>> {

	/**
	 * Create a new stash list command
	 *
	 * @param repo a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public StashListCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public Collection<RevCommit> call() throws GitAPIException,
			InvalidRefNameException {
		checkCallable();

		try {
			if (repo.exactRef(Constants.R_STASH) == null)
				return Collections.emptyList();
		} catch (IOException e) {
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().cannotRead, Constants.R_STASH), e);
		}

		final ReflogCommand refLog = new ReflogCommand(repo);
		refLog.setRef(Constants.R_STASH);
		final Collection<ReflogEntry> stashEntries = refLog.call();
		if (stashEntries.isEmpty())
			return Collections.emptyList();

		final List<RevCommit> stashCommits = new ArrayList<>(
				stashEntries.size());
		try (RevWalk walk = new RevWalk(repo)) {
			for (ReflogEntry entry : stashEntries) {
				try {
					stashCommits.add(walk.parseCommit(entry.getNewId()));
				} catch (IOException e) {
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().cannotReadCommit, entry.getNewId()),
							e);
				}
			}
		}
		return stashCommits;
	}
}
