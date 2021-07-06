/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class CommitGraphWriterTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	private CommitGraphConfig config;

	private ByteArrayOutputStream os;

	private CommitGraphWriter writer;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		os = new ByteArrayOutputStream();
		config = new CommitGraphConfig(db);
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
	}

	@Test
	public void testConstructor() {
		writer = new CommitGraphWriter(config, db.newObjectReader());
		assertTrue(config.isComputeGeneration());
		assertTrue(writer.isComputeGeneration());
		assertEquals(0, writer.getCommitCnt());
	}

	@Test
	public void testModifySettings() {
		config.setComputeGeneration(false);
		assertFalse(config.isComputeGeneration());

		writer = new CommitGraphWriter(config, db.newObjectReader());
		assertFalse(writer.isComputeGeneration());
		writer.setComputeGeneration(true);
		assertTrue(writer.isComputeGeneration());
	}

	@Test
	public void testWriterWithExtraEdgeList() throws Exception {
		RevCommit root = commit();
		RevCommit a = commit(root);
		RevCommit b = commit(root);
		RevCommit c = commit(root);
		RevCommit tip = commit(a, b, c);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		writer = new CommitGraphWriter(config, db.newObjectReader());
		writer.prepareCommitGraph(m, m, wants);

		assertTrue(writer.willWriteExtraEdgeList());
		assertEquals(5, writer.getCommitCnt());

		writer.writeCommitGraph(m, os);
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] {'C', 'G', 'P', 'H', 1, 1, 4, 0}, headers);
	}

	@Test
	public void testWriterWithoutExtraEdgeList() throws Exception {
		RevCommit root = commit();
		RevCommit a = commit(root);
		RevCommit b = commit(root);
		RevCommit tip = commit(a, b);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		writer = new CommitGraphWriter(config, db.newObjectReader());
		writer.prepareCommitGraph(m, m, wants);

		assertFalse(writer.willWriteExtraEdgeList());
		assertEquals(4, writer.getCommitCnt());

		writer.writeCommitGraph(m, os);
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] {'C', 'G', 'P', 'H', 1, 1, 3, 0}, headers);
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}
}
