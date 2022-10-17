/*
 * Copyright (C) 2010, 2017 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;
import static org.eclipse.jgit.util.HttpSupport.HDR_PRAGMA;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.junit.CustomParameterResolver;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CustomParameterResolver.class)
public class DumbClientSmartServerTest extends AllProtocolsHttpTestCase {
	private Repository remoteRepository;

	private URIish remoteURI;

	private RevBlob A_txt;

	private RevCommit A, B;

	@BeforeEach
	public void setUp(TestParameters params) throws Exception {
		super.setUp();
		configure(params);

		final TestRepository<Repository> src = createTestRepository();
		final String srcName = src.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver(new TestRepositoryResolver(src, srcName));
		app.addServlet(new ServletHolder(gs), "/*");

		server.setUp();

		remoteRepository = src.getRepository();
		remoteURI = toURIish(app, srcName);
		StoredConfig cfg = remoteRepository.getConfig();
		cfg.setInt("protocol", null, "version", enableProtocolV2 ? 2 : 0);
		cfg.save();

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);
	}

	@TestAllProtocols
	void testListRemote(@SuppressWarnings("unused") TestParameters params)
			throws IOException {
		Repository dst = createBareRepository();

		assertEquals("http", remoteURI.getScheme());

		Map<String, Ref> map;
		try (Transport t = Transport.open(dst, remoteURI)) {
			((TransportHttp) t).setUseSmartHttp(false);
			// I didn't make up these public interface names, I just
			// approved them for inclusion into the code base. Sorry.
			// --spearce
			//
			assertTrue(t instanceof TransportHttp, "isa TransportHttp");
			assertTrue(t instanceof HttpTransport, "isa HttpTransport");

			try (FetchConnection c = t.openFetch()) {
				map = c.getRefsMap();
			}
		}

		assertNotNull(map, "have map of refs");
		assertEquals(2, map.size());

		assertNotNull(map.get(master), "has " + master);
		assertEquals(B, map.get(master).getObjectId());

		assertNotNull(map.get(Constants.HEAD), "has " + Constants.HEAD);
		assertEquals(B, map.get(Constants.HEAD).getObjectId());

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());
		assertEquals(0, getRequests(remoteURI, "git-upload-pack").size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(0, info.getParameters().size());
		assertNull(info.getParameter("service"), "no service parameter");
		assertEquals("no-cache", info.getRequestHeader(HDR_PRAGMA));
		assertNotNull(info.getRequestHeader(HDR_USER_AGENT), "has user-agent");
		assertTrue(info.getRequestHeader(HDR_USER_AGENT).startsWith("JGit/"),
				"is jgit agent");
		assertEquals("*/*", info.getRequestHeader(HDR_ACCEPT));
		assertEquals(200, info.getStatus());
		assertEquals("text/plain;charset=utf-8",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent head = requests.get(1);
		assertEquals("GET", head.getMethod());
		assertEquals(join(remoteURI, "HEAD"), head.getPath());
		assertEquals(0, head.getParameters().size());
		assertEquals(200, head.getStatus());
		assertEquals("text/plain", head.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testInitialClone_Small(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			((TransportHttp) t).setUseSmartHttp(false);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> loose = getRequests(loose(remoteURI, A_txt));
		assertEquals(1, loose.size());
		assertEquals("GET", loose.get(0).getMethod());
		assertEquals(0, loose.get(0).getParameters().size());
		assertEquals(200, loose.get(0).getStatus());
		assertEquals("application/x-git-loose-object",
				loose.get(0).getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testInitialClone_Packed(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (TestRepository<Repository> tr = new TestRepository<>(
				remoteRepository)) {
			tr.packAndPrune();
		}

		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			((TransportHttp) t).setUseSmartHttp(false);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> req;

		req = getRequests(loose(remoteURI, B));
		assertEquals(1, req.size());
		assertEquals("GET", req.get(0).getMethod());
		assertEquals(0, req.get(0).getParameters().size());
		assertEquals(404, req.get(0).getStatus());

		req = getRequests(join(remoteURI, "objects/info/packs"));
		assertEquals(1, req.size());
		assertEquals("GET", req.get(0).getMethod());
		assertEquals(0, req.get(0).getParameters().size());
		assertEquals(200, req.get(0).getStatus());
		assertEquals("text/plain;charset=utf-8",
				req.get(0).getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testPushNotSupported(@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		TestRepository src = createTestRepository();
		RevCommit Q = src.commit().create();
		Repository db = src.getRepository();

		try (Transport t = Transport.open(db, remoteURI)) {
			((TransportHttp) t).setUseSmartHttp(false);
			try {
				t.push(NullProgressMonitor.INSTANCE, push(src, Q));
				fail("push incorrectly completed against a smart server");
			} catch (NotSupportedException nse) {
				String exp = "smart HTTP push disabled";
				assertEquals(exp, nse.getMessage());
			}
		}
	}
}
