/*
 * Copyright (C) 2014 Christian Halstrick <christian.halstrick@sap.com>
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
