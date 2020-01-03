/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.Test;

public class RevWalkResetTest extends RevWalkTestCase {

	@Test
	public void testRevFilterReceivesParsedCommits() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		final AtomicBoolean filterRan = new AtomicBoolean();
		RevFilter testFilter = new RevFilter() {

			@Override
			public boolean include(RevWalk walker, RevCommit cmit)
					throws StopWalkException, MissingObjectException,
					IncorrectObjectTypeException, IOException {
				assertNotNull("commit is parsed", cmit.getRawBuffer());
				filterRan.set(true);
				return true;
			}

			@Override
			public RevFilter clone() {
				return this;
			}

			@Override
			public boolean requiresCommitBody() {
				return true;
			}
		};

		// Do an initial run through the walk
		filterRan.set(false);
		rw.setRevFilter(testFilter);
		markStart(c);
		rw.markUninteresting(b);
		for (RevCommit cmit = rw.next(); cmit != null; cmit = rw.next()) {
			// Don't dispose the body here, because we want to test the effect
			// of marking 'b' as uninteresting.
		}
		assertTrue("filter ran", filterRan.get());

		// Run through the walk again, this time disposing of all commits.
		filterRan.set(false);
		rw.reset();
		markStart(c);
		for (RevCommit cmit = rw.next(); cmit != null; cmit = rw.next()) {
			cmit.disposeBody();
		}
		assertTrue("filter ran", filterRan.get());

		// Do the third run through the reused walk. Test that the explicitly
		// disposed commits are parsed on this walk.
		filterRan.set(false);
		rw.reset();
		markStart(c);
		for (RevCommit cmit = rw.next(); cmit != null; cmit = rw.next()) {
			// spin through the walk.
		}
		assertTrue("filter ran", filterRan.get());

	}
}
