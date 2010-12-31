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
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;

/** Base class for HTTP related transport testing. */
public abstract class HttpTestCase extends LocalDiskRepositoryTestCase {
	protected static final String master = Constants.R_HEADS + Constants.MASTER;

	/** In-memory application server; subclass must start. */
	protected AppServer server;

	public void setUp() throws Exception {
		super.setUp();
		server = new AppServer();
	}

	public void tearDown() throws Exception {
		server.tearDown();
		super.tearDown();
	}

	protected TestRepository<FileRepository> createTestRepository()
			throws IOException {
		return new TestRepository<FileRepository>(createBareRepository());
	}

	protected URIish toURIish(String path) throws URISyntaxException {
		URI u = server.getURI().resolve(path);
		return new URIish(u.toString());
	}

	protected URIish toURIish(ServletContextHandler app, String name)
			throws URISyntaxException {
		String p = app.getContextPath();
		if (!p.endsWith("/") && !name.startsWith("/"))
			p += "/";
		p += name;
		return toURIish(p);
	}

	protected List<AccessEvent> getRequests() {
		return server.getRequests();
	}

	protected List<AccessEvent> getRequests(URIish base, String path) {
		return server.getRequests(base, path);
	}

	protected List<AccessEvent> getRequests(String path) {
		return server.getRequests(path);
	}

	protected static void fsck(Repository db, RevObject... tips)
			throws Exception {
		new TestRepository(db).fsck(tips);
	}

	protected static Set<RefSpec> mirror(String... refs) {
		HashSet<RefSpec> r = new HashSet<RefSpec>();
		for (String name : refs) {
			RefSpec rs = new RefSpec(name);
			rs = rs.setDestination(name);
			rs = rs.setForceUpdate(true);
			r.add(rs);
		}
		return r;
	}

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

	public static String loose(URIish base, AnyObjectId id) {
		final String objectName = id.name();
		final String d = objectName.substring(0, 2);
		final String f = objectName.substring(2);
		return join(base, "objects/" + d + "/" + f);
	}

	public static String join(URIish base, String path) {
		if (path.startsWith("/"))
			fail("Cannot join absolute path " + path + " to URIish " + base);

		String dir = base.getPath();
		if (!dir.endsWith("/"))
			dir += "/";
		return dir + path;
	}
}
