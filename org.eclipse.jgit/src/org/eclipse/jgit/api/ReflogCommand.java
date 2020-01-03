/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com> and others
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
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;

/**
 * The reflog command
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-reflog.html"
 *      >Git documentation about reflog</a>
 */
public class ReflogCommand extends GitCommand<Collection<ReflogEntry>> {

	private String ref = Constants.HEAD;

	/**
	 * Constructor for ReflogCommand.
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	public ReflogCommand(Repository repo) {
		super(repo);
	}

	/**
	 * The ref used for the reflog operation. If no ref is set, the default
	 * value of HEAD will be used.
	 *
	 * @param ref
	 *            the name of the {@code Ref} to log
	 * @return {@code this}
	 */
	public ReflogCommand setRef(String ref) {
		checkCallable();
		this.ref = ref;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Run the reflog command
	 */
	@Override
	public Collection<ReflogEntry> call() throws GitAPIException,
			InvalidRefNameException {
		checkCallable();

		try {
			ReflogReader reader = repo.getReflogReader(ref);
			if (reader == null)
				throw new RefNotFoundException(MessageFormat.format(
						JGitText.get().refNotResolved, ref));
			return reader.getReverseEntries();
		} catch (IOException e) {
			throw new InvalidRefNameException(MessageFormat.format(
					JGitText.get().cannotRead, ref), e);
		}
	}

}
