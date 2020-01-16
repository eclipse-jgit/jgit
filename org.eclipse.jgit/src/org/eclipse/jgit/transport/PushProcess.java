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

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
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

	/**
	 * Create process for specified transport and refs updates specification.
	 *
	 * @param transport
	 *            transport between remote and local repository, used to create
	 *            connection.
	 * @param toPush
	 *            specification of refs updates (and local tracking branches).
	 *
	 * @throws TransportException
	 */
	PushProcess(final Transport transport,
			final Collection<RemoteRefUpdate> toPush) throws TransportException {
		this(transport, toPush, null);
	}

	/**
	 * Create process for specified transport and refs updates specification.
	 *
	 * @param transport
	 *            transport between remote and local repository, used to create
	 *            connection.
	 * @param toPush
	 *            specification of refs updates (and local tracking branches).
	 * @param out
	 *            OutputStream to write messages to
	 * @throws TransportException
	 */
	PushProcess(final Transport transport,
			final Collection<RemoteRefUpdate> toPush, OutputStream out)
			throws TransportException {
		this.walker = new RevWalk(transport.local);
		this.transport = transport;
		this.toPush = new LinkedHashMap<>();
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
				res.setRemoteUpdates(toPush);
				monitor.endTask();

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

			// check for fast-forward:
			// - both old and new ref must point to commits, AND
			// - both of them must be known for us, exist in repository, AND
			// - old commit must be ancestor of new commit
			boolean fastForward = true;
			try {
				RevObject oldRev = walker.parseAny(advertisedOld);
				final RevObject newRev = walker.parseAny(rru.getNewObjectId());
				if (!(oldRev instanceof RevCommit)
						|| !(newRev instanceof RevCommit)
						|| !walker.isMergedInto((RevCommit) oldRev,
								(RevCommit) newRev))
					fastForward = false;
			} catch (MissingObjectException x) {
				fastForward = false;
			} catch (Exception x) {
				throw new TransportException(transport.getURI(), MessageFormat.format(
						JGitText.get().readingObjectsFromLocalRepositoryFailed, x.getMessage()), x);
			}
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
