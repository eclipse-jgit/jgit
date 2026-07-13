/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;

import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.errors.CancelledException;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.junit.Test;

/**
 * Tests that {@link RevWalk} cooperatively cancels traversal when a
 * {@link ProgressMonitor} reports cancellation, including during tree filtering
 * and topological sorting.
 */
public class RevWalkCancellationTest extends RevWalkTestCase {

	private static final class AlwaysCancelledMonitor
			implements ProgressMonitor {

		AlwaysCancelledMonitor() {
		}

		@Override
		public void start(int totalTasks) {
			// Not used.
		}

		@Override
		public void beginTask(String title, int totalWork) {
			// Not used.
		}

		@Override
		public void update(int completed) {
			// Not used.
		}

		@Override
		public void endTask() {
			// Not used.
		}

		@Override
		public boolean isCancelled() {
			return true;
		}

		@Override
		public void showDuration(boolean enabled) {
			// Not used.
		}
	}

	private void filter(String path) {
		rw.setTreeFilter(AndTreeFilter.create(
				PathFilterGroup.createFromStrings(Collections.singleton(path)),
				TreeFilter.ANY_DIFF));
	}

	@Test
	public void testPathFilteredWalkThrowsWhenCancelled() throws Exception {
		RevCommit a = commit(tree(file("a", blob("1"))));
		RevCommit b = commit(tree(file("a", blob("2"))), a);
		RevCommit c = commit(tree(file("a", blob("3"))), b);
		RevCommit d = commit(tree(file("a", blob("4"))), c);
		RevCommit e = commit(tree(file("a", blob("5"))), d);

		filter("a");
		markStart(e);
		rw.setProgressMonitor(new AlwaysCancelledMonitor());

		try {
			while (rw.next() != null) {
				// Keep pumping the walk; it must eventually observe
				// cancellation and throw before running out of commits.
			}
			fail("Expected CancelledException while traversing a path-filtered walk");
		} catch (CancelledException expected) {
			assertTrue(expected.getMessage() != null);
		}
	}

	@Test
	public void testTopoSortedPathFilteredWalkThrowsWhenCancelled()
			throws Exception {
		RevCommit a = commit(tree(file("a", blob("1"))));
		RevCommit b = commit(tree(file("a", blob("2"))), a);
		RevCommit c1 = commit(-5, tree(file("a", blob("3"))), b);
		RevCommit c2 = commit(10,
				tree(file("a", blob("2")), file("b", blob("1"))), b);
		RevCommit d = commit(tree(file("a", blob("3")), file("b", blob("1"))),
				c1, c2);
		RevCommit e = commit(tree(file("a", blob("4")), file("b", blob("2"))),
				d);

		rw.sort(RevSort.TOPO);
		filter("a");
		markStart(e);
		rw.setProgressMonitor(new AlwaysCancelledMonitor());

		try {
			while (rw.next() != null) {
				// Keep pumping the walk; the topo phases and TreeRevFilter
				// must observe cancellation before the walk completes.
			}
			fail("Expected CancelledException while traversing a topo-sorted, "
					+ "path-filtered walk");
		} catch (CancelledException expected) {
			assertTrue(expected.getMessage() != null);
		}
	}

	@Test
	public void testFollowRenameCancellationAroundRenameDetection()
			throws Exception {
		RevCommit a = commit(tree(file("a", blob("A"))));

		// rename a to b
		CommitBuilder commitBuilder = commitBuilder().parent(a)
				.add("b", blob("A")).rm("a");
		RevCommit renameCommit = commitBuilder.create();

		FollowFilter followFilter = FollowFilter.create("b",
				new Config().get(DiffConfig.KEY));
		rw.setTreeFilter(followFilter);
		markStart(renameCommit);
		rw.setProgressMonitor(new AlwaysCancelledMonitor());

		try {
			while (rw.next() != null) {
				// Keep pumping; cancellation should surface around rename
				// detection triggered by updateFollowFilter().
			}
			fail("Expected CancelledException while following renames");
		} catch (CancelledException expected) {
			assertTrue(expected.getMessage() != null);
		}
	}
}
