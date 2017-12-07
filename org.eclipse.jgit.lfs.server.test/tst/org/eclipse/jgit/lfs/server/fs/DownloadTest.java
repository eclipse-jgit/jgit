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

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

import org.apache.http.client.ClientProtocolException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class DownloadTest extends LfsServerTest {

	@Test
	public void testDownload() throws Exception {
		String TEXT = "test";
		AnyLongObjectId id = putContent(TEXT);
		Path f = Paths.get(getTempDirectory().toString(), "download");
		long len = getContent(id, f);
		assertEquals(TEXT.length(), len);
		FileUtils.delete(f.toFile(), FileUtils.RETRY);
	}

	@Test
	public void testDownloadInvalidPathInfo()
			throws ClientProtocolException, IOException {
		String TEXT = "test";
		String id = putContent(TEXT).name().substring(0, 60);
		Path f = Paths.get(getTempDirectory().toString(), "download");
		try {
			getContent(id, f);
			fail("expected RuntimeException");
		} catch (RuntimeException e) {
			String error = String.format(
					"Invalid pathInfo: '/%s' does not match '/{SHA-256}'", id);
			assertEquals(formatErrorMessage(SC_UNPROCESSABLE_ENTITY, error),
					e.getMessage());
		}
	}

	@Test
	public void testDownloadInvalidId()
			throws ClientProtocolException, IOException {
		String TEXT = "test";
		String id = putContent(TEXT).name().replace('f', 'z');
		Path f = Paths.get(getTempDirectory().toString(), "download");
		try {
			getContent(id, f);
			fail("expected RuntimeException");
		} catch (RuntimeException e) {
			String error = String.format("Invalid id: %s", id);
			assertEquals(formatErrorMessage(SC_UNPROCESSABLE_ENTITY, error),
					e.getMessage());
		}
	}

	@Test
	public void testDownloadNotFound()
			throws ClientProtocolException, IOException {
		String TEXT = "test";
		AnyLongObjectId id = LongObjectIdTestUtils.hash(TEXT);
		Path f = Paths.get(getTempDirectory().toString(), "download");
		try {
			getContent(id, f);
			fail("expected RuntimeException");
		} catch (RuntimeException e) {
			String error = String.format("Object '%s' not found", id.getName());
			assertEquals(formatErrorMessage(SC_NOT_FOUND, error),
					e.getMessage());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	public void testLargeFileDownload() throws Exception {
		Path f = Paths.get(getTempDirectory().toString(), "largeRandomFile");
		long expectedLen = createPseudoRandomContentFile(f, 5 * MiB);
		AnyLongObjectId id = putContent(f);
		Path f2 = Paths.get(getTempDirectory().toString(), "download");
		long start = System.nanoTime();
		long len = getContent(id, f2);
		System.out.println(
				MessageFormat.format("downloaded 10 MiB random data in {0}ms",
						(System.nanoTime() - start) / 1e6));
		assertEquals(expectedLen, len);
		FileUtils.delete(f.toFile(), FileUtils.RETRY);

	}

	@SuppressWarnings("boxing")
	private String formatErrorMessage(int status, String message) {
		return String.format("Status: %d {\"message\":\"%s\"}", status,
				message);
	}
}
