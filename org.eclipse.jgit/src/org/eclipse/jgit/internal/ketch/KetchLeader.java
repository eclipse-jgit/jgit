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

import static org.eclipse.jgit.internal.ketch.KetchLeader.State.CANDIDATE;
import static org.eclipse.jgit.internal.ketch.KetchLeader.State.LEADER;
import static org.eclipse.jgit.internal.ketch.KetchLeader.State.SHUTDOWN;
import static org.eclipse.jgit.internal.ketch.KetchReplica.Type.FOLLOWER;
import static org.eclipse.jgit.internal.ketch.Proposal.State.QUEUED;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.internal.storage.reftree.RefTree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Leader managing consensus across remote followers. */
public abstract class KetchLeader {
	private static final Logger log = LoggerFactory.getLogger(KetchLeader.class);

	static enum State {
		CANDIDATE, LEADER, DEPOSED, SHUTDOWN;
	}

	private final KetchSystem system;

	/** Leader's knowledge of replicas for this repository. */
	private KetchReplica[] voters;
	private KetchReplica[] followers;
	private LocalReplica self;

	/**
	 * Lock protecting all data within this leader instance.
	 * <p>
	 * This lock extends into the {@link KetchReplica} instances used by the
	 * leader. They share the same lock instance to simplify concurrency.
	 */
	final Lock lock;

	private State state = CANDIDATE;

	/** Term of this leader, once elected. */
	private long term;

	/**
	 * Pending proposals accepted into the queue in FIFO order.
	 * <p>
	 * These proposals were preflighted and do not contain any conflicts with
	 * each other and their expectations matched the leader's local view of the
	 * agreed upon {@code refs/txn/accepted} tree.
	 */
	private final List<Proposal> queued;

	/**
	 * Current state of the RefTree and applying all entries from
	 * {@link #queued}, in order. This is what the world will look like after
	 * execution of every entry currently in {@code queued}. New proposals must
	 * be consistent with this tree to be appended to the end of {@code queued}.
	 */
	private RefTree refTree;

	/**
	 * If true {@link #refTree} must be duplicated before adding another
	 * proposal. This is set {@code true} when a proposal begins execution and
	 * set false once the tree objects are persisted in the leader's local
	 * repository. Copying the queue supports a fast-path where proposals arrive
	 * less frequently than rounds begin and the round can reuse the same
	 * RefTree instance.
	 */
	volatile boolean copyOnQueue;

	/** Top of the leader's log. */
	private LogId head;

	/** Leader knows this (and all prior) states are committed. */
	private LogId committed;

	/** A {@link Round} is in progress with the peers. */
	private boolean running;
	private Round runningRound;

	/**
	 * Construct a leader for a Ketch instance.
	 *
	 * @param system
	 *            Ketch system configuration the leader must adhere to.
	 */
	protected KetchLeader(KetchSystem system) {
		this.system = system;
		this.lock = new ReentrantLock(true /* fair */);
		this.queued = new ArrayList<>(4);
	}

	/** @return system configuration. */
	KetchSystem getSystem() {
		return system;
	}

	/**
	 * Configure the replicas used by this Ketch instance.
	 * <p>
	 * Replicas should be configured once at creation before any proposals are
	 * executed. Once elections happen, <b>reconfiguration is a complicated
	 * concept that is not currently supported</b>.
	 *
	 * @param replicas
	 *            members participating with the same repository.
	 */
	public void setReplicas(Collection<KetchReplica> replicas) {
		List<KetchReplica> v = new ArrayList<>(5);
		List<KetchReplica> f = new ArrayList<>(5);
		for (KetchReplica r : replicas) {
			switch (r.getType()) {
			case VOTER:
				v.add(r);
				break;

			case FOLLOWER:
				f.add(r);
				break;

			case NONE:
				continue;
			}
		}

		Collection<Integer> validVoters = validVoterCounts();
		if (!validVoters.contains(Integer.valueOf(v.size()))) {
			throw new IllegalArgumentException(MessageFormat.format(
					KetchText.get().unsupportedVoterCount,
					Integer.valueOf(v.size()),
					validVoters));
		}

		LocalReplica me = findLeader(v);
		if (me == null) {
			throw new IllegalArgumentException(
					KetchText.get().leaderReplicaRequired);
		}

		lock.lock();
		try {
			voters = v.toArray(new KetchReplica[v.size()]);
			followers = f.toArray(new KetchReplica[f.size()]);
			self = me;
		} finally {
			lock.unlock();
		}
	}

