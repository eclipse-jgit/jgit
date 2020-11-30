/*
 * Copyright (C) 2016, 2020 JGit contributors
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contributors:
 *    Saša Živkov 2016 - initial API
 *    Thomas Wolf 2020 - extracted from HttpSupport
 */
package org.eclipse.jgit.transport.http;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * A {@link X509TrustManager} that doesn't verify anything. Can be used for
 * skipping SSL checks.
 *
 * @since 5.11
 */
public class NoCheckX509TrustManager implements X509TrustManager {

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return null;
	}

	@Override
	public void checkClientTrusted(X509Certificate[] certs,
			String authType) {
		// no check
	}

	@Override
	public void checkServerTrusted(X509Certificate[] certs,
			String authType) {
		// no check
	}
}