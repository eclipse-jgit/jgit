/*
 * Copyright (C) 2008-2009, Google Inc. and others
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
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Active network client of {@link org.eclipse.jgit.transport.Daemon}.
 */
public class DaemonClient {
	private final Daemon daemon;

	private InetAddress peer;

	private InputStream rawIn;

	private OutputStream rawOut;

	DaemonClient(Daemon d) {
		daemon = d;
	}

	void setRemoteAddress(InetAddress ia) {
		peer = ia;
	}

	/**
	 * Get the daemon which spawned this client.
	 *
	 * @return the daemon which spawned this client.
	 */
	public Daemon getDaemon() {
		return daemon;
	}

	/**
	 * Get Internet address of the remote client.
	 *
	 * @return Internet address of the remote client.
	 */
	public InetAddress getRemoteAddress() {
		return peer;
	}

	/**
	 * Get input stream to read from the connected client.
	 *
	 * @return input stream to read from the connected client.
	 */
	public InputStream getInputStream() {
		return rawIn;
	}

	/**
	 * Get output stream to send data to the connected client.
	 *
	 * @return output stream to send data to the connected client.
	 */
	public OutputStream getOutputStream() {
		return rawOut;
	}

	void execute(Socket sock) throws IOException,
			ServiceNotEnabledException, ServiceNotAuthorizedException {
		rawIn = new BufferedInputStream(sock.getInputStream());
		rawOut = new BufferedOutputStream(sock.getOutputStream());

		if (0 < daemon.getTimeout())
			sock.setSoTimeout(daemon.getTimeout() * 1000);
		String cmd = new PacketLineIn(rawIn).readStringRaw();

		Collection<String> extraParameters = null;

		int nulnul = cmd.indexOf("\0\0"); //$NON-NLS-1$
		if (nulnul != -1) {
			extraParameters = Arrays.asList(cmd.substring(nulnul + 2).split("\0")); //$NON-NLS-1$
		}

		final int nul = cmd.indexOf('\0');
		if (nul >= 0) {
			// Newer clients hide a "host" header behind this byte.
			// Currently we don't use it for anything, so we ignore
			// this portion of the command.
			//
			cmd = cmd.substring(0, nul);
		}

		final DaemonService srv = getDaemon().matchService(cmd);
		if (srv == null)
			return;
		sock.setSoTimeout(0);
		srv.execute(this, cmd, extraParameters);
	}
}
