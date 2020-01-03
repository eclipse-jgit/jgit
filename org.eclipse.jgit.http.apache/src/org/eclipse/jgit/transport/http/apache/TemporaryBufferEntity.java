/*
 * Copyright (C) 2014 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.http.apache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.entity.AbstractHttpEntity;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * A {@link org.apache.http.HttpEntity} which takes its content from a
 * {@link org.eclipse.jgit.util.TemporaryBuffer}
 *
 * @since 3.3
 */
public class TemporaryBufferEntity extends AbstractHttpEntity
		implements AutoCloseable {
	private TemporaryBuffer buffer;

	private Integer contentLength;

	/**
	 * Construct a new {@link org.apache.http.HttpEntity} which will contain the
	 * content stored in the specified buffer
	 *
	 * @param buffer
	 */
	public TemporaryBufferEntity(TemporaryBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * Get the <code>buffer</code> containing the content
	 *
	 * @return buffer containing the content
	 */
	public TemporaryBuffer getBuffer() {
		return buffer;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isRepeatable() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public long getContentLength() {
		if (contentLength != null)
			return contentLength.intValue();
		return buffer.length();
	}

	/** {@inheritDoc} */
	@Override
	public InputStream getContent() throws IOException, IllegalStateException {
		return buffer.openInputStream();
	}

	/** {@inheritDoc} */
	@Override
	public void writeTo(OutputStream outstream) throws IOException {
		// TODO: dont we need a progressmonitor
		buffer.writeTo(outstream, null);
	}

	/** {@inheritDoc} */
	@Override
	public boolean isStreaming() {
		return false;
	}

	/**
	 * Set the <code>contentLength</code>
	 *
	 * @param contentLength
	 */
	public void setContentLength(int contentLength) {
		this.contentLength = Integer.valueOf(contentLength);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Close destroys the associated buffer used to buffer the entity
	 * @since 4.5
	 */
	@Override
	public void close() {
		if (buffer != null) {
			buffer.destroy();
		}
	}
}
