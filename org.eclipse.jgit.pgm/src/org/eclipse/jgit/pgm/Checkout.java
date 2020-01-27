/*
 * Copyright (C) 2010, 2012 Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2013, Obeo and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;

@Command(common = true, usage = "usage_checkout")
class Checkout extends TextBuiltin {

	@Option(name = "-b", usage = "usage_createBranchAndCheckout")
	private boolean createBranch = false;

	@Option(name = "-B", usage = "usage_forcedSwitchBranch")
	private boolean forceSwitchBranch = false;

	@Option(name = "--force", aliases = { "-f" }, usage = "usage_forceCheckout")
	private boolean forced = false;

	@Option(name = "--orphan", usage = "usage_orphan")
	private boolean orphan = false;

	@Argument(required = false, index = 0, metaVar = "metaVar_name", usage = "usage_checkout")
	private String name;

	@Option(name = "--", metaVar = "metaVar_paths", handler = RestOfArgumentsHandler.class)
	private List<String> paths = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		if (createBranch) {
			final ObjectId head = db.resolve(Constants.HEAD);
			if (head == null)
				throw die(CLIText.get().onBranchToBeBorn);
		}

		try (Git git = new Git(db)) {
			CheckoutCommand command = git.checkout()
					.setProgressMonitor(new TextProgressMonitor(errw));
			if (!paths.isEmpty()) {
				command.setStartPoint(name);
				if (paths.size() == 1 && paths.get(0).equals(".")) { //$NON-NLS-1$
					command.setAllPaths(true);
				} else {
					command.addPaths(paths);
				}
			} else {
				command.setCreateBranch(createBranch);
				command.setName(name);
				command.setForceRefUpdate(forceSwitchBranch);
				command.setForced(forced);
				command.setOrphan(orphan);
			}
			try {
				String oldBranch = db.getBranch();
				Ref ref = command.call();
				if (ref == null)
					return;
				if (Repository.shortenRefName(ref.getName()).equals(oldBranch)) {
					outw.println(MessageFormat.format(
							CLIText.get().alreadyOnBranch,
							name));
					return;
				}
				if (createBranch || orphan)
					outw.println(MessageFormat.format(
							CLIText.get().switchedToNewBranch, name));
				else
					outw.println(MessageFormat.format(
							CLIText.get().switchedToBranch,
							Repository.shortenRefName(ref.getName())));
			} catch (RefNotFoundException e) {
				throw die(MessageFormat
						.format(CLIText.get().pathspecDidNotMatch, name), e);
			} catch (RefAlreadyExistsException e) {
				throw die(MessageFormat
						.format(CLIText.get().branchAlreadyExists, name), e);
			} catch (CheckoutConflictException e) {
				StringBuilder builder = new StringBuilder();
				builder.append(CLIText.get().checkoutConflict);
				builder.append(System.lineSeparator());
				for (String path : e.getConflictingPaths()) {
					builder.append(MessageFormat.format(
							CLIText.get().checkoutConflictPathLine, path));
					builder.append(System.lineSeparator());
				}
				throw die(builder.toString(), e);
			}
		}
	}
}
