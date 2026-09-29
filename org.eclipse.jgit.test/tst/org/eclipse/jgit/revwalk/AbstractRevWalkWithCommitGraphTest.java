/*
 * Copyright (C) 2023-2026, Tencent and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraph.EMPTY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Base Test class for all tests that test RevWalk behavior with an active
 * commit graph.
 * <p>
 * In contrast to other {@linkplain RevWalkTestCase}s, Test authors must
 * explicitly initialize the {@linkplain RevWalkTestCase#rw RevWalk instance}
 * using {@link #initializeRevWalk()}, which will enable the commit graph and
 * generate the commit graph file. The RevWalk can be re-initialized using
 * {@link #reinitializeRevWalk()}.
 * <p>
 * The commit graph is only generated if at least one ref exists in the
 * repository. The graph will also only contain commits reachable via a ref
 * (excluding {@linkplain org.eclipse.jgit.lib.RefDatabase#getAdditionalRefs()
 * additional refs} not starting with 'refs/')
 * <p>
 * The commit instances returned by the {@code commit()} and
 * {@code unparsedCommit()} helpers do not have a graph position. If they are
 * directly marked as a starting commit for the RevWalk (e.g. via
 * {@code rw.markStart()} or {@code rw.markUninteresting()}), then they are not
 * parsed from the commit graph and do not have generation numbers or changed
 * path filters. Use the convenience methods {@link #markStart(RevCommit)} and
 * {@link #markUninteresting(RevCommit)} instead.
 * <p>
 * Test authors can also override the {@link #commitGraphEnabled()} method to
 * disable the generation of a commit graph. This is useful to test a usecase
 * with and without a commit graph. The base class
 * {@link RevWalkWithAndWithoutCommitGraphTestCase} can be used in such cases.
 */
