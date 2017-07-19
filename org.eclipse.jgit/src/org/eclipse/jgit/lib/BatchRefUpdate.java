/*
 * Copyright (C) 2008-2012, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/**
 * Batch of reference updates to be applied to a repository.
 * <p>
 * The batch update is primarily useful in the transport code, where a client or
 * server is making changes to more than one reference at a time.
 */
public class BatchRefUpdate {
	/**
	 * Maximum delay the calling thread will tolerate while waiting for a
	 * {@code MonotonicClock} to resolve associated {@link ProposedTimestamp}s.
	 * <p>
	 * A default of 5 seconds was chosen by guessing. A common assumption is
	 * clock skew between machines on the same LAN using an NTP server also on
	 * the same LAN should be under 5 seconds. 5 seconds is also not that long
	 * for a large `git push` operation to complete.
	 *
	 * @since 4.9
	 */
	protected static final Duration MAX_WAIT = Duration.ofSeconds(5);

	private final RefDatabase refdb;

	/** Commands to apply during this batch. */
	private final List<ReceiveCommand> commands;

	/** Does the caller permit a forced update on a reference? */
	private boolean allowNonFastForwards;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Message the caller wants included in the reflog. */
	private String refLogMessage;

	/** Should the result value be appended to {@link #refLogMessage}. */
	private boolean refLogIncludeResult;

	/** Push certificate associated with this update. */
	private PushCertificate pushCert;

	/** Whether updates should be atomic. */
	private boolean atomic;

	/** Push options associated with this update. */
	private List<String> pushOptions;

	/** Associated timestamps that should be blocked on before update. */
	private List<ProposedTimestamp> timestamps;

	/**
	 * Initialize a new batch update.
	 *
	 * @param refdb
	 *            the reference database of the repository to be updated.
	 */
	protected BatchRefUpdate(RefDatabase refdb) {
		this.refdb = refdb;
		this.commands = new ArrayList<>();
		this.atomic = refdb.performsAtomicTransactions();
	}

	/**
	 * @return true if the batch update will permit a non-fast-forward update to
	 *         an existing reference.
	 */
	public boolean isAllowNonFastForwards() {
		return allowNonFastForwards;
	}

	/**
	 * Set if this update wants to permit a forced update.
	 *
	 * @param allow
	 *            true if this update batch should ignore merge tests.
	 * @return {@code this}.
	 */
	public BatchRefUpdate setAllowNonFastForwards(boolean allow) {
		allowNonFastForwards = allow;
		return this;
	}

	/** @return identity of the user making the change in the reflog. */
	public PersonIdent getRefLogIdent() {
		return refLogIdent;
	}

	/**
	 * Set the identity of the user appearing in the reflog.
	 * <p>
	 * The timestamp portion of the identity is ignored. A new identity with the
	 * current timestamp will be created automatically when the update occurs
	 * and the log record is written.
	 *
	 * @param pi
	 *            identity of the user. If null the identity will be
	 *            automatically determined based on the repository
	 *            configuration.
	 * @return {@code this}.
	 */
	public BatchRefUpdate setRefLogIdent(final PersonIdent pi) {
		refLogIdent = pi;
		return this;
	}

	/**
	 * Get the message to include in the reflog.
	 *
	 * @return message the caller wants to include in the reflog; null if the
	 *         update should not be logged.
	 */
	@Nullable
	public String getRefLogMessage() {
		return refLogMessage;
	}

	/**
	 * Check whether the reflog message should include the result of the update,
	 * such as fast-forward or force-update.
	 * <p>
	 * Describes the default for commands in this batch that do not override it
	 * with {@link ReceiveCommand#setRefLogMessage(String, boolean)}.
	 *
	 * @return true if the message should include the result.
	 */
	public boolean isRefLogIncludingResult() {
		return refLogIncludeResult;
	}

