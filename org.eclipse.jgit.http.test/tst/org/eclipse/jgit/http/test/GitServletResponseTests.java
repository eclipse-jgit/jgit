/*
 * Copyright (C) 2015, christian.Halstrick <christian.halstrick@sap.com>
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TooLargePackException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for correct responses of {@link GitServlet}. Especially error
 * situations where the {@link GitServlet} faces exceptions during request
 * processing are tested
 */
public class GitServletResponseTests extends HttpTestCase {
	private Repository srvRepo;

	private URIish srvURI;

	private GitServlet gs;

	private long maxPackSize = 0; // the maximum pack file size used by
									// the server

	private PostReceiveHook postHook = null;

	private PreReceiveHook preHook = null;

	private ObjectChecker oc = null;

	/**
	 * Setup a http server using {@link GitServlet}. Tests should be able to
	 * configure the maximum pack file size, the object checker and custom hooks
	 * just before they talk to the server.
	 */
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> srv = createTestRepository();
		final String repoName = srv.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		gs = new GitServlet();
		gs.setRepositoryResolver(new RepositoryResolver<HttpServletRequest>() {
			@Override
			public Repository open(HttpServletRequest req, String name)
					throws RepositoryNotFoundException,
					ServiceNotEnabledException {
				if (!name.equals(repoName))
					throw new RepositoryNotFoundException(name);

				final Repository db = srv.getRepository();
				db.incrementOpen();
				return db;
			}
		});
		gs.setReceivePackFactory(new DefaultReceivePackFactory() {
			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				ReceivePack recv = super.create(req, db);
				if (maxPackSize > 0)
					recv.setMaxPackSizeLimit(maxPackSize);
				if (postHook != null)
					recv.setPostReceiveHook(postHook);
				if (preHook != null)
					recv.setPreReceiveHook(preHook);
				if (oc != null)
					recv.setObjectChecker(oc);
				return recv;
			}

		});
		app.addServlet(new ServletHolder(gs), "/*");

		server.setUp();

		srvRepo = srv.getRepository();
		srvURI = toURIish(app, repoName);

		StoredConfig cfg = srvRepo.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();
	}

	/**
	 * Configure a {@link GitServlet} that faces a {@link IllegalStateException}
	 * during executing preReceiveHooks. This used to lead to exceptions with a
	 * description of "invalid channel 101" on the client side. Make sure
	 * clients receive the correct response on the correct sideband.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRuntimeExceptionInPreReceiveHook() throws Exception {
		final TestRepository client = createTestRepository();
		final RevBlob Q_txt = client
				.blob("some blob content to measure pack size");
		final RevCommit Q = client.commit().add("Q", Q_txt).create();
		final Repository clientRepo = client.getRepository();
		final String srvBranchName = Constants.R_HEADS + "new.branch";

		maxPackSize = 0;
		postHook = null;
		preHook = new PreReceiveHook() {
			@Override
			public void onPreReceive(ReceivePack rp,
					Collection<ReceiveCommand> commands) {
				throw new IllegalStateException();
			}
		};

		try (Transport t = Transport.open(clientRepo, srvURI)) {
			RemoteRefUpdate update = new RemoteRefUpdate(clientRepo, Q.name(),
					srvBranchName, false, null, null);
			try {
				t.push(NullProgressMonitor.INSTANCE,
						Collections.singleton(update));
				fail("should not reach this line");
			} catch (Exception e) {
				assertTrue(e instanceof TransportException);
			}
		}
	}

	/**
	 * Configure a {@link GitServlet} that faces a {@link IllegalStateException}
	 * during executing objectChecking.
	 *
	 * @throws Exception
	 */
	@Test
	public void testObjectCheckerException() throws Exception {
		final TestRepository client = createTestRepository();
		final RevBlob Q_txt = client
				.blob("some blob content to measure pack size");
		final RevCommit Q = client.commit().add("Q", Q_txt).create();
		final Repository clientRepo = client.getRepository();
		final String srvBranchName = Constants.R_HEADS + "new.branch";

		maxPackSize = 0;
		postHook = null;
		preHook = null;
		oc = new ObjectChecker() {
			@Override
			public void checkCommit(AnyObjectId id, byte[] raw)
					throws CorruptObjectException {
				throw new CorruptObjectException("refusing all commits");
			}
		};

		try (Transport t = Transport.open(clientRepo, srvURI)) {
			RemoteRefUpdate update = new RemoteRefUpdate(clientRepo, Q.name(),
					srvBranchName, false, null, null);
			try {
				t.push(NullProgressMonitor.INSTANCE,
						Collections.singleton(update));
				fail("should not reach this line");
			} catch (Exception e) {
				assertTrue(e instanceof TransportException);
			}
		}
	}

	/**
	 * Configure a {@link GitServlet} that faces a {@link TooLargePackException}
	 * during persisting the pack and a {@link IllegalStateException} during
	 * executing postReceiveHooks. This used to lead to exceptions with a
	 * description of "invalid channel 101" on the client side. Make sure
	 * clients receive the correct response about the too large pack on the
	 * correct sideband.
	 *
	 * @throws Exception
	 */
	@Test
	public void testUnpackErrorWithSubsequentExceptionInPostReceiveHook()
			throws Exception {
		final TestRepository client = createTestRepository();
		final RevBlob Q_txt = client
				.blob("some blob content to measure pack size");
		final RevCommit Q = client.commit().add("Q", Q_txt).create();
		final Repository clientRepo = client.getRepository();
		final String srvBranchName = Constants.R_HEADS + "new.branch";

		// this maxPackSize leads to an unPackError
		maxPackSize = 100;
		// this PostReceiveHook when called after an unsuccesfull unpack will
		// lead to an IllegalStateException
		postHook = new PostReceiveHook() {
			@Override
			public void onPostReceive(ReceivePack rp,
					Collection<ReceiveCommand> commands) {
				// the maxPackSize setting caused that the packfile couldn't be
				// saved to disk. Calling getPackSize() now will lead to a
				// IllegalStateException.
				rp.getPackSize();
			}
		};

		try (Transport t = Transport.open(clientRepo, srvURI)) {
			RemoteRefUpdate update = new RemoteRefUpdate(clientRepo, Q.name(),
					srvBranchName, false, null, null);
			try {
				t.push(NullProgressMonitor.INSTANCE,
						Collections.singleton(update));
				fail("should not reach this line");
			} catch (Exception e) {
				assertTrue(e instanceof TooLargePackException);
			}
		}
	}
}
