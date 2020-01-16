/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2016, Rüdiger Herrmann <ruediger.herrmann@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_CreateAnEmptyGitRepository")
class Init extends TextBuiltin {
	@Option(name = "--bare", usage = "usage_CreateABareRepository")
	private boolean bare;

	@Argument(index = 0, metaVar = "metaVar_directory")
	private String directory;

	/** {@inheritDoc} */
	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	protected void run() {
		InitCommand command = Git.init();
		command.setBare(bare);
		if (gitdir != null) {
			command.setDirectory(new File(gitdir));
		}
		if (directory != null) {
			command.setDirectory(new File(directory));
		}
		Repository repository;
		try {
			repository = command.call().getRepository();
			outw.println(MessageFormat.format(
					CLIText.get().initializedEmptyGitRepositoryIn,
					repository.getDirectory().getAbsolutePath()));
		} catch (GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
