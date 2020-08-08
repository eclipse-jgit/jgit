/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2020 Shawn O. Pearce <spearce@spearce.org> and others
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
 * An abstraction of a remote "session" for executing remote commands.
 */
public interface RemoteSession {

	/**
	 * Creates a new remote {@link Process} to execute the given command. The
	 * returned process's streams exist and are connected, and execution of the
	 * process is already started.
	 *
	 * @param commandName
	 *            command to execute
	 * @param timeout
	 *            timeout value, in seconds, for creating the remote process
	 * @return a new remote process, already started
	 * @throws java.io.IOException
	 *             may be thrown in several cases. For example, on problems
	 *             opening input or output streams or on problems connecting or
	 *             communicating with the remote host. For the latter two cases,
	 *             a TransportException may be thrown (a subclass of
	 *             java.io.IOException).
	 */
	Process exec(String commandName, int timeout) throws IOException;

	/**
	 * Obtains an {@link FtpChannel} for performing FTP operations over this
	 * {@link RemoteSession}. The default implementation returns {@code null}.
	 *
	 * @return the {@link FtpChannel}
	 * @since 5.2
	 */
	default FtpChannel getFtpChannel() {
		return null;
	}

	/**
	 * Disconnects the remote session.
	 */
	void disconnect();
}
