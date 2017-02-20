/*
 * Copyright (C) 2015, Google Inc.
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
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/**
 * Protocol for transport between manually-specified repositories in tests.
 * <p>
 * Remote repositories are registered using {@link #register(Object,
 * Repository)}, after which they can be accessed using the returned URI. As
 * this class provides both the client side (the protocol) and the server side,
 * the caller is responsible for setting up and passing the connection context,
 * whatever form that may take.
 * <p>
 * Unlike the other built-in protocols, which are automatically-registered
 * singletons, callers are expected to register/unregister specific protocol
 * instances on demand with {@link Transport#register(TransportProtocol)}.
 *
 * @param <C>
 *            the connection type
 * @since 4.0
 */
public class TestProtocol<C> extends TransportProtocol {
	private static final String SCHEME = "test"; //$NON-NLS-1$

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
	 * @param uploadPackFactory
	 *            factory for creating {@link UploadPack} used by all connections
	 *            from this protocol instance.
	 * @param receivePackFactory
	 *            factory for creating {@link ReceivePack} used by all connections
	 *            from this protocol instance.
	 */
	public TestProtocol(UploadPackFactory<C> uploadPackFactory,
			ReceivePackFactory<C> receivePackFactory) {
		this.uploadPackFactory = uploadPackFactory;
		this.receivePackFactory = receivePackFactory;
		this.handles = new HashMap<>();
	}

	@Override
	public String getName() {
		return JGitText.get().transportProtoTest;
	}

	@Override
	public Set<String> getSchemes() {
		return Collections.singleton(SCHEME);
	}

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

	@Override
	public Set<URIishField> getRequiredFields() {
		return EnumSet.of(URIishField.HOST, URIishField.PATH);
	}

	@Override
	public Set<URIishField> getOptionalFields() {
		return Collections.emptySet();
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
			throw new IllegalStateException();
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
			return new InternalFetchConnection<>(
					this, uploadPackFactory, handle.req, handle.remote);
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
