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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.NB;
import org.junit.Before;
import org.junit.Test;

public class CommitGraphWriterTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	private ByteArrayOutputStream os;

	private CommitGraphWriter writer;

	private RevWalk walk;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		os = new ByteArrayOutputStream();
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
		walk = new RevWalk(db);
	}

	@Test
	public void testWriteInEmptyRepo() throws Exception {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		writer = new CommitGraphWriter(GraphCommits.fromWalk(m, Collections.emptySet(), walk));
		writer.write(m, os);
		assertEquals(0, os.size());
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
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits);
		writer.write(m, os);

		assertEquals(5, graphCommits.size());
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H', 1, 1, 4, 0 }, headers);
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_FANOUT, NB.decodeInt32(data, 8));
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_LOOKUP, NB.decodeInt32(data, 20));
		assertEquals(CommitGraphConstants.CHUNK_ID_COMMIT_DATA, NB.decodeInt32(data, 32));
		assertEquals(CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST, NB.decodeInt32(data, 44));
	}

	@Test
	public void testWriterWithoutExtraEdgeList() throws Exception {
		RevCommit root = commit();
		RevCommit a = commit(root);
		RevCommit b = commit(root);
		RevCommit tip = commit(a, b);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits);
		writer.write(m, os);

		assertEquals(4, graphCommits.size());
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H', 1, 1, 3, 0 }, headers);
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_FANOUT, NB.decodeInt32(data, 8));
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_LOOKUP, NB.decodeInt32(data, 20));
		assertEquals(CommitGraphConstants.CHUNK_ID_COMMIT_DATA, NB.decodeInt32(data, 32));
	}

	@Test
	public void testWriterWithCorrectedCommitDate() throws Exception {
		RevCommit root = commit();
		RevCommit a = commit(root);
		RevCommit b = commit(root);
		RevCommit tip = commit(a, b);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, 2);
		writer.write(m, os);

		assertEquals(4, graphCommits.size());
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H', 1, 1, 4, 0 },
				headers);

		assertEquals(CommitGraphConstants.CHUNK_ID_OID_FANOUT,
				NB.decodeInt32(data, 8));
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_LOOKUP,
				NB.decodeInt32(data, 20));
		assertEquals(CommitGraphConstants.CHUNK_ID_COMMIT_DATA,
				NB.decodeInt32(data, 32));
		assertEquals(CommitGraphConstants.CHUNK_GENERATION_DATA,
				NB.decodeInt32(data, 44));
	}

	@Test
	public void testWriterWithCorrectedCommitDateOverflow() throws Exception {
		// corrected commit date = Integer.MAX_VALUE
		RevCommit root = commit(new Date((long) (Integer.MAX_VALUE) * 1000));
		// corrected commit date = Integer.MAX_VALUE + 1, aka overflowed
		RevCommit a = commit(new Date(0L), root);
		// corrected commit date = Integer.MAX_VALUE + 2, aka overflowed
		RevCommit tip = commit(a);

		Set<ObjectId> wants = Collections.singleton(tip);
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		GraphCommits graphCommits = GraphCommits.fromWalk(m, wants, walk);
		writer = new CommitGraphWriter(graphCommits, 2);
		writer.write(m, os);

		assertEquals(3, graphCommits.size());
		byte[] data = os.toByteArray();
		assertTrue(data.length > 0);
		byte[] headers = new byte[8];
		System.arraycopy(data, 0, headers, 0, 8);
		assertArrayEquals(new byte[] { 'C', 'G', 'P', 'H', 1, 1, 5, 0 },
				headers);

		assertEquals(CommitGraphConstants.CHUNK_ID_OID_FANOUT,
				NB.decodeInt32(data, 8));
		assertEquals(CommitGraphConstants.CHUNK_ID_OID_LOOKUP,
				NB.decodeInt32(data, 20));
		assertEquals(CommitGraphConstants.CHUNK_ID_COMMIT_DATA,
				NB.decodeInt32(data, 32));
		assertEquals(CommitGraphConstants.CHUNK_GENERATION_DATA,
				NB.decodeInt32(data, 44));
		assertEquals(CommitGraphConstants.CHUNK_GENERATION_DATA_OVERFLOW,
				NB.decodeInt32(data, 56));
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}

	RevCommit commit(Date commitDate, RevCommit... parents) throws Exception {
		return tr.commit(commitDate, parents);
	}
}
