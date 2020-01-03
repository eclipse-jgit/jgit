/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.junit.Assert.fail;

import java.io.IOException;

import javax.servlet.http.HttpServletRequestWrapper;

import org.eclipse.jetty.server.Request;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.Before;
import org.junit.Test;

public class AsIsServiceTest extends LocalDiskRepositoryTestCase {
	private Repository db;

	private AsIsFileService service;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		db = createBareRepository();
		service = new AsIsFileService();
	}

	@Test
	public void testDisabledSingleton() throws ServiceNotAuthorizedException {
		service = AsIsFileService.DISABLED;
		try {
			service.access(new R(null, "1.2.3.4"), db);
			fail("Created session for anonymous user: null");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}

		try {
			service.access(new R("bob", "1.2.3.4"), db);
			fail("Created session for user: \"bob\"");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}
	}

	@Test
	public void testCreate_Default() throws ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		service.access(new R(null, "1.2.3.4"), db);
		service.access(new R("bob", "1.2.3.4"), db);
	}

	@Test
	public void testCreate_Disabled() throws ServiceNotAuthorizedException,
			IOException {
		final StoredConfig cfg = db.getConfig();
		cfg.setBoolean("http", null, "getanyfile", false);
		cfg.save();

		try {
			service.access(new R(null, "1.2.3.4"), db);
			fail("Created session for anonymous user: null");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}

		try {
			service.access(new R("bob", "1.2.3.4"), db);
			fail("Created session for user: \"bob\"");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}
	}

	@Test
	public void testCreate_Enabled() throws ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		db.getConfig().setBoolean("http", null, "getanyfile", true);
		service.access(new R(null, "1.2.3.4"), db);
		service.access(new R("bob", "1.2.3.4"), db);
	}

	private static final class R extends HttpServletRequestWrapper {
		private final String user;

		private final String host;

		R(String user, String host) {
			super(new Request(null, null) /* can't pass null, sigh */);
			this.user = user;
			this.host = host;
		}

		@Override
		public String getRemoteHost() {
			return host;
		}

		@Override
		public String getRemoteUser() {
			return user;
		}
	}
}
