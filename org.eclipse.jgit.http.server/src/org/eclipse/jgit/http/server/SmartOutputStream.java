/*
 * Copyright (C) 2010, Google Inc.
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

import static org.eclipse.jgit.http.server.ServletUtils.acceptsGzipEncoding;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * Buffers a response, trying to gzip it if the user agent supports that.
 * <p>
 * If the response overflows the buffer, gzip is skipped and the response is
 * streamed to the client as its produced, most likely using HTTP/1.1 chunked
 * encoding. This is useful for servlets that produce mixed-mode content, where
 * smaller payloads are primarily pure text that compresses well, while much
 * larger payloads are heavily compressed binary data. {@link UploadPackServlet}
 * is one such servlet.
 */
class SmartOutputStream extends TemporaryBuffer {
	private static final int LIMIT = 32 * 1024;

	private final HttpServletRequest req;
	private final HttpServletResponse rsp;
	private boolean compressStream;
	private boolean startedOutput;

	SmartOutputStream(final HttpServletRequest req,
			final HttpServletResponse rsp,
			boolean compressStream) {
		super(LIMIT);
		this.req = req;
		this.rsp = rsp;
		this.compressStream = compressStream;
	}

	/** {@inheritDoc} */
	@Override
	protected OutputStream overflow() throws IOException {
		startedOutput = true;

		OutputStream out = rsp.getOutputStream();
		if (compressStream && acceptsGzipEncoding(req)) {
			rsp.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
			out = new GZIPOutputStream(out);
		}
		return out;
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		super.close();

		if (!startedOutput) {
			// If output hasn't started yet, the entire thing fit into our
			// buffer. Try to use a proper Content-Length header, and also
			// deflate the response with gzip if it will be smaller.
			if (256 < this.length() && acceptsGzipEncoding(req)) {
				TemporaryBuffer gzbuf = new TemporaryBuffer.Heap(LIMIT);
				try {
					try (GZIPOutputStream gzip = new GZIPOutputStream(gzbuf)) {
						this.writeTo(gzip, null);
					}
					if (gzbuf.length() < this.length()) {
						rsp.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
						writeResponse(gzbuf);
						return;
					}
				} catch (IOException err) {
					// Most likely caused by overflowing the buffer, meaning
					// its larger if it were compressed. Discard compressed
					// copy and use the original.
				}
			}
			writeResponse(this);
		}
	}

	private void writeResponse(TemporaryBuffer out) throws IOException {
		// The Content-Length cannot overflow when cast to an int, our
		// hardcoded LIMIT constant above assures us we wouldn't store
		// more than 2 GiB of content in memory.
		rsp.setContentLength((int) out.length());
		try (OutputStream os = rsp.getOutputStream()) {
			out.writeTo(os, null);
			os.flush();
		}
	}
}
