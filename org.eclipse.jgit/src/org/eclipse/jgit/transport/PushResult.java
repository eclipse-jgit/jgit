/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com> and others
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

/**
 * Result of push operation to the remote repository. Holding information of
 * {@link org.eclipse.jgit.transport.OperationResult} and remote refs updates
 * status.
 *
 * @see Transport#push(org.eclipse.jgit.lib.ProgressMonitor, Collection)
 */
public class PushResult extends OperationResult {
	private Map<String, RemoteRefUpdate> remoteUpdates = Collections.emptyMap();

	/**
	 * Get status of remote refs updates. Together with
	 * {@link #getAdvertisedRefs()} it provides full description/status of each
	 * ref update.
	 * <p>
	 * Returned collection is not sorted in any order.
	 * </p>
	 *
	 * @return collection of remote refs updates
	 */
	public Collection<RemoteRefUpdate> getRemoteUpdates() {
		return Collections.unmodifiableCollection(remoteUpdates.values());
	}

	/**
	 * Get status of specific remote ref update by remote ref name. Together
	 * with {@link #getAdvertisedRef(String)} it provide full description/status
	 * of this ref update.
	 *
	 * @param refName
	 *            remote ref name
	 * @return status of remote ref update
	 */
	public RemoteRefUpdate getRemoteUpdate(String refName) {
		return remoteUpdates.get(refName);
	}

	void setRemoteUpdates(
			final Map<String, RemoteRefUpdate> remoteUpdates) {
		this.remoteUpdates = remoteUpdates;
	}
}
