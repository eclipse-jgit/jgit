/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com> and others
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
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(common = true, usage = "usage_reset")
class Reset extends TextBuiltin {

	@Option(name = "--soft", usage = "usage_resetSoft")
	private boolean soft = false;

	@Option(name = "--mixed", usage = "usage_resetMixed")
	private boolean mixed = false;

	@Option(name = "--hard", usage = "usage_resetHard")
	private boolean hard = false;

	@Argument(required = false, index = 0, metaVar = "metaVar_commitish", usage = "usage_resetReference")
	private String commit;

	@Argument(required = false, index = 1, metaVar = "metaVar_paths")
	@Option(name = "--", metaVar = "metaVar_paths", handler = RestOfArgumentsHandler.class)
	private List<String> paths = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			ResetCommand command = git.reset();
			command.setRef(commit);
			if (!paths.isEmpty()) {
				for (String path : paths) {
					command.addPath(path);
				}
			} else {
				ResetType mode = null;
				if (soft) {
					mode = selectMode(mode, ResetType.SOFT);
				}
				if (mixed) {
					mode = selectMode(mode, ResetType.MIXED);
				}
				if (hard) {
					mode = selectMode(mode, ResetType.HARD);
				}
				if (mode == null) {
					throw die(CLIText.get().resetNoMode);
				}
				command.setMode(mode);
			}
			command.call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}

	private static ResetType selectMode(ResetType mode, ResetType want) {
		if (mode != null)
			throw die("reset modes are mutually exclusive, select one"); //$NON-NLS-1$
		return want;
	}
}
