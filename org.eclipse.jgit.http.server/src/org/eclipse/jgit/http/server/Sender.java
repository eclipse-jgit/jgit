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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

abstract class Sender {
	abstract void close();

	abstract int getContentLength() throws IOException;

	abstract void sendBody(HttpServletResponse out) throws IOException;

	static class FileSender extends Sender {
		private final RandomAccessFile source;

		FileSender(final File path) throws FileNotFoundException {
			source = new RandomAccessFile(path, "r");
		}

		void close() {
			try {
				source.close();
			} catch (IOException e) {
				// Ignore close errors on a read-only stream.
			}
		}

		@Override
		int getContentLength() throws IOException {
			return (int) source.length();
		}

		String getTailChecksum() throws IOException {
			final int n = 20;
			final byte[] buf = new byte[n];
			NB.readFully(source.getChannel(), source.length() - n, buf, 0, n);
			return ObjectId.fromRaw(buf).getName();
		}

		@Override
		void sendBody(final HttpServletResponse rsp) throws IOException {
			// TODO - Optimize this code path to use NIO and send file support
			// if we are on a container like Jetty where that is possible.
			//

			// TODO - Honor byte range requests, especially if the file is big.
			//

			final byte[] buf = new byte[4096];
			final OutputStream out = rsp.getOutputStream();
			try {
				int n;
				while ((n = source.read(buf)) > 0)
					out.write(buf, 0, n);
			} finally {
				out.close();
			}
		}
	}
}