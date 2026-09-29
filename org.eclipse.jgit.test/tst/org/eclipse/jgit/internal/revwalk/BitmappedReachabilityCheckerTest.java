/*
 * Copyright (C) 2019-2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.revwalk;

import static org.junit.Assert.assertNotNull;

import java.util.Optional;

import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.revwalk.ReachabilityChecker;

public class BitmappedReachabilityCheckerTest
		extends ReachabilityCheckerTestCase {

	public BitmappedReachabilityCheckerTest(boolean withCommitGraph) {
		super(withCommitGraph);
	}

	@Override
	protected ReachabilityChecker createReachabilityChecker() throws Exception {
		Optional<CommitGraph> commitGraph = db.getObjectDatabase()
				.getCommitGraph();

		// Skip GC if commit graph is generated (GC already done)
		if (commitGraph.isEmpty()
				|| commitGraph.orElseThrow().getCommitCnt() == 0) {
			// GC generates the bitmaps
			GC gc = new GC(db);
			gc.setAuto(false);
			gc.gc().get();
		}

		// This is null when the test didn't create any branch
		assertNotNull("Probably the test didn't define any ref",
				rw.getObjectReader().getBitmapIndex());

		return new BitmappedReachabilityChecker(rw);
	}

}
