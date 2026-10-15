/*
 * Copyright (C) 2026, Nishchay Singh <singhnischay04@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http.apache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AppServerBase;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportProtocol;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.junit.Test;

public class HttpClientAuthenticationTest extends HttpTestCase {

	@Test
	public void testAuthenticationQueryPreservesTransportRegistration()
			throws Exception {
		URL[] locations = {
				Transport.class.getProtectionDomain().getCodeSource()
						.getLocation(),
				HttpClientConnection.class.getProtectionDomain().getCodeSource()
						.getLocation() };
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		// Load fresh JGit classes even if the test JVM initialized Transport.
		try (URLClassLoader loader = new URLClassLoader(locations,
				getClass().getClassLoader()) {
			@Override
			protected synchronized Class<?> loadClass(String name,
					boolean resolve) throws ClassNotFoundException {
				if (!name.startsWith("org.eclipse.jgit.")) {
					return super.loadClass(name, resolve);
				}
				Class<?> loaded = findLoadedClass(name);
				if (loaded == null) {
					loaded = findClass(name);
				}
				if (resolve) {
					resolveClass(loaded);
				}
				return loaded;
			}
		}) {
			Thread.currentThread().setContextClassLoader(loader);
			Class<?> connection = loader
					.loadClass(HttpClientConnection.class.getName());
			var query = connection.getDeclaredMethod(
					"supportsJGitAuthentication", HttpResponse.class);
			query.setAccessible(true);
			assertEquals(Boolean.TRUE,
					query.invoke(null, response("Basic realm=\"git\"")));
			Class<?> transport = loader.loadClass(Transport.class.getName());
			Class<?> protocol = loader
					.loadClass(TransportProtocol.class.getName());
			var getSchemes = protocol.getMethod("getSchemes");
			Set<Object> schemes = new HashSet<>();
			for (Object registered : (List<?>) transport
					.getMethod("getTransportProtocols").invoke(null)) {
				schemes.addAll((Set<?>) getSchemes.invoke(registered));
			}
			assertTrue("Missing HTTP transport protocols: " + schemes,
					schemes.containsAll(Set.of("http", "https", "ftp")));
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	public void testSupportedTargetChallengeElements() {
		assertFalse(HttpClientConnection.supportsJGitAuthentication(response()));
		assertFalse(HttpClientConnection
				.supportsJGitAuthentication(response("NTLM")));
		assertFalse(HttpClientConnection
				.supportsJGitAuthentication(response("None")));
		assertFalse(HttpClientConnection
				.supportsJGitAuthentication(response("Bearer")));
		assertTrue(HttpClientConnection
				.supportsJGitAuthentication(response("bAsIc realm=\"git\"")));
		assertTrue(HttpClientConnection.supportsJGitAuthentication(
				response("NTLM", "Basic realm=\"git\"")));
		assertTrue(HttpClientConnection.supportsJGitAuthentication(
				response("Negotiate, NTLM")));
		assertTrue(HttpClientConnection.supportsJGitAuthentication(
				response("Basic realm=\"git\", NTLM")));
		assertTrue(HttpClientConnection.supportsJGitAuthentication(
				response("Digest realm=\"git\", nonce=\"abc\"")));
	}

	private static HttpResponse response(String... challenges) {
		HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
				401, "Unauthorized");
		for (String challenge : challenges) {
			response.addHeader("WWW-Authenticate", challenge);
		}
		return response;
	}

	@Test
	public void testCloneDoesNotConsultAuthenticator() throws Exception {
		assertCloneUsesCredentialsProvider(false, false);
	}

	@Test
	public void testCloneDoesNotUseAuthenticatorCredentials() throws Exception {
		assertCloneUsesCredentialsProvider(true, false);
	}

	@Test
	public void testCloneWithNtlmAndBasicChallenges() throws Exception {
		assertCloneUsesCredentialsProvider(false, true);
	}

	private void assertCloneUsesCredentialsProvider(boolean supplyCredentials,
			boolean offerNtlm)
			throws Exception {
		TestRepository<Repository> src = createTestRepository();
		src.update(master, src.commit().add("file", "content").create());
		GitServlet servlet = new GitServlet();
		servlet.setRepositoryResolver((req, name) -> {
			Repository repository = src.getRepository();
			repository.incrementOpen();
			return repository;
		});
		ServletContextHandler context = server.addContext("/git");
		if (offerNtlm) {
			String expectedAuthorization = "Basic " + Base64.getEncoder()
					.encodeToString((AppServerBase.username + ':'
							+ AppServerBase.password).getBytes(UTF_8));
			context.addFilter(new FilterHolder(new Filter() {
				@Override
				public void doFilter(ServletRequest request,
						ServletResponse response, FilterChain chain)
						throws IOException, ServletException {
					HttpServletRequest httpRequest = (HttpServletRequest) request;
					if (expectedAuthorization.equals(
							httpRequest.getHeader("Authorization"))) {
						chain.doFilter(request, response);
					} else {
						HttpServletResponse httpResponse = (HttpServletResponse) response;
						httpResponse.addHeader("WWW-Authenticate", "NTLM");
						httpResponse.addHeader("WWW-Authenticate",
								"Basic realm=\"git\"");
						httpResponse.setStatus(401);
					}
				}
			}), "/*", EnumSet.of(DispatcherType.REQUEST));
		} else {
			server.authBasic(context);
		}
		context.addServlet(new ServletHolder(servlet), "/*");
		server.setUp();

		AtomicInteger authenticatorCalls = new AtomicInteger();
		AtomicInteger providerCalls = new AtomicInteger();
		Authenticator originalAuthenticator = Authenticator.getDefault();
		HttpConnectionFactory originalFactory = HttpTransport.getConnectionFactory();
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				authenticatorCalls.incrementAndGet();
				return supplyCredentials ? new PasswordAuthentication(
						AppServerBase.username, AppServerBase.password.toCharArray())
						: null;
			}
		});
		HttpTransport.setConnectionFactory(new HttpClientConnectionFactory());
		try {
			UsernamePasswordCredentialsProvider credentials = new UsernamePasswordCredentialsProvider(
					AppServerBase.username, AppServerBase.password) {
				@Override
				public boolean get(URIish uri, CredentialItem... items)
						throws UnsupportedCredentialItem {
					providerCalls.incrementAndGet();
					return super.get(uri, items);
				}
			};
			try (Git clone = Git.cloneRepository()
					.setURI(toURIish(context, "repo").toString())
					.setDirectory(createTempDirectory("clone"))
					.setCredentialsProvider(credentials).call()) {
				assertEquals(src.getRepository().resolve(master),
						clone.getRepository().resolve(master));
			}
			assertEquals(0, authenticatorCalls.get());
			assertEquals(1, providerCalls.get());
		} finally {
			HttpTransport.setConnectionFactory(originalFactory);
			Authenticator.setDefault(originalAuthenticator);
		}
	}

	@Test
	public void testNtlmOnlyChallengeUsesAuthenticator() throws Exception {
		assertTargetAuthenticatorUsed(true, "NTLM");
	}

	@Test
	public void testNtlmAndBasicChallengeUsesJGit() throws Exception {
		assertTargetAuthenticatorUsed(false, "NTLM", "Basic realm=\"git\"");
	}

	@Test
	public void testNtlmAndNegotiateSingleHeaderUsesJGit()
			throws Exception {
		List<String> parsed = assertTargetAuthenticatorUsed(false,
				"Negotiate, NTLM");
		assertTrue(parsed.contains("Negotiate"));
	}

	private List<String> assertTargetAuthenticatorUsed(boolean expected,
			String... challenges) throws Exception {
		ServletContextHandler context = server.addContext("/target");
		context.addServlet(new ServletHolder(new HttpServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse rsp) {
				rsp.setStatus(401);
				for (String challenge : challenges) {
					rsp.addHeader("WWW-Authenticate", challenge);
				}
			}
		}), "/*");
		server.setUp();

		List<Authenticator.RequestorType> requests = new ArrayList<>();
		Authenticator original = Authenticator.getDefault();
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				requests.add(getRequestorType());
				return null;
			}
		});
		try {
			HttpClientConnection connection = new HttpClientConnection(
					server.getURI().resolve("/target/repo").toString());
			connection.setRequestMethod("GET");
			assertEquals(401, connection.getResponseCode());
			assertTrue("Unexpected server authenticator calls: " + requests,
					expected == requests
							.contains(Authenticator.RequestorType.SERVER));
			return connection.getHeaderFields().get("WWW-Authenticate");
		} finally {
			Authenticator.setDefault(original);
		}
	}

	@Test
	public void testNtlmChallengeCanUseAuthenticatorCredentials()
			throws Exception {
		AtomicBoolean authorizationSeen = new AtomicBoolean();
		ServletContextHandler context = server.addContext("/ntlm");
		context.addServlet(new ServletHolder(new HttpServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse rsp) {
				String authorization = req.getHeader("Authorization");
				if (authorization != null
						&& authorization.startsWith("NTLM ")) {
					authorizationSeen.set(true);
					rsp.setStatus(200);
				} else {
					rsp.setHeader("WWW-Authenticate", "NTLM");
					rsp.setStatus(401);
				}
			}
		}), "/*");
		server.setUp();

		List<Authenticator.RequestorType> requests = new ArrayList<>();
		Authenticator original = Authenticator.getDefault();
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				requests.add(getRequestorType());
				return new PasswordAuthentication("user", "pass".toCharArray());
			}
		});
		try {
			HttpClientConnection connection = new HttpClientConnection(
					server.getURI().resolve("/ntlm/repo").toString());
			connection.setRequestMethod("GET");
			assertEquals(200, connection.getResponseCode());
			assertTrue(authorizationSeen.get());
			assertTrue(requests.contains(Authenticator.RequestorType.SERVER));
		} finally {
			Authenticator.setDefault(original);
		}
	}

	@Test
	public void testProxyAuthenticationStillUsesAuthenticator() throws Exception {
		ServletContextHandler context = server.addContext("/proxy");
		context.addServlet(new ServletHolder(new HttpServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse rsp) {
				if ("Basic dXNlcjpwYXNz".equals(req.getHeader("Proxy-Authorization"))) {
					rsp.setStatus(200);
				} else {
					rsp.setHeader("Proxy-Authenticate", "Basic realm=\"proxy\"");
					rsp.setStatus(407);
				}
			}
		}), "/*");
		server.setUp();

		List<Authenticator.RequestorType> requests = new ArrayList<>();
		Authenticator original = Authenticator.getDefault();
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				requests.add(getRequestorType());
				return getRequestorType() == RequestorType.PROXY
						? new PasswordAuthentication("user", "pass".toCharArray())
						: null;
			}
		});
		try {
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(
					server.getURI().getHost(), server.getPort()));
			HttpClientConnection connection = new HttpClientConnection(
					"http://example.invalid/proxy/repo", proxy);
			connection.setRequestMethod("GET");
			assertEquals(200, connection.getResponseCode());
			connection.getInputStream().close();
			assertTrue(requests.contains(Authenticator.RequestorType.PROXY));
			assertEquals(2, getRequests().size());
		} finally {
			Authenticator.setDefault(original);
		}
	}
}
