/*
 * Copyright (C) 2026, Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class RevWalkWithAndWithoutCommitGraphTestCase
		extends AbstractRevWalkWithCommitGraphTest {

	public RevWalkWithAndWithoutCommitGraphTestCase(
			boolean withCommitGraph) {
		super(withCommitGraph);
	}

	@Parameters(name = "[{index}] commitGraphEnabled: {0}")
	public static Object[] commitGraphParameters() {
		return new Object[] { Boolean.FALSE, Boolean.TRUE };
	}

}
