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
import static org.eclipse.jgit.internal.ketch.KetchConstants.ACCEPTED;
import static org.eclipse.jgit.internal.ketch.KetchConstants.*;
import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitSpeed.BATCHED;
import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitSpeed.FAST;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.AHEAD;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.CURRENT;
import static org.eclipse.jgit.internal.ketch.KetchReplica.State.DIVERGENT;
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
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.reftree.RefTree;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Any Ketch replica, either {@link LocalReplica} or {@link RemoteGitReplica}. */
public abstract class KetchReplica {
	static final Logger log = LoggerFactory.getLogger(KetchReplica.class);
	private static final byte[] PEEL = { '^', '{', '}' };

	/** Type of behavior for this replica. */
	public enum Type {
		/** Replica is not a participant in the Ketch system. */
		NONE,

		/** Replica can vote. */
		VOTER,

		/** Replica does not vote, but tracks leader. */
		FOLLOWER;
	}

	/** How this replica wants to receive Ketch commit operations. */
	public enum CommitMethod {
		/** All references are pushed to the peer as standard Git. */
		ALL_REFS,

		/** Only {@code refs/txn/committed} is written/updated. */
		TXN_COMMITTED;
	}

	/** When does this replica commit? */
	public enum CommitSpeed {
		/** Send commit immediately, may run concurrently with next proposal. */
		FAST,

		/** Batch commit with next proposal. */
		BATCHED;
	}

	/** Current state of this remote. */
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

