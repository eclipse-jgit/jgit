/*
 * Copyright (C) 2023, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.pack.PackExt.REVERSE_INDEX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.IO;
import org.junit.Test;

public class GcReverseIndexTest extends GcTestCase {

	@Test
	public void testWriteDefault() throws Exception {
		PackConfig config = new PackConfig(repo);
		gc.setPackConfig(config);

		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/main");
		bb.update(tip);

		gc.gc().get();
		assertRidxDoesNotExist(repo);
	}

	@Test
	public void testWriteDisabled() throws Exception {
		PackConfig config = new PackConfig(repo);
		config.setWriteReverseIndex(false);
		gc.setPackConfig(config);

		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/main");
		bb.update(tip);

		gc.gc().get();
		assertRidxDoesNotExist(repo);
	}

	@Test
	public void testWriteEmptyRepo() throws Exception {
		PackConfig config = new PackConfig(repo);
		config.setWriteReverseIndex(true);
		gc.setPackConfig(config);

		gc.gc().get();
		assertRidxDoesNotExist(repo);
	}

	@Test
	public void testWriteShallowRepo() throws Exception {
		PackConfig config = new PackConfig(repo);
		config.setWriteReverseIndex(true);
		gc.setPackConfig(config);

		RevCommit tip = commitChain(2);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/main");
		bb.update(tip);
		repo.getObjectDatabase().setShallowCommits(Collections.singleton(tip));

		gc.gc().get();
		assertValidRidxExists(repo);
	}

	@Test
	public void testWriteEnabled() throws Exception {
		PackConfig config = new PackConfig(repo);
		config.setWriteReverseIndex(true);
		gc.setPackConfig(config);

		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/main");
		bb.update(tip);

		gc.gc().get();
		assertValidRidxExists(repo);
	}

	private static void assertValidRidxExists(FileRepository repo)
			throws Exception {
		PackFile packFile = repo.getObjectDatabase().getPacks().iterator()
				.next().getPackFile();
		File file = packFile.create(REVERSE_INDEX);
		assertTrue(file.exists());
		try (InputStream os = new FileInputStream(file)) {
			byte[] magic = new byte[4];
			IO.readFully(os, magic, 0, 4);
			assertArrayEquals(new byte[] { 'R', 'I', 'D', 'X' }, magic);
		}
	}

	private static void assertRidxDoesNotExist(FileRepository repo) {
		File packDir = repo.getObjectDatabase().getPackDirectory();
		String[] reverseIndexFilenames = packDir.list(
				(dir, name) -> name.endsWith(REVERSE_INDEX.getExtension()));
		assertEquals(0, reverseIndexFilenames.length);
	}
}
