/*
 * Copyright (C) 2020, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.revwalk;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.ObjectReachabilityChecker;

public class BitmappedObjectReachabilityTest
	extends ObjectReachabilityTestCase {

	@Override
	ObjectReachabilityChecker getChecker(
			TestRepository<FileRepository> repository) throws Exception {
		// GC generates the bitmaps
		GC gc = new GC(repository.getRepository());
		gc.setAuto(false);
		gc.gc().get();

		return new BitmappedObjectReachabilityChecker(
				repository.getRevWalk().toObjectWalkWithSameObjects());
	}

}
