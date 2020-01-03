/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;

/**
 * Base helper class for fetch connection implementations. Provides some common
 * typical structures and methods used during fetch connection.
 * <p>
 * Implementors of fetch over pack-based protocols should consider using
 * {@link BasePackFetchConnection} instead.
 * </p>
 */
abstract class BaseFetchConnection extends BaseConnection implements
		FetchConnection {
	/** {@inheritDoc} */
	@Override
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		fetch(monitor, want, have, null);
	}

	/** {@inheritDoc} */
	@Override
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have,
			OutputStream out) throws TransportException {
		markStartedOperation();
		doFetch(monitor, want, have);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Default implementation of {@link FetchConnection#didFetchIncludeTags()} -
	 * returning false.
	 */
	@Override
	public boolean didFetchIncludeTags() {
		return false;
	}

	/**
	 * Implementation of {@link #fetch(ProgressMonitor, Collection, Set)}
	 * without checking for multiple fetch.
	 *
	 * @param monitor
	 *            as in {@link #fetch(ProgressMonitor, Collection, Set)}
	 * @param want
	 *            as in {@link #fetch(ProgressMonitor, Collection, Set)}
	 * @param have
	 *            as in {@link #fetch(ProgressMonitor, Collection, Set)}
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             as in {@link #fetch(ProgressMonitor, Collection, Set)}, but
	 *             implementation doesn't have to care about multiple
	 *             {@link #fetch(ProgressMonitor, Collection, Set)} calls, as it
	 *             is checked in this class.
	 */
	protected abstract void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException;
}
