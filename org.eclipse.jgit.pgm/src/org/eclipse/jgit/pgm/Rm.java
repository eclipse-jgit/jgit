/*
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StopOptionHandler;

@Command(usage = "usage_StopTrackingAFile", common = true)
class Rm extends TextBuiltin {
	@Argument(metaVar = "metaVar_path", usage = "usage_path", required = true)
	@Option(name = "--", handler = StopOptionHandler.class)
	private List<String> paths = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			RmCommand command = git.rm();
			for (String p : paths) {
				command.addFilepattern(p);
			}
			command.call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}
