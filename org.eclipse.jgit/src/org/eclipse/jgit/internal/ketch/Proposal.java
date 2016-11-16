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

import static org.eclipse.jgit.internal.ketch.Proposal.State.ABORTED;
import static org.eclipse.jgit.internal.ketch.Proposal.State.EXECUTED;
import static org.eclipse.jgit.internal.ketch.Proposal.State.NEW;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.reftree.Command;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/**
 * A proposal to be applied in a Ketch system.
 * <p>
 * Pushing to a Ketch leader results in the leader making a proposal. The
 * proposal includes the list of reference updates. The leader attempts to send
 * the proposal to a quorum of replicas by pushing the proposal to a "staging"
 * area under the {@code refs/txn/stage/} namespace. If the proposal succeeds
 * then the changes are durable and the leader can commit the proposal.
 * <p>
 * Proposals are executed by {@link KetchLeader#queueProposal(Proposal)}, which
 * runs them asynchronously in the background. Proposals are thread-safe futures
 * allowing callers to {@link #await()} for results or be notified by callback
 * using {@link #addListener(Runnable)}.
 */
public class Proposal {
	/** Current state of the proposal. */
	public enum State {
		/** Proposal has not yet been given to a {@link KetchLeader}. */
		NEW(false),

		/**
		 * Proposal was validated and has entered the queue, but a round
		 * containing this proposal has not started yet.
		 */
		QUEUED(false),

		/** Round containing the proposal has begun and is in progress. */
		RUNNING(false),

		/**
		 * Proposal was executed through a round. Individual results from
		 * {@link Proposal#getCommands()}, {@link Command#getResult()} explain
		 * the success or failure outcome.
		 */
		EXECUTED(true),

		/** Proposal was aborted and did not reach consensus. */
		ABORTED(true);

		private final boolean done;

		private State(boolean done) {
			this.done = done;
		}

		/** @return true if this is a terminal state. */
		public boolean isDone() {
			return done;
		}
	}

	private final List<Command> commands;
	private PersonIdent author;
	private String message;
	private PushCertificate pushCert;

	private List<ProposedTimestamp> timestamps;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final AtomicReference<State> state = new AtomicReference<>(NEW);

	/**
	 * Create a proposal from a list of Ketch commands.
	 *
	 * @param cmds
	 *            prepared list of commands.
	 */
	public Proposal(List<Command> cmds) {
		commands = Collections.unmodifiableList(new ArrayList<>(cmds));
	}

	/**
	 * Create a proposal from a collection of received commands.
	 *
	 * @param rw
	 *            walker to assist in preparing commands.
	 * @param cmds
	 *            list of pending commands.
	 * @throws MissingObjectException
	 *             newId of a command is not found locally.
	 * @throws IOException
	 *             local objects cannot be accessed.
	 */
	public Proposal(RevWalk rw, Collection<ReceiveCommand> cmds)
			throws MissingObjectException, IOException {
		commands = asCommandList(rw, cmds);
	}

	private static List<Command> asCommandList(RevWalk rw,
			Collection<ReceiveCommand> cmds)
					throws MissingObjectException, IOException {
		List<Command> commands = new ArrayList<>(cmds.size());
		for (ReceiveCommand cmd : cmds) {
			commands.add(new Command(rw, cmd));
		}
		return Collections.unmodifiableList(commands);
	}

	/** @return commands from this proposal. */
	public Collection<Command> getCommands() {
		return commands;
	}

	/** @return optional author of the proposal. */
	@Nullable
	public PersonIdent getAuthor() {
		return author;
	}

	/**
	 * Set the author for the proposal.
	 *
	 * @param who
	 *            optional identity of the author of the proposal.
	 * @return {@code this}
	 */
	public Proposal setAuthor(@Nullable PersonIdent who) {
		author = who;
		return this;
	}

	/** @return optional message for the commit log of the RefTree. */
	@Nullable
	public String getMessage() {
		return message;
	}

	/**
	 * Set the message to appear in the commit log of the RefTree.
	 *
	 * @param msg
	 *            message text for the commit.
	 * @return {@code this}
	 */
	public Proposal setMessage(@Nullable String msg) {
		message = msg != null && !msg.isEmpty() ? msg : null;
		return this;
	}

	/** @return optional certificate signing the references. */
	@Nullable
	public PushCertificate getPushCertificate() {
		return pushCert;
	}

	/**
	 * Set the push certificate signing the references.
	 *
	 * @param cert
	 *            certificate, may be null.
	 * @return {@code this}
	 */
	public Proposal setPushCertificate(@Nullable PushCertificate cert) {
		pushCert = cert;
		return this;
	}

	/**
	 * @return timestamps that Ketch must block for. These may have been used as
	 *         commit times inside the objects involved in the proposal.
	 */
	public List<ProposedTimestamp> getProposedTimestamps() {
		if (timestamps != null) {
			return timestamps;
		}
		return Collections.emptyList();
	}

