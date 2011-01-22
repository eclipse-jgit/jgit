/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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

package org.eclipse.jgit.subtree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * This class allows you to build up a pseudo-model of what the results from a
 * subtree split/rewrite should look like and then validates that the result
 * matches.
 */
class SubtreeValidator {

	protected abstract class ModelCommit<T extends ModelCommit> {

		protected RevCommit source;

		protected RevCommit result;

		protected String name;

		protected String[] parents;

		protected HashMap<String, String> subtrees = new HashMap<String, String>();

		@Override
		public String toString() {
			return name;
		}

		@SuppressWarnings("unchecked")
		protected T setParents(String... parents) {
			this.parents = parents;
			return (T) this;
		}

		@SuppressWarnings("unchecked")
		protected T addSubtree(String id, String commit) {
			subtrees.put(id, commit);
			return (T) this;
		}

		protected void setName(String name) {
			this.name = name;
		}

		@Override
		protected abstract ModelCommit clone()
				throws CloneNotSupportedException;

		@SuppressWarnings("unchecked")
		protected T copy(ModelCommit<T> mc) {
			name = mc.name;
			parents = Arrays.copyOf(mc.parents, mc.parents.length);
			subtrees = new HashMap<String, String>(mc.subtrees);
			return (T) this;
		}

	}

	protected class RewrittenCommit extends ModelCommit<RewrittenCommit> {

		protected String sourceName;

		protected RewrittenCommit(String sourceName, RevCommit source) {
			this.sourceName = sourceName;
			this.source = source;
		}

		@Override
		protected RewrittenCommit clone() throws CloneNotSupportedException {
			return new RewrittenCommit(sourceName, source).setParents(parents)
					.copy(this);
		}

		@Override
		protected RewrittenCommit addSubtree(String id, String commit) {
			throw new RuntimeException("Add subtrees to source commit");
		}

	}

	protected class NormalCommit extends ModelCommit<NormalCommit> {
		protected boolean rewrite;

		protected NormalCommit(RevCommit commit) {
			this.source = commit;
			this.result = commit;
		}

		protected NormalCommit setRewrite(boolean rewrite) {
			this.rewrite = rewrite;
			return this;
		}

		@Override
		protected NormalCommit clone() throws CloneNotSupportedException {
			return new NormalCommit(result).setRewrite(rewrite).copy(this);
		}

	}

	protected class SubtreeCommit extends ModelCommit<SubtreeCommit> {
		protected SubtreeCommit(RevCommit commit) {
			this.source = commit;
			this.result = commit;
		}

		@Override
		protected SubtreeCommit clone() throws CloneNotSupportedException {
			return new SubtreeCommit(result).copy(this);
		}

	}

	protected class SplitCommit extends ModelCommit<SplitCommit> {
		protected String subtree;

		protected SplitCommit(String subtree, RevCommit source) {
			this.subtree = subtree;
			this.source = source;
		}

		@Override
		protected SplitCommit clone() throws CloneNotSupportedException {
			return new SplitCommit(subtree, source).copy(this);
		}

	}

	private TreeMap<String, ModelCommit> commits = new TreeMap<String, ModelCommit>();

	private RevCommit start;

	private Repository db;

	private RevWalk rw;

	private String[] subtreePaths = new String[0];

	protected SubtreeValidator(Repository db, RevWalk rw) {
		this.db = db;
		this.rw = rw;
	}

	protected SubtreeValidator setSplitPaths(String... paths) {
		subtreePaths = paths;
		return this;
	}

	private <T extends ModelCommit> T add(String name, T mc) {
		if (commits.containsKey(name)) {
			throw new IllegalArgumentException("Commit " + name
					+ " already exists");
		}
		mc.setName(name);
		commits.put(name, mc);
		return mc;
	}

	protected SubtreeCommit subtree(String name, RevCommit commit) {
		return add(name, new SubtreeCommit(commit));
	}

