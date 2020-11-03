/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

/**
 * Transport through a git-daemon waiting for anonymous TCP connections.
 * <p>
 * This transport supports the <code>git://</code> protocol, usually run on
 * the IANA registered port 9418. It is a popular means for distributing open
 * source projects, as there are no authentication or authorization overheads.
 */
class TransportGitAnon extends TcpTransport implements PackTransport {
	static final int GIT_PORT = Daemon.DEFAULT_PORT;

	static final TransportProtocol PROTO_GIT = new TransportProtocol() {
		@Override
		public String getName() {
			return JGitText.get().transportProtoGitAnon;
		}

		@Override
		public Set<String> getSchemes() {
			return Collections.singleton("git"); //$NON-NLS-1$
		}

		@Override
		public Set<URIishField> getRequiredFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.HOST,
					URIishField.PATH));
		}

		@Override
		public Set<URIishField> getOptionalFields() {
			return Collections.unmodifiableSet(EnumSet.of(URIishField.PORT));
		}

		@Override
		public int getDefaultPort() {
			return GIT_PORT;
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException {
			return new TransportGitAnon(local, uri);
		}

		@Override
		public Transport open(URIish uri) throws NotSupportedException, TransportException {
			return new TransportGitAnon(uri);
		}
	};

	TransportGitAnon(Repository local, URIish uri) {
		super(local, uri);
	}

	TransportGitAnon(URIish uri) {
		super(uri);
	}

	/** {@inheritDoc} */
	@Override
	public FetchConnection openFetch() throws TransportException {
		return new TcpFetchConnection();
	}

	/** {@inheritDoc} */
	@Override
	public PushConnection openPush() throws TransportException {
		return new TcpPushConnection();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		// Resources must be established per-connection.
	}

	Socket openConnection() throws TransportException {
		final int tms = getTimeout() > 0 ? getTimeout() * 1000 : 0;
		final int port = uri.getPort() > 0 ? uri.getPort() : GIT_PORT;
		@SuppressWarnings("resource") // Closed by the caller
		final Socket s = new Socket();
		try {
			final InetAddress host = InetAddress.getByName(uri.getHost());
			s.bind(null);
			s.connect(new InetSocketAddress(host, port), tms);
		} catch (IOException c) {
			try {
				s.close();
			} catch (IOException closeErr) {
				// ignore a failure during close, we're already failing
			}
			if (c instanceof UnknownHostException)
				throw new TransportException(uri, JGitText.get().unknownHost);
			if (c instanceof ConnectException)
				throw new TransportException(uri, c.getMessage());
			throw new TransportException(uri, c.getMessage(), c);
		}
		return s;
	}

	void service(String name, PacketLineOut pckOut)
			throws IOException {
		final StringBuilder cmd = new StringBuilder();
		cmd.append(name);
		cmd.append(' ');
		cmd.append(uri.getPath());
		cmd.append('\0');
		cmd.append("host="); //$NON-NLS-1$
		cmd.append(uri.getHost());
		if (uri.getPort() > 0 && uri.getPort() != GIT_PORT) {
			cmd.append(":"); //$NON-NLS-1$
			cmd.append(uri.getPort());
		}
		cmd.append('\0');
		pckOut.writeString(cmd.toString());
		pckOut.flush();
	}

	class TcpFetchConnection extends BasePackFetchConnection {
		private Socket sock;

		TcpFetchConnection() throws TransportException {
			super(TransportGitAnon.this);
			sock = openConnection();
			try {
				InputStream sIn = sock.getInputStream();
				OutputStream sOut = sock.getOutputStream();

				sIn = new BufferedInputStream(sIn);
				sOut = new BufferedOutputStream(sOut);

				init(sIn, sOut);
				service("git-upload-pack", pckOut); //$NON-NLS-1$
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						JGitText.get().remoteHungUpUnexpectedly, err);
			}
			readAdvertisedRefs();
		}

		@Override
		public void close() {
			super.close();

			if (sock != null) {
				try {
					sock.close();
				} catch (IOException err) {
					// Ignore errors during close.
				} finally {
					sock = null;
				}
			}
		}
	}

	class TcpPushConnection extends BasePackPushConnection {
		private Socket sock;

		TcpPushConnection() throws TransportException {
			super(TransportGitAnon.this);
			sock = openConnection();
			try {
				InputStream sIn = sock.getInputStream();
				OutputStream sOut = sock.getOutputStream();

				sIn = new BufferedInputStream(sIn);
				sOut = new BufferedOutputStream(sOut);

				init(sIn, sOut);
				service("git-receive-pack", pckOut); //$NON-NLS-1$
			} catch (IOException err) {
				close();
				throw new TransportException(uri,
						JGitText.get().remoteHungUpUnexpectedly, err);
			}
			readAdvertisedRefs();
		}

		@Override
		public void close() {
			super.close();

			if (sock != null) {
				try {
					sock.close();
				} catch (IOException err) {
					// Ignore errors during close.
				} finally {
					sock = null;
				}
			}
		}
	}
}