	/**
	 * Request the proposal to wait for the affected timestamps to resolve.
	 *
	 * @param ts
	 * @return {@code this}.
	 */
	public Proposal addProposedTimestamp(ProposedTimestamp ts) {
		if (timestamps == null) {
			timestamps = new ArrayList<>(4);
		}
		timestamps.add(ts);
		return this;
	}

	/**
	 * Add a callback to be invoked when the proposal is done.
	 * <p>
	 * A proposal is done when it has entered either {@link State#EXECUTED} or
	 * {@link State#ABORTED} state. If the proposal is already done
	 * {@code callback.run()} is immediately invoked on the caller's thread.
	 *
	 * @param callback
	 *            method to run after the proposal is done. The callback may be
	 *            run on a Ketch system thread and should be completed quickly.
	 */
	public void addListener(Runnable callback) {
		boolean runNow = false;
		synchronized (state) {
			if (state.get().isDone()) {
				runNow = true;
			} else {
				listeners.add(callback);
			}
		}
		if (runNow) {
			callback.run();
		}
	}

	/** Set command result as OK. */
	void success() {
		for (Command c : commands) {
			if (c.getResult() == NOT_ATTEMPTED) {
				c.setResult(OK);
			}
		}
		notifyState(EXECUTED);
	}

	/** Mark commands as "transaction aborted". */
	void abort() {
		Command.abort(commands, null);
		notifyState(ABORTED);
	}

	/** @return read the current state of the proposal. */
	public State getState() {
		return state.get();
	}

	/**
	 * @return {@code true} if the proposal was attempted. A true value does not
	 *         mean consensus was reached, only that the proposal was considered
	 *         and will not be making any more progress beyond its current
	 *         state.
	 */
	public boolean isDone() {
		return state.get().isDone();
	}

	/**
	 * Wait for the proposal to be attempted and {@link #isDone()} to be true.
	 *
	 * @throws InterruptedException
	 *             caller was interrupted before proposal executed.
	 */
	public void await() throws InterruptedException {
		synchronized (state) {
			while (!state.get().isDone()) {
				state.wait();
			}
		}
	}

	/**
	 * Wait for the proposal to be attempted and {@link #isDone()} to be true.
	 *
	 * @param wait
	 *            how long to wait.
	 * @param unit
	 *            unit describing the wait time.
	 * @return true if the proposal is done; false if the method timed out.
	 * @throws InterruptedException
	 *             caller was interrupted before proposal executed.
	 */
	public boolean await(long wait, TimeUnit unit) throws InterruptedException {
		synchronized (state) {
			if (state.get().isDone()) {
				return true;
			}
			state.wait(unit.toMillis(wait));
			return state.get().isDone();
		}
	}

	/**
	 * Wait for the proposal to exit a state.
	 *
	 * @param notIn
	 *            state the proposal should not be in to return.
	 * @param wait
	 *            how long to wait.
	 * @param unit
	 *            unit describing the wait time.
	 * @return true if the proposal exited the state; false on time out.
	 * @throws InterruptedException
	 *             caller was interrupted before proposal executed.
	 */
	public boolean awaitStateChange(State notIn, long wait, TimeUnit unit)
			throws InterruptedException {
		synchronized (state) {
			if (state.get() != notIn) {
				return true;
			}
			state.wait(unit.toMillis(wait));
			return state.get() != notIn;
		}
	}

	void notifyState(State s) {
		synchronized (state) {
			state.set(s);
			state.notifyAll();
		}
		if (s.isDone()) {
			for (Runnable callback : listeners) {
				callback.run();
			}
			listeners.clear();
		}
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("Ketch Proposal {\n"); //$NON-NLS-1$
		s.append("  ").append(state.get()).append('\n'); //$NON-NLS-1$
		if (author != null) {
			s.append("  author ").append(author).append('\n'); //$NON-NLS-1$
		}
		if (message != null) {
			s.append("  message ").append(message).append('\n'); //$NON-NLS-1$
		}
		for (Command c : commands) {
			s.append("  "); //$NON-NLS-1$
			format(s, c.getOldRef(), "CREATE"); //$NON-NLS-1$
			s.append(' ');
			format(s, c.getNewRef(), "DELETE"); //$NON-NLS-1$
			s.append(' ').append(c.getRefName());
			if (c.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) {
				s.append(' ').append(c.getResult()); // $NON-NLS-1$
			}
			s.append('\n');
		}
		s.append('}');
		return s.toString();
	}

	private static void format(StringBuilder s, @Nullable Ref r, String n) {
		if (r == null) {
			s.append(n);
		} else if (r.isSymbolic()) {
			s.append(r.getTarget().getName());
		} else {
			ObjectId id = r.getObjectId();
			if (id != null) {
				s.append(id.abbreviate(8).name());
			}
		}
	}
}
