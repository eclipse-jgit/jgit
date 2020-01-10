/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.URIish;

public class JGitSSHConfigSessionFactory extends org.eclipse.jgit.transport.JschConfigSessionFactory {

	private final JGitFileSystemProviderConfiguration config;

	public JGitSSHConfigSessionFactory(final JGitFileSystemProviderConfiguration config) {
		this.config = config;
	}

	@Override
	protected void configure(final OpenSshConfig.Host hc, final Session session) {
		final CredentialsProvider provider = new CredentialsProvider() {
			@Override
			public boolean isInteractive() {
				return false;
			}

			@Override
			public boolean supports(final CredentialItem... items) {
				return true;
			}

			@Override
			public boolean get(final URIish uri, final CredentialItem... items) throws UnsupportedCredentialItem {
				for (CredentialItem item : items) {
					if (item instanceof CredentialItem.YesNoType) {
						((CredentialItem.YesNoType) item).setValue(true);
					} else if (item instanceof CredentialItem.StringType) {
						((CredentialItem.StringType) item).setValue(config.getSshPassphrase());
					}
				}
				return true;
			}
		};
		final UserInfo userInfo = new CredentialsProviderUserInfo(session, provider);
		session.setUserInfo(userInfo);
		if (config.isSshOverHttpProxy() || config.isSshOverHttpsProxy()) {
			session.setProxy(buildProxy(config));
		}
	}

	ProxyHTTP buildProxy(final JGitFileSystemProviderConfiguration config) {
		final String host;
		final int port;
		String user = null;
		String passw = null;
		if (config.isSshOverHttpProxy()) {
			host = config.getHttpProxyHost();
			port = config.getHttpProxyPort();
			user = config.getHttpProxyUser();
			passw = config.getHttpProxyPassword();
		} else {
			host = config.getHttpsProxyHost();
			port = config.getHttpsProxyPort();
			user = config.getHttpsProxyUser();
			passw = config.getHttpsProxyPassword();
		}
		final ProxyHTTP proxyHTTP = new ProxyHTTP(host, port);
		if (user != null) {
			proxyHTTP.setUserPasswd(user, passw);
		}
		return proxyHTTP;
	}
}
