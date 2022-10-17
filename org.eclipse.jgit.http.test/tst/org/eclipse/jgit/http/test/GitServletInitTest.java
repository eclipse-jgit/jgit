/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import javax.servlet.ServletException;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.junit.http.MockServletConfig;
import org.eclipse.jgit.junit.http.RecordingLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class GitServletInitTest {
	private AppServer server;

	@AfterEach
	public void tearDown() throws Exception {
		if (server != null) {
			server.tearDown();
			server = null;
		}
	}

	@Test
	void testDefaultConstructor_NoBasePath() throws Exception {
		GitServlet s = new GitServlet();
		try {
			s.init(new MockServletConfig());
			fail("Init did not crash due to missing parameter");
		} catch (ServletException e) {
			assertTrue(e.getMessage().contains("base-path"));
		}
	}

	@Test
	void testDefaultConstructor_WithBasePath() throws Exception {
		MockServletConfig c = new MockServletConfig();
		c.setInitParameter("base-path",".");
		c.setInitParameter("export-all","false");

		GitServlet s = new GitServlet();
		s.init(c);
		s.destroy();
	}

	@Test
	void testInitUnderContainer_NoBasePath() throws Exception {
		server = new AppServer();

		ServletContextHandler app = server.addContext("/");
		ServletHolder s = app.addServlet(GitServlet.class,"/git");
		s.setInitOrder(1);
		s.getServletHandler().setStartWithUnavailable(false);
		// The tmp directory is symlinked on OS X
		s.setInitParameter("aliases","true");

		try {
			server.setUp();
		} catch (Exception e) {
			Throwable why = null;
			if (e instanceof MultiException) {
				MultiException multi = (MultiException) e;
				List<Throwable> reasons = multi.getThrowables();
				why = reasons.get(0);
				assertTrue(why instanceof ServletException,
						"Expected ServletException");
			} else if (e instanceof ServletException)
				why = e;

			if (why != null) {
				assertTrue(why.getMessage().contains("base-path"),
						"Wanted base-path");
				return;
			}
		}
		fail("Expected ServletException complaining about unset base-path");
	}

	@Test
	void testInitUnderContainer_WithBasePath() throws Exception {
		server = new AppServer();

		ServletContextHandler app = server.addContext("/");
		ServletHolder s = app.addServlet(GitServlet.class,"/git");
		s.setInitOrder(1);
		s.setInitParameter("base-path",".");
		s.setInitParameter("export-all","true");
		// The tmp directory is symlinked on OS X
		s.setInitParameter("aliases","true");

		server.setUp();
		assertTrue(RecordingLogger.getWarnings().isEmpty(),"no warnings");
	}
}
