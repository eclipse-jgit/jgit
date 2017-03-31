/*
 * Copyright (C) 2010, 2012 Google Inc.
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

package org.eclipse.jgit.junit.http;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.MappedLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jgit.transport.URIish;

/**
 * Tiny web application server for unit testing.
 * <p>
 * Tests should start the server in their {@code setUp()} method and stop the
 * server in their {@code tearDown()} method. Only while started the server's
 * URL and/or port number can be obtained.
 */
public class AppServer {
	/** Realm name for the secure access areas. */
	public static final String realm = "Secure Area";

	/** Username for secured access areas. */
	public static final String username = "agitter";

	/** Password for {@link #username} in secured access areas. */
	public static final String password = "letmein";

	static {
		// Install a logger that throws warning messages.
		//
		final String prop = "org.eclipse.jetty.util.log.class";
		System.setProperty(prop, RecordingLogger.class.getName());
	}

	private final Server server;

	private final ServerConnector connector;

	private final ContextHandlerCollection contexts;

	private final TestRequestLog log;

	public AppServer() {
		this(0);
	}

	/**
	 * @param port
	 *            the http port number
	 * @since 4.2
	 */
	public AppServer(int port) {
		server = new Server();

		HttpConfiguration http_config = new HttpConfiguration();
		http_config.setSecureScheme("https");
		http_config.setSecurePort(8443);
		http_config.setOutputBufferSize(32768);

		connector = new ServerConnector(server,
				new HttpConnectionFactory(http_config));
		connector.setPort(port);
		try {
			final InetAddress me = InetAddress.getByName("localhost");
			connector.setHost(me.getHostAddress());
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot find localhost", e);
		}

		contexts = new ContextHandlerCollection();

		log = new TestRequestLog();
		log.setHandler(contexts);

		server.setConnectors(new Connector[] { connector });
		server.setHandler(log);
	}

	/**
	 * Create a new servlet context within the server.
	 * <p>
	 * This method should be invoked before the server is started, once for each
	 * context the caller wants to register.
	 *
	 * @param path
	 *            path of the context; use "/" for the root context if binding
	 *            to the root is desired.
	 * @return the context to add servlets into.
	 */
	public ServletContextHandler addContext(String path) {
		assertNotYetSetUp();
		if ("".equals(path))
			path = "/";

		ServletContextHandler ctx = new ServletContextHandler();
		ctx.setContextPath(path);
		contexts.addHandler(ctx);

		return ctx;
	}

	public ServletContextHandler authBasic(ServletContextHandler ctx) {
		assertNotYetSetUp();
		auth(ctx, new BasicAuthenticator());
		return ctx;
	}

	static class TestMappedLoginService extends MappedLoginService {
		private String role;

		TestMappedLoginService(String role) {
			this.role = role;
		}

		@Override
		protected UserIdentity loadUser(String who) {
			return null;
		}

		@Override
		protected void loadUsers() throws IOException {
			putUser(username, new Password(password), new String[] { role });
		}

		protected String[] loadRoleInfo(KnownUser user) {
			return null;
		}

		protected KnownUser loadUserInfo(String usrname) {
			return null;
		}
	}

	private void auth(ServletContextHandler ctx, Authenticator authType) {
		final String role = "can-access";

		MappedLoginService users = new TestMappedLoginService(role);
		ConstraintMapping cm = new ConstraintMapping();
		cm.setConstraint(new Constraint());
		cm.getConstraint().setAuthenticate(true);
		cm.getConstraint().setDataConstraint(Constraint.DC_NONE);
		cm.getConstraint().setRoles(new String[] { role });
		cm.setPathSpec("/*");

		ConstraintSecurityHandler sec = new ConstraintSecurityHandler();
		sec.setRealmName(realm);
		sec.setAuthenticator(authType);
		sec.setLoginService(users);
		sec.setConstraintMappings(new ConstraintMapping[] { cm });
		sec.setHandler(ctx);

		contexts.removeHandler(ctx);
		contexts.addHandler(sec);
	}

	/**
	 * Start the server on a random local port.
	 *
	 * @throws Exception
	 *             the server cannot be started, testing is not possible.
	 */
	public void setUp() throws Exception {
		RecordingLogger.clear();
		log.clear();
		server.start();
	}

	/**
	 * Shutdown the server.
	 *
	 * @throws Exception
	 *             the server refuses to halt, or wasn't running.
	 */
	public void tearDown() throws Exception {
		RecordingLogger.clear();
		log.clear();
		server.stop();
	}

	/**
	 * Get the URI to reference this server.
	 * <p>
	 * The returned URI includes the proper host name and port number, but does
	 * not contain a path.
	 *
	 * @return URI to reference this server's root context.
	 */
	public URI getURI() {
		assertAlreadySetUp();
		String host = connector.getHost();
		if (host.contains(":") && !host.startsWith("["))
			host = "[" + host + "]";
		final String uri = "http://" + host + ":" + getPort();
		try {
			return new URI(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Unexpected URI error on " + uri, e);
		}
	}

	/** @return the local port number the server is listening on. */
	public int getPort() {
		assertAlreadySetUp();
		return connector.getLocalPort();
	}

	/** @return all requests since the server was started. */
	public List<AccessEvent> getRequests() {
		return new ArrayList<AccessEvent>(log.getEvents());
	}

	/**
	 * @param base
	 *            base URI used to access the server.
	 * @param path
	 *            the path to locate requests for, relative to {@code base}.
	 * @return all requests which match the given path.
	 */
	public List<AccessEvent> getRequests(URIish base, String path) {
		return getRequests(HttpTestCase.join(base, path));
	}

	/**
	 * @param path
	 *            the path to locate requests for.
	 * @return all requests which match the given path.
	 */
	public List<AccessEvent> getRequests(String path) {
		ArrayList<AccessEvent> r = new ArrayList<AccessEvent>();
		for (AccessEvent event : log.getEvents()) {
			if (event.getPath().equals(path)) {
				r.add(event);
			}
		}
		return r;
	}

	private void assertNotYetSetUp() {
		assertFalse("server is not running", server.isRunning());
	}

	private void assertAlreadySetUp() {
		assertTrue("server is running", server.isRunning());
	}
}