public abstract class AbstractRevWalkWithCommitGraphTest
		extends RevWalkTestCase {

	protected boolean commitGraphEnabled() {
		return true;
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
		mockSystemReader.setJGitConfig(new MockConfig());
		rw.close();
		rw = null; // Test author must explicitly initialize RevWalk after setup
					// to generate commit graph
	}

	/**
	 * Convenience method to mark a commit (potentially belonging to a different
	 * RevWalk instance) as a starting point for the RevWalk.
	 * <p>
	 * If a commit graph is generated, then an instance of the commit with a
	 * graph position, generation number and changed path filter is added as a
	 * starting point of the RevWalk.
	 * <p>
	 * Can only be called after {@link #initializeRevWalk()}
	 */
	@Override
	protected void markStart(RevCommit commit) throws Exception {
		rw.markStart(rw.parseCommit(commit));
	}

	/**
	 * Convenience method to mark a commit (potentially belonging to a different
	 * RevWalk instance) as an uninteresting starting point for the RevWalk.
	 * <p>
	 * If a commit graph is generated, then an instance of the commit with a
	 * graph position, generation number and changed path filter is added as a
	 * starting point of the RevWalk.
	 * <p>
	 * Can only be called after {@link #initializeRevWalk()}
	 */
	@Override
	protected void markUninteresting(RevCommit commit) throws Exception {
		rw.markUninteresting(rw.parseCommit(commit));
	}

	/**
	 * First time initialization of the RevWalk instance for this test case. If
	 * {@link #commitGraphEnabled()} returns {@code true}, then the commit graph
	 * is enabled for the repository and a GC is executed to generate the commit
	 * graph file.
	 * <p>
	 * This method also asserts that after the GC, the commit graph exists and
	 * is not empty, unless {@link #commitGraphEnabled()} returned
	 * {@code false}, in which case this method asserts that the commit Graph is
	 * empty.
	 */
	protected final void initializeRevWalk() throws Exception {
		if (commitGraphEnabled()) {
			enableAndWriteCommitGraph();
		}

		Optional<CommitGraph> commitGraph = db.getObjectDatabase()
				.getCommitGraph();

		if (commitGraphEnabled()) {
			assertTrue(commitGraph.isPresent());
			assertTrue(commitGraph.get().getCommitCnt() > 0);
		} else {
			assertTrue(commitGraph.isEmpty());
		}

		rw = new RevWalk(db);

		if (commitGraphEnabled()) {
			assertTrue(rw.commitGraph().getCommitCnt() > 0);
		} else {
			assertTrue(rw.commitGraph() == CommitGraph.EMPTY);
		}
	}

	@Override
	protected void assertCommit(RevCommit exp, RevCommit act) {
		try {
			assertSame(rw.parseCommit(exp), rw.parseCommit(act));
		} catch (IOException e) {
			throw new AssertionError(e.getMessage(), e);
		}
	}

	protected final void assertCommitCntInGraph(int expect) {
		assertEquals(expect, rw.commitGraph().getCommitCnt());
	}

	protected final void assertCommits(List<RevCommit> expect,
			List<RevCommit> actual) {
		assertEquals(expect.size(), actual.size());

		for (int i = 0; i < expect.size(); i++) {
			RevCommit c1 = expect.get(i);
			RevCommit c2 = actual.get(i);
			assertEquals(c1.getId(), c2.getId());
			assertEquals(c1.getTree(), c2.getTree());
			assertEquals(c1.getCommitTime(), c2.getCommitTime());
			assertArrayEquals(c1.getParents(), c2.getParents());
			assertArrayEquals(c1.getRawBuffer(), c2.getRawBuffer());
		}
	}

	protected final Ref branch(AnyObjectId start, String name)
			throws Exception {
		return Git.wrap(db).branchCreate().setName(name)
				.setStartPoint(start.name()).call();
	}

        protected List<RevCommit> travel(RevWalk walk, boolean enableCommitGraph) {
                db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
                                ConfigConstants.CONFIG_COMMIT_GRAPH, enableCommitGraph);
 
                List<RevCommit> commits = new ArrayList<>();
 
                if (enableCommitGraph) {
                        assertTrue(walk.commitGraph().getCommitCnt() > 0);
                } else {
                        assertEquals(EMPTY, walk.commitGraph());
                }
 
                for (RevCommit commit : walk) {
                        commits.add(commit);
                }
                return commits;
        }

	protected final List<RevCommit> travel(TreeFilter treeFilter,
			RevFilter revFilter, RevSort revSort, boolean enableCommitGraph,
			String... starts)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException, AmbiguousObjectException {
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, enableCommitGraph);

		try (RevWalk walk = new RevWalk(db)) {
			walk.setTreeFilter(treeFilter);
			walk.setRevFilter(revFilter);
			walk.sort(revSort);
			walk.setRetainBody(false);
			for (String start : starts) {
				walk.markStart(walk.lookupCommit(db.resolve(start)));
			}
			return travel(walk, enableCommitGraph);
		}
	}

	protected final void enableAndWriteCommitGraph() throws Exception {
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_CHANGED_PATHS, true);
		GC gc = new GC(db);
		gc.setAuto(false);
		gc.gc().get();
	}

	protected final void reinitializeRevWalk() {
		rw.close();
		rw = new RevWalk(db);
	}

	/**
	 * Mock JGit config that returns {@code true} for
	 * {@code commitGraph.readChangedPaths} if this test instance is running
	 * with an enabled commit graph
	 */
	private final class MockConfig extends FileBasedConfig {
		private MockConfig() {
			super(null, null);
		}

		@Override
		public void load() throws IOException, ConfigInvalidException {
			// Do nothing
		}

		@Override
		public void save() throws IOException {
			// Do nothing
		}

		@Override
		public boolean isOutdated() {
			return false;
		}

		@Override
		public String toString() {
			return "MockConfig";
		}

		@Override
		public boolean getBoolean(final String section, final String name,
				final boolean defaultValue) {
			if (commitGraphEnabled()
					&& section
							.equals(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION)
					&& name.equals(
							ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS)) {
				return true;
			}
			return defaultValue;
		}
	}

}
