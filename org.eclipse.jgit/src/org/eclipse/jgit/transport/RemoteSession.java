/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
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

import java.io.IOException;

/**
 * Create a remote "session" for executing remote commands.
 * <p>
 * Clients should subclass RemoteSession to create an alternate way for JGit to
 * execute remote commands. (The client application may already have this
 * functionality available.) Note that this class is just a factory for creating
 * remote processes. If the application already has a persistent connection to
 * the remote machine, RemoteSession may do nothing more than return a new
 * RemoteProcess when exec is called.
 */
public interface RemoteSession {
	/**
	 * Generate a new remote process to execute the given command. This function
	 * should also start execution and may need to create the streams prior to
	 * execution.
	 *
	 * @param commandName
	 *            command to execute
	 * @param timeout
	 *            timeout value, in seconds, for command execution
	 * @return a new remote process
	 * @throws java.io.IOException
	 *             may be thrown in several cases. For example, on problems
	 *             opening input or output streams or on problems connecting or
	 *             communicating with the remote host. For the latter two cases,
	 *             a TransportException may be thrown (a subclass of
	 *             java.io.IOException).
	 */
	Process exec(String commandName, int timeout) throws IOException;

	/**
	 * Obtain an {@link FtpChannel} for performing FTP operations over this
	 * {@link RemoteSession}. The default implementation returns {@code null}.
	 *
	 * @return the {@link FtpChannel}
	 * @since 5.2
	 */
	default FtpChannel getFtpChannel() {
		return null;
	}

	/**
	 * Disconnect the remote session
	 */
	void disconnect();
}
