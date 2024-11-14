/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

public class PackRefsTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
	}

	@Test
	public void tagPacked() throws Exception {
		git.tag().setName("test").call();
		git.packRefs().call();
		assertEquals(Ref.Storage.PACKED,
				git.getRepository().exactRef("refs/tags/test").getStorage());
	}

	@Test
	public void nonTagRefNotPackedWithoutAll() throws Exception {
		git.branchCreate().setName("test").call();
		git.packRefs().call();
		assertEquals(Ref.Storage.LOOSE,
				git.getRepository().exactRef("refs/heads/test").getStorage());
	}

	@Test
	public void nonTagRefPackedWithAll() throws Exception {
		git.branchCreate().setName("test").call();
		git.packRefs().setAll(true).call();
		assertEquals(Ref.Storage.PACKED,
				git.getRepository().exactRef("refs/heads/test").getStorage());
	}

	@Test
	public void refTableCompacted() throws Exception {
		((FileRepository) git.getRepository()).convertRefStorage(
				ConfigConstants.CONFIG_REF_STORAGE_REFTABLE, false, false);

		git.commit().setMessage("test commit").call();
		File tableDir = new File(db.getDirectory(), Constants.REFTABLE);
		File[] reftables = tableDir.listFiles();
		assertNotNull(reftables);
		assertTrue(reftables.length > 2);

		git.packRefs().call();

		reftables = tableDir.listFiles();
		assertNotNull(reftables);
		assertEquals(2, reftables.length);
	}
}
