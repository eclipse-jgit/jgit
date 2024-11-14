/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
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

@Command(common = true, usage = "usage_PackRefs")
class PackRefs extends TextBuiltin {
	@Option(name = "--all", usage = "usage_All")
	private boolean all;

	@Override
	protected void run() {
		Git git = Git.wrap(db);
		try {
			git.packRefs().setProgressMonitor(new TextProgressMonitor(errw))
					.setAll(all).call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}
