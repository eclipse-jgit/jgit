package org.eclipse.jgit.transport.http;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * A {@link X509TrustManager} that doesn't verify anything. Can be used for
 * skipping SSL checks.
 *
 * @since 5.10
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