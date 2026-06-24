/*
 * Copyright (C) 2011, GitHub Inc. and others
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
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.transport.Transport;

/**
 * Base class for commands that use a
 * {@link org.eclipse.jgit.transport.Transport} during execution.
 * <p>
 * This class provides standard configuration of a transport for options such as
 * a {@link org.eclipse.jgit.transport.CredentialsProvider}, a timeout, and a
 * {@link org.eclipse.jgit.api.TransportConfigCallback}.
 *
 * @param <C>
 *            concrete type of this {@code GitCommand}
 * @param <T>
 *            the return type of the {@code GitCommand}'s {@code call()} method
 */
public abstract class TransportCommand<C extends GitCommand, T> extends
		GitCommand<T> {

	/**
	 * Configured credentials provider
	 */
	protected CredentialsProvider credentialsProvider;

	/**
	 * Configured transport timeout
	 */
	protected int timeout;

	/**
	 * Configured callback for transport configuration
	 */
	protected TransportConfigCallback transportConfigCallback;

	/**
	 * Configured object filter for partial clone/fetch
	 *
	 * @since 7.8
	 */
	protected FilterSpec filterSpec = FilterSpec.NO_FILTER;

	/**
	 * <p>Constructor for TransportCommand.</p>
	 *
	 * @param repo a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected TransportCommand(Repository repo) {
		super(repo);
		setCredentialsProvider(CredentialsProvider.getDefault());
	}

	/**
	 * Set the <code>credentialsProvider</code>.
	 *
	 * @param credentialsProvider
	 *            the {@link org.eclipse.jgit.transport.CredentialsProvider} to
	 *            use
	 * @return {@code this}
	 */
	public C setCredentialsProvider(
			final CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return self();
	}

	/**
	 * Set <code>timeout</code>.
	 *
	 * @param timeout
	 *            the timeout (in seconds) used for the transport step
	 * @return {@code this}
	 */
	public C setTimeout(int timeout) {
		this.timeout = timeout;
		return self();
	}

	/**
	 * Set the <code>TransportConfigCallback</code>.
	 *
	 * @param transportConfigCallback
	 *            if set, the callback will be invoked after the
	 *            {@link org.eclipse.jgit.transport.Transport} has created, but
	 *            before the {@link org.eclipse.jgit.transport.Transport} is
	 *            used. The callback can use this opportunity to set additional
	 *            type-specific configuration on the
	 *            {@link org.eclipse.jgit.transport.Transport} instance.
	 * @return {@code this}
	 */
	public C setTransportConfigCallback(
			final TransportConfigCallback transportConfigCallback) {
		this.transportConfigCallback = transportConfigCallback;
		return self();
	}

	/**
	 * Set an object filter for a partial clone or fetch, e.g. {@code blob:none}
	 * (parsed via {@link FilterSpec#fromFilterLine(String)}) for a blobless
	 * clone.
	 * <p>
	 * The remote must support the {@code filter} capability (i.e.
	 * {@code uploadpack.allowFilter} must be enabled on the server), otherwise
	 * the operation will fail.
	 *
	 * @param filterSpec
	 *            the filter to use, or {@code null} / {@link FilterSpec#NO_FILTER}
	 *            to disable filtering
	 * @return {@code this}
	 * @since 7.8
	 */
	public C setFilterSpec(FilterSpec filterSpec) {
		this.filterSpec = filterSpec == null ? FilterSpec.NO_FILTER : filterSpec;
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

	/**
	 * Configure transport with credentials provider, timeout, and config
	 * callback
	 *
	 * @param transport
	 *            a {@link org.eclipse.jgit.transport.Transport} object.
	 * @return {@code this}
	 */
	protected C configure(Transport transport) {
		if (credentialsProvider != null)
			transport.setCredentialsProvider(credentialsProvider);
		transport.setTimeout(timeout);
		if (filterSpec != null && !filterSpec.isNoOp())
			transport.setFilterSpec(filterSpec);
		if (transportConfigCallback != null)
			transportConfigCallback.configure(transport);
		return self();
	}

	/**
	 * Configure a child command with the current configuration set in
	 * {@code this} command
	 *
	 * @param childCommand
	 *            a {@link org.eclipse.jgit.api.TransportCommand} object.
	 * @return {@code this}
	 */
	protected C configure(TransportCommand childCommand) {
		childCommand.setCredentialsProvider(credentialsProvider);
		childCommand.setTimeout(timeout);
		childCommand.setTransportConfigCallback(transportConfigCallback);
		childCommand.setFilterSpec(filterSpec);
		return self();
	}
}
