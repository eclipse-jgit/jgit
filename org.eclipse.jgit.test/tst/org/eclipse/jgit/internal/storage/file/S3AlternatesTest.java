/*
 * Copyright (C) 2026, NVIDIA Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TestProtocol;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class S3AlternatesTest extends LocalDiskRepositoryTestCase {
	private static final String LINK = "s3://bucket/project";

	private static final byte[] BIG = "large blob held only in S3"
			.getBytes(UTF_8);

	private FileRepository server;

	private FileRepository store;

	private TestRepository<FileRepository> serverTr;

	private TestProtocol<Object> testProtocol;

	private final List<URI> opened = new ArrayList<>();

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		server = createBareRepository();
		store = createBareRepository();
		serverTr = new TestRepository<>(server);
		setOpener(this::openStore);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (testProtocol != null) {
			Transport.unregister(testProtocol);
		}
		serverTr.close();
		super.tearDown();
	}

	@Test
	public void objectOnlyInS3IsFound() throws Exception {
		RevBlob big = storeBlob(BIG);
		writeS3Alternates(LINK);

		assertTrue(server.getObjectDatabase().has(big));
		try (ObjectReader reader = server.newObjectReader()) {
			assertTrue(reader.has(big));
			assertArrayEquals(BIG, reader.open(big).getCachedBytes());
			assertEquals(BIG.length,
					reader.getObjectSize(big, ObjectReader.OBJ_ANY));
		}
		assertEquals(Collections.singletonList(URI.create(LINK)), opened);
	}

	@Test
	public void cachedDatabaseFindsObjectOnlyInS3() throws Exception {
		RevBlob big = storeBlob(BIG);
		writeS3Alternates(LINK);

		ObjectDatabase cached = server.getObjectDatabase().newCachedDatabase();
		assertTrue(cached.has(big));
		try (ObjectReader reader = cached.newReader()) {
			assertArrayEquals(BIG, reader.open(big).getCachedBytes());
		}
	}

	@Test
	public void noFileMeansNoS3Lookups() throws Exception {
		RevBlob big = storeBlob(BIG);

		assertFalse(server.getObjectDatabase().has(big));
		assertTrue(opened.isEmpty());
	}

	@Test
	public void localObjectsDoNotOpenS3() throws Exception {
		RevBlob local = serverTr.blob("local");
		writeS3Alternates(LINK);
		AtomicInteger opens = new AtomicInteger();
		setOpener((cfg, dir, link) -> {
			opens.incrementAndGet();
			return openStore(cfg, dir, link);
		});

		assertTrue(server.getObjectDatabase().has(local));
		try (ObjectReader reader = server.newObjectReader()) {
			assertArrayEquals("local".getBytes(UTF_8),
					reader.open(local).getCachedBytes());
		}
		assertEquals(0, opens.get());
	}

	@Test
	public void fileWrittenAfterOpenIsPickedUp() throws Exception {
		RevBlob big = storeBlob(BIG);
		assertFalse(server.getObjectDatabase().has(big));

		writeS3Alternates(LINK);

		assertTrue(server.getObjectDatabase().has(big));
	}

	@Test
	public void onlyS3LinksAreAccepted() throws Exception {
		RevBlob big = storeBlob(BIG);
		writeS3Alternates("# comment", "",
				store.getObjectsDirectory().getAbsolutePath(),
				"file://" + store.getObjectsDirectory().getAbsolutePath(),
				"s3:///no-bucket");

		assertFalse(server.getObjectDatabase().has(big));
		assertTrue(opened.isEmpty());
	}

	@Test
	public void unavailableS3DatabaseIsSkipped() throws Exception {
		RevBlob big = storeBlob(BIG);
		writeS3Alternates(LINK);
		setOpener(S3Alternates.DEFAULT_OPENER);

		assertFalse(server.getObjectDatabase().has(big));
		try (ObjectReader reader = server.newObjectReader()) {
			assertFalse(reader.has(big));
			assertEquals(-1, ((ObjectDirectory) server.getObjectDatabase())
					.getObjectSize((WindowCursor) reader, big));
		}
	}

	@Test
	public void failedOpenIsRetried() throws Exception {
		RevBlob big = storeBlob(BIG);
		writeS3Alternates(LINK);
		AtomicInteger attempts = new AtomicInteger();
		setOpener((cfg, dir, link) -> {
			if (attempts.incrementAndGet() == 1) {
				throw new IOException("transient");
			}
			return openStore(cfg, dir, link);
		});

		assertFalse(server.getObjectDatabase().has(big));
		assertTrue(server.getObjectDatabase().has(big));
		assertEquals(2, attempts.get());
	}

	@Test
	public void fetchServesBlobFromS3() throws Exception {
		RevBlob big = storeBlob(BIG);
		RevBlob small = serverTr.blob("small");
		RevCommit commit = commitWith(big, small);
		writeS3Alternates(LINK);

		try (InMemoryRepository client = newClient();
				Transport tn = openTransport(client)) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertArrayEquals(BIG, client.open(big).getCachedBytes());
			assertTrue(client.getObjectDatabase().has(small));
		}
	}

	@Test
	public void partialCloneOmitsS3BlobAndLazyFetchServesIt()
			throws Exception {
		RevBlob big = storeBlob(BIG);
		RevBlob small = serverTr.blob("small");
		RevCommit commit = commitWith(big, small);
		writeS3Alternates(LINK);
		server.getConfig().setBoolean("uploadpack", null, "allowfilter",
				true);

		try (InMemoryRepository client = newClient()) {
			try (Transport tn = openTransport(client)) {
				tn.setFilterSpec(FilterSpec.fromFilterLine("blob:limit=10"));
				tn.fetch(NullProgressMonitor.INSTANCE, Collections
						.singletonList(new RefSpec(commit.name())));
			}
			assertFalse(client.getObjectDatabase().has(big));
			assertTrue(client.getObjectDatabase().has(small));

			try (Transport tn = openTransport(client)) {
				tn.setFilterSpec(FilterSpec.fromFilterLine("blob:none"));
				tn.fetch(NullProgressMonitor.INSTANCE,
						Arrays.asList(new RefSpec(commit.name()),
								new RefSpec(big.name())));
			}
			assertArrayEquals(BIG, client.open(big).getCachedBytes());
		}
	}

	private ObjectDirectory openStore(org.eclipse.jgit.lib.Config cfg,
			File objectsDir, URI link) throws IOException {
		opened.add(link);
		return new ObjectDirectory(cfg, store.getObjectsDirectory(), null,
				FS.DETECTED, null);
	}

	private void setOpener(LargeObjectAlternates.Opener opener) {
		for (LargeObjectAlternates alt : ((ObjectDirectory) server
				.getObjectDatabase()).getLargeObjectAlternates()) {
			if (alt instanceof S3Alternates) {
				alt.setOpener(opener);
			}
		}
	}

	private RevBlob storeBlob(byte[] content) throws Exception {
		try (TestRepository<FileRepository> storeTr = new TestRepository<>(
				store)) {
			return storeTr.blob(content);
		}
	}

	private RevCommit commitWith(RevBlob big, RevBlob small)
			throws Exception {
		RevCommit commit = serverTr.commit(serverTr.tree(
				serverTr.file("big.bin", big), serverTr.file("small", small)));
		serverTr.update("master", commit);
		return commit;
	}

	private void writeS3Alternates(String... lines) throws IOException {
		File file = new File(server.getObjectsDirectory(),
				S3Alternates.INFO_S3_ALTERNATES);
		Files.write(file.toPath(),
				(String.join("\n", lines) + "\n").getBytes(UTF_8));
	}

	private static InMemoryRepository newClient() {
		return new InMemoryRepository(new DfsRepositoryDescription("client"));
	}

	private Transport openTransport(Repository client) throws Exception {
		if (testProtocol == null) {
			testProtocol = new TestProtocol<>(
					(Object req, Repository db) -> new UploadPack(db), null);
		}
		URIish uri = testProtocol.register(new Object(), server);
		return testProtocol.open(uri, client, "server");
	}
}
