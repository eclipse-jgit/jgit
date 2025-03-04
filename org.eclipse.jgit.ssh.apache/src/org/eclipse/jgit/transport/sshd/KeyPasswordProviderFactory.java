/*
 * Copyright (C) 2024, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Maintains a static singleton instance of a factory to create a
 * {@link KeyPasswordProvider} from a {@link CredentialsProvider}.
 *
 * @since 7.1
 */
public final class KeyPasswordProviderFactory {

	/**
	 * Creates a {@link KeyPasswordProvider} from a {@link CredentialsProvider}.
	 */
	@FunctionalInterface
	public interface KeyPasswordProviderCreator
			extends Function<CredentialsProvider, KeyPasswordProvider> {
		// Nothing
	}

	private static final KeyPasswordProviderCreator DEFAULT = IdentityPasswordProvider::new;

	private static AtomicReference<KeyPasswordProviderCreator> INSTANCE = new AtomicReference<>(
			DEFAULT);

	private KeyPasswordProviderFactory() {
		// No instantiation
	}

	/**
	 * Retrieves the currently set {@link KeyPasswordProviderCreator}.
	 *
	 * @return the {@link KeyPasswordProviderCreator}
	 */
	@NonNull
	public static KeyPasswordProviderCreator getInstance() {
		return INSTANCE.get();
	}

	/**
	 * Sets a new {@link KeyPasswordProviderCreator}.
	 *
	 * @param provider
	 *            to set; if {@code null}, sets a default provider.
	 * @return the previously set {@link KeyPasswordProviderCreator}
	 */
	@NonNull
	public static KeyPasswordProviderCreator setInstance(
			KeyPasswordProviderCreator provider) {
		if (provider == null) {
			return INSTANCE.getAndSet(DEFAULT);
		}
		return INSTANCE.getAndSet(provider);
	}
}
