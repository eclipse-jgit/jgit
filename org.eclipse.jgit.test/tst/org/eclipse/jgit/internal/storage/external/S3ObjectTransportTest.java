/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.TestProtocol;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UploadPack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Clients fetching from and pushing to a repository whose large blobs exist
 * only in S3.
 */
public class S3ObjectTransportTest extends LocalDiskRepositoryTestCase {

	private static final int MIN_BLOB_SIZE = 1024;

	private static final String BUCKET = "jgit-test";

	private static final byte[] LARGE = content(4096, 'a');

	private FakeS3Server s3;

	private FileRepository server;

	private TestRepository<FileRepository> remote;

	private InMemoryRepository client;

	private TestProtocol<Object> protocol;

	private URIish uri;

	private RevBlob largeBlob;

	private RevBlob smallBlob;

	private RevCommit tip;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		s3 = new FakeS3Server();
		FileRepository initial = createBareRepository();
		FileBasedConfig cfg = initial.getConfig();
		cfg.setString("objectStore", "s3", "type", "s3");
		cfg.setString("objectStore", "s3", "bucket", BUCKET);
		cfg.setString("objectStore", "s3", "region", "eu-central-1");
		cfg.setString("objectStore", "s3", "prefix", "repos/server.git");
		cfg.setString("objectStore", "s3", "endpoint",
				s3.endpoint().toExternalForm());
		cfg.setString("objectStore", "s3", "accessKey", "test-access-key");
		cfg.setString("objectStore", "s3", "secretKey", "test-secret-key");
		cfg.setLong("objectStore", "s3", "minBlobSize", MIN_BLOB_SIZE);
		cfg.setBoolean("uploadpack", null, "allowfilter", true);
		cfg.setBoolean("uploadpack", null, "allowreachablesha1inwant", true);
		cfg.save();
		server = new FileRepository(initial.getDirectory());
		addRepoToClose(server);
		remote = new TestRepository<>(server);

		largeBlob = storeInS3(LARGE);
		smallBlob = remote.blob("small");
		tip = remote.branch("refs/heads/master").commit()
				.add("large.bin", largeBlob).add("small.txt", smallBlob)
				.create();
		assertOnlyInS3(largeBlob);

