/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.transport;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.storage.file.PackLock;

class FetchProcess {
	/** Transport we will fetch over. */
	private final Transport transport;

	/** List of things we want to fetch from the remote repository. */
	private final Collection<RefSpec> toFetch;

	/** Set of refs we will actually wind up asking to obtain. */
	private final HashMap<ObjectId, Ref> askFor = new HashMap<ObjectId, Ref>();

	/** Objects we know we have locally. */
	private final HashSet<ObjectId> have = new HashSet<ObjectId>();

	/** Updates to local tracking branches (if any). */
	private final ArrayList<TrackingRefUpdate> localUpdates = new ArrayList<TrackingRefUpdate>();

	/** Records to be recorded into FETCH_HEAD. */
	private final ArrayList<FetchHeadRecord> fetchHeadUpdates = new ArrayList<FetchHeadRecord>();

	private final ArrayList<PackLock> packLocks = new ArrayList<PackLock>();

	private FetchConnection conn;

	FetchProcess(final Transport t, final Collection<RefSpec> f) {
		transport = t;
		toFetch = f;
	}

	void execute(final ProgressMonitor monitor, final FetchResult result)
			throws NotSupportedException, TransportException {
		askFor.clear();
		localUpdates.clear();
		fetchHeadUpdates.clear();
		packLocks.clear();

		try {
			executeImp(monitor, result);
		} finally {
			for (final PackLock lock : packLocks)
				lock.unlock();
		}
	}

	private void executeImp(final ProgressMonitor monitor,
			final FetchResult result) throws NotSupportedException,
			TransportException {
		conn = transport.openFetch();
		try {
			result.setAdvertisedRefs(transport.getURI(), conn.getRefsMap());
			final Set<Ref> matched = new HashSet<Ref>();
			for (final RefSpec spec : toFetch) {
				if (spec.getSource() == null)
					throw new TransportException(MessageFormat.format(
							JGitText.get().sourceRefNotSpecifiedForRefspec, spec));

				if (spec.isWildcard())
					expandWildcard(spec, matched);
				else
					expandSingle(spec, matched);
			}

			Collection<Ref> additionalTags = Collections.<Ref> emptyList();
			final TagOpt tagopt = transport.getTagOpt();
			if (tagopt == TagOpt.AUTO_FOLLOW)
				additionalTags = expandAutoFollowTags();
			else if (tagopt == TagOpt.FETCH_TAGS)
				expandFetchTags();

			final boolean includedTags;
			if (!askFor.isEmpty() && !askForIsComplete()) {
				fetchObjects(monitor);
				includedTags = conn.didFetchIncludeTags();

				// Connection was used for object transfer. If we
				// do another fetch we must open a new connection.
				//
				closeConnection(result);
			} else {
				includedTags = false;
			}

			if (tagopt == TagOpt.AUTO_FOLLOW && !additionalTags.isEmpty()) {
				// There are more tags that we want to follow, but
				// not all were asked for on the initial request.
				//
				have.addAll(askFor.keySet());
				askFor.clear();
				for (final Ref r : additionalTags) {
					final ObjectId id = r.getPeeledObjectId();
					if (id == null || transport.local.hasObject(id))
						wantTag(r);
				}

				if (!askFor.isEmpty() && (!includedTags || !askForIsComplete())) {
					reopenConnection();
					if (!askFor.isEmpty())
						fetchObjects(monitor);
				}
			}
		} finally {
			closeConnection(result);
		}

		final RevWalk walk = new RevWalk(transport.local);
		try {
			if (transport.isRemoveDeletedRefs())
				deleteStaleTrackingRefs(result, walk);
			for (TrackingRefUpdate u : localUpdates) {
				try {
					u.update(walk);
					result.add(u);
				} catch (IOException err) {
					throw new TransportException(MessageFormat.format(JGitText
							.get().failureUpdatingTrackingRef,
							u.getLocalName(), err.getMessage()), err);
				}
			}
		} finally {
			walk.release();
		}

		if (!fetchHeadUpdates.isEmpty()) {
			try {
				updateFETCH_HEAD(result);
			} catch (IOException err) {
				throw new TransportException(MessageFormat.format(
						JGitText.get().failureUpdatingFETCH_HEAD, err.getMessage()), err);
			}
		}
	}

