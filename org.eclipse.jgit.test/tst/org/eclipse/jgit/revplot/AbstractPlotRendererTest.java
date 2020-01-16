/*
 * Copyright (C) 2014 Rüdiger Herrmann <ruediger.herrmann@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revplot;

import static org.junit.Assert.assertEquals;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

public class AbstractPlotRendererTest extends RepositoryTestCase {

	private Git git;
	private TestPlotRenderer plotRenderer;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		plotRenderer = new TestPlotRenderer();
	}

	@Test
	public void testDrawTextAlignment() throws Exception {
		git.commit().setMessage("initial commit").call();
		git.branchCreate().setName("topic").call();
		git.checkout().setName("topic").call();
		git.commit().setMessage("commit 1 on topic").call();
		git.commit().setMessage("commit 2 on topic").call();
		git.checkout().setName("master").call();
		git.commit().setMessage("commit on master").call();
		MergeResult mergeCall = merge(db.resolve("topic"));
		ObjectId start = mergeCall.getNewHead();
		try (PlotWalk walk = new PlotWalk(db)) {
			walk.markStart(walk.parseCommit(start));
			PlotCommitList<PlotLane> commitList = new PlotCommitList<>();
			commitList.source(walk);
			commitList.fillTo(1000);

			for (int i = 0; i < commitList.size(); i++)
				plotRenderer.paintCommit(commitList.get(i), 30);

			List<Integer> indentations = plotRenderer.indentations;
			assertEquals(indentations.get(2), indentations.get(3));
		}
	}

	private MergeResult merge(ObjectId includeId) throws GitAPIException {
		return git.merge().setFastForward(FastForwardMode.NO_FF)
				.include(includeId).call();
	}

	private static class TestPlotRenderer extends
			AbstractPlotRenderer<PlotLane, Object> {

		List<Integer> indentations = new LinkedList<>();

		@Override
		protected int drawLabel(int x, int y, Ref ref) {
			return 0;
		}

		@Override
		protected Object laneColor(PlotLane myLane) {
			return null;
		}

		@Override
		protected void drawLine(Object color, int x1, int y1, int x2, int y2,
				int width) {
			// do nothing
		}

		@Override
		protected void drawCommitDot(int x, int y, int w, int h) {
			// do nothing
		}

		@Override
		protected void drawBoundaryDot(int x, int y, int w, int h) {
			// do nothing
		}

		@Override
		protected void drawText(String msg, int x, int y) {
			indentations.add(Integer.valueOf(x));
		}
	}

}
