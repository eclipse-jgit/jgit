/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static java.lang.Integer.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.CancelledException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.EmptyProgressMonitor;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Ignore;
import org.junit.Test;

public class GcConcurrentTest extends GcTestCase {
	@Test
	public void concurrentRepack() throws Exception {
		final CyclicBarrier syncPoint = new CyclicBarrier(2);

		class DoRepack extends EmptyProgressMonitor implements
				Callable<Integer> {

			@Override
			public void beginTask(String title, int totalWork) {
				if (title.equals(JGitText.get().writingObjects)) {
					try {
						syncPoint.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (BrokenBarrierException ignored) {
						//
					}
				}
			}

			/** @return 0 for success, 1 in case of error when writing pack */
			@Override
			public Integer call() throws Exception {
				try {
					gc.setProgressMonitor(this);
					gc.repack();
					return valueOf(0);
				} catch (IOException e) {
					// leave the syncPoint in broken state so any awaiting
					// threads and any threads that call await in the future get
					// the BrokenBarrierException
					Thread.currentThread().interrupt();
					try {
						syncPoint.await();
					} catch (InterruptedException ignored) {
						//
					}
					return valueOf(1);
				}
			}
		}

		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			DoRepack repack1 = new DoRepack();
			DoRepack repack2 = new DoRepack();
			Future<Integer> result1 = pool.submit(repack1);
			Future<Integer> result2 = pool.submit(repack2);
			assertEquals(0, result1.get().intValue() + result2.get().intValue());
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}

	@Test
	public void repackAndGetStats() throws Exception {
		TestRepository<FileRepository>.BranchBuilder test = tr.branch("test");
		test.commit().add("a", "a").create();
		GC gc1 = new GC(tr.getRepository());
		gc1.setPackExpireAgeMillis(0);
		gc1.gc().get();
		test.commit().add("b", "b").create();

		// Create a new Repository instance and trigger a gc
		// from that instance. Reusing the existing repo instance
		// tr.getRepository() would not show the problem.
		FileRepository r2 = new FileRepository(
				tr.getRepository().getDirectory());
		GC gc2 = new GC(r2);
		gc2.setPackExpireAgeMillis(0);
		gc2.gc().get();

		new GC(tr.getRepository()).getStatistics();
	}

	@Test
	public void repackAndUploadPack() throws Exception {
		TestRepository<FileRepository>.BranchBuilder test = tr.branch("test");
		// RevCommit a = test.commit().add("a", "a").create();
		test.commit().add("a", "a").create();

		GC gc1 = new GC(tr.getRepository());
		gc1.setPackExpireAgeMillis(0);
		gc1.gc().get();

		RevCommit b = test.commit().add("b", "b").create();

		FileRepository r2 = new FileRepository(
				tr.getRepository().getDirectory());
		GC gc2 = new GC(r2);
		gc2.setPackExpireAgeMillis(0);
		gc2.gc().get();

		// Simulate parts of an UploadPack. This is the situation on
		// server side (e.g. gerrit) when clients are
		// cloning/fetching while the server side repo's
		// are gc'ed by an external process (e.g. scheduled
		// native git gc)
		try (PackWriter pw = new PackWriter(tr.getRepository())) {
			pw.setUseBitmaps(true);
			pw.preparePack(NullProgressMonitor.INSTANCE, Sets.of(b),
					Collections.<ObjectId> emptySet());
			new GC(tr.getRepository()).getStatistics();
		}
	}

	Pack getSinglePack(FileRepository r) {
		Collection<Pack> packs = r.getObjectDatabase().getPacks();
		assertEquals(1, packs.size());
		return packs.iterator().next();
	}

	@Test
	public void repackAndCheckBitmapUsage() throws Exception {
		// create a test repository with one commit and pack all objects. After
		// packing create loose objects to trigger creation of a new packfile on
		// the next gc
		TestRepository<FileRepository>.BranchBuilder test = tr.branch("test");
		test.commit().add("a", "a").create();
		FileRepository repository = tr.getRepository();
		GC gc1 = new GC(repository);
		gc1.setPackExpireAgeMillis(0);
		gc1.gc().get();
		String oldPackName = getSinglePack(repository).getPackName();
		RevCommit b = test.commit().add("b", "b").create();

		// start the garbage collection on a new repository instance,
		FileRepository repository2 = new FileRepository(repository.getDirectory());
		GC gc2 = new GC(repository2);
		gc2.setPackExpireAgeMillis(0);
		gc2.gc().get();
		String newPackName = getSinglePack(repository2).getPackName();
		// make sure gc() has caused creation of a new packfile
		assertNotEquals(oldPackName, newPackName);

		// When asking again for the set of packfiles the new updated data
		// will be returned because of the rescan of the pack directory.
		assertEquals(getSinglePack(repository).getPackName(), newPackName);

		// When accessing object content the new packfile refreshed from
		// the rescan triggered from the list of packs.
		repository.getObjectDatabase().open(b).getSize();
		assertEquals(getSinglePack(repository).getPackName(), newPackName);
		assertNotNull(getSinglePack(repository).getBitmapIndex());
	}

	@Ignore
	@Test
	public void testInterruptGc() throws Exception {
		FileBasedConfig c = repo.getConfig();
		c.setInt(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOPACKLIMIT, 1);
		c.save();
		SampleDataRepositoryTestCase.copyCGitTestPacks(repo);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		final CountDownLatch latch = new CountDownLatch(1);
		Future<Collection<Pack>> result = executor.submit(() -> {
			long start = System.currentTimeMillis();
			System.out.println("starting gc");
			latch.countDown();
			Collection<Pack> r = gc.gc().get();
			System.out.println(
					"gc took " + (System.currentTimeMillis() - start) + " ms");
			return r;
		});
		try {
			latch.await();
			Thread.sleep(5);
			executor.shutdownNow();
			result.get();
			fail("thread wasn't interrupted");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof CancelledException) {
				assertEquals(JGitText.get().operationCanceled,
						cause.getMessage());
			} else if (cause instanceof ClosedByInterruptException) {
				// thread was interrupted
			} else {
				fail("unexpected exception " + e);
			}
		}
	}
}
