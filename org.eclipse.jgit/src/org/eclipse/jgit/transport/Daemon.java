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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.FS;

/** Basic daemon for the anonymous <code>git://</code> transport protocol. */
public class Daemon {
	/** 9418: IANA assigned port number for Git. */
	public static final int DEFAULT_PORT = 9418;

	private static final int BACKLOG = 5;

	private InetSocketAddress myAddress;

	private final DaemonService[] services;

	private final ThreadGroup processors;

	private volatile boolean exportAll;

	private Map<String, Repository> exports;

	private Collection<File> exportBase;

	private boolean run;

	private Thread acceptThread;

	private int timeout;

	private PackConfig packConfig;

	/** Configure a daemon to listen on any available network port. */
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
	public Daemon(final InetSocketAddress addr) {
		myAddress = addr;
		exports = new ConcurrentHashMap<String, Repository>();
		exportBase = new CopyOnWriteArrayList<File>();
		processors = new ThreadGroup("Git-Daemon");

		services = new DaemonService[] {
				new DaemonService("upload-pack", "uploadpack") {
					{
						setEnabled(true);
					}

					@Override
					protected void execute(final DaemonClient dc,
							final Repository db) throws IOException {
						final UploadPack rp = new UploadPack(db);
						final InputStream in = dc.getInputStream();
						rp.setTimeout(Daemon.this.getTimeout());
						rp.setPackConfig(Daemon.this.packConfig);
						rp.upload(in, dc.getOutputStream(), null);
					}
				}, new DaemonService("receive-pack", "receivepack") {
					{
						setEnabled(false);
					}

					@Override
					protected void execute(final DaemonClient dc,
							final Repository db) throws IOException {
						final InetAddress peer = dc.getRemoteAddress();
						String host = peer.getCanonicalHostName();
						if (host == null)
							host = peer.getHostAddress();
						final ReceivePack rp = new ReceivePack(db);
						final InputStream in = dc.getInputStream();
						final String name = "anonymous";
						final String email = name + "@" + host;
						rp.setRefLogIdent(new PersonIdent(name, email));
						rp.setTimeout(Daemon.this.getTimeout());
						rp.receive(in, dc.getOutputStream(), null);
					}
				} };
	}

	/** @return the address connections are received on. */
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
		if (!name.startsWith("git-"))
			name = "git-" + name;
		for (final DaemonService s : services) {
			if (s.getCommandName().equals(name))
				return s;
		}
		return null;
	}

	/**
	 * @return false if <code>git-daemon-export-ok</code> is required to export
	 *         a repository; true if <code>git-daemon-export-ok</code> is
	 *         ignored.
	 * @see #setExportAll(boolean)
	 */
	public boolean isExportAll() {
		return exportAll;
	}

	/**
	 * Set whether or not to export all repositories.
	 * <p>
	 * If false (the default), repositories must have a
	 * <code>git-daemon-export-ok</code> file to be accessed through this
	 * daemon.
	 * <p>
	 * If true, all repositories are available through the daemon, whether or
	 * not <code>git-daemon-export-ok</code> exists.
	 *
	 * @param export
	 */
	public void setExportAll(final boolean export) {
		exportAll = export;
	}

	/**
	 * Add a single repository to the set that is exported by this daemon.
	 * <p>
	 * The existence (or lack-thereof) of <code>git-daemon-export-ok</code> is
	 * ignored by this method. The repository is always published.
	 *
	 * @param name
	 *            name the repository will be published under.
	 * @param db
	 *            the repository instance.
	 */
	public void exportRepository(String name, final Repository db) {
		if (!name.endsWith(Constants.DOT_GIT_EXT))
			name = name + Constants.DOT_GIT_EXT;
		exports.put(name, db);
		RepositoryCache.register(db);
	}

	/**
	 * Recursively export all Git repositories within a directory.
	 *
	 * @param dir
	 *            the directory to export. This directory must not itself be a
	 *            git repository, but any directory below it which has a file
	 *            named <code>git-daemon-export-ok</code> will be published.
	 */
	public void exportDirectory(final File dir) {
		exportBase.add(dir);
	}

	/** @return timeout (in seconds) before aborting an IO operation. */
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
	 * Start this daemon on a background thread.
	 *
	 * @throws IOException
	 *             the server socket could not be opened.
	 * @throws IllegalStateException
	 *             the daemon is already running.
	 */
	public synchronized void start() throws IOException {
		if (acceptThread != null)
			throw new IllegalStateException(JGitText.get().daemonAlreadyRunning);

		final ServerSocket listenSock = new ServerSocket(
				myAddress != null ? myAddress.getPort() : 0, BACKLOG,
				myAddress != null ? myAddress.getAddress() : null);
		myAddress = (InetSocketAddress) listenSock.getLocalSocketAddress();

		run = true;
		acceptThread = new Thread(processors, "Git-Daemon-Accept") {
			public void run() {
				while (isRunning()) {
					try {
						startClient(listenSock.accept());
					} catch (InterruptedIOException e) {
						// Test again to see if we should keep accepting.
					} catch (IOException e) {
						break;
					}
				}

				try {
					listenSock.close();
				} catch (IOException err) {
					//
				} finally {
					synchronized (Daemon.this) {
						acceptThread = null;
					}
				}
			}
		};
		acceptThread.start();
	}

	/** @return true if this daemon is receiving connections. */
	public synchronized boolean isRunning() {
		return run;
	}

	/** Stop this daemon. */
	public synchronized void stop() {
		if (acceptThread != null) {
			run = false;
			acceptThread.interrupt();
		}
	}

	private void startClient(final Socket s) {
		final DaemonClient dc = new DaemonClient(this);

		final SocketAddress peer = s.getRemoteSocketAddress();
		if (peer instanceof InetSocketAddress)
			dc.setRemoteAddress(((InetSocketAddress) peer).getAddress());

		new Thread(processors, "Git-Daemon-Client " + peer.toString()) {
			public void run() {
				try {
					dc.execute(s);
				} catch (IOException e) {
					// Ignore unexpected IO exceptions from clients
					e.printStackTrace();
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

	Repository openRepository(String name) {
		// Assume any attempt to use \ was by a Windows client
		// and correct to the more typical / used in Git URIs.
		//
		name = name.replace('\\', '/');

		// git://thishost/path should always be name="/path" here
		//
		if (!name.startsWith("/"))
			return null;

		// Forbid Windows UNC paths as they might escape the base
		//
		if (name.startsWith("//"))
			return null;

		// Forbid funny paths which contain an up-reference, they
		// might be trying to escape and read /../etc/password.
		//
		if (name.contains("/../"))
			return null;
		name = name.substring(1);

		Repository db;
		db = exports.get(name.endsWith(Constants.DOT_GIT_EXT) ? name : name
				+ Constants.DOT_GIT_EXT);
		if (db != null) {
			db.incrementOpen();
			return db;
		}

		for (final File baseDir : exportBase) {
			final File gitdir = FileKey.resolve(new File(baseDir, name), FS.DETECTED);
			if (gitdir != null && canExport(gitdir))
				return openRepository(gitdir);
		}
		return null;
	}

	private static Repository openRepository(final File gitdir) {
		try {
			return RepositoryCache.open(FileKey.exact(gitdir, FS.DETECTED));
		} catch (IOException err) {
			// null signals it "wasn't found", which is all that is suitable
			// for the remote client to know.
			return null;
		}
	}

	private boolean canExport(final File d) {
		if (isExportAll()) {
			return true;
		}
		return new File(d, "git-daemon-export-ok").exists();
	}
}
