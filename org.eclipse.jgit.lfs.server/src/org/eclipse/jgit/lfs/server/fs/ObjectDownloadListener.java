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
			} catch (Throwable t) {
				LOG.log(Level.SEVERE, t.getMessage(), t);
				buffer = null;
			} finally {
				if (buffer != null) {
					outChannel.write(buffer);
				} else {
					try {
						in.close();
					} catch (IOException e) {
						LOG.log(Level.SEVERE, e.getMessage(), e);
					}
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
