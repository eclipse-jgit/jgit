/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.junit.Test;

public class UploadTest extends LfsServerTest {

	@Test
	public void testUpload() throws Exception {
		String TEXT = "test";
		AnyLongObjectId id = putContent(TEXT);
		assertTrue("expect object " + id.name() + " to exist",
				repository.getSize(id) >= 0);
		assertEquals("expected object length " + TEXT.length(), TEXT.length(),
				repository.getSize(id));
	}

	@Test
	public void testCorruptUpload() throws Exception {
		String TEXT = "test";
		AnyLongObjectId id = LongObjectIdTestUtils.hash("wrongHash");
		try {
			putContent(id, TEXT);
			fail("expected RuntimeException(\"Status 400\")");
		} catch (RuntimeException e) {
			assertEquals("Status: 400. Bad Request", e.getMessage());
		}
		assertFalse("expect object " + id.name() + " not to exist",
				repository.getSize(id) >= 0);
	}

	@SuppressWarnings("boxing")
	@Test
	public void testLargeFileUpload() throws Exception {
		Path f = Paths.get(getTempDirectory().toString(), "largeRandomFile");
		createPseudoRandomContentFile(f, 5 * MiB);
		long start = System.nanoTime();
		LongObjectId id = putContent(f);
		System.out.println(
				MessageFormat.format("uploaded 10 MiB random data in {0}ms",
						(System.nanoTime() - start) / 1e6));
		assertTrue("expect object " + id.name() + " to exist",
				repository.getSize(id) >= 0);
		assertEquals("expected object length " + Files.size(f), Files.size(f),
				repository.getSize(id));
	}

	@Test
	public void testParallelUploads() throws Exception {
		int count = 10;
		List<Path> paths = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			Path f = Paths.get(getTempDirectory().toString(),
					"largeRandomFile_" + i);
			createPseudoRandomContentFile(f, 1 * MiB);
			paths.add(f);
		}

		final CyclicBarrier barrier = new CyclicBarrier(count);

		ExecutorService e = Executors.newFixedThreadPool(count);
		try {
			for (Path p : paths) {
				Future<Object> result = e.submit(() -> {
					barrier.await();
					putContent(p);
					return null;
				});
				assertNotNull(result);
			}
		} finally {
			e.shutdown();
			e.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}
}
