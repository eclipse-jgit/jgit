/*
 * Copyright (C) 2009-2010, Google Inc.
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

import static javax.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_RANGES;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_LENGTH;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_RANGE;
import static org.eclipse.jgit.util.HttpSupport.HDR_IF_RANGE;
import static org.eclipse.jgit.util.HttpSupport.HDR_RANGE;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.MessageFormat;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Dumps a file over HTTP GET (or its information via HEAD).
 * <p>
 * Supports a single byte range requested via {@code Range} HTTP header. This
 * feature supports a dumb client to resume download of a larger object file.
 */
final class FileSender {
	private final File path;

	private final RandomAccessFile source;

	private final long lastModified;

	private final long fileLen;

	private long pos;

	private long end;

	FileSender(final File path) throws FileNotFoundException {
		this.path = path;
		this.source = new RandomAccessFile(path, "r");

		try {
			this.lastModified = path.lastModified();
			this.fileLen = source.getChannel().size();
			this.end = fileLen;
		} catch (IOException e) {
			try {
				source.close();
			} catch (IOException closeError) {
				// Ignore any error closing the stream.
			}

			final FileNotFoundException r;
			r = new FileNotFoundException(MessageFormat.format(HttpServerText.get().cannotGetLengthOf, path));
			r.initCause(e);
			throw r;
		}
	}

	void close() {
		try {
			source.close();
		} catch (IOException e) {
			// Ignore close errors on a read-only stream.
		}
	}

	long getLastModified() {
		return lastModified;
	}

	String getTailChecksum() throws IOException {
		final int n = 20;
		final byte[] buf = new byte[n];
		source.seek(fileLen - n);
		source.readFully(buf, 0, n);
		return ObjectId.fromRaw(buf).getName();
	}

	void serve(final HttpServletRequest req, final HttpServletResponse rsp,
			final boolean sendBody) throws IOException {
		if (!initRangeRequest(req, rsp)) {
			rsp.sendError(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
			return;
		}

		rsp.setHeader(HDR_ACCEPT_RANGES, "bytes");
		rsp.setHeader(HDR_CONTENT_LENGTH, Long.toString(end - pos));

		if (sendBody) {
			try (OutputStream out = rsp.getOutputStream()) {
				final byte[] buf = new byte[4096];
				source.seek(pos);
				while (pos < end) {
					final int r = (int) Math.min(buf.length, end - pos);
					final int n = source.read(buf, 0, r);
					if (n < 0) {
						throw new EOFException(MessageFormat.format(HttpServerText.get().unexpectedeOFOn, path));
					}
					out.write(buf, 0, n);
					pos += n;
				}
				out.flush();
			}
		}
	}

	private boolean initRangeRequest(final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		final Enumeration<String> rangeHeaders = getRange(req);
		if (!rangeHeaders.hasMoreElements()) {
			// No range headers, the request is fine.
			return true;
		}

		final String range = rangeHeaders.nextElement();
		if (rangeHeaders.hasMoreElements()) {
			// To simplify the code we support only one range.
			return false;
		}

		final int eq = range.indexOf('=');
		final int dash = range.indexOf('-');
		if (eq < 0 || dash < 0 || !range.startsWith("bytes=")) {
			return false;
		}

		final String ifRange = req.getHeader(HDR_IF_RANGE);
		if (ifRange != null && !getTailChecksum().equals(ifRange)) {
			// If the client asked us to verify the ETag and its not
			// what they expected we need to send the entire content.
			return true;
		}

		try {
			if (eq + 1 == dash) {
				// "bytes=-500" means last 500 bytes
				pos = Long.parseLong(range.substring(dash + 1));
				pos = fileLen - pos;
			} else {
				// "bytes=500-" (position 500 to end)
				// "bytes=500-1000" (position 500 to 1000)
				pos = Long.parseLong(range.substring(eq + 1, dash));
				if (dash < range.length() - 1) {
					end = Long.parseLong(range.substring(dash + 1));
					end++; // range was inclusive, want exclusive
				}
			}
		} catch (NumberFormatException e) {
			// We probably hit here because of a non-digit such as
			// "," appearing at the end of the first range telling
			// us there is a second range following. To simplify
			// the code we support only one range.
			return false;
		}

		if (end > fileLen) {
			end = fileLen;
		}
		if (pos >= end) {
			return false;
		}

		rsp.setStatus(SC_PARTIAL_CONTENT);
		rsp.setHeader(HDR_CONTENT_RANGE, "bytes " + pos + "-" + (end - 1) + "/"
				+ fileLen);
		source.seek(pos);
		return true;
	}

	private static Enumeration<String> getRange(final HttpServletRequest req) {
		return req.getHeaders(HDR_RANGE);
	}
}
