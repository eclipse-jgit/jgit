/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.internal.storage.file;

import static java.lang.Integer.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.EmptyProgressMonitor;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
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
		gc1.gc();
		test.commit().add("b", "b").create();

		// Create a new Repository instance and trigger a gc
		// from that instance. Reusing the existing repo instance
		// tr.getRepository() would not show the problem.
		FileRepository r2 = new FileRepository(
				tr.getRepository().getDirectory());
		GC gc2 = new GC(r2);
		gc2.setPackExpireAgeMillis(0);
		gc2.gc();

		new GC(tr.getRepository()).getStatistics();
	}

	@Test
	public void repackAndUploadPack() throws Exception {
		TestRepository<FileRepository>.BranchBuilder test = tr.branch("test");
		// RevCommit a = test.commit().add("a", "a").create();
		test.commit().add("a", "a").create();

		GC gc1 = new GC(tr.getRepository());
		gc1.setPackExpireAgeMillis(0);
		gc1.gc();

		RevCommit b = test.commit().add("b", "b").create();

		FileRepository r2 = new FileRepository(
				tr.getRepository().getDirectory());
		GC gc2 = new GC(r2);
		gc2.setPackExpireAgeMillis(0);
		gc2.gc();

		// Simulate parts of an UploadPack. This is the situation on
		// server side (e.g. gerrit) when when clients are
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

	PackFile getSinglePack(FileRepository r) {
		Collection<PackFile> packs = r.getObjectDatabase().getPacks();
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
		gc1.gc();
		String oldPackName = getSinglePack(repository).getPackName();
		RevCommit b = test.commit().add("b", "b").create();

		// start the garbage collection on a new repository instance,
		FileRepository repository2 = new FileRepository(repository.getDirectory());
		GC gc2 = new GC(repository2);
		gc2.setPackExpireAgeMillis(0);
		gc2.gc();
		String newPackName = getSinglePack(repository2).getPackName();
		// make sure gc() has caused creation of a new packfile
		assertNotEquals(oldPackName, newPackName);

		// Even when asking again for the set of packfiles outdated data
		// will be returned. As long as the repository can work on cached data
		// it will do so and not detect that a new packfile exists.
		assertNotEquals(getSinglePack(repository).getPackName(), newPackName);

		// Only when accessing object content it is required to rescan the pack
		// directory and the new packfile will be detected.
		repository.getObjectDatabase().open(b).getSize();
		assertEquals(getSinglePack(repository).getPackName(), newPackName);
		assertNotNull(getSinglePack(repository).getBitmapIndex());
	}
}
