/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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
package org.eclipse.jgit.transport.sshd;

import java.net.InetSocketAddress;
import java.util.Objects;

import org.apache.sshd.client.session.ClientProxyConnector;
import org.eclipse.jgit.annotations.NonNull;

/**
 * A DTO encapsulating the data needed to connect through a proxy server.
 *
 * @since 5.2
 */
public class ProxyData {

	private ClientProxyConnector connector;

	private InetSocketAddress proxyAddress;

	/**
	 * Creates a new {@link ProxyData} instance.
	 *
	 * @param proxy
	 *            to connect to
	 * @param connector
	 *            to use
	 */
	public ProxyData(InetSocketAddress proxy, ClientProxyConnector connector) {
		this.proxyAddress = proxy;
		this.connector = connector;
	}

	/**
	 * Obtains a {@link ClientProxyConnector} implementing the protocol
	 * needed for connecting to the proxy.
	 *
	 * @return the {@link ClientProxyConnector}
	 */
	@NonNull
	public ClientProxyConnector getConnector() {
		return connector;
	}

	/**
	 * Obtains the remote {@link InetSocketAddress} of the proxy to connect to.
	 *
	 * @return the remote address of the proxy
	 */
	@NonNull
	public InetSocketAddress getAddress() {
		return proxyAddress;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj != null && this.getClass().equals(obj.getClass())) {
			ProxyData other = (ProxyData) obj;
			return Objects.equals(proxyAddress, other.proxyAddress)
					&& Objects.equals(connector, other.connector);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(proxyAddress, connector);
	}

}