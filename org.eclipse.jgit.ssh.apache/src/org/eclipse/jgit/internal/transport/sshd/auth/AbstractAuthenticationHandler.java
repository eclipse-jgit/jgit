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

import java.net.InetSocketAddress;

/**
 * Abstract base class for {@link AuthenticationHandler}s encapsulating basic
 * common things.
 *
 * @param <ParameterType>
 *            defining the parameter type for the authentication
 * @param <TokenType>
 *            defining the token type for the authentication
 */
public abstract class AbstractAuthenticationHandler<ParameterType, TokenType>
		implements AuthenticationHandler<ParameterType, TokenType> {

	/** The {@link InetSocketAddress} or the proxy to connect to. */
	protected InetSocketAddress proxy;

	/** The last set parameters. */
	protected ParameterType params;

	/** A flag telling whether this authentication is done. */
	protected boolean done;

	/**
	 * Creates a new {@link AbstractAuthenticationHandler} to authenticate with
	 * the given {@code proxy}.
	 *
	 * @param proxy
	 *            the {@link InetSocketAddress} of the proxy to connect to
	 */
	public AbstractAuthenticationHandler(InetSocketAddress proxy) {
		this.proxy = proxy;
	}

	@Override
	public final void setParams(ParameterType input) {
		params = input;
	}

	@Override
	public final boolean isDone() {
		return done;
	}

}