	private static Collection<Integer> validVoterCounts() {
		@SuppressWarnings("boxing")
		Integer[] valid = {
				// An odd number of voting replicas is required.
				1, 3, 5, 7, 9 };
		return Arrays.asList(valid);
	}

	private static LocalReplica findLeader(Collection<KetchReplica> voters) {
		for (KetchReplica r : voters) {
			if (r instanceof LocalReplica) {
				return (LocalReplica) r;
			}
		}
		return null;
	}

	/**
	 * Get an instance of the repository for use by a leader thread.
	 * <p>
	 * The caller will close the repository.
	 *
	 * @return open the repository for use by the leader thread.
	 * @throws IOException
	 *             cannot reopen the repository for the leader.
	 */
	protected abstract Repository openRepository() throws IOException;

	/**
	 * Queue a reference update proposal for later consensus.
	 * <p>
	 * This method does not wait for consensus to be reached. The proposal is
	 * preflighted to look for risks of conflicts, and then submitted into the
	 * queue for a future execution round.
	 * <p>
	 * Callers must use {@link Proposal#await()} to see if the proposal is done.
	 *
	 * @param proposal
	 *            reference updates to queue for consideration. Once execution
	 *            is complete the individual reference result fields will be
	 *            populated with the outcome.
	 * @throws InterruptedException
	 *             current thread was interrupted. The proposal may have been
	 *             aborted if it was not yet queued for execution.
	 * @throws IOException
	 *             unrecoverable error preventing proposals from being attempted
	 *             by this leader.
	 */
	public void executeAsync(Proposal proposal)
			throws InterruptedException, IOException {
		try {
			lock.lockInterruptibly();
		} catch (InterruptedException e) {
			proposal.abort();
			throw e;
		}
		try {
			if (refTree == null) {
				initialize();
				for (Proposal p : queued) {
					refTree.apply(p.getCommands());
				}
			} else if (copyOnQueue) {
				refTree = refTree.copy();
				copyOnQueue = false;
			}

			if (!refTree.apply(proposal.getCommands())) {
				// A conflict exists so abort the proposal.
				proposal.abort();
				return;
			}

			queued.add(proposal);
			proposal.notifyState(QUEUED);

			if (!running) {
				scheduleLeader();
			}
		} finally {
			lock.unlock();
		}
	}

	private void initialize() throws IOException {
		try (Repository git = openRepository(); RevWalk rw = new RevWalk(git)) {
			self.initialize(git);

			ObjectId accepted = self.getTxnAccepted();
			if (!ObjectId.zeroId().equals(accepted)) {
				RevCommit c = rw.parseCommit(accepted);
				head = new LogId(accepted, 0);
				refTree = RefTree.read(rw.getObjectReader(), c.getTree());
			} else {
				head = new LogId(ObjectId.zeroId(), 0);
				refTree = RefTree.newEmptyTree();
			}
		}
	}

