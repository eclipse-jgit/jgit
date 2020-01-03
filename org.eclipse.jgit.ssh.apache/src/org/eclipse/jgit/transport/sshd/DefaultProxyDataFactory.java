/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * A default implementation of a {@link ProxyDataFactory} based on the standard
 * {@link java.net.ProxySelector}.
 *
 * @since 5.2
 */
public class DefaultProxyDataFactory implements ProxyDataFactory {

	@Override
	public ProxyData get(InetSocketAddress remoteAddress) {
		try {
			List<Proxy> proxies = ProxySelector.getDefault()
					.select(new URI(
							"socket://" + remoteAddress.getHostString())); //$NON-NLS-1$
			ProxyData data = getData(proxies, Proxy.Type.SOCKS);
			if (data == null) {
				proxies = ProxySelector.getDefault()
						.select(new URI(Proxy.Type.HTTP.name(),
								"//" + remoteAddress.getHostString(), //$NON-NLS-1$
								null));
				data = getData(proxies, Proxy.Type.HTTP);
			}
			return data;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private ProxyData getData(List<Proxy> proxies, Proxy.Type type) {
		Proxy proxy = proxies.stream().filter(p -> type == p.type()).findFirst()
				.orElse(null);
		if (proxy == null) {
			return null;
		}
		SocketAddress address = proxy.address();
		if (!(address instanceof InetSocketAddress)) {
			return null;
		}
		switch (type) {
		case HTTP:
			return new ProxyData(proxy);
		case SOCKS:
			return new ProxyData(proxy);
		default:
			return null;
		}
	}
}
