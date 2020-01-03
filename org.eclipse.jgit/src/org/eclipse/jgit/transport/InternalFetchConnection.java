/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

class InternalFetchConnection<C> extends BasePackFetchConnection {
	private Thread worker;

	/**
	 * Constructor for InternalFetchConnection.
	 *
	 * @param transport
	 *            a {@link org.eclipse.jgit.transport.PackTransport}
	 * @param uploadPackFactory
	 *            a
	 *            {@link org.eclipse.jgit.transport.resolver.UploadPackFactory}
	 * @param req
	 *            request
	 * @param remote
	 *            the remote {@link org.eclipse.jgit.lib.Repository}
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             if any.
	 */
	public InternalFetchConnection(PackTransport transport,
			final UploadPackFactory<C> uploadPackFactory,
			final C req, final Repository remote) throws TransportException {
		super(transport);

		final PipedInputStream in_r;
		final PipedOutputStream in_w;

		final PipedInputStream out_r;
		final PipedOutputStream out_w;
		try {
			in_r = new PipedInputStream();
			in_w = new PipedOutputStream(in_r);

			out_r = new PipedInputStream() {
				// The client (BasePackFetchConnection) can write
				// a huge burst before it reads again. We need to
				// force the buffer to be big enough, otherwise it
				// will deadlock both threads.
				{
					buffer = new byte[MIN_CLIENT_BUFFER];
				}
			};
			out_w = new PipedOutputStream(out_r);
		} catch (IOException err) {
			remote.close();
			throw new TransportException(uri, JGitText.get().cannotConnectPipes, err);
		}

		worker = new Thread("JGit-Upload-Pack") { //$NON-NLS-1$
			@Override
			public void run() {
				try {
					final UploadPack rp = uploadPackFactory.create(req, remote);
					rp.upload(out_r, in_w, null);
				} catch (ServiceNotEnabledException
						| ServiceNotAuthorizedException e) {
					// Ignored. Client cannot use this repository.
				} catch (IOException | RuntimeException err) {
					// Client side of the pipes should report the problem.
					err.printStackTrace();
				} finally {
					try {
						out_r.close();
					} catch (IOException e2) {
						// Ignore close failure, we probably crashed above.
					}

					try {
						in_w.close();
					} catch (IOException e2) {
						// Ignore close failure, we probably crashed above.
					}

					remote.close();
				}
			}
		};
		worker.start();

		init(in_r, out_w);
		readAdvertisedRefs();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		super.close();

		try {
			if (worker != null) {
				worker.join();
			}
		} catch (InterruptedException ie) {
			// Stop waiting and return anyway.
		} finally {
			worker = null;
		}
	}
}
