/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> and others
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

/**
 * Used to delete one or several tags.
 *
 * The result of {@link #call()} is a list with the (full) names of the deleted
 * tags.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-tag.html"
 *      >Git documentation about Tag</a>
 */
public class DeleteTagCommand extends GitCommand<List<String>> {

	private final Set<String> tags = new HashSet<>();

	/**
	 * Constructor for DeleteTagCommand
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected DeleteTagCommand(Repository repo) {
		super(repo);
	}

	/** {@inheritDoc} */
	@Override
	public List<String> call() throws GitAPIException {
		checkCallable();
		List<String> result = new ArrayList<>();
		if (tags.isEmpty())
			return result;
		try {
			setCallable(false);
			for (String tagName : tags) {
				if (tagName == null)
					continue;
				Ref currentRef = repo.findRef(tagName);
				if (currentRef == null)
					continue;
				String fullName = currentRef.getName();
				RefUpdate update = repo.updateRef(fullName);
				update.setForceUpdate(true);
				Result deleteResult = update.delete();

				boolean ok = true;
				switch (deleteResult) {
				case IO_FAILURE:
				case LOCK_FAILURE:
				case REJECTED:
					ok = false;
					break;
				default:
					break;
				}

				if (ok) {
					result.add(fullName);
				} else
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().deleteTagUnexpectedResult,
							deleteResult.name()));
			}
			return result;
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		}
	}

	/**
	 * Set names of the tags to delete
	 *
	 * @param tags
	 *            the names of the tags to delete; if not set, this will do
	 *            nothing; invalid tag names will simply be ignored
	 * @return this instance
	 */
	public DeleteTagCommand setTags(String... tags) {
		checkCallable();
		this.tags.clear();
		this.tags.addAll(Arrays.asList(tags));
		return this;
	}
}
