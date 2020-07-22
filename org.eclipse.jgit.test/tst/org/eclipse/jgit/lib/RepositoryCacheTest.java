/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
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
		File gitdir = repository.getDirectory();
		File parent = gitdir.getParentFile();
		File other = new File(parent, "notagit");
		assertEqualsFile(gitdir, FileKey.exact(gitdir, repository.getFS()).getFile());
		assertEqualsFile(parent, FileKey.exact(parent, repository.getFS()).getFile());
		assertEqualsFile(other, FileKey.exact(other, repository.getFS()).getFile());

		assertEqualsFile(gitdir, FileKey.lenient(gitdir, repository.getFS()).getFile());
		assertEqualsFile(gitdir, FileKey.lenient(parent, repository.getFS()).getFile());
		assertEqualsFile(other, FileKey.lenient(other, repository.getFS()).getFile());
	}

	@Test
	public void testBareFileKey() throws IOException {
		Repository bare = createBareRepository();
		File gitdir = bare.getDirectory();
		File parent = gitdir.getParentFile();
		String name = gitdir.getName();
		assertTrue(name.endsWith(".git"));
		name = name.substring(0, name.length() - 4);

		assertEqualsFile(gitdir, FileKey.exact(gitdir, repository.getFS()).getFile());

		assertEqualsFile(gitdir, FileKey.lenient(gitdir, repository.getFS()).getFile());
		assertEqualsFile(gitdir,
				FileKey.lenient(new File(parent, name), repository.getFS()).getFile());
	}

	@Test
	public void testFileKeyOpenExisting() throws IOException {
		try (Repository r = new FileKey(repository.getDirectory(), repository.getFS())
				.open(true)) {
			assertNotNull(r);
			assertEqualsFile(repository.getDirectory(), r.getDirectory());
		}

		try (Repository r = new FileKey(repository.getDirectory(), repository.getFS())
				.open(false)) {
			assertNotNull(r);
			assertEqualsFile(repository.getDirectory(), r.getDirectory());
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
			new FileKey(gitdir, repository.getFS()).open(true);
			fail("incorrectly opened a non existant repository");
		} catch (RepositoryNotFoundException e) {
			assertEquals("repository not found: " + gitdir.getCanonicalPath(),
					e.getMessage());
		}

		final Repository o = new FileKey(gitdir, repository.getFS()).open(false);
		assertNotNull(o);
		assertEqualsFile(gitdir, o.getDirectory());
		assertFalse(gitdir.exists());
	}

	@Test
	public void testCacheRegisterOpen() throws Exception {
		final File dir = repository.getDirectory();
		RepositoryCache.register(repository);
		assertSame(repository, RepositoryCache.open(FileKey.exact(dir, repository.getFS())));

		assertEquals(".git", dir.getName());
		final File parent = dir.getParentFile();
		assertSame(repository, RepositoryCache.open(FileKey.lenient(parent, repository.getFS())));
	}

	@Test
	public void testCacheOpen() throws Exception {
		final FileKey loc = FileKey.exact(repository.getDirectory(), repository.getFS());
		@SuppressWarnings("resource") // We are testing the close() method
		final Repository d2 = RepositoryCache.open(loc);
		assertNotSame(repository, d2);
		assertSame(d2, RepositoryCache.open(FileKey.exact(loc.getFile(), repository.getFS())));
		d2.close();
		d2.close();
	}

	@Test
	public void testGetRegisteredWhenEmpty() {
		assertEquals(0, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testGetRegistered() {
		RepositoryCache.register(repository);

		assertThat(RepositoryCache.getRegisteredKeys(),
				hasItem(FileKey.exact(repository.getDirectory(), repository.getFS())));
		assertEquals(1, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testUnregister() {
		RepositoryCache.register(repository);
		RepositoryCache
				.unregister(FileKey.exact(repository.getDirectory(), repository.getFS()));

		assertEquals(0, RepositoryCache.getRegisteredKeys().size());
	}

	@Test
	public void testRepositoryUsageCount() throws Exception {
		FileKey loc = FileKey.exact(repository.getDirectory(), repository.getFS());
		@SuppressWarnings("resource") // We are testing the close() method
		Repository d2 = RepositoryCache.open(loc);
		assertEquals(1, d2.useCnt.get());
		RepositoryCache.open(FileKey.exact(loc.getFile(), repository.getFS()));
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
		FileKey loc = FileKey.exact(repository.getDirectory(), repository.getFS());
		@SuppressWarnings("resource") // We are testing the close() method
		Repository d2 = RepositoryCache.open(loc);
		assertEquals(1, d2.useCnt.get());
		assertThat(RepositoryCache.getRegisteredKeys(),
				hasItem(FileKey.exact(repository.getDirectory(), repository.getFS())));
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
