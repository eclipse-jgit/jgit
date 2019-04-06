/*
 * Copyright (C) 2015, Google Inc.
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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

class InternalPushConnection<C> extends BasePackPushConnection {
	private Thread worker;

	/**
	 * Constructor for InternalPushConnection.
	 *
	 * @param transport
	 *            a {@link org.eclipse.jgit.transport.PackTransport}
	 * @param receivePackFactory
	 *            a
	 *            {@link org.eclipse.jgit.transport.resolver.ReceivePackFactory}
	 * @param req
	 *            a request
	 * @param remote
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             if any.
	 */
	public InternalPushConnection(PackTransport transport,
			final ReceivePackFactory<C> receivePackFactory,
			final C req, final Repository remote) throws TransportException {
		super(transport);

		final PipedInputStream in_r;
		final PipedOutputStream in_w;

		final PipedInputStream out_r;
		final PipedOutputStream out_w;
		try {
			in_r = new PipedInputStream();
			in_w = new PipedOutputStream(in_r);

			out_r = new PipedInputStream();
			out_w = new PipedOutputStream(out_r);
		} catch (IOException err) {
			remote.close();
			throw new TransportException(uri, JGitText.get().cannotConnectPipes, err);
		}

		worker = new Thread("JGit-Receive-Pack") { //$NON-NLS-1$
			@Override
			public void run() {
				try {
					final ReceivePack rp = receivePackFactory.create(req, remote);
					rp.receive(out_r, in_w, System.err);
				} catch (ServiceNotEnabledException | ServiceNotAuthorizedException e) {
					// Ignored. Client cannot use this repository.
				}
                            // Ignored. Client cannot use this repository.
                             catch (IOException e) {
					// Since the InternalPushConnection
					// is used in tests, we want to avoid hiding exceptions
					// because they can point to programming errors on the server
					// side. By rethrowing, the default handler will dump it
					// to stderr.
					throw new UncheckedIOException(e);
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

		if (worker != null) {
			try {
				worker.join();
			} catch (InterruptedException ie) {
				// Stop waiting and return anyway.
			} finally {
				worker = null;
			}
		}
	}
}
