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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalkTestCase;
import org.junit.Test;

public class PlotCommitListTest extends RevWalkTestCase {

	class CommitListAssert {
		private PlotCommitList<PlotLane> pcl;
		private PlotCommit<PlotLane> current;
		private int nextIndex = 0;

		CommitListAssert(PlotCommitList<PlotLane> pcl) {
			this.pcl = pcl;
		}

		public CommitListAssert commit(RevCommit id) {
			assertTrue("Unexpected end of list at pos#"+nextIndex, pcl.size()>nextIndex);
			current = pcl.get(nextIndex++);
			assertEquals("Expected commit not found at pos#" + (nextIndex - 1),
					id.getId(), current.getId());
			return this;
		}

		public CommitListAssert lanePos(int pos) {
			PlotLane lane = current.getLane();
			assertEquals("Position of lane of commit #" + (nextIndex - 1)
					+ " not as expected.", pos, lane.getPosition());
			return this;
		}

		public CommitListAssert parents(RevCommit... parents) {
			assertEquals("Number of parents of commit #" + (nextIndex - 1)
					+ " not as expected.", parents.length,
					current.getParentCount());
			for (int i = 0; i < parents.length; i++)
				assertEquals("Unexpected parent of commit #" + (nextIndex - 1),
						parents[i], current.getParent(i));
			return this;
		}

		public CommitListAssert noMoreCommits() {
			assertEquals("Unexpected size of list", nextIndex, pcl.size());
			return this;
		}
	}

