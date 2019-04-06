/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.lfs.server.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
				e.submit(() -> {
                                    barrier.await();
                                    putContent(p);
                                    return null;
                                });
			}
		} finally {
			e.shutdown();
			e.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		}
	}
}
