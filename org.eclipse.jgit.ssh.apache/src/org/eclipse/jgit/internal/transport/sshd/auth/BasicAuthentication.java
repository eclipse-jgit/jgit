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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.transport.SshConstants;

/**
 * An abstract implementation of a username-password authentication. It can be
 * given an initial known username-password pair; if so, this will be tried
 * first. Subsequent rounds will then try to obtain a user name and password via
 * the global {@link Authenticator}.
 *
 * @param <ParameterType>
 *            defining the parameter type for the authentication
 * @param <TokenType>
 *            defining the token type for the authentication
 */
public abstract class BasicAuthentication<ParameterType, TokenType>
		extends AbstractAuthenticationHandler<ParameterType, TokenType> {

	/** The current user name. */
	protected String user;

	/** The current password. */
	protected byte[] password;

	/**
	 * Creates a new {@link BasicAuthentication} to authenticate with the given
	 * {@code proxy}.
	 *
	 * @param proxy
	 *            {@link InetSocketAddress} of the proxy to connect to
	 * @param initialUser
	 *            initial user name to try; may be {@code null}
	 * @param initialPassword
	 *            initial password to try, may be {@code null}
	 */
	public BasicAuthentication(InetSocketAddress proxy, String initialUser,
			char[] initialPassword) {
		super(proxy);
		this.user = initialUser;
		this.password = convert(initialPassword);
	}

	private byte[] convert(char[] pass) {
		if (pass == null) {
			return new byte[0];
		}
		ByteBuffer bytes = UTF_8.encode(CharBuffer.wrap(pass));
		byte[] pwd = new byte[bytes.remaining()];
		bytes.get(pwd);
		if (bytes.hasArray()) {
			Arrays.fill(bytes.array(), (byte) 0);
		}
		Arrays.fill(pass, '\000');
		return pwd;
	}

	/**
	 * Clears the {@link #password}.
	 */
	protected void clearPassword() {
		if (password != null) {
			Arrays.fill(password, (byte) 0);
		}
		password = new byte[0];
	}

	@Override
	public final void close() {
		clearPassword();
		done = true;
	}

	@Override
	public final void start() throws Exception {
		if (user != null && !user.isEmpty()
				|| password != null && password.length > 0) {
			return;
		}
		askCredentials();
	}

	@Override
	public void process() throws Exception {
		askCredentials();
	}

	/**
	 * Asks for credentials via the global {@link Authenticator}.
	 */
	protected void askCredentials() {
		clearPassword();
		PasswordAuthentication auth = AccessController
				.doPrivileged(new PrivilegedAction<PasswordAuthentication>() {

					@Override
					public PasswordAuthentication run() {
						return Authenticator.requestPasswordAuthentication(
								proxy.getHostString(), proxy.getAddress(),
								proxy.getPort(), SshConstants.SSH_SCHEME,
								SshdText.get().proxyPasswordPrompt, "Basic", //$NON-NLS-1$
								null, RequestorType.PROXY);
					}
				});
		if (auth == null) {
			user = ""; //$NON-NLS-1$
			throw new CancellationException(
					SshdText.get().authenticationCanceled);
		}
		user = auth.getUserName();
		password = convert(auth.getPassword());
	}
}
