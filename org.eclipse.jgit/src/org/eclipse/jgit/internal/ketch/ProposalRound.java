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

import static org.eclipse.jgit.internal.ketch.Proposal.State.RUNNING;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.reftree.Command;
import org.eclipse.jgit.internal.storage.reftree.RefTree;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/** A {@link Round} that aggregates and sends user {@link Proposal}s. */
class ProposalRound extends Round {
	private final List<Proposal> todo;
	private RefTree queuedTree;

	ProposalRound(KetchLeader leader, LogIndex head, List<Proposal> todo,
			@Nullable RefTree tree) {
		super(leader, head);
		this.todo = todo;

		if (tree != null && canCombine(todo)) {
			this.queuedTree = tree;
		} else {
			leader.roundHoldsReferenceToRefTree = false;
		}
	}

	private static boolean canCombine(List<Proposal> todo) {
		Proposal first = todo.get(0);
		for (int i = 1; i < todo.size(); i++) {
			if (!canCombine(first, todo.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean canCombine(Proposal a, Proposal b) {
		String aMsg = nullToEmpty(a.getMessage());
		String bMsg = nullToEmpty(b.getMessage());
		return aMsg.equals(bMsg) && canCombine(a.getAuthor(), b.getAuthor());
	}

	private static String nullToEmpty(@Nullable String str) {
		return str != null ? str : ""; //$NON-NLS-1$
	}

	private static boolean canCombine(@Nullable PersonIdent a,
			@Nullable PersonIdent b) {
		if (a != null && b != null) {
			// Same name and email address. Combine timestamp as the two
			// proposals are running concurrently and appear together or
			// not at all from the point of view of an outside reader.
			return a.getName().equals(b.getName())
					&& a.getEmailAddress().equals(b.getEmailAddress());
		}

		// If a and b are null, both will be the system identity.
		return a == null && b == null;
	}

	@Override
	void start() throws IOException {
		for (Proposal p : todo) {
			p.notifyState(RUNNING);
		}
		try {
			ObjectId id;
			try (Repository git = leader.openRepository();
					ProposedTimestamp ts = getSystem().getClock().propose()) {
				id = insertProposals(git, ts);
				blockUntil(ts);
			}
			runAsync(id);
		} catch (NoOp e) {
			for (Proposal p : todo) {
				p.success();
			}
			leader.lock.lock();
			try {
				leader.nextRound();
			} finally {
				leader.lock.unlock();
			}
		} catch (IOException e) {
			abort();
			throw e;
		}
	}

	private ObjectId insertProposals(Repository git, ProposedTimestamp ts)
			throws IOException, NoOp {
		ObjectId id;
		try (ObjectInserter inserter = git.newObjectInserter()) {
			// TODO(sop) Process signed push certificates.

			if (queuedTree != null) {
				id = insertSingleProposal(git, ts, inserter);
			} else {
				id = insertMultiProposal(git, ts, inserter);
			}

			stageCommands = makeStageList(git, inserter);
			inserter.flush();
		}
		return id;
	}

	private ObjectId insertSingleProposal(Repository git, ProposedTimestamp ts,
			ObjectInserter inserter) throws IOException, NoOp {
		// Fast path: tree is passed in with all proposals applied.
		ObjectId treeId = queuedTree.writeTree(inserter);
		queuedTree = null;
		leader.roundHoldsReferenceToRefTree = false;

		if (!ObjectId.zeroId().equals(acceptedOldIndex)) {
			try (RevWalk rw = new RevWalk(git)) {
				RevCommit c = rw.parseCommit(acceptedOldIndex);
				if (treeId.equals(c.getTree())) {
					throw new NoOp();
				}
			}
		}

		Proposal p = todo.get(0);
		CommitBuilder b = new CommitBuilder();
		b.setTreeId(treeId);
		if (!ObjectId.zeroId().equals(acceptedOldIndex)) {
			b.setParentId(acceptedOldIndex);
		}
		b.setCommitter(leader.getSystem().newCommitter(ts));
		b.setAuthor(p.getAuthor() != null ? p.getAuthor() : b.getCommitter());
		b.setMessage(message(p));
		return inserter.insert(b);
	}

	private ObjectId insertMultiProposal(Repository git, ProposedTimestamp ts,
			ObjectInserter inserter) throws IOException, NoOp {
		// The tree was not passed in, or there are multiple proposals
		// each needing their own commit. Reset the tree and replay each
		// proposal in order as individual commits.
		ObjectId lastIndex = acceptedOldIndex;
		ObjectId oldTreeId;
		RefTree tree;
		if (ObjectId.zeroId().equals(lastIndex)) {
			oldTreeId = ObjectId.zeroId();
			tree = RefTree.newEmptyTree();
		} else {
			try (RevWalk rw = new RevWalk(git)) {
				RevCommit c = rw.parseCommit(lastIndex);
				oldTreeId = c.getTree();
				tree = RefTree.read(rw.getObjectReader(), c.getTree());
			}
		}

		PersonIdent committer = leader.getSystem().newCommitter(ts);
		for (Proposal p : todo) {
			if (!tree.apply(p.getCommands())) {
				// This should not occur, previously during queuing the
				// commands were successfully applied to the pending tree.
				// Abort the entire round.
				throw new IOException(
						KetchText.get().queuedProposalFailedToApply);
			}

			ObjectId treeId = tree.writeTree(inserter);
			if (treeId.equals(oldTreeId)) {
				continue;
			}

			CommitBuilder b = new CommitBuilder();
			b.setTreeId(treeId);
			if (!ObjectId.zeroId().equals(lastIndex)) {
				b.setParentId(lastIndex);
			}
			b.setAuthor(p.getAuthor() != null ? p.getAuthor() : committer);
			b.setCommitter(committer);
			b.setMessage(message(p));
			lastIndex = inserter.insert(b);
		}
		if (lastIndex.equals(acceptedOldIndex)) {
			throw new NoOp();
		}
		return lastIndex;
	}

	private String message(Proposal p) {
		StringBuilder m = new StringBuilder();
		String msg = p.getMessage();
		if (msg != null && !msg.isEmpty()) {
			m.append(msg);
			while (m.length() < 2 || m.charAt(m.length() - 2) != '\n'
					|| m.charAt(m.length() - 1) != '\n') {
				m.append('\n');
			}
		}
		m.append(KetchConstants.TERM.getName())
				.append(": ") //$NON-NLS-1$
				.append(leader.getTerm());
		return m.toString();
	}

	void abort() {
		for (Proposal p : todo) {
			p.abort();
		}
	}

	@Override
	void success() {
		for (Proposal p : todo) {
			p.success();
		}
	}

	private List<ReceiveCommand> makeStageList(Repository git,
			ObjectInserter inserter) throws IOException {
		// For each branch, collapse consecutive updates to only most recent,
		// avoiding sending multiple objects in a rapid fast-forward chain, or
		// rewritten content.
		Map<String, ObjectId> byRef = new HashMap<>();
		for (Proposal p : todo) {
			for (Command c : p.getCommands()) {
				Ref n = c.getNewRef();
				if (n != null && !n.isSymbolic()) {
					byRef.put(n.getName(), n.getObjectId());
				}
			}
		}
		if (byRef.isEmpty()) {
			return Collections.emptyList();
		}

		Set<ObjectId> newObjs = new HashSet<>(byRef.values());
		StageBuilder b = new StageBuilder(
				leader.getSystem().getTxnStage(),
				acceptedNewIndex);
		return b.makeStageList(newObjs, git, inserter);
	}

	private void blockUntil(ProposedTimestamp ts)
			throws TimeIsUncertainException {
		List<ProposedTimestamp> times = todo.stream()
				.flatMap(p -> p.getProposedTimestamps().stream())
				.collect(Collectors.toCollection(ArrayList::new));
		times.add(ts);

		try {
			Duration maxWait = getSystem().getMaxWaitForMonotonicClock();
			ProposedTimestamp.blockUntil(times, maxWait);
		} catch (InterruptedException | TimeoutException e) {
			throw new TimeIsUncertainException(e);
		}
	}

	private static class NoOp extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
