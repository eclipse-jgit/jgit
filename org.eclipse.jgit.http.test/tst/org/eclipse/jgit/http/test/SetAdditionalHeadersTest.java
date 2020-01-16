/*
 * Copyright (C) 2014, IBM Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.junit.Before;
import org.junit.Test;

public class SetAdditionalHeadersTest extends AllFactoriesHttpTestCase {

	private URIish remoteURI;

	private RevBlob A_txt;

	private RevCommit A, B;

	public SetAdditionalHeadersTest(HttpConnectionFactory cf) {
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
		app.setResourceBase(base.toString());
		ServletHolder holder = app.addServlet(DefaultServlet.class, "/");
		// The tmp directory is symlinked on OS X
		holder.setInitParameter("aliases", "true");
		server.setUp();

		remoteURI = toURIish(app, srcGit.getName());

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);
	}

	@Test
	public void testSetHeaders() throws IOException {
		Repository dst = createBareRepository();

		assertEquals("http", remoteURI.getScheme());

		try (Transport t = Transport.open(dst, remoteURI)) {
			assertTrue("isa TransportHttp", t instanceof TransportHttp);
			assertTrue("isa HttpTransport", t instanceof HttpTransport);

			HashMap<String, String> headers = new HashMap<>();
			headers.put("Cookie", "someTokenValue=23gBog34");
			headers.put("AnotherKey", "someValue");
			((TransportHttp) t).setAdditionalHeaders(headers);
			t.openFetch();
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(info.getRequestHeader("Cookie"), "someTokenValue=23gBog34");
		assertEquals(info.getRequestHeader("AnotherKey"), "someValue");
		assertEquals(200, info.getStatus());

		info = requests.get(1);
		assertEquals("GET", info.getMethod());
		assertEquals(info.getRequestHeader("Cookie"), "someTokenValue=23gBog34");
		assertEquals(info.getRequestHeader("AnotherKey"), "someValue");
		assertEquals(200, info.getStatus());
	}

}
