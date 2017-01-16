/*
 * Copyright (C) 2012, Google Inc.
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
 *	 notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *	 copyright notice, this list of conditions and the following
 *	 disclaimer in the documentation and/or other materials provided
 *	 with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *	 names of its contributors may be used to endorse or promote
 *	 products derived from this software without specific prior
 *	 written permission.
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

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.http.server.glue.RegexGroupFilter;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RegexPipelineTest extends HttpTestCase {
	private ServletContextHandler ctx;

	private static class Servlet extends HttpServlet {
		private static final long serialVersionUID = 1L;

		private final String name;

		private Servlet(String name) {
			this.name = name;
		}

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse res)
				throws IOException {
			res.setStatus(200);
			PrintWriter out = new PrintWriter(res.getOutputStream());
			out.write(name);
			out.write("\n");
			out.write(String.valueOf(req.getServletPath()));
			out.write("\n");
			out.write(String.valueOf(req.getPathInfo()));
			out.write("\n");
			out.flush();
		}
	}

	@Override
	@Before
	public void setUp() throws Exception {
		server = new AppServer();
		ctx = server.addContext("/");
	}

	@Override
	@After
	public void tearDown() throws Exception {
		server.tearDown();
	}

	@Test
	public void testSimpleRegex() throws Exception {
		MetaServlet s = new MetaServlet();
		s.serveRegex("^(/a|/b)$").with(new Servlet("test"));
		ctx.addServlet(new ServletHolder(s), "/*");
		server.setUp();

		final URI uri = server.getURI();
		HttpURLConnection c;
		BufferedReader r;

		c = ((HttpURLConnection) uri.resolve("/a").toURL()
				.openConnection());
		assertEquals(200, c.getResponseCode());
		r = new BufferedReader(new InputStreamReader(c.getInputStream()));
		assertEquals("test", r.readLine());
		assertEquals("", r.readLine());
		assertEquals("/a", r.readLine());
		assertEquals(null, r.readLine());

		c = ((HttpURLConnection) uri.resolve("/b").toURL()
				.openConnection());
		assertEquals(200, c.getResponseCode());
		r = new BufferedReader(new InputStreamReader(c.getInputStream()));
		assertEquals("test", r.readLine());
		assertEquals("", r.readLine());
		assertEquals("/b", r.readLine());
		assertEquals(null, r.readLine());

		c = ((HttpURLConnection) uri.resolve("/c").toURL()
				.openConnection());
		assertEquals(404, c.getResponseCode());
	}

	@Test
	public void testServeOrdering() throws Exception {
		MetaServlet s = new MetaServlet();
		s.serveRegex("^(/a)$").with(new Servlet("test1"));
		s.serveRegex("^(/a+)$").with(new Servlet("test2"));
		ctx.addServlet(new ServletHolder(s), "/*");
		server.setUp();

		final URI uri = server.getURI();
		HttpURLConnection c;
		BufferedReader r;

		c = ((HttpURLConnection) uri.resolve("/a").toURL()
				.openConnection());
		assertEquals(200, c.getResponseCode());
		r = new BufferedReader(new InputStreamReader(c.getInputStream()));
		assertEquals("test1", r.readLine());
		assertEquals("", r.readLine());
		assertEquals("/a", r.readLine());
		assertEquals(null, r.readLine());
	}

	@Test
	public void testRegexGroupFilter() throws Exception {
		MetaServlet s = new MetaServlet();
		s.serveRegex("^(/a)(/b)$")
				.with(new Servlet("test1"));
		s.serveRegex("^(/c)(/d)$")
				.through(new RegexGroupFilter(1))
				.with(new Servlet("test2"));
		s.serveRegex("^(/e)/f(/g)$")
				.through(new RegexGroupFilter(2))
				.with(new Servlet("test3"));
		ctx.addServlet(new ServletHolder(s), "/*");
		server.setUp();

		final URI uri = server.getURI();
		HttpURLConnection c;
		BufferedReader r;

		c = ((HttpURLConnection) uri.resolve("/a/b").toURL()
				.openConnection());
		assertEquals(200, c.getResponseCode());
		r = new BufferedReader(new InputStreamReader(c.getInputStream()));
		assertEquals("test1", r.readLine());
		assertEquals("", r.readLine());
		// No RegexGroupFilter defaults to first group.
		assertEquals("/a", r.readLine());
		assertEquals(null, r.readLine());

		c = ((HttpURLConnection) uri.resolve("/c/d").toURL()
				.openConnection());
		assertEquals(200, c.getResponseCode());
		r = new BufferedReader(new InputStreamReader(c.getInputStream()));
		assertEquals("test2", r.readLine());
		assertEquals("", r.readLine());
		assertEquals("/c", r.readLine());
		assertEquals(null, r.readLine());

		c = ((HttpURLConnection) uri.resolve("/e/f/g").toURL()
				.openConnection());
		assertEquals(200, c.getResponseCode());
		r = new BufferedReader(new InputStreamReader(c.getInputStream()));
		assertEquals("test3", r.readLine());
		assertEquals("/e/f", r.readLine());
		assertEquals("/g", r.readLine());
		assertEquals(null, r.readLine());
	}
}
