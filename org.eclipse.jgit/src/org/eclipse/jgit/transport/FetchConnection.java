/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.PackLock;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;

/**
 * Lists known refs from the remote and copies objects of selected refs.
 * <p>
 * A fetch connection typically connects to the <code>git-upload-pack</code>
 * service running where the remote repository is stored. This provides a
 * one-way object transfer service to copy objects from the remote repository
 * into this local repository.
 * <p>
 * Instances of a FetchConnection must be created by a
 * {@link org.eclipse.jgit.transport.Transport} that implements a specific
 * object transfer protocol that both sides of the connection understand.
 * <p>
 * FetchConnection instances are not thread safe and may be accessed by only one
 * thread at a time.
 *
 * @see Transport
 */
public interface FetchConnection extends Connection {
	/**
	 * Fetch objects we don't have but that are reachable from advertised refs.
	 * <p>
	 * Only one call per connection is allowed. Subsequent calls will result in
	 * {@link org.eclipse.jgit.errors.TransportException}.
	 * </p>
	 * <p>
	 * Implementations are free to use network connections as necessary to
	 * efficiently (for both client and server) transfer objects from the remote
	 * repository into this repository. When possible implementations should
	 * avoid replacing/overwriting/duplicating an object already available in
	 * the local destination repository. Locally available objects and packs
	 * should always be preferred over remotely available objects and packs.
	 * {@link org.eclipse.jgit.transport.Transport#isFetchThin()} should be
	 * honored if applicable.
	 * </p>
	 *
	 * @param monitor
	 *            progress monitor to inform the end-user about the amount of
	 *            work completed, or to indicate cancellation. Implementations
	 *            should poll the monitor at regular intervals to look for
	 *            cancellation requests from the user.
	 * @param want
	 *            one or more refs advertised by this connection that the caller
	 *            wants to store locally.
	 * @param have
	 *            additional objects known to exist in the destination
	 *            repository, especially if they aren't yet reachable by the ref
	 *            database. Connections should take this set as an addition to
	 *            what is reachable through all Refs, not in replace of it.
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             objects could not be copied due to a network failure,
	 *             protocol error, or error on remote side, or connection was
	 *             already used for fetch.
	 */
	void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException;

	/**
	 * Fetch objects we don't have but that are reachable from advertised refs.
	 * <p>
	 * Only one call per connection is allowed. Subsequent calls will result in
	 * {@link org.eclipse.jgit.errors.TransportException}.
	 * </p>
	 * <p>
	 * Implementations are free to use network connections as necessary to
	 * efficiently (for both client and server) transfer objects from the remote
	 * repository into this repository. When possible implementations should
	 * avoid replacing/overwriting/duplicating an object already available in
	 * the local destination repository. Locally available objects and packs
	 * should always be preferred over remotely available objects and packs.
	 * {@link org.eclipse.jgit.transport.Transport#isFetchThin()} should be
	 * honored if applicable.
	 * </p>
	 *
	 * @param monitor
	 *            progress monitor to inform the end-user about the amount of
	 *            work completed, or to indicate cancellation. Implementations
	 *            should poll the monitor at regular intervals to look for
	 *            cancellation requests from the user.
	 * @param want
	 *            one or more refs advertised by this connection that the caller
	 *            wants to store locally.
	 * @param have
	 *            additional objects known to exist in the destination
	 *            repository, especially if they aren't yet reachable by the ref
	 *            database. Connections should take this set as an addition to
	 *            what is reachable through all Refs, not in replace of it.
	 * @param out
	 *            OutputStream to write sideband messages to
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             objects could not be copied due to a network failure,
	 *             protocol error, or error on remote side, or connection was
	 *             already used for fetch.
	 * @since 3.0
	 */
	void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have,
			OutputStream out) throws TransportException;

	/**
	 * Did the last {@link #fetch(ProgressMonitor, Collection, Set)} get tags?
	 * <p>
	 * Some Git aware transports are able to implicitly grab an annotated tag if
	 * {@link org.eclipse.jgit.transport.TagOpt#AUTO_FOLLOW} or
	 * {@link org.eclipse.jgit.transport.TagOpt#FETCH_TAGS} was selected and the
	 * object the tag peels to (references) was transferred as part of the last
	 * {@link #fetch(ProgressMonitor, Collection, Set)} call. If it is possible
	 * for such tags to have been included in the transfer this method returns
	 * true, allowing the caller to attempt tag discovery.
	 * <p>
	 * By returning only true/false (and not the actual list of tags obtained)
	 * the transport itself does not need to be aware of whether or not tags
	 * were included in the transfer.
	 *
	 * @return true if the last fetch call implicitly included tag objects;
	 *         false if tags were not implicitly obtained.
	 */
	boolean didFetchIncludeTags();

	/**
	 * Did the last {@link #fetch(ProgressMonitor, Collection, Set)} validate
	 * graph?
	 * <p>
	 * Some transports walk the object graph on the client side, with the client
	 * looking for what objects it is missing and requesting them individually
	 * from the remote peer. By virtue of completing the fetch call the client
	 * implicitly tested the object connectivity, as every object in the graph
	 * was either already local or was requested successfully from the peer. In
	 * such transports this method returns true.
	 * <p>
	 * Some transports assume the remote peer knows the Git object graph and is
	 * able to supply a fully connected graph to the client (although it may
	 * only be transferring the parts the client does not yet have). Its faster
	 * to assume such remote peers are well behaved and send the correct
	 * response to the client. In such transports this method returns false.
	 *
	 * @return true if the last fetch had to perform a connectivity check on the
	 *         client side in order to succeed; false if the last fetch assumed
	 *         the remote peer supplied a complete graph.
	 */
	boolean didFetchTestConnectivity();

	/**
	 * Set the lock message used when holding a pack out of garbage collection.
	 * <p>
	 * Callers that set a lock message <b>must</b> ensure they call
	 * {@link #getPackLocks()} after
	 * {@link #fetch(ProgressMonitor, Collection, Set)}, even if an exception
	 * was thrown, and release the locks that are held.
	 *
	 * @param message message to use when holding a pack in place.
	 */
	void setPackLockMessage(String message);

	/**
	 * All locks created by the last
	 * {@link #fetch(ProgressMonitor, Collection, Set)} call.
	 *
	 * @return collection (possibly empty) of locks created by the last call to
	 *         fetch. The caller must release these after refs are updated in
	 *         order to safely permit garbage collection.
	 */
	Collection<PackLock> getPackLocks();
}
