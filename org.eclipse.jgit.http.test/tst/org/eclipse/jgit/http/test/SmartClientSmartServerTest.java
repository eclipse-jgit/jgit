/*
 * Copyright (C) 2010, 2017 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
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
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.RemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.AbstractAdvertiseRefsHook;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.SystemReader;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SmartClientSmartServerTest extends HttpTestCase {
	private static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private AdvertiseRefsHook advertiseRefsHook;

	private Repository remoteRepository;

	private CredentialsProvider testCredentials = new UsernamePasswordCredentialsProvider(
			AppServer.username, AppServer.password);

	private URIish remoteURI;

	private URIish brokenURI;

	private URIish redirectURI;

	private URIish authURI;

	private URIish authOnPostURI;

	private RevBlob A_txt;

	private RevCommit A, B, unreachableCommit;

	@Parameters
	public static Collection<Object[]> data() {
		// run all tests with both connection factories we have
		return Arrays.asList(new Object[][] {
				{ new JDKHttpConnectionFactory() },
				{ new HttpClientConnectionFactory() } });
	}

	public SmartClientSmartServerTest(HttpConnectionFactory cf) {
		HttpTransport.setConnectionFactory(cf);
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final String srcName = src.getRepository().getDirectory().getName();
		src.getRepository()
				.getConfig()
				.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);

		GitServlet gs = new GitServlet();
		gs.setUploadPackFactory(new UploadPackFactory<HttpServletRequest>() {
			@Override
			public UploadPack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				DefaultUploadPackFactory f = new DefaultUploadPackFactory();
				UploadPack up = f.create(req, db);
				if (advertiseRefsHook != null) {
					up.setAdvertiseRefsHook(advertiseRefsHook);
				}
				return up;
			}
		});

		ServletContextHandler app = addNormalContext(gs, src, srcName);

		ServletContextHandler broken = addBrokenContext(gs, src, srcName);

		ServletContextHandler redirect = addRedirectContext(gs);

		ServletContextHandler auth = addAuthContext(gs, "auth");

		ServletContextHandler authOnPost = addAuthContext(gs, "pauth", "POST");

		server.setUp();

		remoteRepository = src.getRepository();
		remoteURI = toURIish(app, srcName);
		brokenURI = toURIish(broken, srcName);
		redirectURI = toURIish(redirect, srcName);
		authURI = toURIish(auth, srcName);
		authOnPostURI = toURIish(authOnPost, srcName);

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);

		unreachableCommit = src.commit().add("A_txt", A_txt).create();

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

			// Does an internal forward for GET requests containing "/post/",
			// and issues a 301 redirect on POST requests for such URLs. Used
			// in the POST redirect tests.
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
				String urlString = fullUrl.toString();
				if ("POST".equalsIgnoreCase(httpServletRequest.getMethod())) {
					httpServletResponse.setStatus(
							HttpServletResponse.SC_MOVED_PERMANENTLY);
					httpServletResponse.setHeader(HttpSupport.HDR_LOCATION,
							urlString.replace("/post/", "/"));
				} else {
					String path = httpServletRequest.getPathInfo();
					path = path.replace("/post/", "/");
					if (httpServletRequest.getQueryString() != null) {
						path += '?' + httpServletRequest.getQueryString();
					}
					RequestDispatcher dispatcher = httpServletRequest
							.getRequestDispatcher(path);
					dispatcher.forward(httpServletRequest, httpServletResponse);
				}
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/post/*", EnumSet.of(DispatcherType.REQUEST));
		gs.setRepositoryResolver(new TestRepositoryResolver(src, srcName));
		app.addServlet(new ServletHolder(gs), "/*");
		return app;
	}

	@SuppressWarnings("unused")
	private ServletContextHandler addBrokenContext(GitServlet gs, TestRepository<Repository> src, String srcName) {
		ServletContextHandler broken = server.addContext("/bad");
		broken.addFilter(new FilterHolder(new Filter() {

			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				final HttpServletResponse r = (HttpServletResponse) response;
				r.setContentType("text/plain");
				r.setCharacterEncoding(UTF_8.name());
				try (PrintWriter w = r.getWriter()) {
					w.print("OK");
				}
			}

			@Override
			public void init(FilterConfig filterConfig)
					throws ServletException {
				// empty
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/" + srcName + "/git-upload-pack",
				EnumSet.of(DispatcherType.REQUEST));
		broken.addServlet(new ServletHolder(gs), "/*");
		return broken;
	}

	private ServletContextHandler addAuthContext(GitServlet gs,
			String contextPath, String... methods) {
		ServletContextHandler auth = server.addContext('/' + contextPath);
		auth.addServlet(new ServletHolder(gs), "/*");
		return server.authBasic(auth, methods);
	}

	private ServletContextHandler addRedirectContext(GitServlet gs) {
		ServletContextHandler redirect = server.addContext("/redirect");
		redirect.addFilter(new FilterHolder(new Filter() {

			// Enables tests for different codes, and for multiple redirects.
			// First parameter is the number of redirects, second one is the
			// redirect status code that should be used
			private Pattern responsePattern = Pattern
					.compile("/response/(\\d+)/(30[1237])/");

			// Enables tests to specify the context that the request should be
			// redirected to in the end. If not present, redirects got to the
			// normal /git context.
			private Pattern targetPattern = Pattern.compile("/target(/\\w+)/");

			@Override
			public void init(FilterConfig filterConfig)
					throws ServletException {
				// empty
			}

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
				String urlString = fullUrl.toString();
				if (urlString.contains("/loop/")) {
					urlString = urlString.replace("/loop/", "/loop/x/");
					if (urlString.contains("/loop/x/x/x/x/x/x/x/x/")) {
						// Go back to initial.
						urlString = urlString.replace("/loop/x/x/x/x/x/x/x/x/",
								"/loop/");
					}
					httpServletResponse.setStatus(
							HttpServletResponse.SC_MOVED_TEMPORARILY);
					httpServletResponse.setHeader(HttpSupport.HDR_LOCATION,
							urlString);
					return;
				}
				int responseCode = HttpServletResponse.SC_MOVED_PERMANENTLY;
				int nofRedirects = 0;
				Matcher matcher = responsePattern.matcher(urlString);
				if (matcher.find()) {
					nofRedirects = Integer
							.parseUnsignedInt(matcher.group(1));
					responseCode = Integer.parseUnsignedInt(matcher.group(2));
					if (--nofRedirects <= 0) {
						urlString = urlString.substring(0, matcher.start())
								+ '/' + urlString.substring(matcher.end());
					} else {
						urlString = urlString.substring(0, matcher.start())
								+ "/response/" + nofRedirects + "/"
								+ responseCode + '/'
								+ urlString.substring(matcher.end());
					}
				}
				httpServletResponse.setStatus(responseCode);
				if (nofRedirects <= 0) {
					String targetContext = "/git";
					matcher = targetPattern.matcher(urlString);
					if (matcher.find()) {
						urlString = urlString.substring(0, matcher.start())
								+ '/' + urlString.substring(matcher.end());
						targetContext = matcher.group(1);
					}
					urlString = urlString.replace("/redirect", targetContext);
				}
				httpServletResponse.setHeader(HttpSupport.HDR_LOCATION,
						urlString);
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/*", EnumSet.of(DispatcherType.REQUEST));
		redirect.addServlet(new ServletHolder(gs), "/*");
		return redirect;
	}

	@Test
	public void testListRemote() throws IOException {
		Repository dst = createBareRepository();

		assertEquals("http", remoteURI.getScheme());

		Map<String, Ref> map;
		try (Transport t = Transport.open(dst, remoteURI)) {
			// I didn't make up these public interface names, I just
			// approved them for inclusion into the code base. Sorry.
			// --spearce
			//
			assertTrue("isa TransportHttp", t instanceof TransportHttp);
			assertTrue("isa HttpTransport", t instanceof HttpTransport);

			try (FetchConnection c = t.openFetch()) {
				map = c.getRefsMap();
			}
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
		try (Transport t = Transport.open(dst, uri)) {
			try {
				t.openFetch();
				fail("fetch connection opened");
			} catch (RemoteRepositoryException notFound) {
				assertEquals(uri + ": Git repository not found",
						notFound.getMessage());
			}
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
	public void testFetchBySHA1() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.hasObject(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(B.name())));
		}

		assertTrue(dst.hasObject(A_txt));
	}

	@Test
	public void testFetchBySHA1Unreachable() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.hasObject(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			thrown.expect(TransportException.class);
			thrown.expectMessage(Matchers.containsString(
					"want " + unreachableCommit.name() + " not valid"));
			t.fetch(NullProgressMonitor.INSTANCE, Collections
					.singletonList(new RefSpec(unreachableCommit.name())));
		}
	}

	@Test
	public void testFetchBySHA1UnreachableByAdvertiseRefsHook()
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.hasObject(A_txt));

		advertiseRefsHook = new AbstractAdvertiseRefsHook() {
			@Override
			protected Map<String, Ref> getAdvertisedRefs(Repository repository,
					RevWalk revWalk) {
				return Collections.emptyMap();
			}
		};

		try (Transport t = Transport.open(dst, remoteURI)) {
			thrown.expect(TransportException.class);
			thrown.expectMessage(Matchers.containsString(
					"want " + A.name() + " not valid"));
			t.fetch(NullProgressMonitor.INSTANCE, Collections
					.singletonList(new RefSpec(A.name())));
		}
	}

	@Test
	public void testInitialClone_Small() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
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

	private void initialClone_Redirect(int nofRedirects, int code)
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = redirectURI;
		if (code != 301 || nofRedirects > 1) {
			cloneFrom = extendPath(cloneFrom,
					"/response/" + nofRedirects + "/" + code);
		}
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(2 + nofRedirects, requests.size());

		int n = 0;
		while (n < nofRedirects) {
			AccessEvent redirect = requests.get(n++);
			assertEquals(code, redirect.getStatus());
		}

		AccessEvent info = requests.get(n++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));

		AccessEvent service = requests.get(n++);
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
	public void testInitialClone_Redirect301Small() throws Exception {
		initialClone_Redirect(1, 301);
	}

	@Test
	public void testInitialClone_Redirect302Small() throws Exception {
		initialClone_Redirect(1, 302);
	}

	@Test
	public void testInitialClone_Redirect303Small() throws Exception {
		initialClone_Redirect(1, 303);
	}

	@Test
	public void testInitialClone_Redirect307Small() throws Exception {
		initialClone_Redirect(1, 307);
	}

	@Test
	public void testInitialClone_RedirectMultiple() throws Exception {
		initialClone_Redirect(4, 302);
	}

	@Test
	public void testInitialClone_RedirectMax() throws Exception {
		FileBasedConfig userConfig = SystemReader.getInstance()
				.openUserConfig(null, FS.DETECTED);
		userConfig.setInt("http", null, "maxRedirects", 4);
		userConfig.save();
		initialClone_Redirect(4, 302);
	}

	@Test
	public void testInitialClone_RedirectTooOften() throws Exception {
		FileBasedConfig userConfig = SystemReader.getInstance()
				.openUserConfig(null, FS.DETECTED);
		userConfig.setInt("http", null, "maxRedirects", 3);
		userConfig.save();
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(redirectURI, "/response/4/302");
		String remoteUri = cloneFrom.toString();
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (too many redirects)");
		} catch (TransportException e) {
			String expectedMessageBegin = remoteUri.toString() + ": "
					+ MessageFormat.format(JGitText.get().redirectLimitExceeded,
							"3", remoteUri.replace("/4/", "/1/") + '/', "");
			String message = e.getMessage();
			if (message.length() > expectedMessageBegin.length()) {
				message = message.substring(0, expectedMessageBegin.length());
			}
			assertEquals(expectedMessageBegin, message);
		}
	}

	@Test
	public void testInitialClone_RedirectLoop() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(redirectURI, "/loop");
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirect loop)");
		} catch (TransportException e) {
			assertTrue(e.getMessage().contains("Redirected more than"));
		}
	}

	@Test
	public void testInitialClone_RedirectOnPostAllowed() throws Exception {
		FileBasedConfig userConfig = SystemReader.getInstance()
				.openUserConfig(null, FS.DETECTED);
		userConfig.setString("http", null, "followRedirects", "true");
		userConfig.save();
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(remoteURI, "/post");
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(cloneFrom, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));

		AccessEvent redirect = requests.get(1);
		assertEquals("POST", redirect.getMethod());
		assertEquals(301, redirect.getStatus());

		AccessEvent service = requests.get(2);
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
	public void testInitialClone_RedirectOnPostForbidden() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(remoteURI, "/post");
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirect on POST)");
		} catch (TransportException e) {
			assertTrue(e.getMessage().contains("301"));
		}
	}

	@Test
	public void testInitialClone_RedirectForbidden() throws Exception {
		FileBasedConfig userConfig = SystemReader.getInstance()
				.openUserConfig(null, FS.DETECTED);
		userConfig.setString("http", null, "followRedirects", "false");
		userConfig.save();

		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, redirectURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirects forbidden)");
		} catch (TransportException e) {
			assertTrue(
					e.getMessage().contains("http.followRedirects is false"));
		}
	}

	@Test
	public void testInitialClone_WithAuthentication() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, authURI)) {
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());

		info = requests.get(1);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));

		AccessEvent service = requests.get(2);
		assertEquals("POST", service.getMethod());
		assertEquals(join(authURI, "git-upload-pack"), service.getPath());
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
	public void testInitialClone_WithAuthenticationNoCredentials()
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, authURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should not have succeeded -- no authentication");
		} catch (TransportException e) {
			String msg = e.getMessage();
			assertTrue("Unexpected exception message: " + msg,
					msg.contains("no CredentialsProvider"));
		}
		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());
	}

	@Test
	public void testInitialClone_WithAuthenticationWrongCredentials()
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, authURI)) {
			t.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
					AppServer.username, "wrongpassword"));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should not have succeeded -- wrong password");
		} catch (TransportException e) {
			String msg = e.getMessage();
			assertTrue("Unexpected exception message: " + msg,
					msg.contains("auth"));
		}
		List<AccessEvent> requests = getRequests();
		// Once without authentication plus three re-tries with authentication
		assertEquals(4, requests.size());

		for (AccessEvent event : requests) {
			assertEquals("GET", event.getMethod());
			assertEquals(401, event.getStatus());
		}
	}

	@Test
	public void testInitialClone_WithAuthenticationAfterRedirect()
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		URIish cloneFrom = extendPath(redirectURI, "/target/auth");
		CredentialsProvider uriSpecificCredentialsProvider = new UsernamePasswordCredentialsProvider(
				"unknown", "none") {
			@Override
			public boolean get(URIish uri, CredentialItem... items)
					throws UnsupportedCredentialItem {
				// Only return the true credentials if the uri path starts with
				// /auth. This ensures that we do provide the correct
				// credentials only for the URi after the redirect, making the
				// test fail if we should be asked for the credentials for the
				// original URI.
				if (uri.getPath().startsWith("/auth")) {
					return testCredentials.get(uri, items);
				}
				return super.get(uri, items);
			}
		};
		try (Transport t = Transport.open(dst, cloneFrom)) {
			t.setCredentialsProvider(uriSpecificCredentialsProvider);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(4, requests.size());

		AccessEvent redirect = requests.get(0);
		assertEquals("GET", redirect.getMethod());
		assertEquals(join(cloneFrom, "info/refs"), redirect.getPath());
		assertEquals(301, redirect.getStatus());

		AccessEvent info = requests.get(1);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authURI, "info/refs"), info.getPath());
		assertEquals(401, info.getStatus());

		info = requests.get(2);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));

		AccessEvent service = requests.get(3);
		assertEquals("POST", service.getMethod());
		assertEquals(join(authURI, "git-upload-pack"), service.getPath());
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
	public void testInitialClone_WithAuthenticationOnPostOnly()
			throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, authOnPostURI)) {
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> requests = getRequests();
		assertEquals(3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authOnPostURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(authOnPostURI, "git-upload-pack"), service.getPath());
		assertEquals(401, service.getStatus());

		service = requests.get(2);
		assertEquals("POST", service.getMethod());
		assertEquals(join(authOnPostURI, "git-upload-pack"), service.getPath());
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
	public void testFetch_FewLocalCommits() throws Exception {
		// Bootstrap by doing the clone.
		//
		TestRepository dst = createTestRepository();
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(B, dst.getRepository().exactRef(master).getObjectId());
		List<AccessEvent> cloneRequests = getRequests();

		// Only create a few new commits.
		TestRepository.BranchBuilder b = dst.branch(master);
		for (int i = 0; i < 4; i++)
			b.commit().tick(3600 /* 1 hour */).message("c" + i).create();

		// Create a new commit on the remote.
		//
		b = new TestRepository<>(remoteRepository).branch(master);
		RevCommit Z = b.commit().message("Z").create();

		// Now incrementally update.
		//
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(Z, dst.getRepository().exactRef(master).getObjectId());

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
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(B, dst.getRepository().exactRef(master).getObjectId());
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
		b = new TestRepository<>(remoteRepository).branch(master);
		RevCommit Z = b.commit().message("Z").create();

		// Now incrementally update.
		//
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(Z, dst.getRepository().exactRef(master).getObjectId());

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
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, brokenURI)) {
			try {
				t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
				fail("fetch completed despite upload-pack being broken");
			} catch (TransportException err) {
				String exp = brokenURI + ": expected"
						+ " Content-Type application/x-git-upload-pack-result;"
						+ " received Content-Type text/plain;charset=utf-8";
				assertEquals(exp, err.getMessage());
			}
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
		assertEquals("text/plain;charset=utf-8",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@Test
	public void testInvalidWant() throws Exception {
		@SuppressWarnings("resource")
		ObjectId id = new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB,
				"testInvalidWant".getBytes(UTF_8));

		Repository dst = createBareRepository();
		try (Transport t = Transport.open(dst, remoteURI);
				FetchConnection c = t.openFetch()) {
			Ref want = new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, id.name(),
					id);
			c.fetch(NullProgressMonitor.INSTANCE, Collections.singleton(want),
					Collections.<ObjectId> emptySet());
			fail("Server accepted want " + id.name());
		} catch (TransportException err) {
			assertEquals("want " + id.name() + " not valid", err.getMessage());
		}
	}

	@Test
	public void testFetch_RefsUnreadableOnUpload() throws Exception {
		AppServer noRefServer = new AppServer();
		try {
			final String repoName = "refs-unreadable";
			RefsUnreadableInMemoryRepository badRefsRepo = new RefsUnreadableInMemoryRepository(
					new DfsRepositoryDescription(repoName));
			final TestRepository<Repository> repo = new TestRepository<>(
					badRefsRepo);

			ServletContextHandler app = noRefServer.addContext("/git");
			GitServlet gs = new GitServlet();
			gs.setRepositoryResolver(new TestRepositoryResolver(repo, repoName));
			app.addServlet(new ServletHolder(gs), "/*");
			noRefServer.setUp();

			RevBlob A2_txt = repo.blob("A2");
			RevCommit A2 = repo.commit().add("A2_txt", A2_txt).create();
			RevCommit B2 = repo.commit().parent(A2).add("A2_txt", "C2")
					.add("B2", "B2").create();
			repo.update(master, B2);

			URIish badRefsURI = new URIish(noRefServer.getURI()
					.resolve(app.getContextPath() + "/" + repoName).toString());

			Repository dst = createBareRepository();
			try (Transport t = Transport.open(dst, badRefsURI);
					FetchConnection c = t.openFetch()) {
				// We start failing here to exercise the post-advertisement
				// upload pack handler.
				badRefsRepo.startFailing();
				// Need to flush caches because ref advertisement populated them.
				badRefsRepo.getRefDatabase().refresh();
				c.fetch(NullProgressMonitor.INSTANCE,
						Collections.singleton(c.getRef(master)),
						Collections.<ObjectId> emptySet());
				fail("Successfully served ref with value " + c.getRef(master));
			} catch (TransportException err) {
				assertEquals("internal server error", err.getMessage());
			}
		} finally {
			noRefServer.tearDown();
		}
	}

	@Test
	public void testPush_NotAuthorized() throws Exception {
		final TestRepository src = createTestRepository();
		final RevBlob Q_txt = src.blob("new text");
		final RevCommit Q = src.commit().add("Q", Q_txt).create();
		final Repository db = src.getRepository();
		final String dstName = Constants.R_HEADS + "new.branch";

		// push anonymous shouldn't be allowed.
		//
		try (Transport t = Transport.open(db, remoteURI)) {
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

		enableReceivePack();

		try (Transport t = Transport.open(db, remoteURI)) {
			final String srcExpr = Q.name();
			final boolean forceUpdate = false;
			final String localName = null;
			final ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
		}

		assertTrue(remoteRepository.getObjectDatabase().has(Q_txt));
		assertNotNull("has " + dstName, remoteRepository.exactRef(dstName));
		assertEquals(Q, remoteRepository.exactRef(dstName).getObjectId());
		fsck(remoteRepository, Q);

		final ReflogReader log = remoteRepository.getReflogReader(dstName);
		assertNotNull("has log for " + dstName, log);

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

		enableReceivePack();

		final StoredConfig cfg = db.getConfig();
		cfg.setInt("core", null, "compression", 0);
		cfg.setInt("http", null, "postbuffer", 8 * 1024);
		cfg.save();

		try (Transport t = Transport.open(db, remoteURI)) {
			final String srcExpr = Q.name();
			final boolean forceUpdate = false;
			final String localName = null;
			final ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
		}

		assertTrue(remoteRepository.getObjectDatabase().has(Q_bin));
		assertNotNull("has " + dstName, remoteRepository.exactRef(dstName));
		assertEquals(Q, remoteRepository.exactRef(dstName).getObjectId());
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
