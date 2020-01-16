/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_Gc")
class Gc extends TextBuiltin {
	@Option(name = "--aggressive", usage = "usage_Aggressive")
	private boolean aggressive;

	@Option(name = "--preserve-oldpacks", usage = "usage_PreserveOldPacks")
	private boolean preserveOldPacks;

	@Option(name = "--prune-preserved", usage = "usage_PrunePreserved")
	private boolean prunePreserved;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		Git git = Git.wrap(db);
		try {
			git.gc().setAggressive(aggressive)
					.setPreserveOldPacks(preserveOldPacks)
					.setPrunePreserved(prunePreserved)
					.setProgressMonitor(new TextProgressMonitor(errw)).call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}
