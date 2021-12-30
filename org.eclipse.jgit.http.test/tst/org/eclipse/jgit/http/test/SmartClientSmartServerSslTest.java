/*
 * Copyright (C) 2017, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.HttpSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SmartClientSmartServerSslTest extends AllProtocolsHttpTestCase {

	// We run these tests with a server on localhost with a self-signed
	// certificate. We don't do authentication tests here, so there's no need
	// for username and password.
	//
	// But the server certificate will not validate. We know that Transport will
	// ask whether we trust the server all the same. This credentials provider
	// blindly trusts the self-signed certificate by answering "Yes" to all
	// questions.
	private CredentialsProvider testCredentials = new CredentialsProvider() {

		@Override
		public boolean isInteractive() {
			return false;
		}

		@Override
		public boolean supports(CredentialItem... items) {
			for (CredentialItem item : items) {
				if (item instanceof CredentialItem.InformationalMessage) {
					continue;
				}
				if (item instanceof CredentialItem.YesNoType) {
					continue;
				}
				return false;
			}
			return true;
		}

		@Override
		public boolean get(URIish uri, CredentialItem... items)
				throws UnsupportedCredentialItem {
			for (CredentialItem item : items) {
				if (item instanceof CredentialItem.InformationalMessage) {
					continue;
				}
				if (item instanceof CredentialItem.YesNoType) {
					((CredentialItem.YesNoType) item).setValue(true);
					continue;
				}
				return false;
			}
			return true;
		}
	};

	private URIish remoteURI;

	private URIish secureURI;

	private RevBlob A_txt;

	private RevCommit A, B;

	public SmartClientSmartServerSslTest(TestParameters params) {
		super(params);
	}

	@Override
	protected AppServer createServer() {
		return new AppServer(0, 0);
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final String srcName = src.getRepository().getDirectory().getName();
		StoredConfig cfg = src.getRepository().getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
		cfg.setInt("protocol", null, "version", enableProtocolV2 ? 2 : 0);
		cfg.save();

		GitServlet gs = new GitServlet();

		ServletContextHandler app = addNormalContext(gs, src, srcName);

		server.setUp();

		remoteURI = toURIish(app, srcName);
		secureURI = new URIish(rewriteUrl(remoteURI.toString(), "https",
				server.getSecurePort()));

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);

		src.update("refs/garbage/a/very/long/ref/name/to/compress", B);
	}

	private ServletContextHandler addNormalContext(GitServlet gs, TestRepository<Repository> src, String srcName) {
		ServletContextHandler app = server.addContext("/git");
		app.addFilter(new FilterHolder(new Filter() {

			@Override
			public void init(FilterConfig filterConfig)
					throws ServletException {
				// empty
			}

			// Redirects http to https for requests containing "/https/".
			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
				final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
				final StringBuffer fullUrl = httpServletRequest.getRequestURL();
				if (httpServletRequest.getQueryString() != null) {
					fullUrl.append("?")
							.append(httpServletRequest.getQueryString());
				}
				String urlString = rewriteUrl(fullUrl.toString(), "https",
						server.getSecurePort());
				httpServletResponse
						.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
				httpServletResponse.setHeader(HttpSupport.HDR_LOCATION,
						urlString.replace("/https/", "/"));
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/https/*", EnumSet.of(DispatcherType.REQUEST));
		app.addFilter(new FilterHolder(new Filter() {

			@Override
			public void init(FilterConfig filterConfig)
					throws ServletException {
				// empty
			}

			// Redirects https back to http for requests containing "/back/".
			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
				final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
				final StringBuffer fullUrl = httpServletRequest.getRequestURL();
				if (httpServletRequest.getQueryString() != null) {
					fullUrl.append("?")
							.append(httpServletRequest.getQueryString());
				}
				String urlString = rewriteUrl(fullUrl.toString(), "http",
						server.getPort());
				httpServletResponse
						.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
				httpServletResponse.setHeader(HttpSupport.HDR_LOCATION,
						urlString.replace("/back/", "/"));
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/back/*", EnumSet.of(DispatcherType.REQUEST));
		gs.setRepositoryResolver(new TestRepositoryResolver(src, srcName));
		app.addServlet(new ServletHolder(gs), "/*");
		return app;
	}

	@Test
	public void testInitialClone_ViaHttps() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, secureURI)) {
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 3 : 2, requests.size());
	}

	@Test
	public void testInitialClone_RedirectToHttps() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(remoteURI, "/https");
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());
	}

	@Test
	public void testInitialClone_RedirectBackToHttp() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(secureURI, "/back");
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirect from https to http)");
		} catch (TransportException e) {
			assertTrue(e.getMessage().contains("not allowed"));
		}
	}

	@Test
	public void testInitialClone_SslFailure() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, secureURI)) {
			// Set a credentials provider that doesn't handle questions
			t.setCredentialsProvider(
					new UsernamePasswordCredentialsProvider("any", "anypwd"));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (SSL certificate not trusted)");
		} catch (TransportException e) {
			assertTrue(e.getMessage().contains("Secure connection"));
		}
	}

}
