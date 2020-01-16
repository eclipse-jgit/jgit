/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		PasswordAuthentication auth = AccessController.doPrivileged(
				(PrivilegedAction<PasswordAuthentication>) () -> Authenticator
						.requestPasswordAuthentication(proxy.getHostString(),
								proxy.getAddress(), proxy.getPort(),
								SshConstants.SSH_SCHEME,
								SshdText.get().proxyPasswordPrompt, "Basic", //$NON-NLS-1$
								null, RequestorType.PROXY));
		if (auth == null) {
			user = ""; //$NON-NLS-1$
			throw new CancellationException(
					SshdText.get().authenticationCanceled);
		}
		user = auth.getUserName();
		password = convert(auth.getPassword());
	}
}
