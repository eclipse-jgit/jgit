/*
 * Copyright (C) 2009-2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.http;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;

/**
 * Base class for HTTP related transport testing.
 */
public abstract class HttpTestCase extends LocalDiskRepositoryTestCase {
	/** Constant <code>master="Constants.R_HEADS + Constants.MASTER"</code> */
	protected static final String master = Constants.R_HEADS + Constants.MASTER;

	/** In-memory application server; subclass must start. */
	protected AppServer server;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		server = createServer();
	}

	@Override
	public void tearDown() throws Exception {
		server.tearDown();
		super.tearDown();
	}

	/**
	 * Create the {@link AppServer}.This default implementation creates a server
	 * without SSLsupport listening for HTTP connections on a dynamically chosen
	 * port, which can be gotten once the server has been started via its
	 * {@link org.eclipse.jgit.junit.http.AppServer#getPort()} method.
	 * Subclasses may override if they need a more specialized server.
	 *
	 * @return the {@link org.eclipse.jgit.junit.http.AppServer}.
	 * @since 4.9
	 */
	protected AppServer createServer() {
		return new AppServer();
	}

	/**
	 * Create TestRepository
	 *
	 * @return the TestRepository
	 * @throws IOException
	 *             if an IO error occurred
	 */
	protected TestRepository<Repository> createTestRepository()
			throws IOException {
		final FileRepository repository = createBareRepository();
		addRepoToClose(repository);
		return new TestRepository<>(repository);
	}

	/**
	 * Convert path to URIish
	 *
	 * @param path
	 *            the path
	 * @return the URIish
	 * @throws URISyntaxException
	 *             if URI is invalid
	 */
	protected URIish toURIish(String path) throws URISyntaxException {
		URI u = server.getURI().resolve(path);
		return new URIish(u.toString());
	}

	/**
	 * Convert a path relative to the app's context path to a URIish
	 *
	 * @param app
	 *            app name
	 * @param name
	 *            context path name
	 * @return the warnings (if any) from the last execution
	 * @throws URISyntaxException
	 *             if URI is invalid
	 * @since 7.0
	 */
	protected URIish toURIish(ServletContextHandler app, String name)
			throws URISyntaxException {
		String p = app.getContextPath();
		if (!p.endsWith("/") && !name.startsWith("/"))
			p += "/";
		p += name;
		return toURIish(p);
	}

	/**
	 * Get requests.
	 *
	 * @return list of events
	 */
	protected List<AccessEvent> getRequests() {
		return server.getRequests();
	}

	/**
	 * Get requests.
	 *
	 * @param base
	 *            base URI
	 * @param path
	 *            the request path relative to {@code base}
	 *
	 * @return list of events
	 */
	protected List<AccessEvent> getRequests(URIish base, String path) {
		return server.getRequests(base, path);
	}

	/**
	 * Get requests.
	 *
	 * @param path
	 *            request path
	 *
	 * @return list of events
	 */
	protected List<AccessEvent> getRequests(String path) {
		return server.getRequests(path);
	}

	/**
	 * Run fsck
	 *
	 * @param db
	 *            the repository
	 * @param tips
	 *            tips to start checking from
	 * @throws Exception
	 *             if an error occurred
	 */
	protected static void fsck(Repository db, RevObject... tips)
			throws Exception {
		try (TestRepository<? extends Repository> tr =
				new TestRepository<>(db)) {
			tr.fsck(tips);
		}
	}

	/**
	 * Mirror refs
	 *
	 * @param refs
	 *            the refs
	 * @return set of RefSpecs
	 */
	protected static Set<RefSpec> mirror(String... refs) {
		HashSet<RefSpec> r = new HashSet<>();
		for (String name : refs) {
			RefSpec rs = new RefSpec(name);
			rs = rs.setDestination(name);
			rs = rs.setForceUpdate(true);
			r.add(rs);
		}
		return r;
	}

	/**
	 * Push a commit
	 *
	 * @param from
	 *            repository from which to push
	 * @param q
	 *            commit to push
	 * @return collection of RefUpdates
	 * @throws IOException
	 *             if an IO error occurred
	 */
	protected static Collection<RemoteRefUpdate> push(TestRepository from,
			RevCommit q) throws IOException {
		final Repository db = from.getRepository();
		final String srcExpr = q.name();
		final String dstName = master;
		final boolean forceUpdate = true;
		final String localName = null;
		final ObjectId oldId = null;

		RemoteRefUpdate u = new RemoteRefUpdate(db, srcExpr, dstName,
				forceUpdate, localName, oldId);
		return Collections.singleton(u);
	}

	/**
	 * Create loose object path
	 *
	 * @param base
	 *            base URI
	 * @param id
	 *            objectId
	 * @return path of the loose object
	 */
	public static String loose(URIish base, AnyObjectId id) {
		final String objectName = id.name();
		final String d = objectName.substring(0, 2);
		final String f = objectName.substring(2);
		return join(base, "objects/" + d + "/" + f);
	}

	/**
	 * Join a base URIish and a path
	 *
	 * @param base
	 *            base URI
	 * @param path
	 *            a relative path
	 * @return the joined path
	 */
	public static String join(URIish base, String path) {
		if (path.startsWith("/"))
			fail("Cannot join absolute path " + path + " to URIish " + base);

		String dir = base.getPath();
		if (!dir.endsWith("/"))
			dir += "/";
		return dir + path;
	}

	/**
	 * Rewrite a url
	 *
	 * @param url
	 *            the URL
	 * @param newProtocol
	 *            new protocol
	 * @param newPort
	 *            new port
	 * @return the rewritten url
	 */
	protected static String rewriteUrl(String url, String newProtocol,
			int newPort) {
		String newUrl = url;
		if (newProtocol != null && !newProtocol.isEmpty()) {
			int schemeEnd = newUrl.indexOf("://");
			if (schemeEnd >= 0) {
				newUrl = newProtocol + newUrl.substring(schemeEnd);
			}
		}
		if (newPort > 0) {
			newUrl = newUrl.replaceFirst(":\\d+/", ":" + newPort + "/");
		} else {
			// Remove the port, if any
			newUrl = newUrl.replaceFirst(":\\d+/", "/");
		}
		return newUrl;
	}

	/**
	 * Extend a path
	 *
	 * @param uri
	 *            the URI
	 * @param pathComponents
	 *            path components
	 * @return the extended URIish
	 * @throws URISyntaxException
	 *             if URI is invalid
	 */
	protected static URIish extendPath(URIish uri, String pathComponents)
			throws URISyntaxException {
		String raw = uri.toString();
		String newComponents = pathComponents;
		if (!newComponents.startsWith("/")) {
			newComponents = '/' + newComponents;
		}
		if (!newComponents.endsWith("/")) {
			newComponents += '/';
		}
		int i = raw.lastIndexOf('/');
		raw = raw.substring(0, i) + newComponents + raw.substring(i + 1);
		return new URIish(raw);
	}
}
