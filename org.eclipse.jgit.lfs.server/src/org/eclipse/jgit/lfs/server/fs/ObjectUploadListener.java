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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lfs.errors.CorruptLongObjectException;
import org.eclipse.jgit.lfs.internal.AtomicObjectOutputStream;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;

/**
 * Handle asynchronous object upload.
 *
 * @since 4.6
 */
public class ObjectUploadListener implements ReadListener {

	private static final Logger LOG = Logger
			.getLogger(ObjectUploadListener.class.getName());

	private final AsyncContext context;

	private final HttpServletResponse response;

	private final ServletInputStream in;

	private final ReadableByteChannel inChannel;

	private final AtomicObjectOutputStream out;

	private WritableByteChannel channel;

	private final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);

	private final Path path;

	private long uploaded;

	private Callback callback;

	/**
	 * Callback invoked after object upload completed.
	 *
	 * @since 5.1.7
	 */
	public interface Callback {
		/**
		 * Notified after object upload completed.
		 *
		 * @param path
		 *            path to the object on the backend
		 * @param size
		 *            uploaded size in bytes
		 */
		void uploadCompleted(String path, long size);
	}

	/**
	 * Constructor for ObjectUploadListener.
	 *
	 * @param repository
	 *            the repository storing large objects
	 * @param context
	 *            a {@link javax.servlet.AsyncContext} object.
	 * @param request
	 *            a {@link javax.servlet.http.HttpServletRequest} object.
	 * @param response
	 *            a {@link javax.servlet.http.HttpServletResponse} object.
	 * @param id
	 *            a {@link org.eclipse.jgit.lfs.lib.AnyLongObjectId} object.
	 * @throws java.io.FileNotFoundException
	 * @throws java.io.IOException
	 */
	public ObjectUploadListener(FileLfsRepository repository,
			AsyncContext context, HttpServletRequest request,
			HttpServletResponse response, AnyLongObjectId id)
					throws FileNotFoundException, IOException {
		this.context = context;
		this.response = response;
		this.in = request.getInputStream();
		this.inChannel = Channels.newChannel(in);
		this.out = repository.getOutputStream(id);
		this.channel = Channels.newChannel(out);
		this.path = repository.getPath(id);
		this.uploaded = 0L;
		response.setContentType(Constants.CONTENT_TYPE_GIT_LFS_JSON);
	}

	/**
	 * Set the callback to invoke after upload completed.
	 *
	 * @param callback
	 *            the callback
	 * @return {@code this}.
	 * @since 5.1.7
	 */
	public ObjectUploadListener setCallback(Callback callback) {
		this.callback = callback;
		return this;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Writes all the received data to the output channel
	 */
	@Override
	public void onDataAvailable() throws IOException {
		while (in.isReady()) {
			if (inChannel.read(buffer) > 0) {
				buffer.flip();
				uploaded += Integer.valueOf(channel.write(buffer)).longValue();
				buffer.compact();
			} else {
				buffer.flip();
				while (buffer.hasRemaining()) {
					uploaded += Integer.valueOf(channel.write(buffer))
							.longValue();
				}
				close();
				return;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public void onAllDataRead() throws IOException {
		close();
	}

	/**
	 * Close resources held by this listener
	 *
	 * @throws java.io.IOException
	 */
	protected void close() throws IOException {
		try {
			inChannel.close();
			channel.close();
			// TODO check if status 200 is ok for PUT request, HTTP foresees 204
			// for successful PUT without response body
			if (!response.isCommitted()) {
				response.setStatus(HttpServletResponse.SC_OK);
			}
			if (callback != null) {
				callback.uploadCompleted(path.toString(), uploaded);
			}
		} finally {
			context.complete();
		}
	}

	/** {@inheritDoc} */
	@Override
	public void onError(Throwable e) {
		try {
			out.abort();
			inChannel.close();
			channel.close();
			int status;
			if (e instanceof CorruptLongObjectException) {
				status = HttpStatus.SC_BAD_REQUEST;
				LOG.log(Level.WARNING, e.getMessage(), e);
			} else {
				status = HttpStatus.SC_INTERNAL_SERVER_ERROR;
				LOG.log(Level.SEVERE, e.getMessage(), e);
			}
			FileLfsServlet.sendError(response, status, e.getMessage());
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, ex.getMessage(), ex);
		}
	}
}