	/**
	 * Set the message to include in the reflog.
	 * <p>
	 * Describes the default for commands in this batch that do not override it
	 * with {@link ReceiveCommand#setRefLogMessage(String, boolean)}.
	 *
	 * @param msg
	 *            the message to describe this change. If null and appendStatus is
	 *            false, the reflog will not be updated.
	 * @param appendStatus
	 *            true if the status of the ref change (fast-forward or
	 *            forced-update) should be appended to the user supplied message.
	 * @return {@code this}.
	 */
	public BatchRefUpdate setRefLogMessage(String msg, boolean appendStatus) {
		if (msg == null && !appendStatus)
			disableRefLog();
		else if (msg == null && appendStatus) {
			refLogMessage = ""; //$NON-NLS-1$
			refLogIncludeResult = true;
		} else {
			refLogMessage = msg;
			refLogIncludeResult = appendStatus;
		}
		return this;
	}

	/**
	 * Don't record this update in the ref's associated reflog.
	 * <p>
	 * Equivalent to {@code setRefLogMessage(null, false)}.
	 *
	 * @return {@code this}.
	 */
	public BatchRefUpdate disableRefLog() {
		refLogMessage = null;
		refLogIncludeResult = false;
		return this;
	}

	/**
	 * Check whether log has been disabled by {@link #disableRefLog()}.
	 *
	 * @return true if disabled.
	 */
	public boolean isRefLogDisabled() {
		return refLogMessage == null;
	}

	/**
	 * Request that all updates in this batch be performed atomically.
	 * <p>
	 * When atomic updates are used, either all commands apply successfully, or
	 * none do. Commands that might have otherwise succeeded are rejected with
	 * {@code REJECTED_OTHER_REASON}.
	 * <p>
	 * This method only works if the underlying ref database supports atomic
	 * transactions, i.e. {@link RefDatabase#performsAtomicTransactions()} returns
	 * true. Calling this method with true if the underlying ref database does not
	 * support atomic transactions will cause all commands to fail with {@code
	 * REJECTED_OTHER_REASON}.
	 *
	 * @param atomic whether updates should be atomic.
	 * @return {@code this}
	 * @since 4.4
	 */
	public BatchRefUpdate setAtomic(boolean atomic) {
		this.atomic = atomic;
		return this;
	}

	/**
	 * @return atomic whether updates should be atomic.
	 * @since 4.4
	 */
	public boolean isAtomic() {
		return atomic;
	}

	/**
	 * Set a push certificate associated with this update.
	 * <p>
	 * This usually includes commands to update the refs in this batch, but is not
	 * required to.
	 *
	 * @param cert
	 *            push certificate, may be null.
	 * @since 4.1
	 */
	public void setPushCertificate(PushCertificate cert) {
		pushCert = cert;
	}

	/**
	 * Set the push certificate associated with this update.
	 * <p>
	 * This usually includes commands to update the refs in this batch, but is not
	 * required to.
	 *
	 * @return push certificate, may be null.
	 * @since 4.1
	 */
	protected PushCertificate getPushCertificate() {
		return pushCert;
	}

	/** @return commands this update will process. */
	public List<ReceiveCommand> getCommands() {
		return Collections.unmodifiableList(commands);
	}

	/**
	 * Add a single command to this batch update.
	 *
	 * @param cmd
	 *            the command to add, must not be null.
	 * @return {@code this}.
	 */
	public BatchRefUpdate addCommand(ReceiveCommand cmd) {
		commands.add(cmd);
		return this;
	}

	/**
	 * Add commands to this batch update.
	 *
	 * @param cmd
	 *            the commands to add, must not be null.
	 * @return {@code this}.
	 */
	public BatchRefUpdate addCommand(ReceiveCommand... cmd) {
		return addCommand(Arrays.asList(cmd));
	}

	/**
	 * Add commands to this batch update.
	 *
	 * @param cmd
	 *            the commands to add, must not be null.
	 * @return {@code this}.
	 */
	public BatchRefUpdate addCommand(Collection<ReceiveCommand> cmd) {
		commands.addAll(cmd);
		return this;
	}

