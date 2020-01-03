/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.proxy;

import java.util.concurrent.Callable;

import org.apache.sshd.client.session.ClientProxyConnector;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.util.Readable;

/**
 * Some proxy connections are stateful and require the exchange of multiple
 * request-reply messages. The default {@link ClientProxyConnector} has only
 * support for sending a message; replies get routed through the Ssh session,
 * and don't get back to this proxy connector. Augment the interface so that the
 * session can know when to route messages received to the proxy connector, and
 * when to start handling them itself.
 */
public interface StatefulProxyConnector extends ClientProxyConnector {

	/**
	 * A property key for a session property defining the timeout for setting up
	 * the proxy connection.
	 */
	static final String TIMEOUT_PROPERTY = StatefulProxyConnector.class
			.getName() + "-timeout"; //$NON-NLS-1$

	/**
	 * Handle a received message.
	 *
	 * @param session
	 *            to use for writing data
	 * @param buffer
	 *            received data
	 * @throws Exception
	 *             if data cannot be read, or the connection attempt fails
	 */
	void messageReceived(IoSession session, Readable buffer) throws Exception;

	/**
	 * Runs {@code command} once the proxy connection is established. May be
	 * called multiple times; commands are run sequentially. If the proxy
	 * connection is already established, {@code command} is executed directly
	 * synchronously.
	 *
	 * @param command
	 *            operation to run
	 * @throws Exception
	 *             if the operation is run synchronously and throws an exception
	 */
	void runWhenDone(Callable<Void> command) throws Exception;
}
