/*
 * Copyright (C) 2009-2017, Google Inc.
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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jetty.servlet.ServletContextHandler;
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

	/** {@inheritDoc} */
	@Override
	public void setUp() throws Exception {
		super.setUp();
		server = createServer();
	}

	/** {@inheritDoc} */
	@Override
	public void tearDown() throws Exception {
		server.tearDown();
		super.tearDown();
	}

	/**
	 * Create the {@linkAppServer}.This default implementation creates a server
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
	 */
	protected TestRepository<Repository> createTestRepository()
			throws IOException {
		return new TestRepository<>(createBareRepository());
	}

	/**
	 * Convert path to URIish
	 *
	 * @param path
	 * @return the URIish
	 * @throws URISyntaxException
	 */
	protected URIish toURIish(String path) throws URISyntaxException {
		URI u = server.getURI().resolve(path);
		return new URIish(u.toString());
	}

	/**
	 * Convert a path relative to the app's context path to a URIish
	 *
	 * @param app
	 * @param name
	 * @return the warnings (if any) from the last execution
	 * @throws URISyntaxException
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
	 * @param path
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
	 * @param tips
	 * @throws Exception
	 */
	protected static void fsck(Repository db, RevObject... tips)
			throws Exception {
		TestRepository<? extends Repository> tr =
				new TestRepository<>(db);
		tr.fsck(tips);
	}

	/**
	 * Mirror refs
	 *
	 * @param refs
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
	 * @param q
	 * @return collection of RefUpdates
	 * @throws IOException
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
	 * @param id
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
	 * @param newProtocol
	 * @param newPort
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
	 * @param pathComponents
	 * @return the extended URIish
	 * @throws URISyntaxException
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
