/*
 * Copyright (C) 2012, Tomasz Zarna <tomasz.zarna@tasktop.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ReflogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;

@Command(common = true, usage = "usage_manageReflogInformation")
class Reflog extends TextBuiltin {

	@Argument(metaVar = "metaVar_ref")
	private String ref;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			ReflogCommand cmd = git.reflog();
			if (ref != null)
				cmd.setRef(ref);
			Collection<ReflogEntry> entries = cmd.call();
			int i = 0;
			for (ReflogEntry entry : entries) {
				outw.println(toString(entry, i++));
			}
		} catch (GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	private String toString(ReflogEntry entry, int i) {
		final StringBuilder s = new StringBuilder();
		s.append(entry.getNewId().abbreviate(7).name());
		s.append(" "); //$NON-NLS-1$
		s.append(ref == null ? Constants.HEAD : Repository.shortenRefName(ref));
		s.append("@{" + i + "}:"); //$NON-NLS-1$ //$NON-NLS-2$
		s.append(" "); //$NON-NLS-1$
		s.append(entry.getComment());
		return s.toString();
	}
}
