/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/** HTTP servlet that operates against a single Git repository. */
public abstract class RepositoryServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/** Request attribute which stores the {@link Repository} instance. */
	private static final String ATTRIBUTE_REPOSITORY = "org.eclipse.jgit.Repository";

	/**
	 * Get the selected repository from the request.
	 *
	 * @param req
	 *            the HTTP request.
	 * @return the repository; never null.
	 * @throws IllegalStateException
	 *             the repository was not set by the filter, the servlet is
	 *             being invoked incorrectly and the programmer should ensure
	 *             the filter runs before the servlet.
	 */
	public static Repository getRepository(final HttpServletRequest req) {
		Repository db = (Repository) req.getAttribute(ATTRIBUTE_REPOSITORY);
		if (db == null)
			throw new IllegalStateException("Expected Repository attribute");
		return db;
	}

	/**
	 * Set the repository selected by this request.
	 *
	 * @param req
	 *            the request.
	 * @param db
	 *            the repository to select, must not be null.
	 */
	static void setRepository(final HttpServletRequest req, final Repository db) {
		assert db != null;
		req.setAttribute(ATTRIBUTE_REPOSITORY, db);
	}

	/**
	 * Disallow caching of the HTTP response.
	 *
	 * @param rsp
	 *            the response whose content must not be cached.
	 */
	protected void nocache(final HttpServletResponse rsp) {
		rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
		rsp.setHeader("Pragma", "no-cache");
		rsp.setHeader("Cache-Control", "no-cache, max-age=0, must-revalidate");
	}

	/**
	 * Allow caching of the HTTP response for an indefinite period.
	 * <p>
	 * The server will set cache control headers such that the content may be
	 * cached by any proxy server for a very long period of time, potentially
	 * for many years.
	 *
	 * @param rsp
	 *            the response whose content can be cached for a long time.
	 */
	protected void cacheForever(final HttpServletResponse rsp) {
		final long now = System.currentTimeMillis();
		rsp.setHeader("Cache-Control", "public, max-age=31536000");
		rsp.setDateHeader("Expires", now + 31536000000L);
		rsp.setDateHeader("Date", now);
	}

	/**
	 * Send a plain text response to a {@code GET} or {@code HEAD} HTTP request.
	 * <p>
	 * The text response is encoded in the Git character encoding, UTF-8.
	 * <p>
	 * If the user agent supports a compressed transfer encoding and the content
	 * is large enough, the content may be compressed before sending.
	 * <p>
	 * The {@code ETag} and {@code Content-Length} headers are automatically set
	 * by this method. {@code Content-Encoding} is conditionally set if the user
	 * agent supports a compressed transfer. Callers are responsible for setting
	 * any cache control headers (
	 * {@link RepositoryServlet#cacheForever(HttpServletResponse)} or
	 * {@link RepositoryServlet#nocache(HttpServletResponse)}).
	 *
	 * @param content
	 *            to return to the user agent as this entity's body.
	 * @param req
	 *            the incoming request.
	 * @param rsp
	 *            the outgoing response.
	 * @throws IOException
	 *             the servlet API rejected sending the body.
	 */
	protected void sendPlainText(final String content,
			final HttpServletRequest req, final HttpServletResponse rsp)
			throws IOException {
		final String enc = Constants.CHARACTER_ENCODING;
		final byte[] raw = content.getBytes(enc);
		rsp.setContentType("text/plain");
		rsp.setCharacterEncoding(enc);
		send(raw, req, rsp);
	}

	/**
	 * Send a response to a {@code GET} or {@code HEAD} HTTP request.
	 * <p>
	 * If the user agent supports a compressed transfer encoding and the content
	 * is large enough, the content may be compressed before sending.
	 * <p>
	 * The {@code ETag} and {@code Content-Length} headers are automatically set
	 * by this method. {@code Content-Encoding} is conditionally set if the user
	 * agent supports a compressed transfer. Callers are responsible for setting
	 * {@code Content-Type} and any cache control headers (
	 * {@link RepositoryServlet#cacheForever(HttpServletResponse)} or
	 * {@link RepositoryServlet#nocache(HttpServletResponse)}).
	 *
	 * @param content
	 *            to return to the user agent as this entity's body.
	 * @param req
	 *            the incoming request.
	 * @param rsp
	 *            the outgoing response.
	 * @throws IOException
	 *             the servlet API rejected sending the body.
	 */
	protected void send(byte[] content, final HttpServletRequest req,
			final HttpServletResponse rsp)
			throws IOException {
		content = sendInit(content, req, rsp);
		final OutputStream out = rsp.getOutputStream();
		try {
			out.write(content);
		} finally {
			out.close();
		}
	}

	private byte[] sendInit(byte[] content, final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		rsp.setHeader("ETag", etag(content));
		if (256 < content.length && acceptsGzipEncoding(req)) {
			content = compress(content);
			rsp.setHeader("Content-Encoding", "gzip");
		}
		rsp.setContentLength(content.length);
		return content;
	}

	private static boolean acceptsGzipEncoding(final HttpServletRequest req) {
		final String accepts = req.getHeader("Accept-Encoding");
		return accepts != null && 0 <= accepts.indexOf("gzip");
	}

	private static byte[] compress(final byte[] raw) throws IOException {
		final int maxLen = raw.length + 32;
		final ByteArrayOutputStream out = new ByteArrayOutputStream(maxLen);
		final GZIPOutputStream gz = new GZIPOutputStream(out);
		gz.write(raw);
		gz.finish();
		gz.flush();
		return out.toByteArray();
	}

	private static String etag(final byte[] content) {
		final MessageDigest md = Constants.newMessageDigest();
		md.update(content);
		return ObjectId.fromRaw(md.digest()).getName();
	}
}
