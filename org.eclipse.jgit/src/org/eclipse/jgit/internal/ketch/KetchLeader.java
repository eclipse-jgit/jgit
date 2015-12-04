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
import static org.eclipse.jgit.internal.ketch.KetchReplica.Participation.FOLLOWER_ONLY;
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

/**
 * A leader managing consensus across remote followers.
 * <p>
 * A leader instance starts up in {@link State#CANDIDATE} and tries to begin a
 * new term by sending an {@link ElectionRound} to all replicas. Its term starts
 * if a majority of replicas have accepted this leader instance for the term.
 * <p>
 * Once elected by a majority the instance enters {@link State#LEADER} and runs
 * proposals offered to {@link #queueProposal(Proposal)}. This continues until
 * the leader is timed out for inactivity, or is deposed by a competing leader
 * gaining its own majority.
 * <p>
 * Once timed out or deposed this {@code KetchLeader} instance should be
 * discarded, and a new instance takes over.
 * <p>
 * Each leader instance coordinates a group of {@link KetchReplica}s. Replica
 * instances are owned by the leader instance and must be discarded when the
 * leader is discarded.
 * <p>
 * In Ketch all push requests are issued through the leader. The steps are as
 * follows (see {@link KetchPreReceive} for an example):
 * <ul>
 * <li>Create a {@link Proposal} with the
 * {@link org.eclipse.jgit.transport.ReceiveCommand}s that represent the push.
 * <li>Invoke {@link #queueProposal(Proposal)} on the leader instance.
 * <li>Wait for consensus with {@link Proposal#await()}.
 * <li>To examine the status of the push, check {@link Proposal#getCommands()},
 * looking at
 * {@link org.eclipse.jgit.internal.storage.reftree.Command#getResult()}.
 * </ul>
 * <p>
 * The leader gains consensus by first pushing the needed objects and a
 * {@link RefTree} representing the desired target repository state to the
 * {@code refs/txn/accepted} branch on each of the replicas. Once a majority has
 * succeeded, the leader commits the state by either pushing the
 * {@code refs/txn/accepted} value to {@code refs/txn/committed} (for
 * Ketch-aware replicas) or by pushing updates to {@code refs/heads/master},
 * etc. for stock Git replicas.
 * <p>
 * Internally, the actual transport to replicas is performed on background
 * threads via the {@link KetchSystem}'s executor service. For performance, the
 * {@link KetchLeader}, {@link KetchReplica} and {@link Proposal} objects share
 * some state, and may invoke each other's methods on different threads. This
 * access is protected by the leader's {@link #lock} object. Care must be taken
 * to prevent concurrent access by correctly obtaining the leader's lock.
 */
public abstract class KetchLeader {
	private static final Logger log = LoggerFactory.getLogger(KetchLeader.class);

	/** Current state of the leader instance. */
	public static enum State {
		/** Newly created instance trying to elect itself leader. */
		CANDIDATE,

		/** Leader instance elected by a majority. */
		LEADER,

		/** Instance has been deposed by another with a more recent term. */
		DEPOSED,

		/** Leader has been gracefully shutdown, e.g. due to inactivity. */
		SHUTDOWN;
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
	 * State of the repository's RefTree after applying all entries in
	 * {@link #queued}. New proposals must be consistent with this tree to be
	 * appended to the end of {@link #queued}.
	 * <p>
	 * Must be deep-copied with {@link RefTree#copy()} if
	 * {@link #roundHoldsReferenceToRefTree} is {@code true}.
	 */
	private RefTree refTree;

	/**
	 * If {@code true} {@link #refTree} must be duplicated before queuing the
	 * next proposal. The {@link #refTree} was passed into the constructor of a
	 * {@link ProposalRound}, and that external reference to the {@link RefTree}
	 * object is held by the proposal until it materializes the tree object in
	 * the object store. This field is set {@code true} when the proposal begins
	 * execution and set {@code false} once tree objects are persisted in the
	 * local repository's object store or {@link #refTree} is replaced with a
	 * copy to isolate it from any running rounds.
	 * <p>
	 * If proposals arrive less frequently than the {@code RefTree} is written
	 * out to the repository the {@link #roundHoldsReferenceToRefTree} behavior
	 * avoids duplicating {@link #refTree}, reducing both time and memory used.
	 * However if proposals arrive more frequently {@link #refTree} must be
	 * duplicated to prevent newly queued proposals from corrupting the
	 * {@link #runningRound}.
	 */
	volatile boolean roundHoldsReferenceToRefTree;

	/** End of the leader's log. */
	private LogIndex headIndex;

	/** Leader knows this (and all prior) states are committed. */
	private LogIndex committedIndex;

	/**
	 * Is the leader idle with no work pending? If {@code true} there is no work
	 * for the leader (normal state). This field is {@code false} when the
	 * leader thread is scheduled for execution, or while {@link #runningRound}
	 * defines a round in progress.
	 */
	private boolean idle;

	/** Current round the leader is preparing and waiting for a vote on. */
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
		this.idle = true;
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
			switch (r.getParticipation()) {
			case FULL:
				v.add(r);
				break;

			case FOLLOWER_ONLY:
				f.add(r);
				break;
			}
		}

		Collection<Integer> validVoters = validVoterCounts();
		if (!validVoters.contains(Integer.valueOf(v.size()))) {
			throw new IllegalArgumentException(MessageFormat.format(
					KetchText.get().unsupportedVoterCount,
					Integer.valueOf(v.size()),
					validVoters));
		}

		LocalReplica me = findLocal(v);
		if (me == null) {
			throw new IllegalArgumentException(
					KetchText.get().localReplicaRequired);
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

	private static LocalReplica findLocal(Collection<KetchReplica> voters) {
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
	 * @return opened repository for use by the leader thread.
	 * @throws IOException
	 *             cannot reopen the repository for the leader.
	 */
	protected abstract Repository openRepository() throws IOException;

	/**
	 * Queue a reference update proposal for consensus.
	 * <p>
	 * This method does not wait for consensus to be reached. The proposal is
	 * checked to look for risks of conflicts, and then submitted into the queue
	 * for distribution as soon as possible.
	 * <p>
	 * Callers must use {@link Proposal#await()} to see if the proposal is done.
	 *
	 * @param proposal
	 *            the proposed reference updates to queue for consideration.
	 *            Once execution is complete the individual reference result
	 *            fields will be populated with the outcome.
	 * @throws InterruptedException
	 *             current thread was interrupted. The proposal may have been
	 *             aborted if it was not yet queued for execution.
	 * @throws IOException
	 *             unrecoverable error preventing proposals from being attempted
	 *             by this leader.
	 */
	public void queueProposal(Proposal proposal)
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
			} else if (roundHoldsReferenceToRefTree) {
				refTree = refTree.copy();
				roundHoldsReferenceToRefTree = false;
			}

			if (!refTree.apply(proposal.getCommands())) {
				// A conflict exists so abort the proposal.
				proposal.abort();
				return;
			}

			queued.add(proposal);
			proposal.notifyState(QUEUED);

			if (idle) {
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
				headIndex = LogIndex.unknown(accepted);
				refTree = RefTree.read(rw.getObjectReader(), c.getTree());
			} else {
				headIndex = LogIndex.unknown(ObjectId.zeroId());
				refTree = RefTree.newEmptyTree();
			}
		}
	}

	private void scheduleLeader() {
		idle = false;
		system.getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				runLeader();
			}
		});
	}

