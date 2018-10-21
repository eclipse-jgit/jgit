/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
	 * Runs {@code startSsh} once the proxy connection is established.
	 *
	 * @param startSsh
	 *            operation to run
	 * @throws Exception
	 *             if the operation is run synchronously and throws an exception
	 */
	void runWhenDone(Callable<Void> startSsh) throws Exception;
}
