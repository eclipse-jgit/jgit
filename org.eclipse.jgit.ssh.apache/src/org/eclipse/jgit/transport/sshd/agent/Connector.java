/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd.agent;

import java.io.IOException;

/**
 * Simple interface for connecting to something and making RPC-style
 * request-reply calls.
 *
 * @since 6.0
 */
public interface Connector {

	/**
	 * Connects to an SSH agent if there is one running. If called when already
	 * connected just returns {@code true}.
	 *
	 * @return {@code true} if an SSH agent is available and connected,
	 *         {@false} if no SSH agent is available
	 * @throws IOException
	 *             if connecting to the SSH agent failed
	 */
	boolean connect() throws IOException;

	/**
	 * Disconnects from the SSH agent, if connected. Disconnecting must
	 * interrupt any currently ongoing {@link #rpc(byte, byte[])}.
	 *
	 * @throws IOException
	 *             if an error occurs
	 */
	void disconnect() throws IOException;

	/**
	 * Performs a remote call to the SSH agent and returns the result.
	 *
	 * @param command
	 *            to send
	 * @param message
	 *            to send; must have at least 5 bytes, and must have 5 unused
	 *            bytes at the front.
	 * @return the result received
	 * @throws IOException
	 *             if an error occurs
	 */
	byte[] rpc(byte command, byte[] message) throws IOException;

	/**
	 * Performs a remote call sending only a command without any parameters to
	 * the SSH agent and returns the result.
	 *
	 * @param command
	 *            to send
	 * @return the result received
	 * @throws IOException
	 *             if an error occurs
	 */
	default byte[] rpc(byte command) throws IOException {
		return rpc(command, new byte[5]);
	}
}
