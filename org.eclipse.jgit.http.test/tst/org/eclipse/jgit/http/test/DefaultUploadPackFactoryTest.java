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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.eclipse.jetty.server.Request;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultUploadPackFactoryTest extends LocalDiskRepositoryTestCase {
	private Repository db;

	private UploadPackFactory<HttpServletRequest> factory;

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();

		db = createBareRepository();
		factory = new DefaultUploadPackFactory();
	}

	@SuppressWarnings("unchecked")
	@Test
	void testDisabledSingleton() throws ServiceNotAuthorizedException {
		factory = (UploadPackFactory<HttpServletRequest>) UploadPackFactory.DISABLED;

		try {
			factory.create(new R(null, "localhost"), db);
			fail("Created session for anonymous user: null");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}

		try {
			factory.create(new R("", "localhost"), db);
			fail("Created session for anonymous user: \"\"");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}

		try {
			factory.create(new R("bob", "localhost"), db);
			fail("Created session for user: \"bob\"");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}
	}

	@Test
	void testCreate_Default()
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {
		UploadPack up;

		up = factory.create(new R(null, "1.2.3.4"), db);
		assertNotNull(up, "have UploadPack");
		assertSame(db, up.getRepository());

		up = factory.create(new R("bob", "1.2.3.4"), db);
		assertNotNull(up, "have UploadPack");
		assertSame(db, up.getRepository());
	}

	@Test
	void testCreate_Disabled()
			throws ServiceNotAuthorizedException, IOException {
		final StoredConfig cfg = db.getConfig();
		cfg.setBoolean("http", null, "uploadpack", false);
		cfg.save();

		try {
			factory.create(new R(null, "localhost"), db);
			fail("Created session for anonymous user: null");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}

		try {
			factory.create(new R("bob", "localhost"), db);
			fail("Created session for user: \"bob\"");
		} catch (ServiceNotEnabledException e) {
			// expected not authorized
		}
	}

	@Test
	void testCreate_Enabled()
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {
		db.getConfig().setBoolean("http", null, "uploadpack", true);
		UploadPack up;

		up = factory.create(new R(null, "1.2.3.4"), db);
		assertNotNull(up, "have UploadPack");
		assertSame(db, up.getRepository());

		up = factory.create(new R("bob", "1.2.3.4"), db);
		assertNotNull(up, "have UploadPack");
		assertSame(db, up.getRepository());
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
