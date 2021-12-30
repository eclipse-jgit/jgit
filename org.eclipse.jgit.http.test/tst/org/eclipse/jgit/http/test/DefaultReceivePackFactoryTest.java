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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.eclipse.jetty.server.Request;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.Before;
import org.junit.Test;

public class DefaultReceivePackFactoryTest extends LocalDiskRepositoryTestCase {
	private Repository db;

	private ReceivePackFactory<HttpServletRequest> factory;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		db = createBareRepository();
		factory = new DefaultReceivePackFactory();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDisabledSingleton() throws ServiceNotAuthorizedException {
		factory = (ReceivePackFactory<HttpServletRequest>) ReceivePackFactory.DISABLED;

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
	public void testCreate_NullUser() throws ServiceNotEnabledException {
		try {
			factory.create(new R(null, "localhost"), db);
			fail("Created session for anonymous user: null");
		} catch (ServiceNotAuthorizedException e) {
			// expected not authorized
		}
	}

	@Test
	public void testCreate_EmptyStringUser() throws ServiceNotEnabledException {
		try {
			factory.create(new R("", "localhost"), db);
			fail("Created session for anonymous user: \"\"");
		} catch (ServiceNotAuthorizedException e) {
			// expected not authorized
		}
	}

	@Test
	public void testCreate_AuthUser() throws ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		ReceivePack rp;
		rp = factory.create(new R("bob", "1.2.3.4"), db);
		assertNotNull("have ReceivePack", rp);
		assertSame(db, rp.getRepository());

		PersonIdent id = rp.getRefLogIdent();
		assertNotNull(id);
		assertEquals("bob", id.getName());
		assertEquals("bob@1.2.3.4", id.getEmailAddress());

		// Should have inherited off the current system, which is mocked
		assertEquals(author.getTimeZoneOffset(), id.getTimeZoneOffset());
		assertEquals(author.getWhen(), id.getWhen());
	}

	@Test
	public void testCreate_Disabled() throws ServiceNotAuthorizedException,
			IOException {
		final StoredConfig cfg = db.getConfig();
		cfg.setBoolean("http", null, "receivepack", false);
		cfg.save();

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
	public void testCreate_Enabled() throws ServiceNotEnabledException,
			ServiceNotAuthorizedException, IOException {
		final StoredConfig cfg = db.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();

		ReceivePack rp;

		rp = factory.create(new R(null, "1.2.3.4"), db);
		assertNotNull("have ReceivePack", rp);
		assertSame(db, rp.getRepository());

		PersonIdent id = rp.getRefLogIdent();
		assertNotNull(id);
		assertEquals("anonymous", id.getName());
		assertEquals("anonymous@1.2.3.4", id.getEmailAddress());

		// Should have inherited off the current system, which is mocked
		assertEquals(author.getTimeZoneOffset(), id.getTimeZoneOffset());
		assertEquals(author.getWhen(), id.getWhen());

		rp = factory.create(new R("bob", "1.2.3.4"), db);
		assertNotNull("have ReceivePack", rp);
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