	private void fetchObjects(final ProgressMonitor monitor)
			throws TransportException {
		try {
			conn.setPackLockMessage("jgit fetch " + transport.uri);
			conn.fetch(monitor, askFor.values(), have);
		} finally {
			packLocks.addAll(conn.getPackLocks());
		}
		if (transport.isCheckFetchedObjects()
				&& !conn.didFetchTestConnectivity() && !askForIsComplete())
			throw new TransportException(transport.getURI(),
					JGitText.get().peerDidNotSupplyACompleteObjectGraph);
	}

	private void closeConnection(final FetchResult result) {
		if (conn != null) {
			conn.close();
			result.addMessages(conn.getMessages());
			conn = null;
		}
	}

	private void reopenConnection() throws NotSupportedException,
			TransportException {
		if (conn != null)
			return;

		conn = transport.openFetch();

		// Since we opened a new connection we cannot be certain
		// that the system we connected to has the same exact set
		// of objects available (think round-robin DNS and mirrors
		// that aren't updated at the same time).
		//
		// We rebuild our askFor list using only the refs that the
		// new connection has offered to us.
		//
		final HashMap<ObjectId, Ref> avail = new HashMap<ObjectId, Ref>();
		for (final Ref r : conn.getRefs())
			avail.put(r.getObjectId(), r);

		final Collection<Ref> wants = new ArrayList<Ref>(askFor.values());
		askFor.clear();
		for (final Ref want : wants) {
			final Ref newRef = avail.get(want.getObjectId());
			if (newRef != null) {
				askFor.put(newRef.getObjectId(), newRef);
			} else {
				removeFetchHeadRecord(want.getObjectId());
				removeTrackingRefUpdate(want.getObjectId());
			}
		}
	}

	private void removeTrackingRefUpdate(final ObjectId want) {
		final Iterator<TrackingRefUpdate> i = localUpdates.iterator();
		while (i.hasNext()) {
			final TrackingRefUpdate u = i.next();
			if (u.getNewObjectId().equals(want))
				i.remove();
		}
	}

	private void removeFetchHeadRecord(final ObjectId want) {
		final Iterator<FetchHeadRecord> i = fetchHeadUpdates.iterator();
		while (i.hasNext()) {
			final FetchHeadRecord fh = i.next();
			if (fh.newValue.equals(want))
				i.remove();
		}
	}

	private void updateFETCH_HEAD(final FetchResult result) throws IOException {
		File meta = transport.local.getDirectory();
		if (meta == null)
			return;
		final LockFile lock = new LockFile(new File(meta, "FETCH_HEAD"),
				transport.local.getFS());
		try {
			if (lock.lock()) {
				final Writer w = new OutputStreamWriter(lock.getOutputStream());
				try {
					for (final FetchHeadRecord h : fetchHeadUpdates) {
						h.write(w);
						result.add(h);
					}
				} finally {
					w.close();
				}
				lock.commit();
			}
		} finally {
			lock.unlock();
		}
	}

	private boolean askForIsComplete() throws TransportException {
		try {
			final ObjectWalk ow = new ObjectWalk(transport.local);
			try {
				for (final ObjectId want : askFor.keySet())
					ow.markStart(ow.parseAny(want));
				for (final Ref ref : transport.local.getAllRefs().values())
					ow.markUninteresting(ow.parseAny(ref.getObjectId()));
				ow.checkConnectivity();
			} finally {
				ow.release();
			}
			return true;
		} catch (MissingObjectException e) {
			return false;
		} catch (IOException e) {
			throw new TransportException(JGitText.get().unableToCheckConnectivity, e);
		}
	}

	private void expandWildcard(final RefSpec spec, final Set<Ref> matched)
			throws TransportException {
		for (final Ref src : conn.getRefs()) {
			if (spec.matchSource(src) && matched.add(src))
				want(src, spec.expandFromSource(src));
		}
	}

	private void expandSingle(final RefSpec spec, final Set<Ref> matched)
			throws TransportException {
		final Ref src = conn.getRef(spec.getSource());
		if (src == null) {
			throw new TransportException(MessageFormat.format(JGitText.get().remoteDoesNotHaveSpec, spec.getSource()));
		}
		if (matched.add(src))
			want(src, spec);
	}

