/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.api;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;

/**
 * Base class for commands that use a {@link Transport} during execution.
 * <p>
 * This class provides standard configuration of a transport for options such as
 * a {@link CredentialsProvider}, a timeout, and a
 * {@link TransportConfigCallback}.
 *
 * @param <C>
 * @param <T>
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
	 * @param repo
	 */
	protected TransportCommand(final Repository repo) {
		super(repo);
	}

	/**
	 * @param credentialsProvider
	 *            the {@link CredentialsProvider} to use
	 * @return {@code this}
	 */
	public C setCredentialsProvider(
			final CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return self();
	}

	/**
	 * @param timeout
	 *            the timeout used for the transport step
	 * @return {@code this}
	 */
	public C setTimeout(int timeout) {
		this.timeout = timeout;
		return self();
	}

	/**
	 * @param transportConfigCallback
	 *            if set, the callback will be invoked after the
	 *            {@link Transport} has created, but before the
	 *            {@link Transport} is used. The callback can use this
	 *            opportunity to set additional type-specific configuration on
	 *            the {@link Transport} instance.
	 * @return {@code this}
	 */
	public C setTransportConfigCallback(
			final TransportConfigCallback transportConfigCallback) {
		this.transportConfigCallback = transportConfigCallback;
		return self();
	}

	/** @return {@code this} */
	@SuppressWarnings("unchecked")
	protected final C self() {
		return (C) this;
	}

	/**
	 * Configure transport with credentials provider, timeout, and config
	 * callback
	 *
	 * @param transport
	 * @return {@code this}
	 */
	protected C configure(final Transport transport) {
		if (credentialsProvider != null)
			transport.setCredentialsProvider(credentialsProvider);
		transport.setTimeout(timeout);
		if (transportConfigCallback != null)
			transportConfigCallback.configure(transport);
		return self();
	}

	/**
	 * Configure a child command with the current configuration set in
	 * {@code this} command
	 *
	 * @param childCommand
	 * @return {@code this}
	 */
	protected C configure(final TransportCommand childCommand) {
		childCommand.setCredentialsProvider(credentialsProvider);
		childCommand.setTimeout(timeout);
		childCommand.setTransportConfigCallback(transportConfigCallback);
		return self();
	}
}