	/**
	 * Gets the list of option strings associated with this update.
	 *
	 * @return push options that were passed to {@link #execute}; prior to calling
	 *         {@link #execute}, always returns null.
	 * @since 4.5
	 */
	@Nullable
	public List<String> getPushOptions() {
		return pushOptions;
	}

	/**
	 * Set push options associated with this update.
	 * <p>
	 * Implementations must call this at the top of {@link #execute(RevWalk,
	 * ProgressMonitor, List)}.
	 *
	 * @param options options passed to {@code execute}.
	 * @since 4.9
	 */
	protected void setPushOptions(List<String> options) {
		pushOptions = options;
	}

	/**
	 * @return list of timestamps the batch must wait for.
	 * @since 4.6
	 */
	public List<ProposedTimestamp> getProposedTimestamps() {
		if (timestamps != null) {
			return Collections.unmodifiableList(timestamps);
		}
		return Collections.emptyList();
	}

	/**
	 * Request the batch to wait for the affected timestamps to resolve.
	 *
	 * @param ts
	 * @return {@code this}.
	 * @since 4.6
	 */
	public BatchRefUpdate addProposedTimestamp(ProposedTimestamp ts) {
		if (timestamps == null) {
			timestamps = new ArrayList<>(4);
		}
		timestamps.add(ts);
		return this;
	}

	/**
	 * Execute this batch update.
	 * <p>
	 * The default implementation of this method performs a sequential reference
	 * update over each reference.
	 * <p>
	 * Implementations must respect the atomicity requirements of the underlying
	 * database as described in {@link #setAtomic(boolean)} and
	 * {@link RefDatabase#performsAtomicTransactions()}.
	 *
	 * @param walk
	 *            a RevWalk to parse tags in case the storage system wants to
	 *            store them pre-peeled, a common performance optimization.
	 * @param monitor
	 *            progress monitor to receive update status on.
	 * @param options
	 *            a list of option strings; set null to execute without
	 * @throws IOException
	 *             the database is unable to accept the update. Individual
	 *             command status must be tested to determine if there is a
	 *             partial failure, or a total failure.
	 * @since 4.5
	 */
	public void execute(RevWalk walk, ProgressMonitor monitor,
			List<String> options) throws IOException {

		if (atomic && !refdb.performsAtomicTransactions()) {
			for (ReceiveCommand c : commands) {
				if (c.getResult() == NOT_ATTEMPTED) {
					c.setResult(REJECTED_OTHER_REASON,
							JGitText.get().atomicRefUpdatesNotSupported);
				}
			}
			return;
		}
		if (!blockUntilTimestamps(MAX_WAIT)) {
			return;
		}

		if (options != null) {
			setPushOptions(options);
		}

		monitor.beginTask(JGitText.get().updatingReferences, commands.size());
		List<ReceiveCommand> commands2 = new ArrayList<>(
				commands.size());
		// First delete refs. This may free the name space for some of the
		// updates.
		for (ReceiveCommand cmd : commands) {
			try {
				if (cmd.getResult() == NOT_ATTEMPTED) {
					if (isMissing(walk, cmd.getOldId())
							|| isMissing(walk, cmd.getNewId())) {
						cmd.setResult(ReceiveCommand.Result.REJECTED_MISSING_OBJECT);
						continue;
					}
					cmd.updateType(walk);
					switch (cmd.getType()) {
					case CREATE:
						commands2.add(cmd);
						break;
					case UPDATE:
					case UPDATE_NONFASTFORWARD:
						commands2.add(cmd);
						break;
					case DELETE:
						RefUpdate rud = newUpdate(cmd);
						monitor.update(1);
						cmd.setResult(rud.delete(walk));
					}
				}
			} catch (IOException err) {
				cmd.setResult(
						REJECTED_OTHER_REASON,
						MessageFormat.format(JGitText.get().lockError,
								err.getMessage()));
			}
		}
		if (!commands2.isEmpty()) {
			// What part of the name space is already taken
			Collection<String> takenNames = new HashSet<>(refdb.getRefs(
					RefDatabase.ALL).keySet());
			Collection<String> takenPrefixes = getTakenPrefixes(takenNames);

			// Now to the update that may require more room in the name space
			for (ReceiveCommand cmd : commands2) {
				try {
					if (cmd.getResult() == NOT_ATTEMPTED) {
						cmd.updateType(walk);
						RefUpdate ru = newUpdate(cmd);
						SWITCH: switch (cmd.getType()) {
						case DELETE:
							// Performed in the first phase
							break;
						case UPDATE:
						case UPDATE_NONFASTFORWARD:
							RefUpdate ruu = newUpdate(cmd);
							cmd.setResult(ruu.update(walk));
							break;
						case CREATE:
							for (String prefix : getPrefixes(cmd.getRefName())) {
								if (takenNames.contains(prefix)) {
									cmd.setResult(Result.LOCK_FAILURE);
									break SWITCH;
								}
							}
							if (takenPrefixes.contains(cmd.getRefName())) {
								cmd.setResult(Result.LOCK_FAILURE);
								break SWITCH;
							}
							ru.setCheckConflicting(false);
							takenPrefixes.addAll(getPrefixes(cmd.getRefName()));
							takenNames.add(cmd.getRefName());
							cmd.setResult(ru.update(walk));
						}
					}
				} catch (IOException err) {
					cmd.setResult(REJECTED_OTHER_REASON, MessageFormat.format(
							JGitText.get().lockError, err.getMessage()));
				} finally {
					monitor.update(1);
				}
			}
		}
		monitor.endTask();
	}

