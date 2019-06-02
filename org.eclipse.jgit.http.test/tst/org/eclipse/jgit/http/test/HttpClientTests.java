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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.junit.Before;
import org.junit.Test;

public class HttpClientTests extends HttpTestCase {
	private TestRepository<Repository> remoteRepository;

	private URIish dumbAuthNoneURI;

	private URIish dumbAuthBasicURI;

	private URIish smartAuthNoneURI;

	private URIish smartAuthBasicURI;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		remoteRepository = createTestRepository();
		remoteRepository.update(master, remoteRepository.commit().create());

		ServletContextHandler dNone = dumb("/dnone");
		ServletContextHandler dBasic = server.authBasic(dumb("/dbasic"));

		ServletContextHandler sNone = smart("/snone");
		ServletContextHandler sBasic = server.authBasic(smart("/sbasic"));

		server.setUp();

		final String srcName = nameOf(remoteRepository.getRepository());
		dumbAuthNoneURI = toURIish(dNone, srcName);
		dumbAuthBasicURI = toURIish(dBasic, srcName);

		smartAuthNoneURI = toURIish(sNone, srcName);
		smartAuthBasicURI = toURIish(sBasic, srcName);
	}

	private ServletContextHandler dumb(String path) {
		final File srcGit = remoteRepository.getRepository().getDirectory();
		final URI base = srcGit.getParentFile().toURI();

		ServletContextHandler ctx = server.addContext(path);
		ctx.setResourceBase(base.toString());
		ServletHolder holder = ctx.addServlet(DefaultServlet.class, "/");
		// The tmp directory is symlinked on OS X
		holder.setInitParameter("aliases", "true");
		return ctx;
	}

	private ServletContextHandler smart(String path) {
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver((HttpServletRequest req, String name) -> {
			final Repository db = remoteRepository.getRepository();
			if (!name.equals(nameOf(db))) {
				throw new RepositoryNotFoundException(name);
			}
			db.incrementOpen();
			return db;
		});

		ServletContextHandler ctx = server.addContext(path);
		ctx.addServlet(new ServletHolder(gs), "/*");
		return ctx;
	}

	private static String nameOf(Repository db) {
		return db.getDirectory().getName();
	}

	@Test
	public void testRepositoryNotFound_Dumb() throws Exception {
		URIish uri = toURIish("/dumb.none/not-found");
		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, uri)) {
			try {
				t.openFetch();
				fail("connection opened to not found repository");
			} catch (NoRemoteRepositoryException err) {
				String exp = uri + ": " + uri
						+ "/info/refs?service=git-upload-pack not found";
				assertNotNull(err.getMessage());
				assertTrue("Unexpected error message",
						err.getMessage().startsWith(exp));
			}
		}
	}

	@Test
	public void testRepositoryNotFound_Smart() throws Exception {
		URIish uri = toURIish("/smart.none/not-found");
		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, uri)) {
			try {
				t.openFetch();
				fail("connection opened to not found repository");
			} catch (NoRemoteRepositoryException err) {
				String exp = uri + ": " + uri
						+ "/info/refs?service=git-upload-pack not found";
				assertNotNull(err.getMessage());
				assertTrue("Unexpected error message",
						err.getMessage().startsWith(exp));
			}
		}
	}

	@Test
	public void testListRemote_Dumb_DetachedHEAD() throws Exception {
		Repository src = remoteRepository.getRepository();
		RefUpdate u = src.updateRef(Constants.HEAD, true);
		RevCommit Q = remoteRepository.commit().message("Q").create();
		u.setNewObjectId(Q);
		assertEquals(RefUpdate.Result.FORCED, u.forceUpdate());

		Repository dst = createBareRepository();
		Ref head;
		try (Transport t = Transport.open(dst, dumbAuthNoneURI);
				FetchConnection c = t.openFetch()) {
			head = c.getRef(Constants.HEAD);
		}
		assertNotNull("has " + Constants.HEAD, head);
		assertEquals(Q, head.getObjectId());
	}

	@Test
	public void testListRemote_Dumb_NoHEAD() throws Exception {
		Repository src = remoteRepository.getRepository();
		File headref = new File(src.getDirectory(), Constants.HEAD);
		assertTrue("HEAD used to be present", headref.delete());
		assertFalse("HEAD is gone", headref.exists());

		Repository dst = createBareRepository();
		Ref head;
		try (Transport t = Transport.open(dst, dumbAuthNoneURI);
				FetchConnection c = t.openFetch()) {
			head = c.getRef(Constants.HEAD);
		}
		assertNull("has no " + Constants.HEAD, head);
	}

	@Test
	public void testListRemote_Smart_DetachedHEAD() throws Exception {
		Repository src = remoteRepository.getRepository();
		RefUpdate u = src.updateRef(Constants.HEAD, true);
		RevCommit Q = remoteRepository.commit().message("Q").create();
		u.setNewObjectId(Q);
		assertEquals(RefUpdate.Result.FORCED, u.forceUpdate());

		Repository dst = createBareRepository();
		Ref head;
		try (Transport t = Transport.open(dst, smartAuthNoneURI);
				FetchConnection c = t.openFetch()) {
			head = c.getRef(Constants.HEAD);
		}
		assertNotNull("has " + Constants.HEAD, head);
		assertEquals(Q, head.getObjectId());
	}

	@Test
	public void testListRemote_Smart_WithQueryParameters() throws Exception {
		URIish myURI = toURIish("/snone/do?r=1&p=test.git");
		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, myURI)) {
			try {
				t.openFetch();
				fail("test did not fail to find repository as expected");
			} catch (NoRemoteRepositoryException err) {
				// expected
			}
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals("/snone/do", info.getPath());
		assertEquals(3, info.getParameters().size());
		assertEquals("1", info.getParameter("r"));
		assertEquals("test.git/info/refs", info.getParameter("p"));
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(404, info.getStatus());
	}

	@Test
	public void testListRemote_Dumb_NeedsAuth() throws Exception {
		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, dumbAuthBasicURI)) {
			try {
				t.openFetch();
				fail("connection opened even info/refs needs auth basic");
			} catch (TransportException err) {
				String exp = dumbAuthBasicURI + ": "
						+ JGitText.get().noCredentialsProvider;
				assertEquals(exp, err.getMessage());
			}
		}
	}

	@Test
	public void testListRemote_Dumb_Auth() throws Exception {
		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, dumbAuthBasicURI)) {
			t.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
					AppServer.username, AppServer.password));
			t.openFetch().close();
		}
		try (Transport t = Transport.open(dst, dumbAuthBasicURI)) {
			t.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
					AppServer.username, ""));
			try {
				t.openFetch();
				fail("connection opened even info/refs needs auth basic and we provide wrong password");
			} catch (TransportException err) {
				String exp = dumbAuthBasicURI + ": "
						+ JGitText.get().notAuthorized;
				assertEquals(exp, err.getMessage());
			}
		}
	}

	@Test
	public void testListRemote_Smart_UploadPackNeedsAuth() throws Exception {
		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, smartAuthBasicURI)) {
			try {
				t.openFetch();
				fail("connection opened even though service disabled");
			} catch (TransportException err) {
				String exp = smartAuthBasicURI + ": "
						+ JGitText.get().noCredentialsProvider;
				assertEquals(exp, err.getMessage());
			}
		}
	}

	@Test
	public void testListRemote_Smart_UploadPackDisabled() throws Exception {
		Repository src = remoteRepository.getRepository();
		final StoredConfig cfg = src.getConfig();
		cfg.setBoolean("http", null, "uploadpack", false);
		cfg.save();

		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, smartAuthNoneURI)) {
			try {
				t.openFetch();
				fail("connection opened even though service disabled");
			} catch (TransportException err) {
				String exp = smartAuthNoneURI + ": "
						+ JGitText.get().serviceNotEnabledNoName;
				assertEquals(exp, err.getMessage());
			}
		}
	}

	@Test
	public void testListRemoteWithoutLocalRepository() throws Exception {
		try (Transport t = Transport.open(smartAuthNoneURI);
				FetchConnection c = t.openFetch()) {
			Ref head = c.getRef(Constants.HEAD);
			assertNotNull(head);
		}
	}

	@Test
	public void testHttpClientWantsV2ButServerNotConfigured() throws Exception {
		JDKHttpConnectionFactory f = new JDKHttpConnectionFactory();
		String url = smartAuthNoneURI.toString() + "/info/refs?service=git-upload-pack";
		HttpConnection c = f.create(new URL(url));
		c.setRequestMethod("GET");
		c.setRequestProperty("Git-Protocol", "version=2");
		c.connect();
		assertEquals(200, c.getResponseCode());

		PacketLineIn pckIn = new PacketLineIn(c.getInputStream());

		// Check that we get a v0 response.
		assertThat(pckIn.readString(), is("# service=git-upload-pack"));
		assertTrue(PacketLineIn.isEnd(pckIn.readString()));
		assertTrue(pckIn.readString().matches("[0-9a-f]{40} HEAD.*"));
	}

	@Test
	public void testV2HttpFirstResponse() throws Exception {
		remoteRepository.getRepository().getConfig().setInt(
				"protocol", null, "version", 2);

		JDKHttpConnectionFactory f = new JDKHttpConnectionFactory();
		String url = smartAuthNoneURI.toString() + "/info/refs?service=git-upload-pack";
		HttpConnection c = f.create(new URL(url));
		c.setRequestMethod("GET");
		c.setRequestProperty("Git-Protocol", "version=2");
		c.connect();
		assertEquals(200, c.getResponseCode());

		PacketLineIn pckIn = new PacketLineIn(c.getInputStream());
		assertThat(pckIn.readString(), is("version 2"));

		// What remains are capabilities - ensure that all of them are
		// non-empty strings, and that we see END at the end.
		String s;
		while ((s = pckIn.readString()) != PacketLineIn.END) {
			assertTrue(!s.isEmpty());
		}
	}

	@Test
	public void testV2HttpSubsequentResponse() throws Exception {
		remoteRepository.getRepository().getConfig().setInt(
				"protocol", null, "version", 2);

		JDKHttpConnectionFactory f = new JDKHttpConnectionFactory();
		String url = smartAuthNoneURI.toString() + "/git-upload-pack";
		HttpConnection c = f.create(new URL(url));
		c.setRequestMethod("POST");
		c.setRequestProperty("Content-Type", "application/x-git-upload-pack-request");
		c.setRequestProperty("Git-Protocol", "version=2");
		c.setDoOutput(true);
		c.connect();

		// Test ls-refs to verify that everything is connected
		// properly. Tests for other commands go in
		// UploadPackTest.java.

		try (OutputStream os = c.getOutputStream()) {
			PacketLineOut pckOut = new PacketLineOut(os);
			pckOut.writeString("command=ls-refs");
			pckOut.writeDelim();
			pckOut.end();
		}

		PacketLineIn pckIn = new PacketLineIn(c.getInputStream());

		// Just check that we get what looks like a ref advertisement.
		String s;
		while ((s = pckIn.readString()) != PacketLineIn.END) {
			assertTrue(s.matches("[0-9a-f]{40} [A-Za-z/]*"));
		}

		assertEquals(200, c.getResponseCode());
	}
}
