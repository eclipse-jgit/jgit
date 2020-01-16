/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
