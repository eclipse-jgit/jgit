/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit.http;

import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;

/**
 * Simple http server for testing http access to Git repositories.
 * Authentication with hardcoded credentials user:agitter password:letmein.
 */
public class SimpleHttpServer {

	AppServer server;

	private final Repository db;

	private URIish uri;

	private URIish secureUri;

	/**
	 * Constructor for <code>SimpleHttpServer</code>.
	 *
	 * @param repository
	 */
	public SimpleHttpServer(Repository repository) {
		this(repository, false);
	}

	/**
	 * Constructor for <code>SimpleHttpServer</code>.
	 *
	 * @param repository
	 * @param withSsl
	 */
	public SimpleHttpServer(Repository repository, boolean withSsl) {
		this.db = repository;
		server = new AppServer(0, withSsl ? 0 : -1);
	}

	/**
	 * Start the server
	 *
	 * @throws Exception
	 */
	public void start() throws Exception {
		ServletContextHandler sBasic = server.authBasic(smart("/sbasic"));
		server.setUp();
		final String srcName = db.getDirectory().getName();
		uri = toURIish(sBasic, srcName);
		int sslPort = server.getSecurePort();
		if (sslPort > 0) {
			secureUri = uri.setPort(sslPort).setScheme("https");
		}
	}

	/**
	 * Stop the server.
	 *
	 * @throws Exception
	 */
	public void stop() throws Exception {
		server.tearDown();
	}

	/**
	 * Get the <code>uri</code>.
	 *
	 * @return the uri
	 */
	public URIish getUri() {
		return uri;
	}

	/**
	 * Get the <code>secureUri</code>.
	 *
	 * @return the secure uri
	 */
	public URIish getSecureUri() {
		return secureUri;
	}

	private ServletContextHandler smart(String path) {
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver((HttpServletRequest req, String name) -> {
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

	private URIish toURIish(String path) throws URISyntaxException {
		URI u = server.getURI().resolve(path);
		return new URIish(u.toString());
	}

	private URIish toURIish(ServletContextHandler app, String name)
			throws URISyntaxException {
		String p = app.getContextPath();
		if (!p.endsWith("/") && !name.startsWith("/"))
			p += "/";
		p += name;
		return toURIish(p);
	}
}
