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
package org.eclipse.jgit.internal.transport.sshd;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.session.SessionContext;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;

/**
 * A bridge from sshd's {@link RepeatingFilePasswordProvider} to our
 * {@link KeyPasswordProvider} API.
 */
public class PasswordProviderWrapper implements RepeatingFilePasswordProvider {

	private final KeyPasswordProvider delegate;

	private Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

	/**
	 * @param delegate
	 */
	public PasswordProviderWrapper(@NonNull KeyPasswordProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public void setAttempts(int numberOfPasswordPrompts) {
		delegate.setAttempts(numberOfPasswordPrompts);
	}

	@Override
	public int getAttempts() {
		return delegate.getAttempts();
	}

	@Override
	public String getPassword(SessionContext session, NamedResource resource,
			int attemptIndex) throws IOException {
		String key = resource.getName();
		int attempt = counts
				.computeIfAbsent(key, k -> new AtomicInteger()).get();
		char[] passphrase = delegate.getPassphrase(toUri(key), attempt);
		if (passphrase == null) {
			return null;
		}
		try {
			return new String(passphrase);
		} finally {
			Arrays.fill(passphrase, '\000');
		}
	}

	@Override
	public ResourceDecodeResult handleDecodeAttemptResult(
			SessionContext session, NamedResource resource, int retryIndex,
			String password, Exception err)
			throws IOException, GeneralSecurityException {
		String key = resource.getName();
		AtomicInteger count = counts.get(key);
		int numberOfAttempts = count == null ? 0 : count.incrementAndGet();
		ResourceDecodeResult result = null;
		try {
			if (delegate.keyLoaded(toUri(key), numberOfAttempts, err)) {
				result = ResourceDecodeResult.RETRY;
			} else {
				result = ResourceDecodeResult.TERMINATE;
			}
		} finally {
			if (result != ResourceDecodeResult.RETRY) {
				counts.remove(key);
			}
		}
		return result;
	}

	/**
	 * Creates a {@link URIish} from a given string. The
	 * {@link CredentialsProvider} uses uris as resource identifications.
	 *
	 * @param resourceKey
	 *            to convert
	 * @return the uri
	 */
	private URIish toUri(String resourceKey) {
		try {
			return new URIish(resourceKey);
		} catch (URISyntaxException e) {
			return new URIish().setPath(resourceKey); // Doesn't check!!
		}
	}

}
