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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
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

/** A {@link Round} that aggregates and sends user {@link Proposal}s. */
class ProposalRound extends Round {
	private final List<Proposal> todo;
	private RefTree queuedTree;

	ProposalRound(KetchLeader leader, LogId head, List<Proposal> todo,
			@Nullable RefTree tree) {
		super(leader, head);
		this.todo = todo;
		this.queuedTree = tree;
	}

	void start() throws IOException {
		for (Proposal p : todo) {
			p.notifyState(RUNNING);
		}
		try {
			ObjectId id;
			try (Repository git = leader.openRepository()) {
				id = insertProposals(git);
			}
			acceptAsync(id);
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
			abort(JGitText.get().transactionAborted);
			throw e;
		}
	}

	private ObjectId insertProposals(Repository git)
			throws IOException, NoOp {
		ObjectId id;
		try (ObjectInserter inserter = git.newObjectInserter()) {
			// TODO(sop) Process signed push certificates.

			if (queuedTree != null && todo.size() == 1) {
				id = insertSingleProposal(git, inserter);
			} else {
				if (queuedTree != null) {
					queuedTree = null;
					leader.copyOnQueue = false;
				}
				id = insertMultiProposal(git, inserter);
			}

			stageCommands = makeStageList(git, inserter);
			inserter.flush();
		}
		return id;
	}

	private ObjectId insertSingleProposal(Repository git,
			ObjectInserter inserter) throws IOException, NoOp {
		// Fast path of tree passed in with only one proposal to run.
		// Tree already has the proposal applied.
		ObjectId treeId = queuedTree.writeTree(inserter);
		queuedTree = null;
		leader.copyOnQueue = false;

		if (!ObjectId.zeroId().equals(acceptedOld)) {
			try (RevWalk rw = new RevWalk(git)) {
				RevCommit c = rw.parseCommit(acceptedOld);
				if (treeId.equals(c.getTree())) {
					throw new NoOp();
				}
			}
		}

		Proposal p = todo.get(0);
		CommitBuilder b = new CommitBuilder();
		b.setTreeId(treeId);
		if (!ObjectId.zeroId().equals(acceptedOld)) {
			b.setParentId(acceptedOld);
		}
		b.setCommitter(leader.getSystem().newCommitter());
		b.setAuthor(p.getAuthor() != null ? p.getAuthor() : b.getCommitter());
		b.setMessage(message(p));
		return inserter.insert(b);
	}

	private ObjectId insertMultiProposal(Repository git,
			ObjectInserter inserter)
			throws IOException, NoOp {
		// The tree was not passed in, or there are multiple proposals
		// each needing their own commit. Reset the tree to acceptedOld
		// and replay the proposals.
		ObjectId last = acceptedOld;
		ObjectId oldTree;
		RefTree tree;
		if (ObjectId.zeroId().equals(last)) {
			oldTree = ObjectId.zeroId();
			tree = RefTree.newEmptyTree();
		} else {
			try (RevWalk rw = new RevWalk(git)) {
				RevCommit c = rw.parseCommit(last);
				oldTree = c.getTree();
				tree = RefTree.read(rw.getObjectReader(), c.getTree());
			}
		}

		PersonIdent committer = leader.getSystem().newCommitter();
		for (Proposal p : todo) {
			if (!tree.apply(p.getCommands())) {
				// This should not occur, previously during queuing the
				// commands were successfully applied to the pending tree.
				// Abort the entire round.
				throw new IOException(
						KetchText.get().queuedProposalFailedToApply);
			}

			ObjectId treeId = tree.writeTree(inserter);
			if (treeId.equals(oldTree)) {
				continue;
			}

			CommitBuilder b = new CommitBuilder();
			b.setTreeId(treeId);
			if (!ObjectId.zeroId().equals(last)) {
				b.setParentId(last);
			}
			b.setAuthor(p.getAuthor() != null ? p.getAuthor() : committer);
			b.setCommitter(committer);
			b.setMessage(message(p));
			last = inserter.insert(b);
		}
		if (last.equals(acceptedOld)) {
			throw new NoOp();
		}
		return last;
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

	void abort(String msg) {
		for (Proposal p : todo) {
			p.abort(msg);
		}
	}

	void success() {
		for (Proposal p : todo) {
			p.success();
		}
	}

	private List<ReceiveCommand> makeStageList(Repository git,
			ObjectInserter inserter) throws IOException {
		// Collapse consecutive updates to only most recent, avoiding sending
		// multiple objects in a rapid fast-forward chain, or rewritten content.
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
				acceptedNew);
		return b.makeStageList(newObjs, git, inserter);
	}


	private static class NoOp extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
