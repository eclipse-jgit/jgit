/*
 * Copyright (C) 2011, Ketan Padegaonkar <ketanpadegaonkar@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Used to obtain a list of tags.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
 *      >Git documentation about Tag</a>
 */
public class ListTagCommand extends GitCommand<List<Ref>> {

	private final RevWalk rw;
	private RevCommit commit;

	/**
	 * Constructor for ListTagCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected ListTagCommand(Repository repo) {
		super(repo);
		rw = new RevWalk(repo);
	}

	/**
	 * Only list tags which contain the specified commit.
	 *
	 * @param commit
	 *            the specified commit
	 * @return this command
	 * @throws IOException
	 *             if an IO error occurred
	 * @throws IncorrectObjectTypeException
	 *             if commit has an incorrect object type
	 * @throws MissingObjectException
	 *             if the commit is missing
	 *
	 * @since 6.6
	 */
	public ListTagCommand setContains(AnyObjectId commit)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		checkCallable();
		this.commit = rw.parseCommit(commit);
		return this;
	}

	@Override
	public List<Ref> call() throws GitAPIException {
		checkCallable();
		List<Ref> tags;
		try {
			List<Ref> refList = repo.getRefDatabase()
					.getRefsByPrefix(Constants.R_TAGS);
			if (commit != null) {
				// if body is retained #getMergedInto needs to access data not
				// available in commit graph which is slower
				rw.setRetainBody(false);
				tags = rw.getMergedInto(commit, refList);
			} else {
				tags = new ArrayList<>(refList);
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} finally {
			rw.close();
		}
		Collections.sort(tags,
				(Ref o1, Ref o2) -> o1.getName().compareTo(o2.getName()));
		setCallable(false);
		return tags;
	}

}
