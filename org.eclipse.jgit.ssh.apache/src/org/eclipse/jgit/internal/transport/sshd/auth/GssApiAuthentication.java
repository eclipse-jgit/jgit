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
package org.eclipse.jgit.internal.transport.sshd.auth;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.eclipse.jgit.internal.transport.sshd.GssApiMechanisms;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.ietf.jgss.GSSContext;

/**
 * An abstract implementation of a GSS-API multi-round authentication.
 *
 * @param <ParameterType>
 *            defining the parameter type for the authentication
 * @param <TokenType>
 *            defining the token type for the authentication
 */
public abstract class GssApiAuthentication<ParameterType, TokenType>
		extends AbstractAuthenticationHandler<ParameterType, TokenType> {

	private GSSContext context;

	/** The last token generated. */
	protected byte[] token;

	/**
	 * Creates a new {@link GssApiAuthentication} to authenticate with the given
	 * {@code proxy}.
	 *
	 * @param proxy
	 *            the {@link InetSocketAddress} of the proxy to connect to
	 */
	public GssApiAuthentication(InetSocketAddress proxy) {
		super(proxy);
	}

	@Override
	public void close() {
		GssApiMechanisms.closeContextSilently(context);
		context = null;
		done = true;
	}

	@Override
	public final void start() throws Exception {
		try {
			context = createContext();
			context.requestMutualAuth(true);
			context.requestConf(false);
			context.requestInteg(false);
			byte[] empty = new byte[0];
			token = context.initSecContext(empty, 0, 0);
		} catch (Exception e) {
			close();
			throw e;
		}
	}

	@Override
	public final void process() throws Exception {
		if (context == null) {
			throw new IOException(
					format(SshdText.get().proxyCannotAuthenticate, proxy));
		}
		try {
			byte[] received = extractToken(params);
			token = context.initSecContext(received, 0, received.length);
			checkDone();
		} catch (Exception e) {
			close();
			throw e;
		}
	}

	private void checkDone() throws Exception {
		done = context.isEstablished();
		if (done) {
			context.dispose();
			context = null;
		}
	}

	/**
	 * Creates the {@link GSSContext} to use.
	 *
	 * @return a fresh {@link GSSContext} to use
	 * @throws Exception
	 *             if the context cannot be created
	 */
	protected abstract GSSContext createContext() throws Exception;

	/**
	 * Extracts the token from the last set parameters.
	 *
	 * @param input
	 *            to extract the token from
	 * @return the extracted token, or {@code null} if none
	 * @throws Exception
	 *             if an error occurs
	 */
	protected abstract byte[] extractToken(ParameterType input)
			throws Exception;
}
