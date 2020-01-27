/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.BasePackFetchConnection.FetchConfig;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/**
 * Protocol for transport between manually-specified repositories in tests.
 * <p>
 * Remote repositories are registered using
 * {@link #register(Object, Repository)}, after which they can be accessed using
 * the returned URI. As this class provides both the client side (the protocol)
 * and the server side, the caller is responsible for setting up and passing the
 * connection context, whatever form that may take.
 * <p>
 * Unlike the other built-in protocols, which are automatically-registered
 * singletons, callers are expected to register/unregister specific protocol
 * instances on demand with
 * {@link org.eclipse.jgit.transport.Transport#register(TransportProtocol)}.
 *
 * @param <C>
 *            the connection type
 * @since 4.0
 */
public class TestProtocol<C> extends TransportProtocol {
	private static final String SCHEME = "test"; //$NON-NLS-1$

	private static FetchConfig fetchConfig;

	private class Handle {
		final C req;
		final Repository remote;

		Handle(C req, Repository remote) {
			this.req = req;
			this.remote = remote;
		}
	}

	final UploadPackFactory<C> uploadPackFactory;
	final ReceivePackFactory<C> receivePackFactory;
	private final HashMap<URIish, Handle> handles;

	/**
	 * Constructor for TestProtocol.
	 *
	 * @param uploadPackFactory
	 *            factory for creating
	 *            {@link org.eclipse.jgit.transport.UploadPack} used by all
	 *            connections from this protocol instance.
	 * @param receivePackFactory
	 *            factory for creating
	 *            {@link org.eclipse.jgit.transport.ReceivePack} used by all
	 *            connections from this protocol instance.
	 */
	public TestProtocol(UploadPackFactory<C> uploadPackFactory,
			ReceivePackFactory<C> receivePackFactory) {
		this.uploadPackFactory = uploadPackFactory;
		this.receivePackFactory = receivePackFactory;
		this.handles = new HashMap<>();
	}

	/** {@inheritDoc} */
	@Override
	public String getName() {
		return JGitText.get().transportProtoTest;
	}

	/** {@inheritDoc} */
	@Override
	public Set<String> getSchemes() {
		return Collections.singleton(SCHEME);
	}

	/** {@inheritDoc} */
	@Override
	public Transport open(URIish uri, Repository local, String remoteName)
			throws NotSupportedException, TransportException {
		Handle h = handles.get(uri);
		if (h == null) {
			throw new NotSupportedException(MessageFormat.format(
					JGitText.get().URINotSupported, uri));
		}
		return new TransportInternal(local, uri, h);
	}

	/** {@inheritDoc} */
	@Override
	public Set<URIishField> getRequiredFields() {
		return EnumSet.of(URIishField.HOST, URIishField.PATH);
	}

	/** {@inheritDoc} */
	@Override
	public Set<URIishField> getOptionalFields() {
		return Collections.emptySet();
	}

	static void setFetchConfig(FetchConfig c) {
		fetchConfig = c;
	}

	/**
	 * Register a repository connection over the internal test protocol.
	 *
	 * @param req
	 *            connection context. This instance is reused for all connections
	 *            made using this protocol; if it is stateful and usable only for
	 *            one connection, the same repository should be registered
	 *            multiple times.
	 * @param remote
	 *            remote repository to connect to.
	 * @return a URI that can be used to connect to this repository for both fetch
	 *         and push.
	 */
	public synchronized URIish register(C req, Repository remote) {
		URIish uri;
		try {
			int n = handles.size();
			uri = new URIish(SCHEME + "://test/conn" + n); //$NON-NLS-1$
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
		handles.put(uri, new Handle(req, remote));
		return uri;
	}

	private class TransportInternal extends Transport implements PackTransport {
		private final Handle handle;

		TransportInternal(Repository local, URIish uri, Handle handle) {
			super(local, uri);
			this.handle = handle;
		}

		@Override
		public FetchConnection openFetch() throws NotSupportedException,
				TransportException {
			handle.remote.incrementOpen();
			return new InternalFetchConnection<C>(this, uploadPackFactory,
					handle.req, handle.remote) {
				@Override
				FetchConfig getFetchConfig() {
					return fetchConfig != null ? fetchConfig
							: super.getFetchConfig();
				}
			};
		}

		@Override
		public PushConnection openPush() throws NotSupportedException,
				TransportException {
			handle.remote.incrementOpen();
			return new InternalPushConnection<>(
					this, receivePackFactory, handle.req, handle.remote);
		}

		@Override
		public void close() {
			// Resources must be established per-connection.
		}
	}
}
