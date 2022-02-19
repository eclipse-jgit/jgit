/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

/**
 * Class performing push operation on remote repository.
 *
 * @see Transport#push(ProgressMonitor, Collection, OutputStream)
 */
class PushProcess {
	/** Task name for {@link ProgressMonitor} used during opening connection. */
	static final String PROGRESS_OPENING_CONNECTION = JGitText.get().openingConnection;

	/** Transport used to perform this operation. */
	private final Transport transport;

	/** Push operation connection created to perform this operation */
	private PushConnection connection;

	/** Refs to update on remote side. */
	private final Map<String, RemoteRefUpdate> toPush;

	/** Revision walker for checking some updates properties. */
	private final RevWalk walker;

	/** an outputstream to write messages to */
	private final OutputStream out;

	/** A list of option strings associated with this push */
	private List<String> pushOptions;

	private final PrePushHook prePush;

	/**
	 * Create process for specified transport and refs updates specification.
	 *
	 * @param transport
	 *            transport between remote and local repository, used to create
	 *            connection.
	 * @param toPush
	 *            specification of refs updates (and local tracking branches).
	 * @param prePush
	 *            {@link PrePushHook} to run after the remote advertisement has
	 *            been gotten
	 * @throws TransportException
	 */
	PushProcess(final Transport transport,
			final Collection<RemoteRefUpdate> toPush, PrePushHook prePush)
			throws TransportException {
		this(transport, toPush, prePush, null);
	}

	/**
	 * Create process for specified transport and refs updates specification.
	 *
	 * @param transport
	 *            transport between remote and local repository, used to create
	 *            connection.
	 * @param toPush
	 *            specification of refs updates (and local tracking branches).
	 * @param prePush
	 *            {@link PrePushHook} to run after the remote advertisement has
	 *            been gotten
	 * @param out
	 *            OutputStream to write messages to
	 * @throws TransportException
	 */
	PushProcess(final Transport transport,
			final Collection<RemoteRefUpdate> toPush, PrePushHook prePush,
			OutputStream out) throws TransportException {
		this.walker = new RevWalk(transport.local);
		this.transport = transport;
		this.toPush = new LinkedHashMap<>();
		this.prePush = prePush;
		this.out = out;
		this.pushOptions = transport.getPushOptions();
		for (RemoteRefUpdate rru : toPush) {
			if (this.toPush.put(rru.getRemoteName(), rru) != null)
				throw new TransportException(MessageFormat.format(
						JGitText.get().duplicateRemoteRefUpdateIsIllegal, rru.getRemoteName()));
		}
	}

	/**
	 * Perform push operation between local and remote repository - set remote
	 * refs appropriately, send needed objects and update local tracking refs.
	 * <p>
	 * When {@link Transport#isDryRun()} is true, result of this operation is
	 * just estimation of real operation result, no real action is performed.
	 *
	 * @param monitor
	 *            progress monitor used for feedback about operation.
	 * @return result of push operation with complete status description.
	 * @throws NotSupportedException
	 *             when push operation is not supported by provided transport.
	 * @throws TransportException
	 *             when some error occurred during operation, like I/O, protocol
	 *             error, or local database consistency error.
	 */
	PushResult execute(ProgressMonitor monitor)
			throws NotSupportedException, TransportException {
		try {
			monitor.beginTask(PROGRESS_OPENING_CONNECTION,
					ProgressMonitor.UNKNOWN);

			final PushResult res = new PushResult();
			connection = transport.openPush();
			try {
				res.setAdvertisedRefs(transport.getURI(), connection
						.getRefsMap());
				res.peerUserAgent = connection.getPeerUserAgent();
				monitor.endTask();

				Map<String, RemoteRefUpdate> expanded = expandMatching();
				toPush.clear();
				toPush.putAll(expanded);

				if (prePush != null) {
					try {
						prePush.setRefs(toPush.values());
						prePush.call();
					} catch (AbortedByHookException | IOException e) {
						throw new TransportException(e.getMessage(), e);
					}
				}

				res.setRemoteUpdates(toPush);
				final Map<String, RemoteRefUpdate> preprocessed = prepareRemoteUpdates();
				if (transport.isDryRun())
					modifyUpdatesForDryRun();
				else if (!preprocessed.isEmpty())
					connection.push(monitor, preprocessed, out);
			} finally {
				connection.close();
				res.addMessages(connection.getMessages());
			}
			if (!transport.isDryRun())
				updateTrackingRefs();
			for (RemoteRefUpdate rru : toPush.values()) {
				final TrackingRefUpdate tru = rru.getTrackingRefUpdate();
				if (tru != null)
					res.add(tru);
			}
			return res;
		} finally {
			walker.close();
		}
	}