		client = new InMemoryRepository(
				new DfsRepositoryDescription("client"));
		protocol = new TestProtocol<>(
				(Object req, Repository db) -> new UploadPack(db),
				(Object req, Repository db) -> new ReceivePack(db));
		uri = protocol.register(new Object(), server);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		Transport.unregister(protocol);
		remote.close();
		client.close();
		s3.close();
		super.tearDown();
	}

	@Test
	public void serverRepositoryReadsBlobFromS3() throws Exception {
		assertTrue(server.getObjectDatabase().has(largeBlob));
		assertContent(server, largeBlob, LARGE);
	}

	@Test
	public void fullFetchReceivesBlobFromS3() throws Exception {
		fetch(null, "refs/heads/master:refs/heads/master");

		assertTrue(client.getObjectDatabase().has(tip));
		assertTrue(client.getObjectDatabase().has(smallBlob));
		assertContent(client, largeBlob, LARGE);
		assertTrue(s3.requests("GET") > 0);
	}

	@Test
	public void partialFetchOmitsLargeBlobsUntilRequested() throws Exception {
		FilterSpec filter = blobLimit();

		fetch(filter, "refs/heads/master:refs/heads/master");

		assertTrue(client.getObjectDatabase().has(tip));
		assertTrue(client.getObjectDatabase().has(tip.getTree()));
		assertTrue(client.getObjectDatabase().has(smallBlob));
		assertFalse(client.getObjectDatabase().has(largeBlob));
		assertEquals("the filtered fetch does not read content from S3", 0,
				s3.requests("GET"));

		// What a partial clone does when the blob is needed, for example on
		// checkout: request it by id.
		fetch(filter, largeBlob.name());

		assertContent(client, largeBlob, LARGE);
	}

	@Test
	public void unreachableBlobInS3IsRefused() throws Exception {
		RevBlob secretBlob = storeInS3(content(4096, 's'));
		remote.branch("refs/heads/secret").commit()
				.add("secret.bin", secretBlob).create();
		RefUpdate ru = server.updateRef("refs/heads/secret");
		ru.setForceUpdate(true);
		ru.delete();

		assertThrows(TransportException.class,
				() -> fetch(blobLimit(), secretBlob.name()));
		assertFalse(client.getObjectDatabase().has(secretBlob));
	}

	@Test
	public void fetchFailsWhileS3IsUnavailable() throws Exception {
		s3.denyAll(true);

		assertThrows(TransportException.class,
				() -> fetch(null, "refs/heads/master:refs/heads/master"));
		assertFalse(client.getObjectDatabase().has(tip));
		assertFalse(client.getObjectDatabase().has(largeBlob));
	}

	@Test
	public void pushReachesServerWhileS3IsAvailable() throws Exception {
		RevCommit pushed = commitOnClient();

		assertEquals(Status.OK, push(pushed));
		assertEquals(pushed, server.exactRef("refs/heads/topic").getObjectId());
	}

	@Test
	public void pushIsRejectedWhileS3IsUnavailable() throws Exception {
		RevCommit pushed = commitOnClient();
		s3.denyAll(true);

		// The server cannot tell whether the pushed objects collide with
		// objects in S3, so it must not accept them.
		assertFalse(Status.OK.equals(pushOrAbort(pushed)));
		assertNull(server.exactRef("refs/heads/topic"));
	}

	/** @return the push status, or {@code null} if the push was aborted */
	private Status pushOrAbort(RevCommit commit) throws Exception {
		try {
			return push(commit);
		} catch (TransportException aborted) {
			return null;
		}
	}

	private RevCommit commitOnClient() throws Exception {
		fetch(null, "refs/heads/master:refs/heads/master");
		try (TestRepository<InMemoryRepository> local = new TestRepository<>(
				client)) {
			return local.branch("refs/heads/topic").commit().parent(tip)
					.add("new.txt", local.blob("new content")).create();
		}
	}

	private Status push(RevCommit commit) throws Exception {
		try (Transport tn = protocol.open(uri, client, "server")) {
			RemoteRefUpdate update = new RemoteRefUpdate(client,
					"refs/heads/topic", commit, "refs/heads/topic", false,
					null, null);
			PushResult result = tn.push(NullProgressMonitor.INSTANCE,
					Collections.singleton(update));
			return result.getRemoteUpdate("refs/heads/topic").getStatus();
		}
	}

	private RevBlob storeInS3(byte[] content) throws IOException {
		ObjectId id = new ObjectInserter.Formatter().idFor(OBJ_BLOB, content);
		ExternalObjectDatabase s3db = server.getObjectDatabase()
				.getExternalObjectDatabases().get(0);
		try (ExternalObjectInserter ins = s3db.newInserter()) {
			ins.insert(id, OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
		}
		return remote.getRevWalk().lookupBlob(id);
	}

	private void assertOnlyInS3(AnyObjectId id) {
		assertFalse(server.getObjectDatabase().fileFor(id).exists());
		assertTrue(server.getObjectDatabase().getPacks().isEmpty());
		assertTrue(s3.object(BUCKET, "repos/server.git/" + id.name()) != null);
	}

	private void fetch(FilterSpec filter, String refSpec) throws Exception {
		try (Transport tn = protocol.open(uri, client, "server")) {
			if (filter != null) {
				tn.setFilterSpec(filter);
			}
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(refSpec)));
		}
	}

	private static FilterSpec blobLimit() throws Exception {
		return FilterSpec.fromFilterLine("blob:limit=" + MIN_BLOB_SIZE);
	}

	private static void assertContent(Repository r, AnyObjectId id,
			byte[] expected) throws IOException {
		try (ObjectReader reader = r.newObjectReader();
				ObjectStream in = reader.open(id, OBJ_BLOB).openStream()) {
			assertArrayEquals(expected, in.readAllBytes());
		}
	}

	private static byte[] content(int size, char seed) {
		byte[] content = new byte[size];
		for (int i = 0; i < size; i++) {
			content[i] = (byte) (seed + i % 7);
		}
		return content;
	}
}
