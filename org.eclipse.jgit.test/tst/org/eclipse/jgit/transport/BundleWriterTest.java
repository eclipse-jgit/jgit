/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;

import org.eclipse.jgit.errors.MissingBundlePrerequisiteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BundleWriterTest extends SampleDataRepositoryTestCase {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testEmptyBundleFails() throws Exception {
		Repository newRepo = createBareRepository();
		thrown.expect(TransportException.class);
		fetchFromBundle(newRepo, new byte[0]);
	}

	@Test
	public void testNonBundleFails() throws Exception {
		Repository newRepo = createBareRepository();
		thrown.expect(TransportException.class);
		fetchFromBundle(newRepo, "Not a bundle file".getBytes(UTF_8));
	}

	@Test
	public void testGarbageBundleFails() throws Exception {
		Repository newRepo = createBareRepository();
		thrown.expect(TransportException.class);
		fetchFromBundle(newRepo,
				(TransportBundle.V2_BUNDLE_SIGNATURE + '\n' + "Garbage")
						.getBytes(UTF_8));
	}

	@Test
	public void testWriteSingleRef() throws Exception {
		// Create a tiny bundle, (well one of) the first commits only
		final byte[] bundle = makeBundle("refs/heads/firstcommit",
				"42e4e7c5e507e113ebbb7801b16b52cf867b7ce1", null);

		// Then we clone a new repo from that bundle and do a simple test. This
		// makes sure we could read the bundle we created.
		Repository newRepo = createBareRepository();
		FetchResult fetchResult = fetchFromBundle(newRepo, bundle);
		Ref advertisedRef = fetchResult
				.getAdvertisedRef("refs/heads/firstcommit");

		// We expect first commit to appear by id
		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1", advertisedRef
				.getObjectId().name());
		// ..and by name as the bundle created a new ref
		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1", newRepo
				.resolve("refs/heads/firstcommit").name());
	}

	@Test
	public void testWriteHEAD() throws Exception {
		byte[] bundle = makeBundle("HEAD",
				"42e4e7c5e507e113ebbb7801b16b52cf867b7ce1", null);

		Repository newRepo = createBareRepository();
		FetchResult fetchResult = fetchFromBundle(newRepo, bundle);
		Ref advertisedRef = fetchResult.getAdvertisedRef("HEAD");

		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1", advertisedRef
				.getObjectId().name());
	}

	@Test
	public void testIncrementalBundle() throws Exception {
		byte[] bundle;

		// Create a small bundle, an early commit
		bundle = makeBundle("refs/heads/aa", db.resolve("a").name(), null);

		// Then we clone a new repo from that bundle and do a simple test. This
		// makes sure
		// we could read the bundle we created.
		Repository newRepo = createBareRepository();
		FetchResult fetchResult = fetchFromBundle(newRepo, bundle);
		Ref advertisedRef = fetchResult.getAdvertisedRef("refs/heads/aa");

		assertEquals(db.resolve("a").name(), advertisedRef.getObjectId().name());
		assertEquals(db.resolve("a").name(), newRepo.resolve("refs/heads/aa")
				.name());
		assertNull(newRepo.resolve("refs/heads/a"));

		// Next an incremental bundle
		try (RevWalk rw = new RevWalk(db)) {
			bundle = makeBundle("refs/heads/cc", db.resolve("c").name(),
					rw.parseCommit(db.resolve("a").toObjectId()));
			fetchResult = fetchFromBundle(newRepo, bundle);
			advertisedRef = fetchResult.getAdvertisedRef("refs/heads/cc");
			assertEquals(db.resolve("c").name(), advertisedRef.getObjectId().name());
			assertEquals(db.resolve("c").name(), newRepo.resolve("refs/heads/cc")
					.name());
			assertNull(newRepo.resolve("refs/heads/c"));
			assertNull(newRepo.resolve("refs/heads/a")); // still unknown

			try {
				// Check that we actually needed the first bundle
				Repository newRepo2 = createBareRepository();
				fetchResult = fetchFromBundle(newRepo2, bundle);
				fail("We should not be able to fetch from bundle with prerequisites that are not fulfilled");
			} catch (MissingBundlePrerequisiteException e) {
				assertTrue(e.getMessage()
						.indexOf(db.resolve("refs/heads/a").name()) >= 0);
			}
		}
	}

	@Test
	public void testAbortWrite() throws Exception {
		boolean caught = false;
		try {
			makeBundleWithCallback(
					"refs/heads/aa", db.resolve("a").name(), null, false);
		} catch (WriteAbortedException e) {
			caught = true;
		}
		assertTrue(caught);
	}

	@Test
	public void testCustomObjectReader() throws Exception {
		String refName = "refs/heads/blob";
		String data = "unflushed data";
		ObjectId id;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (Repository repo = new InMemoryRepository(
					new DfsRepositoryDescription("repo"));
				ObjectInserter ins = repo.newObjectInserter();
				ObjectReader or = ins.newReader()) {
			id = ins.insert(OBJ_BLOB, Constants.encode(data));
			BundleWriter bw = new BundleWriter(or);
			bw.include(refName, id);
			bw.writeBundle(NullProgressMonitor.INSTANCE, out);
			assertNull(repo.exactRef(refName));
			try {
				repo.open(id, OBJ_BLOB);
				fail("We should not be able to open the unflushed blob");
			} catch (MissingObjectException e) {
				// Expected.
			}
		}

		try (Repository repo = new InMemoryRepository(
					new DfsRepositoryDescription("copy"))) {
			fetchFromBundle(repo, out.toByteArray());
			Ref ref = repo.exactRef(refName);
			assertNotNull(ref);
			assertEquals(id, ref.getObjectId());
			assertEquals(data,
					new String(repo.open(id, OBJ_BLOB).getBytes(), UTF_8));
		}
	}

	private static FetchResult fetchFromBundle(final Repository newRepo,
			final byte[] bundle) throws URISyntaxException,
			NotSupportedException, TransportException {
		final URIish uri = new URIish("in-memory://");
		final ByteArrayInputStream in = new ByteArrayInputStream(bundle);
		final RefSpec rs = new RefSpec("refs/heads/*:refs/heads/*");
		final Set<RefSpec> refs = Collections.singleton(rs);
		try (TransportBundleStream transport = new TransportBundleStream(
				newRepo, uri, in)) {
			return transport.fetch(NullProgressMonitor.INSTANCE, refs);
		}
	}

	private byte[] makeBundle(final String name,
			final String anObjectToInclude, final RevCommit assume)
			throws FileNotFoundException, IOException {
		return makeBundleWithCallback(name, anObjectToInclude, assume, true);
	}

	private byte[] makeBundleWithCallback(final String name,
			final String anObjectToInclude, final RevCommit assume,
			boolean value)
			throws FileNotFoundException, IOException {
		final BundleWriter bw;

		bw = new BundleWriter(db);
		bw.setObjectCountCallback(new NaiveObjectCountCallback(value));
		bw.include(name, ObjectId.fromString(anObjectToInclude));
		if (assume != null)
			bw.assume(assume);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		bw.writeBundle(NullProgressMonitor.INSTANCE, out);
		return out.toByteArray();
	}

	private static class NaiveObjectCountCallback
			implements ObjectCountCallback {
		private final boolean value;

		NaiveObjectCountCallback(boolean value) {
			this.value = value;
		}

		@Override
		public void setObjectCount(long unused) throws WriteAbortedException {
			if (!value)
				throw new WriteAbortedException();
		}
	}

}