	private Map<String, RemoteRefUpdate> prepareRemoteUpdates()
			throws TransportException {
		boolean atomic = transport.isPushAtomic();
		final Map<String, RemoteRefUpdate> result = new LinkedHashMap<>();
		for (RemoteRefUpdate rru : toPush.values()) {
			final Ref advertisedRef = connection.getRef(rru.getRemoteName());
			ObjectId advertisedOld = null;
			if (advertisedRef != null) {
				advertisedOld = advertisedRef.getObjectId();
			}
			if (advertisedOld == null) {
				advertisedOld = ObjectId.zeroId();
			}

			if (rru.getNewObjectId().equals(advertisedOld)) {
				if (rru.isDelete()) {
					// ref does exist neither locally nor remotely
					rru.setStatus(Status.NON_EXISTING);
				} else {
					// same object - nothing to do
					rru.setStatus(Status.UP_TO_DATE);
				}
				continue;
			}

			// caller has explicitly specified expected old object id, while it
			// has been changed in the mean time - reject
			if (rru.isExpectingOldObjectId()
					&& !rru.getExpectedOldObjectId().equals(advertisedOld)) {
				rru.setStatus(Status.REJECTED_REMOTE_CHANGED);
				if (atomic) {
					return rejectAll();
				}
				continue;
			}
			if (!rru.isExpectingOldObjectId()) {
				rru.setExpectedOldObjectId(advertisedOld);
			}

			// create ref (hasn't existed on remote side) and delete ref
			// are always fast-forward commands, feasible at this level
			if (advertisedOld.equals(ObjectId.zeroId()) || rru.isDelete()) {
				rru.setFastForward(true);
				result.put(rru.getRemoteName(), rru);
				continue;
			}

			boolean fastForward = isFastForward(advertisedOld,
					rru.getNewObjectId());
			rru.setFastForward(fastForward);
			if (!fastForward && !rru.isForceUpdate()) {
				rru.setStatus(Status.REJECTED_NONFASTFORWARD);
				if (atomic) {
					return rejectAll();
				}
			} else {
				result.put(rru.getRemoteName(), rru);
			}
		}
		return result;
	}

	/**
	 * Determines whether an update from {@code oldOid} to {@code newOid} is a
	 * fast-forward update:
	 * <ul>
	 * <li>both old and new must be commits, AND</li>
	 * <li>both of them must be known to us and exist in the repository,
	 * AND</li>
	 * <li>the old commit must be an ancestor of the new commit.</li>
	 * </ul>
	 *
	 * @param oldOid
	 *            {@link ObjectId} of the old commit
	 * @param newOid
	 *            {@link ObjectId} of the new commit
	 * @return {@code true} if the update fast-forwards, {@code false} otherwise
	 * @throws TransportException
	 */
	private boolean isFastForward(ObjectId oldOid, ObjectId newOid)
			throws TransportException {
		try {
			RevObject oldRev = walker.parseAny(oldOid);
			RevObject newRev = walker.parseAny(newOid);
			if (!(oldRev instanceof RevCommit) || !(newRev instanceof RevCommit)
					|| !walker.isMergedInto((RevCommit) oldRev,
							(RevCommit) newRev)) {
				return false;
			}
		} catch (MissingObjectException x) {
			return false;
		} catch (Exception x) {
			throw new TransportException(transport.getURI(),
					MessageFormat.format(JGitText
							.get().readingObjectsFromLocalRepositoryFailed,
							x.getMessage()),
					x);
		}
		return true;
	}

