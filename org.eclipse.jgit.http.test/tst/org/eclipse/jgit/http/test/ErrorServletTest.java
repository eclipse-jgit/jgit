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

import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.http.server.glue.ErrorServlet;
import org.eclipse.jgit.junit.http.AppServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ErrorServletTest {
	private AppServer server;

	@Before
	public void setUp() throws Exception {

		server = new AppServer();

		ServletContextHandler ctx = server.addContext("/");
		ctx.addServlet(new ServletHolder(new ErrorServlet(404)), "/404");
		ctx.addServlet(new ServletHolder(new ErrorServlet(500)), "/500");

		server.setUp();
	}

	@After
	public void tearDown() throws Exception {
		if (server != null) {
			server.tearDown();
		}
	}

	@Test
	public void testHandler() throws Exception {
		final URI uri = server.getURI();
		assertEquals(404, ((HttpURLConnection) uri.resolve("/404").toURL()
				.openConnection()).getResponseCode());
		assertEquals(500, ((HttpURLConnection) uri.resolve("/500").toURL()
				.openConnection()).getResponseCode());
	}
}
