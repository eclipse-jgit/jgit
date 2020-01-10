/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class JGitFileSystemProviderConcurrentOperationsTest extends AbstractTestInfra {

	private Logger logger = LoggerFactory.getLogger(JGitFileSystemProviderConcurrentOperationsTest.class);

	@Test
	public void testConcurrentGitCreation() {

		int threadCount = 2;
		final CountDownLatch finished = new CountDownLatch(threadCount);
		List<Thread> threads = new ArrayList<>();

		for (int i = 0; i < threadCount; i++) {
			final int name = i;
			Runnable r = () -> {
				this.provider.createNewGitRepo(EMPTY_ENV, "git://parent/concurrent-test" + name);
				finished.countDown();
				logger.info("Countdown" + Thread.currentThread().getName());
			};
			Thread t = new Thread(r);
			threads.add(t);
			t.start();
		}

		wait(threads);
		assertEquals(0, finished.getCount());
	}

	@Test
	public void testConcurrentGitDeletion() throws IOException {

		String gitRepo = "git://parent/delete-test-repo";
		final URI newRepo = URI.create(gitRepo);
		JGitFileSystemProxy fs = (JGitFileSystemProxy) provider.newFileSystem(newRepo, EMPTY_ENV);

		int threadCount = 2;
		final CountDownLatch finished = new CountDownLatch(threadCount);
		List<Thread> threads = new ArrayList<>();

		for (int i = 0; i < threadCount; i++) {
			final int name = i;
			Runnable r = () -> {
				try {
					this.provider.deleteFS(fs.getRealJGitFileSystem());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				finished.countDown();
				logger.info("Countdown" + Thread.currentThread().getName());
			};
			Thread t = new Thread(r);
			threads.add(t);
			t.start();
		}

		wait(threads);
		assertEquals(0, finished.getCount());
	}

	private void wait(List<Thread> threads) {
		threads.forEach(thread -> {
			try {
				thread.join();
			} catch (InterruptedException e) {
				logger.error("Error waiting for threads", e);
			}
		});
	}
}
