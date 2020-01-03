/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2014, Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;

/**
 * Used to obtain a list of branches.
 * <p>
 * In case HEAD is detached (it points directly to a commit), it is also
 * returned in the results.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-branch.html"
 *      >Git documentation about Branch</a>
 */
public class ListBranchCommand extends GitCommand<List<Ref>> {
	private ListMode listMode;

	private String containsCommitish;

	/**
	 * The modes available for listing branches (corresponding to the -r and -a
	 * options)
	 */
	public enum ListMode {
		/**
		 * Corresponds to the -a option (all branches)
		 */
		ALL,
		/**
		 * Corresponds to the -r option (remote branches only)
		 */
		REMOTE;
	}

	/**
	 * Constructor for ListBranchCommand.
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected ListBranchCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> call() throws GitAPIException {
		checkCallable();
		List<Ref> resultRefs;
		try {
			Collection<Ref> refs = new ArrayList<>();

			// Also return HEAD if it's detached
			Ref head = repo.exactRef(HEAD);
			if (head != null && head.getLeaf().getName().equals(HEAD)) {
				refs.add(head);
			}

			if (listMode == null) {
				refs.addAll(repo.getRefDatabase().getRefsByPrefix(R_HEADS));
			} else if (listMode == ListMode.REMOTE) {
				refs.addAll(repo.getRefDatabase().getRefsByPrefix(R_REMOTES));
			} else {
				refs.addAll(repo.getRefDatabase().getRefsByPrefix(R_HEADS,
						R_REMOTES));
			}
			resultRefs = new ArrayList<>(filterRefs(refs));
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		Collections.sort(resultRefs,
				(Ref o1, Ref o2) -> o1.getName().compareTo(o2.getName()));
		setCallable(false);
		return resultRefs;
	}

	private Collection<Ref> filterRefs(Collection<Ref> refs)
			throws RefNotFoundException, IOException {
		if (containsCommitish == null)
			return refs;

		try (RevWalk walk = new RevWalk(repo)) {
			ObjectId resolved = repo.resolve(containsCommitish);
			if (resolved == null)
				throw new RefNotFoundException(MessageFormat.format(
						JGitText.get().refNotResolved, containsCommitish));

			RevCommit containsCommit = walk.parseCommit(resolved);
			return RevWalkUtils.findBranchesReachableFrom(containsCommit, walk,
					refs);
		}
	}

	/**
	 * Set the list mode
	 *
	 * @param listMode
	 *            optional: corresponds to the -r/-a options; by default, only
	 *            local branches will be listed
	 * @return this instance
	 */
	public ListBranchCommand setListMode(ListMode listMode) {
		checkCallable();
		this.listMode = listMode;
		return this;
	}

	/**
	 * If this is set, only the branches that contain the specified commit-ish
	 * as an ancestor are returned.
	 *
	 * @param containsCommitish
	 *            a commit ID or ref name
	 * @return this instance
	 * @since 3.4
	 */
	public ListBranchCommand setContains(String containsCommitish) {
		checkCallable();
		this.containsCommitish = containsCommitish;
		return this;
	}
}
