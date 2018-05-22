/*
 * Copyright (C) 2009-2010, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

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
import org.junit.Before;
import org.junit.Test;

public class DefaultUploadPackFactoryTest extends LocalDiskRepositoryTestCase {
	private Repository db;

	private UploadPackFactory<HttpServletRequest> factory;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		db = createBareRepository();
		factory = new DefaultUploadPackFactory();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDisabledSingleton() throws ServiceNotAuthorizedException {
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
	public void testCreate_Default() throws ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		UploadPack up;

		up = factory.create(new R(null, "1.2.3.4"), db);
		assertNotNull("have UploadPack", up);
		assertSame(db, up.getRepository());

		up = factory.create(new R("bob", "1.2.3.4"), db);
		assertNotNull("have UploadPack", up);
		assertSame(db, up.getRepository());
	}

	@Test
	public void testCreate_Disabled() throws ServiceNotAuthorizedException,
			IOException {
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
	public void testCreate_Enabled() throws ServiceNotEnabledException,
			ServiceNotAuthorizedException {
		db.getConfig().setBoolean("http", null, "uploadpack", true);
		UploadPack up;

		up = factory.create(new R(null, "1.2.3.4"), db);
		assertNotNull("have UploadPack", up);
		assertSame(db, up.getRepository());

		up = factory.create(new R("bob", "1.2.3.4"), db);
		assertNotNull("have UploadPack", up);
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
