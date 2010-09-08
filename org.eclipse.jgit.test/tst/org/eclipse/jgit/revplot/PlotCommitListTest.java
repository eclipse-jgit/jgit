/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.revplot;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalkTestCase;

public class PlotCommitListTest extends RevWalkTestCase {
	@SuppressWarnings("boxing")
	public void testLinar() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.createCommit(c.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		assertCommitListTopo(pcl, c, new RevCommit[] { b }, 0, b,
				new RevCommit[] { a }, 0, a, new RevCommit[] {}, 0);
	}

	@SuppressWarnings("boxing")
	public void testMerged() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(b, c);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.createCommit(d.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		assertCommitListTopo(pcl, //
				d, new RevCommit[] { b, c }, 0, //
				c, new RevCommit[] { a }, 0, //
				b, new RevCommit[] { a }, 1, //
				a, new RevCommit[] {}, 0);
	}

	@SuppressWarnings("boxing")
	public void testSideBranch() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.createCommit(b.getId()));
		pw.markStart(pw.createCommit(c.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		assertCommitListTopo(pcl, //
				c, new RevCommit[] { a }, 0, //
				b, new RevCommit[] { a }, 1, //
				a, new RevCommit[] {}, 0);
	}

	@SuppressWarnings("boxing")
	public void test2SideBranches() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(a);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.createCommit(b.getId()));
		pw.markStart(pw.createCommit(c.getId()));
		pw.markStart(pw.createCommit(d.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		assertCommitListTopo(pcl, //
				d, new RevCommit[] { a }, 0, //
				c, new RevCommit[] { a }, 1, //
				b, new RevCommit[] { a }, 1, //
				a, new RevCommit[] {}, 0);
	}

	@SuppressWarnings("boxing")
	public void testBug300282_1() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(a);
		final RevCommit e = commit(a);
		final RevCommit f = commit(a);
		final RevCommit g = commit(f);

		PlotWalk pw = new PlotWalk(db);
		// TODO: when we add unnecessary commit's as tips (e.g. a commit which
		// is a parent of another tip) the walk will return those commits twice.
		// Find out why!
		// pw.markStart(pw.createCommit(a.getId()));
		pw.markStart(pw.createCommit(b.getId()));
		pw.markStart(pw.createCommit(c.getId()));
		pw.markStart(pw.createCommit(d.getId()));
		pw.markStart(pw.createCommit(e.getId()));
		// pw.markStart(pw.createCommit(f.getId()));
		pw.markStart(pw.createCommit(g.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		assertCommitListTopo(pcl, //
				g, new RevCommit[] { f }, 0, //
				f, new RevCommit[] { a }, 0, //
				e, new RevCommit[] { a }, 1, //
				d, new RevCommit[] { a }, 1, //
				c, new RevCommit[] { a }, 1, //
				b, new RevCommit[] { a }, 1, //
				a, new RevCommit[] {}, 0);
	}

	@SuppressWarnings("boxing")
	public void assertCommitListTopo(PlotCommitList<PlotLane> p,
			Object... commitAndPos) {
		assertEquals("wrong size of PlotCommitList", commitAndPos.length / 3,
				p.size());
		for (int i = 0; i < p.size(); i++) {
			PlotCommit<PlotLane> pc = p.get(i);
			assertEquals("wrong id for commit #" + i,
					((RevCommit) commitAndPos[i * 3]).getId(), pc.getId());
			assertEquals("wrong number of parents for commit #" + i,
					((RevCommit[]) commitAndPos[i * 3 + 1]).length,
					pc.getParentCount());
			for (int j = 0; j < pc.getParentCount(); j++) {
				assertEquals("wrong parent for commit #" + i,
						((RevCommit[]) commitAndPos[i * 3 + 1])[j],
						pc.getParent(j));
			}
			assertEquals("wrong lane for commit #" + i,
					commitAndPos[i * 3 + 2], (pc.getLane() == null) ? 0 : pc
							.getLane().getPosition());
		}
	}
}