	private static boolean isMissing(RevWalk walk, ObjectId id)
			throws IOException {
		if (id.equals(ObjectId.zeroId())) {
			return false; // Explicit add or delete is not missing.
		}
		try {
			walk.parseAny(id);
			return false;
		} catch (MissingObjectException e) {
			return true;
		}
	}

	/**
	 * Wait for timestamps to be in the past, aborting commands on timeout.
	 *
	 * @param maxWait
	 *            maximum amount of time to wait for timestamps to resolve.
	 * @return true if timestamps were successfully waited for; false if
	 *         commands were aborted.
	 * @since 4.6
	 */
	protected boolean blockUntilTimestamps(Duration maxWait) {
		if (timestamps == null) {
			return true;
		}
		try {
			ProposedTimestamp.blockUntil(timestamps, maxWait);
			return true;
		} catch (TimeoutException | InterruptedException e) {
			String msg = JGitText.get().timeIsUncertain;
			for (ReceiveCommand c : commands) {
				if (c.getResult() == NOT_ATTEMPTED) {
					c.setResult(REJECTED_OTHER_REASON, msg);
				}
			}
			return false;
		}
	}

	/**
	 * Execute this batch update without option strings.
	 *
	 * @param walk
	 *            a RevWalk to parse tags in case the storage system wants to
	 *            store them pre-peeled, a common performance optimization.
	 * @param monitor
	 *            progress monitor to receive update status on.
	 * @throws IOException
	 *             the database is unable to accept the update. Individual
	 *             command status must be tested to determine if there is a
	 *             partial failure, or a total failure.
	 */
	public void execute(RevWalk walk, ProgressMonitor monitor)
			throws IOException {
		execute(walk, monitor, null);
	}

	private static Collection<String> getTakenPrefixes(Collection<String> names) {
		Collection<String> ref = new HashSet<>();
		for (String name : names) {
			addPrefixesTo(name, ref);
		}
		return ref;
	}

	/**
	 * Get all path prefixes of a ref name.
	 *
	 * @param name
	 *            ref name.
	 * @return path prefixes of the ref name. For {@code refs/heads/foo}, returns
	 *         {@code refs} and {@code refs/heads}.
	 * @since 4.9
	 */
	protected static Collection<String> getPrefixes(String name) {
		Collection<String> ret = new HashSet<>();
		addPrefixesTo(name, ret);
		return ret;
	}

