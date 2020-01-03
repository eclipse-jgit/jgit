/*
 * Copyright (C) 2012, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;

/**
 * Transport protocol contributed via service provider
 */
public class SpiTransport extends Transport {

	/**
	 * Transport protocol scheme
	 */
	public static final String SCHEME = "testspi";

	/**
	 * Instance
	 */
	public static final TransportProtocol PROTO = new TransportProtocol() {

		@Override
		public String getName() {
			return "Test SPI Transport Protocol";
		}

		@Override
		public Set<String> getSchemes() {
			return Collections.singleton(SCHEME);
		}

		@Override
		public Transport open(URIish uri, Repository local, String remoteName)
				throws NotSupportedException, TransportException {
			throw new NotSupportedException("not supported");
		}
	};

	private SpiTransport(Repository local, URIish uri) {
		super(local, uri);
	}

	@Override
	public FetchConnection openFetch() throws NotSupportedException,
			TransportException {
		throw new NotSupportedException("not supported");
	}

	@Override
	public PushConnection openPush() throws NotSupportedException,
			TransportException {
		throw new NotSupportedException("not supported");
	}

	@Override
	public void close() {
		// Intentionally left blank
	}
}
