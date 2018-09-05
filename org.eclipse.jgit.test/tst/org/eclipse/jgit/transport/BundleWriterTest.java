/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Mike Ralphson <mike@abacus.co.uk>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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
import org.junit.Test;

public class BundleWriterTest extends SampleDataRepositoryTestCase {

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
