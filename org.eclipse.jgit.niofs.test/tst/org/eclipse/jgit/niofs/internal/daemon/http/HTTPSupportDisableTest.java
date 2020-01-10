/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.eclipse.jgit.niofs.internal.BaseTest;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.junit.Test;

public class HTTPSupportDisableTest extends BaseTest {

	/*
	 * Default Git preferences suitable for most of the tests. If specific test
	 * needs some custom configuration, it needs to override this method and provide
	 * own map of preferences.
	 */
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = super.getGitPreferences();
		gitPrefs.put(JGitFileSystemProviderConfiguration.GIT_HTTP_ENABLED, "false");
		return gitPrefs;
	}

	@Test
	public void testRoot() {
		assertThat(provider.getFullHostNames().get("http")).isNull();
	}

	@Test
	public void test() {
		final HTTPSupport httpSupport = new HTTPSupport() {
			@Override
			protected JGitFileSystemProvider resolveProvider() {
				return provider;
			}
		};

		final ServletContextEvent sce = mock(ServletContextEvent.class);
		final ServletContext sc = mock(ServletContext.class);
		when(sce.getServletContext()).thenReturn(sc);
		httpSupport.contextInitialized(sce);
		assertFalse(provider.getFullHostNames().containsKey("http"));

		verify(sc, times(0)).addServlet(anyString(), any(Servlet.class));
	}
}
