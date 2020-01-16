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

import java.io.Closeable;

/**
 * An {@code AuthenticationHandler} encapsulates a possibly multi-step
 * authentication protocol. Intended usage:
 *
 * <pre>
 * setParams(something);
 * start();
 * sendToken(getToken());
 * while (!isDone()) {
 * 	setParams(receiveMessageAndExtractParams());
 * 	process();
 * 	Object t = getToken();
 * 	if (t != null) {
 * 		sendToken(t);
 * 	}
 * }
 * </pre>
 *
 * An {@code AuthenticationHandler} may be stateful and therefore is a
 * {@link Closeable}.
 *
 * @param <ParameterType>
 *            defining the parameter type for {@link #setParams(Object)}
 * @param <TokenType>
 *            defining the token type for {@link #getToken()}
 */
public interface AuthenticationHandler<ParameterType, TokenType>
		extends Closeable {

	/**
	 * Produces the initial authentication token that can be then retrieved via
	 * {@link #getToken()}.
	 *
	 * @throws Exception
	 *             if an error occurs
	 */
	void start() throws Exception;

	/**
	 * Produces the next authentication token, if any.
	 *
	 * @throws Exception
	 *             if an error occurs
	 */
	void process() throws Exception;

	/**
	 * Sets the parameters for the next token generation via {@link #start()} or
	 * {@link #process()}.
	 *
	 * @param input
	 *            to set, may be {@code null}
	 */
	void setParams(ParameterType input);

	/**
	 * Retrieves the last token generated.
	 *
	 * @return the token, or {@code null} if there is none
	 * @throws Exception
	 *             if an error occurs
	 */
	TokenType getToken() throws Exception;

	/**
	 * Tells whether is authentication mechanism is done (successfully or
	 * unsuccessfully).
	 *
	 * @return whether this authentication is done
	 */
	boolean isDone();

	@Override
	void close();
}
