/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * A push request sending objects to a replica, and its result.
 * <p>
 * Implementors of {@link org.eclipse.jgit.internal.ketch.KetchReplica} must
 * populate the command result fields, {@link #setRefs(Map)}, and call one of
 * {@link #setException(Repository, Throwable)} or {@link #done(Repository)} to
 * finish processing.
 */
public class ReplicaPushRequest {
	private final KetchReplica replica;
	private final Collection<ReceiveCommand> commands;
	private Map<String, Ref> refs;
	private Throwable exception;
	private boolean notified;

	/**
	 * Construct a new push request for a replica.
	 *
	 * @param replica
	 *            the replica being pushed to.
	 * @param commands
	 *            commands to be executed.
	 */
	public ReplicaPushRequest(KetchReplica replica,
			Collection<ReceiveCommand> commands) {
		this.replica = replica;
		this.commands = commands;
	}

	/**
	 * Get commands to be executed, and their results.
	 *
	 * @return commands to be executed, and their results.
	 */
	public Collection<ReceiveCommand> getCommands() {
		return commands;
	}

	/**
	 * Get remote references, usually from the advertisement.
	 *
	 * @return remote references, usually from the advertisement.
	 */
	@Nullable
	public Map<String, Ref> getRefs() {
		return refs;
	}

	/**
	 * Set references observed from the replica.
	 *
	 * @param refs
	 *            references observed from the replica.
	 */
	public void setRefs(Map<String, Ref> refs) {
		this.refs = refs;
	}

	/**
	 * Get exception thrown, if any.
	 *
	 * @return exception thrown, if any.
	 */
	@Nullable
	public Throwable getException() {
		return exception;
	}

	/**
	 * Mark the request as crashing with a communication error.
	 * <p>
	 * This method may take significant time acquiring the leader lock and
	 * updating the Ketch state machine with the failure.
	 *
	 * @param repo
	 *            local repository reference used by the push attempt.
	 * @param err
	 *            exception thrown during communication.
	 */
	public void setException(@Nullable Repository repo, Throwable err) {
		if (KetchReplica.log.isErrorEnabled()) {
			KetchReplica.log.error(describe("failed"), err); //$NON-NLS-1$
		}
		if (!notified) {
			notified = true;
			exception = err;
			replica.afterPush(repo, this);
		}
	}

	/**
	 * Mark the request as completed without exception.
	 * <p>
	 * This method may take significant time acquiring the leader lock and
	 * updating the Ketch state machine with results from this replica.
	 *
	 * @param repo
	 *            local repository reference used by the push attempt.
	 */
	public void done(Repository repo) {
		if (KetchReplica.log.isDebugEnabled()) {
			KetchReplica.log.debug(describe("completed")); //$NON-NLS-1$
		}
		if (!notified) {
			notified = true;
			replica.afterPush(repo, this);
		}
	}

	private String describe(String heading) {
		StringBuilder b = new StringBuilder();
		b.append("push to "); //$NON-NLS-1$
		b.append(replica.describeForLog());
		b.append(' ').append(heading).append(":\n"); //$NON-NLS-1$
		for (ReceiveCommand cmd : commands) {
			b.append(String.format(
					"  %-12s %-12s %s %s", //$NON-NLS-1$
					LeaderSnapshot.str(cmd.getOldId()),
					LeaderSnapshot.str(cmd.getNewId()),
					cmd.getRefName(),
					cmd.getResult()));
			if (cmd.getMessage() != null) {
				b.append(' ').append(cmd.getMessage());
			}
			b.append('\n');
		}
		return b.toString();
	}
}
