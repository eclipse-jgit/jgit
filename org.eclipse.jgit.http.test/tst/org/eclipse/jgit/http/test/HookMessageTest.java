/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.junit.CustomParameterResolver;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
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
public class HookMessageTest extends AllFactoriesHttpTestCase {

	private Repository remoteRepository;

	private URIish remoteURI;

	@BeforeEach
	public void setUp(HttpConnectionFactory cf) throws Exception {
		super.setUp();
		HttpTransport.setConnectionFactory(cf);

		TestRepository<Repository> src = createTestRepository();
		String srcName = src.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		GitServlet gs = new GitServlet();
		gs.setRepositoryResolver((HttpServletRequest req, String name) -> {
			if (!name.equals(srcName)) {
				throw new RepositoryNotFoundException(name);
			}
			Repository db = src.getRepository();
			db.incrementOpen();
			return db;
		});
		gs.setReceivePackFactory(new DefaultReceivePackFactory() {
			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				ReceivePack recv = super.create(req, db);
				recv.setPreReceiveHook((ReceivePack rp,
						Collection<ReceiveCommand> commands) -> {
					rp.sendMessage("message line 1");
					rp.sendError("no soup for you!");
					rp.sendMessage("come back next year!");
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
	void testPush_CreateBranch(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		TestRepository src = createTestRepository();
		RevBlob Q_txt = src.blob("new text");
		RevCommit Q = src.commit().add("Q", Q_txt).create();
		Repository db = src.getRepository();
		String dstName = Constants.R_HEADS + "new.branch";
		PushResult result;

		try (Transport t = Transport.open(db, remoteURI)) {
			String srcExpr = Q.name();
			boolean forceUpdate = false;
			String localName = null;
			ObjectId oldId = null;

			RemoteRefUpdate update = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			result = t.push(NullProgressMonitor.INSTANCE,
					Collections.singleton(update));
		}

		assertTrue(remoteRepository.getObjectDatabase().has(Q_txt));
		assertNotNull(remoteRepository.exactRef(dstName), "has " + dstName);
		assertEquals(Q, remoteRepository.exactRef(dstName).getObjectId());
		fsck(remoteRepository, Q);

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());

		AccessEvent service = requests.get(1);
		assertEquals("POST", service.getMethod());
		assertEquals(join(remoteURI, "git-receive-pack"), service.getPath());
		assertEquals(200, service.getStatus());

		assertEquals("message line 1\n" //
				+ "error: no soup for you!\n" //
				+ "come back next year!\n", //
				result.getMessages());
	}

	@TestAllImplementations
	void testPush_HookMessagesToOutputStream(
			@SuppressWarnings("unused") HttpConnectionFactory cf)
			throws Exception {
		TestRepository src = createTestRepository();
		RevBlob Q_txt = src.blob("new text");
		RevCommit Q = src.commit().add("Q", Q_txt).create();
		Repository db = src.getRepository();
		String dstName = Constants.R_HEADS + "new.branch";
		PushResult result;

		OutputStream out = new ByteArrayOutputStream();
		try (Transport t = Transport.open(db, remoteURI)) {
			String srcExpr = Q.name();
			boolean forceUpdate = false;
			String localName = null;
			ObjectId oldId = null;

			RemoteRefUpdate update = new RemoteRefUpdate(src.getRepository(),
					srcExpr, dstName, forceUpdate, localName, oldId);
			result = t.push(NullProgressMonitor.INSTANCE,
					Collections.singleton(update), out);
		}

		String expectedMessage = "message line 1\n" //
				+ "error: no soup for you!\n" //
				+ "come back next year!\n";
		assertEquals(expectedMessage, //
				result.getMessages());

		assertEquals(expectedMessage, out.toString());
	}

}
