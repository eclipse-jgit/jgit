/*
 * Copyright (C) 2019-2026, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.revwalk;

import org.eclipse.jgit.revwalk.ReachabilityChecker;

public class PedestrianReachabilityCheckerTest
		extends ReachabilityCheckerTestCase {

	public PedestrianReachabilityCheckerTest(boolean withCommitGraph) {
		super(withCommitGraph);
	}

	@Override
	protected ReachabilityChecker createReachabilityChecker() throws Exception {
		return new PedestrianReachabilityChecker(true, rw);
	}

}
