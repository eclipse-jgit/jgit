/*
 * Copyright (C) 2022 Nail Samatov <sanail@yandex.ru> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Base class for commands that use a
 *  * {@link org.eclipse.jgit.transport.CredentialsProvider} during execution.
 *
 * @param <C>
 * @param <T>
 * @since 6.1
 */
public abstract class CredentialsAwareCommand<C extends GitCommand, T>
		extends GitCommand<T> {
	/**
	 * Configured credentials provider
	 */
	protected CredentialsProvider credentialsProvider;

	/**
	 * <p>Constructor for CredentialsAwareCommand.</p>
	 *
	 * @param repo a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected CredentialsAwareCommand(Repository repo) {
		super(repo);
		this.credentialsProvider = CredentialsProvider.getDefault();
	}

	/**
	 * Set the <code>credentialsProvider</code>.
	 *
	 * @param credentialsProvider
	 *            the {@link org.eclipse.jgit.transport.CredentialsProvider} to
	 *            use when querying for credentials (eg., during signing,
	 *            pushing, downloading LFS resources)
	 * @return {@code this}
	 */
	public C setCredentialsProvider(
			final CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return self();
	}

	/**
	 * Return this command cast to {@code C}
	 *
	 * @return {@code this} cast to {@code C}
	 */
	@SuppressWarnings("unchecked")
	protected final C self() {
		return (C) this;
	}
}
