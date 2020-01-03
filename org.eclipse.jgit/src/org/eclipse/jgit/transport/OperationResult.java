/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.jgit.lib.Ref;

/**
 * Class holding result of operation on remote repository. This includes refs
 * advertised by remote repo and local tracking refs updates.
 */
public abstract class OperationResult {

	Map<String, Ref> advertisedRefs = Collections.emptyMap();

	URIish uri;

	final SortedMap<String, TrackingRefUpdate> updates = new TreeMap<>();

	StringBuilder messageBuffer;

	String peerUserAgent;

	/**
	 * Get the URI this result came from.
	 * <p>
	 * Each transport instance connects to at most one URI at any point in time.
	 *
	 * @return the URI describing the location of the remote repository.
	 */
	public URIish getURI() {
		return uri;
	}

	/**
	 * Get the complete list of refs advertised by the remote.
	 * <p>
	 * The returned refs may appear in any order. If the caller needs these to
	 * be sorted, they should be copied into a new array or List and then sorted
	 * by the caller as necessary.
	 *
	 * @return available/advertised refs. Never null. Not modifiable. The
	 *         collection can be empty if the remote side has no refs (it is an
	 *         empty/newly created repository).
	 */
	public Collection<Ref> getAdvertisedRefs() {
		return Collections.unmodifiableCollection(advertisedRefs.values());
	}

	/**
	 * Get a single advertised ref by name.
	 * <p>
	 * The name supplied should be valid ref name. To get a peeled value for a
	 * ref (aka <code>refs/tags/v1.0^{}</code>) use the base name (without
	 * the <code>^{}</code> suffix) and look at the peeled object id.
	 *
	 * @param name
	 *            name of the ref to obtain.
	 * @return the requested ref; null if the remote did not advertise this ref.
	 */
	public final Ref getAdvertisedRef(String name) {
		return advertisedRefs.get(name);
	}

	/**
	 * Get the status of all local tracking refs that were updated.
	 *
	 * @return unmodifiable collection of local updates. Never null. Empty if
	 *         there were no local tracking refs updated.
	 */
	public Collection<TrackingRefUpdate> getTrackingRefUpdates() {
		return Collections.unmodifiableCollection(updates.values());
	}

	/**
	 * Get the status for a specific local tracking ref update.
	 *
	 * @param localName
	 *            name of the local ref (e.g. "refs/remotes/origin/master").
	 * @return status of the local ref; null if this local ref was not touched
	 *         during this operation.
	 */
	public TrackingRefUpdate getTrackingRefUpdate(String localName) {
		return updates.get(localName);
	}

	void setAdvertisedRefs(URIish u, Map<String, Ref> ar) {
		uri = u;
		advertisedRefs = ar;
	}

	void add(TrackingRefUpdate u) {
		updates.put(u.getLocalName(), u);
	}

	/**
	 * Get the additional messages, if any, returned by the remote process.
	 * <p>
	 * These messages are most likely informational or error messages, sent by
	 * the remote peer, to help the end-user correct any problems that may have
	 * prevented the operation from completing successfully. Application UIs
	 * should try to show these in an appropriate context.
	 *
	 * @return the messages returned by the remote, most likely terminated by a
	 *         newline (LF) character. The empty string is returned if the
	 *         remote produced no additional messages.
	 */
	public String getMessages() {
		return messageBuffer != null ? messageBuffer.toString() : ""; //$NON-NLS-1$
	}

	void addMessages(String msg) {
		if (msg != null && msg.length() > 0) {
			if (messageBuffer == null)
				messageBuffer = new StringBuilder();
			messageBuffer.append(msg);
			if (!msg.endsWith("\n")) //$NON-NLS-1$
				messageBuffer.append('\n');
		}
	}

	/**
	 * Get the user agent advertised by the peer server, if available.
	 *
	 * @return advertised user agent, e.g. {@code "JGit/4.0"}. Null if the peer
	 *         did not advertise version information.
	 * @since 4.0
	 */
	public String getPeerUserAgent() {
		return peerUserAgent;
	}
}
