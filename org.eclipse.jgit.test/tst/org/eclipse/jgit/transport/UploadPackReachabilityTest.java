/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test combinations of:
 * <ul>
 * <li>Fetch a blob or a commit</li>
 * <li>Fetched object is reachable or not</li>
 * <li>With and without bitmaps</li>
 * </ul>
 */
public class UploadPackReachabilityTest {

	private URIish uri;

	private TestProtocol<Object> testProtocol;

	private Object ctx = new Object();

	private InMemoryRepository server;

	private InMemoryRepository client;

	private TestRepository<InMemoryRepository> remote;

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
		client = newRepo("client");

		remote = new TestRepository<>(server);
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	@Test
	public void testFetchUnreachableBlobWithBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		remote.commit(remote.tree(remote.file("foo", blob)));
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			TransportException e = assertThrows(TransportException.class,
					() -> tn.fetch(NullProgressMonitor.INSTANCE, Collections
							.singletonList(new RefSpec(blob.name()))));
			assertThat(e.getMessage(),
					containsString("want " + blob.name() + " not valid"));
		}
	}

	@Test
	public void testFetchReachableBlobWithoutBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		RevCommit commit = remote.commit(remote.tree(remote.file("foo", blob)));
		remote.update("master", commit);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			TransportException e = assertThrows(TransportException.class,
					() -> tn.fetch(NullProgressMonitor.INSTANCE, Collections
							.singletonList(new RefSpec(blob.name()))));
			assertThat(e.getMessage(),
					containsString(
						"want " + blob.name() + " not valid"));
		}
	}

	@Test
	public void testFetchReachableBlobWithoutBitmapButFilterAllowed() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob blob = remote2.blob("foo");
			RevCommit commit = remote2.commit(remote2.tree(remote2.file("foo", blob)));
			remote2.update("master", commit);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					true);

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.fetch(NullProgressMonitor.INSTANCE,
						Collections.singletonList(new RefSpec(blob.name())));
				assertTrue(client.getObjectDatabase().has(blob.toObjectId()));
			}
		}
	}

	@Test
	public void testFetchUnreachableBlobWithoutBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		remote.commit(remote.tree(remote.file("foo", blob)));

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			TransportException e = assertThrows(TransportException.class, () ->
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(blob.name()))));
			assertThat(e.getMessage(),
					containsString("want " + blob.name() + " not valid"));
		}
	}

	@Test
	public void testFetchReachableBlobWithBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		RevCommit commit = remote.commit(remote.tree(remote.file("foo", blob)));
		remote.update("master", commit);
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(blob.name())));
			assertTrue(client.getObjectDatabase().has(blob.toObjectId()));
		}
	}

	@Test
	public void testFetchReachableCommitWithBitmap() throws Exception {
		RevCommit commit = remote
				.commit(remote.tree(remote.file("foo", remote.blob("foo"))));
		remote.update("master", commit);
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(commit.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertTrue(client.getObjectDatabase().has(commit.toObjectId()));
		}
	}

	@Test
	public void testFetchReachableCommitWithoutBitmap() throws Exception {
		RevCommit commit = remote
				.commit(remote.tree(remote.file("foo", remote.blob("foo"))));
		remote.update("master", commit);
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(commit.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertTrue(client.getObjectDatabase().has(commit.toObjectId()));
		}
	}

	@Test
	public void testFetchUnreachableCommitWithBitmap() throws Exception {
		RevCommit commit = remote
				.commit(remote.tree(remote.file("foo", remote.blob("foo"))));
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(commit.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			TransportException e = assertThrows(TransportException.class,
					() -> tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name()))));
			assertThat(e.getMessage(),
					containsString("want " + commit.name() + " not valid"));
		}
	}

	@Test
	public void testFetchUnreachableCommitWithoutBitmap() throws Exception {
		RevCommit commit = remote
				.commit(remote.tree(remote.file("foo", remote.blob("foo"))));

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(commit.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			TransportException e = assertThrows(TransportException.class,
					() -> tn.fetch(NullProgressMonitor.INSTANCE, Collections
							.singletonList(new RefSpec(commit.name()))));
			assertThat(e.getMessage(),
					containsString("want " + commit.name() + " not valid"));
		}
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	private void generateBitmaps(InMemoryRepository repo) throws Exception {
		new DfsGarbageCollector(repo).pack(null);
		repo.scanForRepoChanges();
	}

	private static TestProtocol<Object> generateReachableCommitUploadPackProtocol() {
		return new TestProtocol<>(new UploadPackFactory<Object>() {
			@Override
			public UploadPack create(Object req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				UploadPack up = new UploadPack(db);
				up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
				return up;
			}
		}, null);
	}
}
