/*
 * Copyright (C) 2026, Nasser Grainawi and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jgit.errors.CancelledException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphLoader;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.NB;
import org.junit.Test;

public class RevWalkMergedIntoWithCommitGraphTest
		extends AbstractRevWalkWithCommitGraphTest {

	@Test
	public void reverseAncestryPrunesHistory() throws Exception {
		RevCommit root = commit();
		RevCommit tip = root;
		for (int i = 0; i < 128; i++) {
			tip = commit(tip);
		}
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		try (RevWalk walk = new RevWalk(db)) {
			walk.setRetainBody(false);
			assertFalse(walk.isMergedInto(walk.lookupCommit(tip),
					walk.lookupCommit(root)));
			assertTrue("Generation rejection should only parse endpoints",
					parsedCommits(walk) <= 2);
		}
	}

	@Test
	public void compatibleGenerationsPruneSharedHistory() throws Exception {
		RevCommit shared = commit();
		for (int i = 0; i < 128; i++) {
			shared = commit(shared);
		}
		RevCommit base = commit(shared);
		RevCommit tip = commit(shared);
		branch(base, "base");
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		try (RevWalk walk = new RevWalk(db)) {
			walk.setRetainBody(false);
			assertFalse(walk.isMergedInto(walk.lookupCommit(base),
					walk.lookupCommit(tip)));
			assertTrue("Do not traverse below the base generation",
					parsedCommits(walk) < 8);
		}
	}

	@Test
	public void partialGraphPreservesFiltersFlagsAndRepeatedQueries()
			throws Exception {
		RevCommit root = commit();
		RevCommit base = commit(root);
		RevCommit other = commit(root);
		branch(base, "base");
		branch(other, "other");
		enableAndWriteCommitGraph();
		RevCommit tip = commit(base, other);
		try (RevWalk walk = new RevWalk(db)) {
			RevCommit b = walk.parseCommit(base);
			RevCommit t = walk.parseCommit(tip);
			RevFlag flag = walk.newFlag("caller");
			b.add(flag);
			RevFilter rf = RevFilter.NONE;
			TreeFilter tf = PathFilter.create("absent");
			walk.setRevFilter(rf);
			walk.setTreeFilter(tf);
			assertTrue(walk.isMergedInto(b, t));
			assertFalse(walk.isMergedInto(t, b));
			assertTrue(walk.isMergedInto(b, b));
			assertTrue(walk.isMergedInto(walk.lookupCommit(other), t));
			assertSame(rf, walk.getRevFilter());
			assertSame(tf, walk.getTreeFilter());
			assertTrue(b.has(flag));
			assertTrue(walk.isRetainBody());
			assertTrue(t.getRawBuffer() != null);
			walk.reset();
			walk.setRevFilter(RevFilter.ALL);
			walk.setTreeFilter(TreeFilter.ALL);
			walk.markStart(b);
			assertEquals(b, walk.next());
			assertEquals(root, walk.next());
		}
	}

	@Test
	public void graphSearchHonorsCancellationAndFirstParentRejection()
			throws Exception {
		RevCommit root = commit();
		RevCommit tip = root;
		for (int i = 0; i < 64; i++) {
			tip = commit(tip);
		}
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		try (RevWalk walk = new RevWalk(db)) {
			walk.setRetainBody(false);
			RevCommit r = walk.lookupCommit(root);
			RevCommit t = walk.lookupCommit(tip);
			walk.setFirstParent(true);
			assertThrows(IllegalStateException.class,
					() -> walk.isMergedInto(r, t));
			walk.reset();
			walk.setFirstParent(false);
			walk.setProgressMonitor(new ProgressMonitor() {
				@Override
				public boolean isCancelled() {
					return parsedCommits(walk) >= 8;
				}

				@Override
				public void start(int totalTasks) {
				}

				@Override
				public void beginTask(String title, int totalWork) {
				}

				@Override
				public void update(int completed) {
				}

				@Override
				public void endTask() {
				}

				@Override
				public void showDuration(boolean enabled) {
				}
			});
			assertThrows(CancelledException.class,
					() -> walk.isMergedInto(r, t));
			assertTrue(parsedCommits(walk) < 16);
		}
	}

	@Test
	public void allPairsWithSkewedDatesAndDifferentGraphCoverage()
			throws Exception {
		RevCommit[] commits = new RevCommit[8];
		commits[0] = commit();
		commits[1] = commit();
		commits[2] = commit(-100, commits[0]);
		commits[3] = commit(200, commits[0]);
		commits[4] = commit(-300, commits[2], commits[3]);
		commits[5] = commit(400, commits[1]);
		branch(commits[4], "left");
		branch(commits[5], "right");
		enableAndWriteCommitGraph();
		CommitGraph partial = CommitGraphLoader.read(
				new ByteArrayInputStream(Files.readAllBytes(db.getDirectory()
						.toPath().resolve("objects/info/commit-graph"))));
		commits[6] = commit(-500, commits[4], commits[5]);
		commits[7] = commit(600, commits[3], commits[5]);
		branch(commits[6], "merge");
		branch(commits[7], "cousin");
		enableAndWriteCommitGraph();
		byte[] bytes = Files.readAllBytes(db.getDirectory().toPath()
				.resolve("objects/info/commit-graph"));
		CommitGraph full = CommitGraphLoader
				.read(new ByteArrayInputStream(bytes));
		// Hand-derived ancestry bitsets, including each tip itself.
		int[] ancestors = { 1, 2, 5, 9, 29, 34, 127, 171 };
		for (CommitGraph graph : new CommitGraph[] { CommitGraph.EMPTY, partial,
				full, withGeneration(bytes, 0),
				withGeneration(bytes, 0x3fffffff) }) {
			for (boolean retainBody : new boolean[] { false, true }) {
				try (RevWalk walk = walkWithGraph(graph)) {
					walk.setRetainBody(retainBody);
					walk.sort(RevSort.TOPO);
					walk.sort(RevSort.REVERSE, true);
					for (int base = 0; base < commits.length; base++) {
						for (int tip = 0; tip < commits.length; tip++) {
							assertEquals("base=" + base + ", tip=" + tip,
									(ancestors[tip] & (1 << base)) != 0,
									walk.isMergedInto(
											walk.lookupCommit(commits[base]),
											walk.lookupCommit(commits[tip])));
						}
					}
				}
			}
		}
	}

	@Test
	public void shallowHistoryDoesNotFollowGraphParents() throws Exception {
		RevCommit root = commit();
		RevCommit shallow = commit(root);
		RevCommit tip = commit(shallow);
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		Files.writeString(db.getDirectory().toPath().resolve("shallow"),
				shallow.name() + "\n");
		try (RevWalk walk = new RevWalk(db)) {
			walk.setRetainBody(false);
			assertFalse(walk.isMergedInto(walk.lookupCommit(root),
					walk.lookupCommit(tip)));
			assertTrue(walk.isMergedInto(walk.lookupCommit(shallow),
					walk.lookupCommit(tip)));
		}
	}

	@Test
	public void retainedBodyReportsMissingGraphEndpoint() throws Exception {
		RevCommit root = commit();
		RevCommit tip = commit(root);
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		ObjectReader delegate = db.newObjectReader();
		try (ObjectReader reader = new ObjectReader.Filter() {
			@Override
			protected ObjectReader delegate() {
				return delegate;
			}

			@Override
			public ObjectLoader open(AnyObjectId id, int typeHint)
					throws IOException {
				if (id.equals(root)) {
					throw new MissingObjectException(id.copy(),
							Constants.OBJ_COMMIT);
				}
				return delegate.open(id, typeHint);
			}
		}; RevWalk walk = new RevWalk(reader)) {
			walk.setRetainBody(true);
			assertThrows(MissingObjectException.class,
					() -> walk.isMergedInto(walk.lookupCommit(root),
							walk.lookupCommit(tip)));
		}
	}

	@Test
	public void missingTipAncestorAndRetry() throws Exception {
		RevCommit base = commit();
		branch(base, "base");
		enableAndWriteCommitGraph();
		ObjectId missing = ObjectId
				.fromString("0123456789012345678901234567890123456789");
		ObjectId tip = unparsedCommit(missing);
		try (RevWalk walk = new RevWalk(db)) {
			RevCommit b = walk.lookupCommit(base);
			assertThrows(MissingObjectException.class,
					() -> walk.isMergedInto(b, walk.lookupCommit(tip)));
			assertTrue(walk.isMergedInto(b, b));
		}
	}

	@Test
	public void graphSearchDoesNotNeedFreeApplicationFlags() throws Exception {
		RevCommit base = commit();
		RevCommit tip = commit(base);
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		try (RevWalk walk = new RevWalk(db)) {
			while (walk.freeFlags != 0) {
				walk.newFlag("caller");
			}
			assertTrue(walk.isMergedInto(walk.lookupCommit(base),
					walk.lookupCommit(tip)));
			assertFalse(walk.isMergedInto(walk.lookupCommit(tip),
					walk.lookupCommit(base)));
			walk.reset();
			assertEquals(0, walk.freeFlags);
		}
	}

	@Test
	public void callerReaderSeesUnflushedTip() throws Exception {
		RevCommit root = commit();
		RevCommit base = commit(root);
		branch(base, "base");
		enableAndWriteCommitGraph();
		CommitGraph graph = CommitGraphLoader.read(
				new ByteArrayInputStream(Files.readAllBytes(db.getDirectory()
						.toPath().resolve("objects/info/commit-graph"))));
		try (InMemoryRepository repo = new InMemoryRepository(
				new DfsRepositoryDescription("staged"));
				ObjectInserter inserter = repo.newObjectInserter()) {
			inserter.insert(Constants.OBJ_COMMIT, db.open(root).getBytes());
			inserter.insert(Constants.OBJ_COMMIT, db.open(base).getBytes());
			inserter.flush();
			parseBody(base);
			CommitBuilder builder = new CommitBuilder();
			builder.setTreeId(base.getTree());
			builder.setParentId(base);
			builder.setAuthor(base.getAuthorIdent());
			builder.setCommitter(base.getCommitterIdent());
			builder.setMessage("unflushed");
			ObjectId tip = inserter.insert(builder);
			assertFalse(repo.getObjectDatabase().has(tip));
			ObjectReader delegate = inserter.newReader();
			try (RevWalk walk = new RevWalk(new ObjectReader.Filter() {
				@Override
				protected ObjectReader delegate() {
					return delegate;
				}

				@Override
				public Optional<CommitGraph> getCommitGraph() {
					return Optional.of(graph);
				}
			})) {
				assertTrue(walk.isMergedInto(walk.lookupCommit(base),
						walk.lookupCommit(tip)));
			}
		}
	}

	@Test
	public void depthAndObjectWalksRetainPairAncestryBehavior()
			throws Exception {
		RevCommit base = commit();
		RevCommit tip = commit(commit(base));
		branch(tip, "tip");
		enableAndWriteCommitGraph();
		try (DepthWalk.RevWalk walk = new DepthWalk.RevWalk(db, 1)) {
			RevCommit t = walk.parseCommit(tip);
			walk.markRoot(t);
			// The pair API's merge-base path has never applied depth limits.
			assertTrue(walk.isMergedInto(walk.lookupCommit(base), t));
		}
		try (ObjectWalk walk = new ObjectWalk(db)) {
			assertTrue(walk.isMergedInto(walk.lookupCommit(base),
					walk.lookupCommit(tip)));
			assertEquals(base.getTree(), walk.nextObject());
			assertFalse(walk.isMergedInto(walk.lookupCommit(tip),
					walk.lookupCommit(base)));
		}
	}

	private RevWalk walkWithGraph(CommitGraph graph) {
		ObjectReader delegate = db.newObjectReader();
		return new RevWalk(new ObjectReader.Filter() {
			@Override
			protected ObjectReader delegate() {
				return delegate;
			}

			@Override
			public Optional<CommitGraph> getCommitGraph() {
				return Optional.of(graph);
			}
		});
	}

	private static CommitGraph withGeneration(byte[] original, int generation)
			throws IOException {
		byte[] bytes = original.clone();
		int count = (int) CommitGraphLoader
				.read(new ByteArrayInputStream(bytes)).getCommitCnt();
		for (int chunk = 0; chunk < (bytes[6] & 0xff); chunk++) {
			int entry = 8 + 12 * chunk;
			if (NB.decodeInt32(bytes, entry) == 0x43444154) { // CDAT
				int offset = (int) NB.decodeInt64(bytes, entry + 4);
				for (int i = 0; i < count; i++) {
					int packedDate = offset + 36 * i + 28;
					NB.encodeInt32(bytes, packedDate,
							(NB.decodeInt32(bytes, packedDate) & 3)
									| (generation << 2));
				}
			}
		}
		byte[] checksum = Constants.newMessageDigest().digest(Arrays
				.copyOf(bytes, bytes.length - Constants.OBJECT_ID_LENGTH));
		System.arraycopy(checksum, 0, bytes,
				bytes.length - Constants.OBJECT_ID_LENGTH, checksum.length);
		return CommitGraphLoader.read(new ByteArrayInputStream(bytes));
	}

	private static int parsedCommits(RevWalk walk) {
		int count = 0;
		for (RevObject object : walk.objects) {
			if (object instanceof RevCommit
					&& (object.flags & RevWalk.PARSED) != 0) {
				count++;
			}
		}
		return count;
	}
}
