/*
 * Copyright (C) 2016, Ned Twigg <ned.twigg@diffplug.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_Clean")
class Clean extends TextBuiltin {
	@Option(name = "-d", usage = "usage_removeUntrackedDirectories")
	private boolean dirs = false;

	@Option(name = "--force", aliases = {
			"-f" }, usage = "usage_forceClean")
	private boolean force = false;

	@Option(name = "--dryRun", aliases = { "-n" })
	private boolean dryRun = false;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			boolean requireForce = git.getRepository().getConfig()
					.getBoolean("clean", "requireForce", true); //$NON-NLS-1$ //$NON-NLS-2$
			if (requireForce && !(force || dryRun)) {
				throw die(CLIText.fatalError(CLIText.get().cleanRequireForce));
			}
			// Note that CleanCommand's setForce(true) will delete
			// .git folders. In the cgit cli, this behavior
			// requires setting "-f" twice, not sure how to do
			// this with args4j, so this feature is unimplemented
			// for now.
			Set<String> removedFiles = git.clean().setCleanDirectories(dirs)
					.setDryRun(dryRun).call();
			for (String removedFile : removedFiles) {
				outw.println(MessageFormat.format(CLIText.get().removing,
						removedFile));
			}
		} catch (NoWorkTreeException | GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}
}
