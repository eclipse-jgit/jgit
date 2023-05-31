/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class SnapshottingRefDirectoryTest extends RefDirectoryTest {
	private RefDirectory originalRefDirectory;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		originalRefDirectory = refdir;
		refdir = refdir.createSnapshottingRefDirectory();
	}

	@Test
	public void testSnapshot_CannotSeeExternalPackedRefsUpdates()
			throws IOException {
		String refName = "refs/heads/new";

		writePackedRef(refName, A);
		assertEquals(A, originalRefDirectory.exactRef(refName).getObjectId());
		assertEquals(A, refdir.exactRef(refName).getObjectId());

		writePackedRef(refName, B);
		assertEquals(B, originalRefDirectory.exactRef(refName).getObjectId());
		assertEquals(A, refdir.exactRef(refName).getObjectId());
	}

	@Test
	public void testSnapshot_WriteThrough() throws IOException {
		String refName = "refs/heads/new";

		writePackedRef(refName, A);
		assertEquals(A, originalRefDirectory.exactRef(refName).getObjectId());
		assertEquals(A, refdir.exactRef(refName).getObjectId());

		PackedBatchRefUpdate update = refdir.newBatchUpdate();
		update.addCommand(new ReceiveCommand(A, B, refName));
		update.execute(repo.getRevWalk(), NullProgressMonitor.INSTANCE);

		assertEquals(B, originalRefDirectory.exactRef(refName).getObjectId());
		assertEquals(B, refdir.exactRef(refName).getObjectId());
	}

	@Test
	public void testSnapshot_IncludeExternalPackedRefsUpdatesWithWrites()
			throws IOException {
		String refA = "refs/heads/refA";
		String refB = "refs/heads/refB";
		writePackedRefs("" + //
				A.name() + " " + refA + "\n" + //
				A.name() + " " + refB + "\n");
		assertEquals(A, refdir.exactRef(refA).getObjectId());
		assertEquals(A, refdir.exactRef(refB).getObjectId());

		writePackedRefs("" + //
				B.name() + " " + refA + "\n" + //
				A.name() + " " + refB + "\n");
		PackedBatchRefUpdate update = refdir.newBatchUpdate();
		update.addCommand(new ReceiveCommand(A, B, refB));
		update.execute(repo.getRevWalk(), NullProgressMonitor.INSTANCE);

		assertEquals(B, originalRefDirectory.exactRef(refA).getObjectId());
		assertEquals(B, refdir.exactRef(refA).getObjectId());
		assertEquals(B, originalRefDirectory.exactRef(refB).getObjectId());
		assertEquals(B, refdir.exactRef(refB).getObjectId());
	}
}