	private void scheduleLeader() {
		running = true;
		system.getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				runLeader();
			}
		});
	}

	private void runLeader() {
		Round r;
		lock.lock();
		try {
			switch (state) {
			case CANDIDATE:
				r = new ElectionRound(this, head);
				break;

			case LEADER:
				r = newProposalRound();
				break;

			case DEPOSED:
			case SHUTDOWN:
			default:
				log.warn("Leader cannot run {}", state); //$NON-NLS-1$
				// TODO(sop): Redirect proposals.
				return;
			}
		} finally {
			lock.unlock();
		}

		try {
			r.start();
		} catch (IOException e) {
			// TODO(sop) Depose leader if it cannot use its repository.
			log.error(KetchText.get().leaderFailedStore, e);
			lock.lock();
			try {
				nextRound();
			} finally {
				lock.unlock();
			}
		}
	}

	private ProposalRound newProposalRound() {
		List<Proposal> todo = new ArrayList<>(queued);
		queued.clear();

		ProposalRound r = new ProposalRound(this, head, todo);
		if (todo.size() == 1) {
			copyOnQueue = true;
			r.setTree(refTree);
		} else {
			copyOnQueue = false;
		}
		return r;
	}

	/** @return term of this leader's reign. */
	long getTerm() {
		return term;
	}

	/** @return top of the leader's log. */
	LogId getHead() {
		return head;
	}

	/** @return state leader knows it has committed across the cluster. */
	LogId getCommitted() {
		return committed;
	}

	boolean isIdle() {
		return !running;
	}

	void acceptAsync(Round round) {
		lock.lock();
		try {
			// Top of the log is this round. Once transport begins it is
			// reasonable to assume at least one replica will eventually get
			// this, and there is reasonable probability it commits.
			head = round.acceptedNew;
			runningRound = round;

			for (KetchReplica r : voters) {
				r.acceptAsync(round);
			}
			for (KetchReplica r : followers) {
				r.acceptAsync(round);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Asynchronous signal from a replica after completion.
	 * <p>
	 * Must be called while {@link #lock} is held by the replica.
	 *
	 * @param replica
	 *            replica posting a completion event.
	 */
	void onReplicaUpdate(KetchReplica replica) {
		if (log.isDebugEnabled()) {
			log.debug("Replica {} finished:\n{}", //$NON-NLS-1$
					replica.describeForLog(), snapshot());
		}

		if (replica.getType() == FOLLOWER) {
			// Followers cannot vote, so votes haven't changed.
			return;
		} else if (runningRound == null) {
			// No round running, no need to build a tally of votes.
			return;
		}

		assert head.equals(runningRound.acceptedNew);
		int matching = 0;
		for (KetchReplica r : voters) {
			if (r.hasAccepted(head)) {
				matching++;
			}
		}

		int quorum = voters.length / 2 + 1;
		boolean success = matching >= quorum;
		if (!success) {
			return;
		}

		switch (state) {
		case CANDIDATE:
			term = ((ElectionRound) runningRound).getTerm();
			state = LEADER;
			if (log.isDebugEnabled()) {
				log.debug("Won election, running term " + term); //$NON-NLS-1$
			}

			//$FALL-THROUGH$
		case LEADER:
			committed = head;
			if (log.isDebugEnabled()) {
				log.debug("Committed {}/{} in term {}", //$NON-NLS-1$
						Long.valueOf(committed.index),
						committed.abbreviate(8).name(),
						Long.valueOf(term));
			}
			runningRound.success();
			nextRound();
			commitAsync(replica);
			if (log.isDebugEnabled()) {
				log.debug("Leader state:\n{}", snapshot()); //$NON-NLS-1$
			}
			break;
		}
	}

	private void commitAsync(KetchReplica caller) {
		LogId c = committed;
		boolean idle = isIdle();
		for (KetchReplica r : voters) {
			if (r != caller && r.hasAccepted(c)) {
				r.commitAsync(c, idle);
			}
		}
		for (KetchReplica r : followers) {
			if (r != caller && r.hasAccepted(c)) {
				r.commitAsync(c, idle);
			}
		}
	}

	/** Schedule the next round; invoked while {@link #lock} is held. */
	void nextRound() {
		runningRound = null;

		if (queued.isEmpty()) {
			running = false;
		} else {
			// Caller holds lock. Reschedule leader on a new thread so
			// the call stack can unwind and lock is not held unexpectedly
			// during prepare for the next round.
			scheduleLeader();
		}
	}

	/** @return snapshot this leader. */
	public Snapshot snapshot() {
		lock.lock();
		try {
			Snapshot s = new Snapshot();
			s.state = state;
			s.term = term;
			s.head = head;
			s.committed = committed;
			s.running = running;
			for (KetchReplica r : voters) {
				s.replicas.add(r.snapshot());
			}
			for (KetchReplica r : followers) {
				s.replicas.add(r.snapshot());
			}
			return s;
		} finally {
			lock.unlock();
		}
	}

	/** Gracefully shutdown this leader and cancel outstanding operations. */
	public void shutdown() {
		lock.lock();
		try {
			state = SHUTDOWN;
			for (KetchReplica r : voters) {
				r.shutdown();
			}
			for (KetchReplica r : followers) {
				r.shutdown();
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return snapshot().toString();
	}
}
