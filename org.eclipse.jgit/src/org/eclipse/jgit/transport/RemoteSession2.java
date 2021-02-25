/*
 * Copyright (C) 2020, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.Map;

/**
 * A {@link RemoteSession} that supports passing environment variables to
 * commands.
 *
 * @since 5.11
 */
public interface RemoteSession2 extends RemoteSession {

	/**
	 * Creates a new remote {@link Process} to execute the given command. The
	 * returned process's streams exist and are connected, and execution of the
	 * process is already started.
	 *
	 * @param commandName
	 *            command to execute
	 * @param environment
	 *            environment variables to pass on
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
	Process exec(String commandName, Map<String, String> environment,
			int timeout) throws IOException;
}
