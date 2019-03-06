/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.junit.Test;

public class RepositoryCacheTest extends RepositoryTestCase {
	@Test
	public void testNonBareFileKey() throws IOException {
		File gitdir = db.getDirectory();
		File parent = gitdir.getParentFile();
		File other = new File(parent, "notagit");
		assertEqualsFile(gitdir, FileKey.exact(gitdir, db.getFS()).getFile());
		assertEqualsFile(parent, FileKey.exact(parent, db.getFS()).getFile());
		assertEqualsFile(other, FileKey.exact(other, db.getFS()).getFile());

		assertEqualsFile(gitdir, FileKey.lenient(gitdir, db.getFS()).getFile());
		assertEqualsFile(gitdir, FileKey.lenient(parent, db.getFS()).getFile());
		assertEqualsFile(other, FileKey.lenient(other, db.getFS()).getFile());
	}

	@Test
	public void testBareFileKey() throws IOException {
		Repository bare = createBareRepository();
		File gitdir = bare.getDirectory();
		File parent = gitdir.getParentFile();
		String name = gitdir.getName();
		assertTrue(name.endsWith(".git"));
		name = name.substring(0, name.length() - 4);

		assertEqualsFile(gitdir, FileKey.exact(gitdir, db.getFS()).getFile());

		assertEqualsFile(gitdir, FileKey.lenient(gitdir, db.getFS()).getFile());
		assertEqualsFile(gitdir,
				FileKey.lenient(new File(parent, name), db.getFS()).getFile());
	}

	@Test
	public void testFileKeyOpenExisting() throws IOException {
		try (Repository r = new FileKey(db.getDirectory(), db.getFS())
				.open(true)) {
			assertNotNull(r);
			assertEqualsFile(db.getDirectory(), r.getDirectory());
		}

		try (Repository r = new FileKey(db.getDirectory(), db.getFS())
				.open(false)) {
			assertNotNull(r);
			assertEqualsFile(db.getDirectory(), r.getDirectory());
		}
	}

	@Test
	public void testFileKeyOpenNew() throws IOException {
		File gitdir;
		try (Repository n = createRepository(true)) {
			gitdir = n.getDirectory();
		}
		recursiveDelete(gitdir);
		assertFalse(gitdir.exists());

		try {
			new FileKey(gitdir, db.getFS()).open(true);
			fail("incorrectly opened a non existant repository");
		} catch (RepositoryNotFoundException e) {
			assertEquals("repository not found: " + gitdir.getCanonicalPath(),
					e.getMessage());
		}

		final Repository o = new FileKey(gitdir, db.getFS()).open(false);
		assertNotNull(o);
		assertEqualsFile(gitdir, o.getDirectory());
		assertFalse(gitdir.exists());
	}

	@Test
	public void testCacheRegisterOpen() throws Exception {
		final File dir = db.getDirectory();
		RepositoryCache.register(db);
		assertSame(db, RepositoryCache.open(FileKey.exact(dir, db.getFS())));

		assertEquals(".git", dir.getName());
		final File parent = dir.getParentFile();
		assertSame(db, RepositoryCache.open(FileKey.lenient(parent, db.getFS())));
	}

	@Test
	public void testCacheOpen() throws Exception {
		final FileKey loc = FileKey.exact(db.getDirectory(), db.getFS());
		@SuppressWarnings("resource") // We are testing the close() method
		final Repository d2 = RepositoryCache.open(loc);
		assertNotSame(db, d2);
		assertSame(d2, RepositoryCache.open(FileKey.exact(loc.getFile(), db.getFS())));
		d2.close();
		d2.close();
	}

