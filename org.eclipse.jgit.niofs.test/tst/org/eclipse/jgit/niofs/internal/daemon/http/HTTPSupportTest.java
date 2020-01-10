/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.http;

import static org.assertj.core.api.Assertions.assertThat;
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
import javax.servlet.ServletRegistration;

import org.eclipse.jgit.niofs.internal.BaseTest;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class HTTPSupportTest extends BaseTest {

	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = super.getGitPreferences();
		gitPrefs.put(JGitFileSystemProviderConfiguration.GIT_HTTP_ENABLED, "true");
		return gitPrefs;
	}

	@Test
	public void testRoot() {
		base("");
		assertThat(provider.getFullHostNames().get("http")).isEqualTo("localhost:8080/git");
	}

	@Test
	public void testContext() {
		base("/app-former");
		assertThat(provider.getFullHostNames().get("http")).isEqualTo("localhost:8080/app-former/git");
	}

	public void base(final String contextPath) {
		final HTTPSupport httpSupport = new HTTPSupport() {
			@Override
			protected JGitFileSystemProvider resolveProvider() {
				return provider;
			}
		};

		final ServletContextEvent sce = mock(ServletContextEvent.class);

		final ServletContext sc = mock(ServletContext.class);
		final ServletRegistration.Dynamic dyn = mock(ServletRegistration.Dynamic.class);

		ArgumentCaptor<Servlet> servletArgumentCaptor = ArgumentCaptor.forClass(Servlet.class);

		when(sc.addServlet(anyString(), servletArgumentCaptor.capture())).thenReturn(dyn);

		when(sce.getServletContext()).thenReturn(sc);
		when(sc.getContextPath()).thenReturn(contextPath);

		httpSupport.contextInitialized(sce);

		verify(sc, times(1)).addServlet(anyString(), any(Servlet.class));
		verify(dyn, times(1)).addMapping("/git/*");
		verify(dyn, times(1)).setLoadOnStartup(1);
		verify(dyn, times(1)).setAsyncSupported(false);
	}
}