	/**
	 * Add prefixes of a ref name to an existing collection.
	 *
	 * @param name
	 *            ref name.
	 * @param out
	 *            path prefixes of the ref name. For {@code refs/heads/foo},
	 *            returns {@code refs} and {@code refs/heads}.
	 * @since 4.9
	 */
	protected static void addPrefixesTo(String name, Collection<String> out) {
		int p1 = name.indexOf('/');
		while (p1 > 0) {
			out.add(name.substring(0, p1));
			p1 = name.indexOf('/', p1 + 1);
		}
	}

	/**
	 * Create a new RefUpdate copying the batch settings.
	 *
	 * @param cmd
	 *            specific command the update should be created to copy.
	 * @return a single reference update command.
	 * @throws IOException
	 *             the reference database cannot make a new update object for
	 *             the given reference.
	 */
	protected RefUpdate newUpdate(ReceiveCommand cmd) throws IOException {
		RefUpdate ru = refdb.newUpdate(cmd.getRefName(), false);
		if (isRefLogDisabled(cmd)) {
			ru.disableRefLog();
		} else {
			ru.setRefLogIdent(refLogIdent);
			ru.setRefLogMessage(getRefLogMessage(cmd), isRefLogIncludingResult(cmd));
		}
		ru.setPushCertificate(pushCert);
		switch (cmd.getType()) {
		case DELETE:
			if (!ObjectId.zeroId().equals(cmd.getOldId()))
				ru.setExpectedOldObjectId(cmd.getOldId());
			ru.setForceUpdate(true);
			return ru;

		case CREATE:
		case UPDATE:
		case UPDATE_NONFASTFORWARD:
		default:
			ru.setForceUpdate(isAllowNonFastForwards());
			ru.setExpectedOldObjectId(cmd.getOldId());
			ru.setNewObjectId(cmd.getNewId());
			return ru;
		}
	}

	/**
	 * Check whether reflog is disabled for a command.
	 *
	 * @param cmd
	 *            specific command.
	 * @return whether the reflog is disabled, taking into account the state from
	 *         this instance as well as overrides in the given command.
	 * @since 4.9
	 */
	protected boolean isRefLogDisabled(ReceiveCommand cmd) {
		return cmd.hasCustomRefLog() ? cmd.isRefLogDisabled() : isRefLogDisabled();
	}

	/**
	 * Get reflog message for a command.
	 *
	 * @param cmd
	 *            specific command.
	 * @return reflog message, taking into account the state from this instance as
	 *         well as overrides in the given command.
	 * @since 4.9
	 */
	protected String getRefLogMessage(ReceiveCommand cmd) {
		return cmd.hasCustomRefLog() ? cmd.getRefLogMessage() : getRefLogMessage();
	}

	/**
	 * Check whether the reflog message for a command should include the result.
	 *
	 * @param cmd
	 *            specific command.
	 * @return whether the reflog message should show the result, taking into
	 *         account the state from this instance as well as overrides in the
	 *         given command.
	 * @since 4.9
	 */
	protected boolean isRefLogIncludingResult(ReceiveCommand cmd) {
		return cmd.hasCustomRefLog()
				? cmd.isRefLogIncludingResult() : isRefLogIncludingResult();
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append(getClass().getSimpleName()).append('[');
		if (commands.isEmpty())
			return r.append(']').toString();

		r.append('\n');
		for (ReceiveCommand cmd : commands) {
			r.append("  "); //$NON-NLS-1$
			r.append(cmd);
			r.append("  (").append(cmd.getResult()); //$NON-NLS-1$
			if (cmd.getMessage() != null) {
				r.append(": ").append(cmd.getMessage()); //$NON-NLS-1$
			}
			r.append(")\n"); //$NON-NLS-1$
		}
		return r.append(']').toString();
	}
}
