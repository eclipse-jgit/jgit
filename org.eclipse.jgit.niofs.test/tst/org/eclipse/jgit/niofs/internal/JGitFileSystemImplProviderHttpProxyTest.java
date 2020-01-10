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

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashMap;

import org.junit.Test;

import static java.net.Authenticator.requestPasswordAuthentication;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_DAEMON_ENABLED;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_ENABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JGitFileSystemImplProviderHttpProxyTest {

	@Test
	public void testHttpProxy() throws IOException {
		final String userName = "user";
		final String passw = "passwd";

		final JGitFileSystemProvider provider = new JGitFileSystemProvider(new HashMap<String, String>() {
			{
				put("http.proxyUser", "user");
				put("http.proxyPassword", "passwd");
				put(GIT_DAEMON_ENABLED, "false");
				put(GIT_SSH_ENABLED, "false");
			}
		});

		final PasswordAuthentication passwdAuth = requestPasswordAuthentication("localhost", InetAddress.getLocalHost(),
				8080, "http", "xxx", "http", new URL("http://localhost"), Authenticator.RequestorType.PROXY);

		assertEquals(userName, passwdAuth.getUserName());
		assertEquals(passw, new String(passwdAuth.getPassword()));

		provider.dispose();
	}

	@Test
	public void testHttpsProxy() throws IOException {
		final String userName = "user";
		final String passw = "passwd";

		final JGitFileSystemProvider provider = new JGitFileSystemProvider(new HashMap<String, String>() {
			{
				put("https.proxyUser", "user");
				put("https.proxyPassword", "passwd");
				put(GIT_DAEMON_ENABLED, "false");
				put(GIT_SSH_ENABLED, "false");
			}
		});

		final PasswordAuthentication passwdAuth = requestPasswordAuthentication("localhost", InetAddress.getLocalHost(),
				8080, "https", "xxx", "https", new URL("https://localhost"), Authenticator.RequestorType.PROXY);

		assertEquals(userName, passwdAuth.getUserName());
		assertEquals(passw, new String(passwdAuth.getPassword()));

		provider.dispose();
	}

	@Test
	public void testNoProxyInfo() throws IOException {
		final JGitFileSystemProvider provider = new JGitFileSystemProvider(new HashMap<String, String>() {
			{
				put(GIT_DAEMON_ENABLED, "false");
				put(GIT_SSH_ENABLED, "false");
			}
		});

		{
			final PasswordAuthentication passwdAuth = requestPasswordAuthentication("localhost",
					InetAddress.getLocalHost(), 8080, "https", "xxx", "https", new URL("https://localhost"),
					Authenticator.RequestorType.PROXY);

			assertNull(passwdAuth);
		}

		{
			final PasswordAuthentication passwdAuth = requestPasswordAuthentication("localhost",
					InetAddress.getLocalHost(), 8080, "http", "xxx", "http", new URL("http://localhost"),
					Authenticator.RequestorType.PROXY);

			assertNull(passwdAuth);
		}

		provider.dispose();
	}
}
