/*
 * Copyright (C) 2014, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.http.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.junit.CustomParameterResolver;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(CustomParameterResolver.class)
public class MeasurePackSizeTest extends AllFactoriesHttpTestCase {

	private Repository remoteRepository;

	private URIish remoteURI;

	long packSize = -1;

	@BeforeEach
	public void setUp(HttpConnectionFactory cf) throws Exception {
		super.setUp();
		HttpTransport.setConnectionFactory(cf);

		final TestRepository<Repository> src = createTestRepository();
		final String srcName = src.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver((HttpServletRequest req, String name) -> {
			if (!name.equals(srcName)) {
				throw new RepositoryNotFoundException(name);
			}
			final Repository db = src.getRepository();
			db.incrementOpen();
			return db;
		});
		gs.setReceivePackFactory(new DefaultReceivePackFactory() {
			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				ReceivePack recv = super.create(req, db);
				recv.setPostReceiveHook((ReceivePack rp,
						Collection<ReceiveCommand> commands) -> {
					packSize = rp.getPackSize();
				});
				return recv;
			}

		});
		app.addServlet(new ServletHolder(gs), "/*");

		server.setUp();

		remoteRepository = src.getRepository();
		addRepoToClose(remoteRepository);
		remoteURI = toURIish(app, srcName);

		StoredConfig cfg = remoteRepository.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();
	}

	@TestAllImplementations
	void testPush_packSize(@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		final TestRepository src = createTestRepository();
		final RevBlob Q_txt = src
				.blob("some blob content to measure pack size");
		final RevCommit Q = src.commit().add("Q", Q_txt).create();
		final Repository db = src.getRepository();
		final String dstName = Constants.R_HEADS + "new.branch";
		PushResult result;

		try (Transport t = Transport.open(db, remoteURI)) {
			final String srcExpr = Q.name();
			final boolean forceUpdate = false;
			final String localName = null;
			final ObjectId oldId = null;

			RemoteRefUpdate update = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			result = t.push(NullProgressMonitor.INSTANCE,
					Collections.singleton(update));
		}
		assertEquals(1, result.getRemoteUpdates().size(),
				"expected 1 RemoteUpdate");
		assertEquals(1398, packSize, "unexpected pack size");
	}

}