	private Collection<Ref> expandAutoFollowTags() throws TransportException {
		final Collection<Ref> additionalTags = new ArrayList<Ref>();
		final Map<String, Ref> haveRefs = transport.local.getAllRefs();
		for (final Ref r : conn.getRefs()) {
			if (!isTag(r))
				continue;
			if (r.getPeeledObjectId() == null) {
				additionalTags.add(r);
				continue;
			}

			final Ref local = haveRefs.get(r.getName());
			if (local != null) {
				if (!r.getObjectId().equals(local.getObjectId()))
					wantTag(r);
			} else if (askFor.containsKey(r.getPeeledObjectId())
					|| transport.local.hasObject(r.getPeeledObjectId()))
				wantTag(r);
			else
				additionalTags.add(r);
		}
		return additionalTags;
	}

	private void expandFetchTags() throws TransportException {
		final Map<String, Ref> haveRefs = transport.local.getAllRefs();
		for (final Ref r : conn.getRefs()) {
			if (!isTag(r))
				continue;
			final Ref local = haveRefs.get(r.getName());
			if (local == null || !r.getObjectId().equals(local.getObjectId()))
				wantTag(r);
		}
	}

	private void wantTag(final Ref r) throws TransportException {
		want(r, new RefSpec().setSource(r.getName())
				.setDestination(r.getName()));
	}

	private void want(final Ref src, final RefSpec spec)
			throws TransportException {
		final ObjectId newId = src.getObjectId();
		if (spec.getDestination() != null) {
			try {
				final TrackingRefUpdate tru = createUpdate(spec, newId);
				if (newId.equals(tru.getOldObjectId()))
					return;
				localUpdates.add(tru);
			} catch (IOException err) {
				// Bad symbolic ref? That is the most likely cause.
				//
				throw new TransportException( MessageFormat.format(
						JGitText.get().cannotResolveLocalTrackingRefForUpdating, spec.getDestination()), err);
			}
		}

		askFor.put(newId, src);

		final FetchHeadRecord fhr = new FetchHeadRecord();
		fhr.newValue = newId;
		fhr.notForMerge = spec.getDestination() != null;
		fhr.sourceName = src.getName();
		fhr.sourceURI = transport.getURI();
		fetchHeadUpdates.add(fhr);
	}

	private TrackingRefUpdate createUpdate(final RefSpec spec,
			final ObjectId newId) throws IOException {
		return new TrackingRefUpdate(transport.local, spec, newId, "fetch");
	}

	private void deleteStaleTrackingRefs(final FetchResult result,
			final RevWalk walk) throws TransportException {
		final Repository db = transport.local;
		for (final Ref ref : db.getAllRefs().values()) {
			final String refname = ref.getName();
			for (final RefSpec spec : toFetch) {
				if (spec.matchDestination(refname)) {
					final RefSpec s = spec.expandFromDestination(refname);
					if (result.getAdvertisedRef(s.getSource()) == null) {
						deleteTrackingRef(result, db, walk, s, ref);
					}
				}
			}
		}
	}

	private void deleteTrackingRef(final FetchResult result,
			final Repository db, final RevWalk walk, final RefSpec spec,
			final Ref localRef) throws TransportException {
		final String name = localRef.getName();
		try {
			final TrackingRefUpdate u = new TrackingRefUpdate(db, name, spec
					.getSource(), true, ObjectId.zeroId(), "deleted");
			result.add(u);
			if (transport.isDryRun()){
				return;
			}
			u.delete(walk);
			switch (u.getResult()) {
			case NEW:
			case NO_CHANGE:
			case FAST_FORWARD:
			case FORCED:
				break;
			default:
				throw new TransportException(transport.getURI(), MessageFormat.format(
						JGitText.get().cannotDeleteStaleTrackingRef2, name, u.getResult().name()));
			}
		} catch (IOException e) {
			throw new TransportException(transport.getURI(), MessageFormat.format(
					JGitText.get().cannotDeleteStaleTrackingRef, name), e);
		}
	}

	private static boolean isTag(final Ref r) {
		return isTag(r.getName());
	}

	private static boolean isTag(final String name) {
		return name.startsWith(Constants.R_TAGS);
	}
}
