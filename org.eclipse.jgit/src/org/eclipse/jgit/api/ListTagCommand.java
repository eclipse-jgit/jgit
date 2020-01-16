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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Used to obtain a list of tags.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
 *      >Git documentation about Tag</a>
 */
public class ListTagCommand extends GitCommand<List<Ref>> {

	/**
	 * Constructor for ListTagCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected ListTagCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> call() throws GitAPIException {
		checkCallable();
		List<Ref> tags = new ArrayList<>();
		try (RevWalk revWalk = new RevWalk(repo)) {
			List<Ref> refList = repo.getRefDatabase()
					.getRefsByPrefix(Constants.R_TAGS);
			for (Ref ref : refList) {
				tags.add(ref);
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		Collections.sort(tags,
				(Ref o1, Ref o2) -> o1.getName().compareTo(o2.getName()));
		setCallable(false);
		return tags;
	}

}
