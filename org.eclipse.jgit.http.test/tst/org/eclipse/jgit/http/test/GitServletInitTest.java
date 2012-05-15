/*
 * Copyright (C) 2010, Google Inc.
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.servlet.ServletException;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.junit.http.MockServletConfig;
import org.eclipse.jgit.junit.http.RecordingLogger;
import org.junit.After;
import org.junit.Test;

public class GitServletInitTest {
	private AppServer server;

	@After
	public void tearDown() throws Exception {
		if (server != null) {
			server.tearDown();
			server = null;
		}
	}

	@Test
	public void testDefaultConstructor_NoBasePath() throws Exception {
		GitServlet s = new GitServlet();
		try {
			s.init(new MockServletConfig());
			fail("Init did not crash due to missing parameter");
		} catch (ServletException e) {
			assertTrue(e.getMessage().contains("base-path"));
		}
	}

	@Test
	public void testDefaultConstructor_WithBasePath() throws Exception {
		MockServletConfig c = new MockServletConfig();
		c.setInitParameter("base-path", ".");
		c.setInitParameter("export-all", "false");

		GitServlet s = new GitServlet();
		s.init(c);
		s.destroy();
	}

	@Test
	public void testInitUnderContainer_NoBasePath() throws Exception {
		server = new AppServer();

		ServletContextHandler app = server.addContext("/");
		ServletHolder s = app.addServlet(GitServlet.class, "/git");
		s.setInitOrder(1);
		s.getServletHandler().setStartWithUnavailable(false);

		try {
			server.setUp();
		} catch (Exception e) {
			Throwable why = null;
			if (e instanceof MultiException) {
				MultiException multi = (MultiException) e;
				List<Throwable> reasons = multi.getThrowables();
				why = reasons.get(0);
				assertTrue("Expected ServletException",
						why instanceof ServletException);
			} else if (e instanceof ServletException)
				why = e;

			if (why != null) {
				assertTrue("Wanted base-path",
						why.getMessage().contains("base-path"));
				return;
			}
		}
		fail("Expected ServletException complaining about unset base-path");
	}

	@Test
	public void testInitUnderContainer_WithBasePath() throws Exception {
		server = new AppServer();

		ServletContextHandler app = server.addContext("/");
		ServletHolder s = app.addServlet(GitServlet.class, "/git");
		s.setInitOrder(1);
		s.setInitParameter("base-path", ".");
		s.setInitParameter("export-all", "true");

		server.setUp();
		assertTrue("no warnings", RecordingLogger.getWarnings().isEmpty());
	}
}
