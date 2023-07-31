/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;

@Command(common = true, usage = "usage_convertRefStorage")
class ConvertRefStorage extends TextBuiltin {

	@Option(name = "--format", usage = "usage_convertRefStorageFormat")
	private String format = "reftable"; //$NON-NLS-1$

	@Option(name = "--backup", handler = ExplicitBooleanOptionHandler.class, aliases = {
			"-b" }, usage = "usage_convertRefStorageBackup")
	private boolean backup = true;

	@Option(name = "--reflogs", handler = ExplicitBooleanOptionHandler.class, aliases = {
			"-r" }, usage = "usage_convertRefStorageRefLogs")
	private boolean writeLogs = true;

	@Option(name = "--reps", usage = "usage_mergeRef")
	private  int reps = 1000;

	@Argument(required = true, metaVar = "metaVar_ref", usage = "usage_mergeRef")
	private String revision;

	@Argument(required = true, index = 1, metaVar = "metaVar_ref", usage = "usage_mergeRef")
	private String tip;

	@Override
	protected void run() throws Exception {
		int reachable = 0;
		for (int i = 0; i < reps; i++) {
			try (RevWalk rw = new RevWalk(db)) {
				RevCommit rc = rw.parseCommit(ObjectId.fromString(revision));

				RevCommit masterCommit = rw.parseCommit(ObjectId.fromString(tip));

				ReachabilityChecker checker = rw.getObjectReader().createReachabilityChecker(rw);
				List<RevCommit> want = new ArrayList<>();
				want.add(rc);
				List<RevCommit> branches = new ArrayList<>();
				want.add(masterCommit);
				if (checker.areAllReachable(want, branches).isPresent())
					reachable ++;
			}
		}
		System.err.println("reachable " + reachable);
	}
}
