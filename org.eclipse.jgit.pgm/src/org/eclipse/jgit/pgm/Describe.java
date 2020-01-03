/*
 * Copyright (C) 2013, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_Describe")
class Describe extends TextBuiltin {

	@Argument(index = 0, metaVar = "metaVar_treeish")
	private ObjectId tree;

	@Option(name = "--long", usage = "usage_LongFormat")
	private boolean longDesc;

	@Option(name = "--tags", usage = "usage_UseTags")
	private boolean useTags;

	@Option(name = "--always", usage = "usage_AlwaysFallback")
	private boolean always;

	@Option(name = "--match", usage = "usage_Match", metaVar = "metaVar_pattern")
	private List<String> patterns = new ArrayList<>();

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			DescribeCommand cmd = git.describe();
			if (tree != null) {
				cmd.setTarget(tree);
			}
			cmd.setLong(longDesc);
			cmd.setTags(useTags);
			cmd.setAlways(always);
			cmd.setMatch(patterns.toArray(new String[0]));
			String result = null;
			try {
				result = cmd.call();
			} catch (RefNotFoundException e) {
				throw die(CLIText.get().noNamesFound, e);
			}
			if (result == null) {
				throw die(CLIText.get().noNamesFound);
			}

			outw.println(result);
		} catch (IOException | InvalidPatternException | GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}
