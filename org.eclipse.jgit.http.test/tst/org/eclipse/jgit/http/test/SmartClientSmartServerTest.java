/*
 * Copyright (C) 2010, 2020 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_LENGTH;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.junit.CustomParameterResolver;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.junit.http.AppServer;
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
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CustomParameterResolver.class)
public class SmartClientSmartServerTest extends AllProtocolsHttpTestCase {
	private static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";

	private AdvertiseRefsHook advertiseRefsHook;

	private Repository remoteRepository;

	private CredentialsProvider testCredentials = new UsernamePasswordCredentialsProvider(
			AppServer.username, AppServer.password);

	private URIish remoteURI;

	private URIish brokenURI;

	private URIish redirectURI;

	private URIish authURI;

	private URIish authOnPostURI;

	private URIish slowURI;

	private URIish slowAuthURI;

	private RevBlob A_txt;

	private RevCommit A, B, unreachableCommit;

	@BeforeEach
	public void setUp(TestParameters params) throws Exception {
		super.setUp();
		configure(params);

		TestRepository<Repository> src = createTestRepository();
		String srcName = src.getRepository().getDirectory().getName();
		StoredConfig cfg = src.getRepository().getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, true);
		cfg.setInt("protocol", null, "version", enableProtocolV2 ? 2 : 0);
		cfg.save();

		GitServlet gs = new GitServlet();
		gs.setUploadPackFactory((HttpServletRequest req, Repository db) -> {
			DefaultUploadPackFactory f = new DefaultUploadPackFactory();
			UploadPack up = f.create(req, db);
			if (advertiseRefsHook != null) {
				up.setAdvertiseRefsHook(advertiseRefsHook);
			}
			return up;
		});

		ServletContextHandler app = addNormalContext(gs, src, srcName);

		ServletContextHandler broken = addBrokenContext(gs, srcName);

		ServletContextHandler redirect = addRedirectContext(gs);

		ServletContextHandler auth = addAuthContext(gs, "auth");

		ServletContextHandler authOnPost = addAuthContext(gs, "pauth", "POST");

		ServletContextHandler slow = addSlowContext(gs, "slow", false);

		ServletContextHandler slowAuth = addSlowContext(gs, "slowAuth", true);

		server.setUp();

		remoteRepository = src.getRepository();
		remoteURI = toURIish(app, srcName);
		brokenURI = toURIish(broken, srcName);
		redirectURI = toURIish(redirect, srcName);
		authURI = toURIish(auth, srcName);
		authOnPostURI = toURIish(authOnPost, srcName);
		slowURI = toURIish(slow, srcName);
		slowAuthURI = toURIish(slowAuth, srcName);

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);

		unreachableCommit = src.commit().add("A_txt", A_txt).create();

		src.update("refs/garbage/a/very/long/ref/name/to/compress", B);
	}

	private ServletContextHandler addNormalContext(GitServlet gs,
			TestRepository<Repository> src, String srcName) {
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
				HttpServletResponse httpServletResponse = (HttpServletResponse) response;
				HttpServletRequest httpServletRequest = (HttpServletRequest) request;
				StringBuffer fullUrl = httpServletRequest.getRequestURL();
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

	private ServletContextHandler addBrokenContext(GitServlet gs,
			String srcName) {
		ServletContextHandler broken = server.addContext("/bad");
		broken.addFilter(new FilterHolder(new Filter() {

			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				HttpServletResponse r = (HttpServletResponse) response;
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

			private String local(String url, boolean toLocal) {
				if (!toLocal) {
					return url;
				}
				try {
					URI u = new URI(url);
					String fragment = u.getRawFragment();
					if (fragment != null) {
						return u.getRawPath() + '#' + fragment;
					}
					return u.getRawPath();
				} catch (URISyntaxException e) {
					return url;
				}
			}

			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				HttpServletResponse httpServletResponse = (HttpServletResponse) response;
				HttpServletRequest httpServletRequest = (HttpServletRequest) request;
				StringBuffer fullUrl = httpServletRequest.getRequestURL();
				if (httpServletRequest.getQueryString() != null) {
					fullUrl.append("?")
							.append(httpServletRequest.getQueryString());
				}
				String urlString = fullUrl.toString();
				boolean localRedirect = false;
				if (urlString.contains("/local")) {
					urlString = urlString.replace("/local", "");
					localRedirect = true;
				}
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
							local(urlString, localRedirect));
					return;
				}
				int responseCode = HttpServletResponse.SC_MOVED_PERMANENTLY;
				int nofRedirects = 0;
				Matcher matcher = responsePattern.matcher(urlString);
				if (matcher.find()) {
					nofRedirects = Integer.parseUnsignedInt(matcher.group(1));
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
						local(urlString, localRedirect));
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/*", EnumSet.of(DispatcherType.REQUEST));
		redirect.addServlet(new ServletHolder(gs), "/*");
		return redirect;
	}

	private ServletContextHandler addSlowContext(GitServlet gs, String path,
			boolean auth) {
		ServletContextHandler slow = server.addContext('/' + path);
		slow.addFilter(new FilterHolder(new Filter() {

			@Override
			public void init(FilterConfig filterConfig)
					throws ServletException {
				// empty
			}

			// Simply delays the servlet for two seconds. Used for timeout
			// tests, which use a one-second timeout.
			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response, FilterChain chain)
					throws IOException, ServletException {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
				chain.doFilter(request, response);
			}

			@Override
			public void destroy() {
				// empty
			}
		}), "/*", EnumSet.of(DispatcherType.REQUEST));
		slow.addServlet(new ServletHolder(gs), "/*");
		if (auth) {
			return server.authBasic(slow);
		}
		return slow;
	}

	@TestAllProtocols
	void testListRemote(@SuppressWarnings("unused") TestParameters params)
			throws IOException {
		assertEquals("http", remoteURI.getScheme());

		Map<String, Ref> map;
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, remoteURI)) {
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
		assertEquals(3, map.size());

		assertNotNull(map.get(master), "has " + master);
		assertEquals(B, map.get(master).getObjectId());

		assertNotNull(map.get(Constants.HEAD), "has " + Constants.HEAD);
		assertEquals(B, map.get(Constants.HEAD).getObjectId());

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 2 : 1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		} else {
			AccessEvent lsRefs = requests.get(1);
			assertEquals("POST", lsRefs.getMethod());
			assertEquals(join(remoteURI, "git-upload-pack"), lsRefs.getPath());
			assertEquals(0, lsRefs.getParameters().size());
			assertNotNull(lsRefs.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(lsRefs.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals("version=2", lsRefs.getRequestHeader("Git-Protocol"));
			assertEquals(200, lsRefs.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					lsRefs.getResponseHeader(HDR_CONTENT_TYPE));
		}
	}

	@TestAllProtocols
	void testListRemote_BadName(
			@SuppressWarnings("unused") TestParameters params)
			throws IOException, URISyntaxException {
		URIish uri = new URIish(this.remoteURI.toString() + ".invalid");
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, uri)) {
			try {
				t.openFetch();
				fail("fetch connection opened");
			} catch (NoRemoteRepositoryException notFound) {
				assertEquals(uri + ": " + uri
						+ "/info/refs?service=git-upload-pack not found: Not Found",
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
		assertEquals(404, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testFetchBySHA1(@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, remoteURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(B.name())));
			assertTrue(dst.getObjectDatabase().has(A_txt));
		}
	}

	@TestAllProtocols
	void testFetchBySHA1Unreachable(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, remoteURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			Exception e = assertThrows(TransportException.class,
					() -> t.fetch(NullProgressMonitor.INSTANCE,
							Collections.singletonList(
									new RefSpec(unreachableCommit.name()))));
			assertTrue(e.getMessage().contains("Bad Request"));
		}
		assertLastRequestStatusCode(400);
	}

	@TestAllProtocols
	void testFetchBySHA1UnreachableByAdvertiseRefsHook(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		advertiseRefsHook = new AbstractAdvertiseRefsHook() {
			@Override
			protected Map<String, Ref> getAdvertisedRefs(Repository repository,
					RevWalk revWalk) {
				return Collections.emptyMap();
			}
		};

		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, remoteURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			Exception e = assertThrows(TransportException.class,
					() -> t.fetch(NullProgressMonitor.INSTANCE,
							Collections.singletonList(new RefSpec(A.name()))));
			assertTrue(e.getMessage().contains("Bad Request"));
		}
		assertLastRequestStatusCode(400);
	}

	@TestAllProtocols
	void testTimeoutExpired(@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, slowURI)) {
			t.setTimeout(1);
			TransportException expected = assertThrows(TransportException.class,
					() -> t.fetch(NullProgressMonitor.INSTANCE,
							mirror(master)));
			assertTrue(expected.getMessage().contains("time"),
					"Unexpected exception message: " + expected.toString());
		}
	}

	@TestAllProtocols
	void testTimeoutExpiredWithAuth(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, slowAuthURI)) {
			t.setTimeout(1);
			t.setCredentialsProvider(testCredentials);
			TransportException expected = assertThrows(TransportException.class,
					() -> t.fetch(NullProgressMonitor.INSTANCE,
							mirror(master)));
			assertTrue(expected.getMessage().contains("time"),
					"Unexpected exception message: " + expected.toString());
			assertFalse(expected.getMessage().contains("auth"),
					"Unexpected exception message: " + expected.toString());
		}
	}

	@TestAllProtocols
	void testInitialClone_Small(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, remoteURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 3 : 2, requests.size());

		int requestNumber = 0;
		AccessEvent info = requests.get(requestNumber++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		} else {
			AccessEvent lsRefs = requests.get(requestNumber++);
			assertEquals("POST", lsRefs.getMethod());
			assertEquals(join(remoteURI, "git-upload-pack"), lsRefs.getPath());
			assertEquals(0, lsRefs.getParameters().size());
			assertNotNull(lsRefs.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(lsRefs.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals("version=2", lsRefs.getRequestHeader("Git-Protocol"));
			assertEquals(200, lsRefs.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					lsRefs.getResponseHeader(HDR_CONTENT_TYPE));
		}

		AccessEvent service = requests.get(requestNumber);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void test_CloneWithCustomFactory(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		HttpConnectionFactory globalFactory = HttpTransport
				.getConnectionFactory();
		HttpConnectionFactory failingConnectionFactory = new HttpConnectionFactory() {

			@Override
			public HttpConnection create(URL url) throws IOException {
				throw new IOException("Should not be reached");
			}

			@Override
			public HttpConnection create(URL url, Proxy proxy)
					throws IOException {
				throw new IOException("Should not be reached");
			}
		};
		HttpTransport.setConnectionFactory(failingConnectionFactory);
		try {
			File tmp = createTempDirectory("cloneViaApi");
			boolean[] localFactoryUsed = { false };
			TransportConfigCallback callback = new TransportConfigCallback() {

				@Override
				public void configure(Transport transport) {
					if (transport instanceof TransportHttp) {
						((TransportHttp) transport).setHttpConnectionFactory(
								new HttpConnectionFactory() {

									@Override
									public HttpConnection create(URL url)
											throws IOException {
										localFactoryUsed[0] = true;
										return globalFactory.create(url);
									}

									@Override
									public HttpConnection create(URL url,
											Proxy proxy) throws IOException {
										localFactoryUsed[0] = true;
										return globalFactory.create(url, proxy);
									}
								});
					}
				}
			};
			try (Git git = Git.cloneRepository().setDirectory(tmp)
					.setTransportConfigCallback(callback)
					.setURI(remoteURI.toPrivateString()).call()) {
				assertTrue(localFactoryUsed[0],
						"Should have used the local HttpConnectionFactory");
			}
		} finally {
			HttpTransport.setConnectionFactory(globalFactory);
		}
	}

	private void initialClone_Redirect(int nofRedirects, int code)
			throws Exception {
		initialClone_Redirect(nofRedirects, code, false);
	}

	private void initialClone_Redirect(int nofRedirects, int code,
			boolean localRedirect) throws Exception {
		URIish cloneFrom = redirectURI;
		if (localRedirect) {
			cloneFrom = extendPath(cloneFrom, "/local");
		}
		if (code != 301 || nofRedirects > 1) {
			cloneFrom = extendPath(cloneFrom,
					"/response/" + nofRedirects + "/" + code);
		}

		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, cloneFrom)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals((enableProtocolV2 ? 3 : 2) + nofRedirects,
				requests.size());

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
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		} else {
			AccessEvent lsRefs = requests.get(n++);
			assertEquals("POST", lsRefs.getMethod());
			assertEquals(join(remoteURI, "git-upload-pack"), lsRefs.getPath());
			assertEquals(0, lsRefs.getParameters().size());
			assertNotNull(lsRefs.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(lsRefs.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals("version=2", lsRefs.getRequestHeader("Git-Protocol"));
			assertEquals(200, lsRefs.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					lsRefs.getResponseHeader(HDR_CONTENT_TYPE));
		}

		AccessEvent service = requests.get(n++);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testInitialClone_Redirect301Small(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		initialClone_Redirect(1, 301);
	}

	@TestAllProtocols
	void testInitialClone_Redirect301Local(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		initialClone_Redirect(1, 301, true);
	}

	@TestAllProtocols
	void testInitialClone_Redirect302Small(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		initialClone_Redirect(1, 302);
	}

	@TestAllProtocols
	void testInitialClone_Redirect303Small(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		initialClone_Redirect(1, 303);
	}

	@TestAllProtocols
	void testInitialClone_Redirect307Small(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		initialClone_Redirect(1, 307);
	}

	@TestAllProtocols
	void testInitialClone_RedirectMultiple(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		initialClone_Redirect(4, 302);
	}

	@TestAllProtocols
	void testInitialClone_RedirectMax(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		StoredConfig userConfig = SystemReader.getInstance().getUserConfig();
		userConfig.setInt("http", null, "maxRedirects", 4);
		userConfig.save();
		initialClone_Redirect(4, 302);
	}

	@TestAllProtocols
	void testInitialClone_RedirectTooOften(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		StoredConfig userConfig = SystemReader.getInstance().getUserConfig();
		userConfig.setInt("http", null, "maxRedirects", 3);
		userConfig.save();

		URIish cloneFrom = extendPath(redirectURI, "/response/4/302");
		String remoteUri = cloneFrom.toString();
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, cloneFrom)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
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

	@TestAllProtocols
	void testInitialClone_RedirectLoop(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		URIish cloneFrom = extendPath(redirectURI, "/loop");
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, cloneFrom)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirect loop)");
		} catch (TransportException e) {
			assertTrue(e.getMessage().contains("Redirected more than"));
		}
	}

	@TestAllProtocols
	void testInitialClone_RedirectOnPostAllowed(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		StoredConfig userConfig = SystemReader.getInstance().getUserConfig();
		userConfig.setString("http", null, "followRedirects", "true");
		userConfig.save();

		URIish cloneFrom = extendPath(remoteURI, "/post");
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, cloneFrom)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(cloneFrom, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		}

		AccessEvent redirect = requests.get(1);
		assertEquals("POST", redirect.getMethod());
		assertEquals(301, redirect.getStatus());

		for (int i = 2; i < requests.size(); i++) {
			AccessEvent service = requests.get(i);
			assertEquals("POST", service.getMethod());
			assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
			assertEquals(0, service.getParameters().size());
			assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals(200, service.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					service.getResponseHeader(HDR_CONTENT_TYPE));
		}
	}

	@TestAllProtocols
	void testInitialClone_RedirectOnPostForbidden(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		URIish cloneFrom = extendPath(remoteURI, "/post");
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, cloneFrom)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirect on POST)");
		} catch (TransportException e) {
			assertTrue(e.getMessage().contains("301"));
		}
		assertLastRequestStatusCode(301);
	}

	@TestAllProtocols
	void testInitialClone_RedirectForbidden(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		StoredConfig userConfig = SystemReader.getInstance().getUserConfig();
		userConfig.setString("http", null, "followRedirects", "false");
		userConfig.save();

		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, redirectURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should have failed (redirects forbidden)");
		} catch (TransportException e) {
			assertTrue(
					e.getMessage().contains("http.followRedirects is false"));
		}
		assertLastRequestStatusCode(301);
	}

	private void assertFetchRequests(List<AccessEvent> requests, int index) {
		AccessEvent info = requests.get(index++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		}

		for (int i = index; i < requests.size(); i++) {
			AccessEvent service = requests.get(i);
			assertEquals("POST", service.getMethod());
			assertEquals(join(authURI, "git-upload-pack"), service.getPath());
			assertEquals(0, service.getParameters().size());
			assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");

			assertEquals(200, service.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					service.getResponseHeader(HDR_CONTENT_TYPE));
		}
	}

	@TestAllProtocols
	void testInitialClone_WithAuthentication(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());

		assertFetchRequests(requests, 1);
	}

	@TestAllProtocols
	void testInitialClone_WithPreAuthentication(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			((TransportHttp) t).setPreemptiveBasicAuthentication(
					AppServer.username, AppServer.password);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 3 : 2, requests.size());

		assertFetchRequests(requests, 0);
	}

	@TestAllProtocols
	void testInitialClone_WithPreAuthenticationCleared(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			((TransportHttp) t).setPreemptiveBasicAuthentication(
					AppServer.username, AppServer.password);
			((TransportHttp) t).setPreemptiveBasicAuthentication(null, null);
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());

		assertFetchRequests(requests, 1);
	}

	@TestAllProtocols
	void testInitialClone_PreAuthenticationTooLate(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			((TransportHttp) t).setPreemptiveBasicAuthentication(
					AppServer.username, AppServer.password);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
			List<AccessEvent> requests = getRequests();
			assertEquals(enableProtocolV2 ? 3 : 2, requests.size());
			assertFetchRequests(requests, 0);
			assertThrows(IllegalStateException.class,
					() -> ((TransportHttp) t).setPreemptiveBasicAuthentication(
							AppServer.username, AppServer.password));
			assertThrows(IllegalStateException.class, () -> ((TransportHttp) t)
					.setPreemptiveBasicAuthentication(null, null));
		}
	}

	@TestAllProtocols
	void testInitialClone_WithWrongPreAuthenticationAndCredentialProvider(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			((TransportHttp) t).setPreemptiveBasicAuthentication(
					AppServer.username, AppServer.password + 'x');
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());

		assertFetchRequests(requests, 1);
	}

	@TestAllProtocols
	void testInitialClone_WithWrongPreAuthentication(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			((TransportHttp) t).setPreemptiveBasicAuthentication(
					AppServer.username, AppServer.password + 'x');
			TransportException e = assertThrows(TransportException.class,
					() -> t.fetch(NullProgressMonitor.INSTANCE,
							mirror(master)));
			String msg = e.getMessage();
			assertTrue(msg.contains("no CredentialsProvider"),
					"Unexpected exception message: " + msg);
		}
		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());
	}

	@TestAllProtocols
	void testInitialClone_WithUserInfo(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		URIish withUserInfo = authURI.setUser(AppServer.username)
				.setPass(AppServer.password);
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, withUserInfo)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 3 : 2, requests.size());

		assertFetchRequests(requests, 0);
	}

	@TestAllProtocols
	void testInitialClone_PreAuthOverridesUserInfo(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		URIish withUserInfo = authURI.setUser(AppServer.username)
				.setPass(AppServer.password + 'x');
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, withUserInfo)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			((TransportHttp) t).setPreemptiveBasicAuthentication(
					AppServer.username, AppServer.password);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 3 : 2, requests.size());

		assertFetchRequests(requests, 0);
	}

	@TestAllProtocols
	void testInitialClone_WithAuthenticationNoCredentials(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should not have succeeded -- no authentication");
		} catch (TransportException e) {
			String msg = e.getMessage();
			assertTrue(msg.contains("no CredentialsProvider"),
					"Unexpected exception message: " + msg);
		}
		List<AccessEvent> requests = getRequests();
		assertEquals(1, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(401, info.getStatus());
	}

	@TestAllProtocols
	void testInitialClone_WithAuthenticationWrongCredentials(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
					AppServer.username, "wrongpassword"));
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			fail("Should not have succeeded -- wrong password");
		} catch (TransportException e) {
			String msg = e.getMessage();
			assertTrue(msg.contains("auth"),
					"Unexpected exception message: " + msg);
		}
		List<AccessEvent> requests = getRequests();
		// Once without authentication plus three re-tries with authentication
		assertEquals(4, requests.size());

		for (AccessEvent event : requests) {
			assertEquals("GET", event.getMethod());
			assertEquals(401, event.getStatus());
		}
	}

	@TestAllProtocols
	void testInitialClone_WithAuthenticationAfterRedirect(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
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
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, cloneFrom)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.setCredentialsProvider(uriSpecificCredentialsProvider);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 5 : 4, requests.size());

		int requestNumber = 0;
		AccessEvent redirect = requests.get(requestNumber++);
		assertEquals("GET", redirect.getMethod());
		assertEquals(join(cloneFrom, "info/refs"), redirect.getPath());
		assertEquals(301, redirect.getStatus());

		AccessEvent info = requests.get(requestNumber++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authURI, "info/refs"), info.getPath());
		assertEquals(401, info.getStatus());

		info = requests.get(requestNumber++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		} else {
			AccessEvent lsRefs = requests.get(requestNumber++);
			assertEquals("POST", lsRefs.getMethod());
			assertEquals(join(authURI, "git-upload-pack"), lsRefs.getPath());
			assertEquals(0, lsRefs.getParameters().size());
			assertNotNull(lsRefs.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(lsRefs.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals("version=2", lsRefs.getRequestHeader("Git-Protocol"));
			assertEquals(200, lsRefs.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					lsRefs.getResponseHeader(HDR_CONTENT_TYPE));
		}

		AccessEvent service = requests.get(requestNumber);
		assertEquals("POST", service.getMethod());
		assertEquals(join(authURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testInitialClone_WithAuthenticationOnPostOnly(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, authOnPostURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
			t.setCredentialsProvider(testCredentials);
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
			assertTrue(dst.getObjectDatabase().has(A_txt));
			assertEquals(B, dst.exactRef(master).getObjectId());
			fsck(dst, B);
		}

		List<AccessEvent> requests = getRequests();
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(authOnPostURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));
		if (!enableProtocolV2) {
			assertEquals("gzip", info.getResponseHeader(HDR_CONTENT_ENCODING));
		}

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(authOnPostURI, "git-upload-pack"), service.getPath());
		assertEquals(401, service.getStatus());

		for (int i = 2; i < requests.size(); i++) {
			service = requests.get(i);
			assertEquals("POST", service.getMethod());
			assertEquals(join(authOnPostURI, "git-upload-pack"),
					service.getPath());
			assertEquals(0, service.getParameters().size());
			assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");

			assertEquals(200, service.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					service.getResponseHeader(HDR_CONTENT_TYPE));
		}
	}

	@TestAllProtocols
	void testFetch_FewLocalCommits(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
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
		RevCommit Z;
		try (TestRepository<Repository> tr = new TestRepository<>(
				remoteRepository)) {
			b = tr.branch(master);
			Z = b.commit().message("Z").create();
		}

		// Now incrementally update.
		//
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(Z, dst.getRepository().exactRef(master).getObjectId());

		List<AccessEvent> requests = getRequests();
		requests.removeAll(cloneRequests);

		assertEquals(enableProtocolV2 ? 3 : 2, requests.size());

		int requestNumber = 0;
		AccessEvent info = requests.get(requestNumber++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		if (enableProtocolV2) {
			AccessEvent lsRefs = requests.get(requestNumber++);
			assertEquals("POST", lsRefs.getMethod());
			assertEquals(join(remoteURI, "git-upload-pack"), lsRefs.getPath());
			assertEquals(0, lsRefs.getParameters().size());
			assertNotNull(lsRefs.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(lsRefs.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals("version=2", lsRefs.getRequestHeader("Git-Protocol"));
			assertEquals(200, lsRefs.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					lsRefs.getResponseHeader(HDR_CONTENT_TYPE));
		}

		// We should have needed one request to perform the fetch.
		//
		AccessEvent service = requests.get(requestNumber);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testFetch_TooManyLocalCommits(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
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
		RevCommit Z;
		try (TestRepository<Repository> tr = new TestRepository<>(
				remoteRepository)) {
			b = tr.branch(master);
			Z = b.commit().message("Z").create();
		}

		// Now incrementally update.
		//
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(Z, dst.getRepository().exactRef(master).getObjectId());

		List<AccessEvent> requests = getRequests();
		requests.removeAll(cloneRequests);
		assertEquals(enableProtocolV2 ? 4 : 3, requests.size());

		int requestNumber = 0;
		AccessEvent info = requests.get(requestNumber++);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		if (enableProtocolV2) {
			AccessEvent lsRefs = requests.get(requestNumber++);
			assertEquals("POST", lsRefs.getMethod());
			assertEquals(join(remoteURI, "git-upload-pack"), lsRefs.getPath());
			assertEquals(0, lsRefs.getParameters().size());
			assertNotNull(lsRefs.getRequestHeader(HDR_CONTENT_LENGTH),
					"has content-length");
			assertNull(lsRefs.getRequestHeader(HDR_TRANSFER_ENCODING),
					"not chunked");
			assertEquals("version=2", lsRefs.getRequestHeader("Git-Protocol"));
			assertEquals(200, lsRefs.getStatus());
			assertEquals("application/x-git-upload-pack-result",
					lsRefs.getResponseHeader(HDR_CONTENT_TYPE));
		}

		// We should have needed two requests to perform the fetch
		// due to the high number of local unknown commits.
		//
		AccessEvent service = requests.get(requestNumber++);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));

		service = requests.get(requestNumber);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-upload-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testFetch_MaxHavesCutoffAfterAckOnly(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		// Bootstrap by doing the clone.
		//
		TestRepository dst = createTestRepository();
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}
		assertEquals(B, dst.getRepository().exactRef(master).getObjectId());

		// Force enough into the local client that enumeration will
		// need more than MAX_HAVES (256) haves to be sent. The server
		// doesn't know any of these, so it will never ACK. The client
		// should keep going.
		//
		// If it does, client and server will find a common commit, and
		// the pack file will contain exactly the one commit object Z
		// we create on the remote, which we can test for via the progress
		// monitor, which should have something like
		// "Receiving objects: 100% (1/1)". If the client sends a "done"
		// too early, the server will send more objects, and we'll have
		// a line like "Receiving objects: 100% (8/8)".
		TestRepository.BranchBuilder b = dst.branch(master);
		// The client will send 32 + 64 + 128 + 256 + 512 haves. Only the
		// last one will be a common commit. If the cutoff kicks in too
		// early (after 480), we'll get too many objects in the fetch.
		for (int i = 0; i < 992; i++)
			b.commit().tick(3600 /* 1 hour */).message("c" + i).create();

		// Create a new commit on the remote.
		//
		RevCommit Z;
		try (TestRepository<Repository> tr = new TestRepository<>(
				remoteRepository)) {
			b = tr.branch(master);
			Z = b.commit().message("Z").create();
		}

		// Now incrementally update.
		//
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8);
		TextProgressMonitor monitor = new TextProgressMonitor(writer);
		try (Transport t = Transport.open(dst.getRepository(), remoteURI)) {
			t.fetch(monitor, mirror(master));
		}
		assertEquals(Z, dst.getRepository().exactRef(master).getObjectId());

		String progressMessages = new String(buffer.toByteArray(),
				StandardCharsets.UTF_8);
		Pattern expected = Pattern
				.compile("Receiving objects:\\s+100% \\(1/1\\)\n");
		if (!expected.matcher(progressMessages).find()) {
			System.out.println(progressMessages);
			fail("Expected only one object to be sent");
		}
	}

	@TestAllProtocols
	void testInitialClone_BrokenServer(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, brokenURI)) {
			assertFalse(dst.getObjectDatabase().has(A_txt));
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
		assertEquals("application/x-git-upload-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(brokenURI, "git-upload-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertEquals(200, service.getStatus());
		assertEquals("text/plain;charset=utf-8",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testInvalidWant(@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		ObjectId id;
		try (ObjectInserter.Formatter formatter = new ObjectInserter.Formatter()) {
			id = formatter.idFor(Constants.OBJ_BLOB,
					"testInvalidWant".getBytes(UTF_8));
		}

		try (Repository dst = createBareRepository();
				Transport t = Transport.open(dst, remoteURI);
				FetchConnection c = t.openFetch()) {
			Ref want = new ObjectIdRef.Unpeeled(Ref.Storage.NETWORK, id.name(),
					id);
			c.fetch(NullProgressMonitor.INSTANCE, Collections.singleton(want),
					Collections.<ObjectId> emptySet());
			fail("Server accepted want " + id.name());
		} catch (TransportException err) {
			assertTrue(err.getMessage().contains("Bad Request"));
		}
		assertLastRequestStatusCode(400);
	}

	@TestAllProtocols
	void testFetch_RefsUnreadableOnUpload(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		AppServer noRefServer = new AppServer();
		try {
			String repoName = "refs-unreadable";
			RefsUnreadableInMemoryRepository badRefsRepo = new RefsUnreadableInMemoryRepository(
					new DfsRepositoryDescription(repoName));
			TestRepository<Repository> repo = new TestRepository<>(badRefsRepo);
			badRefsRepo.getConfig().setInt("protocol", null, "version",
					enableProtocolV2 ? 2 : 0);

			ServletContextHandler app = noRefServer.addContext("/git");
			GitServlet gs = new GitServlet();
			gs.setRepositoryResolver(
					new TestRepositoryResolver(repo, repoName));
			app.addServlet(new ServletHolder(gs), "/*");
			noRefServer.setUp();

			RevBlob A2_txt = repo.blob("A2");
			RevCommit A2 = repo.commit().add("A2_txt", A2_txt).create();
			RevCommit B2 = repo.commit().parent(A2).add("A2_txt", "C2")
					.add("B2", "B2").create();
			repo.update(master, B2);

			URIish badRefsURI = new URIish(noRefServer.getURI()
					.resolve(app.getContextPath() + "/" + repoName).toString());

			try (Repository dst = createBareRepository();
					Transport t = Transport.open(dst, badRefsURI);
					FetchConnection c = t.openFetch()) {
				// We start failing here to exercise the post-advertisement
				// upload pack handler.
				badRefsRepo.startFailing();
				// Need to flush caches because ref advertisement populated
				// them.
				badRefsRepo.getRefDatabase().refresh();
				c.fetch(NullProgressMonitor.INSTANCE,
						Collections.singleton(c.getRef(master)),
						Collections.<ObjectId> emptySet());
				fail("Successfully served ref with value " + c.getRef(master));
			} catch (TransportException err) {
				assertTrue(err.getMessage().contains("Server Error"),
						"Unexpected exception message " + err.getMessage());
			}
		} finally {
			noRefServer.tearDown();
		}
	}

	@TestAllProtocols
	void testPush_NotAuthorized(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		TestRepository src = createTestRepository();
		RevBlob Q_txt = src.blob("new text");
		RevCommit Q = src.commit().add("Q", Q_txt).create();
		Repository db = src.getRepository();
		String dstName = Constants.R_HEADS + "new.branch";

		// push anonymous shouldn't be allowed.
		//
		try (Transport t = Transport.open(db, remoteURI)) {
			String srcExpr = Q.name();
			boolean forceUpdate = false;
			String localName = null;
			ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			try {
				t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
				fail("anonymous push incorrectly accepted without error");
			} catch (TransportException e) {
				String exp = remoteURI + ": "
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

	@TestAllProtocols
	void testPush_CreateBranch(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		TestRepository src = createTestRepository();
		RevBlob Q_txt = src.blob("new text");
		RevCommit Q = src.commit().add("Q", Q_txt).create();
		Repository db = src.getRepository();
		String dstName = Constants.R_HEADS + "new.branch";

		enableReceivePack();

		try (Transport t = Transport.open(db, remoteURI)) {
			String srcExpr = Q.name();
			boolean forceUpdate = false;
			String localName = null;
			ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
		}

		assertTrue(remoteRepository.getObjectDatabase().has(Q_txt));
		assertNotNull(remoteRepository.exactRef(dstName), "has " + dstName);
		assertEquals(Q, remoteRepository.exactRef(dstName).getObjectId());
		fsck(remoteRepository, Q);

		ReflogReader log = remoteRepository.getReflogReader(dstName);
		assertNotNull(log, "has log for " + dstName);

		ReflogEntry last = log.getLastEntry();
		assertNotNull(last, "has last entry");
		assertEquals(ObjectId.zeroId(), last.getOldId());
		assertEquals(Q, last.getNewId());
		assertEquals("anonymous", last.getWho().getName());

		// Assumption: The host name we use to contact the server should
		// be the server's own host name, because it should be the loopback
		// network interface.
		//
		String clientHost = remoteURI.getHost();
		assertEquals("anonymous@" + clientHost,
				last.getWho().getEmailAddress());
		assertEquals("push: created", last.getComment());

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-receive-pack", info.getParameter("service"));
		assertEquals(200, info.getStatus());
		assertEquals("application/x-git-receive-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-receive-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNotNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"has content-length");
		assertNull(service.getRequestHeader(HDR_TRANSFER_ENCODING),
				"not chunked");

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-receive-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	@TestAllProtocols
	void testPush_ChunkedEncoding(
			@SuppressWarnings("unused") TestParameters params)
			throws Exception {
		TestRepository<Repository> src = createTestRepository();
		RevBlob Q_bin = src.blob(new TestRng("Q").nextBytes(128 * 1024));
		RevCommit Q = src.commit().add("Q", Q_bin).create();
		Repository db = src.getRepository();
		String dstName = Constants.R_HEADS + "new.branch";

		enableReceivePack();

		StoredConfig cfg = db.getConfig();
		cfg.setInt("core", null, "compression", 0);
		cfg.setInt("http", null, "postbuffer", 8 * 1024);
		cfg.save();

		try (Transport t = Transport.open(db, remoteURI)) {
			String srcExpr = Q.name();
			boolean forceUpdate = false;
			String localName = null;
			ObjectId oldId = null;

			RemoteRefUpdate u = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			t.push(NullProgressMonitor.INSTANCE, Collections.singleton(u));
		}

		assertTrue(remoteRepository.getObjectDatabase().has(Q_bin));
		assertNotNull(remoteRepository.exactRef(dstName), "has " + dstName);
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
		assertEquals("application/x-git-receive-pack-advertisement",
				info.getResponseHeader(HDR_CONTENT_TYPE));

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-receive-pack"), service.getPath());
		assertEquals(0, service.getParameters().size());
		assertNull(service.getRequestHeader(HDR_CONTENT_LENGTH),
				"no content-length");
		assertEquals("chunked",
				service.getRequestHeader(HDR_TRANSFER_ENCODING));

		assertEquals(200, service.getStatus());
		assertEquals("application/x-git-receive-pack-result",
				service.getResponseHeader(HDR_CONTENT_TYPE));
	}

	private void assertLastRequestStatusCode(int statusCode) {
		List<AccessEvent> requests = getRequests();
		assertEquals(statusCode, requests.get(requests.size() - 1).getStatus());
	}

	private void enableReceivePack() throws IOException {
		StoredConfig cfg = remoteRepository.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();
	}
}
