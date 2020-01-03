/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertNotNull;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.TestRepository;

public class BitmappedReachabilityCheckerTest
		extends ReachabilityCheckerTestCase {

	@Override
	protected ReachabilityChecker getChecker(
			TestRepository<FileRepository> repository) throws Exception {
		// GC generates the bitmaps
		GC gc = new GC(repo.getRepository());
		gc.setAuto(false);
		gc.gc();

		// This is null when the test didn't create any branch
		assertNotNull("Probably the test didn't define any ref",
				repo.getRevWalk().getObjectReader().getBitmapIndex());

		return new BitmappedReachabilityChecker(repository.getRevWalk());
	}

}
