/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
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

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

/**
 * Single shot fetch from a streamed Git bundle.
 * <p>
 * The bundle is read from an unbuffered input stream, which limits the
 * transport to opening at most one FetchConnection before needing to recreate
 * the transport instance.
 */
public class TransportBundleStream extends Transport implements TransportBundle {
	private InputStream src;

	/**
	 * Create a new transport to fetch objects from a streamed bundle.
	 * <p>
	 * The stream can be unbuffered (buffering is automatically provided
	 * internally to smooth out short reads) and unpositionable (the stream is
	 * read from only once, sequentially).
	 * <p>
	 * When the FetchConnection or the this instance is closed the supplied
	 * input stream is also automatically closed. This frees callers from
	 * needing to keep track of the supplied stream.
	 *
	 * @param db
	 *            repository the fetched objects will be loaded into.
	 * @param uri
	 *            symbolic name of the source of the stream. The URI can
	 *            reference a non-existent resource. It is used only for
	 *            exception reporting.
	 * @param in
	 *            the stream to read the bundle from.
	 */
	public TransportBundleStream(final Repository db, final URIish uri,
			final InputStream in) {
		super(db, uri);
		src = in;
	}

	/** {@inheritDoc} */
	@Override
	public FetchConnection openFetch() throws TransportException {
		if (src == null)
			throw new TransportException(uri, JGitText.get().onlyOneFetchSupported);
		try {
			return new BundleFetchConnection(this, src);
		} finally {
			src = null;
		}
	}

	/** {@inheritDoc} */
	@Override
	public PushConnection openPush() throws NotSupportedException {
		throw new NotSupportedException(
				JGitText.get().pushIsNotSupportedForBundleTransport);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (src != null) {
			try {
				src.close();
			} catch (IOException err) {
				// Ignore a close error.
			} finally {
				src = null;
			}
		}
	}
}
