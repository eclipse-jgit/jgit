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

/**
 * Interface for obtaining {@link ProxyData} to connect through some proxy.
 *
 * @since 5.2
 */
public interface ProxyDataFactory {

	/**
	 * Get the {@link ProxyData} to connect to a proxy. It should return a
	 * <em>new</em> {@link ProxyData} instance every time; if the returned
	 * {@link ProxyData} contains a password, the {@link SshdSession} will clear
	 * it once it is no longer needed.
	 *
	 * @param remoteAddress
	 *            to connect to
	 * @return the {@link ProxyData} or {@code null} if a direct connection is
	 *         to be made
	 */
	ProxyData get(InetSocketAddress remoteAddress);
}
