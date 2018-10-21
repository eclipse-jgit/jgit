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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A simple representation of an authentication challenge as sent in a
 * "WWW-Authenticate" or "Proxy-Authenticate" header. Such challenges start with
 * a mechanism name, followed either by one single token, or by a list of
 * key=value pairs.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7235#section-2.1">RFC 7235, sec.
 *      2.1</a>
 */
public class AuthenticationChallenge {

	private final String mechanism;

	private String token;

	private Map<String, String> arguments;

	/**
	 * Create a new {@link AuthenticationChallenge} with the given mechanism.
	 *
	 * @param mechanism
	 *            for the challenge
	 */
	public AuthenticationChallenge(String mechanism) {
		this.mechanism = mechanism;
	}

	/**
	 * Retrieves the authentication mechanism specified by this challenge, for
	 * instance "Basic".
	 *
	 * @return the mechanism name
	 */
	public String getMechanism() {
		return mechanism;
	}

	/**
	 * Retrieves the token of the challenge, if any.
	 *
	 * @return the token, or {@code null} if there is none.
	 */
	public String getToken() {
		return token;
	}

	/**
	 * Retrieves the arguments of the challenge.
	 *
	 * @return a possibly empty map of the key=value arguments of the challenge
	 */
	@NonNull
	public Map<String, String> getArguments() {
		return arguments == null ? Collections.emptyMap() : arguments;
	}

	void addArgument(String key, String value) {
		if (arguments == null) {
			arguments = new LinkedHashMap<>();
		}
		arguments.put(key, value);
	}

	void setToken(String token) {
		this.token = token;
	}

	@Override
	public String toString() {
		return "AuthenticationChallenge[" + mechanism + ',' + token + ',' //$NON-NLS-1$
				+ (arguments == null ? "<none>" : arguments.toString()) + ']'; //$NON-NLS-1$
	}
}
