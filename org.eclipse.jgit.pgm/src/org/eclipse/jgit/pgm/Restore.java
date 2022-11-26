/*
 * Copyright (C) 2022 Yuriy Mitrofanov <mitr15fan15v@gmail.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RestoreCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StopOptionHandler;

import java.util.ArrayList;
import java.util.List;

@Command(usage = "usage_RestoreWorkingTreeFile", common = true)
class Restore extends TextBuiltin {
	@Argument(metaVar = "metaVar_path", usage = "usage_path", required = true)
	@Option(name = "--", handler = StopOptionHandler.class)
	private List<String> paths = new ArrayList<>();

	@Option(name = "--cached", aliases = { "--staged" }, usage = "usage_cached")
	private boolean cached;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			RestoreCommand command = git.restore();
			command.setCached(cached);
			for (String p : paths) {
				command.addFilepattern(p);
			}
			command.call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}
