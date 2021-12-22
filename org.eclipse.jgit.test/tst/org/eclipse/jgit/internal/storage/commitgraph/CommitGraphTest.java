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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.BLOOM_BITS_PER_ENTRY;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.BLOOM_KEY_NUM_HASHES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BloomFilter;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class CommitGraphTest extends RepositoryTestCase {

	private TestRepository<FileRepository> tr;

	private CommitGraph commitGraph;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db, new RevWalk(db), mockSystemReader);
	}

	@Test
	public void testGraphWithManyParents() throws Exception {
		int parentsNum = 40;
		RevCommit root = commit();

		RevCommit[] parents = new RevCommit[parentsNum];
		for (int i = 0; i < parents.length; i++) {
			parents[i] = commit(root);
		}
		RevCommit tip = commit(parents);

		Set<ObjectId> wants = Collections.singleton(tip);
		writeCommitGraph(wants);
		assertEquals(parentsNum + 2, commitGraph.getCommitCnt());
		verifyCommitGraph();

		assertEquals(1, getGenerationNumber(root));
		for (RevCommit parent : parents) {
			assertEquals(2, getGenerationNumber(parent));
		}
		assertEquals(3, getGenerationNumber(tip));
	}

	@Test
	public void testGraphWithoutMerges() throws Exception {
		int commitNum = 20;
		RevCommit[] commits = new RevCommit[commitNum];
		for (int i = 0; i < commitNum; i++) {
			if (i == 0) {
				commits[i] = commit();
			} else {
				commits[i] = commit(commits[i - 1]);
			}
		}

		Set<ObjectId> wants = Collections.singleton(commits[commitNum - 1]);
		writeCommitGraph(wants);
		assertEquals(commitNum, commitGraph.getCommitCnt());
		verifyCommitGraph();
		for (int i = 0; i < commitNum; i++) {
			assertEquals(i + 1, getGenerationNumber(commits[i]));
		}
	}

	@Test
	public void testGraphWithoutGeneration() throws Exception {
		StoredConfig storedConfig = db.getConfig();
		storedConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMPUTE_GENERATION, false);
		storedConfig.save();

		int commitNum = 10;
		RevCommit[] commits = new RevCommit[commitNum];
		for (int i = 0; i < commitNum; i++) {
			if (i == 0) {
				commits[i] = commit();
			} else {
				commits[i] = commit(commits[i - 1]);
			}
		}

		Set<ObjectId> wants = Collections.singleton(commits[commitNum - 1]);
		writeCommitGraph(wants);
		assertEquals(commitNum, commitGraph.getCommitCnt());
		verifyCommitGraph();
		for (int i = 0; i < commitNum; i++) {
			assertEquals(CommitGraph.GENERATION_NOT_COMPUTED,
					getGenerationNumber(commits[i]));
		}
	}

	@Test
	public void testGraphWithMerges() throws Exception {
		RevCommit c1 = commit();
		RevCommit c2 = commit(c1);
		RevCommit c3 = commit(c2);
		RevCommit c4 = commit(c1);
		RevCommit c5 = commit(c4);
		RevCommit c6 = commit(c1);
		RevCommit c7 = commit(c6);

		RevCommit m1 = commit(c2, c4);
		RevCommit m2 = commit(c4, c6);
		RevCommit m3 = commit(c3, c5, c7);

		Set<ObjectId> wants = new HashSet<>();

		/*
		 * <pre>
		 * current graph structure:
		 *    M1
		 *   /  \
		 *  2    4
		 *  |___/
		 *  1
		 * </pre>
		 */
		wants.add(m1);
		writeCommitGraph(wants);
		assertEquals(4, commitGraph.getCommitCnt());
		verifyCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *    M1   M2
		 *   /  \ /  \
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */
		wants.add(m2);
		writeCommitGraph(wants);
		assertEquals(6, commitGraph.getCommitCnt());
		verifyCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *
		 *    __M3___
		 *   /   |   \
		 *  3 M1 5 M2 7
		 *  |/  \|/  \|
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */
		wants.add(m3);
		writeCommitGraph(wants);
		assertEquals(10, commitGraph.getCommitCnt());
		verifyCommitGraph();

		/*
		 * <pre>
		 * current graph structure:
		 *       8
		 *       |
		 *    __M3___
		 *   /   |   \
		 *  3 M1 5 M2 7
		 *  |/  \|/  \|
		 *  2    4    6
		 *  |___/____/
		 *  1
		 * </pre>
		 */
		RevCommit c8 = commit(m3);
		wants.add(c8);
		writeCommitGraph(wants);
		assertEquals(11, commitGraph.getCommitCnt());
		verifyCommitGraph();

		assertEquals(getGenerationNumber(c1), 1);
		assertEquals(getGenerationNumber(c2), 2);
		assertEquals(getGenerationNumber(c4), 2);
		assertEquals(getGenerationNumber(c6), 2);
		assertEquals(getGenerationNumber(c3), 3);
		assertEquals(getGenerationNumber(c5), 3);
		assertEquals(getGenerationNumber(c7), 3);
		assertEquals(getGenerationNumber(m1), 3);
		assertEquals(getGenerationNumber(m2), 3);
		assertEquals(getGenerationNumber(m3), 4);
		assertEquals(getGenerationNumber(c8), 5);
	}

	@Test
	public void testGraphComputeChangedPaths() throws Exception {
		StoredConfig storedConfig = db.getConfig();
		storedConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMPUTE_CHANGED_PATHS, true);
		storedConfig.save();
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);

		writeCommitGraph(Collections.singleton(c));
		assertEquals(3, commitGraph.getCommitCnt());
		assertNotNull(commitGraph.newBloomKey("test"));
		assertNotNull(commitGraph.findBloomFilter(a));
		assertNotNull(commitGraph.findBloomFilter(b));
		assertNotNull(commitGraph.findBloomFilter(c));

		storedConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMPUTE_CHANGED_PATHS, false);
		storedConfig.save();
		writeCommitGraph(Collections.singleton(c));
		assertEquals(3, commitGraph.getCommitCnt());
		assertNull(commitGraph.newBloomKey("test"));
		assertNull(commitGraph.findBloomFilter(a));
		assertNull(commitGraph.findBloomFilter(b));
		assertNull(commitGraph.findBloomFilter(c));
	}

	@Test
	public void testGraphReadChangedPaths() throws Exception {
		StoredConfig storedConfig = db.getConfig();
		storedConfig.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION, true);
		storedConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMPUTE_CHANGED_PATHS, true);
		storedConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				null, ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS, false);
		storedConfig.save();
		RevCommit a = commit(tree(file("d/f", blob("a"))));
		RevCommit b = commit(tree(file("d/f", blob("a"))), a);
		RevCommit c = commit(tree(file("d/f", blob("b"))), b);

		GC gc = new GC(db);
		gc.writeCommitGraph(Collections.singleton(c));
		commitGraph = db.newObjectReader().getCommitGraph();

		assertNotNull(commitGraph);
		assertEquals(3, commitGraph.getCommitCnt());
		assertNull(commitGraph.newBloomKey("test"));
		assertNull(commitGraph.findBloomFilter(a));
		assertNull(commitGraph.findBloomFilter(b));
		assertNull(commitGraph.findBloomFilter(c));
	}

	@Test
	public void testBloomFilters() throws Exception {
		StoredConfig storedConfig = db.getConfig();
		storedConfig.setBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
				null, ConfigConstants.CONFIG_KEY_COMPUTE_CHANGED_PATHS, true);
		storedConfig.save();
		RevCommit a = commit(
				tree(file("d/f", blob("a")), file("1.txt", blob("1"))));
		RevCommit b = commit(
				tree(file("d/f", blob("a")), file("1.txt", blob("2"))), a);
		RevCommit c = commit(
				tree(file("d/f", blob("b")), file("1.txt", blob("2"))), b);

		writeCommitGraph(Collections.singleton(c));
		assertEquals(3, commitGraph.getCommitCnt());
		assertNotNull(commitGraph.newBloomKey("test"));
		ChangedPathFilter filter;

		filter = (ChangedPathFilter) commitGraph.findBloomFilter(a);
		assertTrue(filter.contains(commitGraph.newBloomKey("d/f")));
		assertTrue(filter.contains(commitGraph.newBloomKey("d")));
		assertTrue(filter.contains(commitGraph.newBloomKey("1.txt")));
		assertArrayEquals(filter.getData(),
				createBloomFilterData(new String[] { "d/f", "d", "1.txt" }));

		filter = (ChangedPathFilter) commitGraph.findBloomFilter(b);
		assertTrue(filter.contains(commitGraph.newBloomKey("1.txt")));
		assertArrayEquals(filter.getData(),
				createBloomFilterData(new String[] { "1.txt" }));

		filter = (ChangedPathFilter) commitGraph.findBloomFilter(c);
		assertTrue(filter.contains(commitGraph.newBloomKey("d/f")));
		assertTrue(filter.contains(commitGraph.newBloomKey("d")));
		assertArrayEquals(filter.getData(),
				createBloomFilterData(new String[] { "d/f", "d" }));
	}

	private byte[] createBloomFilterData(String[] strings) {
		int filterLen = (strings.length * BLOOM_BITS_PER_ENTRY + Byte.SIZE - 1)
				/ Byte.SIZE;
		ChangedPathFilter filter = new ChangedPathFilter(filterLen,
				BLOOM_KEY_NUM_HASHES);

		for (String str : strings) {
			BloomFilter.Key key = ChangedPathFilter.newBloomKey(str,
					BLOOM_KEY_NUM_HASHES);
			filter.addKey(key);
		}
		return filter.getData();
	}

	void writeCommitGraph(Set<ObjectId> wants) throws Exception {
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		CommitGraphWriter writer = new CommitGraphWriter(db);
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		writer.prepareCommitGraph(m, m, wants);
		writer.writeCommitGraph(m, os);

		InputStream inputStream = new ByteArrayInputStream(os.toByteArray());
		CommitGraphData graphData = CommitGraphData.read(inputStream);

		commitGraph = new CommitGraphSingleImpl(graphData);
	}

	void verifyCommitGraph() throws Exception {
		try (RevWalk walk = new RevWalk(db)) {
			for (int i = 0; i < commitGraph.getCommitCnt(); i++) {
				ObjectId objId = commitGraph.getObjectId(i);
				CommitGraph.CommitData commit = commitGraph.getCommitData(i);
				int[] pList = commit.getParents();

				RevCommit expect = walk.lookupCommit(objId);
				walk.parseBody(expect);

				assertEquals(expect.getCommitTime(), commit.getCommitTime());
				assertEquals(expect.getTree(), commit.getTree());
				assertEquals(expect.getParentCount(), pList.length);

				if (pList.length > 0) {
					ObjectId[] parents = new ObjectId[pList.length];
					for (int j = 0; j < parents.length; j++) {
						parents[j] = commitGraph.getObjectId(pList[j]);
					}
					assertArrayEquals(expect.getParents(), parents);
				}
			}
		}
	}

	int getGenerationNumber(ObjectId id) {
		CommitGraph.CommitData commitData = commitGraph.getCommitData(id);
		if (commitData != null) {
			return commitData.getGeneration();
		}
		return CommitGraph.GENERATION_UNKNOWN;
	}

	RevCommit commit(RevCommit... parents) throws Exception {
		return tr.commit(parents);
	}

	RevCommit commit(RevTree tree, RevCommit... parents) throws Exception {
		return tr.commit(tree, parents);
	}

	RevBlob blob(String content) throws Exception {
		return tr.blob(content);
	}

	DirCacheEntry file(String path, RevBlob blob) throws Exception {
		return tr.file(path, blob);
	}

	RevTree tree(DirCacheEntry... entries) throws Exception {
		return tr.tree(entries);
	}
}