	/**
	 * Expands all placeholder {@link RemoteRefUpdate}s for "matching"
	 * {@link RefSpec}s ":" in {@link #toPush} and returns the resulting map in
	 * which the placeholders have been replaced by their expansion.
	 *
	 * @return a new map of {@link RemoteRefUpdate}s keyed by remote name
	 * @throws TransportException
	 *             if the expansion results in duplicate updates
	 */
	private Map<String, RemoteRefUpdate> expandMatching()
			throws TransportException {
		Map<String, RemoteRefUpdate> result = new LinkedHashMap<>();
		boolean hadMatch = false;
		for (RemoteRefUpdate update : toPush.values()) {
			if (update.isMatching()) {
				if (hadMatch) {
					throw new TransportException(MessageFormat.format(
							JGitText.get().duplicateRemoteRefUpdateIsIllegal,
							":")); //$NON-NLS-1$
				}
				expandMatching(result, update);
				hadMatch = true;
			} else if (result.put(update.getRemoteName(), update) != null) {
				throw new TransportException(MessageFormat.format(
						JGitText.get().duplicateRemoteRefUpdateIsIllegal,
						update.getRemoteName()));
			}
		}
		return result;
	}

	/**
	 * Expands the placeholder {@link RemoteRefUpdate} {@code match} for a
	 * "matching" {@link RefSpec} ":" or "+:" and puts the expansion into the
	 * given map {@code updates}.
	 *
	 * @param updates
	 *            map to put the expansion in
	 * @param match
	 *            the placeholder {@link RemoteRefUpdate} to expand
	 *
	 * @throws TransportException
	 *             if the expansion results in duplicate updates, or the local
	 *             branches cannot be determined
	 */
	private void expandMatching(Map<String, RemoteRefUpdate> updates,
			RemoteRefUpdate match) throws TransportException {
		try {
			Map<String, Ref> advertisement = connection.getRefsMap();
			Collection<RefSpec> fetchSpecs = match.getFetchSpecs();
			boolean forceUpdate = match.isForceUpdate();
			for (Ref local : transport.local.getRefDatabase()
					.getRefsByPrefix(Constants.R_HEADS)) {
				if (local.isSymbolic()) {
					continue;
				}
				String name = local.getName();
				Ref advertised = advertisement.get(name);
				if (advertised == null || advertised.isSymbolic()) {
					continue;
				}
				ObjectId oldOid = advertised.getObjectId();
				if (oldOid == null || ObjectId.zeroId().equals(oldOid)) {
					continue;
				}
				ObjectId newOid = local.getObjectId();
				if (newOid == null || ObjectId.zeroId().equals(newOid)) {
					continue;
				}

				RemoteRefUpdate rru = new RemoteRefUpdate(transport.local, name,
						newOid, name, forceUpdate,
						Transport.findTrackingRefName(name, fetchSpecs),
						oldOid);
				if (updates.put(rru.getRemoteName(), rru) != null) {
					throw new TransportException(MessageFormat.format(
							JGitText.get().duplicateRemoteRefUpdateIsIllegal,
							rru.getRemoteName()));
				}
			}
		} catch (IOException x) {
			throw new TransportException(transport.getURI(),
					MessageFormat.format(JGitText
							.get().readingObjectsFromLocalRepositoryFailed,
							x.getMessage()),
					x);
		}
	}

	private Map<String, RemoteRefUpdate> rejectAll() {
		for (RemoteRefUpdate rru : toPush.values()) {
			if (rru.getStatus() == Status.NOT_ATTEMPTED) {
				rru.setStatus(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
				rru.setMessage(JGitText.get().transactionAborted);
			}
		}
		return Collections.emptyMap();
	}

	private void modifyUpdatesForDryRun() {
		for (RemoteRefUpdate rru : toPush.values())
			if (rru.getStatus() == Status.NOT_ATTEMPTED)
				rru.setStatus(Status.OK);
	}

	private void updateTrackingRefs() {
		for (RemoteRefUpdate rru : toPush.values()) {
			final Status status = rru.getStatus();
			if (rru.hasTrackingRefUpdate()
					&& (status == Status.UP_TO_DATE || status == Status.OK)) {
				// update local tracking branch only when there is a chance that
				// it has changed; this is possible for:
				// -updated (OK) status,
				// -up to date (UP_TO_DATE) status
				try {
					rru.updateTrackingRef(walker);
				} catch (IOException e) {
					// ignore as RefUpdate has stored I/O error status
				}
			}
		}
	}

	/**
	 * Gets the list of option strings associated with this push.
	 *
	 * @return pushOptions
	 * @since 4.5
	 */
	public List<String> getPushOptions() {
		return pushOptions;
	}
}