	protected SplitCommit split(String name, String subtree, RevCommit source) {
		return add(name, new SplitCommit(subtree, source));
	}

	protected NormalCommit normal(String name, RevCommit c) {
		return add(name, new NormalCommit(c));
	}

	protected RewrittenCommit rewritten(String name, String parentName) {
		if (!(commits.get(parentName) instanceof NormalCommit)) {
			throw new IllegalArgumentException("Parent is not a normal commit");
		}
		NormalCommit parent = (NormalCommit) commits.get(parentName);
		parent.rewrite = true;
		return add(name, new RewrittenCommit(parentName, parent.result));
	}

	protected SubtreeValidator setStart(RevCommit start) {
		this.start = start;
		return this;
	}

	private void validateParentList(ModelCommit mc)
			throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		// Check the length
		rw.parseCommit(mc.result);
		assertNotNull("Parents not set for " + mc.name, mc.parents);
		assertEquals("Parent length of " + mc.name, mc.parents.length,
				mc.result.getParentCount());
		// Check the contents
		List<RevCommit> parentList = Arrays.asList(mc.result.getParents());
		for (String p : mc.parents) {
			ModelCommit pmc = commits.get(p);
			assertNotNull("Info not provided for parent " + p + " of commit "
					+ mc.name, pmc);
			assertTrue("Parents of " + mc.name + " do not contain " + pmc.name,
					parentList.contains(pmc.result));
		}
	}

	private void validateSubtrees(SubtreeSplitter ss, ModelCommit mc) {

		Map<String, String> subtrees = null;
		if (mc instanceof RewrittenCommit) {
			RewrittenCommit rc = (RewrittenCommit) mc;
			NormalCommit nc = (NormalCommit) commits.get(rc.sourceName);
			subtrees = nc.subtrees;
		} else if (mc instanceof NormalCommit) {
			NormalCommit nc = (NormalCommit) mc;
			subtrees = nc.subtrees;
		} else {
			return;
		}

		for (SubtreeContext sc : ss.getSubtreeContexts().values()) {
			String resultName = subtrees.get(sc.id);
			ModelCommit resultMc = resultName != null ? commits.get(resultName) : null;
			RevCommit result = resultMc != null ? resultMc.result : null;
			RevCommit split = sc.getSplitCommit(mc.source);
			split = split == SubtreeContext.NO_SUBTREE ? null : split;
			assertEquals("Subtree " + sc.id + " of " + mc.name, result, split);
		}

		for (Object id : subtrees.keySet()) {
			boolean found = false;
			for (SubtreeContext sc : ss.getSubtreeContexts().values()) {
				if (sc.getId().equals(id)) {
					found = true;
					break;
				}
			}

			assertTrue("Subtree id " + id + " can not be found on " + mc.name,
					found);
		}
	}

	private boolean compareTree(ObjectId t1, ObjectId t2)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		TreeWalk tw = new TreeWalk(db);
		try {
			tw.addTree(t1);
			tw.addTree(t2);
			tw.setFilter(TreeFilter.ANY_DIFF);
			while (tw.next()) {
				if (!tw.getPathString().equals(".gitsubtree")) {
					return false;
				}
			}
			return true;
		} finally {
			tw.release();
		}
	}

	protected void validate() throws IOException {

		// Do the split
		SubtreeSplitter ss = new SubtreeSplitter(db, rw);
		ArrayList<PathBasedContext> pbc = new ArrayList<PathBasedContext>();
		for (String subtreePath : subtreePaths) {
			pbc.add(new PathBasedContext(subtreePath, subtreePath));
		}
		ss.splitSubtrees(start, pbc, SubtreeSplitter.REWRITE_ALL);
		Collection<SubtreeContext> contexts = ss.getSubtreeContexts().values();
		Map<ObjectId, RevCommit> rewritten = ss.getRewrittenCommits();

		// Delete old tags
		for (Ref ref : db.getRefDatabase().getRefs("refs/tags").values()) {
			db.getRefDatabase().newUpdate(ref.getName(), false).delete(rw);
		}

		// 'Load' the new commits
		for (String name : commits.keySet()) {
			ModelCommit mc = commits.get(name);
			if (mc instanceof SplitCommit) {
				SplitCommit sc = (SplitCommit) mc;
				for (SubtreeContext candidate : contexts) {
					if (candidate.getId().equals(sc.subtree)) {
						sc.result = candidate.getSplitCommit(sc.source);
						break;
					}
				}
			} else if (mc instanceof RewrittenCommit) {
				RewrittenCommit rc = (RewrittenCommit) mc;
				rc.result = rewritten.get(rc.source);
			}

			assertNotNull("Couldn't find commit for " + mc.name, mc.result);

			// Create a tag. This is handy for debugging tests using gitk.
			rw.parseCommit(mc.result);
			TagBuilder newTag = new TagBuilder();
			newTag.setTag(mc.name);
			newTag.setMessage(mc.name);
			newTag.setObjectId(mc.result);
			ObjectInserter oi = db.newObjectInserter();
			oi.insert(newTag);
			oi.flush();
			oi.release();
			RefUpdate tagRef = db.updateRef("refs/tags/" + mc.name);
			tagRef.setNewObjectId(rw.parseAny(newTag.getObjectId()));
			tagRef.setForceUpdate(true);
			tagRef.update(rw);
		}

		// Finally, make sure everything looks right.
		for (String name : commits.keySet()) {
			ModelCommit mc = commits.get(name);
			validateParentList(mc);
			validateSubtrees(ss, mc);
			if (mc instanceof SplitCommit) {
				SplitCommit sc = (SplitCommit) mc;
				// Check the subtree contents.
				TreeWalk tw = TreeWalk.forPath(db, sc.subtree,
						sc.source.getTree());
				assertEquals(sc.result.getTree(), tw.getObjectId(0));
				tw.release();
				// Make sure this commit isn't in the rewritten list.
				assertNull(rewritten.get(sc.result));
				// Make sure something was actually done.
				assertNotNull(sc.result);
				// Make sure this commit doesn't match its source.
				assertNotSame(sc.source, sc.result);
			} else if (mc instanceof RewrittenCommit) {
				RewrittenCommit rc = (RewrittenCommit) mc;
				// Make sure something was actually done.
				assertNotNull(rc.result);
				// Make sure the parents are as expected.
				assertTrue("Tree of rewritten commit " + rc.name
						+ " does not match source commit",
						compareTree(rc.source.getTree(), rc.result.getTree()));
				// Make sure it is actually a different commit.
				assertNotSame(rc.source, rc.result);
			} else if (mc instanceof NormalCommit) {
				NormalCommit nc = (NormalCommit) mc;
				assertNotNull(
						nc.name
								+ " should be in rewritten map since it's a mainline commit",
						rewritten.get(nc.result));
				if (!nc.rewrite) {
					assertEquals(nc.name + " should not be rewritten",
							nc.result, rewritten.get(nc.result));
				}
			} else if (mc instanceof SubtreeCommit) {
				SubtreeCommit sc = (SubtreeCommit) mc;
				assertNull(
						sc.name + " is a subtree and sould not be rewritten",
						rewritten.get(sc.result));
			}
		}
	}

	protected void reset() {
		for (String name : commits.keySet()) {
			ModelCommit mc = commits.get(name);
			if (mc instanceof SplitCommit) {
				((SplitCommit) mc).result = null;
			} else if (mc instanceof RewrittenCommit) {
				((RewrittenCommit) mc).result = null;
			}
		}
		start = null;
	}

	@Override
	protected SubtreeValidator clone() throws CloneNotSupportedException {
		SubtreeValidator clone = new SubtreeValidator(db, rw)
				.setSplitPaths(subtreePaths);
		for (String name : commits.keySet()) {
			ModelCommit<?> mc = commits.get(name).clone();
			clone.commits.put(name, mc);
		}
		return clone;
	}

}