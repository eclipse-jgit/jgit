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

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;

import org.apache.http.client.ClientProtocolException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DownloadTest extends LfsServerTest {

	@Rule
	public ExpectedException exception = ExpectedException.none();

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
		String error = String.format(
				"Invalid pathInfo: '/%s' does not match '/{SHA-256}'", id);
		exception.expect(RuntimeException.class);
		exception.expectMessage(
				formatErrorMessage(SC_UNPROCESSABLE_ENTITY, error));
		getContent(id, f);
	}

	@Test
	public void testDownloadInvalidId()
			throws ClientProtocolException, IOException {
		String TEXT = "test";
		String id = putContent(TEXT).name().replace('f', 'z');
		Path f = Paths.get(getTempDirectory().toString(), "download");
		String error = String.format("Invalid id: %s", id);
		exception.expect(RuntimeException.class);
		exception.expectMessage(
				formatErrorMessage(SC_UNPROCESSABLE_ENTITY, error));
		getContent(id, f);
	}

	@Test
	public void testDownloadNotFound()
			throws ClientProtocolException, IOException {
		String TEXT = "test";
		AnyLongObjectId id = LongObjectIdTestUtils.hash(TEXT);
		Path f = Paths.get(getTempDirectory().toString(), "download");
		String error = String.format("Object '%s' not found", id.getName());
		exception.expect(RuntimeException.class);
		exception.expectMessage(formatErrorMessage(SC_NOT_FOUND, error));
		getContent(id, f);
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
