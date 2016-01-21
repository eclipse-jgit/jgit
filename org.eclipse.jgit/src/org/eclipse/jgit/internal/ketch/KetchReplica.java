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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitSpeed.BATCHED;
import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitSpeed.FAST;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.CURRENT;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.LAGGING;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.OFFLINE;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.UNKNOWN;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.reftree.RefTree;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Ketch replica, either {@link LocalReplica} or {@link RemoteGitReplica}.
 * <p>
 * Replicas can be either a stock Git replica, or a Ketch-aware replica.
 * <p>
 * A stock Git replica has no special knowledge of Ketch and simply stores
 * objects and references. Ketch communicates with the stock Git replica using
 * the Git push wire protocol. The {@link KetchLeader} commits an agreed upon
 * state by pushing all references to the Git replica, for example
 * {@code "refs/heads/master"} is pushed during commit. Stock Git replicas use
 * {@link CommitMethod#ALL_REFS} to record the final state.
 * <p>
 * Ketch-aware replicas understand the {@code RefTree} sent during the proposal
 * and during commit are able to update their own reference space to match the
 * state represented by the {@code RefTree}. Ketch-aware replicas typically use
 * a {@link org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase} and
 * {@link CommitMethod#TXN_COMMITTED} to record the final state.
 * <p>
 * KetchReplica instances are tightly coupled with a single {@link KetchLeader}.
 * Some state may be accessed by the leader thread and uses the leader's own
 * {@link KetchLeader#lock} to protect shared data.
 */
public abstract class KetchReplica {
	static final Logger log = LoggerFactory.getLogger(KetchReplica.class);
	private static final byte[] PEEL = { ' ', '^' };

	/** Participation of a replica in establishing consensus. */
	public enum Participation {
		/** Replica can vote. */
		FULL,

		/** Replica does not vote, but tracks leader. */
		FOLLOWER_ONLY;
	}

	/** How this replica wants to receive Ketch commit operations. */
	public enum CommitMethod {
		/** All references are pushed to the peer as standard Git. */
		ALL_REFS,

		/** Only {@code refs/txn/committed} is written/updated. */
		TXN_COMMITTED;
	}

	/** Delay before committing to a replica. */
	public enum CommitSpeed {
		/**
		 * Send the commit immediately, even if it could be batched with the
		 * next proposal.
		 */
		FAST,

		/**
		 * If the next proposal is available, batch the commit with it,
		 * otherwise just send the commit. This generates less network use, but
		 * may provide slower consistency on the replica.
		 */
		BATCHED;
	}

	/** Current state of a replica. */
	public enum State {
		/** Leader has not yet contacted the replica. */
		UNKNOWN,

		/** Replica is behind the consensus. */
		LAGGING,

		/** Replica matches the consensus. */
		CURRENT,

		/** Replica has a different (or unknown) history. */
		DIVERGENT,

		/** Replica's history contains the leader's history. */
		AHEAD,

		/** Replica can not be contacted. */
		OFFLINE;
	}

	private final KetchLeader leader;
	private final String replicaName;
	private final Participation participation;
	private final CommitMethod commitMethod;
	private final CommitSpeed commitSpeed;
	private final long minRetryMillis;
	private final long maxRetryMillis;
	private final Map<ObjectId, List<ReceiveCommand>> staged;
	private final Map<String, ReceiveCommand> running;
	private final Map<String, ReceiveCommand> waiting;
	private final List<ReplicaPushRequest> queued;

	/**
	 * Value known for {@code "refs/txn/accepted"}.
	 * <p>
	 * Raft literature refers to this as {@code matchIndex}.
	 */
	private ObjectId txnAccepted;

	/**
	 * Value known for {@code "refs/txn/committed"}.
	 * <p>
	 * Raft literature refers to this as {@code commitIndex}. In traditional
	 * Raft this is a state variable inside the follower implementation, but
	 * Ketch keeps it in the leader.
	 */
	private ObjectId txnCommitted;

	/** What is happening with this replica. */
	private State state = UNKNOWN;
	private String error;

	/** Scheduled retry due to communication failure. */
	private Future<?> retryFuture;
	private long lastRetryMillis;
	private long retryAtMillis;

	/**
	 * Configure a replica representation.
	 *
	 * @param leader
	 *            instance this replica follows.
	 * @param name
	 *            unique-ish name identifying this replica for debugging.
	 * @param cfg
	 *            how Ketch should treat the replica.
	 */
	protected KetchReplica(KetchLeader leader, String name, ReplicaConfig cfg) {
		this.leader = leader;
		this.replicaName = name;
		this.participation = cfg.getParticipation();
		this.commitMethod = cfg.getCommitMethod();
		this.commitSpeed = cfg.getCommitSpeed();
		this.minRetryMillis = cfg.getMinRetry(MILLISECONDS);
		this.maxRetryMillis = cfg.getMaxRetry(MILLISECONDS);
		this.staged = new HashMap<>();
		this.running = new HashMap<>();
		this.waiting = new HashMap<>();
		this.queued = new ArrayList<>(4);
	}

	/** @return system configuration. */
	public KetchSystem getSystem() {
		return getLeader().getSystem();
	}

	/** @return leader instance this replica follows. */
	public KetchLeader getLeader() {
		return leader;
	}

	/** @return unique-ish name for debugging. */
	public String getName() {
		return replicaName;
	}

	/** @return description of this replica for error/debug logging purposes. */
	protected String describeForLog() {
		return getName();
	}

	/** @return how the replica participates in this Ketch system. */
	public Participation getParticipation() {
		return participation;
	}

	/** @return how Ketch will commit to the repository. */
	public CommitMethod getCommitMethod() {
		return commitMethod;
	}

	/** @return when Ketch will commit to the repository. */
	public CommitSpeed getCommitSpeed() {
		return commitSpeed;
	}

	/**
	 * Called by leader to perform graceful shutdown.
	 * <p>
	 * Default implementation cancels any scheduled retry. Subclasses may add
	 * additional logic before or after calling {@code super.shutdown()}.
	 * <p>
	 * Called with {@link KetchLeader#lock} held by caller.
	 */
	protected void shutdown() {
		Future<?> f = retryFuture;
		if (f != null) {
			retryFuture = null;
			f.cancel(true);
		}
	}

	ReplicaSnapshot snapshot() {
		ReplicaSnapshot s = new ReplicaSnapshot(this);
		s.accepted = txnAccepted;
		s.committed = txnCommitted;
		s.state = state;
		s.error = error;
		s.retryAtMillis = waitingForRetry() ? retryAtMillis : 0;
		return s;
	}

	/**
	 * Update the leader's view of the replica after a poll.
	 * <p>
	 * Called with {@link KetchLeader#lock} held by caller.
	 *
	 * @param refs
	 *            map of refs from the replica.
	 */
	void initialize(Map<String, Ref> refs) {
		if (txnAccepted == null) {
			txnAccepted = getId(refs.get(getSystem().getTxnAccepted()));
		}
		if (txnCommitted == null) {
			txnCommitted = getId(refs.get(getSystem().getTxnCommitted()));
		}
	}

	ObjectId getTxnAccepted() {
		return txnAccepted;
	}

	boolean hasAccepted(LogIndex id) {
		return equals(txnAccepted, id);
	}

	private static boolean equals(@Nullable ObjectId a, LogIndex b) {
		return a != null && b != null && AnyObjectId.equals(a, b);
	}

	/**
	 * Schedule a proposal round with the replica.
	 * <p>
	 * Called with {@link KetchLeader#lock} held by caller.
	 *
	 * @param round
	 *            current round being run by the leader.
	 */
	void pushTxnAcceptedAsync(Round round) {
		List<ReceiveCommand> cmds = new ArrayList<>();
		if (commitSpeed == BATCHED) {
			LogIndex committedIndex = leader.getCommitted();
			if (equals(txnAccepted, committedIndex)
					&& !equals(txnCommitted, committedIndex)) {
				prepareTxnCommitted(cmds, committedIndex);
			}
		}

		// TODO(sop) Lagging replicas should build accept on the fly.
		if (round.stageCommands != null) {
			for (ReceiveCommand cmd : round.stageCommands) {
				// TODO(sop): Do not send certain object graphs to replica.
				cmds.add(copy(cmd));
			}
		}
		cmds.add(new ReceiveCommand(
				round.acceptedOldIndex, round.acceptedNewIndex,
				getSystem().getTxnAccepted()));
		pushAsync(new ReplicaPushRequest(this, cmds));
	}

	private static ReceiveCommand copy(ReceiveCommand c) {
		return new ReceiveCommand(c.getOldId(), c.getNewId(), c.getRefName());
	}

	boolean shouldPushUnbatchedCommit(LogIndex committed, boolean leaderIdle) {
		return (leaderIdle || commitSpeed == FAST) && hasAccepted(committed);
	}

	void pushCommitAsync(LogIndex committed) {
		List<ReceiveCommand> cmds = new ArrayList<>();
		prepareTxnCommitted(cmds, committed);
		pushAsync(new ReplicaPushRequest(this, cmds));
	}

	private void prepareTxnCommitted(List<ReceiveCommand> cmds,
			ObjectId committed) {
		removeStaged(cmds, committed);
		cmds.add(new ReceiveCommand(
				txnCommitted, committed,
				getSystem().getTxnCommitted()));
	}

	private void removeStaged(List<ReceiveCommand> cmds, ObjectId committed) {
		List<ReceiveCommand> a = staged.remove(committed);
		if (a != null) {
			delete(cmds, a);
		}
		if (staged.isEmpty() || !(committed instanceof LogIndex)) {
			return;
		}

		LogIndex committedIndex = (LogIndex) committed;
		Iterator<Map.Entry<ObjectId, List<ReceiveCommand>>> itr = staged
				.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<ObjectId, List<ReceiveCommand>> e = itr.next();
			if (e.getKey() instanceof LogIndex) {
				LogIndex stagedIndex = (LogIndex) e.getKey();
				if (stagedIndex.isBefore(committedIndex)) {
					delete(cmds, e.getValue());
					itr.remove();
				}
			}
		}
	}

	private static void delete(List<ReceiveCommand> cmds,
			List<ReceiveCommand> createCmds) {
		for (ReceiveCommand cmd : createCmds) {
			ObjectId id = cmd.getNewId();
			String name = cmd.getRefName();
			cmds.add(new ReceiveCommand(id, ObjectId.zeroId(), name));
		}
	}

	/**
	 * Determine the next push for this replica (if any) and start it.
	 * <p>
	 * If the replica has successfully accepted the committed state of the
	 * leader, this method will push all references to the replica using the
	 * configured {@link CommitMethod}.
	 * <p>
	 * If the replica is {@link State#LAGGING} this method will begin catch up
	 * by sending a more recent {@code refs/txn/accepted}.
	 * <p>
	 * Must be invoked with {@link KetchLeader#lock} held by caller.
	 */
	private void runNextPushRequest() {
		LogIndex committed = leader.getCommitted();
		if (!equals(txnCommitted, committed)
				&& shouldPushUnbatchedCommit(committed, leader.isIdle())) {
			pushCommitAsync(committed);
		}

		if (queued.isEmpty() || !running.isEmpty() || waitingForRetry()) {
			return;
		}

		// Collapse all queued requests into a single request.
		Map<String, ReceiveCommand> cmdMap = new HashMap<>();
		for (ReplicaPushRequest req : queued) {
			for (ReceiveCommand cmd : req.getCommands()) {
				String name = cmd.getRefName();
				ReceiveCommand old = cmdMap.remove(name);
				if (old != null) {
					cmd = new ReceiveCommand(
							old.getOldId(), cmd.getNewId(),
							name);
				}
				cmdMap.put(name, cmd);
			}
		}
		queued.clear();
		waiting.clear();

		List<ReceiveCommand> next = new ArrayList<>(cmdMap.values());
		for (ReceiveCommand cmd : next) {
			running.put(cmd.getRefName(), cmd);
		}
		startPush(new ReplicaPushRequest(this, next));
	}

	private void pushAsync(ReplicaPushRequest req) {
		if (defer(req)) {
			// TODO(sop) Collapse during long retry outage.
			for (ReceiveCommand cmd : req.getCommands()) {
				waiting.put(cmd.getRefName(), cmd);
			}
			queued.add(req);
		} else {
			for (ReceiveCommand cmd : req.getCommands()) {
				running.put(cmd.getRefName(), cmd);
			}
			startPush(req);
		}
	}

	private boolean defer(ReplicaPushRequest req) {
		if (waitingForRetry()) {
			// Prior communication failure; everything is deferred.
			return true;
		}

		for (ReceiveCommand nextCmd : req.getCommands()) {
			ReceiveCommand priorCmd = waiting.get(nextCmd.getRefName());
			if (priorCmd == null) {
				priorCmd = running.get(nextCmd.getRefName());
			}
			if (priorCmd != null) {
				// Another request pending on same ref; that must go first.
				// Verify priorCmd.newId == nextCmd.oldId?
				return true;
			}
		}
		return false;
	}

	private boolean waitingForRetry() {
		Future<?> f = retryFuture;
		return f != null && !f.isDone();
	}

	private void retryLater(ReplicaPushRequest req) {
		Collection<ReceiveCommand> cmds = req.getCommands();
		for (ReceiveCommand cmd : cmds) {
			cmd.setResult(NOT_ATTEMPTED, null);
			if (!waiting.containsKey(cmd.getRefName())) {
				waiting.put(cmd.getRefName(), cmd);
			}
		}
		queued.add(0, new ReplicaPushRequest(this, cmds));

		if (!waitingForRetry()) {
			long delay = KetchSystem.delay(
					lastRetryMillis,
					minRetryMillis, maxRetryMillis);
			if (log.isDebugEnabled()) {
				log.debug("Retrying {} after {} ms", //$NON-NLS-1$
						describeForLog(), Long.valueOf(delay));
			}
			lastRetryMillis = delay;
			retryAtMillis = SystemReader.getInstance().getCurrentTime() + delay;
			retryFuture = getSystem().getExecutor()
					.schedule(new WeakRetryPush(this), delay, MILLISECONDS);
		}
	}

	/** Weakly holds a retrying replica, allowing it to garbage collect. */
	static class WeakRetryPush extends WeakReference<KetchReplica>
			implements Callable<Void> {
		WeakRetryPush(KetchReplica r) {
			super(r);
		}

		@Override
		public Void call() throws Exception {
			KetchReplica r = get();
			if (r != null) {
				r.doRetryPush();
			}
			return null;
		}
	}

	private void doRetryPush() {
		leader.lock.lock();
		try {
			retryFuture = null;
			runNextPushRequest();
		} finally {
			leader.lock.unlock();
		}
	}

	/**
	 * Begin executing a single push.
	 * <p>
	 * This method must move processing onto another thread.
	 * Called with {@link KetchLeader#lock} held by caller.
	 *
	 * @param req
	 *            the request to send to the replica.
	 */
	protected abstract void startPush(ReplicaPushRequest req);

	/**
	 * Callback from {@link ReplicaPushRequest} upon success or failure.
	 * <p>
	 * Acquires the {@link KetchLeader#lock} and updates the leader's internal
	 * knowledge about this replica to reflect what has been learned during a
	 * push to the replica. In some cases of divergence this method may take
	 * some time to determine how the replica has diverged; to reduce contention
	 * this is evaluated before acquiring the leader lock.
	 *
	 * @param repo
	 *            local repository instance used by the push thread.
	 * @param req
	 *            push request just attempted.
	 */
	void afterPush(@Nullable Repository repo, ReplicaPushRequest req) {
		ReceiveCommand acceptCmd = null;
		ReceiveCommand commitCmd = null;
		List<ReceiveCommand> stages = null;

		for (ReceiveCommand cmd : req.getCommands()) {
			String name = cmd.getRefName();
			if (name.equals(getSystem().getTxnAccepted())) {
				acceptCmd = cmd;
			} else if (name.equals(getSystem().getTxnCommitted())) {
				commitCmd = cmd;
			} else if (cmd.getResult() == OK && cmd.getType() == CREATE
					&& name.startsWith(getSystem().getTxnStage())) {
				if (stages == null) {
					stages = new ArrayList<>();
				}
				stages.add(cmd);
			}
		}

		State newState = null;
		ObjectId acceptId = readId(req, acceptCmd);
		if (repo != null && acceptCmd != null && acceptCmd.getResult() != OK
				&& req.getException() == null) {
			try (LagCheck lag = new LagCheck(this, repo)) {
				newState = lag.check(acceptId, acceptCmd);
				acceptId = lag.getRemoteId();
			}
		}

		leader.lock.lock();
		try {
			for (ReceiveCommand cmd : req.getCommands()) {
				running.remove(cmd.getRefName());
			}

			Throwable err = req.getException();
			if (err != null) {
				state = OFFLINE;
				error = err.toString();
				retryLater(req);
				leader.onReplicaUpdate(this);
				return;
			}

			lastRetryMillis = 0;
			error = null;
			updateView(req, acceptId, commitCmd);

			if (acceptCmd != null && acceptCmd.getResult() == OK) {
				state = hasAccepted(leader.getHead()) ? CURRENT : LAGGING;
				if (stages != null) {
					staged.put(acceptCmd.getNewId(), stages);
				}
			} else if (newState != null) {
				state = newState;
			}

			leader.onReplicaUpdate(this);
			runNextPushRequest();
		} finally {
			leader.lock.unlock();
		}
	}

	private void updateView(ReplicaPushRequest req, @Nullable ObjectId acceptId,
			ReceiveCommand commitCmd) {
		if (acceptId != null) {
			txnAccepted = acceptId;
		}

		ObjectId committed = readId(req, commitCmd);
		if (committed != null) {
			txnCommitted = committed;
		} else if (acceptId != null && txnCommitted == null) {
			// Initialize during first conversation.
			Map<String, Ref> adv = req.getRefs();
			if (adv != null) {
				Ref refs = adv.get(getSystem().getTxnCommitted());
				txnCommitted = getId(refs);
			}
		}
	}

	@Nullable
	private static ObjectId readId(ReplicaPushRequest req,
			@Nullable ReceiveCommand cmd) {
		if (cmd == null) {
			// Ref was not in the command list, do not trust advertisement.
			return null;

		} else if (cmd.getResult() == OK) {
			// Currently at newId.
			return cmd.getNewId();
		}

		Map<String, Ref> refs = req.getRefs();
		return refs != null ? getId(refs.get(cmd.getRefName())) : null;
	}

	/**
	 * Fetch objects from the remote using the calling thread.
	 * <p>
	 * Called without {@link KetchLeader#lock}.
	 *
	 * @param repo
	 *            local repository to fetch objects into.
	 * @param req
	 *            the request to fetch from a replica.
	 * @throws IOException
	 *             communication with the replica was not possible.
	 */
	protected abstract void blockingFetch(Repository repo,
			ReplicaFetchRequest req) throws IOException;

	/**
	 * Build a list of commands to commit {@link CommitMethod#ALL_REFS}.
	 *
	 * @param git
	 *            local leader repository to read committed state from.
	 * @param current
	 *            all known references in the replica's repository. Typically
	 *            this comes from a push advertisement.
	 * @param committed
	 *            state being pushed to {@code refs/txn/committed}.
	 * @return commands to update during commit.
	 * @throws IOException
	 *             cannot read the committed state.
	 */
	protected Collection<ReceiveCommand> prepareCommit(Repository git,
			Map<String, Ref> current, ObjectId committed) throws IOException {
		List<ReceiveCommand> delta = new ArrayList<>();
		Map<String, Ref> remote = new HashMap<>(current);
		try (RevWalk rw = new RevWalk(git);
				TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
			tw.setRecursive(true);
			tw.addTree(rw.parseCommit(committed).getTree());
			while (tw.next()) {
				if (tw.getRawMode(0) != TYPE_GITLINK
						|| tw.isPathSuffix(PEEL, 2)) {
					// Symbolic references cannot be pushed.
					// Caching peeled values is handled remotely.
					continue;
				}

				// TODO(sop) Do not send certain ref names to replica.
				String name = RefTree.refName(tw.getPathString());
				Ref oldRef = remote.remove(name);
				ObjectId oldId = getId(oldRef);
				ObjectId newId = tw.getObjectId(0);
				if (!AnyObjectId.equals(oldId, newId)) {
					delta.add(new ReceiveCommand(oldId, newId, name));
				}
			}
		}

		// Delete any extra references not in the committed state.
		for (Ref ref : remote.values()) {
			if (canDelete(ref)) {
				delta.add(new ReceiveCommand(
					ref.getObjectId(), ObjectId.zeroId(),
					ref.getName()));
			}
		}
		return delta;
	}

	boolean canDelete(Ref ref) {
		String name = ref.getName();
		if (HEAD.equals(name)) {
			return false;
		}
		if (name.startsWith(getSystem().getTxnNamespace())) {
			return false;
		}
		// TODO(sop) Do not delete precious names from replica.
		return true;
	}

	@NonNull
	static ObjectId getId(@Nullable Ref ref) {
		if (ref != null) {
			ObjectId id = ref.getObjectId();
			if (id != null) {
				return id;
			}
		}
		return ObjectId.zeroId();
	}
}
