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
				} catch (ServiceNotEnabledException | ServiceNotAuthorizedException e) {
					// Ignored. Client cannot use this repository.
				}
                            // Ignored. Client cannot use this repository.
                             catch (IOException err) {
					// Client side of the pipes should report the problem.
					err.printStackTrace();
				} catch (RuntimeException err) {
					// Client side will notice we went away, and report.
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
