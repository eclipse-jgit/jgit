/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.IO;
import org.junit.Test;

public class GcCommitGraphTest extends GcTestCase {

	@Test
	public void testCommitGraphConfig() {
		StoredConfig config = repo.getConfig();
		assertFalse(gc.shouldWriteCommitGraphWhenGc());

		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);
		assertTrue(gc.shouldWriteCommitGraphWhenGc());

		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, false);
		assertFalse(gc.shouldWriteCommitGraphWhenGc());
	}

	@Test
	public void testWriteEmptyRepo() throws Exception {
		StoredConfig config = repo.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);

		assertTrue(gc.shouldWriteCommitGraphWhenGc());
		gc.writeCommitGraph(Collections.emptySet());
		File graphFile = new File(repo.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);
		assertFalse(graphFile.exists());
	}

	@Test
	public void testWriteShallowRepo() throws Exception {
		StoredConfig config = repo.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);

		RevCommit tip = commitChain(2);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.update(tip);
		repo.getObjectDatabase().setShallowCommits(Collections.singleton(tip));

		gc.writeCommitGraph(Collections.singleton(tip));
		File graphFile = new File(repo.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);
		assertFalse(graphFile.exists());
	}

	@Test
	public void testWriteWhenGc() throws Exception {
		StoredConfig config = repo.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);

		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.update(tip);

		assertTrue(gc.shouldWriteCommitGraphWhenGc());
		gc.gc().get();
		File graphFile = new File(repo.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);
		assertGraphFile(graphFile);
	}

	@Test
	public void testDefaultWriteWhenGc() throws Exception {
		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.update(tip);

		assertFalse(gc.shouldWriteCommitGraphWhenGc());
		gc.gc().get();
		File graphFile = new File(repo.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);
		assertFalse(graphFile.exists());
	}

	@Test
	public void testDisableWriteWhenGc() throws Exception {
		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.update(tip);
		File graphFile = new File(repo.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);

		StoredConfig config = repo.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, false);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);

		gc.gc().get();
		assertFalse(graphFile.exists());

		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, false);
		gc.gc().get();
		assertFalse(graphFile.exists());

		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, false);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, false);
		gc.gc().get();
		assertFalse(graphFile.exists());
	}

	@Test
	public void testWriteCommitGraphOnly() throws Exception {
		RevCommit tip = commitChain(10);
		TestRepository.BranchBuilder bb = tr.branch("refs/heads/master");
		bb.update(tip);

		StoredConfig config = repo.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, false);
		gc.writeCommitGraph(Collections.singleton(tip));

		File graphFile = new File(repo.getObjectsDirectory(),
				Constants.INFO_COMMIT_GRAPH);
		assertFalse(graphFile.exists());

		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		gc.writeCommitGraph(Collections.singleton(tip));
		assertGraphFile(graphFile);
	}

	private void assertGraphFile(File graphFile) throws Exception {
		assertTrue(graphFile.exists());
		try (InputStream os = new FileInputStream(graphFile)) {
			byte[] magic = new byte[4];
			IO.readFully(os, magic, 0, 4);
			assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H' }, magic);
		}
	}
}
