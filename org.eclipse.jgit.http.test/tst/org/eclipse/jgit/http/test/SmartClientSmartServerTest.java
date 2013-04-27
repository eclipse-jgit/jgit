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

import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_LENGTH;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.RemoteRepositoryException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.Before;
import org.junit.Test;

public class SmartClientSmartServerTest extends HttpTestCase {
	private static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";

	private Repository remoteRepository;

	private URIish remoteURI;

	private URIish brokenURI;

	private RevBlob A_txt;

	private RevCommit A, B;

	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final String srcName = src.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver(new RepositoryResolver<HttpServletRequest>() {
			public Repository open(HttpServletRequest req, String name)
					throws RepositoryNotFoundException,
					ServiceNotEnabledException {
				if (!name.equals(srcName))
					throw new RepositoryNotFoundException(name);

				final Repository db = src.getRepository();
				db.incrementOpen();
				return db;
			}
		});
		app.addServlet(new ServletHolder(gs), "/*");

		ServletContextHandler broken = server.addContext("/bad");
		broken.addFilter(new FilterHolder(new Filter() {
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				final HttpServletResponse r = (HttpServletResponse) response;
				r.setContentType("text/plain");
				r.setCharacterEncoding("UTF-8");
				PrintWriter w = r.getWriter();
				w.print("OK");
				w.close();
			}

			public void init(FilterConfig filterConfig) throws ServletException {
				//
			}

			public void destroy() {
				//
			}
		}), "/" + srcName + "/git-upload-pack", FilterMapping.DEFAULT);
		broken.addServlet(new ServletHolder(gs), "/*");

		server.setUp();

		remoteRepository = src.getRepository();
		remoteURI = toURIish(app, srcName);
		brokenURI = toURIish(broken, srcName);

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);

		src.update("refs/garbage/a/very/long/ref/name/to/compress", B);
	}

	@Test
	public void testListRemote() throws IOException {
		Repository dst = createBareRepository();

		assertEquals("http", remoteURI.getScheme());

		Map<String, Ref> map;
		Transport t = Transport.open(dst, remoteURI);
		try {
			// I didn't make up these public interface names, I just
			// approved them for inclusion into the code base. Sorry.
			// --spearce
			//
			assertTrue("isa TransportHttp", t instanceof TransportHttp);
			assertTrue("isa HttpTransport", t instanceof HttpTransport);

			FetchConnection c = t.openFetch();
			try {
				map = c.getRefsMap();
			} finally {
				c.close();
			}
		} finally {
			t.close();
		}

		assertNotNull("have map of refs", map);
		assertEquals(3, map.size());

		assertNotNull("has " + master, map.get(master));
		assertEquals(B, map.get(master).getObjectId());

		assertNotNull("has " + Constants.HEAD, map.get(Constants.HEAD));
		assertEquals(B, map.get(Constants.HEAD).getObjectId());

		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement", info
				.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
	}

	@Test
	public void testListRemote_BadName() throws IOException, URISyntaxException {
		Repository dst = createBareRepository();
		URIish uri = new URIish(this.remoteURI.toString() + ".invalid");
		Transport t = Transport.open(dst, uri);
		try {
			try {
				t.openFetch();
				fail("fetch connection opened");
			} catch (RemoteRepositoryException notFound) {
				assertEquals(uri + ": Git repository not found",
						notFound.getMessage());
			}
		} finally {
			t.close();
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(uri, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testInitialClone_Small() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.hasObject(A_txt));

		Transport t = Transport.open(dst, remoteURI);
		try {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		} finally {
			t.close();
		}

		assertTrue(dst.hasObject(A_txt));
		assertEquals(B, dst.getRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement", info
				.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull("has content-length", service
				.getRequestHeader(HDR_CONTENT_LENGTH));
		assertNull("not chunked", service
				.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result", service
				.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testFetch_FewLocalCommits() throws Exception {
		// Bootstrap by doing the clone.
		//
		TestRepository dst = createTestRepository();
		Transport t = Transport.open(dst.getRepository(), remoteURI);
		try {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		} finally {
			t.close();
		}
		assertEquals(B, dst.getRepository().getRef(master).getObjectId());
		List<AccessEvent> cloneRequests = getRequests();

		// Only create a few new commits.
		TestRepository.BranchBuilder b = dst.branch(master);
		for (int i = 0; i < 4; i++)
			b.commit().tick(3600 /* 1 hour */).message("c" + i).create();

		// Create a new commit on the remote.
		//
		b = new TestRepository<Repository>(remoteRepository).branch(master);
		RevCommit Z = b.commit().message("Z").create();

		// Now incrementally update.
		//
		t = Transport.open(dst.getRepository(), remoteURI);
		try {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		} finally {
			t.close();
		}
		assertEquals(Z, dst.getRepository().getRef(master).getObjectId());

		List<AccessEvent> requests = getRequests();
		requests.removeAll(cloneRequests);
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		// We should have needed one request to perform the fetch.
		//
		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull("has content-length",
				service.getRequestHeader(HDR_CONTENT_LENGTH));
		assertNull("not chunked",
				service.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testFetch_TooManyLocalCommits() throws Exception {
		// Bootstrap by doing the clone.
		//
		TestRepository dst = createTestRepository();
		Transport t = Transport.open(dst.getRepository(), remoteURI);
		try {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		} finally {
			t.close();
		}
		assertEquals(B, dst.getRepository().getRef(master).getObjectId());
		List<AccessEvent> cloneRequests = getRequests();

		// Force enough into the local client that enumeration will
		// need multiple packets, but not too many to overflow and
		// not pick up the ACK_COMMON message.
		//
		TestRepository.BranchBuilder b = dst.branch(master);
		for (int i = 0; i < 32 - 1; i++)
			b.commit().tick(3600 /* 1 hour */).message("c" + i).create();

		// Create a new commit on the remote.
		//
		b = new TestRepository<Repository>(remoteRepository).branch(master);
		RevCommit Z = b.commit().message("Z").create();

		// Now incrementally update.
		//
		t = Transport.open(dst.getRepository(), remoteURI);
		try {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		} finally {
			t.close();
		}
		assertEquals(Z, dst.getRepository().getRef(master).getObjectId());

		List<AccessEvent> requests = getRequests();
		requests.removeAll(cloneRequests);
		assertEquals(3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement", info
				.getResponseHeader(HDR_CONTENT_TYPE));

		// We should have needed two requests to perform the fetch
		// due to the high number of local unknown commits.
		//
		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull("has content-length", service
				.getRequestHeader(HDR_CONTENT_LENGTH));
		assertNull("not chunked", service
				.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result", service
				.getResponseHeader(HDR_CONTENT_TYPE));

		service = requests.get(2);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull("has content-length", service
				.getRequestHeader(HDR_CONTENT_LENGTH));
		assertNull("not chunked", service
				.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result", service
				.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testInitialClone_BrokenServer() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.hasObject(A_txt));

		Transport t = Transport.open(dst, brokenURI);
		try {
			try {
				t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
				fail("fetch completed despite upload-pack being broken");
			} catch (TransportException err) {
				String exp = brokenURI + ": expected"
						+ " Content-Type application/x-git-upload-pack-result;"
						+ " received Content-Type text/plain;charset=UTF-8";
				assertEquals(exp, err.getMessage());
			}
		} finally {
			t.close();
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(brokenURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement", info
				.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(brokenURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertEquals(200, service.getStatus());
		assertEquals("text/plain;charset=UTF-8", service
				.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testPush_NotAuthorized() throws Exception {
		final TestRepository src = createTestRepository();
		final RevBlob Q_txt = src.blob("new text");
		final RevCommit Q = src.commit().add("Q", Q_txt).create();
		final Repository db = src.getRepository();
		final String dstName = Constants.R_HEADS + "new.branch";
		Transport t;

		// push anonymous shouldn't be allowed.
		//
		t = Transport.open(db, remoteURI);
		try {
			final String srcExpr = Q.name();
			final boolean forceUpdate = false;
			final String localName = null;
			final ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			try {
				t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
				fail("anonymous push incorrectly accepted without error");
			} catch (TransportException e) {
				final String exp = remoteURI + ": "
						+ JGitText.get().authenticationNotSupported;
				assertEquals(exp, e.getMessage());
			}
		} finally {
			t.close();
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-receive-pack", info.getParameter("service"));
		assertEquals(401, info.getStatus());
	}

	@Test
	public void testPush_CreateBranch() throws Exception {
		final TestRepository src = createTestRepository();
		final RevBlob Q_txt = src.blob("new text");
		final RevCommit Q = src.commit().add("Q", Q_txt).create();
		final Repository db = src.getRepository();
		final String dstName = Constants.R_HEADS + "new.branch";
		Transport t;

		enableReceivePack();

		t = Transport.open(db, remoteURI);
		try {
			final String srcExpr = Q.name();
			final boolean forceUpdate = false;
			final String localName = null;
			final ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
		} finally {
			t.close();
		}

		assertTrue(remoteRepository.hasObject(Q_txt));
		assertNotNull("has " + dstName, remoteRepository.getRef(dstName));
		assertEquals(Q, remoteRepository.getRef(dstName).getObjectId());
		fsck(remoteRepository, Q);

		final ReflogReader log = remoteRepository.getReflogReader(dstName);
		assertNotNull("has log for " + dstName);

		final ReflogEntry last = log.getLastEntry();
		assertNotNull("has last entry", last);
		assertEquals(ObjectId.zeroId(), last.getOldId());
		assertEquals(Q, last.getNewId());
		assertEquals("anonymous", last.getWho().getName());

		// Assumption: The host name we use to contact the server should
		// be the server's own host name, because it should be the loopback
		// network interface.
		//
		final String clientHost = remoteURI.getHost();
		assertEquals("anonymous@" + clientHost, last.getWho().getEmailAddress());
		assertEquals("push: created", last.getComment());

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-receive-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-receive-pack-advertisement", info
				.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-receive-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull("has content-length", service
				.getRequestHeader(HDR_CONTENT_LENGTH));
		assertNull("not chunked", service
				.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-receive-pack-result", service
				.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testPush_ChunkedEncoding() throws Exception {
		final TestRepository<Repository> src = createTestRepository();
		final RevBlob Q_bin = src.blob(new TestRng("Q").nextBytes(128 * 1024));
		final RevCommit Q = src.commit().add("Q", Q_bin).create();
		final Repository db = src.getRepository();
		final String dstName = Constants.R_HEADS + "new.branch";
		Transport t;

		enableReceivePack();

		final StoredConfig cfg = db.getConfig();
		cfg.setInt("core", null, "compression", 0);
		cfg.setInt("http", null, "postbuffer", 8 * 1024);
		cfg.save();

		t = Transport.open(db, remoteURI);
		try {
			final String srcExpr = Q.name();
			final boolean forceUpdate = false;
			final String localName = null;
			final ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
		} finally {
			t.close();
		}

		assertTrue(remoteRepository.hasObject(Q_bin));
		assertNotNull("has " + dstName, remoteRepository.getRef(dstName));
		assertEquals(Q, remoteRepository.getRef(dstName).getObjectId());
		fsck(remoteRepository, Q);

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-receive-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-receive-pack-advertisement", info
				.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-receive-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNull("no content-length", service
				.getRequestHeader(HDR_CONTENT_LENGTH));
		assertEquals("chunked", service.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-receive-pack-result", service
				.getResponseHeader(HDR_CONTENT_TYPE));
	}

	private void enableReceivePack() throws IOException {
		final StoredConfig cfg = remoteRepository.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();
	}
}
