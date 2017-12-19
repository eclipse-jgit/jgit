/*
 * Copyright (C) 2008-2009, Google Inc.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/**
 * Basic daemon for the anonymous <code>git://</code> transport protocol.
 */
public class Daemon {
	/** 9418: IANA assigned port number for Git. */
	public static final int DEFAULT_PORT = 9418;

	private static final int BACKLOG = 5;

	private InetSocketAddress myAddress;

	private final DaemonService[] services;

	private final ThreadGroup processors;

	private Acceptor acceptThread;

	private int timeout;

	private PackConfig packConfig;

	private volatile RepositoryResolver<DaemonClient> repositoryResolver;

	volatile UploadPackFactory<DaemonClient> uploadPackFactory;

	volatile ReceivePackFactory<DaemonClient> receivePackFactory;

	/**
	 * Configure a daemon to listen on any available network port.
	 */
	public Daemon() {
		this(null);
	}

	/**
	 * Configure a new daemon for the specified network address.
	 *
	 * @param addr
	 *            address to listen for connections on. If null, any available
	 *            port will be chosen on all network interfaces.
	 */
	@SuppressWarnings("unchecked")
	public Daemon(final InetSocketAddress addr) {
		myAddress = addr;
		processors = new ThreadGroup("Git-Daemon"); //$NON-NLS-1$

		repositoryResolver = (RepositoryResolver<DaemonClient>) RepositoryResolver.NONE;

		uploadPackFactory = new UploadPackFactory<DaemonClient>() {
			@Override
			public UploadPack create(DaemonClient req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				UploadPack up = new UploadPack(db);
				up.setTimeout(getTimeout());
				up.setPackConfig(getPackConfig());
				return up;
			}
		};

		receivePackFactory = new ReceivePackFactory<DaemonClient>() {
			@Override
			public ReceivePack create(DaemonClient req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				ReceivePack rp = new ReceivePack(db);

				InetAddress peer = req.getRemoteAddress();
				String host = peer.getCanonicalHostName();
				if (host == null)
					host = peer.getHostAddress();
				String name = "anonymous"; //$NON-NLS-1$
				String email = name + "@" + host; //$NON-NLS-1$
				rp.setRefLogIdent(new PersonIdent(name, email));
				rp.setTimeout(getTimeout());

				return rp;
			}
		};

		services = new DaemonService[] {
				new DaemonService("upload-pack", "uploadpack") { //$NON-NLS-1$ //$NON-NLS-2$
					{
						setEnabled(true);
					}

					@Override
					protected void execute(final DaemonClient dc,
							final Repository db) throws IOException,
							ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = uploadPackFactory.create(dc, db);
						InputStream in = dc.getInputStream();
						OutputStream out = dc.getOutputStream();
						up.upload(in, out, null);
					}
				}, new DaemonService("receive-pack", "receivepack") { //$NON-NLS-1$ //$NON-NLS-2$
					{
						setEnabled(false);
					}

					@Override
					protected void execute(final DaemonClient dc,
							final Repository db) throws IOException,
							ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						ReceivePack rp = receivePackFactory.create(dc, db);
						InputStream in = dc.getInputStream();
						OutputStream out = dc.getOutputStream();
						rp.receive(in, out, null);
					}
				} };
	}

	/**
	 * Get the address connections are received on.
	 *
	 * @return the address connections are received on.
	 */
	public synchronized InetSocketAddress getAddress() {
		return myAddress;
	}

	/**
	 * Lookup a supported service so it can be reconfigured.
	 *
	 * @param name
	 *            name of the service; e.g. "receive-pack"/"git-receive-pack" or
	 *            "upload-pack"/"git-upload-pack".
	 * @return the service; null if this daemon implementation doesn't support
	 *         the requested service type.
	 */
	public synchronized DaemonService getService(String name) {
		if (!name.startsWith("git-")) //$NON-NLS-1$
			name = "git-" + name; //$NON-NLS-1$
		for (final DaemonService s : services) {
			if (s.getCommandName().equals(name))
				return s;
		}
		return null;
	}

	/**
	 * Get timeout (in seconds) before aborting an IO operation.
	 *
	 * @return timeout (in seconds) before aborting an IO operation.
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with the
	 *            connected client.
	 */
	public void setTimeout(final int seconds) {
		timeout = seconds;
	}

	/**
	 * Get configuration controlling packing, may be null.
	 *
	 * @return configuration controlling packing, may be null.
	 */
	public PackConfig getPackConfig() {
		return packConfig;
	}

	/**
	 * Set the configuration used by the pack generator.
	 *
	 * @param pc
	 *            configuration controlling packing parameters. If null the
	 *            source repository's settings will be used.
	 */
	public void setPackConfig(PackConfig pc) {
		this.packConfig = pc;
	}

	/**
	 * Set the resolver used to locate a repository by name.
	 *
	 * @param resolver
	 *            the resolver instance.
	 */
	public void setRepositoryResolver(RepositoryResolver<DaemonClient> resolver) {
		repositoryResolver = resolver;
	}

	/**
	 * Set the factory to construct and configure per-request UploadPack.
	 *
	 * @param factory
	 *            the factory. If null upload-pack is disabled.
	 */
	@SuppressWarnings("unchecked")
	public void setUploadPackFactory(UploadPackFactory<DaemonClient> factory) {
		if (factory != null)
			uploadPackFactory = factory;
		else
			uploadPackFactory = (UploadPackFactory<DaemonClient>) UploadPackFactory.DISABLED;
	}

	/**
	 * Get the factory used to construct per-request ReceivePack.
	 *
	 * @return the factory.
	 * @since 4.3
	 */
	public ReceivePackFactory<DaemonClient> getReceivePackFactory() {
		return receivePackFactory;
	}

	/**
	 * Set the factory to construct and configure per-request ReceivePack.
	 *
	 * @param factory
	 *            the factory. If null receive-pack is disabled.
	 */
	@SuppressWarnings("unchecked")
	public void setReceivePackFactory(ReceivePackFactory<DaemonClient> factory) {
		if (factory != null)
			receivePackFactory = factory;
		else
			receivePackFactory = (ReceivePackFactory<DaemonClient>) ReceivePackFactory.DISABLED;
	}

	private class Acceptor extends Thread {

		private final ServerSocket listenSocket;

		private final AtomicBoolean running = new AtomicBoolean(true);

		public Acceptor(ThreadGroup group, String name, ServerSocket socket) {
			super(group, name);
			this.listenSocket = socket;
		}

		@Override
		public void run() {
			setUncaughtExceptionHandler((thread, throwable) -> terminate());
			while (isRunning()) {
				try {
					startClient(listenSocket.accept());
				} catch (SocketException e) {
					// Test again to see if we should keep accepting.
				} catch (IOException e) {
					break;
				}
			}

			terminate();
		}

		private void terminate() {
			try {
				shutDown();
			} finally {
				clearThread();
			}
		}

		public boolean isRunning() {
			return running.get();
		}

		public void shutDown() {
			running.set(false);
			try {
				listenSocket.close();
			} catch (IOException err) {
				//
			}
		}

	}

	/**
	 * Start this daemon on a background thread.
	 *
	 * @throws java.io.IOException
	 *             the server socket could not be opened.
	 * @throws java.lang.IllegalStateException
	 *             the daemon is already running.
	 */
	public synchronized void start() throws IOException {
		if (acceptThread != null) {
			throw new IllegalStateException(JGitText.get().daemonAlreadyRunning);
		}
		ServerSocket socket = new ServerSocket();
		socket.setReuseAddress(true);
		if (myAddress != null) {
			socket.bind(myAddress, BACKLOG);
		} else {
			socket.bind(new InetSocketAddress((InetAddress) null, 0), BACKLOG);
		}
		myAddress = (InetSocketAddress) socket.getLocalSocketAddress();

		acceptThread = new Acceptor(processors, "Git-Daemon-Accept", socket); //$NON-NLS-1$
		acceptThread.start();
	}

	private synchronized void clearThread() {
		acceptThread = null;
	}

	/**
	 * Whether this daemon is receiving connections.
	 *
	 * @return {@code true} if this daemon is receiving connections.
	 */
	public synchronized boolean isRunning() {
		return acceptThread != null && acceptThread.isRunning();
	}

	/**
	 * Stop this daemon.
	 */
	public synchronized void stop() {
		if (acceptThread != null) {
			acceptThread.shutDown();
		}
	}

	/**
	 * Stops this daemon and waits until it's acceptor thread has finished.
	 *
	 * @throws java.lang.InterruptedException
	 *             if waiting for the acceptor thread is interrupted
	 * @since 4.9
	 */
	public void stopAndWait() throws InterruptedException {
		Thread acceptor = null;
		synchronized (this) {
			acceptor = acceptThread;
			stop();
		}
		if (acceptor != null) {
			acceptor.join();
		}
	}

	void startClient(final Socket s) {
		final DaemonClient dc = new DaemonClient(this);

		final SocketAddress peer = s.getRemoteSocketAddress();
		if (peer instanceof InetSocketAddress)
			dc.setRemoteAddress(((InetSocketAddress) peer).getAddress());

		new Thread(processors, "Git-Daemon-Client " + peer.toString()) { //$NON-NLS-1$
			@Override
			public void run() {
				try {
					dc.execute(s);
				} catch (ServiceNotEnabledException e) {
					// Ignored. Client cannot use this repository.
				} catch (ServiceNotAuthorizedException e) {
					// Ignored. Client cannot use this repository.
				} catch (IOException e) {
					// Ignore unexpected IO exceptions from clients
				} finally {
					try {
						s.getInputStream().close();
					} catch (IOException e) {
						// Ignore close exceptions
					}
					try {
						s.getOutputStream().close();
					} catch (IOException e) {
						// Ignore close exceptions
					}
				}
			}
		}.start();
	}

	synchronized DaemonService matchService(final String cmd) {
		for (final DaemonService d : services) {
			if (d.handles(cmd))
				return d;
		}
		return null;
	}

	Repository openRepository(DaemonClient client, String name)
			throws ServiceMayNotContinueException {
		// Assume any attempt to use \ was by a Windows client
		// and correct to the more typical / used in Git URIs.
		//
		name = name.replace('\\', '/');

		// git://thishost/path should always be name="/path" here
		//
		if (!name.startsWith("/")) //$NON-NLS-1$
			return null;

		try {
			return repositoryResolver.open(client, name.substring(1));
		} catch (RepositoryNotFoundException e) {
			// null signals it "wasn't found", which is all that is suitable
			// for the remote client to know.
			return null;
		} catch (ServiceNotAuthorizedException e) {
			// null signals it "wasn't found", which is all that is suitable
			// for the remote client to know.
			return null;
		} catch (ServiceNotEnabledException e) {
			// null signals it "wasn't found", which is all that is suitable
			// for the remote client to know.
			return null;
		}
	}
}