	@Test
	public void testGetRegisteredWhenEmpty() {
		assertEquals(0, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testGetRegistered() {
		RepositoryCache.register(db);

		assertThat(RepositoryCache.getRegisteredKeys(),
				hasItem(FileKey.exact(db.getDirectory(), db.getFS())));
		assertEquals(1, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testUnregister() {
		RepositoryCache.register(db);
		RepositoryCache
				.unregister(FileKey.exact(db.getDirectory(), db.getFS()));

		assertEquals(0, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testRepositoryUsageCount() throws Exception {
		FileKey loc = FileKey.exact(db.getDirectory(), db.getFS());
		@SuppressWarnings("resource") // We are testing the close() method
		Repository d2 = RepositoryCache.open(loc);
		assertEquals(1, d2.useCnt.get());
		RepositoryCache.open(FileKey.exact(loc.getFile(), db.getFS()));
		assertEquals(2, d2.useCnt.get());
		d2.close();
		assertEquals(1, d2.useCnt.get());
		d2.close();
		assertEquals(0, d2.useCnt.get());
	}

	@Test
	public void testRepositoryUsageCountWithRegisteredRepository()
			throws IOException {
		@SuppressWarnings({"resource", "deprecation"}) // We are testing the close() method
		Repository repo = createRepository(false, false);
		assertEquals(1, repo.useCnt.get());
		RepositoryCache.register(repo);
		assertEquals(1, repo.useCnt.get());
		repo.close();
		assertEquals(0, repo.useCnt.get());
	}

	@Test
	public void testRepositoryNotUnregisteringWhenClosing() throws Exception {
		FileKey loc = FileKey.exact(db.getDirectory(), db.getFS());
		@SuppressWarnings("resource") // We are testing the close() method
		Repository d2 = RepositoryCache.open(loc);
		assertEquals(1, d2.useCnt.get());
		assertThat(RepositoryCache.getRegisteredKeys(),
				hasItem(FileKey.exact(db.getDirectory(), db.getFS())));
		assertEquals(1, RepositoryCache.getRegisteredKeys().size());
		d2.close();
		assertEquals(0, d2.useCnt.get());
		assertEquals(1, RepositoryCache.getRegisteredKeys().size());
		assertTrue(RepositoryCache.isCached(d2));
	}

	@Test
	public void testRepositoryUnregisteringWhenExpiredAndUsageCountNegative()
			throws Exception {
		@SuppressWarnings("resource") // We are testing the close() method
		Repository repoA = createBareRepository();
		RepositoryCache.register(repoA);

		assertEquals(1, RepositoryCache.getRegisteredKeys().size());
		assertTrue(RepositoryCache.isCached(repoA));

		// close the repo twice to make usage count negative
		repoA.close();
		repoA.close();
		// fake that repoA was closed more than 1 hour ago (default expiration
		// time)
		repoA.closedAt.set(System.currentTimeMillis() - 65 * 60 * 1000);

		RepositoryCache.clearExpired();

		assertEquals(0, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testRepositoryUnregisteringWhenExpired() throws Exception {
		@SuppressWarnings({"resource", "deprecation"}) // We are testing the close() method
		Repository repoA = createRepository(true, false);
		@SuppressWarnings({"resource", "deprecation"}) // We are testing the close() method
		Repository repoB = createRepository(true, false);
		Repository repoC = createBareRepository();
		RepositoryCache.register(repoA);
		RepositoryCache.register(repoB);
		RepositoryCache.register(repoC);

		assertEquals(3, RepositoryCache.getRegisteredKeys().size());
		assertTrue(RepositoryCache.isCached(repoA));
		assertTrue(RepositoryCache.isCached(repoB));
		assertTrue(RepositoryCache.isCached(repoC));

		// fake that repoA was closed more than 1 hour ago (default expiration
		// time)
		repoA.close();
		repoA.closedAt.set(System.currentTimeMillis() - 65 * 60 * 1000);
		// close repoB but this one will not be expired
		repoB.close();

		assertEquals(3, RepositoryCache.getRegisteredKeys().size());
		assertTrue(RepositoryCache.isCached(repoA));
		assertTrue(RepositoryCache.isCached(repoB));
		assertTrue(RepositoryCache.isCached(repoC));

		RepositoryCache.clearExpired();

		assertEquals(2, RepositoryCache.getRegisteredKeys().size());
		assertFalse(RepositoryCache.isCached(repoA));
		assertTrue(RepositoryCache.isCached(repoB));
		assertTrue(RepositoryCache.isCached(repoC));
	}

	@Test
	public void testReconfigure() throws InterruptedException, IOException {
		@SuppressWarnings({"resource", "deprecation"}) // We are testing the close() method
		Repository repo = createRepository(false, false);
		RepositoryCache.register(repo);
		assertTrue(RepositoryCache.isCached(repo));
		repo.close();
		assertTrue(RepositoryCache.isCached(repo));

		// Actually, we would only need to validate that
		// WorkQueue.getExecutor().scheduleWithFixedDelay is called with proper
		// values but since we do not have a mock library, we test
		// reconfiguration from a black box perspective. I.e. reconfigure
		// expireAfter and cleanupDelay to 1 ms and wait until the Repository
		// is evicted to prove that reconfiguration worked.
		RepositoryCacheConfig config = new RepositoryCacheConfig();
		config.setExpireAfter(1);
		config.setCleanupDelay(1);
		config.install();

		// Instead of using a fixed waiting time, start with small and increase:
		// sleep 1, 2, 4, 8, 16, ..., 1024 ms
		// This wait will time out after 2048 ms
		for (int i = 0; i <= 10; i++) {
			Thread.sleep(1 << i);
			if (!RepositoryCache.isCached(repo)) {
				return;
			}
		}
		fail("Repository should have been evicted from cache");
	}
}
