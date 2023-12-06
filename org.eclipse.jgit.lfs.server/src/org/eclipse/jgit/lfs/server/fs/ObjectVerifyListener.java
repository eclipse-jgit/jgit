/*
 * Copyright (c) 2024 Yury Molchan <yury.molchan@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.fs;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lfs.Protocol;
import org.eclipse.jgit.lfs.errors.CorruptLongObjectException;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.LongObjectId;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handle asynchronous object verification.
 *
 * @since 6.10.0
 */
public class ObjectVerifyListener implements ReadListener {

	private static final Logger LOG = Logger
			.getLogger(ObjectVerifyListener.class.getName());

	private final AsyncContext context;

	private final HttpServletResponse response;

	private final ServletInputStream in;

	private final byte[] buffer = new byte[256];

	private final AnyLongObjectId id;
	private final long size;
	private final ByteArrayOutputStream body = new ByteArrayOutputStream(256);

	/**
	 * Constructor for ObjectUploadListener.
	 *
	 * @param repository
	 *            the repository storing large objects
	 * @param context
	 *            a {@link AsyncContext} object.
	 * @param request
	 *            a {@link HttpServletRequest} object.
	 * @param response
	 *            a {@link HttpServletResponse} object.
	 * @param id
	 *            a {@link AnyLongObjectId} object.
	 * @throws IOException
	 *             if an IO error occurred
	 */
	public ObjectVerifyListener(FileLfsRepository repository,
                                AsyncContext context, HttpServletRequest request,
                                HttpServletResponse response, AnyLongObjectId id)
					throws IOException {
		this.id = id;
		this.size = repository.getSize(id);
		this.context = context;
		this.response = response;
		this.in = request.getInputStream();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Writes all the received data to the output channel
	 */
	@Override
	public void onDataAvailable() throws IOException {
		while (in.isReady()) {
			var len = in.read(buffer);
			if (len > 0) {
				body.write(buffer, 0, len);
			} else if (len < 0) {
				close();
				return;
			}
		}
	}

	@Override
	public void onAllDataRead() throws IOException {
		close();
	}

	private void close() throws IOException {
		try {
			in.close();
			var json = new String(body.toByteArray(), StandardCharsets.UTF_8);
			var pointer = Protocol.gson().fromJson(json, Protocol.ObjectSpec.class);
			if (!response.isCommitted()) {
				if (pointer == null || !id.equals(LongObjectId.fromString(pointer.oid))) {
					response.setStatus(422);
				} else if (size >= 0) {
					response.setStatus(size == pointer.size ? HttpServletResponse.SC_OK : 422);
				} else {
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				}
			}
		} finally {
			context.complete();
		}
	}

	@Override
	public void onError(Throwable e) {
		try {
			in.close();
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
