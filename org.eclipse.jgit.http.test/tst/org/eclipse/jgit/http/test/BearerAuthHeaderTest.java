/*
 * Copyright (C) 2026, JGit contributors and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee11.servlet.DefaultServlet;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.BearerCredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.junit.Before;
import org.junit.Test;

public class BearerAuthHeaderTest extends AllFactoriesHttpTestCase {

	private URIish remoteURI;

	private URIish unauthorizedURI;

	public BearerAuthHeaderTest(HttpConnectionFactory cf) {
		super(cf);
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final File srcGit = src.getRepository().getDirectory();
		final URI base = srcGit.getParentFile().toURI();

		ServletContextHandler app = server.addContext("/git");
		app.setBaseResourceAsString(base.toString());
		ServletHolder holder = app.addServlet(DefaultServlet.class, "/");
		// The tmp directory is symlinked on OS X
		holder.setInitParameter("aliases", "true");

		ServletContextHandler unauthorized = server.addContext("/unauthorized");
		unauthorized.addServlet(new ServletHolder(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req,
					HttpServletResponse resp) throws IOException {
				resp.setHeader("WWW-Authenticate", "Bearer");
				resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			}
		}), "/*");

		server.setUp();

		remoteURI = toURIish(app, srcGit.getName());
		unauthorizedURI = toURIish(unauthorized, srcGit.getName());

		RevCommit a = src.commit().add("A_txt", src.blob("A")).create();
		src.update(master, src.commit().parent(a).add("B", "B").create());
	}

	@Test
	public void testBearerHeaderSentPreemptively() throws Exception {
		Repository dst = createBareRepository();
		assertEquals("http", remoteURI.getScheme());

		try (Transport t = Transport.open(dst, remoteURI)) {
			assertTrue("isa TransportHttp", t instanceof TransportHttp);
			t.setCredentialsProvider(new BearerCredentialsProvider("tok-123"));
			t.openFetch();
		}

		List<AccessEvent> requests = getRequests();
		assertFalse("expected requests", requests.isEmpty());
		// Preemptive: the first request already carries the token and gets 200.
		assertEquals("Bearer tok-123",
				requests.get(0).getRequestHeader("Authorization"));
		assertEquals(200, requests.get(0).getStatus());
		for (AccessEvent info : requests) {
			assertEquals("GET", info.getMethod());
			assertEquals("Bearer tok-123",
					info.getRequestHeader("Authorization"));
		}
	}

	@Test
	public void testRejectedBearerReportsNotAuthorized() throws Exception {
		Repository dst = createBareRepository();

		try (Transport t = Transport.open(dst, unauthorizedURI)) {
			t.setCredentialsProvider(new BearerCredentialsProvider("bad"));
			TransportException e = assertThrows(TransportException.class,
					() -> t.openFetch());
			assertTrue(e.getMessage(),
					e.getMessage().contains(JGitText.get().notAuthorized));
		}
	}
}
