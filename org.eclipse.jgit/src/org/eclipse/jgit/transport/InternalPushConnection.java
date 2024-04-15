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
	 * Flag to synchronize the "JGit-Receive-Pack" thread with the main calling
	 * thread.
	 */
	private final Boolean[] synchronizationPointReached = {false};

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
				} catch (ServiceNotEnabledException
						| ServiceNotAuthorizedException e) {
					// Ignored. Client cannot use this repository.
				} catch (IOException e) {
					// Since the InternalPushConnection is used in tests, we
					// want to avoid hiding exceptions because they can point to
					// programming errors on the server side. By rethrowing, the
					// default handler will dump it to stderr.
					throw new UncheckedIOException(e);
				} finally {
					synchronized (synchronizationPointReached) {
						while (!synchronizationPointReached[0])
							try {
								// We need to wait here so as not to close out_r until the
								// main calling thread executes the readStatusReport() method.
								// If out_r is closed before the readStatusReport() method is
								// executed, then the main calling thread may not receive the
								// message sent by this thread and the exception corresponding
								// to this message will not be thrown by the main calling thread
								// in that case. IOException "Pipe closed" will be thrown by
								// the main calling thread instead.
								synchronizationPointReached.wait();
							} catch (InterruptedException e) {
								// Ignore spurious wakeups
							}
					}
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

	@Override
	public void close() {
		super.close();

		if (worker != null) {
			synchronizationPointReached[0] = true;
			synchronized (synchronizationPointReached) {
				// The "JGit-Receive-Pack" thread could be waiting for this thread
				// so as not to close out_r until we get here.
				// Let's wake it up now.
				synchronizationPointReached.notifyAll();
			}

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
