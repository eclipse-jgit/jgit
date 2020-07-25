/*
 * Copyright (C) 2018, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
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
import java.util.function.Supplier;

import org.apache.sshd.client.ClientAuthenticationManager;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.session.SessionContext;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;

/**
 * A bridge from sshd's {@link FilePasswordProvider} to our per-session
 * {@link KeyPasswordProvider} API.
 */
public class PasswordProviderWrapper implements FilePasswordProvider {

	private static final AttributeKey<PerSessionState> STATE = new AttributeKey<>();

	private static class PerSessionState {

		Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

		KeyPasswordProvider delegate;

	}

	private final Supplier<KeyPasswordProvider> factory;

	/**
	 * Creates a new {@link PasswordProviderWrapper}.
	 *
	 * @param factory
	 *            to use to create per-session {@link KeyPasswordProvider}s
	 */
	public PasswordProviderWrapper(
			@NonNull Supplier<KeyPasswordProvider> factory) {
		this.factory = factory;
	}

	private PerSessionState getState(SessionContext context) {
		PerSessionState state = context.getAttribute(STATE);
		if (state == null) {
			state = new PerSessionState();
			state.delegate = factory.get();
			Integer maxNumberOfAttempts = context
					.getInteger(ClientAuthenticationManager.PASSWORD_PROMPTS);
			if (maxNumberOfAttempts != null
					&& maxNumberOfAttempts.intValue() > 0) {
				state.delegate.setAttempts(maxNumberOfAttempts.intValue());
			} else {
				state.delegate.setAttempts(
						ClientAuthenticationManager.DEFAULT_PASSWORD_PROMPTS);
			}
			context.setAttribute(STATE, state);
		}
		return state;
	}

	@Override
	public String getPassword(SessionContext session, NamedResource resource,
			int attemptIndex) throws IOException {
		String key = resource.getName();
		PerSessionState state = getState(session);
		int attempt = state.counts
				.computeIfAbsent(key, k -> new AtomicInteger()).get();
		char[] passphrase = state.delegate.getPassphrase(toUri(key), attempt);
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
		PerSessionState state = getState(session);
		AtomicInteger count = state.counts.get(key);
		int numberOfAttempts = count == null ? 0 : count.incrementAndGet();
		ResourceDecodeResult result = null;
		try {
			if (state.delegate.keyLoaded(toUri(key), numberOfAttempts, err)) {
				result = ResourceDecodeResult.RETRY;
			} else {
				result = ResourceDecodeResult.TERMINATE;
			}
		} finally {
			if (result != ResourceDecodeResult.RETRY) {
				state.counts.remove(key);
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
