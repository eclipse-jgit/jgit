/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * In-process stand-in for S3, enough to test {@link S3ObjectStore}.
 * <p>
 * It serves path-style requests ({@code /<bucket>/<key>}) for {@code HEAD},
 * {@code GET}, {@code PUT} and {@code DELETE}, keeps objects in memory and
 * counts requests by method. Like S3, it rejects requests that are not
 * signed with AWS Signature Version 4 and uploads whose
 * {@code x-amz-checksum-sha1} does not match their body. It does not verify
 * signatures.
 */
class FakeS3Server implements AutoCloseable {

	private final HttpServer server;

	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

	private final AtomicInteger failures = new AtomicInteger();

	private final AtomicInteger finished = new AtomicInteger();

	private volatile boolean denyAll;

	private volatile Headers lastRequestHeaders;

	FakeS3Server() throws IOException {
		server = HttpServer.create(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", this::handle); //$NON-NLS-1$
		server.start();
	}

	/** @return base URL to pass as the store's endpoint */
	URL endpoint() throws MalformedURLException {
		return URI.create("http://" + server.getAddress().getHostString() //$NON-NLS-1$
				+ ':' + server.getAddress().getPort() + '/').toURL();
	}

	/**
	 * @param bucket
	 *            bucket name
	 * @param key
	 *            object key
	 * @return stored bytes of the object, or {@code null}
	 */
	byte[] object(String bucket, String key) {
		return objects.get('/' + bucket + '/' + key);
	}

	/** @return number of stored objects */
	int objectCount() {
		return objects.size();
	}

	/**
	 * @param method
	 *            HTTP method
	 * @return number of requests received with that method
	 */
	int requests(String method) {
		AtomicInteger n = requests.get(method);
		return n != null ? n.get() : 0;
	}

	/**
	 * @param n
	 *            number of following requests to answer with HTTP 500
	 */
	void failNext(int n) {
		failures.set(n);
	}

	/**
	 * @param deny
	 *            whether to answer every request with HTTP 403
	 */
	void denyAll(boolean deny) {
		denyAll = deny;
	}

	/**
	 * Wait until the server has finished handling a number of requests.
	 * <p>
	 * A client that fails an upload part-way returns before the server has
	 * finished reading it, so tests wait here before checking what was
	 * stored.
	 *
	 * @param count
	 *            number of requests, counted since the server started
	 * @throws InterruptedException
	 *             interrupted while waiting
	 */
	void awaitFinished(int count) throws InterruptedException {
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (finished.get() < count) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("server handled only "
						+ finished.get() + " of " + count + " requests");
			}
			Thread.sleep(10);
		}
	}

	/** @return headers of the most recent request */
	Headers lastRequestHeaders() {
		return lastRequestHeaders;
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void handle(HttpExchange ex) throws IOException {
		try {
			handleRequest(ex);
		} finally {
			finished.incrementAndGet();
		}
	}

	private void handleRequest(HttpExchange ex) throws IOException {
		try (ex) {
			String method = ex.getRequestMethod();
			requests.computeIfAbsent(method, m -> new AtomicInteger())
					.incrementAndGet();
			lastRequestHeaders = ex.getRequestHeaders();
			String path = ex.getRequestURI().getPath();

			if (failures.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
				drain(ex);
				error(ex, 500, "InternalError"); //$NON-NLS-1$
				return;
			}
			if (denyAll || !signed(ex.getRequestHeaders())) {
				drain(ex);
				error(ex, 403, "AccessDenied"); //$NON-NLS-1$
				return;
			}
			switch (method) {
			case "HEAD": //$NON-NLS-1$
				head(ex, path);
				break;
			case "GET": //$NON-NLS-1$
				get(ex, path);
				break;
			case "PUT": //$NON-NLS-1$
				put(ex, path);
				break;
			case "DELETE": //$NON-NLS-1$
				objects.remove(path);
				ex.sendResponseHeaders(204, -1);
				break;
			default:
				error(ex, 405, "MethodNotAllowed"); //$NON-NLS-1$
			}
		}
	}

	private static boolean signed(Headers h) {
		String auth = h.getFirst("Authorization"); //$NON-NLS-1$
		return auth != null && auth.startsWith("AWS4-HMAC-SHA256 Credential=") //$NON-NLS-1$
				&& auth.contains("SignedHeaders=") //$NON-NLS-1$
				&& auth.contains("Signature=") //$NON-NLS-1$
				&& h.getFirst("x-amz-date") != null //$NON-NLS-1$
				&& h.getFirst("x-amz-content-sha256") != null; //$NON-NLS-1$
	}

	private void head(HttpExchange ex, String path) throws IOException {
		byte[] data = objects.get(path);
		if (data == null) {
			ex.sendResponseHeaders(404, -1);
			return;
		}
		ex.getResponseHeaders().set("Content-length", //$NON-NLS-1$
				Integer.toString(data.length));
		ex.sendResponseHeaders(200, -1);
	}

	private void get(HttpExchange ex, String path) throws IOException {
		byte[] data = objects.get(path);
		if (data == null) {
			error(ex, 404, "NoSuchKey"); //$NON-NLS-1$
			return;
		}
		ex.sendResponseHeaders(200, data.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(data);
		}
	}

	private void put(HttpExchange ex, String path) throws IOException {
		byte[] data;
		try (InputStream in = ex.getRequestBody()) {
			data = in.readAllBytes();
		}
		String declared = ex.getRequestHeaders().getFirst("Content-length"); //$NON-NLS-1$
		if (declared == null || Long.parseLong(declared) != data.length) {
			// The client gave up before sending the whole body.
			error(ex, 400, "IncompleteBody"); //$NON-NLS-1$
			return;
		}
		String checksum = ex.getRequestHeaders()
				.getFirst("x-amz-checksum-sha1"); //$NON-NLS-1$
		if (checksum != null && !checksum.equals(sha1(data))) {
			error(ex, 400, "BadDigest"); //$NON-NLS-1$
			return;
		}
		objects.put(path, data);
		ex.sendResponseHeaders(200, -1);
	}

	private static String sha1(byte[] data) {
		try {
			return Base64.getEncoder().encodeToString(
					MessageDigest.getInstance("SHA-1").digest(data)); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void drain(HttpExchange ex) {
		try (InputStream in = ex.getRequestBody()) {
			in.readAllBytes();
		} catch (IOException e) {
			// Ignore: the response does not depend on the body.
		}
	}

	private static void error(HttpExchange ex, int status, String code)
			throws IOException {
		if ("HEAD".equals(ex.getRequestMethod())) { //$NON-NLS-1$
			ex.sendResponseHeaders(status, -1);
			return;
		}
		byte[] body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" //$NON-NLS-1$
				+ "<Error><Code>" + code + "</Code></Error>").getBytes(UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		ex.getResponseHeaders().set("Content-Type", "application/xml"); //$NON-NLS-1$ //$NON-NLS-2$
		ex.sendResponseHeaders(status, body.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(body);
		}
	}
}