	private void runLeader() {
		Round round;
		lock.lock();
		try {
			switch (state) {
			case CANDIDATE:
				round = new ElectionRound(this, headIndex);
				break;

			case LEADER:
				round = newProposalRound();
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
			round.start();
		} catch (IOException e) {
			// TODO(sop) Depose leader if it cannot use its repository.
			log.error(KetchText.get().leaderFailedToStore, e);
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
		roundHoldsReferenceToRefTree = true;
		return new ProposalRound(this, headIndex, todo, refTree);
	}

	/** @return term of this leader's reign. */
	long getTerm() {
		return term;
	}

	/** @return end of the leader's log. */
	LogIndex getHead() {
		return headIndex;
	}

	/**
	 * @return state leader knows it has committed across a quorum of replicas.
	 */
	LogIndex getCommitted() {
		return committedIndex;
	}

	boolean isIdle() {
		return idle;
	}

	void runAsync(Round round) {
		lock.lock();
		try {
			// End of the log is this round. Once transport begins it is
			// reasonable to assume at least one replica will eventually get
			// this, and there is reasonable probability it commits.
			headIndex = round.acceptedNewIndex;
			runningRound = round;

			for (KetchReplica replica : voters) {
				replica.pushTxnAcceptedAsync(round);
			}
			for (KetchReplica replica : followers) {
				replica.pushTxnAcceptedAsync(round);
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

		if (replica.getParticipation() == FOLLOWER_ONLY) {
			// Followers cannot vote, so votes haven't changed.
			return;
		} else if (runningRound == null) {
			// No round running, no need to tally votes.
			return;
		}

		assert headIndex.equals(runningRound.acceptedNewIndex);
		int matching = 0;
		for (KetchReplica r : voters) {
			if (r.hasAccepted(headIndex)) {
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
			committedIndex = headIndex;
			if (log.isDebugEnabled()) {
				log.debug("Committed {} in term {}", //$NON-NLS-1$
						committedIndex.describeForLog(),
						Long.valueOf(term));
			}
			nextRound();
			commitAsync(replica);
			notifySuccess(runningRound);
			if (log.isDebugEnabled()) {
				log.debug("Leader state:\n{}", snapshot()); //$NON-NLS-1$
			}
			break;

		default:
			log.debug("Leader ignoring replica while in {}", state); //$NON-NLS-1$
			break;
		}
	}

	private void notifySuccess(Round round) {
		// Drop the leader lock while notifying Proposal listeners.
		lock.unlock();
		try {
			round.success();
		} finally {
			lock.lock();
		}
	}

	private void commitAsync(KetchReplica caller) {
		for (KetchReplica r : voters) {
			if (r == caller) {
				continue;
			}
			if (r.shouldPushUnbatchedCommit(committedIndex, isIdle())) {
				r.pushCommitAsync(committedIndex);
			}
		}
		for (KetchReplica r : followers) {
			if (r == caller) {
				continue;
			}
			if (r.shouldPushUnbatchedCommit(committedIndex, isIdle())) {
				r.pushCommitAsync(committedIndex);
			}
		}
	}

	/** Schedule the next round; invoked while {@link #lock} is held. */
	void nextRound() {
		runningRound = null;

		if (queued.isEmpty()) {
			idle = true;
		} else {
			// Caller holds lock. Reschedule leader on a new thread so
			// the call stack can unwind and lock is not held unexpectedly
			// during prepare for the next round.
			scheduleLeader();
		}
	}

	/** @return snapshot this leader. */
	public LeaderSnapshot snapshot() {
		lock.lock();
		try {
			LeaderSnapshot s = new LeaderSnapshot();
			s.state = state;
			s.term = term;
			s.headIndex = headIndex;
			s.committedIndex = committedIndex;
			s.idle = isIdle();
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
			if (state != SHUTDOWN) {
				state = SHUTDOWN;
				for (KetchReplica r : voters) {
					r.shutdown();
				}
				for (KetchReplica r : followers) {
					r.shutdown();
				}
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
