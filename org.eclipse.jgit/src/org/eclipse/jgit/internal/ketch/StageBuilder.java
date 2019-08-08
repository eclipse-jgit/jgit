/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.internal.ketch;

import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Constructs a set of commands to stage content during a proposal.
 */
public class StageBuilder {
	/**
	 * Acceptable number of references to send in a single stage transaction.
	 * <p>
	 * If the number of unique objects exceeds this amount the builder will
	 * attempt to decrease the reference count by chaining commits..
	 */
	private static final int SMALL_BATCH_SIZE = 5;

	/**
	 * Acceptable number of commits to chain together using parent pointers.
	 * <p>
	 * When staging many unique commits the {@link StageBuilder} batches
	 * together unrelated commits as parents of a temporary commit. After the
	 * proposal completes the temporary commit is discarded and can be garbage
	 * collected by all replicas.
	 */
	private static final int TEMP_PARENT_BATCH_SIZE = 128;

	private static final byte[] PEEL = { ' ', '^' };

	private final String txnStage;
	private final String txnId;

	/**
	 * Construct a stage builder for a transaction.
	 *
	 * @param txnStageNamespace
	 *            namespace for transaction references to build
	 *            {@code "txnStageNamespace/txnId.n"} style names.
	 * @param txnId
	 *            identifier used to name temporary staging refs.
	 */
	public StageBuilder(String txnStageNamespace, ObjectId txnId) {
		this.txnStage = txnStageNamespace;
		this.txnId = txnId.name();
	}

	/**
	 * Compare two RefTrees and return commands to stage new objects.
	 * <p>
	 * This method ignores the lineage between the two RefTrees and does a
	 * straight diff on the two trees. New objects will be staged. The diff
	 * strategy is useful to catch-up a lagging replica, without sending every
	 * intermediate step. This may mean the replica does not have the same
	 * object set as other replicas if there are rewinds or branch deletes.
	 *
	 * @param git
	 *            source repository to read {@code oldTree} and {@code newTree}
	 *            from.
	 * @param oldTree
	 *            accepted RefTree on the replica ({@code refs/txn/accepted}).
	 *            Use {@link org.eclipse.jgit.lib.ObjectId#zeroId()} if the
	 *            remote does not have any ref tree, e.g. a new replica catching
	 *            up.
	 * @param newTree
	 *            RefTree being sent to the replica. The trees will be compared.
	 * @return list of commands to create {@code "refs/txn/stage/..."}
	 *         references on replicas anchoring new objects into the repository
	 *         while a transaction gains consensus.
	 * @throws java.io.IOException
	 *             {@code git} cannot be accessed to compare {@code oldTree} and
	 *             {@code newTree} to build the object set.
	 */
	public List<ReceiveCommand> makeStageList(Repository git, ObjectId oldTree,
			ObjectId newTree) throws IOException {
		try (RevWalk rw = new RevWalk(git);
				TreeWalk tw = new TreeWalk(rw.getObjectReader());
				ObjectInserter ins = git.newObjectInserter()) {
			if (AnyObjectId.isEqual(oldTree, ObjectId.zeroId())) {
				tw.addTree(new EmptyTreeIterator());
			} else {
				tw.addTree(rw.parseTree(oldTree));
			}
			tw.addTree(rw.parseTree(newTree));
			tw.setFilter(TreeFilter.ANY_DIFF);
			tw.setRecursive(true);

			Set<ObjectId> newObjs = new HashSet<>();
			while (tw.next()) {
				if (tw.getRawMode(1) == TYPE_GITLINK
						&& !tw.isPathSuffix(PEEL, 2)) {
					newObjs.add(tw.getObjectId(1));
				}
			}

			List<ReceiveCommand> cmds = makeStageList(newObjs, git, ins);
			ins.flush();
			return cmds;
		}
	}

