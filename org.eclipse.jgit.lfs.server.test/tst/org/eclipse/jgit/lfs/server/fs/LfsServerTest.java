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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.SecureRandom;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.http.AppServer;
import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;

public abstract class LfsServerTest {

	private static final long timeout = /* 10 sec */ 10 * 1000;

	protected static final int MiB = 1024 * 1024;

	/** In-memory application server; subclass must start. */
	protected AppServer server;

	private Path tmp;

	private Path dir;

	protected FileLfsRepository repository;

	protected FileLfsServlet servlet;

	public LfsServerTest() {
		super();
	}

	public Path getTempDirectory() {
		return tmp;
	}

	public Path getDir() {
		return dir;
	}

	@Before
	public void setup() throws Exception {
		SystemReader.setInstance(new MockSystemReader());
		tmp = Files.createTempDirectory("jgit_test_");

		// measure timer resolution before the test to avoid time critical tests
		// are affected by time needed for measurement
		FS.getFileStoreAttributes(tmp.getParent());

		server = new AppServer();
		ServletContextHandler app = server.addContext("/lfs");
		dir = Paths.get(tmp.toString(), "lfs");
		this.repository = new FileLfsRepository(null, dir);
		servlet = new FileLfsServlet(repository, timeout);
		app.addServlet(new ServletHolder(servlet), "/objects/*");

		LfsProtocolServlet protocol = new LfsProtocolServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected LargeFileRepository getLargeFileRepository(
					LfsRequest request, String path, String auth)
					throws LfsException {
				return repository;
			}
		};
		app.addServlet(new ServletHolder(protocol), "/objects/batch");

		server.setUp();
		this.repository.setUrl(server.getURI() + "/lfs/objects/");
	}

	@After
	public void tearDown() throws Exception {
		server.tearDown();
		FileUtils.delete(tmp.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	protected AnyLongObjectId putContent(String s)
			throws IOException, ClientProtocolException {
		AnyLongObjectId id = LongObjectIdTestUtils.hash(s);
		return putContent(id, s);
	}

	protected AnyLongObjectId putContent(AnyLongObjectId id, String s)
			throws ClientProtocolException, IOException {
		try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
			HttpEntity entity = new StringEntity(s,
					ContentType.APPLICATION_OCTET_STREAM);
			String hexId = id.name();
			HttpPut request = new HttpPut(
					server.getURI() + "/lfs/objects/" + hexId);
			request.setEntity(entity);
			try (CloseableHttpResponse response = client.execute(request)) {
				StatusLine statusLine = response.getStatusLine();
				int status = statusLine.getStatusCode();
				if (status >= 400) {
					throw new RuntimeException("Status: " + status + ". "
							+ statusLine.getReasonPhrase());
				}
			}
			return id;
		}
	}

	protected LongObjectId putContent(Path f)
			throws FileNotFoundException, IOException {
		try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
			LongObjectId id1, id2;
			String hexId1, hexId2;
			try (DigestInputStream in = new DigestInputStream(
					new BufferedInputStream(Files.newInputStream(f)),
					Constants.newMessageDigest())) {
				InputStreamEntity entity = new InputStreamEntity(in,
						Files.size(f), ContentType.APPLICATION_OCTET_STREAM);
				id1 = LongObjectIdTestUtils.hash(f);
				hexId1 = id1.name();
				HttpPut request = new HttpPut(
						server.getURI() + "/lfs/objects/" + hexId1);
				request.setEntity(entity);
				HttpResponse response = client.execute(request);
				checkResponseStatus(response);
				id2 = LongObjectId.fromRaw(in.getMessageDigest().digest());
				hexId2 = id2.name();
				assertEquals(hexId1, hexId2);
			}
			return id1;
		}
	}

	private void checkResponseStatus(HttpResponse response) {
		StatusLine statusLine = response.getStatusLine();
		int status = statusLine.getStatusCode();
		if (statusLine.getStatusCode() >= 400) {
			String error;
			try {
				ByteBuffer buf = IO.readWholeStream(new BufferedInputStream(
						response.getEntity().getContent()), 1024);
				if (buf.hasArray()) {
					error = new String(buf.array(),
							buf.arrayOffset() + buf.position(), buf.remaining(),
							UTF_8);
				} else {
					final byte[] b = new byte[buf.remaining()];
					buf.duplicate().get(b);
					error = new String(b, UTF_8);
				}
			} catch (IOException e) {
				error = statusLine.getReasonPhrase();
			}
			throw new RuntimeException("Status: " + status + " " + error);
		}
		assertEquals(200, status);
	}

	protected long getContent(AnyLongObjectId id, Path f) throws IOException {
		String hexId = id.name();
		return getContent(hexId, f);
	}

	protected long getContent(String hexId, Path f) throws IOException {
		try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
			HttpGet request = new HttpGet(
					server.getURI() + "/lfs/objects/" + hexId);
			HttpResponse response = client.execute(request);
			checkResponseStatus(response);
			HttpEntity entity = response.getEntity();
			long pos = 0;
			try (InputStream in = entity.getContent();
					ReadableByteChannel inChannel = Channels.newChannel(in);
					FileChannel outChannel = FileChannel.open(f,
							StandardOpenOption.CREATE_NEW,
							StandardOpenOption.WRITE)) {
				long transferred;
				do {
					transferred = outChannel.transferFrom(inChannel, pos, MiB);
					pos += transferred;
				} while (transferred > 0);
			}
			return pos;
		}
	}

	/**
	 * Creates a file with random content, repeatedly writing a random string of
	 * 4k length to the file until the file has at least the specified length.
	 *
	 * @param f
	 *            file to fill
	 * @param size
	 *            size of the file to generate
	 * @return length of the generated file in bytes
	 * @throws IOException
	 */
	protected long createPseudoRandomContentFile(Path f, long size)
			throws IOException {
		SecureRandom rnd = new SecureRandom();
		byte[] buf = new byte[4096];
		rnd.nextBytes(buf);
		ByteBuffer bytebuf = ByteBuffer.wrap(buf);
		try (FileChannel outChannel = FileChannel.open(f,
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			long len = 0;
			do {
				len += outChannel.write(bytebuf);
				if (bytebuf.position() == 4096) {
					bytebuf.rewind();
				}
			} while (len < size);
		}
		return Files.size(f);
	}
}
