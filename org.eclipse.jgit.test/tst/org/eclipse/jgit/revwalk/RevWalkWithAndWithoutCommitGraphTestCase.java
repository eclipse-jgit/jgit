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

/**
 * A parameterized RevWalk test that runs each test method once without and once
 * with a generated commit graph.
 * <p>
 * Test authors only have to declare a constructor accepting a {@code boolean}
 * argument and pass this argument to
 * {@link #RevWalkWithAndWithoutCommitGraphTestCase(boolean)}
 */
@RunWith(Parameterized.class)
public abstract class RevWalkWithAndWithoutCommitGraphTestCase
		extends AbstractRevWalkWithCommitGraphTest {

	private final boolean commitGraphEnabled;

	public RevWalkWithAndWithoutCommitGraphTestCase(boolean withCommitGraph) {
		this.commitGraphEnabled = withCommitGraph;
	}

	@Override
	protected boolean commitGraphEnabled() {
		return commitGraphEnabled;
	}

	@Parameters(name = "[{index}] commitGraphEnabled: {0}")
	public static Object[] commitGraphParameters() {
		return new Object[] { Boolean.FALSE, Boolean.TRUE };
	}

}
