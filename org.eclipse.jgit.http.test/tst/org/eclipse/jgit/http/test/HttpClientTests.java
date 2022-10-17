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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.CustomParameterResolver;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CustomParameterResolver.class)
public class HttpClientTests extends AllFactoriesHttpTestCase {

	private TestRepository<Repository> remoteRepository;

	private URIish dumbAuthNoneURI;

	private URIish dumbAuthBasicURI;

	private URIish smartAuthNoneURI;

	private URIish smartAuthBasicURI;

	@BeforeEach
	public void setUp(HttpConnectionFactory cf) throws Exception {
		super.setUp();
		HttpTransport.setConnectionFactory(cf);

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

	@TestAllImplementations
	void testRepositoryNotFound_Dumb(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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
				assertTrue(err.getMessage().startsWith(exp),
						"Unexpected error message");
			}
		}
	}

	@TestAllImplementations
	void testRepositoryNotFound_Smart(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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
				assertTrue(err.getMessage().startsWith(exp),
						"Unexpected error message");
			}
		}
	}

	@TestAllImplementations
	void testListRemote_Dumb_DetachedHEAD(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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
		assertNotNull(head, "has " + Constants.HEAD);
		assertEquals(Q, head.getObjectId());
	}

	@TestAllImplementations
	void testListRemote_Dumb_NoHEAD(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		Repository src = remoteRepository.getRepository();
		File headref = new File(src.getDirectory(), Constants.HEAD);
		assertTrue(headref.delete(), "HEAD used to be present");
		assertFalse(headref.exists(), "HEAD is gone");

		Repository dst = createBareRepository();
		Ref head;
		try (Transport t = Transport.open(dst, dumbAuthNoneURI);
				FetchConnection c = t.openFetch()) {
			head = c.getRef(Constants.HEAD);
		}
		assertNull(head, "has no " + Constants.HEAD);
	}

	@TestAllImplementations
	void testListRemote_Smart_DetachedHEAD(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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
		assertNotNull(head, "has " + Constants.HEAD);
		assertEquals(Q, head.getObjectId());
	}

	@TestAllImplementations
	void testListRemote_Smart_WithQueryParameters(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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

	@TestAllImplementations
	void testListRemote_Dumb_NeedsAuth(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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

	@TestAllImplementations
	void testListRemote_Dumb_Auth(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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

	@TestAllImplementations
	void testListRemote_Smart_UploadPackNeedsAuth(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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

	@TestAllImplementations
	void testListRemote_Smart_UploadPackDisabled(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
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
				String exp = smartAuthNoneURI + ": " + MessageFormat.format(
						JGitText.get().serviceNotPermitted,
						smartAuthNoneURI.toString() + "/", "git-upload-pack");
				assertEquals(exp, err.getMessage());
			}
		}
	}

	@TestAllImplementations
	void testListRemoteWithoutLocalRepository(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		try (Transport t = Transport.open(smartAuthNoneURI);
				FetchConnection c = t.openFetch()) {
			Ref head = c.getRef(Constants.HEAD);
			assertNotNull(head);
		}
	}

	@TestAllImplementations
	void testHttpClientWantsV2AndServerNotConfigured(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		String url = smartAuthNoneURI.toString()
				+ "/info/refs?service=git-upload-pack";
		HttpConnection c = HttpTransport.getConnectionFactory()
				.create(new URL(url));
		c.setRequestMethod("GET");
		c.setRequestProperty("Git-Protocol", "version=2");
		assertEquals(200, c.getResponseCode());

		PacketLineIn pckIn = new PacketLineIn(c.getInputStream());
		assertEquals(pckIn.readString(), "version 2");
	}

	@TestAllImplementations
	void testHttpServerConfiguredToV0(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		remoteRepository.getRepository().getConfig().setInt("protocol", null,
				"version", 0);
		String url = smartAuthNoneURI.toString()
				+ "/info/refs?service=git-upload-pack";
		HttpConnection c = HttpTransport.getConnectionFactory()
				.create(new URL(url));
		c.setRequestMethod("GET");
		c.setRequestProperty("Git-Protocol", "version=2");
		assertEquals(200, c.getResponseCode());

		PacketLineIn pckIn = new PacketLineIn(c.getInputStream());

		// Check that we get a v0 response.
		assertEquals(pckIn.readString(), "# service=git-upload-pack");
		assertTrue(PacketLineIn.isEnd(pckIn.readString()));
		assertTrue(pckIn.readString().matches("[0-9a-f]{40} HEAD.*"));
	}

	@TestAllImplementations
	void testV2HttpFirstResponse(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		String url = smartAuthNoneURI.toString()
				+ "/info/refs?service=git-upload-pack";
		HttpConnection c = HttpTransport.getConnectionFactory()
				.create(new URL(url));
		c.setRequestMethod("GET");
		c.setRequestProperty("Git-Protocol", "version=2");
		assertEquals(200, c.getResponseCode());

		PacketLineIn pckIn = new PacketLineIn(c.getInputStream());
		assertEquals(pckIn.readString(), "version 2");

		// What remains are capabilities - ensure that all of them are
		// non-empty strings, and that we see END at the end.
		for (String s : pckIn.readStrings()) {
			assertFalse(s.isEmpty());
		}
	}

	@TestAllImplementations
	void testV2HttpSubsequentResponse(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		String url = smartAuthNoneURI.toString() + "/git-upload-pack";
		HttpConnection c = HttpTransport.getConnectionFactory()
				.create(new URL(url));
		c.setRequestMethod("POST");
		c.setRequestProperty("Content-Type",
				"application/x-git-upload-pack-request");
		c.setRequestProperty("Git-Protocol", "version=2");
		c.setDoOutput(true);

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
		for (String s : pckIn.readStrings()) {
			assertTrue(s.matches("[0-9a-f]{40} [A-Za-z/]*"));
		}

		assertEquals(200, c.getResponseCode());
	}

	@TestAllImplementations
	void testCloneWithDepth(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		remoteRepository.getRepository().getConfig().setInt("protocol", null,
				"version", 0);
		File directory = createTempDirectory("testCloneWithDepth");
		Git git = Git.cloneRepository().setDirectory(directory).setDepth(1)
				.setURI(smartAuthNoneURI.toString()).call();

		assertEquals(Set.of(git.getRepository().resolve(Constants.HEAD)),
				git.getRepository().getObjectDatabase().getShallowCommits());
	}

	@TestAllImplementations
	void testCloneWithDeepenSince(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		remoteRepository.getRepository().getConfig().setInt("protocol", null,
				"version", 0);
		RevCommit commit = remoteRepository.commit()
				.parent(remoteRepository.git().log().call().iterator().next())
				.message("Test").add("test.txt", "Hello world").create();
		remoteRepository.update(master, commit);

		File directory = createTempDirectory("testCloneWithDeepenSince");
		Git git = Git.cloneRepository().setDirectory(directory)
				.setShallowSince(Instant.ofEpochSecond(commit.getCommitTime()))
				.setURI(smartAuthNoneURI.toString()).call();

		assertEquals(Set.of(git.getRepository().resolve(Constants.HEAD)),
				git.getRepository().getObjectDatabase().getShallowCommits());
	}

	@TestAllImplementations
	void testCloneWithDeepenNot(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		remoteRepository.getRepository().getConfig().setInt("protocol", null,
				"version", 0);
		RevCommit commit = remoteRepository.git().log().call().iterator()
				.next();
		remoteRepository.update(master, remoteRepository.commit().parent(commit)
				.message("Test").add("test.txt", "Hello world").create());

		File directory = createTempDirectory("testCloneWithDeepenNot");
		Git git = Git.cloneRepository().setDirectory(directory)
				.addShallowExclude(commit.getId())
				.setURI(smartAuthNoneURI.toString()).call();

		assertEquals(Set.of(git.getRepository().resolve(Constants.HEAD)),
				git.getRepository().getObjectDatabase().getShallowCommits());
	}

	@TestAllImplementations
	void testV2CloneWithDepth(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		File directory = createTempDirectory("testV2CloneWithDepth");
		Git git = Git.cloneRepository().setDirectory(directory).setDepth(1)
				.setURI(smartAuthNoneURI.toString()).call();

		assertEquals(Set.of(git.getRepository().resolve(Constants.HEAD)),
				git.getRepository().getObjectDatabase().getShallowCommits());
	}

	@TestAllImplementations
	void testV2CloneWithDeepenSince(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		RevCommit commit = remoteRepository.commit()
				.parent(remoteRepository.git().log().call().iterator().next())
				.message("Test").add("test.txt", "Hello world").create();
		remoteRepository.update(master, commit);

		File directory = createTempDirectory("testV2CloneWithDeepenSince");
		Git git = Git.cloneRepository().setDirectory(directory)
				.setShallowSince(Instant.ofEpochSecond(commit.getCommitTime()))
				.setURI(smartAuthNoneURI.toString()).call();

		assertEquals(Set.of(git.getRepository().resolve(Constants.HEAD)),
				git.getRepository().getObjectDatabase().getShallowCommits());
	}

	@TestAllImplementations
	void testV2CloneWithDeepenNot(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		RevCommit commit = remoteRepository.git().log().call().iterator()
				.next();
		remoteRepository.update(master, remoteRepository.commit().parent(commit)
				.message("Test").add("test.txt", "Hello world").create());

		File directory = createTempDirectory("testV2CloneWithDeepenNot");
		Git git = Git.cloneRepository().setDirectory(directory)
				.addShallowExclude(commit.getId())
				.setURI(smartAuthNoneURI.toString()).call();

		assertEquals(Set.of(git.getRepository().resolve(Constants.HEAD)),
				git.getRepository().getObjectDatabase().getShallowCommits());
	}
}
