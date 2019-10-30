/*
 * Copyright (c) 2019, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class HttpSupportTest {

	private static class TestProxySelector extends ProxySelector {

		private static final Proxy DUMMY = new Proxy(Proxy.Type.HTTP,
				InetSocketAddress.createUnresolved("localhost", 0));

		@Override
		public List<Proxy> select(URI uri) {
			if ("http".equals(uri.getScheme())
					&& "somehost".equals(uri.getHost())) {
				return Collections.singletonList(DUMMY);
			}
			return Collections.singletonList(Proxy.NO_PROXY);
		}

		@Override
		public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
			// Empty
		}
	}

	@Test
	public void testMalformedUri() throws Exception {
		// Valid URL, but backslash is not allowed in a URI in the userinfo part
		// per RFC 3986: https://tools.ietf.org/html/rfc3986#section-3.2.1 .
		// Test that conversion to URI to call the ProxySelector does not throw
		// an exception.
		Proxy proxy = HttpSupport.proxyFor(new TestProxySelector(), new URL(
				"http://infor\\c.jones@somehost/somewhere/someproject.git"));
		assertNotNull(proxy);
		assertEquals(Proxy.Type.HTTP, proxy.type());
	}

	@Test
	public void testCorrectUri() throws Exception {
		// Backslash escaped as %5C is correct.
		Proxy proxy = HttpSupport.proxyFor(new TestProxySelector(), new URL(
				"http://infor%5Cc.jones@somehost/somewhere/someproject.git"));
		assertNotNull(proxy);
		assertEquals(Proxy.Type.HTTP, proxy.type());
	}
}
