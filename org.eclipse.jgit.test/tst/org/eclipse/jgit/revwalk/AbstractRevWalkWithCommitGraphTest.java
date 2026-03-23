/*
 * Copyright (C) 2023, Tencent and others
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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

public abstract class AbstractRevWalkWithCommitGraphTest extends RevWalkTestCase {

	@Override
	public void setUp() throws Exception {
		super.setUp();
		reinitializeRevWalk();
		mockSystemReader.setJGitConfig(new MockConfig());
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

	protected final Ref branch(RevCommit commit, String name) throws Exception {
		return Git.wrap(db).branchCreate().setName(name)
				.setStartPoint(commit.name()).call();
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
	}

	protected final void enableAndWriteCommitGraph() throws Exception {
		db.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, true);
		db.getConfig().setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_CHANGED_PATHS, true);
		GC gc = new GC(db);
		gc.gc().get();
	}

	protected final void reinitializeRevWalk() {
		rw.close();
		rw = new RevWalk(db);
	}

	private static final class MockConfig extends FileBasedConfig {
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
			if (section.equals(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION)
					&& name.equals(
							ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS)) {
				return true;
			}
			return defaultValue;
		}
	}

}