	@Test
	public void testLinear() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(b);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.lookupCommit(c.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);
		test.commit(c).lanePos(0).parents(b);
		test.commit(b).lanePos(0).parents(a);
		test.commit(a).lanePos(0).parents();
		test.noMoreCommits();
	}

	@Test
	public void testMerged() throws Exception {
		/**
		 * <pre>
		 *  a-c-d
		 *   \ /
		 *    b
		 * </pre>
		 */
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(b, c);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.lookupCommit(d.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);
		test.commit(d).lanePos(0).parents(b, c);
		test.commit(c).lanePos(0).parents(a);
		test.commit(b).lanePos(1).parents(a);
		test.commit(a).lanePos(0).parents();
		test.noMoreCommits();
	}

	@Test
	public void testMerged2() throws Exception {
		/**
		 * <pre>
		 *  a-----d
		 *   \ \
		 *    b c---e
		 * </pre>
		 *
		 * gitk shows this differently: It will show:
		 *
		 * <pre>
		 *  a----c-e
		 *   \ \
		 *    b d
		 * </pre>
		 */
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(a);
		final RevCommit e = commit(c);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.lookupCommit(e.getId()));
		pw.markStart(pw.lookupCommit(b.getId()));
		pw.markStart(pw.lookupCommit(d.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);
		test.commit(e).lanePos(0).parents(c);
		test.commit(d).lanePos(1).parents(a);
		test.commit(c).lanePos(0).parents(a);
		test.commit(b).lanePos(0).parents(a);
		test.commit(a).lanePos(1).parents();
		test.noMoreCommits();
	}

	@Test
	public void testSideBranch() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.lookupCommit(b.getId()));
		pw.markStart(pw.lookupCommit(c.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);
		test.commit(c).lanePos(0).parents(a);
		test.commit(b).lanePos(0).parents(a);
		test.commit(a).lanePos(1).parents();
		test.noMoreCommits();
	}

	@Test
	public void test2SideBranches() throws Exception {
		final RevCommit a = commit();
		final RevCommit b = commit(a);
		final RevCommit c = commit(a);
		final RevCommit d = commit(a);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.lookupCommit(b.getId()));
		pw.markStart(pw.lookupCommit(c.getId()));
		pw.markStart(pw.lookupCommit(d.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);
		test.commit(d).lanePos(0).parents(a);
		test.commit(c).lanePos(0).parents(a);
		test.commit(b).lanePos(0).parents(a);
		test.commit(a).lanePos(1).parents();
		test.noMoreCommits();
	}

	@Test
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
		// pw.markStart(pw.lookupCommit(a.getId()));
		pw.markStart(pw.lookupCommit(b.getId()));
		pw.markStart(pw.lookupCommit(c.getId()));
		pw.markStart(pw.lookupCommit(d.getId()));
		pw.markStart(pw.lookupCommit(e.getId()));
		// pw.markStart(pw.lookupCommit(f.getId()));
		pw.markStart(pw.lookupCommit(g.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);
		test.commit(g).lanePos(0).parents(f);
		test.commit(f).lanePos(0).parents(a);
		test.commit(e).lanePos(0).parents(a);
		test.commit(d).lanePos(0).parents(a);
		test.commit(c).lanePos(0).parents(a);
		test.commit(b).lanePos(0).parents(a);
		test.commit(a).lanePos(1).parents();
		test.noMoreCommits();
	}

	// test the history of the egit project between 9fdaf3c1 and e76ad9170f
	@Test
	public void testEgitHistory() throws Exception {
		final RevCommit merge_fix = commit();
		final RevCommit add_simple = commit(merge_fix);
		final RevCommit remove_unused = commit(merge_fix);
		final RevCommit merge_remove = commit(add_simple, remove_unused);
		final RevCommit resolve_handler = commit(merge_fix);
		final RevCommit clear_repositorycache = commit(merge_remove);
		final RevCommit add_Maven = commit(clear_repositorycache);
		final RevCommit use_remote = commit(clear_repositorycache);
		final RevCommit findToolBar_layout = commit(clear_repositorycache);
		final RevCommit merge_add_Maven = commit(findToolBar_layout, add_Maven);
		final RevCommit update_eclipse_iplog = commit(merge_add_Maven);
		final RevCommit changeset_implementation = commit(clear_repositorycache);
		final RevCommit merge_use_remote = commit(update_eclipse_iplog,
				use_remote);
		final RevCommit disable_source = commit(merge_use_remote);
		final RevCommit update_eclipse_iplog2 = commit(merge_use_remote);
		final RevCommit merge_disable_source = commit(update_eclipse_iplog2,
				disable_source);
		final RevCommit merge_changeset_implementation = commit(
				merge_disable_source, changeset_implementation);
		final RevCommit clone_operation = commit(merge_disable_source,
				merge_changeset_implementation);
		final RevCommit update_eclipse = commit(add_Maven);
		final RevCommit merge_resolve_handler = commit(clone_operation,
				resolve_handler);
		final RevCommit disable_comment = commit(clone_operation);
		final RevCommit merge_disable_comment = commit(merge_resolve_handler,
				disable_comment);
		final RevCommit fix_broken = commit(merge_disable_comment);
		final RevCommit add_a_clear = commit(fix_broken);
		final RevCommit merge_update_eclipse = commit(add_a_clear,
				update_eclipse);
		final RevCommit sort_roots = commit(merge_update_eclipse);
		final RevCommit fix_logged_npe = commit(merge_changeset_implementation);
		final RevCommit merge_fixed_logged_npe = commit(sort_roots,
				fix_logged_npe);

		PlotWalk pw = new PlotWalk(db);
		pw.markStart(pw.lookupCommit(merge_fixed_logged_npe.getId()));

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		CommitListAssert test = new CommitListAssert(pcl);

		test.commit(merge_fixed_logged_npe).parents(sort_roots, fix_logged_npe)
				.lanePos(0);
		test.commit(fix_logged_npe).parents(merge_changeset_implementation)
				.lanePos(0);
		test.commit(sort_roots).parents(merge_update_eclipse).lanePos(1);
		test.commit(merge_update_eclipse).parents(add_a_clear, update_eclipse)
				.lanePos(1);
		test.commit(add_a_clear).parents(fix_broken).lanePos(1);
		test.commit(fix_broken).parents(merge_disable_comment).lanePos(1);
		test.commit(merge_disable_comment)
				.parents(merge_resolve_handler, disable_comment).lanePos(1);
		test.commit(disable_comment).parents(clone_operation).lanePos(1);
		test.commit(merge_resolve_handler)
				.parents(clone_operation, resolve_handler).lanePos(2);
		test.commit(update_eclipse).parents(add_Maven).lanePos(3);
		test.commit(clone_operation)
				.parents(merge_disable_source, merge_changeset_implementation)
				.lanePos(1);
		test.commit(merge_changeset_implementation)
				.parents(merge_disable_source, changeset_implementation)
				.lanePos(0);
		test.commit(merge_disable_source)
				.parents(update_eclipse_iplog2, disable_source).lanePos(1);
		test.commit(update_eclipse_iplog2).parents(merge_use_remote).lanePos(0);
		test.commit(disable_source).parents(merge_use_remote).lanePos(1);
		test.commit(merge_use_remote).parents(update_eclipse_iplog, use_remote)
				.lanePos(0);
		test.commit(changeset_implementation).parents(clear_repositorycache)
				.lanePos(2);
		test.commit(update_eclipse_iplog).parents(merge_add_Maven).lanePos(0);
		test.commit(merge_add_Maven).parents(findToolBar_layout, add_Maven)
				.lanePos(0);
		test.commit(findToolBar_layout).parents(clear_repositorycache)
				.lanePos(0);
		test.commit(use_remote).parents(clear_repositorycache).lanePos(1);
		test.commit(add_Maven).parents(clear_repositorycache).lanePos(3);
		test.commit(clear_repositorycache).parents(merge_remove).lanePos(2);
		test.commit(resolve_handler).parents(merge_fix).lanePos(4);
		test.commit(merge_remove).parents(add_simple, remove_unused).lanePos(2);
		test.commit(remove_unused).parents(merge_fix).lanePos(0);
		test.commit(add_simple).parents(merge_fix).lanePos(1);
		test.commit(merge_fix).parents().lanePos(3);
		test.noMoreCommits();
	}

	@Test
	public void testRepo1Changes() throws Exception {
		testCommitGraphTopology(
				"**********************************************************"
						+ "**********************************************************"
						+ "**********************************************************"
						+ "**********************************************************"
						+ "***2*12*2*21**********3*21**2*12*8*21******4*21**7*21*3*21*"
						+ "2*21**3*21****2**13***5*2**2*14***7*2*37*13*13****8*2*5*23*"
						+ "12***a*31*3*21**2***24**13********5*21********2*2*3*21*14*"
						+ "******a*21*3*21*2*21****5*21***2*2*21********3*21*4*21*5*"
						+ "21*****3*2******17*8*21*2*21*2*21***2*21*2*21*2*21*9*21*4*"
						+ "21*2*21*2*21*******2*12**3*21****************7*31*4*21*15*"
						+ "6*21***2*2**13*4*21*7*5*31*13*3*21**3*21*2*21*2*21**3*21**"
						+ "**2*21*3*21*2*21****f*21*3*21*9*21***4*21***2*8*21*7*21*16*"
						+ "****2**3*24*12**6*21*******8*21**3*31*13*7*21**3**31*5***4*"
						+ "2*2**3**3*2*2*h*9*41*43*6*31*2*21*7*21*7*2*******2*21*3*21*"
						+ "c*2*****2*21************", false);
	}

	@Test
	public void testRepo2Changes() throws Exception {
		testCommitGraphTopology(
				"**2*21*3*21**********3*21**2*21*8*21******4*21**7*21*3*21*2*"
						+ "21**3*21*****3*21*****8*21***6*21*6*21*5*21*****8*21*6*"
						+ "21**9*21*3**21***3*21**4*21********5*21*********2*21*5*"
						+ "21*******b*21*3*21*2*21****5*21***2**31********3*21*4*"
						+ "21*5*21***********9*21*2*21*2*21*2*21***2*21*2*21*2*21*"
						+ "9*21*4*21*2*21*2*21*******2*21**4*21***************6*2"
						+ "1*3*21*5*21*2*21*****4*21*2*21*5*21*9*21*4*21**3*21*2*"
						+ "21*2*21**3*21****2*21*3*21*2*21****f*21*3*21*9*21***4*"
						+ "21***7*21*6*21*6*21******3*21*2*21**7*21*******8*21*2*"
						+ "21*3*21*7*21**3**31******a*21***e*******4*21*3*21*2*21*"
						+ "6*21********2*21*3*21******2*21************", false);
	}

	@Test
	public void testRepo3Changes() throws Exception {
		testCommitGraphTopology(
				"**2*3*4*2******19*1b*5*2*3*2**15*14***2*2**2*12***17***k*2*"
						+ "3*4*5*25***2*******1e*1c*1f**1c**K*2*", true);
	}

	/**
	 * Tests complicated commit graphs for consistency
	 *
	 * @param graphDescription
	 *            describes the commit graph which should be tested. The
	 *            description is processed from left to right.
	 *            <ul>
	 *            <li>Each '*' creates a commit. By default the only parent of a
	 *            commit is the commit which was created in the previous step</li>
	 *            <li>If a '*' is prefixed by n hexadecimal digits then the
	 *            commit will have n parents. Each digit determines one parent
	 *            as a relative reference: "1" refers to the commit created in
	 *            the previous step, "2" refers to the commit created two steps
	 *            before, "a" refers to the commit created 10 steps before and
	 *            so on.</li>
	 *            </ul>
	 * @param tagAllCommits
	 *            <code>true</code> if every commit should get a tag. This makes
	 *            it easier to debug and call e.g. gitk on a repo created by
	 *            this test
	 *
	 * @throws Exception
	 */
	public void testCommitGraphTopology(String graphDescription,
			boolean tagAllCommits)
			throws Exception {
		List<RevCommit> commits = createCommitGraph(graphDescription);

		PlotWalk pw = new PlotWalk(db);
		int tnr = 0;
		for (RevCommit c : commits) {
			pw.markStart(pw.lookupCommit(c.getId()));
			if (tagAllCommits)
				ltag("tag" + tnr++, c.getId());
		}

		PlotCommitList<PlotLane> pcl = new PlotCommitList<PlotLane>();
		pcl.source(pw);
		pcl.fillTo(Integer.MAX_VALUE);

		for (int i = 0; i < pcl.size(); i++) {
			PlotCommit<PlotLane> pc = pcl.get(i);
			int myPos = pc.getLane().getPosition();
			for (PlotLane l : pc.passingLanes)
				assertTrue("Commit #" + i
						+ " is located on a position of a passing lane " + l,
						myPos != l.position);
			int ci = lookupIndex(commits, pc.getId(), i);
			RevCommit c = commits.get(ci);

			assertEquals("Not the same number of parents on commit #" + i
					+ ": " + c.toString(), pc.getParentCount(),
					c.getParentCount());
			assertEquals("Not the same number of children on commit #" + i
					+ ": " + c.toString(), pc.getChildCount(),
					getChildrenCount(commits, c));
		}
	}

	public void ltag(String name, ObjectId tgt) throws IOException {
		RefUpdate tagRef = db.updateRef(Constants.R_TAGS + name);
		tagRef.setNewObjectId(tgt);
		tagRef.setForceUpdate(true);
		tagRef.setRefLogMessage("tagged " + name, false);
		tagRef.update();
	}

	private int lookupIndex(List<RevCommit> commits, ObjectId id, int i) {
		for (int j = i; j < commits.size(); j++)
			if (commits.get(j).getId().equals(id))
				return j;
		for (int j = 0; j < i; j++)
			if (commits.get(j).getId().equals(id))
				return j;
		return -1;
	}

	private int getChildrenCount(List<RevCommit> commits, RevCommit parent) {
		int nrOfChildren = 0;
		for (RevCommit c : commits) {
			for (RevCommit aktParent : c.getParents()) {
				if (aktParent.equals(parent))
					nrOfChildren++;
			}
		}
		return nrOfChildren;
	}

	public List<RevCommit> createCommitGraph(String graphDescription)
			throws Exception {
		RevCommit lastCommit = null;
		byte[] bytes = graphDescription.getBytes();
		List<RevCommit> commits = new ArrayList<RevCommit>();
		List<RevCommit> parents = new ArrayList<RevCommit>();
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] != '*') {
				do
					parents.add(commits.get(commits.size()
							- hexChar2int(bytes[i++])));
				while (bytes[i] != '*');
				lastCommit = commit(parents.toArray(new RevCommit[parents
						.size()]));
				parents.clear();
			} else
				lastCommit = (lastCommit == null) ? commit()
						: commit(lastCommit);
			commits.add(parseBody(lastCommit));
		}
		Collections.reverse(commits);
		return commits;
	}

	public static int hexChar2int(byte b) {
		if (b >= '0' && b <= '9')
			return b - '0';
		if (b >= 'a' && b <= 'z')
			return 10 + b - 'a';
		if (b >= 'A' && b <= 'Z')
			return 36 + b - 'A';
		else
			throw new IllegalArgumentException();
	}
}
