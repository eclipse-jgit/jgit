/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.SSH_OVER_HTTP;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.SSH_OVER_HTTPS;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.HashMap;

import org.eclipse.jgit.niofs.internal.config.ConfigProperties;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.junit.Test;

import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;

public class JGitSSHConfigSessionFactoryTest {

	@Test
	public void testNoProxy() {
		JGitFileSystemProviderConfiguration config = new JGitFileSystemProviderConfiguration() {

		};
		config.load(new ConfigProperties(Collections.emptyMap()));

		final JGitSSHConfigSessionFactory instance = new JGitSSHConfigSessionFactory(config) {
			@Override
			ProxyHTTP buildProxy(final JGitFileSystemProviderConfiguration config) {
				fail("no proxy should be set");
				return null;
			}
		};
		instance.configure(mock(OpenSshConfig.Host.class), mock(Session.class));
	}

	@Test
	public void testHttpProxy() {
		JGitFileSystemProviderConfiguration config = new JGitFileSystemProviderConfiguration() {

		};
		config.load(new ConfigProperties(new HashMap<String, String>() {
			{
				put(SSH_OVER_HTTP, "true");
				put("http.proxyHost", "somehost");
				put("http.proxyPort", "10");
			}
		}));

		final JGitSSHConfigSessionFactory instance = new JGitSSHConfigSessionFactory(config) {
			@Override
			ProxyHTTP buildProxy(final JGitFileSystemProviderConfiguration config) {
				ProxyHTTP proxy = super.buildProxy(config);
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_host", "somehost");
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_port", 10);
				assertThat(proxy).hasFieldOrPropertyWithValue("user", null);
				assertThat(proxy).hasFieldOrPropertyWithValue("passwd", null);
				return proxy;
			}
		};
		instance.configure(mock(OpenSshConfig.Host.class), mock(Session.class));
	}

	@Test
	public void testHttpProxyWithAuthentication() {
		JGitFileSystemProviderConfiguration config = new JGitFileSystemProviderConfiguration() {

		};
		config.load(new ConfigProperties(new HashMap<String, String>() {
			{
				put(SSH_OVER_HTTP, "true");
				put("http.proxyHost", "somehost");
				put("http.proxyPort", "10");
				put("http.proxyUser", "user");
				put("http.proxyPassword", "passwd");
			}
		}));

		final JGitSSHConfigSessionFactory instance = new JGitSSHConfigSessionFactory(config) {
			@Override
			ProxyHTTP buildProxy(final JGitFileSystemProviderConfiguration config) {
				ProxyHTTP proxy = super.buildProxy(config);
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_host", "somehost");
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_port", 10);
				assertThat(proxy).hasFieldOrPropertyWithValue("user", "user");
				assertThat(proxy).hasFieldOrPropertyWithValue("passwd", "passwd");
				return proxy;
			}
		};
		instance.configure(mock(OpenSshConfig.Host.class), mock(Session.class));
	}

	@Test
	public void testHttpsProxy() {
		JGitFileSystemProviderConfiguration config = new JGitFileSystemProviderConfiguration() {

		};
		config.load(new ConfigProperties(new HashMap<String, String>() {
			{
				put(SSH_OVER_HTTPS, "true");
				put("https.proxyHost", "somehost");
				put("https.proxyPort", "10");
			}
		}));

		final JGitSSHConfigSessionFactory instance = new JGitSSHConfigSessionFactory(config) {
			@Override
			ProxyHTTP buildProxy(final JGitFileSystemProviderConfiguration config) {
				ProxyHTTP proxy = super.buildProxy(config);
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_host", "somehost");
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_port", 10);
				assertThat(proxy).hasFieldOrPropertyWithValue("user", null);
				assertThat(proxy).hasFieldOrPropertyWithValue("passwd", null);
				return proxy;
			}
		};
		instance.configure(mock(OpenSshConfig.Host.class), mock(Session.class));
	}

	@Test
	public void testHttpsProxyWithAuthentication() {
		JGitFileSystemProviderConfiguration config = new JGitFileSystemProviderConfiguration() {

		};
		config.load(new ConfigProperties(new HashMap<String, String>() {
			{
				put(SSH_OVER_HTTPS, "true");
				put("https.proxyHost", "somehost");
				put("https.proxyPort", "10");
				put("https.proxyUser", "user");
				put("https.proxyPassword", "passwd");
			}
		}));

		final JGitSSHConfigSessionFactory instance = new JGitSSHConfigSessionFactory(config) {
			@Override
			ProxyHTTP buildProxy(final JGitFileSystemProviderConfiguration config) {
				ProxyHTTP proxy = super.buildProxy(config);
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_host", "somehost");
				assertThat(proxy).hasFieldOrPropertyWithValue("proxy_port", 10);
				assertThat(proxy).hasFieldOrPropertyWithValue("user", "user");
				assertThat(proxy).hasFieldOrPropertyWithValue("passwd", "passwd");
				return proxy;
			}
		};
		instance.configure(mock(OpenSshConfig.Host.class), mock(Session.class));
	}
}