		/** Connectivity with the replica is not working. */
		OFFLINE;
	}

	private final KetchLeader leader;
	private final String name;
	private final Type type;
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

	/** What is happening with this remote. */
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
	 *            unique-ish name identifying this remote for debugging.
	 * @param cfg
	 *            how Ketch should treat the replica.
	 */
	protected KetchReplica(KetchLeader leader, String name, ReplicaConfig cfg) {
		this.leader = leader;
		this.name = name;
		this.type = cfg.getType();
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
		return name;
	}

	/** @return describe this replica for error/debug logging purposes. */
	protected String describeForLog() {
		return getName();
	}

	/** @return configured Ketch behavior of the repository. */
	public Type getType() {
		return type;
	}

	/** @return how Ketch will commit to the repository. */
	public CommitMethod getCommitMethod() {
		return commitMethod;
	}

	/** @return when Ketch will commit to the repository. */
	public CommitSpeed getCommitSpeed() {
		return commitSpeed;
	}

	/** @return reference namespace storing transaction refs. */
	protected String getTxnNamespace() {
		return getSystem().getTxnNamespace();
	}

	/**
	 * Called by leader to perform graceful shutdown.
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

	Snapshot.Replica snapshot() {
		Snapshot.Replica s = new Snapshot.Replica();
		s.name = name;
		s.type = type;
		s.txnAccepted = txnAccepted;
		s.txnCommitted = txnCommitted;
		s.state = state;
		s.error = error;
		s.retryAtMillis = waitingForRetry() ? retryAtMillis : 0;
		return s;
	}

	ObjectId getTxnAccepted() {
		return txnAccepted;
	}

	boolean hasAccepted(LogId id) {
		return equals(txnAccepted, id);
	}

	private static boolean equals(@Nullable ObjectId a, LogId b) {
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
	void acceptAsync(Round round) {
		List<ReceiveCommand> cmds = new ArrayList<>();
		if (commitSpeed == BATCHED) {
			LogId c = leader.getCommitted();
			if (equals(txnAccepted, c) && !equals(txnCommitted, c)) {
				commit(cmds, c);
			}
		}

		// TODO(sop) Lagging replicas should build accept on the fly.
		if (round.stageCommands != null) {
			for (ReceiveCommand c : round.stageCommands) {
				// TODO(sop): Do not send certain object graphs to remote.
				cmds.add(copy(c));
			}
		}
		cmds.add(new ReceiveCommand(
round.acceptedOld, round.acceptedNew,
				getTxnNamespace() + ACCEPTED));
		pushAsync(new ReplicaPushRequest(this, cmds));
	}

	private static ReceiveCommand copy(ReceiveCommand c) {
		return new ReceiveCommand(c.getOldId(), c.getNewId(), c.getRefName());
	}

	void commitAsync(ObjectId committed, boolean leaderIsIdle) {
		if (leaderIsIdle || commitSpeed == FAST) {
			List<ReceiveCommand> cmds = new ArrayList<>();
			commit(cmds, committed);
			pushAsync(new ReplicaPushRequest(this, cmds));
		}
	}

	private void commit(List<ReceiveCommand> cmds, ObjectId committed) {
		removeStaged(cmds, committed);
		cmds.add(new ReceiveCommand(txnCommitted, committed,
				getTxnNamespace() + COMMITTED));
	}

	private void removeStaged(List<ReceiveCommand> cmds, ObjectId committed) {
		List<ReceiveCommand> a = staged.remove(committed);
		if (a != null) {
			delete(cmds, a);
		}
		if (staged.isEmpty() || !(committed instanceof LogId)) {
			return;
		}

		long n = ((LogId) committed).index;
		Iterator<Map.Entry<ObjectId, List<ReceiveCommand>>> i;

		i = staged.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry<ObjectId, List<ReceiveCommand>> e = i.next();
			if (e.getKey() instanceof LogId) {
				LogId k = (LogId) e.getKey();
				if (k.index <= n) {
					delete(cmds, e.getValue());
					i.remove();
				}
			}
		}
	}

	private static void delete(List<ReceiveCommand> cmds,
			List<ReceiveCommand> createCmds) {
		for (ReceiveCommand c : createCmds) {
			ObjectId id = c.getNewId();
			String n = c.getRefName();
			cmds.add(new ReceiveCommand(id, ObjectId.zeroId(), n));
		}
	}

	private void nextPush() {
		LogId c = leader.getCommitted();
		if (equals(txnAccepted, c) && !equals(txnCommitted, c)) {
			commitAsync(c, leader.isIdle());
		}

		if (queued.isEmpty() || !running.isEmpty() || waitingForRetry()) {
			return;
		}

		// Collapse all queued requests into a single request.
		Map<String, ReceiveCommand> cmdMap = new HashMap<>();
		for (ReplicaPushRequest req : queued) {
			for (ReceiveCommand n : req.getCommands()) {
				String rn = n.getRefName();
				ReceiveCommand o = cmdMap.remove(rn);
				if (o != null) {
					n = new ReceiveCommand(o.getOldId(), n.getNewId(), rn);
				}
				cmdMap.put(rn, n);
			}
		}
		queued.clear();
		waiting.clear();

		List<ReceiveCommand> next = new ArrayList<>(cmdMap.values());
		for (ReceiveCommand r : next) {
			running.put(r.getRefName(), r);
		}
		start(new ReplicaPushRequest(this, next));
	}

	private void pushAsync(ReplicaPushRequest req) {
		if (defer(req)) {
			// TODO(sop) Collapse during long retry outage.
			for (ReceiveCommand c : req.getCommands()) {
				waiting.put(c.getRefName(), c);
			}
			queued.add(req);
		} else {
			for (ReceiveCommand c : req.getCommands()) {
				running.put(c.getRefName(), c);
			}
			start(req);
		}
	}

	private boolean defer(ReplicaPushRequest req) {
		if (waitingForRetry()) {
			// Prior communication failure; everything is deferred.
			return true;
		}

		for (ReceiveCommand n : req.getCommands()) {
			ReceiveCommand o = waiting.get(n.getRefName());
			if (o == null) {
				o = running.get(n.getRefName());
			}
			if (o != null) {
				// Another request pending on same ref; that must go first.
				// Verify o.newId == n.oldId? o finishes before n starts.
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
		for (ReceiveCommand c : cmds) {
			c.setResult(NOT_ATTEMPTED, null);
			if (!waiting.containsKey(c.getRefName())) {
				waiting.put(c.getRefName(), c);
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
			retryAtMillis = System.currentTimeMillis() + delay;
			retryFuture = getSystem().getExecutor()
					.schedule(new WeakRetry(this), delay, MILLISECONDS);
		}
	}

	/** Weakly holds a retrying replica, allowing it to garbage collect. */
	static class WeakRetry extends WeakReference<KetchReplica>
			implements Callable<Void> {
		WeakRetry(KetchReplica r) {
			super(r);
		}

		@Override
		public Void call() throws Exception {
			KetchReplica r = get();
			if (r != null) {
				r.doRetry();
			}
			return null;
		}
	}

	private void doRetry() {
		leader.lock.lock();
		try {
			retryFuture = null;
			nextPush();
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
	protected abstract void start(ReplicaPushRequest req);

	/**
	 * Update the leader's view of the replica after a poll.
	 * <p>
	 * Called with {@link KetchLeader#lock} held by caller.
	 *
	 * @param refs
	 *            map of refs from the replica.
	 */
	protected void initialize(Map<String, Ref> refs) {
		if (txnAccepted == null) {
			txnAccepted = getId(refs.get(getTxnNamespace() + ACCEPTED));
		}
		if (txnCommitted == null) {
			txnCommitted = getId(refs.get(getTxnNamespace() + COMMITTED));
		}
	}

	void afterPush(@Nullable Repository repo, ReplicaPushRequest req) {
		Collection<ReceiveCommand> cmds = req.getCommands();
		ReceiveCommand acceptCmd = null;
		ReceiveCommand commitCmd = null;
		List<ReceiveCommand> stages = null;

		String acceptedRefName = getTxnNamespace() + ACCEPTED;
		String committedRefName = getTxnNamespace() + COMMITTED;
		String stageNamespace = getTxnNamespace() + STAGE;

		for (ReceiveCommand c : cmds) {
			if (acceptedRefName.equals(c.getRefName())) {
				acceptCmd = c;
			} else if (committedRefName.equals(c.getRefName())) {
				commitCmd = c;
			} else if (c.getResult() == OK
					&& c.getType() == CREATE
					&& c.getRefName().startsWith(stageNamespace)) {
				if (stages == null) {
					stages = new ArrayList<>();
				}
				stages.add(c);
			}
		}

		if (repo != null && acceptCmd != null && acceptCmd.getResult() != OK) {
			checkLagging(repo, acceptCmd, req);
		}

		leader.lock.lock();
		try {
			for (ReceiveCommand c : cmds) {
				running.remove(c.getRefName());
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
			updateView(req, acceptCmd, commitCmd);

			if (acceptCmd != null && acceptCmd.getResult() == OK) {
				state = hasAccepted(leader.getHead()) ? CURRENT : LAGGING;
				if (stages != null) {
					staged.put(acceptCmd.getNewId(), stages);
				}
			}

			leader.onReplicaUpdate(this);
			nextPush();
		} finally {
			leader.lock.unlock();
		}
	}

	private void checkLagging(Repository repo,
			ReceiveCommand acceptCmd,
			ReplicaPushRequest req) {
		ObjectId id = readId(req, acceptCmd);
		if (id == null) {
			return;
		} else if (AnyObjectId.equals(id, ObjectId.zeroId())) {
			state = LAGGING;
			return;
		}

		try (RevWalk rw = new RevWalk(repo)) {
			rw.setRetainBody(false);
			RevCommit remote;
			try {
				remote = rw.parseCommit(id);
			} catch (MissingObjectException notLocal) {
				state = DIVERGENT;
				return;
			}

			RevCommit head = rw.parseCommit(acceptCmd.getNewId());
			if (rw.isMergedInto(remote, head)) {
				state = LAGGING;
			} else if (rw.isMergedInto(head, remote)) {
				state = AHEAD;
			} else {
				state = DIVERGENT;
			}
		} catch (IOException err) {
			log.error("Cannot compare " + acceptCmd.getRefName(), err); //$NON-NLS-1$
		}
	}

	private void updateView(ReplicaPushRequest req, ReceiveCommand acceptCmd,
			ReceiveCommand commitCmd) {
		ObjectId accepted = readId(req, acceptCmd);
		if (accepted != null) {
			txnAccepted = accepted;
		}

		ObjectId committed = readId(req, commitCmd);
		if (committed != null) {
			txnCommitted = committed;
		} else if (acceptCmd != null && txnCommitted == null) {
			// Initialize during first conversation.
			Map<String, Ref> adv = req.getRefs();
			if (adv != null) {
				Ref refs = adv.get(getTxnNamespace() + COMMITTED);
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
	protected Collection<ReceiveCommand> commit(Repository git,
			Map<String, Ref> current, ObjectId committed) throws IOException {
		List<ReceiveCommand> delta = new ArrayList<>();
		Map<String, Ref> remote = new HashMap<>(current);
		try (RevWalk rw = new RevWalk(git);
				TreeWalk tw = new TreeWalk(rw.getObjectReader())) {
			tw.setRecursive(true);
			tw.addTree(rw.parseCommit(committed).getTree());
			while (tw.next()) {
				if (tw.getRawMode(0) != TYPE_GITLINK
						|| tw.isPathSuffix(PEEL, 3)) {
					// Symbolic references cannot be pushed.
					// Caching peeled values is handled remotely.
					continue;
				}

				// TODO(sop) Do not send certain ref names to remote.
				String n = RefTree.refName(tw.getPathString());
				Ref oldRef = remote.remove(n);
				ObjectId oldId = getId(oldRef);
				ObjectId newId = tw.getObjectId(0);
				if (!AnyObjectId.equals(oldId, newId)) {
					delta.add(new ReceiveCommand(oldId, newId, n));
				}
			}
		}

		// Delete any extra references not in the committed state.
		for (Ref r : remote.values()) {
			// TODO(sop) Do not delete precious names from remote.
			if (!r.getName().startsWith(getTxnNamespace())
					&& !HEAD.equals(r.getName())) {
				delta.add(new ReceiveCommand(
					r.getObjectId(),
					ObjectId.zeroId(),
					r.getName()));
			}
		}
		return delta;
	}

	@NonNull
	static ObjectId getId(@Nullable Ref r) {
		if (r != null) {
			ObjectId id = r.getObjectId();
			if (id != null) {
				return id;
			}
		}
		return ObjectId.zeroId();
	}
}
