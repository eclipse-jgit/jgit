/*
 * Copyright (C) 2010, 2025 Sasa Zivkov <sasa.zivkov@sap.com> and others
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

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_addFileContentsToTheIndex")
class Add extends TextBuiltin {

	@Option(name = "--renormalize", usage = "usage_addRenormalize")
	private boolean renormalize = false;

	@Option(name = "--update", aliases = { "-u" }, usage = "usage_onlyMatchAgainstAlreadyTrackedFiles")
	private boolean update = false;

	@Option(name = "--all", aliases = { "-A",
			"--no-ignore-removal" }, forbids = {
					"--no-all" }, usage = "usage_addStageDeletions")
	private Boolean all;

	@Option(name = "--no-all", aliases = { "--ignore-removal" }, forbids = {
			"--all" }, usage = "usage_addDontStageDeletions")
	private void noAll(@SuppressWarnings("unused") boolean ignored) {
		all = Boolean.FALSE;
	}

	@Argument(metaVar = "metaVar_filepattern", usage = "usage_filesToAddContentFrom")
	private List<String> filepatterns = new ArrayList<>();

	@Override
	protected void run() throws Exception {
		try (Git git = new Git(db)) {
			if (renormalize) {
				update = true;
			}
			if (update && all != null) {
				throw die(CLIText.get().addIncompatibleOptions);
			}
			AddCommand addCmd = git.add();
			addCmd.setUpdate(update).setRenormalize(renormalize);
			if (all != null) {
				addCmd.setAll(all.booleanValue());
			}
			for (String p : filepatterns) {
				addCmd.addFilepattern(p);
			}
			addCmd.call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}
