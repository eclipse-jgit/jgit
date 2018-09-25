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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.util.HttpSupport;

/**
 * Handle asynchronous large object download.
 *
 * @since 4.7
 */
public class ObjectDownloadListener implements WriteListener {

	private static Logger LOG = Logger
			.getLogger(ObjectDownloadListener.class.getName());

	private final AsyncContext context;

	private final HttpServletResponse response;

	private final ServletOutputStream out;

	private final ReadableByteChannel in;

	private final WritableByteChannel outChannel;

	private ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

	/**
	 * <p>Constructor for ObjectDownloadListener.</p>
	 *
	 * @param repository
	 *            the repository storing large objects
	 * @param context
	 *            the servlet asynchronous context
	 * @param response
	 *            the servlet response
	 * @param id
	 *            id of the object to be downloaded
	 * @throws java.io.IOException
	 */
	public ObjectDownloadListener(FileLfsRepository repository,
			AsyncContext context, HttpServletResponse response,
			AnyLongObjectId id) throws IOException {
		this.context = context;
		this.response = response;
		this.in = repository.getReadChannel(id);
		this.out = response.getOutputStream();
		this.outChannel = Channels.newChannel(out);

		response.addHeader(HttpSupport.HDR_CONTENT_LENGTH,
				String.valueOf(repository.getSize(id)));
		response.setContentType(Constants.HDR_APPLICATION_OCTET_STREAM);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Write file content
	 */
	@Override
	public void onWritePossible() throws IOException {
		while (out.isReady()) {
			try {
				buffer.clear();
				if (in.read(buffer) < 0) {
					buffer = null;
				} else {
					buffer.flip();
				}
			} catch(Throwable t) {
				LOG.log(Level.SEVERE, t.getMessage(), t);
				buffer = null;
			} finally {
				if (buffer != null) {
					outChannel.write(buffer);
				} else {
					try {
						out.close();
					} finally {
						context.complete();
					}
					// This is need to avoid endless loop in recent Jetty versions.
					// That's because out.isReady() is returning true for already
					// closed streams and because out.close() doesn't throw any
					// exception any more when trying to close already closed stream.
					return;
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Handle errors
	 */
	@Override
	public void onError(Throwable e) {
		try {
			FileLfsServlet.sendError(response,
					HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			context.complete();
			in.close();
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}
}