	/**
	 * Construct a set of commands to stage objects on a replica.
	 *
	 * @param newObjs
	 *            objects to send to a replica.
	 * @param git
	 *            local repository to read source objects from. Required to
	 *            perform minification of {@code newObjs}.
	 * @param inserter
	 *            inserter to write temporary commit objects during minification
	 *            if many new branches are created by {@code newObjs}.
	 * @return list of commands to create {@code "refs/txn/stage/..."}
	 *         references on replicas anchoring {@code newObjs} into the
	 *         repository while a transaction gains consensus.
	 * @throws java.io.IOException
	 *             {@code git} cannot be accessed to perform minification of
	 *             {@code newObjs}.
	 */
	public List<ReceiveCommand> makeStageList(Set<ObjectId> newObjs,
			@Nullable Repository git, @Nullable ObjectInserter inserter)
					throws IOException {
		if (git == null || newObjs.size() <= SMALL_BATCH_SIZE) {
			// Without a source repository can only construct unique set.
			List<ReceiveCommand> cmds = new ArrayList<>(newObjs.size());
			for (ObjectId id : newObjs) {
				stage(cmds, id);
			}
			return cmds;
		}

		List<ReceiveCommand> cmds = new ArrayList<>();
		List<RevCommit> commits = new ArrayList<>();
		reduceObjects(cmds, commits, git, newObjs);

		if (inserter == null || commits.size() <= 1
				|| (cmds.size() + commits.size()) <= SMALL_BATCH_SIZE) {
			// Without an inserter to aggregate commits, or for a small set of
			// commits just send one stage ref per commit.
			for (RevCommit c : commits) {
				stage(cmds, c.copy());
			}
			return cmds;
		}

		// 'commits' is sorted most recent to least recent commit.
		// Group batches of commits and build a chain.
		// TODO(sop) Cluster by restricted graphs to support filtering.
		ObjectId tip = null;
		for (int end = commits.size(); end > 0;) {
			int start = Math.max(0, end - TEMP_PARENT_BATCH_SIZE);
			List<RevCommit> batch = commits.subList(start, end);
			List<ObjectId> parents = new ArrayList<>(1 + batch.size());
			if (tip != null) {
				parents.add(tip);
			}
			parents.addAll(batch);

			CommitBuilder b = new CommitBuilder();
			b.setTreeId(batch.get(0).getTree());
			b.setParentIds(parents);
			b.setAuthor(tmpAuthor(batch));
			b.setCommitter(b.getAuthor());
			tip = inserter.insert(b);
			end = start;
		}
		stage(cmds, tip);
		return cmds;
	}

	private static PersonIdent tmpAuthor(List<RevCommit> commits) {
		// Construct a predictable author using most recent commit time.
		int t = 0;
		for (int i = 0; i < commits.size();) {
			t = Math.max(t, commits.get(i).getCommitTime());
		}
		String name = "Ketch Stage"; //$NON-NLS-1$
		String email = "tmp@tmp"; //$NON-NLS-1$
		return new PersonIdent(name, email, t * 1000L, 0);
	}

	private void reduceObjects(List<ReceiveCommand> cmds,
			List<RevCommit> commits, Repository git,
			Set<ObjectId> newObjs) throws IOException {
		try (RevWalk rw = new RevWalk(git)) {
			rw.setRetainBody(false);

			for (ObjectId id : newObjs) {
				RevObject obj = rw.parseAny(id);
				if (obj instanceof RevCommit) {
					rw.markStart((RevCommit) obj);
				} else {
					stage(cmds, id);
				}
			}

			for (RevCommit c; (c = rw.next()) != null;) {
				commits.add(c);
				rw.markUninteresting(c);
			}
		}
	}

	private void stage(List<ReceiveCommand> cmds, ObjectId id) {
		int estLen = txnStage.length() + txnId.length() + 5;
		StringBuilder n = new StringBuilder(estLen);
		n.append(txnStage).append(txnId).append('.');
		n.append(Integer.toHexString(cmds.size()));
		cmds.add(new ReceiveCommand(ObjectId.zeroId(), id, n.toString()));
	}
}
